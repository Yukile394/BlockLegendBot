package com.silvera.blocklegendbot

import android.content.Context
import android.graphics.*
import android.view.View

/**
 * Şeffaf canvas overlay.
 * EspEngine'den gelen Box listesini ekrana çizer.
 * WindowManager'a TYPE_APPLICATION_OVERLAY ile eklenir.
 */
class EspOverlayView(context: Context) : View(context) {

    data class EspBox(
        val rect: RectF,
        val label: String,
        val color: Int
    )

    // EspEngine bu listeyi günceller, invalidate() tetikler
    var boxes: List<EspBox> = emptyList()
        set(value) { field = value; postInvalidate() }

    // ── Paint nesneleri ───────────────────────────────────────────────
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.WHITE
        textSize  = 28f
        typeface  = Typeface.DEFAULT_BOLD
        setShadowLayer(4f, 1f, 1f, Color.BLACK)
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style     = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        boxes.forEach { box -> drawEspBox(canvas, box) }
    }

    // ── Box çizimi ────────────────────────────────────────────────────
    private fun drawEspBox(canvas: Canvas, box: EspBox) {
        val r = box.rect
        val c = box.color

        // Hafif iç dolgu
        fillPaint.color = Color.argb(30, Color.red(c), Color.green(c), Color.blue(c))
        canvas.drawRect(r, fillPaint)

        // Ana çerçeve
        boxPaint.color = c
        canvas.drawRect(r, boxPaint)

        // Köşe vurguları (CS tarzı)
        cornerPaint.color = Color.WHITE
        val cs = minOf(r.width(), r.height()) * 0.18f
        drawCorners(canvas, r, cs)

        // Label
        if (box.label.isNotEmpty()) {
            val tx = r.left + 4f
            val ty = r.top - 6f
            canvas.drawText(box.label, tx, ty, textPaint)
        }
    }

    private fun drawCorners(canvas: Canvas, r: RectF, size: Float) {
        // Sol-üst
        canvas.drawLine(r.left,        r.top,          r.left + size, r.top,          cornerPaint)
        canvas.drawLine(r.left,        r.top,          r.left,        r.top + size,   cornerPaint)
        // Sağ-üst
        canvas.drawLine(r.right,       r.top,          r.right - size,r.top,          cornerPaint)
        canvas.drawLine(r.right,       r.top,          r.right,       r.top + size,   cornerPaint)
        // Sol-alt
        canvas.drawLine(r.left,        r.bottom,       r.left + size, r.bottom,       cornerPaint)
        canvas.drawLine(r.left,        r.bottom,       r.left,        r.bottom - size,cornerPaint)
        // Sağ-alt
        canvas.drawLine(r.right,       r.bottom,       r.right - size,r.bottom,       cornerPaint)
        canvas.drawLine(r.right,       r.bottom,       r.right,       r.bottom - size,cornerPaint)
    }
}
