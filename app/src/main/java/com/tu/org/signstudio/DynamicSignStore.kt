package com.tu.org.signstudio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.sqrt

data class DynamicPrediction(
    val label: String,
    val confidence: Float,
    val distance: Float,
    val sequenceCount: Int
)

/**
 * Stores and recognizes dynamic signs.
 *
 * IMPORTANT: older SignStudio builds stored sequences as:
 *   personal_dynamic_sign_training/LABEL/movement.seq.jsonl
 * while a newer build briefly used:
 *   personal_dynamic_sign_training/LABEL.seq.jsonl
 *
 * This implementation reads BOTH layouts so previously recorded signs are not lost.
 * New recordings use the per-label directory layout because that is the layout already
 * present on the validation phone and is easier to back up/export safely.
 */
class DynamicSignStore(private val context: Context) {
    private val root = File(context.filesDir, "personal_dynamic_sign_training").apply { mkdirs() }
    private val targetFrames = 24
    // Cache en memoria: evita abrir y parsear todos los JSONL en cada frame de cámara.
    @Volatile private var cachedByLabel: Map<String, List<List<FloatArray>>>? = null

    private fun invalidateCache() { cachedByLabel = null }

    @Synchronized
    private fun sequencesByLabel(): Map<String, List<List<FloatArray>>> {
        cachedByLabel?.let { return it }
        val grouped = linkedMapOf<String, MutableList<List<FloatArray>>>()
        allSequenceFiles().forEach { file ->
            loadRows(file).forEach { (label, seq) ->
                grouped.getOrPut(label) { mutableListOf() }.add(seq)
            }
        }
        return grouped.mapValues { it.value.toList() }.also { cachedByLabel = it }
    }

    fun saveSequence(label: String, frames: List<FloatArray>): Int {
        if (frames.size < 6) return countSequences(label)
        val normalized = resample(frames, targetFrames)
        val safe = sanitize(label)
        val labelDir = File(root, safe).apply { mkdirs() }
        val file = File(labelDir, "movement.seq.jsonl")

        val frameArray = JSONArray()
        normalized.forEach { frame ->
            val values = JSONArray()
            frame.forEach { values.put(it.toDouble()) }
            frameArray.put(values)
        }

        val row = JSONObject()
            .put("label", label.trim().uppercase(Locale.ROOT))
            .put("timestamp", System.currentTimeMillis())
            .put("frames", frameArray)

        file.appendText(row.toString() + "\n")
        invalidateCache()
        return countSequences(label)
    }

    fun loadPlaybackSequence(label: String): List<FloatArray>? =
        filesForLabel(label).flatMap { loadRows(it) }.lastOrNull()?.second

    fun countSequences(label: String): Int =
        filesForLabel(label).sumOf { file -> loadRows(file).size }

    fun trainedLabels(): Map<String, Int> {
        val grouped = linkedMapOf<String, MutableList<Pair<String, List<FloatArray>>>>()
        allSequenceFiles().forEach { file ->
            loadRows(file).forEach { row -> grouped.getOrPut(row.first) { mutableListOf() } += row }
        }
        return grouped.toSortedMap().mapValues { it.value.size }
    }

    fun deleteLabel(label: String): Boolean {
        var ok = true
        filesForLabel(label).forEach { if (it.exists() && !it.delete()) ok = false }
        val dir = File(root, sanitize(label))
        if (dir.exists() && dir.listFiles().isNullOrEmpty()) dir.delete()
        invalidateCache()
        return ok
    }

