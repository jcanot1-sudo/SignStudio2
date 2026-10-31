package com.tu.org.signstudio

import android.content.ActivityNotFoundException
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.text.Normalizer
import java.util.Locale

class ConversationActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var txtChat: TextView
    private lateinit var inputMensaje: EditText
    private lateinit var signPlayer: DynamicSignPlayerView
    private lateinit var txtSignNow: TextView
    private lateinit var txtSignMode: TextView
    private lateinit var dynamicStore: DynamicSignStore
    private lateinit var preinstalledStore: PreinstalledLenseguaStore
    private lateinit var conversationPersonalStore: PersonalSignStore
    private val messages = mutableListOf<String>()
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val handler = Handler(Looper.getMainLooper())
    private var playGeneration = 0

    private lateinit var conversationPreview: PreviewView
    private lateinit var conversationOverlay: HandOverlayView
    private lateinit var txtCameraStatus: TextView
    private lateinit var conversationCameraExecutor: ExecutorService
    private var conversationHandLandmarker: HandLandmarker? = null
    private var conversationLensFacing = CameraSelector.LENS_FACING_FRONT
    private val conversationFrames = ArrayDeque<FloatArray>()
    private var conversationFrameTick = 0
    private var lastCameraAcceptedAt = 0L
    private var lastCameraLabel: String? = null
    private var pendingCameraLabel: String? = null
    private var pendingCameraHits = 0
    private var lastConversationDynamicPrediction: DynamicPrediction? = null
    private var lastDynamicEvaluatedTick = -1
    // Evita confirmar una pose parcial cuando una segunda mano entra en escena.
    private var previousConversationHandCount = 0
    private var twoHandTransitionTick = -1000

    private val conversationCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startConversationCamera() else txtCameraStatus.text = "Cámara sin permiso"
    }

    private val quickPhrases = arrayOf(
        "Hola", "Buenos días", "¿Cómo estás?", "Gracias", "Por favor",
        "Necesito ayuda", "No entiendo", "Repite por favor", "¿Dónde está el baño?",
        "Tengo hambre", "Tengo sed", "Necesito un médico", "Adiós"
    )

    private val signCameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val recognized = result.data?.getStringExtra("recognized_text")?.trim().orEmpty()
            if (recognized.isNotBlank()) {
                addMessage("🤟 Persona LENSEGUA", recognized)
                speak(recognized)
            }
        }
    }

    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            addMessage("🎤 Persona oyente", spoken)
            playSentenceAsSigns(spoken)
        } else if (result.resultCode != RESULT_CANCELED) {
            Toast.makeText(this, "No se obtuvo texto de voz", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        txtChat = findViewById(R.id.txtChat)
        inputMensaje = findViewById(R.id.inputMensaje)
        signPlayer = findViewById(R.id.conversationSignPlayer)
        txtSignNow = findViewById(R.id.txtConversationSignNow)
        txtSignMode = findViewById(R.id.txtConversationSignMode)
        dynamicStore = DynamicSignStore(this)
        preinstalledStore = PreinstalledLenseguaStore(this)
        conversationPersonalStore = PersonalSignStore(this)
        conversationPreview = findViewById(R.id.conversationPreviewView)
        conversationOverlay = findViewById(R.id.conversationHandOverlay)
        txtCameraStatus = findViewById(R.id.txtEstadoCamaraConversacion)
        conversationPreview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        conversationPreview.scaleType = PreviewView.ScaleType.FILL_CENTER
        conversationCameraExecutor = Executors.newSingleThreadExecutor()
        setupConversationHandLandmarker()
        findViewById<Button>(R.id.btnCambiarCamaraConversacion).setOnClickListener {
            conversationLensFacing = if (conversationLensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
            // No mezclar frames de una cámara con la otra.
            conversationFrames.clear()
            lastConversationDynamicPrediction = null
            conversationFrameTick = 0
            previousConversationHandCount = 0
            twoHandTransitionTick = -1000
            pendingCameraLabel = null
            pendingCameraHits = 0
            txtCameraStatus.text = "📷 Cambiando cámara…"
            startConversationCamera()
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startConversationCamera()
        else conversationCameraPermission.launch(Manifest.permission.CAMERA)
        tts = TextToSpeech(this, this)
        loadHistory(); renderChat()

        findViewById<Button>(R.id.btnHablarConversacion).setOnClickListener { launchVoiceRecognition() }
        findViewById<Button>(R.id.btnAgregarMensaje).setOnClickListener {
            val text = inputMensaje.text.toString().trim()
            if (text.isNotEmpty()) {
                // El texto escrito pertenece a la persona oyente; se reproduce como señas.
                addMessage("🎤 Persona oyente", text)
                playSentenceAsSigns(text)
                inputMensaje.text.clear()
            }
        }
        findViewById<Button>(R.id.btnAbrirCamaraConversacion).setOnClickListener {
            signCameraLauncher.launch(Intent(this, CameraActivity::class.java).putExtra("return_to_conversation", true))
        }
        findViewById<Button>(R.id.btnAbrirLenseguaConversacion).setOnClickListener { startActivity(Intent(this, LenseguaActivity::class.java)) }
        findViewById<Button>(R.id.btnLeerUltimoMensaje).setOnClickListener { speakLastMessage() }
        findViewById<Button>(R.id.btnFrasesRapidas).setOnClickListener { showQuickPhrases() }
        findViewById<Button>(R.id.btnMostrarSenas).setOnClickListener { showLastMessageAsSigns() }
        findViewById<Button>(R.id.btnCompartirConversacion).setOnClickListener { shareConversation() }
        findViewById<Button>(R.id.btnLimpiarHistorial).setOnClickListener { messages.clear(); saveHistory(); renderChat() }
        findViewById<Button>(R.id.btnVolverConversacion).setOnClickListener { finish() }
    }

    private fun showQuickPhrases() {
        AlertDialog.Builder(this).setTitle("Frases rápidas").setItems(quickPhrases) { _, which ->
            val phrase = quickPhrases[which]
            inputMensaje.setText(phrase); inputMensaje.setSelection(inputMensaje.text.length)
            addMessage("🎤 Persona oyente", phrase)
            playSentenceAsSigns(phrase)
        }.setNegativeButton("Cancelar", null).show()
    }

    private fun showLastMessageAsSigns() {
        val text = messages.lastOrNull()?.substringAfter(": ")?.trim().orEmpty()
            .ifBlank { inputMensaje.text.toString().trim() }
        if (text.isBlank()) { Toast.makeText(this, "Primero agrega o dicta un mensaje", Toast.LENGTH_SHORT).show(); return }
        playSentenceAsSigns(text)
    }

    /** Reproduce EN ESTA MISMA PANTALLA las secuencias dinámicas disponibles.
     *  Prioridad: seña/frase completa entrenada -> palabra entrenada -> deletreo marcado.
     *  Nunca inventa un movimiento que no exista en el almacén validado/entrenado.
     */
    private fun playSentenceAsSigns(text: String) {
        val generation = ++playGeneration
        val clean = normalize(text)
        val whole = loadMotion(clean)
        if (whole != null) {
            txtSignMode.text = "LENSEGUA • animación vectorial"
            playSequence(clean, whole, generation) { }
            return
        }
        // Segmenta usando "longest match": primero intenta frases/palabras instaladas (p.ej. BUENOS DIAS,
        // NECESITO AYUDA, NO ENTIENDO) y solo después cae a palabras individuales/deletreo.
        val tokens = segmentByInstalledMotions(clean)
        playWordAt(tokens, 0, generation)
    }

    private fun segmentByInstalledMotions(clean: String): List<String> {
        val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var i = 0
        while (i < words.size) {
            var matched: String? = null
            val maxSpan = minOf(5, words.size - i)
            for (span in maxSpan downTo 2) {
                val candidate = words.subList(i, i + span).joinToString(" ")
                if (loadMotion(candidate) != null) { matched = candidate; break }
            }
            if (matched != null) {
                result += matched
                i += matched.split(" ").size
            } else {
                result += words[i]
                i++
            }
        }
        return result
    }

    private fun playWordAt(words: List<String>, index: Int, generation: Int) {
        if (generation != playGeneration) return
        if (index >= words.size) {
            txtSignNow.text = "✓ Traducción visual terminada"
            txtSignMode.text = "Lista para continuar la conversación"
            handler.postDelayed({ if (generation == playGeneration) signPlayer.setFrame(null) }, 700)
            return
        }
        val word = words[index]
        val seq = loadMotion(word)
        if (seq != null) {
            txtSignMode.text = "LENSEGUA • animación vectorial"
            playSequence(word, seq, generation) { playWordAt(words, index + 1, generation) }
        } else {
            // Respaldo seguro: identifica el deletreo requerido. Los movimientos de letras solo se
            // reproducen cuando existe una secuencia LENSEGUA instalada/entrenada para esa letra.
            val letters = word.filter { it.isLetterOrDigit() }.map { it.toString() }
            playLetterAt(word, letters, 0, generation) { playWordAt(words, index + 1, generation) }
        }
    }

    private fun playLetterAt(word: String, letters: List<String>, index: Int, generation: Int, done: () -> Unit) {
        if (generation != playGeneration) return
        if (index >= letters.size) { done(); return }
        val letter = letters[index].uppercase(Locale.ROOT)
        val seq = loadMotion(letter)
        txtSignMode.text = "Deletreo LENSEGUA • $word"
        if (seq != null) {
            playSequence(letter, seq, generation) { playLetterAt(word, letters, index + 1, generation, done) }
        } else {
            // No se dibuja una seña falsa: se informa qué letra falta en el paquete de movimiento.
            txtSignNow.text = "$letter • sin animación instalada (continúa con deletreo disponible)"
            signPlayer.setFrame(null)
            handler.postDelayed({ playLetterAt(word, letters, index + 1, generation, done) }, 500)
        }
    }

    private fun loadMotion(label: String): List<FloatArray>? {
        // Prioridad: paquete LENSEGUA preinstalado validado; después entrenamiento local del usuario.
        return preinstalledStore.load(label) ?: dynamicStore.loadPlaybackSequence(label)
    }

    private fun playSequence(label: String, frames: List<FloatArray>, generation: Int, done: () -> Unit) {
        if (frames.isEmpty() || generation != playGeneration) { done(); return }
        txtSignNow.text = label
        var frameIndex = 0
        val runner = object : Runnable {
            override fun run() {
                if (generation != playGeneration) return
                if (frameIndex >= frames.size) {
                    handler.postDelayed({ if (generation == playGeneration) done() }, 180)
                    return
                }
                signPlayer.setFrame(frames[frameIndex++])
                handler.postDelayed(this, 55L)
            }
        }
        handler.post(runner)
    }

    private fun normalize(value: String): String {
        val upper = value.trim().uppercase(Locale.ROOT).replace("Ñ", "__ENYE__")
        return Normalizer.normalize(upper, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace("__ENYE__", "Ñ")
            .replace(Regex("[^A-Z0-9Ñ ]+"), " ")
            .replace(Regex("\\s+"), " ").trim()
    }

    private fun shareConversation() {
        val content = if (messages.isEmpty()) inputMensaje.text.toString().trim() else messages.joinToString("\n\n")
        if (content.isBlank()) { Toast.makeText(this, "No hay texto para compartir", Toast.LENGTH_SHORT).show(); return }
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, content) }
        try { startActivity(Intent.createChooser(send, "Compartir traducción")) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, "No hay una aplicación para compartir", Toast.LENGTH_SHORT).show() }
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "conversation_sign_voice")
    }
    private fun speakLastMessage() {
        val last = messages.lastOrNull()?.substringAfter(": ")?.trim().orEmpty()
        if (last.isBlank()) { Toast.makeText(this, "Todavía no hay mensajes", Toast.LENGTH_SHORT).show(); return }
        if (!ttsReady) { Toast.makeText(this, "La voz todavía se está preparando", Toast.LENGTH_SHORT).show(); return }
        speak(last)
    }
    private fun launchVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-GT")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla; SignTranslate mostrará la respuesta en señas")
        }
        try { voiceLauncher.launch(intent) } catch (_: Exception) { Toast.makeText(this, "Reconocimiento de voz no disponible", Toast.LENGTH_SHORT).show() }
    }
    private fun addMessage(role: String, message: String) { messages.add("$role: $message"); if (messages.size > 50) messages.removeAt(0); saveHistory(); renderChat() }
    private fun renderChat() { txtChat.text = if (messages.isEmpty()) "La conversación aparecerá aquí.\n\n🤟 Señas → voz   •   🎤 Voz → señas" else messages.joinToString("\n\n") }
    private fun saveHistory() { getSharedPreferences("conversation_history", MODE_PRIVATE).edit().putString("messages", messages.joinToString("\u001E")).apply() }
    private fun loadHistory() { val saved=getSharedPreferences("conversation_history", MODE_PRIVATE).getString("messages", "").orEmpty(); if(saved.isNotBlank()) messages.addAll(saved.split("\u001E").filter{it.isNotBlank()}) }
    override fun onInit(status: Int) { if(status==TextToSpeech.SUCCESS){ val result=tts?.setLanguage(Locale("es","GT")); ttsReady=result!=TextToSpeech.LANG_MISSING_DATA && result!=TextToSpeech.LANG_NOT_SUPPORTED } }
    override fun onDestroy() {
        playGeneration++
        handler.removeCallbacksAndMessages(null)
        conversationHandLandmarker?.close()
        if (::conversationCameraExecutor.isInitialized) conversationCameraExecutor.shutdownNow()
        tts?.stop(); tts?.shutdown()
        super.onDestroy()
    }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    private fun setupConversationHandLandmarker() {
        val base = BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(base).setNumHands(2)
            .setMinHandDetectionConfidence(0.60f).setMinHandPresenceConfidence(0.60f).setMinTrackingConfidence(0.60f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ -> processConversationHands(result) }
            .setErrorListener { runOnUiThread { txtCameraStatus.text = "Error detectando manos" } }
            .build()
        conversationHandLandmarker = HandLandmarker.createFromOptions(this, options)
    }

    private fun startConversationCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val preferred = CameraSelector.Builder().requireLensFacing(conversationLensFacing).build()
                val otherFacing = if (conversationLensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
                val other = CameraSelector.Builder().requireLensFacing(otherFacing).build()
                val selector = when {
                    provider.hasCamera(preferred) -> preferred
                    provider.hasCamera(other) -> { conversationLensFacing = otherFacing; other }
                    else -> null
                }
                if (selector == null) { txtCameraStatus.text = "No hay cámara disponible"; return@addListener }
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(conversationPreview.surfaceProvider) }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(conversationCameraExecutor) { analyzeConversationImage(it) }
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, analysis)

                // Las muestras personales se entrenan con la cámara frontal espejada.
                // En la trasera espejamos también la vista/análisis para conservar el
                // mismo sistema de coordenadas y lateralidad; de lo contrario una seña
                // válida puede quedar invertida y no coincidir con el entrenamiento.
                conversationPreview.scaleX = 1f // La cámara trasera no debe verse en espejo
                txtCameraStatus.text = if (conversationLensFacing == CameraSelector.LENS_FACING_FRONT) "📷 Cámara frontal • muestra tus señas" else "📷 Cámara trasera • reconocimiento compatible con tus señas"
            } catch (e: Exception) { txtCameraStatus.text = "No se pudo iniciar la cámara" }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeConversationImage(imageProxy: ImageProxy) {
        try {
            var bitmap = imageProxy.toBitmap()
            val rotation = imageProxy.imageInfo.rotationDegrees
            if (rotation != 0) {
                val m = Matrix().apply { postRotate(rotation.toFloat()) }
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            }
            // Igual que CameraActivity: solamente espejar la cámara frontal.
            // Las señas dinámicas deben usar la misma geometría que Señas a Texto.
            if (true) { // Canonicalizar ambas cámaras al espacio de referencia frontal
                val mirror = Matrix().apply { preScale(-1f, 1f) }
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, mirror, true)
            }
            conversationHandLandmarker?.detectAsync(BitmapImageBuilder(bitmap).build(), SystemClock.uptimeMillis())
        } catch (_: Exception) { } finally { imageProxy.close() }
    }

    private fun processConversationHands(result: HandLandmarkerResult) {
        val hands = result.landmarks()
        if (hands.isEmpty()) {
            previousConversationHandCount = 0
            twoHandTransitionTick = -1000
            conversationFrameTick = 0
            lastDynamicEvaluatedTick = -1
            conversationFrames.clear()
            lastConversationDynamicPrediction = null
            pendingCameraLabel = null
            pendingCameraHits = 0
            runOnUiThread { conversationOverlay.clearLandmarks(); txtCameraStatus.text = "📷 Coloca una o dos manos dentro de la cámara" }
            return
        }
        val overlayHands = hands.map { hand -> hand.map { Pair(it.x(), it.y()) } }
        val features = buildConversation126(result)
        conversationFrames.addLast(features)
        while (conversationFrames.size > 96) conversationFrames.removeFirst()
        conversationFrameTick++
        // Mantener la secuencia desde la primera mano: NO limpiar conversationFrames.
        // Al entrar la segunda mano, descartar confirmaciones parciales, no los datos.
        if (previousConversationHandCount == 1 && hands.size >= 2) {
            twoHandTransitionTick = conversationFrameTick
            pendingCameraLabel = null
            pendingCameraHits = 0
            lastConversationDynamicPrediction = null
            lastDynamicEvaluatedTick = -1
        }
        previousConversationHandCount = hands.size
        runOnUiThread { conversationOverlay.setHands(overlayHands); txtCameraStatus.text = "🤟 ${hands.size} mano(s) detectada(s) • reconociendo" }

        // Usa las muestras personales existentes. El modo conversación es deliberadamente
        // un poco más tolerante que el modo de entrenamiento, pero confirma el resultado antes
        // de insertarlo para evitar falsos positivos.
        // IMPORTANTE: usar exactamente los mismos stores, valores por defecto y
        // persistencia temporal que CameraActivity. No existe un segundo
        // reconocedor "especial" para conversación.
        val staticPrediction = conversationPersonalStore.predict(features)

        if (conversationFrames.size >= 24 && conversationFrameTick % 4 == 0) {
            lastDynamicEvaluatedTick = conversationFrameTick
            val all = conversationFrames.toList()
            lastConversationDynamicPrediction = intArrayOf(24, 36, 48, 72, 96)
                .filter { it <= all.size }
                .mapNotNull { size -> dynamicStore.predictSequence(all.takeLast(size)) }
                .minByOrNull { it.distance }
        } else if (conversationFrames.size < 24) {
            lastConversationDynamicPrediction = null
        }

        // CameraActivity conserva el último resultado dinámico entre análisis
        // (el matcher dinámico corre cada 8 frames). Hacer lo mismo aquí evita
        // perder HOLA justo cuando termina el movimiento.
        val dynamicPrediction = lastConversationDynamicPrediction?.takeIf {
            conversationFrameTick - lastDynamicEvaluatedTick <= 3
        }

        // MODO CONVERSACIÓN: las letras/números aislados nunca deben ganarle a una
        // palabra o frase entrenada. Una pose intermedia de BUENOS_DIAS puede parecer
        // una A/B/3 durante unos frames; por eso aquí solo publicamos etiquetas de
        // palabra/frase. El deletreo sigue disponible fuera de este flujo y para la
        // salida voz/texto -> señas.
        val dynamicPhrase = dynamicPrediction?.takeUnless { isSingleGlyphLabel(it.label) }
        val staticPhrase = staticPrediction?.takeUnless { isSingleGlyphLabel(it.label) }
        // Con dos manos, no aceptar poses intermedias como frases estáticas.
        val label = dynamicPhrase?.label ?: if (hands.size == 1) staticPhrase?.label else null

        // Dar un margen breve para que se complete la entrada de la segunda mano.
        // Se sigue acumulando movimiento y evaluando dinámicas durante este margen.
        val joiningSecondHand = conversationFrameTick - twoHandTransitionTick < 10
        if (joiningSecondHand) {
            pendingCameraLabel = null
            pendingCameraHits = 0
            runOnUiThread { txtCameraStatus.text = "🤟 Completando seña con ambas manos…" }
        } else if (!label.isNullOrBlank()) {
            confirmCameraConversationLabel(label)
        } else if (conversationFrameTick % 4 == 0) {
            runOnUiThread { txtCameraStatus.text = "🤟 ${hands.size} mano(s) • esperando seña/frase completa…" }
        }
    }


    private fun isSingleGlyphLabel(label: String): Boolean {
        val clean = label.trim().uppercase(Locale.ROOT)
        // A-Z/Ñ y 0-9 son resultados de deletreo/números. En conversación no se
        // publican de inmediato porque interfieren con el inicio de las frases.
        return clean.matches(Regex("^[A-ZÑ0-9]$"))
    }

    private fun confirmCameraConversationLabel(label: String) {
        if (pendingCameraLabel == label) pendingCameraHits++ else {
            pendingCameraLabel = label
            pendingCameraHits = 1
        }
        // Dinámicas se evalúan cada 8 frames; dos coincidencias evitan agregar ruido al chat.
        if (pendingCameraHits >= 3) {
            pendingCameraLabel = null
            pendingCameraHits = 0
            acceptCameraConversationLabel(label)
        } else {
            runOnUiThread { txtCameraStatus.text = "🤟 Analizando seña…" }
        }
    }

    private fun acceptCameraConversationLabel(label: String) {
        val now = SystemClock.uptimeMillis()
        if (label == lastCameraLabel && now - lastCameraAcceptedAt < 1800L) return
        if (now - lastCameraAcceptedAt < 1000L) return
        lastCameraLabel = label
        lastCameraAcceptedAt = now
        // Empezar una secuencia nueva después de aceptar evita repetir la misma seña.
        conversationFrames.clear()
        lastConversationDynamicPrediction = null
        conversationFrameTick = 0
        lastDynamicEvaluatedTick = -1
        previousConversationHandCount = 0
        twoHandTransitionTick = -1000
        pendingCameraLabel = null
        pendingCameraHits = 0
        val cleanLabel = label.replace('_', ' ')
        runOnUiThread {
            addMessage("🤟 Persona LENSEGUA", cleanLabel)
            inputMensaje.setText(cleanLabel)
            inputMensaje.setSelection(inputMensaje.text.length)
            txtCameraStatus.text = "✅ Agregado al chat: $cleanLabel"
            speak(cleanLabel)
        }
    }

    private fun buildConversation126(result: HandLandmarkerResult): FloatArray {
        val left = FloatArray(63); val right = FloatArray(63)
        val hands = result.landmarks(); val handedness = result.handedness()
        for (i in hands.indices) {
            if (hands[i].size != 21) continue
            val pts = hands[i].map { Triple(it.x(), it.y(), it.z()) }
            // IMPORTANTE: usar EXACTAMENTE la misma normalización de CameraActivity.
            // Las muestras guardadas fueron creadas con coordenadas X/Y normalizadas
            // por el bounding box de la mano y Z sin modificar. Antes Conversación
            // usaba muñeca/escala, por lo que nunca coincidía con las muestras.
            val xs = pts.map { it.first }
            val ys = pts.map { it.second }
            val xmin = xs.minOrNull() ?: 0f
            val xmax = xs.maxOrNull() ?: 1f
            val ymin = ys.minOrNull() ?: 0f
            val ymax = ys.maxOrNull() ?: 1f
            val bw = maxOf(xmax - xmin, 0.000001f)
            val bh = maxOf(ymax - ymin, 0.000001f)
            val norm = FloatArray(63)
            var k = 0
            for (p in pts) {
                norm[k++] = (p.first - xmin) / bw
                norm[k++] = (p.second - ymin) / bh
                norm[k++] = p.third
            }
            val name = if (i < handedness.size && handedness[i].isNotEmpty()) handedness[i][0].categoryName() else ""
            // La imagen frontal se espeja antes de MediaPipe; se conserva el mismo orden usado por CameraActivity.
            if (name.equals("Left", true)) norm.copyInto(right) else norm.copyInto(left)
        }
        return FloatArray(126).also { left.copyInto(it,0); right.copyInto(it,63) }
    }


}
