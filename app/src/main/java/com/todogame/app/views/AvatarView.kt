package com.todogame.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Минималистичный аватар — геометрическая «мордочка» в кружке.
 * Вид задаётся индексом (0..N). Рисуется программно, без картинок.
 * Бесплатные аватары: индексы 0..7. Покупные: 8..11.
 */
class AvatarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var index = 0

    // Пастельные фоны кружка
    private val bgColors = listOf(
        "#7C4DFF", "#FF6B6B", "#4ECDC4", "#45B7D1",
        "#FFA94D", "#A78BFA", "#F472B6", "#34D399",
        "#FBBF24", "#60A5FA", "#FB7185", "#2DD4BF"
    )

    fun setAvatar(i: Int) { index = i.coerceIn(0, bgColors.size - 1); invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        val r = minOf(w, h) / 2f

        // Фон-кружок
        paint.color = Color.parseColor(bgColors[index])
        canvas.drawCircle(cx, cy, r, paint)

        // Внутренняя фигура — разная для каждого индекса (минималистичные «персонажи»)
        paint.color = Color.WHITE
        val s = r // масштаб
        when (index % 12) {
            0 -> { // лис: треугольные ушки + мордочка
                triangle(canvas, cx - s*0.35f, cy - s*0.2f, s*0.3f)
                triangle(canvas, cx + s*0.05f, cy - s*0.2f, s*0.3f)
                canvas.drawCircle(cx, cy + s*0.1f, s*0.32f, paint)
                eyes(canvas, cx, cy, s, Color.parseColor(bgColors[index]))
            }
            1 -> { // кот: острые ушки
                triangle(canvas, cx - s*0.4f, cy - s*0.15f, s*0.28f)
                triangle(canvas, cx + s*0.12f, cy - s*0.15f, s*0.28f)
                canvas.drawCircle(cx, cy + s*0.12f, s*0.34f, paint)
                eyes(canvas, cx, cy + s*0.02f, s, Color.parseColor(bgColors[index]))
            }
            2 -> { // зайчик: длинные ушки
                roundRect(canvas, cx - s*0.28f, cy - s*0.55f, s*0.16f, s*0.5f)
                roundRect(canvas, cx + s*0.12f, cy - s*0.55f, s*0.16f, s*0.5f)
                canvas.drawCircle(cx, cy + s*0.12f, s*0.32f, paint)
                eyes(canvas, cx, cy + s*0.05f, s, Color.parseColor(bgColors[index]))
            }
            3 -> { // мишка: круглые ушки
                canvas.drawCircle(cx - s*0.3f, cy - s*0.3f, s*0.16f, paint)
                canvas.drawCircle(cx + s*0.3f, cy - s*0.3f, s*0.16f, paint)
                canvas.drawCircle(cx, cy + s*0.05f, s*0.36f, paint)
                eyes(canvas, cx, cy, s, Color.parseColor(bgColors[index]))
            }
            4 -> { // звезда
                star(canvas, cx, cy, s*0.5f)
            }
            5 -> { // капля/призрак
                canvas.drawCircle(cx, cy - s*0.05f, s*0.36f, paint)
                eyes(canvas, cx, cy - s*0.05f, s, Color.parseColor(bgColors[index]))
            }
            6 -> { // ромб (кристалл)
                val p = Path().apply {
                    moveTo(cx, cy - s*0.45f); lineTo(cx + s*0.35f, cy)
                    lineTo(cx, cy + s*0.45f); lineTo(cx - s*0.35f, cy); close()
                }
                canvas.drawPath(p, paint)
            }
            7 -> { // сердце
                heart(canvas, cx, cy, s*0.42f)
            }
            8 -> { // орёл: острый клюв (покупной)
                triangle(canvas, cx - s*0.35f, cy - s*0.25f, s*0.28f)
                triangle(canvas, cx + s*0.07f, cy - s*0.25f, s*0.28f)
                canvas.drawCircle(cx, cy + s*0.05f, s*0.33f, paint)
                paint.color = Color.parseColor("#FFB020")
                triangle(canvas, cx - s*0.08f, cy + s*0.12f, s*0.16f)
                paint.color = Color.WHITE
            }
            9 -> { // единорог: рог
                paint.color = Color.parseColor("#FFD93D")
                triangle(canvas, cx - s*0.08f, cy - s*0.55f, s*0.16f)
                paint.color = Color.WHITE
                canvas.drawCircle(cx, cy + s*0.08f, s*0.34f, paint)
                eyes(canvas, cx, cy, s, Color.parseColor(bgColors[index]))
            }
            10 -> { // дракон: гребень
                for (i in -1..1) triangle(canvas, cx + i*s*0.16f - s*0.06f, cy - s*0.5f, s*0.12f)
                canvas.drawCircle(cx, cy + s*0.05f, s*0.34f, paint)
                eyes(canvas, cx, cy, s, Color.parseColor(bgColors[index]))
            }
            else -> { // молния
                val p = Path().apply {
                    moveTo(cx + s*0.1f, cy - s*0.45f); lineTo(cx - s*0.25f, cy + s*0.05f)
                    lineTo(cx, cy + s*0.05f); lineTo(cx - s*0.1f, cy + s*0.45f)
                    lineTo(cx + s*0.28f, cy - s*0.1f); lineTo(cx + s*0.03f, cy - s*0.1f); close()
                }
                canvas.drawPath(p, paint)
            }
        }
    }

    private fun eyes(c: Canvas, cx: Float, cy: Float, s: Float, col: Int) {
        paint.color = col
        c.drawCircle(cx - s*0.11f, cy + s*0.05f, s*0.05f, paint)
        c.drawCircle(cx + s*0.11f, cy + s*0.05f, s*0.05f, paint)
        paint.color = Color.WHITE
    }
    private fun triangle(c: Canvas, x: Float, y: Float, size: Float) {
        val p = Path().apply { moveTo(x, y); lineTo(x + size, y); lineTo(x + size/2, y - size); close() }
        c.drawPath(p, paint)
    }
    private fun roundRect(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        c.drawRoundRect(x, y, x + w, y + h, w/2, w/2, paint)
    }
    private fun star(c: Canvas, cx: Float, cy: Float, r: Float) {
        val p = Path()
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) r else r * 0.45f
            val a = Math.PI / 5 * i - Math.PI / 2
            val x = cx + (rr * Math.cos(a)).toFloat()
            val y = cy + (rr * Math.sin(a)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close(); c.drawPath(p, paint)
    }
    private fun heart(c: Canvas, cx: Float, cy: Float, r: Float) {
        val p = Path()
        p.moveTo(cx, cy + r*0.7f)
        p.cubicTo(cx - r*1.4f, cy - r*0.4f, cx - r*0.5f, cy - r*1.1f, cx, cy - r*0.3f)
        p.cubicTo(cx + r*0.5f, cy - r*1.1f, cx + r*1.4f, cy - r*0.4f, cx, cy + r*0.7f)
        p.close(); c.drawPath(p, paint)
    }
}
