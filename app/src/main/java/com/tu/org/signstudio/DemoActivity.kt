package com.tu.org.signstudio

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DemoActivity : AppCompatActivity() {
    private var step = 0
    private lateinit var txtStep: TextView
    private lateinit var txtInstruction: TextView
    private lateinit var btnAction: Button

    private val titles = listOf(
        "1 de 5 • Señas → Texto",
        "2 de 5 • LENSEGUA",
        "3 de 5 • Conversación",
        "4 de 5 • Entrenamiento",
        "5 de 5 • Resumen"
    )
    private val instructions = listOf(
        "Abre la cámara, muestra una seña y enseña cómo SignTranslate identifica la mano, la seña y la confianza.",
        "Muestra una frase rápida y reproduce su deletreo LENSEGUA con Anterior, Siguiente y Reproducir.",
        "Demuestra el modo conversación: voz de la persona oyente y mensajes traducidos en un mismo historial.",
        "Entrena o corrige una seña desde el teléfono y guárdala localmente para volver a reconocerla.",
        "Versión final: visión por computadora, IA, voz, texto, LENSEGUA, entrenamiento local e interacción bidireccional."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_demo)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        txtStep = findViewById(R.id.txtDemoStep)
        txtInstruction = findViewById(R.id.txtDemoInstruction)
        btnAction = findViewById(R.id.btnDemoAction)

        findViewById<Button>(R.id.btnDemoAnterior).setOnClickListener {
            if (step > 0) step--
            render()
        }
        findViewById<Button>(R.id.btnDemoSiguiente).setOnClickListener {
            if (step < titles.lastIndex) step++
            render()
        }
        btnAction.setOnClickListener { openCurrentModule() }
        findViewById<Button>(R.id.btnVolverDemo).setOnClickListener { finish() }
        render()
    }

    private fun render() {
        txtStep.text = titles[step]
        txtInstruction.text = instructions[step]
        btnAction.text = when (step) {
            0 -> "Abrir reconocimiento de señas"
            1 -> "Abrir módulo LENSEGUA"
            2 -> "Abrir modo conversación"
            3 -> "Abrir entrenamiento"
            else -> "Ver acerca del proyecto"
        }
        findViewById<Button>(R.id.btnDemoAnterior).isEnabled = step > 0
        findViewById<Button>(R.id.btnDemoSiguiente).isEnabled = step < titles.lastIndex
    }

    private fun openCurrentModule() {
        val target = when (step) {
            0 -> CameraActivity::class.java
            1 -> LenseguaActivity::class.java
            2 -> ConversationActivity::class.java
            3 -> TrainingActivity::class.java
            else -> AboutActivity::class.java
        }
        startActivity(Intent(this, target))
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

}
