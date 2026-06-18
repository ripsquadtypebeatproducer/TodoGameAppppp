package com.todogame.app.activities

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.todogame.app.R
import com.todogame.app.database.DatabaseHelper
import com.todogame.app.utils.SessionManager
import com.todogame.app.views.BarChartView
import kotlinx.coroutines.*

class StatsActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)
        session = SessionManager(this)
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        loadStats()
    }

    private fun loadStats() {
        val uid = session.getUserId()
        val chart = findViewById<BarChartView>(R.id.barChart)
        scope.launch {
            val weekly = withContext(Dispatchers.IO) { DatabaseHelper.getWeeklyTaskStats(uid) }
            val stats = withContext(Dispatchers.IO) { DatabaseHelper.getProductivityStats(uid) }

            chart.setData(weekly.map { it.label to it.value }, "#7C4DFF")

            findViewById<TextView>(R.id.tvTotalDone).text = (stats["total_done"] ?: 0).toString()
            findViewById<TextView>(R.id.tvWeekDone).text = (stats["week_done"] ?: 0).toString()
            findViewById<TextView>(R.id.tvStreak).text = (stats["streak"] ?: 0).toString()

            val dayNames = listOf("Вс","Пн","Вт","Ср","Чт","Пт","Сб")
            val bestDow = stats["best_dow"] ?: 0
            findViewById<TextView>(R.id.tvBestDay).text =
                if (bestDow in 1..7) dayNames[bestDow - 1] else "—"
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
