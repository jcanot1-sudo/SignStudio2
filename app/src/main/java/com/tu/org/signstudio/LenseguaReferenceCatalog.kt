package com.tu.org.signstudio

/**
 * Fuentes de referencia para validar el paquete de movimientos.
 * IMPORTANTE: estas URLs son referencias; no sustituyen los JSON de landmarks.
 * No se etiqueta una animación como LENSEGUA hasta validarla contra material real.
 */
object LenseguaReferenceCatalog {
    const val CONADI_BASE = "https://conadi.gob.gt/web/lensegua/"
    const val CONADI_COURTESY_VIDEO = "https://www.youtube.com/watch?v=4cbSyQkXyJo"
    const val CONADI_ALPHABET_VIDEO = "https://www.youtube.com/watch?v=BTwKepmC7L8"

    val priorityVocabulary = listOf(
        "HOLA", "ADIOS", "BUENOS DIAS", "BUENAS TARDES", "BUENAS NOCHES",
        "GRACIAS", "POR FAVOR", "SI", "NO", "COMO ESTAS", "BIEN",
        "AYUDA", "NECESITO AYUDA", "NO ENTIENDO", "REPITE", "DESPACIO",
        "AGUA", "COMER", "BEBER", "BAÑO", "CASA", "MEDICO", "HOSPITAL",
        "DONDE", "QUE", "QUIEN", "YO", "TU"
    )
}
