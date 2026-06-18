package com.todogame.app.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.todogame.app.R
import com.todogame.app.utils.SessionManager

class OnboardingActivity : AppCompatActivity() {
    private lateinit var session: SessionManager

    private val slides = listOf(
        Triple("Серия дней", "Выполняй хотя бы одну задачу каждый день и строй непрерывную серию. Чем дольше — тем лучше.", android.R.drawable.ic_menu_today),
        Triple("Привычки", "Создавай ежедневные привычки и следи за прогрессом на тепловой карте за последние 3 месяца.", android.R.drawable.ic_menu_agenda),
        Triple("Вызовы дня", "Каждый день 3 новых вызова. Выполни все три и получи звезду дня в свой профиль.", android.R.drawable.ic_menu_upload)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        if (session.isOnboardingDone()) { goNext(); return }
        setContentView(R.layout.activity_onboarding)

        val vp     = findViewById<ViewPager2>(R.id.viewPager)
        val btnNext= findViewById<Button>(R.id.btnNext)
        val btnSkip= findViewById<TextView>(R.id.btnSkip)
        val dots   = findViewById<LinearLayout>(R.id.dotsContainer)

        vp.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = slides.size
            override fun onCreateViewHolder(p: ViewGroup, t: Int): RecyclerView.ViewHolder {
                val v = LayoutInflater.from(p.context).inflate(R.layout.item_onboarding, p, false)
                return object : RecyclerView.ViewHolder(v) {}
            }
            override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
                h.itemView.findViewById<TextView>(R.id.tvTitle).text = slides[pos].first
                h.itemView.findViewById<TextView>(R.id.tvDesc).text  = slides[pos].second
                h.itemView.findViewById<ImageView>(R.id.ivOnboardIcon).setImageResource(slides[pos].third)
            }
        }

        fun updateDots(cur: Int) {
            dots.removeAllViews()
            val dp = resources.displayMetrics.density
            slides.forEachIndexed { i, _ ->
                View(this).apply {
                    val w = if (i == cur) (24 * dp).toInt() else (8 * dp).toInt()
                    layoutParams = LinearLayout.LayoutParams(w, (8 * dp).toInt()).apply { marginEnd = (6 * dp).toInt() }
                    setBackgroundResource(if (i == cur) R.drawable.dot_active else R.drawable.dot_inactive)
                }.also { dots.addView(it) }
            }
        }

        updateDots(0)
        vp.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(pos: Int) {
                updateDots(pos)
                btnNext.text = if (pos == slides.size - 1) "НАЧАТЬ" else "ДАЛЕЕ"
            }
        })

        btnNext.setOnClickListener {
            if (vp.currentItem < slides.size - 1) vp.currentItem++
            else done()
        }
        btnSkip.setOnClickListener { done() }
    }

    private fun done() { session.setOnboardingDone(); goNext() }
    private fun goNext() {
        startActivity(Intent(this, if (SessionManager(this).isLoggedIn()) MainActivity::class.java else LoginActivity::class.java))
        finish()
    }
}
