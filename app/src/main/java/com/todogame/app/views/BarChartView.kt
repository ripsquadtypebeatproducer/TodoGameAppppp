package com.todogame.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Простой минималистичный столбчатый график (без внешних библиотек).
 * Данные: список пар (метка, значение).
 */
class BarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private var data: List<Pair<String, Int>> = emptyList()
    private var barColor = Color.parseColor("#7C4DFF")
    private var labelColor = Color.parseColor("#9794B0")

    fun setData(d: List<Pair<String, Int>>, barColorHex: String = "#7C4DFF", labelColorInt: Int = Color.parseColor("#9794B0")) {
        data = d
        barColor = Color.parseColor(barColorHex)
        labelColor = labelColorInt
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return
        val w = width.toFloat(); val h = height.toFloat()
        val labelH = h * 0.14f
        val valueH = h * 0.12f
        val chartH = h - labelH - valueH
        val maxVal = (data.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
        val n = data.size
        val slot = w / n
        val barW = slot * 0.5f
        val radius = barW * 0.35f

        textPaint.color = labelColor; textPaint.textSize = h * 0.085f
        valuePaint.color = barColor; valuePaint.textSize = h * 0.085f
        valuePaint.isFakeBoldText = true

        for (i in data.indices) {
            val (label, value) = data[i]
            val cx = slot * i + slot / 2
            val barH = if (maxVal > 0) chartH * (value.toFloat() / maxVal) else 0f
            val top = valueH + (chartH - barH)
            val bottom = valueH + chartH
            val left = cx - barW / 2; val right = cx + barW / 2

            // столбец (минимальная высота, чтобы 0 был виден как точка)
            barPaint.color = if (value > 0) barColor else Color.parseColor("#E0E0E0")
            val effTop = if (value > 0) top else bottom - barW * 0.25f
            canvas.drawRoundRect(left, effTop, right, bottom, radius, radius, barPaint)

            // значение над столбцом
            if (value > 0) canvas.drawText(value.toString(), cx, top - h * 0.02f, valuePaint)
            // метка под столбцом
            canvas.drawText(label, cx, h - h * 0.02f, textPaint)
        }
    }
}
