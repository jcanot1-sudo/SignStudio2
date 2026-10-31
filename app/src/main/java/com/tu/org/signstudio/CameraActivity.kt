package com.tu.org.signstudio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

import org.json.JSONArray

import java.nio.FloatBuffer
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import kotlin.math.exp

class CameraActivity : AppCompatActivity() {

    // =====================================================
    // CONFIGURACIÓN
    // =====================================================

    private val T = 24
    private val FEATURES_PER_FRAME = 126
    private val INPUT_DIM = 3024

    private var CONF_THRESH = 0.93f

    private var REQUIRED_STABLE_PREDICTIONS = 10
    private val ACCEPT_COOLDOWN_MS = 1200L

    // Versión final: permite letras, números, palabras y frases que existan
    // en labels.json/modelo. Las señas entrenadas en el teléfono siguen
    // teniendo prioridad sobre el modelo general.
    private fun isUsableLabel(label: String): Boolean {
        val normalized = label.trim()
        return normalized.isNotEmpty() &&
                !normalized.equals("DESCONOCIDO", ignoreCase = true) &&
                !normalized.equals("UNKNOWN", ignoreCase = true)
    }


    // =====================================================
    // INTERFAZ
    // =====================================================

    private lateinit var previewView: PreviewView

    private lateinit var handOverlay: HandOverlayView

    private lateinit var txtMano: TextView
    private lateinit var txtSena: TextView
    private lateinit var txtConfianza: TextView
    private lateinit var txtConversacion: TextView
    private lateinit var txtAsistenteCamara: TextView

    private lateinit var btnMicrofono: Button
    private lateinit var btnLeer: Button
    private lateinit var btnLimpiar: Button

    // =====================================================
    // CÁMARA
    // =====================================================

    private lateinit var cameraExecutor: ExecutorService
    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    // =====================================================
    // MEDIAPIPE
    // =====================================================

    private var handLandmarker: HandLandmarker? = null

    // =====================================================
    // ONNX
    // =====================================================

    private lateinit var ortEnvironment: OrtEnvironment
    private lateinit var ortSession: OrtSession

    private var labels: List<String> = emptyList()

    // =====================================================
    // BUFFER
    // =====================================================

    private val frameBuffer =
        ArrayDeque<FloatArray>()

    // Buffer independiente para señas con movimiento. El clasificador ONNX
    // sigue usando 24 frames, pero una seña dinámica puede durar 1–4 segundos.
    private val dynamicFrameBuffer = ArrayDeque<FloatArray>()
    private val DYNAMIC_MAX_FRAMES = 96
    private var dynamicTick = 0

    // =====================================================
    // CONVERSACIÓN
    // =====================================================

    private val conversation =
        mutableListOf<String>()

    private var lastPrediction: String? = null

    private var candidatePrediction: String? = null

    private var candidateCount = 0

    private var lastAcceptedTime = 0L

    private lateinit var personalSignStore: PersonalSignStore
    private lateinit var dynamicSignStore: DynamicSignStore
    private lateinit var txtOrigen: TextView
    private var lastPersonalPrediction: PersonalPrediction? = null
    private var lastDynamicPrediction: DynamicPrediction? = null


    // =====================================================
    // VOZ
    // =====================================================

    private lateinit var tts: TextToSpeech

    private var speechRecognizer: SpeechRecognizer? = null

    private lateinit var speechIntent: Intent

    private var micEnabled = false

    // =====================================================
    // PERMISO CÁMARA
    // =====================================================