    fun predictSequence(
        frames: List<FloatArray>,
        // One validated recording is enough to make a sign usable immediately.
        // More samples still improve robustness because the nearest two are averaged.
        minSequences: Int = 1,
        maxDistance: Float = 0.30f,
        requiredMargin: Float = 0.010f,
        minConfidence: Float = 0.22f,
        minMotion: Float = 0.018f
    ): DynamicPrediction? {
        if (frames.size < 8) return null
        val input = resample(frames, targetFrames)

        // Una pose quieta NO debe competir contra las señas dinámicas.
        // Las 177 secuencias recuperadas del teléfono tienen movimiento real
        // por encima de este piso; así las letras/poses estáticas continúan
        // resolviéndose por PersonalSignStore/ONNX.
        if (motionEnergy(input) < minMotion) return null
        // Las plantillas ya están cargadas en RAM; aquí no hay I/O ni parseo JSON.
        val byLabel = sequencesByLabel()

        val candidates = mutableListOf<DynamicPrediction>()
        byLabel.forEach { (label, sequences) ->
            if (sequences.size < minSequences) return@forEach
            val distances = sequences.map { seq -> sequenceDistance(input, seq) }.sorted()
            val k = minOf(2, distances.size)
            val distance = distances.take(k).average().toFloat()
            val confidence = (1f - distance / maxDistance).coerceIn(0f, 1f)
            candidates += DynamicPrediction(label, confidence, distance, sequences.size)
        }

        if (candidates.isEmpty()) return null
        val sorted = candidates.sortedBy { it.distance }
        val best = sorted.first()
        if (best.distance > maxDistance || best.confidence < minConfidence) return null
        val second = sorted.getOrNull(1)
        if (second != null && second.distance - best.distance < requiredMargin) return null
        return best
    }

    private fun allSequenceFiles(): List<File> {
        val files = mutableListOf<File>()
        // Current/validated layout: LABEL/movement.seq.jsonl
        root.listFiles { f -> f.isDirectory }?.forEach { dir ->
            val movement = File(dir, "movement.seq.jsonl")
            if (movement.isFile) files += movement
        }
        // Compatibility with the temporary flat layout: LABEL.seq.jsonl
        root.listFiles { f -> f.isFile && f.name.endsWith(".seq.jsonl") }?.let { files += it }
        return files.distinctBy { it.absolutePath }
    }

    private fun filesForLabel(label: String): List<File> {
        val safe = sanitize(label)
        return listOf(
            File(File(root, safe), "movement.seq.jsonl"),
            File(root, "$safe.seq.jsonl")
        ).filter { it.isFile }
    }

    private fun loadRows(file: File): List<Pair<String, List<FloatArray>>> {
        val rows = mutableListOf<Pair<String, List<FloatArray>>>()
        file.forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            try {
                val obj = JSONObject(line)
                val fallback = file.parentFile?.name ?: file.name.removeSuffix(".seq.jsonl")
                val label = obj.optString("label", fallback)
                val arr = obj.getJSONArray("frames")
                val seq = mutableListOf<FloatArray>()
                for (i in 0 until arr.length()) {
                    val frame = arr.getJSONArray(i)
                    if (frame.length() != 126) continue
                    seq += FloatArray(126) { j -> frame.getDouble(j).toFloat() }
                }
                if (seq.size == targetFrames) rows += label to seq
            } catch (_: Exception) {
            }
        }
        return rows
    }

    private fun resample(frames: List<FloatArray>, count: Int): List<FloatArray> {
        if (frames.isEmpty()) return emptyList()
        if (frames.size == 1) return List(count) { frames.first().copyOf() }
        return List(count) { index ->
            val position = index.toFloat() * (frames.lastIndex.toFloat() / (count - 1).coerceAtLeast(1))
            val low = position.toInt().coerceIn(0, frames.lastIndex)
            val high = (low + 1).coerceAtMost(frames.lastIndex)
            val t = position - low
            FloatArray(126) { j -> frames[low][j] * (1f - t) + frames[high][j] * t }
        }
    }

    private fun motionEnergy(frames: List<FloatArray>): Float {
        if (frames.size < 2) return 0f
        var sum = 0.0
        var count = 0
        for (i in 1 until frames.size) {
            for (j in 0 until 126) {
                val d = (frames[i][j] - frames[i - 1][j]).toDouble()
                sum += d * d
                count++
            }
        }
        return if (count == 0) 0f else sqrt(sum / count).toFloat()
    }

    private fun sequenceDistance(a: List<FloatArray>, b: List<FloatArray>): Float {
        val size = minOf(a.size, b.size)
        var sum = 0.0
        var count = 0
        for (i in 0 until size) {
            for (j in 0 until 126) {
                val d = (a[i][j] - b[i][j]).toDouble()
                sum += d * d
                count++
            }
        }
        return if (count == 0) 1f else sqrt(sum / count).toFloat()
    }

    private fun sanitize(value: String): String =
        value.trim().uppercase(Locale.ROOT)
            .replace("Ñ", "N_ENYE")
            .replace(Regex("[^A-Z0-9_]+"), "_")
            .trim('_')
}
