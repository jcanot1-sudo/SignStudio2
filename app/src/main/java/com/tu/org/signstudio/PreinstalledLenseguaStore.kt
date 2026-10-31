package com.tu.org.signstudio

import android.content.Context
import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale

/**
 * Lee animaciones LENSEGUA empaquetadas en assets/lensegua_motion/<ETIQUETA>.json.
 * Formato: [[126 floats], ...] (21 landmarks x 3 x 2 manos por frame).
 * Si un recurso no existe devuelve null: nunca inventa una seña.
 */
class PreinstalledLenseguaStore(private val context: Context) {
    fun load(label: String): List<FloatArray>? {
        val key = key(label)
        val path = "lensegua_motion/$key.json"
        return try {
            val raw = context.assets.open(path).bufferedReader().use { it.readText() }
            val arr = JSONArray(raw)
            val out = mutableListOf<FloatArray>()
            for (i in 0 until arr.length()) {
                val f = arr.getJSONArray(i)
                if (f.length() != 126) continue
                out += FloatArray(126) { j -> f.getDouble(j).toFloat() }
            }
            out.takeIf { it.size >= 2 }
        } catch (_: Exception) { null }
    }

    fun has(label: String): Boolean = try {
        context.assets.open("lensegua_motion/${key(label)}.json").close(); true
    } catch (_: Exception) { false }

    fun installedLabels(): Set<String> = try {
        context.assets.list("lensegua_motion").orEmpty()
            .filter { it.endsWith(".json") }
            .map { it.removeSuffix(".json") }.toSet()
    } catch (_: Exception) { emptySet() }

    private fun key(value: String): String {
        val upper = value.trim().uppercase(Locale.ROOT).replace("Ñ", "__ENYE__")
        return Normalizer.normalize(upper, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace("__ENYE__", "N_ENYE")
            .replace(Regex("[^A-Z0-9_]+"), "_")
            .trim('_')
    }
}
