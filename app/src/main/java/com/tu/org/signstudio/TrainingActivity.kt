package com.tu.org.signstudio

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TrainingActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var spinnerLabel: Spinner
    private lateinit var inputCustomLabel: EditText
    private lateinit var txtMuestras: TextView
    private lateinit var txtEstado: TextView
    private lateinit var btnCapturar: Button
    private lateinit var btnCapturaRapida: Button
    private lateinit var btnCapturaMovimiento: Button
    private lateinit var btnEliminar: Button
    private lateinit var btnAbrirCarpeta: Button
    private lateinit var btnExportar: Button
    private lateinit var btnVolver: Button
    private lateinit var btnSwitchTrainingCamera: Button
    private var useBackCamera = false

    private lateinit var cameraExecutor: ExecutorService
    private var handLandmarker: HandLandmarker? = null
    private var faceLandmarker: FaceLandmarker? = null
    private var latestFaceFeatures: FloatArray? = null
    private var latestFaceTimestamp = 0L
    private var latestFeatures: FloatArray? = null
    private var latestFeatureTimestamp = 0L

    private lateinit var store: PersonalSignStore
    private lateinit var dynamicStore: DynamicSignStore
    private lateinit var faceStore: FaceExpressionStore
    private val movementHandler = Handler(Looper.getMainLooper())
    private val movementFrames = mutableListOf<FloatArray>()
    private val movementFaceFrames = mutableListOf<FloatArray>()
    private var movementCapturing = false

    private var rapidCaptureRemaining = 0
    private var rapidCaptureRunning = false

    private val labels = buildList {
        addAll(('A'..'Z').map { it.toString() })
        add(14, "Ñ")
        addAll((0..9).map { it.toString() })
        addAll(SignCatalog.dictionaryLabels)
    }.distinct()

    private val exportTraining =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) exportTrainingZip(uri)
        }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else txtEstado.text = "Permiso de cámara requerido"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_training)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        previewView = findViewById(R.id.previewTraining)
        spinnerLabel = findViewById(R.id.spinnerTrainingLabel)
        inputCustomLabel = findViewById(R.id.inputCustomTrainingLabel)
        txtMuestras = findViewById(R.id.txtTrainingSamples)
        txtEstado = findViewById(R.id.txtTrainingStatus)
        btnCapturar = findViewById(R.id.btnCaptureSample)
        btnCapturaRapida = findViewById(R.id.btnRapidCapture)
        btnCapturaMovimiento = findViewById(R.id.btnCaptureMovement)
        btnEliminar = findViewById(R.id.btnDeleteTraining)
        btnAbrirCarpeta = findViewById(R.id.btnTrainingFolder)
        btnExportar = findViewById(R.id.btnExportTraining)
        btnVolver = findViewById(R.id.btnBackTraining)
        btnSwitchTrainingCamera = findViewById(R.id.btnSwitchTrainingCamera)
        btnSwitchTrainingCamera.setOnClickListener {
            if (movementCapturing || rapidCaptureRunning) {
                txtEstado.text = "Finaliza la captura antes de cambiar de cámara"
            } else {
                useBackCamera = !useBackCamera
                latestFeatures = null
                latestFaceFeatures = null
                btnSwitchTrainingCamera.text = if (useBackCamera) "📷 Usar cámara frontal" else "📷 Usar cámara trasera"
                startCamera()
            }
        }

        store = PersonalSignStore(this)
        dynamicStore = DynamicSignStore(this)
        faceStore = FaceExpressionStore(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            labels
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLabel.adapter = adapter

        val preselect = intent.getStringExtra("label")
        val preselectIndex = labels.indexOf(preselect)
        if (preselectIndex >= 0) {
            spinnerLabel.setSelection(preselectIndex)
        } else if (!preselect.isNullOrBlank()) {
            inputCustomLabel.setText(preselect)
        }

        spinnerLabel.setOnItemSelectedListener(SimpleItemSelectedListener {
            refreshSampleCount()
        })

        btnCapturar.setOnClickListener { saveCurrentSample() }

        btnCapturaRapida.setOnClickListener {
            if (rapidCaptureRunning) {
                stopRapidCapture()
            } else {
                rapidCaptureRemaining = 10
                rapidCaptureRunning = true
                btnCapturaRapida.text = "⏹ Detener captura"
                txtEstado.text = "Mantén la seña visible • capturando 10 muestras"
                captureRapidSample()
            }
        }

        btnCapturaMovimiento.setOnClickListener {
            if (movementCapturing) stopMovementCapture() else startMovementCapture()
        }

        btnEliminar.setOnClickListener {
            val label = selectedLabel()
            store.deleteLabel(label)
            dynamicStore.deleteLabel(label)
            faceStore.deleteLabel(label)
            latestFeatures = null
            refreshSampleCount()
            txtEstado.text = "Entrenamiento de $label eliminado"
        }

        btnExportar.setOnClickListener {
            exportTraining.launch("SignTranslate_LENSEGUA_entrenamiento.zip")
        }

        btnAbrirCarpeta.setOnClickListener {
            Toast.makeText(
                this,
                "Entrenamiento local estático:\n${store.datasetDirectory().absolutePath}\n\nLas secuencias con movimiento se guardan internamente en personal_dynamic_sign_training.",
                Toast.LENGTH_LONG
            ).show()
        }

        btnVolver.setOnClickListener { finish() }

        try {
            setupHandLandmarker()
            setupFaceLandmarker()
        } catch (e: Exception) {
            txtEstado.text = "Error inicializando detector de mano"
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }

        refreshSampleCount()
    }

    private fun setupHandLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.65f)
            .setMinHandPresenceConfidence(0.65f)
            .setMinTrackingConfidence(0.65f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ -> processHandResult(result) }
            .setErrorListener {
                runOnUiThread { txtEstado.text = "Error MediaPipe" }
            }
            .build()

        handLandmarker = HandLandmarker.createFromOptions(this, options)
    }

    private fun setupFaceLandmarker() {
        val baseOptions = BaseOptions.builder().setModelAssetPath("face_landmarker.task").build()
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ -> processFaceResult(result) }
            .setErrorListener { }
            .build()
        faceLandmarker = FaceLandmarker.createFromOptions(this, options)
    }

    private fun processFaceResult(result: FaceLandmarkerResult) {
        val faces = result.faceLandmarks()
        if (faces.isEmpty()) { latestFaceFeatures = null; return }
        val lm = faces[0]
        // Compact expression descriptor: selected mouth, eyes, brows, nose/chin points.
        val ids = intArrayOf(1,10,13,14,17,33,61,70,78,105,133,145,152,159,263,291,300,308,334,362,374,386,468,473)
        val out = FloatArray(ids.size * 3)
        var k = 0
        for (id in ids) {
            if (id < lm.size) {
                val p = lm[id]; out[k++] = p.x(); out[k++] = p.y(); out[k++] = p.z()
            } else { k += 3 }
        }
        // Normalize around nose and inter-eye distance so distance/camera framing matters less.
        val nx = out[0]; val ny = out[1]
        val leftEye = ids.indexOf(33) * 3; val rightEye = ids.indexOf(263) * 3
        val dx = out[leftEye] - out[rightEye]; val dy = out[leftEye+1] - out[rightEye+1]
        val scale = kotlin.math.sqrt(dx*dx + dy*dy).coerceAtLeast(0.0001f)
        for (i in ids.indices) {
            val o=i*3; out[o]=(out[o]-nx)/scale; out[o+1]=(out[o+1]-ny)/scale; out[o+2]/=scale
        }
        latestFaceFeatures = out
        latestFaceTimestamp = System.currentTimeMillis()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)

        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    analyzeImage(imageProxy)
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    if (useBackCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )

                txtEstado.text = if (useBackCamera) "Cámara trasera lista • coloca las manos y rostro dentro del encuadre" else "Cámara frontal lista • coloca la mano completa"
            } catch (e: Exception) {
                txtEstado.text = "No se pudo iniciar la cámara"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeImage(imageProxy: ImageProxy) {
        try {
            var bitmap = imageProxy.toBitmap()

            val rotation = imageProxy.imageInfo.rotationDegrees
            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                bitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
            }

            if (true) { // Guardar futuras muestras en coordenadas consistentes con cámara frontal
                val mirror = Matrix().apply { preScale(-1f, 1f) }
                bitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, mirror, true
                )
            }

            val mpImage = BitmapImageBuilder(bitmap).build()
            val ts = SystemClock.uptimeMillis()
            handLandmarker?.detectAsync(mpImage, ts)
            faceLandmarker?.detectAsync(mpImage, ts)
        } catch (_: Exception) {
        } finally {
            imageProxy.close()
        }
    }

    private fun processHandResult(result: HandLandmarkerResult) {
        if (result.landmarks().isEmpty()) {
            latestFeatures = null
            runOnUiThread {
                txtEstado.text = "No se detecta mano • muestra la seña frente a la cámara"
            }
            return
        }

        latestFeatures = build126Features(result)
        latestFeatureTimestamp = System.currentTimeMillis()

        runOnUiThread {
            val age = store.countSamples(selectedLabel())
            val faceOk = latestFaceFeatures != null && System.currentTimeMillis() - latestFaceTimestamp < 1500L
            txtEstado.text = if (faceOk) "✓ Manos + rostro detectados • listo para entrenar" else "✓ Mano detectada • rostro no visible"
            txtMuestras.text = "Muestras de ${selectedLabel()}: $age"
        }
    }

    private fun saveCurrentSample(onDone: (() -> Unit)? = null) {
        val features = latestFeatures
        val age = System.currentTimeMillis() - latestFeatureTimestamp

        if (features == null || age > 1500L) {
            runOnUiThread {
                txtEstado.text = "Primero mantén la seña visible y estable"
                onDone?.invoke()
            }
            return
        }

        val label = selectedLabel()
        val count = store.saveSample(label, features.copyOf())
        runOnUiThread {
            txtMuestras.text = "Muestras de $label: $count"
            txtEstado.text = if (count >= 20) {
                "✓ $label tiene buen entrenamiento local ($count muestras)"
            } else {
                "Muestra guardada • recomendado: 20–30"
            }
            onDone?.invoke()
        }
    }

    private fun captureRapidSample() {
        if (!rapidCaptureRunning || rapidCaptureRemaining <= 0) {
            stopRapidCapture()
            return
        }

        saveCurrentSample {
            rapidCaptureRemaining--
            if (rapidCaptureRemaining > 0 && rapidCaptureRunning) {
                previewView.postDelayed({ captureRapidSample() }, 500L)
            } else {
                stopRapidCapture()
                txtEstado.text = "✓ Captura rápida completada"
            }
        }
    }

    private fun stopRapidCapture() {
        rapidCaptureRunning = false
        rapidCaptureRemaining = 0
        btnCapturaRapida.text = "⚡ Capturar 10 muestras"
    }

    private fun selectedLabel(): String {
        val custom = inputCustomLabel.text?.toString()?.trim().orEmpty()
        return custom.ifBlank { spinnerLabel.selectedItem?.toString() ?: "A" }
    }

    private fun refreshSampleCount() {
        val label = selectedLabel()
        val staticCount = store.countSamples(label)
        val dynamicCount = dynamicStore.countSequences(label)
        val faceCount = faceStore.countSequences(label)
        txtMuestras.text = "${label}: $staticCount estáticas • $dynamicCount movimiento • $faceCount expresión"
    }

    private fun startMovementCapture() {
        movementFrames.clear()
        movementFaceFrames.clear()
        movementCapturing = true
        btnCapturaMovimiento.text = "⏹ Detener movimiento"
        txtEstado.text = "🎥 Haz la seña completa • máximo 6 segundos • pulsa Finalizar cuando termines"
        captureMovementFrame()
    }

    private fun captureMovementFrame() {
        if (!movementCapturing) return
        latestFeatures?.copyOf()?.let { movementFrames += it }
        latestFaceFeatures?.takeIf { System.currentTimeMillis() - latestFaceTimestamp < 1500L }?.copyOf()?.let { movementFaceFrames += it }
        if (movementFrames.size >= 48) {
            saveMovementCapture()
            return
        }
        movementHandler.postDelayed({ captureMovementFrame() }, 125L)
    }

    private fun stopMovementCapture() {
        saveMovementCapture()
    }

    private fun saveMovementCapture() {
        movementCapturing = false
        movementHandler.removeCallbacksAndMessages(null)
        btnCapturaMovimiento.text = "🎥 Guardar seña con movimiento (hasta 6 s)"

        if (movementFrames.size < 6) {
            txtEstado.text = "⚠ Movimiento demasiado corto. Intenta nuevamente."
            movementFrames.clear()
            return
        }

        val label = selectedLabel()
        val count = dynamicStore.saveSequence(label, movementFrames.toList())
        val faceCount = if (movementFaceFrames.size >= 6) faceStore.saveSequence(label, movementFaceFrames.toList()) else faceStore.countSequences(label)
        txtEstado.text = if (movementFaceFrames.size >= 6) "✓ $label guardada: 2 manos/movimiento + rostro/expresión ($count / $faceCount)" else "✓ Movimiento de $label guardado ($count) • rostro no capturado"
        movementFrames.clear()
        movementFaceFrames.clear()
        refreshSampleCount()
    }

    private fun exportTrainingZip(uri: android.net.Uri) {
        txtEstado.text = "Preparando exportación…"
        Thread {
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    ZipOutputStream(output).use { zip ->
                        val roots = listOf(
                            "personal_sign_training" to File(filesDir, "personal_sign_training"),
                            "personal_dynamic_sign_training" to File(filesDir, "personal_dynamic_sign_training"),
                            "personal_face_expression_training" to File(filesDir, "personal_face_expression_training")
                        )
                        var filesAdded = 0
                        roots.forEach { (prefix, root) ->
                            if (root.exists()) root.walkTopDown().filter { it.isFile }.forEach { file ->
                                val rel = file.relativeTo(root).invariantSeparatorsPath
                                zip.putNextEntry(ZipEntry("$prefix/$rel"))
                                file.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                                filesAdded++
                            }
                        }
                        val manifest = "SignTranslate LENSEGUA training export\ncreated=${System.currentTimeMillis()}\nstaticLabels=${store.trainedLabels().size}\ndynamicLabels=${dynamicStore.trainedLabels().size}\nfaceExpressionLabels=${faceStore.trainedLabels().size}\n"
                        zip.putNextEntry(ZipEntry("manifest.txt")); zip.write(manifest.toByteArray()); zip.closeEntry()
                        runOnUiThread { txtEstado.text = "✓ Exportación completa • $filesAdded archivos guardados" }
                    }
                } ?: throw IllegalStateException("No se pudo abrir el archivo")
            } catch (e: Exception) {
                runOnUiThread { txtEstado.text = "⚠ No se pudo exportar: ${e.message ?: "error"}" }
            }
        }.start()
    }

    private fun build126Features(result: HandLandmarkerResult): FloatArray {
        val left = FloatArray(63)
        val right = FloatArray(63)

        val hands = result.landmarks()
        val handedness = result.handedness()

        for (i in hands.indices) {
            val landmarks = hands[i]
            if (landmarks.size != 21) continue

            val normalized = normalizeHand(
                landmarks.map { Triple(it.x(), it.y(), it.z()) }
            )

            var handName = ""
            if (i < handedness.size && handedness[i].isNotEmpty()) {
                handName = handedness[i][0].categoryName()
            }

            // Mantener exactamente la misma convención de CameraActivity.
            if (handName.equals("Left", ignoreCase = true)) {
                normalized.copyInto(right)
            } else {
                normalized.copyInto(left)
            }
        }

        return FloatArray(126).also { output ->
            left.copyInto(output, 0)
            right.copyInto(output, 63)
        }
    }

    private fun normalizeHand(
        points: List<Triple<Float, Float, Float>>
    ): FloatArray {
        val output = FloatArray(63)
        val xs = points.map { it.first }
        val ys = points.map { it.second }

        val xmin = xs.minOrNull() ?: 0f
        val xmax = xs.maxOrNull() ?: 1f
        val ymin = ys.minOrNull() ?: 0f
        val ymax = ys.maxOrNull() ?: 1f

        val bw = maxOf(xmax - xmin, 0.000001f)
        val bh = maxOf(ymax - ymin, 0.000001f)

        var index = 0
        for (point in points) {
            output[index++] = (point.first - xmin) / bw
            output[index++] = (point.second - ymin) / bh
            output[index++] = point.third
        }
        return output
    }

    override fun onDestroy() {
        movementHandler.removeCallbacksAndMessages(null)
        movementCapturing = false
        stopRapidCapture()
        handLandmarker?.close()
        faceLandmarker?.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

private class SimpleItemSelectedListener(
    private val onSelected: () -> Unit
) : android.widget.AdapterView.OnItemSelectedListener {
    override fun onItemSelected(
        parent: android.widget.AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long
    ) = onSelected()

    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
}
