package com.tu.org.signstudio

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.random.Random

class LearningActivity : AppCompatActivity() {

    private lateinit var txtPregunta: TextView
    private lateinit var txtPuntuacion: TextView
    private lateinit var txtResultado: TextView
    private lateinit var imgSena: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var btnOpcion1: Button
    private lateinit var btnOpcion2: Button
    private lateinit var btnOpcion3: Button
    private lateinit var btnOpcion4: Button
    private lateinit var btnSiguiente: Button
    private lateinit var btnCambiarModo: Button
    private lateinit var btnVolver: Button

    private var modoNumeros = false
    private var correcta = ""
    private var aciertos = 0
    private var total = 0
    private var answered = false

    private val alphabet = buildList {
        addAll(('A'..'Z').map { it.toString() })
        add(14, "Ñ")
    }
    private val numbers = (0..9).map { it.toString() }

    private val ALPHABET_PDF_URL =
        "https://edu.mineduc.gob.gt/DIGEESP/documents/MaterialesDeApoyo/Abecedario%20en%20Lengua%20de%20Se%C3%B1as%20de%20Guatemala.pdf"

    private val BOOK_PDF_URL =
        "https://edu.mineduc.gob.gt/DIGEESP/documents/MaterialesDeApoyo/Libro%20de%20Lengua%20de%20Se%C3%B1as.pdf"

    @Volatile private var alphabetPageCache: Bitmap? = null
    @Volatile private var numbersPageCache: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learning)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        txtPregunta = findViewById(R.id.txtPreguntaAprendizaje)
        txtPuntuacion = findViewById(R.id.txtPuntuacionAprendizaje)
        txtResultado = findViewById(R.id.txtResultadoAprendizaje)
        imgSena = findViewById(R.id.imgSenaAprendizaje)
        progress = findViewById(R.id.progressAprendizaje)
        btnOpcion1 = findViewById(R.id.btnOpcion1)
        btnOpcion2 = findViewById(R.id.btnOpcion2)
        btnOpcion3 = findViewById(R.id.btnOpcion3)
        btnOpcion4 = findViewById(R.id.btnOpcion4)
        btnSiguiente = findViewById(R.id.btnSiguienteAprendizaje)
        btnCambiarModo = findViewById(R.id.btnCambiarModoAprendizaje)
        btnVolver = findViewById(R.id.btnVolverAprendizaje)

        listOf(btnOpcion1, btnOpcion2, btnOpcion3, btnOpcion4).forEach { button ->
            button.setOnClickListener {
                evaluarRespuesta(button.text.toString())
            }
        }

        btnSiguiente.setOnClickListener { nuevaPregunta() }

        btnCambiarModo.setOnClickListener {
            modoNumeros = !modoNumeros
            btnCambiarModo.text = if (modoNumeros) {
                "Cambiar a letras A–Z/Ñ"
            } else {
                "Cambiar a números 0–9"
            }
            nuevaPregunta()
        }

        btnVolver.setOnClickListener { finish() }

        actualizarPuntuacion()
        nuevaPregunta()
    }

    private fun nuevaPregunta() {
        answered = false
        txtResultado.text = ""
        btnSiguiente.isEnabled = false
        setOptionsEnabled(false)

        val pool = if (modoNumeros) numbers else alphabet
        correcta = pool.random()

        val opciones = mutableSetOf(correcta)
        while (opciones.size < 4) {
            opciones.add(pool.random())
        }

        val shuffled = opciones.shuffled()
        btnOpcion1.text = shuffled[0]
        btnOpcion2.text = shuffled[1]
        btnOpcion3.text = shuffled[2]
        btnOpcion4.text = shuffled[3]

        txtPregunta.text = "Observa la seña y elige la respuesta correcta"

        cargarSenaVisual(correcta)
    }

    private fun evaluarRespuesta(respuesta: String) {
        if (answered) return
        answered = true
        total++

        if (respuesta == correcta) {
            aciertos++
            txtResultado.text = "✅ ¡Correcto! Es $correcta"
        } else {
            txtResultado.text = "❌ Era $correcta"
        }

        actualizarPuntuacion()
        setOptionsEnabled(false)
        btnSiguiente.isEnabled = true
    }

    private fun actualizarPuntuacion() {
        txtPuntuacion.text = "Puntuación: $aciertos / $total"
    }

    private fun setOptionsEnabled(enabled: Boolean) {
        btnOpcion1.isEnabled = enabled
        btnOpcion2.isEnabled = enabled
        btnOpcion3.isEnabled = enabled
        btnOpcion4.isEnabled = enabled
    }

    private fun cargarSenaVisual(token: String) {
        imgSena.visibility = View.GONE
        progress.visibility = View.VISIBLE
        txtResultado.text = ""

        thread {
            try {
                val bitmap = if (modoNumeros) {
                    cropNumberToken(getNumbersPage(), token.toInt())
                } else {
                    cropAlphabetToken(getAlphabetPage(), token)
                }

                runOnUiThread {
                    progress.visibility = View.GONE
                    imgSena.setImageBitmap(bitmap)
                    imgSena.visibility = View.VISIBLE
                    txtResultado.text = ""
                    setOptionsEnabled(true)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    imgSena.visibility = View.GONE
                    txtResultado.text = "No se pudo cargar la seña. Verifica Internet."
                    setOptionsEnabled(false)
                }
            }
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
        val fullCell = cropNormalized(page, box[0], box[1], box[2], box[3])
        return removePrintedLabel(fullCell)
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

    private fun removePrintedLabel(bitmap: Bitmap): Bitmap {
        // La lámina oficial incluye la letra/nombre debajo de la fotografía.
        // En Modo Aprendizaje mostramos únicamente la seña.
        val visibleHeight = (bitmap.height * 0.76f).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            visibleHeight.coerceAtMost(bitmap.height)
        )
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

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
