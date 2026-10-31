package com.tu.org.signstudio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat

class DiagnosticsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val out = findViewById<TextView>(R.id.txtDiagnostics)
        findViewById<Button>(R.id.btnRunDiagnostics).setOnClickListener { runChecks(out) }
        findViewById<Button>(R.id.btnDiagnosticsBack).setOnClickListener { finish() }
        runChecks(out)
    }
    private fun runChecks(out: TextView) {
        val cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val micPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val speech = SpeechRecognizer.isRecognitionAvailable(this)
        val modelAssets = listOf("hand_landmarker.task", "sign_classifier.onnx", "labels.json").associateWith { name ->
            try { assets.open(name).close(); true } catch (_: Exception) { false }
        }
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val front = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            val back = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            out.text = buildString {
                appendLine("DIAGNÓSTICO SIGNTRANSLATE")
                appendLine()
                appendLine(if (cameraPermission) "✓ Permiso de cámara" else "✗ Falta permiso de cámara")
                appendLine(if (micPermission) "✓ Permiso de micrófono" else "✗ Falta permiso de micrófono")
                appendLine(if (front) "✓ Cámara frontal disponible" else "• Cámara frontal no disponible")
                appendLine(if (back) "✓ Cámara trasera disponible" else "• Cámara trasera no disponible")
                appendLine(if (speech) "✓ Reconocimiento de voz disponible" else "✗ Reconocimiento de voz no disponible")
                modelAssets.forEach { (name, ok) -> appendLine(if (ok) "✓ Modelo: $name" else "✗ No se encontró: $name") }
                appendLine()
                appendLine("Este panel sirve para comprobar rápidamente el equipo antes de la demostración.")
            }
        }, ContextCompat.getMainExecutor(this))
    }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
