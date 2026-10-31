package com.tu.org.signstudio

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.view.View
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import androidx.appcompat.app.AppCompatActivity

class LenseguaActivity : AppCompatActivity() {
    private lateinit var panelAbecedario: LinearLayout
    private lateinit var panelNumeros: LinearLayout
    private lateinit var inputPalabraLensegua: EditText
    private lateinit var btnTraducirPalabra: Button




    private lateinit var txtFrase: TextView
    private lateinit var txtSecuencia: TextView
    private lateinit var txtLetraActual: TextView
    private lateinit var txtProgreso: TextView
    private lateinit var dynamicPlayer: DynamicSignPlayerView
    private lateinit var dynamicStore: DynamicSignStore
    private var dynamicFrames: List<FloatArray>? = null
    private var dynamicFrameIndex = 0
    private lateinit var btnAnterior: Button
    private lateinit var btnSiguiente: Button
    private lateinit var btnReproducir: Button
    private lateinit var btnReiniciar: Button
    private lateinit var btnReferencia: Button
    private lateinit var imgReferencia: ImageView
    private lateinit var progressReferencia: ProgressBar
    private lateinit var txtReferencia: TextView

    private var sequence: List<String> = emptyList()
    private var currentIndex = 0
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())

    private val ALPHABET_PDF_URL =
        "https://edu.mineduc.gob.gt/DIGEESP/documents/MaterialesDeApoyo/Abecedario%20en%20Lengua%20de%20Se%C3%B1as%20de%20Guatemala.pdf"

    private val BOOK_PDF_URL =
        "https://edu.mineduc.gob.gt/DIGEESP/documents/MaterialesDeApoyo/Libro%20de%20Lengua%20de%20Se%C3%B1as.pdf"

    @Volatile
    private var alphabetPageCache: Bitmap? = null

    @Volatile
    private var numbersPageCache: Bitmap? = null


    private val dynamicRunnable = object : Runnable {
        override fun run() {
            val frames = dynamicFrames ?: return
            if (!isPlaying || frames.isEmpty()) return
            dynamicPlayer.setFrame(frames[dynamicFrameIndex])
            dynamicFrameIndex++
            if (dynamicFrameIndex >= frames.size) dynamicFrameIndex = 0
            handler.postDelayed(this, 70L)
        }
    }

    private val playbackRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying || sequence.isEmpty()) return

            if (currentIndex < sequence.lastIndex) {
                currentIndex++
                renderCurrentToken()
                handler.postDelayed(this, 1300L)
            } else {
                stopPlayback()
                txtProgreso.text = "Secuencia completa • ${sequence.size} elementos"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lensegua)

        panelAbecedario = findViewById(R.id.panelAbecedario)
        panelNumeros = findViewById(R.id.panelNumeros)
        setupAlphabetAndNumbers()

        inputPalabraLensegua = findViewById(R.id.inputPalabraLensegua)
        btnTraducirPalabra = findViewById(R.id.btnTraducirPalabra)

        btnTraducirPalabra.setOnClickListener {
            translateTypedWord()
        }


        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        txtFrase = findViewById(R.id.txtLenseguaFrase)
        txtSecuencia = findViewById(R.id.txtLenseguaSecuencia)
        txtLetraActual = findViewById(R.id.txtLetraActual)
        txtProgreso = findViewById(R.id.txtProgreso)
        dynamicPlayer = findViewById(R.id.dynamicSignPlayer)
        dynamicStore = DynamicSignStore(this)
        btnAnterior = findViewById(R.id.btnAnterior)
        btnSiguiente = findViewById(R.id.btnSiguiente)
        btnReproducir = findViewById(R.id.btnReproducir)
        btnReiniciar = findViewById(R.id.btnReiniciar)
        btnReferencia = findViewById(R.id.btnReferenciaOficial)
        imgReferencia = findViewById(R.id.imgReferenciaOficial)
        progressReferencia = findViewById(R.id.progressReferenciaOficial)
        txtReferencia = findViewById(R.id.txtReferenciaActual)

        val quickPhrases = mapOf(
            R.id.btnHola to "HOLA",
            R.id.btnGracias to "GRACIAS",
            R.id.btnAyuda to "AYUDA",
            R.id.btnSi to "SI",
            R.id.btnNo to "NO",
            R.id.btnPorFavor to "POR FAVOR"
        )

        quickPhrases.forEach { (buttonId, phrase) ->
            findViewById<Button>(buttonId).setOnClickListener {
                showPhrase(phrase)
            }
        }

        intent.getStringExtra("dictionary_word")?.takeIf { it.isNotBlank() }?.let { word ->
            inputPalabraLensegua.setText(word)
            inputPalabraLensegua.post {
                translateTypedWord()
                if (intent.getBooleanExtra("autoplay", false) && sequence.isNotEmpty()) {
                    isPlaying = true
                    btnReproducir.text = "⏸ Pausar"
                    renderCurrentToken()
                    handler.postDelayed(playbackRunnable, 1300L)
                }
            }
        }

        btnAnterior.setOnClickListener {
            stopPlayback()
            if (sequence.isNotEmpty() && currentIndex > 0) {
                currentIndex--
                renderCurrentToken()
            }
        }

        btnSiguiente.setOnClickListener {
            stopPlayback()
            if (sequence.isNotEmpty() && currentIndex < sequence.lastIndex) {
                currentIndex++
                renderCurrentToken()
            }
        }

        btnReproducir.setOnClickListener {
            if (sequence.isEmpty()) return@setOnClickListener

            if (isPlaying) {
                stopPlayback()
            } else {
                if (currentIndex >= sequence.lastIndex) currentIndex = 0
                isPlaying = true
                btnReproducir.text = "⏸ Pausar"
                renderCurrentToken()
                handler.postDelayed(playbackRunnable, 1300L)
            }
        }

        btnReiniciar.setOnClickListener {
            stopPlayback()
            if (sequence.isNotEmpty()) {
                currentIndex = 0
                renderCurrentToken()
            }
        }


        btnReferencia.setOnClickListener {
            if (imgReferencia.visibility == View.VISIBLE) {
                imgReferencia.visibility = View.GONE
                progressReferencia.visibility = View.GONE
                btnReferencia.text = "Ver abecedario completo MINEDUC"
            } else {
                btnReferencia.text = "Ocultar referencia completa"
                loadOfficialAlphabetImage()
            }
        }

        findViewById<Button>(R.id.btnVolver).setOnClickListener {
            finish()
        }

        showPhrase("")
    }

    private fun showPhrase(phrase: String) {
        stopPlayback()

        if (phrase.isBlank()) {
            sequence = emptyList()
            currentIndex = 0
            txtFrase.text = "Selecciona una frase"
            txtSecuencia.text = "Deletreo: ---"
            txtLetraActual.text = "—"
            txtProgreso.text = "Sin secuencia"
            txtReferencia.text = "Referencia oficial: selecciona una letra"
            updateButtons()
            return
        }

        txtFrase.text = "Texto: $phrase"
        sequence = buildSpellingSequence(phrase)
        currentIndex = 0

        txtSecuencia.text = "Deletreo LENSEGUA de apoyo:\n${sequence.joinToString(" → ")}"
        renderCurrentToken()
    }

    private fun buildSpellingSequence(phrase: String): List<String> =
        phrase.uppercase().mapNotNull { ch ->
            when {
                ch == 'Ñ' -> "Ñ"
                ch in 'A'..'Z' -> ch.toString()
                ch == ' ' -> "ESPACIO"
                else -> null
            }
        }

    private fun renderCurrentToken() {
        if (sequence.isEmpty()) {
            txtLetraActual.text = "—"
            txtProgreso.text = "Sin secuencia"
            txtReferencia.text = "Referencia oficial: selecciona una letra o número"
            imgReferencia.visibility = View.GONE
            progressReferencia.visibility = View.GONE
            updateButtons()
            return
        }

        val token = sequence[currentIndex]
        txtLetraActual.text = if (token == "ESPACIO") "␣" else token
        txtProgreso.text = "Elemento ${currentIndex + 1} de ${sequence.size}"
        dynamicFrames = dynamicStore.loadPlaybackSequence(token)
        dynamicFrameIndex = 0
        if (!dynamicFrames.isNullOrEmpty()) {
            dynamicPlayer.visibility = View.VISIBLE
            imgReferencia.visibility = View.GONE
            progressReferencia.visibility = View.GONE
            txtReferencia.text = "Seña con movimiento • $token • entrenamiento local"
            dynamicPlayer.setFrame(dynamicFrames!!.first())
            if (isPlaying) { handler.removeCallbacks(dynamicRunnable); handler.post(dynamicRunnable) }
            updateButtons()
            return
        } else {
            dynamicPlayer.visibility = View.GONE
            dynamicPlayer.setFrame(null)
        }

        when {
            token == "ESPACIO" -> {
                progressReferencia.visibility = View.GONE
                imgReferencia.visibility = View.GONE
                txtReferencia.text = "Espacio entre palabras"
            }
            token.length == 1 && (token[0] in 'A'..'Z' || token == "Ñ") -> {
                txtReferencia.text = "Seña visual LENSEGUA • letra $token • MINEDUC / DIGEESP"
                loadVisualForAlphabetToken(token)
            }
            token.length == 1 && token[0].isDigit() -> {
                txtReferencia.text = "Seña visual LENSEGUA • número $token • material LENSEGUA"
                loadVisualForNumberToken(token.toInt())
            }
            else -> {
                progressReferencia.visibility = View.GONE
                imgReferencia.visibility = View.GONE
                txtReferencia.text = "Referencia visual no disponible para $token"
            }
        }

        updateButtons()
    }

    private fun updateButtons() {
        val hasSequence = sequence.isNotEmpty()
        btnAnterior.isEnabled = hasSequence && currentIndex > 0
        btnSiguiente.isEnabled = hasSequence && currentIndex < sequence.lastIndex
        btnReproducir.isEnabled = hasSequence
        btnReiniciar.isEnabled = hasSequence
    }

    private fun stopPlayback() {
        isPlaying = false
        handler.removeCallbacks(playbackRunnable)
        handler.removeCallbacks(dynamicRunnable)
        if (::btnReproducir.isInitialized) {
            btnReproducir.text = "▶ Reproducir"
        }
    }

    private fun loadOfficialAlphabetImage() {
        imgReferencia.visibility = View.GONE
        progressReferencia.visibility = View.VISIBLE
        txtReferencia.text = "Cargando lámina oficial completa MINEDUC…"

        thread {
            try {
                val page = getAlphabetPage()
                runOnUiThread {
                    progressReferencia.visibility = View.GONE
                    imgReferencia.setImageBitmap(page)
                    imgReferencia.visibility = View.VISIBLE
                    txtReferencia.text = "Abecedario oficial LENSEGUA • MINEDUC / DIGEESP"
                }
            } catch (e: Exception) {
                showVisualError("No se pudo cargar la lámina oficial. Verifica Internet.")
            }
        }
    }

    private fun loadVisualForAlphabetToken(token: String) {
        imgReferencia.visibility = View.GONE
        progressReferencia.visibility = View.VISIBLE

        thread {
            try {
                val fullPage = getAlphabetPage()
                val cropped = cropAlphabetToken(fullPage, token)
                runOnUiThread {
                    progressReferencia.visibility = View.GONE
                    imgReferencia.setImageBitmap(cropped)
                    imgReferencia.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                showVisualError("No se pudo cargar la seña visual de $token.")
            }
        }
    }

    private fun loadVisualForNumberToken(number: Int) {
        imgReferencia.visibility = View.GONE
        progressReferencia.visibility = View.VISIBLE

        thread {
            try {
                val numbersPage = getNumbersPage()
                val cropped = cropNumberToken(numbersPage, number)
                runOnUiThread {
                    progressReferencia.visibility = View.GONE
                    imgReferencia.setImageBitmap(cropped)
                    imgReferencia.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                showVisualError(
                    "No se pudo cargar la seña visual del número $number. " +
                    "Puedes seguir usando el número y reintentar con Internet."
                )
            }
        }
    }

    private fun showVisualError(message: String) {
        runOnUiThread {
            progressReferencia.visibility = View.GONE
            imgReferencia.visibility = View.GONE
            txtReferencia.text = message
        }
    }

    @Synchronized
    private fun getAlphabetPage(): Bitmap {
        alphabetPageCache?.let { return it }
        val pdf = ensureDownloadedPdf(
            File(cacheDir, "abecedario_lensegua_mineduc.pdf"),
            ALPHABET_PDF_URL
        )
        val bitmap = renderPdfPage(pdf, 0, 1600)
        alphabetPageCache = bitmap
        return bitmap
    }

    @Synchronized
    private fun getNumbersPage(): Bitmap {
        numbersPageCache?.let { return it }
        val pdf = ensureDownloadedPdf(
            File(cacheDir, "libro_lensegua_mineduc.pdf"),
            BOOK_PDF_URL
        )

        // En el libro LENSEGUA los números 0-11 aparecen en la página impresa 6.
        // El PDF incluye portada e introducción antes de la numeración.
        val descriptor = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        val preferredIndex = if (renderer.pageCount > 7) 7 else (renderer.pageCount - 1).coerceAtLeast(0)
        renderer.close()
        descriptor.close()

        val bitmap = renderPdfPage(pdf, preferredIndex, 1200)
        numbersPageCache = bitmap
        return bitmap
    }

    private fun ensureDownloadedPdf(file: File, url: String): File {
        if (file.exists() && file.length() > 0L) return file

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 45000
            requestMethod = "GET"
            instanceFollowRedirects = true
        }

        connection.connect()
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${connection.responseCode}")
        }

        connection.inputStream.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        connection.disconnect()
        return file
    }

    private fun renderPdfPage(file: File, pageIndex: Int, targetWidth: Int): Bitmap {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        val safeIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
        val page = renderer.openPage(safeIndex)

        val scale = targetWidth.toFloat() / page.width.toFloat()
        val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        page.close()
        renderer.close()
        descriptor.close()
        return bitmap
    }

    private fun cropAlphabetToken(page: Bitmap, token: String): Bitmap {
        // Coordenadas normalizadas tomadas de la lámina oficial de una página.
        val boxes = mapOf(
            "A" to floatArrayOf(.048f,.095f,.150f,.297f),
            "B" to floatArrayOf(.163f,.095f,.266f,.297f),
            "C" to floatArrayOf(.278f,.095f,.380f,.297f),
            "D" to floatArrayOf(.390f,.095f,.493f,.297f),

            "E" to floatArrayOf(.048f,.313f,.150f,.513f),
            "F" to floatArrayOf(.163f,.313f,.266f,.513f),
            "G" to floatArrayOf(.278f,.313f,.380f,.513f),
            "H" to floatArrayOf(.390f,.313f,.493f,.513f),

            "I" to floatArrayOf(.048f,.530f,.150f,.730f),
            "J" to floatArrayOf(.163f,.530f,.266f,.730f),
            "K" to floatArrayOf(.278f,.530f,.380f,.730f),
            "L" to floatArrayOf(.390f,.530f,.493f,.730f),

            "M" to floatArrayOf(.163f,.745f,.266f,.945f),
            "N" to floatArrayOf(.278f,.745f,.380f,.945f),
            "Ñ" to floatArrayOf(.390f,.745f,.493f,.945f),

            "O" to floatArrayOf(.515f,.095f,.618f,.297f),
            "P" to floatArrayOf(.627f,.095f,.730f,.297f),
            "Q" to floatArrayOf(.740f,.095f,.843f,.297f),
            "R" to floatArrayOf(.850f,.095f,.955f,.297f),

            "S" to floatArrayOf(.627f,.313f,.730f,.513f),
            "T" to floatArrayOf(.740f,.313f,.843f,.513f),
            "U" to floatArrayOf(.850f,.313f,.955f,.513f),

            "V" to floatArrayOf(.515f,.530f,.618f,.730f),
            "W" to floatArrayOf(.627f,.530f,.730f,.730f),
            "X" to floatArrayOf(.740f,.530f,.843f,.730f),
            "Y" to floatArrayOf(.850f,.530f,.955f,.730f),

            "Z" to floatArrayOf(.515f,.745f,.618f,.945f)
        )

        val box = boxes[token] ?: return page
        return cropNormalized(page, box[0], box[1], box[2], box[3])
    }

    private fun cropNumberToken(page: Bitmap, number: Int): Bitmap {
        // Página oficial "LOS NÚMEROS": 0–11 organizados en 3 columnas x 4 filas.
        val row = number / 3
        val col = number % 3

        val leftMargin = 0.055f
        val rightMargin = 0.055f
        val top = 0.115f
        val bottom = 0.925f
        val gapX = 0.015f
        val gapY = 0.010f

        val totalWidth = 1f - leftMargin - rightMargin - (gapX * 2)
        val cellWidth = totalWidth / 3f
        val totalHeight = bottom - top - (gapY * 3)
        val cellHeight = totalHeight / 4f

        val cellX0 = leftMargin + col * (cellWidth + gapX)
        val cellY0 = top + row * (cellHeight + gapY)

        // Dentro de cada celda dejamos únicamente la fotografía de la mano.
        // Se elimina la franja superior/inferior donde aparece el nombre impreso.
        val x0 = cellX0 + cellWidth * 0.07f
        val x1 = cellX0 + cellWidth * 0.93f
        val y0 = cellY0 + cellHeight * 0.12f
        val y1 = cellY0 + cellHeight * 0.76f

        return cropNormalized(page, x0, y0, x1, y1)
    }

    private fun cropNormalized(
        bitmap: Bitmap,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): Bitmap {
        val x = (bitmap.width * left).toInt().coerceIn(0, bitmap.width - 1)
        val y = (bitmap.height * top).toInt().coerceIn(0, bitmap.height - 1)
        val x2 = (bitmap.width * right).toInt().coerceIn(x + 1, bitmap.width)
        val y2 = (bitmap.height * bottom).toInt().coerceIn(y + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, x, y, x2 - x, y2 - y)
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }


    private fun setupAlphabetAndNumbers() {
        val letras = ('A'..'Z').map { it.toString() }.toMutableList()
        letras.add(14, "Ñ")

        letras.forEach { letra ->
            panelAbecedario.addView(createSymbolButton(letra) {
                showCustomSequence(letra)
            })
        }

        (0..9).forEach { numero ->
            val value = numero.toString()
            panelNumeros.addView(createSymbolButton(value) {
                showCustomSequence(value)
            })
        }
    }

    private fun createSymbolButton(text: String, action: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 16f
            minWidth = dp(54)
            minHeight = dp(48)
            setPadding(dp(10), 0, dp(10), 0)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = dp(6)
            layoutParams = params
            setOnClickListener { action() }
        }
    }

    private fun showCustomSequence(value: String) {
        stopPlayback()
        sequence = value.map { it.toString() }
        currentIndex = 0
        txtFrase.text = if (value.firstOrNull()?.isDigit() == true) {
            "Número seleccionado: $value"
        } else {
            "Letra seleccionada: $value"
        }
        txtSecuencia.text = "Referencia visual LENSEGUA"
        renderCurrentToken()
        updateButtons()
    }

    private fun translateTypedWord() {
        val raw = inputPalabraLensegua.text?.toString().orEmpty().trim()

        if (raw.isBlank()) {
            Toast.makeText(
                this,
                "Escribe una palabra para traducir.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val normalized = raw
            .uppercase()
            .filter { it == 'Ñ' || it in 'A'..'Z' || it == ' ' }

        if (normalized.isBlank()) {
            Toast.makeText(
                this,
                "La palabra no contiene letras válidas A-Z/Ñ.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        stopPlayback()
        val trained = dynamicStore.trainedLabels().keys.associateBy { it.uppercase() }
        sequence = normalized.split(Regex("\\s+")).flatMap { word ->
            val trainedLabel = trained[word]
            if (trainedLabel != null) listOf(trainedLabel) else buildSpellingSequence(word) + listOf("ESPACIO")
        }.dropLastWhile { it == "ESPACIO" }
        currentIndex = 0

        txtFrase.text = "Frase: $normalized"
        txtSecuencia.text = "Movimiento cuando existe una seña entrenada; deletreo como respaldo:\n${sequence.joinToString(" → ")}"

        renderCurrentToken()
        startAutomaticPlayback()
    }

    private fun startAutomaticPlayback() {
        if (sequence.isEmpty()) return

        stopPlayback()
        if (currentIndex >= sequence.lastIndex) {
            currentIndex = 0
        }

        isPlaying = true
        btnReproducir.text = "⏸ Pausar"
        renderCurrentToken()
        handler.postDelayed(playbackRunnable, 1600L)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

}
