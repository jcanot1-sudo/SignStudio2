package com.tu.org.signstudio

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        findViewById<Button>(R.id.btnSenasTexto).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        findViewById<Button>(R.id.btnLenseguaHome).setOnClickListener {
            startActivity(Intent(this, LenseguaActivity::class.java))
        }
        findViewById<Button>(R.id.btnConversacionHome).setOnClickListener {
            startActivity(Intent(this, ConversationActivity::class.java))
        }
        findViewById<Button>(R.id.btnDemoHome).setOnClickListener {
            startActivity(Intent(this, DemoActivity::class.java))
        }
        findViewById<Button>(R.id.btnEmergenciaHome).setOnClickListener {
            startActivity(Intent(this, EmergencyActivity::class.java))
        }
        findViewById<Button>(R.id.btnDiagnosticoHome).setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        findViewById<Button>(R.id.btnAcercaHome).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        findViewById<Button>(R.id.btnDiccionarioHome).setOnClickListener { startActivity(Intent(this, DictionaryActivity::class.java)) }
        findViewById<Button>(R.id.btnAprendizajeHome).setOnClickListener { startActivity(Intent(this, LearningActivity::class.java)) }
        findViewById<Button>(R.id.btnEntrenamientoHome).setOnClickListener { startActivity(Intent(this, TrainingActivity::class.java)) }
        findViewById<Button>(R.id.btnAjustesHome).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

    }
}
