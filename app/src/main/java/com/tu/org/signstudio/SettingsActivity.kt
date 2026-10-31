package com.tu.org.signstudio

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = getSharedPreferences("signtranslate_settings", MODE_PRIVATE)
        val spinnerProfile = findViewById<Spinner>(R.id.spinnerProfile)
        val spinnerSpeed = findViewById<Spinner>(R.id.spinnerSpeed)
        val seekConfidence = findViewById<SeekBar>(R.id.seekConfidence)
        val txtConfidence = findViewById<TextView>(R.id.txtConfidenceValue)

        val profiles = arrayOf("Usuario principal", "Usuario 2", "Demostración")
        spinnerProfile.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, profiles)
        spinnerProfile.setSelection(profiles.indexOf(prefs.getString("profile", profiles[0])).coerceAtLeast(0))

        val speeds = arrayOf("Preciso", "Normal", "Rápido")
        spinnerSpeed.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, speeds)
        spinnerSpeed.setSelection(prefs.getInt("speed", 1).coerceIn(0, 2))

        seekConfidence.progress = prefs.getInt("confidence", 90)
        txtConfidence.text = "Confianza mínima: ${seekConfidence.progress}%"
        seekConfidence.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { txtConfidence.text = "Confianza mínima: ${progress.coerceAtLeast(70)}%" }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) { if ((seekBar?.progress ?: 70) < 70) seekBar?.progress = 70 }
        })

        findViewById<Button>(R.id.btnGuardarAjustes).setOnClickListener {
            prefs.edit()
                .putString("profile", profiles[spinnerProfile.selectedItemPosition])
                .putInt("speed", spinnerSpeed.selectedItemPosition)
                .putInt("confidence", seekConfidence.progress.coerceAtLeast(70))
                .apply()
            Toast.makeText(this, "Ajustes guardados", Toast.LENGTH_SHORT).show()
            finish()
        }
        findViewById<Button>(R.id.btnVolverAjustes).setOnClickListener { finish() }
    }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
