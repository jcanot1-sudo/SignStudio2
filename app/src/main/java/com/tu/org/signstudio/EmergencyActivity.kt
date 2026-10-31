package com.tu.org.signstudio

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class EmergencyActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        tts = TextToSpeech(this, this)
        val phrases = mapOf(
            R.id.btnEmergencyHelp to "Necesito ayuda",
            R.id.btnEmergencyDoctor to "Necesito un médico",
            R.id.btnEmergencyFamily to "Por favor llame a mi familiar",
            R.id.btnEmergencyRepeat to "No entiendo. Repita por favor",
            R.id.btnEmergencyBathroom to "¿Dónde está el baño?",
            R.id.btnEmergencyWater to "Necesito agua"
        )
        phrases.forEach { (id, phrase) -> findViewById<Button>(id).setOnClickListener { speak(phrase) } }
        findViewById<Button>(R.id.btnEmergencyBack).setOnClickListener { finish() }
    }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts?.language = Locale("es", "GT") }
    private fun speak(text: String) { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "emergency") }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
