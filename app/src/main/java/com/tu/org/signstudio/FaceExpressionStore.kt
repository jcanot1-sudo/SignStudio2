package com.tu.org.signstudio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Sidecar dataset for facial expression/head information.
 * It never replaces the existing hand datasets, so old recordings stay compatible.
 */
class FaceExpressionStore(context: Context) {
    private val root = File(context.filesDir, "personal_face_expression_training").apply { mkdirs() }

    fun saveSequence(label: String, frames: List<FloatArray>): Int {
        if (frames.size < 6) return countSequences(label)
        val safe = sanitize(label)
        val dir = File(root, safe).apply { mkdirs() }
        val file = File(dir, "expression.seq.jsonl")
        val arr = JSONArray()
        frames.forEach { f ->
            val values = JSONArray(); f.forEach { values.put(it.toDouble()) }; arr.put(values)
        }
        val row = JSONObject()
            .put("label", label.trim().uppercase(Locale.ROOT))
            .put("timestamp", System.currentTimeMillis())
            .put("frames", arr)
        file.appendText(row.toString() + "\n")
        return countSequences(label)
    }

    fun countSequences(label: String): Int = expressionFile(label).takeIf { it.isFile }
        ?.useLines { lines -> lines.count { it.isNotBlank() } } ?: 0

    fun deleteLabel(label: String) {
        val dir = File(root, sanitize(label)); if (dir.exists()) dir.deleteRecursively()
    }

    fun trainedLabels(): Map<String, Int> = root.listFiles { f -> f.isDirectory }
        ?.associate { it.name to (File(it, "expression.seq.jsonl").takeIf(File::isFile)?.useLines { s -> s.count { l -> l.isNotBlank() } } ?: 0) }
        ?.filterValues { it > 0 } ?: emptyMap()

    private fun expressionFile(label: String) = File(File(root, sanitize(label)), "expression.seq.jsonl")
    private fun sanitize(value: String): String = value.trim().uppercase(Locale.ROOT)
        .replace("Ñ", "N_ENYE").replace(Regex("[^A-Z0-9_]+"), "_").trim('_')
}
