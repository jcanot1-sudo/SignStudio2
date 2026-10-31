package com.tu.org.signstudio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * Reproductor humanizado para las secuencias locales.
 * Las grabaciones antiguas contienen landmarks normalizados de una/dos manos (126 valores),
 * por lo que el cuerpo es una referencia visual estable y las manos conservan su forma/movimiento.
 */
class DynamicSignPlayerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private var frame: FloatArray? = null

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF334155.toInt(); strokeWidth = 5f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val skin = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF0C7A5.toInt(); style = Paint.Style.FILL }
    private val shirt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDDE7F5.toInt(); style = Paint.Style.FILL }
    private val handLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5B4A91.toInt(); strokeWidth = 6f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val joint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF334155.toInt(); style = Paint.Style.FILL }
    private val connections = intArrayOf(0,1,1,2,2,3,3,4, 0,5,5,6,6,7,7,8, 5,9,9,10,10,11,11,12, 9,13,13,14,14,15,15,16, 13,17,17,18,18,19,19,20, 0,17)

    fun setFrame(values: FloatArray?) { frame = values; invalidate() }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val cx = width * .5f
        val headY = height * .18f
        val headR = minOf(width, height) * .075f
        val shoulderY = height * .37f
        drawHumanBase(c, cx, headY, headR, shoulderY)

        val f = frame
        if (f == null || f.size < 63) {
            drawNeutralArms(c, shoulderY)
            return
        }

        val hasLeft = hasHand(f, 0)
        val hasRight = f.size >= 126 && hasHand(f, 63)
        val leftWrist = if (hasLeft) drawHand(c, f, 0, true) else null
        val rightWrist = if (hasRight) drawHand(c, f, 63, false) else null
        drawArm(c, width * .35f, shoulderY, leftWrist ?: floatArrayOf(width*.30f, height*.62f))
        drawArm(c, width * .65f, shoulderY, rightWrist ?: floatArrayOf(width*.70f, height*.62f))
    }

    private fun drawHumanBase(c: Canvas, cx: Float, headY: Float, r: Float, shoulderY: Float) {
        c.drawCircle(cx, headY, r, skin); c.drawCircle(cx, headY, r, outline)
        c.drawLine(cx-r*.35f, headY+r*.15f, cx-r*.12f, headY+r*.15f, outline)
        c.drawLine(cx+r*.12f, headY+r*.15f, cx+r*.35f, headY+r*.15f, outline)
        c.drawLine(cx-r*.18f, headY+r*.48f, cx+r*.18f, headY+r*.48f, outline)
        c.drawLine(cx, headY+r, cx, shoulderY-r*.10f, outline)
        val torso = Path().apply {
            moveTo(width*.34f, shoulderY); quadTo(cx, height*.31f, width*.66f, shoulderY)
            lineTo(width*.62f, height*.92f); lineTo(width*.38f, height*.92f); close()
        }
        c.drawPath(torso, shirt); c.drawPath(torso, outline)
    }

    private fun drawNeutralArms(c: Canvas, shoulderY: Float) {
        drawArm(c, width*.35f, shoulderY, floatArrayOf(width*.30f, height*.63f))
        drawArm(c, width*.65f, shoulderY, floatArrayOf(width*.70f, height*.63f))
        c.drawCircle(width*.30f, height*.63f, 15f, skin); c.drawCircle(width*.70f, height*.63f, 15f, skin)
    }

    private fun drawArm(c: Canvas, sx: Float, sy: Float, wrist: FloatArray) {
        val elbowX = (sx + wrist[0]) * .5f + if (wrist[0] < width*.5f) -width*.035f else width*.035f
        val elbowY = (sy + wrist[1]) * .5f
        c.drawLine(sx, sy, elbowX, elbowY, outline)
        c.drawLine(elbowX, elbowY, wrist[0], wrist[1], outline)
        c.drawCircle(elbowX, elbowY, 7f, skin)
    }

    private fun hasHand(f: FloatArray, off: Int): Boolean {
        if (f.size < off + 63) return false
        var energy = 0f
        for (i in off until off + 63) energy += kotlin.math.abs(f[i])
        return energy > .05f
    }

    /** Dibuja la mano dentro de una zona corporal estable; evita que ocupe toda la pantalla. */
    private fun drawHand(c: Canvas, f: FloatArray, off: Int, leftSide: Boolean): FloatArray {
        val boxW = width * .30f
        val boxH = height * .34f
        val originX = if (leftSide) width * .20f else width * .50f
        val originY = height * .34f
        val pts = Array(21) { i ->
            val x = f.getOrElse(off+i*3){.5f}.coerceIn(0f,1f)
            val y = f.getOrElse(off+i*3+1){.5f}.coerceIn(0f,1f)
            floatArrayOf(originX + x*boxW, originY + y*boxH)
        }
        var k=0
        while(k<connections.size){ val a=pts[connections[k]]; val b=pts[connections[k+1]]; c.drawLine(a[0],a[1],b[0],b[1],handLine); k+=2 }
        pts.forEachIndexed { i,p -> c.drawCircle(p[0],p[1], if(i==0) 8f else 5f, if(i==0) skin else joint) }
        return pts[0]
    }
}
