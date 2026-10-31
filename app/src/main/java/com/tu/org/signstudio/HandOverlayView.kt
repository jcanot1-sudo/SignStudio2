package com.tu.org.signstudio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class HandOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var hands: List<List<Pair<Float, Float>>> = emptyList()

    // Pincel para los puntos
    private val pointPaint = Paint().apply {
        style = Paint.Style.FILL
        strokeWidth = 8f
        isAntiAlias = true
    }

    // Pincel para las líneas
    private val linePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    // Conexiones de los 21 puntos de MediaPipe
    private val connections = listOf(

        // Pulgar
        0 to 1,
        1 to 2,
        2 to 3,
        3 to 4,

        // Índice
        0 to 5,
        5 to 6,
        6 to 7,
        7 to 8,

        // Medio
        5 to 9,
        9 to 10,
        10 to 11,
        11 to 12,

        // Anular
        9 to 13,
        13 to 14,
        14 to 15,
        15 to 16,

        // Meñique
        13 to 17,
        17 to 18,
        18 to 19,
        19 to 20,

        // Palma
        0 to 17
    )

    // =====================================================
    // RECIBIR LOS PUNTOS DE MEDIAPIPE
    // =====================================================

    fun setLandmarks(
        newPoints: List<Pair<Float, Float>>
    ) {

        hands = if (newPoints.size == 21) listOf(newPoints) else emptyList()
        invalidate()
    }

    fun setHands(newHands: List<List<Pair<Float, Float>>>) {
        hands = newHands.filter { it.size == 21 }
        invalidate()
    }

    // =====================================================
    // LIMPIAR LA MANO
    // =====================================================

    fun clearLandmarks() {

        hands = emptyList()

        invalidate()
    }

    // =====================================================
    // DIBUJAR
    // =====================================================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (points in hands) {
            if (points.size != 21) continue
            for (connection in connections) {
                val p1 = points[connection.first]
                val p2 = points[connection.second]
                canvas.drawLine(p1.first * width, p1.second * height, p2.first * width, p2.second * height, linePaint)
            }
            for (point in points) {
                canvas.drawCircle(point.first * width, point.second * height, 10f, pointPaint)
            }
        }
    }
}
