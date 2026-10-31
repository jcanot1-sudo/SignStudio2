package com.tu.org.signstudio

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class DictionaryActivity : AppCompatActivity() {

    data class Entry(val word: String, val category: String, val type: String = "Seña / deletreo")

    private lateinit var inputSearch: EditText
    private lateinit var spinnerCategory: Spinner
    private lateinit var txtCount: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var store: PersonalSignStore
    private val favorites by lazy { getSharedPreferences("dictionary_favorites", MODE_PRIVATE) }

    private val entries = listOf(
        // Saludos y conversación
        Entry("Hola", "Saludos y conversación"), Entry("Adiós", "Saludos y conversación"),
        Entry("Buenos días", "Saludos y conversación"), Entry("Buenas tardes", "Saludos y conversación"),
        Entry("Buenas noches", "Saludos y conversación"), Entry("Gracias", "Saludos y conversación"),
        Entry("Por favor", "Saludos y conversación"), Entry("Perdón", "Saludos y conversación"),
        Entry("Disculpa", "Saludos y conversación"), Entry("Sí", "Saludos y conversación"),
        Entry("No", "Saludos y conversación"), Entry("Bien", "Saludos y conversación"),
        Entry("Mal", "Saludos y conversación"), Entry("Cómo", "Saludos y conversación"),
        Entry("Qué", "Saludos y conversación"), Entry("Dónde", "Saludos y conversación"),
        Entry("Cuándo", "Saludos y conversación"), Entry("Quién", "Saludos y conversación"),
        Entry("Cuánto", "Saludos y conversación"), Entry("Entiendo", "Saludos y conversación"),
        Entry("No entiendo", "Saludos y conversación"), Entry("Repite", "Saludos y conversación"),
        Entry("Despacio", "Saludos y conversación"), Entry("Rápido", "Saludos y conversación"),
        Entry("Nombre", "Saludos y conversación"), Entry("Mucho gusto", "Saludos y conversación"),

        // Familia y personas
        Entry("Mamá", "Familia y personas"), Entry("Papá", "Familia y personas"),
        Entry("Hermano", "Familia y personas"), Entry("Hermana", "Familia y personas"),
        Entry("Abuelo", "Familia y personas"), Entry("Abuela", "Familia y personas"),
        Entry("Hijo", "Familia y personas"), Entry("Hija", "Familia y personas"),
        Entry("Familia", "Familia y personas"), Entry("Amigo", "Familia y personas"),
        Entry("Amiga", "Familia y personas"), Entry("Esposo", "Familia y personas"),
        Entry("Esposa", "Familia y personas"), Entry("Niño", "Familia y personas"),
        Entry("Niña", "Familia y personas"), Entry("Hombre", "Familia y personas"),
        Entry("Mujer", "Familia y personas"), Entry("Persona", "Familia y personas"),

        // Necesidades básicas
        Entry("Agua", "Necesidades básicas"), Entry("Comida", "Necesidades básicas"),
        Entry("Hambre", "Necesidades básicas"), Entry("Sed", "Necesidades básicas"),
        Entry("Baño", "Necesidades básicas"), Entry("Dormir", "Necesidades básicas"),
        Entry("Descansar", "Necesidades básicas"), Entry("Ayuda", "Necesidades básicas"),
        Entry("Necesito", "Necesidades básicas"), Entry("Quiero", "Necesidades básicas"),
        Entry("Puedo", "Necesidades básicas"), Entry("No puedo", "Necesidades básicas"),
        Entry("Frío", "Necesidades básicas"), Entry("Calor", "Necesidades básicas"),
        Entry("Tengo sueño", "Necesidades básicas"), Entry("Tengo hambre", "Necesidades básicas"),
        Entry("Tengo sed", "Necesidades básicas"), Entry("Necesito ayuda", "Necesidades básicas"),

        // Salud y emergencias
        Entry("Emergencia", "Salud y emergencias"), Entry("Médico", "Salud y emergencias"),
        Entry("Hospital", "Salud y emergencias"), Entry("Dolor", "Salud y emergencias"),
        Entry("Fiebre", "Salud y emergencias"), Entry("Medicamento", "Salud y emergencias"),
        Entry("Ambulancia", "Salud y emergencias"), Entry("Policía", "Salud y emergencias"),
        Entry("Peligro", "Salud y emergencias"), Entry("Auxilio", "Salud y emergencias"),
        Entry("Enfermo", "Salud y emergencias"), Entry("Accidente", "Salud y emergencias"),
        Entry("Herida", "Salud y emergencias"), Entry("Alergia", "Salud y emergencias"),
        Entry("Cabeza", "Salud y emergencias"), Entry("Estómago", "Salud y emergencias"),
        Entry("Respirar", "Salud y emergencias"), Entry("Me duele", "Salud y emergencias"),

        // Lugares
        Entry("Casa", "Lugares"), Entry("Escuela", "Lugares"), Entry("Universidad", "Lugares"),
        Entry("Trabajo", "Lugares"), Entry("Tienda", "Lugares"), Entry("Banco", "Lugares"),
        Entry("Restaurante", "Lugares"), Entry("Iglesia", "Lugares"), Entry("Parque", "Lugares"),
        Entry("Calle", "Lugares"), Entry("Mercado", "Lugares"), Entry("Farmacia", "Lugares"),
        Entry("Baño público", "Lugares"), Entry("Terminal", "Lugares"), Entry("Aeropuerto", "Lugares"),
        Entry("Quetzaltenango", "Lugares"), Entry("Guatemala", "Lugares"),

        // Acciones
        Entry("Comer", "Acciones"), Entry("Beber", "Acciones"), Entry("Caminar", "Acciones"),
        Entry("Correr", "Acciones"), Entry("Sentarse", "Acciones"), Entry("Levantarse", "Acciones"),
        Entry("Ir", "Acciones"), Entry("Venir", "Acciones"), Entry("Esperar", "Acciones"),
        Entry("Hablar", "Acciones"), Entry("Escuchar", "Acciones"), Entry("Mirar", "Acciones"),
        Entry("Escribir", "Acciones"), Entry("Leer", "Acciones"), Entry("Trabajar", "Acciones"),
        Entry("Estudiar", "Acciones"), Entry("Comprar", "Acciones"), Entry("Pagar", "Acciones"),
        Entry("Abrir", "Acciones"), Entry("Cerrar", "Acciones"), Entry("Entrar", "Acciones"),
        Entry("Salir", "Acciones"), Entry("Llamar", "Acciones"), Entry("Buscar", "Acciones"),
        Entry("Encontrar", "Acciones"), Entry("Aprender", "Acciones"), Entry("Enseñar", "Acciones"),

        // Tiempo
        Entry("Hoy", "Tiempo"), Entry("Mañana", "Tiempo"), Entry("Ayer", "Tiempo"),
        Entry("Ahora", "Tiempo"), Entry("Después", "Tiempo"), Entry("Antes", "Tiempo"),
        Entry("Temprano", "Tiempo"), Entry("Tarde", "Tiempo"), Entry("Día", "Tiempo"),
        Entry("Noche", "Tiempo"), Entry("Hora", "Tiempo"), Entry("Minuto", "Tiempo"),
        Entry("Semana", "Tiempo"), Entry("Mes", "Tiempo"), Entry("Año", "Tiempo"),
        Entry("Lunes", "Tiempo"), Entry("Martes", "Tiempo"), Entry("Miércoles", "Tiempo"),
        Entry("Jueves", "Tiempo"), Entry("Viernes", "Tiempo"), Entry("Sábado", "Tiempo"),
        Entry("Domingo", "Tiempo"),

        // Emociones
        Entry("Feliz", "Emociones"), Entry("Triste", "Emociones"), Entry("Enojado", "Emociones"),
        Entry("Miedo", "Emociones"), Entry("Cansado", "Emociones"), Entry("Preocupado", "Emociones"),
        Entry("Sorprendido", "Emociones"), Entry("Tranquilo", "Emociones"), Entry("Nervioso", "Emociones"),
        Entry("Contento", "Emociones"), Entry("Amor", "Emociones"), Entry("Me gusta", "Emociones"),
        Entry("No me gusta", "Emociones"),

        // Objetos comunes
        Entry("Teléfono", "Objetos comunes"), Entry("Computadora", "Objetos comunes"),
        Entry("Carro", "Objetos comunes"), Entry("Dinero", "Objetos comunes"), Entry("Ropa", "Objetos comunes"),
        Entry("Zapatos", "Objetos comunes"), Entry("Llave", "Objetos comunes"), Entry("Puerta", "Objetos comunes"),
        Entry("Mesa", "Objetos comunes"), Entry("Silla", "Objetos comunes"), Entry("Cama", "Objetos comunes"),
        Entry("Libro", "Objetos comunes"), Entry("Cuaderno", "Objetos comunes"), Entry("Lapicero", "Objetos comunes"),
        Entry("Mochila", "Objetos comunes"), Entry("Dinero", "Objetos comunes"), Entry("Tarjeta", "Objetos comunes"),

        // Educación y tecnología
        Entry("Profesor", "Educación y tecnología"), Entry("Estudiante", "Educación y tecnología"),
        Entry("Clase", "Educación y tecnología"), Entry("Tarea", "Educación y tecnología"),
        Entry("Examen", "Educación y tecnología"), Entry("Internet", "Educación y tecnología"),
        Entry("Cámara", "Educación y tecnología"), Entry("Micrófono", "Educación y tecnología"),
        Entry("Aplicación", "Educación y tecnología"), Entry("Contraseña", "Educación y tecnología"),
        Entry("Mensaje", "Educación y tecnología"), Entry("Video", "Educación y tecnología"),
        Entry("Foto", "Educación y tecnología"), Entry("Traducir", "Educación y tecnología"),
        Entry("Seña", "Educación y tecnología"), Entry("Lengua de señas", "Educación y tecnología"),

        // Transporte y movilidad
        Entry("Bus", "Transporte y movilidad"), Entry("Taxi", "Transporte y movilidad"),
        Entry("Motocicleta", "Transporte y movilidad"), Entry("Bicicleta", "Transporte y movilidad"),
        Entry("Avión", "Transporte y movilidad"), Entry("Viajar", "Transporte y movilidad"),
        Entry("Subir", "Transporte y movilidad"), Entry("Bajar", "Transporte y movilidad"),
        Entry("Izquierda", "Transporte y movilidad"), Entry("Derecha", "Transporte y movilidad"),
        Entry("Recto", "Transporte y movilidad"), Entry("Cerca", "Transporte y movilidad"),
        Entry("Lejos", "Transporte y movilidad"), Entry("Parada", "Transporte y movilidad"),

        // Alimentos y compras
        Entry("Desayuno", "Alimentos y compras"), Entry("Almuerzo", "Alimentos y compras"),
        Entry("Cena", "Alimentos y compras"), Entry("Pan", "Alimentos y compras"),
        Entry("Arroz", "Alimentos y compras"), Entry("Carne", "Alimentos y compras"),
        Entry("Pollo", "Alimentos y compras"), Entry("Fruta", "Alimentos y compras"),
        Entry("Verdura", "Alimentos y compras"), Entry("Café", "Alimentos y compras"),
        Entry("Leche", "Alimentos y compras"), Entry("Jugo", "Alimentos y compras"),
        Entry("Precio", "Alimentos y compras"), Entry("Barato", "Alimentos y compras"),
        Entry("Caro", "Alimentos y compras"), Entry("Cambio", "Alimentos y compras"),

        // Colores y descripciones
        Entry("Rojo", "Colores y descripciones"), Entry("Azul", "Colores y descripciones"),
        Entry("Verde", "Colores y descripciones"), Entry("Amarillo", "Colores y descripciones"),
        Entry("Negro", "Colores y descripciones"), Entry("Blanco", "Colores y descripciones"),
        Entry("Grande", "Colores y descripciones"), Entry("Pequeño", "Colores y descripciones"),
        Entry("Alto", "Colores y descripciones"), Entry("Bajo", "Colores y descripciones"),
        Entry("Nuevo", "Colores y descripciones"), Entry("Viejo", "Colores y descripciones"),

        // Frases esenciales
        Entry("¿Cómo estás?", "Frases esenciales"), Entry("Estoy bien", "Frases esenciales"),
        Entry("¿Cuál es tu nombre?", "Frases esenciales"), Entry("Mi nombre es", "Frases esenciales"),
        Entry("¿Dónde está el baño?", "Frases esenciales"), Entry("Quiero agua", "Frases esenciales"),
        Entry("Quiero comer", "Frases esenciales"), Entry("Llame a una ambulancia", "Frases esenciales"),
        Entry("Necesito un médico", "Frases esenciales"), Entry("Estoy perdido", "Frases esenciales"),
        Entry("Espere por favor", "Frases esenciales"), Entry("Escriba por favor", "Frases esenciales"),
        Entry("No escucho", "Frases esenciales"), Entry("Soy una persona sorda", "Frases esenciales"),
        Entry("Hable despacio", "Frases esenciales"), Entry("Muchas gracias", "Frases esenciales")
    ).distinctBy { it.word.lowercase() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dictionary)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Diccionario"

        inputSearch = findViewById(R.id.inputBuscarDiccionario)
        spinnerCategory = findViewById(R.id.spinnerCategoriaDiccionario)
        txtCount = findViewById(R.id.txtConteoDiccionario)
        listContainer = findViewById(R.id.listaDiccionario)
        store = PersonalSignStore(this)

        val categories = listOf("Todas") + entries.map { it.category }.distinct().sorted()
        spinnerCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)

        inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render()
            override fun afterTextChanged(s: Editable?) = Unit
        })

        spinnerCategory.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) = render()
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

        findViewById<Button>(R.id.btnVolverDiccionario).setOnClickListener { finish() }
        render()
    }

    private fun render() {
        val q = inputSearch.text?.toString().orEmpty().trim().lowercase()
        val category = spinnerCategory.selectedItem?.toString() ?: "Todas"
        val filtered = entries.filter {
            (category == "Todas" || it.category == category) &&
                (q.isBlank() || it.word.lowercase().contains(q) || it.category.lowercase().contains(q))
        }

        txtCount.text = "${filtered.size} de ${entries.size} palabras y frases"
        listContainer.removeAllViews()

        if (filtered.isEmpty()) {
            TextView(this).apply {
                text = "No se encontraron palabras con ese filtro."
                textSize = 16f
                setTextColor(0xFF64748B.toInt())
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(32), dp(12), dp(32))
                listContainer.addView(this)
            }
            return
        }

        filtered.forEach { entry ->
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                setBackgroundResource(R.drawable.bg_dark_card)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(10)
                layoutParams = lp

                addView(TextView(this@DictionaryActivity).apply {
                    text = entry.word
                    textSize = 18f
                    setTextColor(0xFFF8FAFC.toInt())
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                val sampleCount = store.countSamples(entry.word)
                val isFavorite = favorites.getBoolean(entry.word.lowercase(), false)
                addView(TextView(this@DictionaryActivity).apply {
                    text = "${entry.category} • ${if (sampleCount >= 5) "✓ entrenada ($sampleCount)" else "lista para entrenar"}"
                    textSize = 13f
                    setTextColor(0xFFCBD5E1.toInt())
                    setPadding(0, dp(4), 0, dp(8))
                })
                addView(LinearLayout(this@DictionaryActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(Button(this@DictionaryActivity).apply {
                        text = "🤟 Ver"
                        isAllCaps = false
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        setOnClickListener {
                            startActivity(Intent(this@DictionaryActivity, LenseguaActivity::class.java).apply {
                                putExtra("dictionary_word", entry.word)
                            })
                        }
                    })
                    addView(Button(this@DictionaryActivity).apply {
                        text = "🧠 Entrenar"
                        isAllCaps = false
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) }
                        setOnClickListener {
                            startActivity(Intent(this@DictionaryActivity, TrainingActivity::class.java).apply {
                                putExtra("label", entry.word)
                            })
                        }
                    })
                    addView(Button(this@DictionaryActivity).apply {
                        text = if (isFavorite) "★" else "☆"
                        isAllCaps = false
                        layoutParams = LinearLayout.LayoutParams(dp(58), LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(6) }
                        setOnClickListener {
                            favorites.edit().putBoolean(entry.word.lowercase(), !isFavorite).apply()
                            render()
                        }
                    })
                })
                listContainer.addView(this)
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
