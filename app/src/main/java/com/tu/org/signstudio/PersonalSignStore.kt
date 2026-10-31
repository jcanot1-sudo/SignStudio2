package com.tu.org.signstudio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.sqrt

data class PersonalPrediction(
    val label: String,
    val confidence: Float,
    val distance: Float,
    val sampleCount: Int
)

class PersonalSignStore(private val context: Context) {

    private val root = File(context.filesDir, "personal_sign_training").apply { mkdirs() }

    fun saveSample(label: String, features: FloatArray): Int {
        require(features.size == 126) { "Se esperaban 126 features." }
        val safe = sanitize(label)
        val file = File(root, "$safe.jsonl")

        val values = JSONArray()
        features.forEach { values.put(it.toDouble()) }

        val row = JSONObject()
            .put("label", label.trim().uppercase(Locale.ROOT))
            .put("timestamp", System.currentTimeMillis())
            .put("features", values)

        file.appendText(row.toString() + "\n")
        return countSamples(label)
    }

    fun countSamples(label: String): Int {
        val file = File(root, "${sanitize(label)}.jsonl")
        if (!file.exists()) return 0
        return file.useLines { lines -> lines.count { it.isNotBlank() } }
    }

    fun trainedLabels(): Map<String, Int> {
        val result = linkedMapOf<String, Int>()
        root.listFiles { file -> file.isFile && file.extension == "jsonl" }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                val rows = loadRows(file)
                if (rows.isNotEmpty()) {
                    val label = rows.first().first
                    result[label] = rows.size
                }
            }
        return result
    }

    fun deleteLabel(label: String): Boolean {
        val file = File(root, "${sanitize(label)}.jsonl")
        return !file.exists() || file.delete()
    }

    fun clearAll(): Boolean {
        var ok = true
        root.listFiles()?.forEach { file ->
            if (file.isFile && !file.delete()) ok = false
        }
        return ok
    }

    fun predict(
        features: FloatArray,
        minSamples: Int = 5,
        maxDistance: Float = 0.20f,
        requiredMargin: Float = 0.018f
    ): PersonalPrediction? {
        if (features.size != 126) return null

        val candidates = mutableListOf<PersonalPrediction>()

        root.listFiles { file -> file.isFile && file.extension == "jsonl" }
            ?.forEach { file ->
                val rows = loadRows(file)
                if (rows.size < minSamples) return@forEach

                // k-NN local: promedio de las 3 muestras más cercanas.
                val distances = rows
                    .map { (_, sample) -> rmsDistance(features, sample) }
                    .sorted()

                val k = minOf(3, distances.size)
                val distance = distances.take(k).average().toFloat()
                val confidence = (1f - (distance / maxDistance)).coerceIn(0f, 1f)

                candidates += PersonalPrediction(
                    label = rows.first().first,
                    confidence = confidence,
                    distance = distance,
                    sampleCount = rows.size
                )
            }

        if (candidates.isEmpty()) return null

        val sorted = candidates.sortedBy { it.distance }
        val best = sorted.first()
        if (best.distance > maxDistance) return null

        val second = sorted.getOrNull(1)
        if (second != null && (second.distance - best.distance) < requiredMargin) {
            return null
        }

        return best
    }

    fun datasetDirectory(): File = root

    private fun loadRows(file: File): List<Pair<String, FloatArray>> {
        val result = mutableListOf<Pair<String, FloatArray>>()
        if (!file.exists()) return result

        file.forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            try {
                val obj = JSONObject(line)
                val label = obj.optString("label", file.nameWithoutExtension)
                val arr = obj.getJSONArray("features")
                if (arr.length() != 126) return@forEachLine
                val values = FloatArray(126) { index -> arr.getDouble(index).toFloat() }
                result += label to values
            } catch (_: Exception) {
                // Ignora una línea dañada y conserva el resto del entrenamiento.
            }
        }
        return result
    }

    private fun rmsDistance(a: FloatArray, b: FloatArray): Float {
        var sum = 0.0
        for (i in a.indices) {
            val d = (a[i] - b[i]).toDouble()
            sum += d * d
        }
        return sqrt(sum / a.size).toFloat()
    }

    private fun sanitize(value: String): String =
        value.trim()
            .uppercase(Locale.ROOT)
            .replace("Ñ", "N_ENYE")
            .replace(Regex("[^A-Z0-9_]+"), "_")
            .trim('_')
}