    private val cameraPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                startCamera()
            }
        }

    // =====================================================
    // PERMISO MICRÓFONO
    // =====================================================

    private val microphonePermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {

                iniciarEscucha()

            } else {

                btnMicrofono.text =
                    "🎤 Sin permiso"

                micEnabled = false
            }
        }

    // =====================================================
    // ON CREATE
    // =====================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )

        findViewById<android.view.View>(R.id.btnRegresarInicio).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnCambiarCamara).setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
            startCamera()
        }

        val returnToConversation = intent.getBooleanExtra("return_to_conversation", false)
        findViewById<android.widget.Button>(R.id.btnUsarEnConversacion).apply {
            visibility = if (returnToConversation) android.view.View.VISIBLE else android.view.View.GONE
            setOnClickListener {
                val text = conversation.joinToString(" ").replace(Regex("\\s+"), " ").trim()
                if (text.isBlank()) {
                    android.widget.Toast.makeText(this@CameraActivity, "Primero forma una palabra o frase", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    setResult(RESULT_OK, Intent().putExtra("recognized_text", text))
                    finish()
                }
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // =================================================
        // CONECTAR INTERFAZ
        // =================================================

        previewView =
            findViewById(
                R.id.previewView
            )

        // COMPATIBLE evita pantallas negras en varios emuladores, webcams USB
        // y algunos controladores donde PERFORMANCE no crea bien la Surface.
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

        handOverlay =
            findViewById(
                R.id.handOverlay
            )

        txtMano =
            findViewById(
                R.id.txtMano
            )

        txtSena =
            findViewById(
                R.id.txtSena
            )

        txtConfianza =
            findViewById(
                R.id.txtConfianza
            )

        txtConversacion =
            findViewById(
                R.id.txtConversacion
            )

        txtAsistenteCamara = findViewById(R.id.txtAsistenteCamara)
        val settings = getSharedPreferences("signtranslate_settings", MODE_PRIVATE)
        CONF_THRESH = settings.getInt("confidence", 90).coerceIn(70, 100) / 100f
        REQUIRED_STABLE_PREDICTIONS = when (settings.getInt("speed", 1)) {
            0 -> 14
            2 -> 6
            else -> 10
        }

        findViewById<Button>(R.id.btnDeshacer).setOnClickListener {
            if (conversation.isNotEmpty()) conversation.removeAt(conversation.lastIndex)
            lastPrediction = null
            txtConversacion.text = "Texto: ${conversation.joinToString(" ")}"
        }
        findViewById<Button>(R.id.btnEspacio).setOnClickListener {
            if (conversation.isNotEmpty() && conversation.last() != " ") conversation.add(" ")
            lastPrediction = null
            txtConversacion.text = "Texto: ${conversation.joinToString(" ").replace("  ", " ")}"
        }


        txtOrigen = findViewById(R.id.txtOrigenReconocimiento)
        personalSignStore = PersonalSignStore(this)
        dynamicSignStore = DynamicSignStore(this)

        findViewById<Button>(R.id.btnCorregirSena).setOnClickListener {
            val suggested = candidatePrediction ?: lastPrediction
            startActivity(
                Intent(this, TrainingActivity::class.java).apply {
                    suggested?.let { putExtra("label", it) }
                }
            )
        }

        btnMicrofono =
            findViewById(
                R.id.btnMicrofono
            )

        btnLeer =
            findViewById(
                R.id.btnLeer
            )

        btnLimpiar =
            findViewById(
                R.id.btnLimpiar
            )

        findViewById<Button>(R.id.btnLensegua)
            .setOnClickListener {
                startActivity(
                    Intent(
                        this,
                        LenseguaActivity::class.java
                    )
                )
            }

        // =================================================
        // EXECUTOR CÁMARA
        // =================================================

        cameraExecutor =
            Executors
                .newSingleThreadExecutor()

        // =================================================
        // CARGAR IA
        // =================================================

        try {

            loadLabels()

            setupOnnx()

            setupHandLandmarker()

        } catch (e: Exception) {

            e.printStackTrace()

            txtSena.text =
                "Error inicializando IA"
        }

        // =================================================
        // CONFIGURAR VOZ
        // =================================================

        setupTts()

        setupSpeechRecognizer()

        setupButtons()

        // =================================================
        // CÁMARA
        // =================================================

        checkCameraPermission()
    }

    // =====================================================
    // TEXTO A VOZ
    // =====================================================

    private fun setupTts() {

        tts =
            TextToSpeech(this) { status ->

                if (
                    status ==
                    TextToSpeech.SUCCESS
                ) {

                    val result =
                        tts.setLanguage(
                            Locale(
                                "es",
                                "GT"
                            )
                        )

                    if (
                        result ==
                        TextToSpeech.LANG_MISSING_DATA ||
                        result ==
                        TextToSpeech.LANG_NOT_SUPPORTED
                    ) {

                        tts.setLanguage(
                            Locale(
                                "es",
                                "MX"
                            )
                        )
                    }

                    tts.setSpeechRate(
                        0.9f
                    )
                }
            }
    }

    // =====================================================
    // RECONOCIMIENTO DE VOZ
    // =====================================================

    private fun setupSpeechRecognizer() {

        speechRecognizer =
            SpeechRecognizer
                .createSpeechRecognizer(
                    this
                )

        speechIntent =
            Intent(
                RecognizerIntent
                    .ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent
                        .EXTRA_LANGUAGE_MODEL,

                    RecognizerIntent
                        .LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent
                        .EXTRA_LANGUAGE,

                    "es-GT"
                )

                putExtra(
                    RecognizerIntent
                        .EXTRA_PARTIAL_RESULTS,

                    true
                )
            }

        speechRecognizer
            ?.setRecognitionListener(

                object :
                    RecognitionListener {

                    override fun onReadyForSpeech(
                        params: Bundle?
                    ) {

                        btnMicrofono.text =
                            "🎤 Escuchando..."
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(
                        rmsdB: Float
                    ) {}

                    override fun onBufferReceived(
                        buffer: ByteArray?
                    ) {}

                    override fun onEndOfSpeech() {

                        btnMicrofono.text =
                            "🎤 Mic"

                        micEnabled =
                            false
                    }

                    override fun onError(
                        error: Int
                    ) {

                        btnMicrofono.text =
                            "🎤 Mic"

                        micEnabled =
                            false
                    }

                    override fun onResults(
                        results: Bundle?
                    ) {

                        val textos =
                            results
                                ?.getStringArrayList(
                                    SpeechRecognizer
                                        .RESULTS_RECOGNITION
                                )

                        val texto =
                            textos
                                ?.firstOrNull()

                        if (
                            !texto
                                .isNullOrBlank()
                        ) {

                            conversation.add(
                                texto
                            )

                            txtConversacion.text =
                                "Texto: ${
                                    conversation
                                        .joinToString(" ")
                                }"
                        }

                        btnMicrofono.text =
                            "🎤 Mic"

                        micEnabled =
                            false
                    }

                    override fun onPartialResults(
                        partialResults: Bundle?
                    ) {

                        val textos =
                            partialResults
                                ?.getStringArrayList(
                                    SpeechRecognizer
                                        .RESULTS_RECOGNITION
                                )

                        val parcial =
                            textos
                                ?.firstOrNull()

                        if (
                            !parcial
                                .isNullOrBlank()
                        ) {

                            txtSena.text =
                                "Voz: $parcial"
                        }
                    }

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?
                    ) {}
                }
            )
    }

    // =====================================================
    // INICIAR MICRÓFONO
    // =====================================================

    private fun iniciarEscucha() {

        if (
            !SpeechRecognizer
                .isRecognitionAvailable(
                    this
                )
        ) {

            btnMicrofono.text =
                "Voz no disponible"

            return
        }

        micEnabled =
            true

        speechRecognizer
            ?.startListening(
                speechIntent
            )

        btnMicrofono.text =
            "🎤 Escuchando..."
    }

    // =====================================================
    // BOTONES
    // =====================================================

    private fun setupButtons() {

        // =================================================
        // LEER
        // =================================================

        btnLeer
            .setOnClickListener {

                val texto =
                    conversation
                        .joinToString(
                            " "
                        )

                if (
                    texto
                        .isNotBlank()
                ) {

                    tts.speak(
                        texto,
                        TextToSpeech
                            .QUEUE_FLUSH,
                        null,
                        "SIGNSTUDIO_TTS"
                    )
                }
            }

        // =================================================
        // LIMPIAR
        // =================================================

        btnLimpiar
            .setOnClickListener {

                conversation.clear()

                frameBuffer.clear()
                dynamicFrameBuffer.clear()

                lastPrediction =
                    null

                candidatePrediction =
                    null

                candidateCount =
                    0

                lastAcceptedTime =
                    0L

                handOverlay
                    .clearLandmarks()

                txtConversacion.text =
                    "Texto:"

                txtSena.text =
                    "Seña: ---"

                txtConfianza.text =
                    "Confianza: ---"

                if (::txtOrigen.isInitialized) txtOrigen.text = "Origen: esperando seña"
            }

        // =================================================
        // MICRÓFONO
        // =================================================

        btnMicrofono
            .setOnClickListener {

                if (
                    micEnabled
                ) {

                    speechRecognizer
                        ?.cancel()

                    micEnabled =
                        false

                    btnMicrofono.text =
                        "🎤 Mic OFF"

                } else {

                    if (
                        ContextCompat
                            .checkSelfPermission(
                                this,
                                Manifest
                                    .permission
                                    .RECORD_AUDIO
                            ) ==
                        PackageManager
                            .PERMISSION_GRANTED
                    ) {

                        iniciarEscucha()

                    } else {

                        microphonePermissionLauncher
                            .launch(
                                Manifest
                                    .permission
                                    .RECORD_AUDIO
                            )
                    }
                }
            }
    }

    // =====================================================
    // CARGAR LABELS
    // =====================================================

    private fun loadLabels() {

        val jsonText =
            assets
                .open(
                    "labels.json"
                )
                .bufferedReader()
                .use {
                    it.readText()
                }

        val array =
            JSONArray(
                jsonText
            )

        val temp =
            mutableListOf<String>()

        for (
        i in 0 until
                array.length()
        ) {

            var label =
                array
                    .getString(
                        i
                    )

            label =
                label.replace(
                    "\uFEFF",
                    ""
                )

            temp.add(
                label
            )
        }

        labels =
            temp
    }

    // =====================================================
    // ONNX
    // =====================================================

    private fun setupOnnx() {

        ortEnvironment =
            OrtEnvironment
                .getEnvironment()

        val modelBytes =
            assets
                .open(
                    "sign_classifier.onnx"
                )
                .use {
                    it.readBytes()
                }

        ortSession =
            ortEnvironment
                .createSession(
                    modelBytes
                )
    }

    // =====================================================
    // MEDIAPIPE
    // =====================================================

    private fun setupHandLandmarker() {

        val baseOptions =
            BaseOptions
                .builder()
                .setModelAssetPath(
                    "hand_landmarker.task"
                )
                .build()

        val options =
            HandLandmarker
                .HandLandmarkerOptions
                .builder()

                .setBaseOptions(
                    baseOptions
                )

                .setNumHands(
                    2
                )

                .setMinHandDetectionConfidence(
                    0.65f
                )

                .setMinHandPresenceConfidence(
                    0.65f
                )

                .setMinTrackingConfidence(
                    0.65f
                )

                .setRunningMode(
                    RunningMode
                        .LIVE_STREAM
                )

                .setResultListener {
                        result,
                        _ ->

                    processHandResult(
                        result
                    )
                }

                .setErrorListener {
                        error ->

                    error
                        .printStackTrace()

                    runOnUiThread {

                        txtMano.text =
                            "Error MediaPipe"
                    }
                }

                .build()

        handLandmarker =
            HandLandmarker
                .createFromOptions(
                    this,
                    options
                )
    }

    // =====================================================
    // RESULTADOS MEDIAPIPE
    // =====================================================

    private fun processHandResult(
        result: HandLandmarkerResult
    ) {

        val hands =
            result.landmarks()

        // =================================================
        // NO HAY MANOS
        // =================================================

        if (
            hands.isEmpty()
        ) {

            frameBuffer.clear()
            dynamicFrameBuffer.clear()
            lastDynamicPrediction = null

            lastPrediction =
                null

            candidatePrediction =
                null

            candidateCount =
                0

            runOnUiThread {

                handOverlay
                    .clearLandmarks()

                txtMano.text =
                    "Mano: No detectada"
                txtAsistenteCamara.text = "💡 Coloca la mano completa dentro de la cámara"

                txtSena.text =
                    "Seña: ---"

                txtConfianza.text =
                    "Confianza: ---"
            }

            return
        }

        // =================================================
        // DIBUJAR 21 PUNTOS
        // =================================================

        val overlayHands = hands.map { hand ->
            hand.map { landmark -> Pair(landmark.x(), landmark.y()) }
        }

        runOnUiThread {

            handOverlay
                .setHands(overlayHands)

            txtMano.text =
                "Mano: Detectada (${hands.size})"
            txtAsistenteCamara.text = if (dynamicFrameBuffer.size < 48)
                "🟡 Reconociendo pose y preparando movimiento"
            else
                "🟢 Reconocimiento dual: estática + movimiento"
        }

        // =================================================
        // EXTRAER FEATURES
        // =================================================

        val features =
            build126Features(
                result
            )

        lastPersonalPrediction = personalSignStore.predict(features)

        // La secuencia dinámica se conserva durante varios segundos y se analiza
        // con distintas ventanas. Así no es necesario mantener la mano estática.
        dynamicFrameBuffer.addLast(features)
        while (dynamicFrameBuffer.size > DYNAMIC_MAX_FRAMES) dynamicFrameBuffer.removeFirst()
        dynamicTick++
        if (dynamicFrameBuffer.size >= 48 && dynamicTick % 8 == 0) {
            val all = dynamicFrameBuffer.toList()
            val windowSizes = intArrayOf(48, 72, 96)
            lastDynamicPrediction = windowSizes
                .filter { it <= all.size }
                .mapNotNull { size -> dynamicSignStore.predictSequence(all.takeLast(size)) }
                .minByOrNull { it.distance }
        } else if (dynamicFrameBuffer.size < 48) {
            lastDynamicPrediction = null
        }

        frameBuffer
            .addLast(
                features
            )

        while (
            frameBuffer.size >
            T
        ) {

            frameBuffer
                .removeFirst()
        }

        // =================================================
        // ESPERAR 24 FRAMES
        // =================================================

        if (
            frameBuffer.size <
            T
        ) {

            runOnUiThread {

                txtSena.text =
                    "Seña: Analizando..."

                txtConfianza.text =
                    "Frames: ${
                        frameBuffer.size
                    }/$T"
            }

            return
        }

        runClassifier()
    }

    // =====================================================
    // CREAR 126 FEATURES
    // =====================================================

    private fun build126Features(
        result: HandLandmarkerResult
    ): FloatArray {

        val left =
            FloatArray(
                63
            )

        val right =
            FloatArray(
                63
            )

        val hands =
            result.landmarks()

        val handedness =
            result.handedness()

        for (
        i in
        hands.indices
        ) {

            val landmarks =
                hands[i]

            if (
                landmarks.size !=
                21
            ) {

                continue
            }

            val normalized =
                normalizeHand(

                    landmarks.map {

                        Triple(
                            it.x(),
                            it.y(),
                            it.z()
                        )
                    }
                )

            var handName =
                ""

            if (
                i <
                handedness.size &&
                handedness[i]
                    .isNotEmpty()
            ) {

                handName =
                    handedness[i][0]
                        .categoryName()
            }

            if (
                handName.equals(
                    "Left",
                    ignoreCase = true
                )
            ) {
                normalized.copyInto(right)
            } else {
                normalized.copyInto(left)

            }
        }

        val output =
            FloatArray(
                126
            )

        left.copyInto(
            output,
            destinationOffset =
                0
        )

        right.copyInto(
            output,
            destinationOffset =
                63
        )

        return output
    }

    // =====================================================
    // NORMALIZAR MANO
    // =====================================================

    private fun normalizeHand(
        points:
        List<
                Triple<
                        Float,
                        Float,
                        Float
                        >
                >
    ): FloatArray {

        val output =
            FloatArray(
                63
            )

        val xs =
            points.map {
                it.first
            }

        val ys =
            points.map {
                it.second
            }

        val xmin =
            xs.minOrNull()
                ?: 0f

        val xmax =
            xs.maxOrNull()
                ?: 1f

        val ymin =
            ys.minOrNull()
                ?: 0f

        val ymax =
            ys.maxOrNull()
                ?: 1f

        val bw =
            maxOf(
                xmax - xmin,
                0.000001f
            )

        val bh =
            maxOf(
                ymax - ymin,
                0.000001f
            )

        var index =
            0

        for (
        point in
        points
        ) {

            val x =
                (
                        point.first -
                                xmin
                        ) / bw

            val y =
                (
                        point.second -
                                ymin
                        ) / bh

            val z =
                point.third

            output[index++] =
                x

            output[index++] =
                y

            output[index++] =
                z
        }

        return output
    }

    // =====================================================
    // CLASIFICADOR
    // =====================================================

    private fun handleAcceptedPrediction(
        label: String,
        confidence: Float,
        origin: String
    ) {
        // No publicar hipótesis: la pantalla solo cambia al confirmar.
        /* runOnUiThread {
            txtSena.text = "Seña: $label"
            txtConfianza.text = "Coincidencia personal: ${
                String.format(Locale.US, "%.0f", confidence * 100)
            }%"
            txtOrigen.text = "Origen: $origin"
        } */

        if (candidatePrediction == label) {
            candidateCount++
        } else {
            candidatePrediction = label
            candidateCount = 1
        }

        if (candidateCount >= 5) {
            val now = System.currentTimeMillis()
            val cooldownPassed = now - lastAcceptedTime >= ACCEPT_COOLDOWN_MS

            if (lastPrediction != label && cooldownPassed) {
                lastPrediction = label
                lastAcceptedTime = now
                conversation.add(label.replace("_", " "))

                // Si fue una seña con movimiento, iniciar una ventana nueva
                // evita que la cola del gesto anterior vuelva a ganar.
                if (origin.startsWith("Seña con movimiento")) {
                    dynamicFrameBuffer.clear()
                    lastDynamicPrediction = null
                    dynamicTick = 0
                }

                runOnUiThread {
                    txtSena.text = "Seña: $label"
                    txtConfianza.text = "Coincidencia personal: ${String.format(Locale.US, "%.0f", confidence * 100)}%"
                    txtOrigen.text = "Origen: $origin"
                    txtConversacion.text = "Texto: ${conversation.joinToString(" ")}"
                    txtAsistenteCamara.text = "✅ Seña aceptada: ${label.replace("_", " ")}"
                }
            }
        }
    }

    private fun runClassifier() {

        var inputTensor:
                OnnxTensor? =
            null

        var result:
                OrtSession.Result? =
            null

        try {

            val flattened =
                FloatArray(
                    INPUT_DIM
                )

            var offset =
                0

            for (
            frame in
            frameBuffer
            ) {

                frame.copyInto(
                    flattened,
                    destinationOffset =
                        offset
                )

                offset +=
                    FEATURES_PER_FRAME
            }

            // =============================================
            // CREAR TENSOR
            // =============================================

            inputTensor =
                OnnxTensor
                    .createTensor(
                        ortEnvironment,
                        FloatBuffer.wrap(
                            flattened
                        ),
                        longArrayOf(
                            1,
                            INPUT_DIM
                                .toLong()
                        )
                    )

            val inputName =
                ortSession
                    .inputNames
                    .first()

            result =
                ortSession.run(
                    mapOf(
                        inputName
                                to
                                inputTensor
                    )
                )

            // =============================================
            // SALIDA
            // =============================================

            val rawOutput =
                result[0]
                    .value

            val logits =
                when (
                    rawOutput
                ) {

                    is Array<*> -> {

                        @Suppress(
                            "UNCHECKED_CAST"
                        )

                        (
                                rawOutput
                                        as
                                        Array<FloatArray>
                                )[0]
                    }

                    else -> {

                        throw
                        IllegalStateException(
                            "Salida ONNX inesperada"
                        )
                    }
                }

            // =============================================
            // SOFTMAX
            // =============================================

            val probabilities =
                softmax(
                    logits
                )

            var bestIndex =
                0

            var bestConfidence =
                0f

            for (
            i in
            probabilities.indices
            ) {

                if (
                    probabilities[i] >
                    bestConfidence
                ) {

                    bestConfidence =
                        probabilities[i]

                    bestIndex =
                        i
                }
            }

            // =============================================
            // LABEL
            // =============================================

            val label =
                if (
                    bestIndex <
                    labels.size
                ) {

                    labels[
                        bestIndex
                    ]

                } else {

                    "DESCONOCIDO"
                }

            // =============================================
            // ENTRENAMIENTO PERSONAL DEL TELÉFONO
            // =============================================

            val dynamic = lastDynamicPrediction
            if (dynamic != null && dynamicFrameBuffer.size >= 48) {
                handleAcceptedPrediction(
                    label = dynamic.label,
                    confidence = dynamic.confidence,
                    origin = "Seña con movimiento (${dynamic.sequenceCount} secuencias)"
                )
                return
            }

            val personal = lastPersonalPrediction
            if (personal != null) {
                handleAcceptedPrediction(
                    label = personal.label,
                    confidence = personal.confidence,
                    origin = "Entrenamiento personal (${personal.sampleCount} muestras)"
                )
                return
            }

            // =============================================
            // CONFIANZA
            // =============================================

            if (
                bestConfidence >=
                CONF_THRESH &&
                isUsableLabel(label)
            ) {

                runOnUiThread {

                    txtSena.text =
                        "Seña: $label"

                    txtConfianza.text =
                        "Confianza: ${
                            String.format(
                                Locale.US,
                                "%.0f",
                                bestConfidence *
                                        100
                            )
                        }%"

                    txtOrigen.text = "Origen: Modelo IA general"
                }

                // =========================================
                // ESTABILIDAD
                // =========================================

                if (
                    candidatePrediction ==
                    label
                ) {

                    candidateCount++

                } else {

                    candidatePrediction =
                        label

                    candidateCount =
                        1
                }

                // =========================================
                // ACEPTAR SEÑA
                // =========================================

                if (
                    candidateCount >=
                    REQUIRED_STABLE_PREDICTIONS
                ) {

                    val currentTime =
                        System
                            .currentTimeMillis()

                    val cooldownPassed =
                        currentTime -
                                lastAcceptedTime >=
                                ACCEPT_COOLDOWN_MS

                    if (
                        lastPrediction !=
                        label &&
                        cooldownPassed
                    ) {

                        lastPrediction =
                            label

                        lastAcceptedTime =
                            currentTime

                        conversation.add(

                            label.replace(
                                "_",
                                " "
                            )
                        )

                        runOnUiThread {

                            txtConversacion.text =
                                "Texto: ${
                                    conversation
                                        .joinToString(
                                            " "
                                        )
                                }"
                        }
                    }
                }

            } else {

                candidatePrediction =
                    null

                candidateCount =
                    0

                runOnUiThread {

                    txtSena.text =
                        "Seña: No reconocida"

                    txtConfianza.text =
                        "Confianza: ${
                            String.format(
                                Locale.US,
                                "%.0f",
                                bestConfidence *
                                        100
                            )
                        }%"
                }
            }

        } catch (
            e: Exception
        ) {

            e.printStackTrace()

            runOnUiThread {

                txtSena.text =
                    "Error ONNX"
            }

        } finally {

            inputTensor
                ?.close()

            result
                ?.close()
        }
    }

    // =====================================================
    // SOFTMAX
    // =====================================================

    private fun softmax(
        logits:
        FloatArray
    ): FloatArray {

        val max =
            logits
                .maxOrNull()
                ?: 0f

        val expValues =
            FloatArray(
                logits.size
            )

        var sum =
            0.0

        for (
        i in
        logits.indices
        ) {

            val value =
                exp(
                    (
                            logits[i] -
                                    max
                            ).toDouble()
                )

            expValues[i] =
                value
                    .toFloat()

            sum +=
                value
        }

        if (
            sum ==
            0.0
        ) {

            return expValues
        }

        for (
        i in
        expValues.indices
        ) {

            expValues[i] =
                (
                        expValues[i] /
                                sum
                        ).toFloat()
        }

        return expValues
    }

    // =====================================================
    // PERMISO CÁMARA
    // =====================================================

    private fun checkCameraPermission() {

        if (
            ContextCompat
                .checkSelfPermission(
                    this,
                    Manifest
                        .permission
                        .CAMERA
                ) ==
            PackageManager
                .PERMISSION_GRANTED
        ) {

            startCamera()

        } else {

            cameraPermissionLauncher
                .launch(
                    Manifest
                        .permission
                        .CAMERA
                )
        }
    }

    // =====================================================
    // INICIAR CÁMARA
    // =====================================================

    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            analyzeImage(imageProxy)
                        }
                    }

                // Preferimos frontal para conversación. Si el dispositivo/emulador
                // no la expone, usamos automáticamente la trasera disponible.
                val preferred = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                val fallbackFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
                val fallback = CameraSelector.Builder().requireLensFacing(fallbackFacing).build()
                val selector = when {
                    cameraProvider.hasCamera(preferred) -> preferred
                    cameraProvider.hasCamera(fallback) -> { lensFacing = fallbackFacing; fallback }
                    else -> null
                }

                if (selector == null) {
                    txtMano.text = "Cámara: no disponible"
                    txtSena.text = "No se encontró una cámara compatible"
                    return@addListener
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    selector,
                    preview,
                    imageAnalysis
                )

                txtMano.text = "Mano: esperando detección"

            } catch (e: Exception) {
                e.printStackTrace()
                txtMano.text = "Cámara: error"
                txtSena.text = "No se pudo iniciar la cámara"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // =====================================================
    // ANALIZAR IMAGEN
    // =====================================================

    private fun analyzeImage(
        imageProxy:
        ImageProxy
    ) {

        try {

            var bitmap =
                imageProxy
                    .toBitmap()

            val rotationDegrees =
                imageProxy
                    .imageInfo
                    .rotationDegrees

            // =============================================
            // ROTACIÓN
            // =============================================

            if (
                rotationDegrees !=
                0
            ) {

                val rotationMatrix =
                    Matrix()

                rotationMatrix
                    .postRotate(
                        rotationDegrees
                            .toFloat()
                    )

                bitmap =
                    Bitmap
                        .createBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.width,
                            bitmap.height,
                            rotationMatrix,
                            true
                        )
            }

            // =============================================
            // ESPEJO CÁMARA FRONTAL
            // =============================================

            if (true) { // Canonicalizar ambos sensores al espacio espejado del entrenamiento frontal
                val mirrorMatrix = Matrix()
                mirrorMatrix.preScale(-1f, 1f)
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, mirrorMatrix, true)
            }

            // =============================================
            // MEDIAPIPE
            // =============================================

            val mpImage =
                BitmapImageBuilder(
                    bitmap
                ).build()

            val timestamp =
                SystemClock
                    .uptimeMillis()

            handLandmarker
                ?.detectAsync(
                    mpImage,
                    timestamp
                )

        } catch (
            e: Exception
        ) {

            e.printStackTrace()

        } finally {

            imageProxy
                .close()
        }
    }

    // =====================================================
    // CERRAR RECURSOS
    // =====================================================

    override fun onDestroy() {

        super.onDestroy()

        speechRecognizer
            ?.cancel()

        speechRecognizer
            ?.destroy()

        speechRecognizer =
            null

        if (
            ::tts.isInitialized
        ) {

            tts.stop()

            tts.shutdown()
        }

        handLandmarker
            ?.close()

        handLandmarker =
            null

        if (
            ::ortSession
                .isInitialized
        ) {

            ortSession
                .close()
        }

        if (
            ::cameraExecutor
                .isInitialized
        ) {

            cameraExecutor
                .shutdown()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

}