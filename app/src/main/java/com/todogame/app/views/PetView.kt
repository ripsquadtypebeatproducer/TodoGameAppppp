package com.todogame.app.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

/**
 * Минималистичный питомец-маскот с лёгкими анимациями.
 * Рисуется программно (без картинок), реагирует на настроение (0..100).
 * Типы: fox, cat, panda, dragon.
 */
class PetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f               // фаза анимации (дыхание/прыжки)
    private var petType = "fox"
    private var happiness = 80           // 0..100

    private var animator: ValueAnimator? = null

    // Палитра по типам питомца
    private data class PetColors(val body: Int, val ear: Int, val dark: Int)
    private fun colorsFor(type: String): PetColors = when (type) {
        "cat"    -> PetColors(Color.parseColor("#F0997B"), Color.parseColor("#D85A30"), Color.parseColor("#4A1B0C"))
        "panda"  -> PetColors(Color.parseColor("#E8E8E8"), Color.parseColor("#2C2C2A"), Color.parseColor("#2C2C2A"))
        "dragon" -> PetColors(Color.parseColor("#5DCAA5"), Color.parseColor("#0F6E56"), Color.parseColor("#04342C"))
        else     -> PetColors(Color.parseColor("#7F77DD"), Color.parseColor("#534AB7"), Color.parseColor("#26215C")) // fox
    }

    fun setPet(type: String, happiness: Int) {
        this.petType = type
        this.happiness = happiness.coerceIn(0, 100)
        invalidate()
    }

    fun startAnimating() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = if (happiness >= 70) 1400L else if (happiness >= 40) 2600L else 4000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun stopAnimating() { animator?.cancel(); animator = null }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); stopAnimating() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f
        val size = minOf(w, h) * 0.7f
        val c = colorsFor(petType)

        // Вертикальное смещение: счастливый прыгает, грустный оседает
        val bounce: Float = when {
            happiness >= 70 -> -size * 0.06f * sin(phase) - size * 0.02f   // подпрыгивает
            happiness >= 40 -> size * 0.015f * sin(phase)                  // лёгкое покачивание
            else -> size * 0.03f * (1 + sin(phase)) / 2 + size * 0.04f     // оседает вниз
        }
        val cy = h / 2f + bounce

        // Тень
        paint.color = Color.parseColor("#000000"); paint.alpha = 18
        canvas.drawOval(cx - size*0.28f, h*0.5f + size*0.42f, cx + size*0.28f, h*0.5f + size*0.50f, paint)
        paint.alpha = 255

        // Уши
        paint.color = c.ear
        val earY = cy - size*0.40f
        canvas.drawPath(Path().apply {
            moveTo(cx - size*0.26f, earY); lineTo(cx - size*0.34f, earY - size*0.18f)
            lineTo(cx - size*0.10f, earY - size*0.04f); close()
        }, paint)
        canvas.drawPath(Path().apply {
            moveTo(cx + size*0.26f, earY); lineTo(cx + size*0.34f, earY - size*0.18f)
            lineTo(cx + size*0.10f, earY - size*0.04f); close()
        }, paint)

        // Тело
        paint.color = c.body
        canvas.drawOval(cx - size*0.30f, cy - size*0.05f, cx + size*0.30f, cy + size*0.42f, paint)
        // Голова
        canvas.drawCircle(cx, cy - size*0.18f, size*0.27f, paint)

        // Дракончику — гребень
        if (petType == "dragon") {
            paint.color = c.ear
            for (i in -1..1) {
                val sx = cx + i * size*0.08f
                canvas.drawPath(Path().apply {
                    moveTo(sx, cy - size*0.42f); lineTo(sx - size*0.04f, cy - size*0.30f)
                    lineTo(sx + size*0.04f, cy - size*0.30f); close()
                }, paint)
            }
        }
        // Панде — тёмные пятна вокруг глаз
        if (petType == "panda") {
            paint.color = c.dark
            canvas.drawCircle(cx - size*0.10f, cy - size*0.19f, size*0.06f, paint)
            canvas.drawCircle(cx + size*0.10f, cy - size*0.19f, size*0.06f, paint)
        }

        // Глаза
        paint.color = c.dark
        val eyeY = cy - size*0.20f
        if (happiness < 40) {
            // грустные глаза — чёрточки
            paint.strokeWidth = size*0.025f; paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(cx - size*0.14f, eyeY, cx - size*0.06f, eyeY + size*0.02f, paint)
            canvas.drawLine(cx + size*0.14f, eyeY, cx + size*0.06f, eyeY + size*0.02f, paint)
        } else {
            canvas.drawCircle(cx - size*0.10f, eyeY, size*0.035f, paint)
            canvas.drawCircle(cx + size*0.10f, eyeY, size*0.035f, paint)
            // блик
            paint.color = Color.WHITE
            canvas.drawCircle(cx - size*0.085f, eyeY - size*0.012f, size*0.012f, paint)
            canvas.drawCircle(cx + size*0.115f, eyeY - size*0.012f, size*0.012f, paint)
            paint.color = c.dark
        }

        // Рот
        paint.style = Paint.Style.STROKE; paint.strokeWidth = size*0.022f; paint.strokeCap = Paint.Cap.ROUND
        val mouthY = cy - size*0.10f
        val mouthPath = Path()
        if (happiness >= 70) {
            // улыбка
            mouthPath.moveTo(cx - size*0.06f, mouthY)
            mouthPath.quadTo(cx, mouthY + size*0.06f, cx + size*0.06f, mouthY)
        } else if (happiness >= 40) {
            // ровный рот
            mouthPath.moveTo(cx - size*0.05f, mouthY + size*0.02f)
            mouthPath.quadTo(cx, mouthY + size*0.03f, cx + size*0.05f, mouthY + size*0.02f)
        } else {
            // грусть
            mouthPath.moveTo(cx - size*0.05f, mouthY + size*0.04f)
            mouthPath.quadTo(cx, mouthY - size*0.01f, cx + size*0.05f, mouthY + size*0.04f)
        }
        canvas.drawPath(mouthPath, paint)
        paint.style = Paint.Style.FILL

        // Щёчки у счастливого
        if (happiness >= 70) {
            paint.color = Color.parseColor("#D4537E"); paint.alpha = 90
            canvas.drawCircle(cx - size*0.18f, cy - size*0.12f, size*0.04f, paint)
            canvas.drawCircle(cx + size*0.18f, cy - size*0.12f, size*0.04f, paint)
            paint.alpha = 255
        }
        // Слезинка у грустного
        if (happiness < 40) {
            paint.color = Color.parseColor("#85B7EB")
            val tearY = eyeY + size*0.06f + (size*0.03f * ((sin(phase)+1)/2))
            canvas.drawCircle(cx + size*0.13f, tearY, size*0.022f, paint)
        }
    }
}
