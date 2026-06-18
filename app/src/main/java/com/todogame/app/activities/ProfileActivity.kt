package com.todogame.app.activities

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.todogame.app.R
import com.todogame.app.adapters.AchievementAdapter
import com.todogame.app.database.DatabaseHelper
import com.todogame.app.models.AvatarData
import com.todogame.app.utils.SessionManager
import kotlinx.coroutines.*

class ProfileActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        session = SessionManager(this)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.navPet).setOnClickListener { startActivity(Intent(this, PetActivity::class.java)) }
        findViewById<View>(R.id.navStats).setOnClickListener { startActivity(Intent(this, StatsActivity::class.java)) }
        findViewById<View>(R.id.navShop).setOnClickListener { startActivity(Intent(this, ShopActivity::class.java)) }
        findViewById<View>(R.id.navFriends).setOnClickListener { startActivity(Intent(this, FriendsActivity::class.java)) }
        findViewById<View>(R.id.btnEditAvatar).setOnClickListener {
            startActivity(Intent(this, AvatarPickerActivity::class.java))
        }

        loadProfile()
    }

    private fun loadProfile() {
        scope.launch {
            val userId = session.getUserId()
            val profile = withContext(Dispatchers.IO) { DatabaseHelper.getUserProfile(userId) }
            val achievements = withContext(Dispatchers.IO) { DatabaseHelper.getUserAchievements(userId) }
            val moodLogs = withContext(Dispatchers.IO) { DatabaseHelper.getMoodLogs(userId, 30) }

            // Header
            val avatarId = session.getAvatarId()
            findViewById<com.todogame.app.views.AvatarView>(R.id.profileAvatarView).setAvatar(avatarId)

            findViewById<TextView>(R.id.tvProfileUsername).text = profile["username"]?.toString() ?: ""
            findViewById<TextView>(R.id.tvProfileEmail).text = profile["email"]?.toString() ?: ""
            val role = profile["role"]?.toString() ?: "user"
            val roleView = findViewById<TextView>(R.id.tvProfileRole)
            roleView.text = if (role == "admin") "ADMIN" else "USER"
            roleView.setBackgroundColor(if (role == "admin") 0xFFFF6B6B.toInt() else 0xFF7C4DFF.toInt())

            // Stats
            findViewById<TextView>(R.id.tvTasksCompleted).text = (profile["tasksCompleted"] as? Int)?.toString() ?: "0"
            findViewById<TextView>(R.id.tvTasksActive).text = (profile["tasksActive"] as? Int)?.toString() ?: "0"
            findViewById<TextView>(R.id.tvStreakCount).text = (profile["streak"] as? Int)?.toString() ?: "0"
            findViewById<TextView>(R.id.tvAchievementsCount).text = (profile["achievements"] as? Int)?.toString() ?: "0"
            findViewById<TextView>(R.id.tvMoodLogsCount).text = (profile["moodLogs"] as? Int)?.toString() ?: "0"

            // Mood chart
            if (moodLogs.isNotEmpty()) {
                val chartView = findViewById<MoodChartView>(R.id.moodChartView)
                chartView.setData(moodLogs.map { it.mood to it.tasksCompleted })
                chartView.visibility = View.VISIBLE
                findViewById<TextView>(R.id.tvMoodChartLabel).visibility = View.VISIBLE
            }

            // Achievements
            val rv = findViewById<RecyclerView>(R.id.rvAchievements)
            rv.layoutManager = LinearLayoutManager(this@ProfileActivity)
            rv.adapter = AchievementAdapter(achievements)
        }
    }

    override fun onResume() { super.onResume(); loadProfile() }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}

class MoodChartView @JvmOverloads constructor(context: android.content.Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val moodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3f; style = Paint.Style.STROKE }
    private val taskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f; style = Paint.Style.STROKE; color = 0xFFFFD700.toInt() }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFF7C4DFF.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f; color = 0x88FFFFFF.toInt() }
    private var data: List<Pair<Int, Int>> = emptyList()

    fun setData(d: List<Pair<Int, Int>>) { data = d; invalidate() }

    override fun onDraw(canvas: Canvas) {
        if (data.isEmpty()) return
        val w = width.toFloat(); val h = height.toFloat()
        val pad = 40f; val chartW = w - pad * 2; val chartH = h - pad * 2

        // Mood line (1-5 scale)
        val moodColors = intArrayOf(0xFFEF4444.toInt(), 0xFFF97316.toInt(), 0xFFF59E0B.toInt(), 0xFF84CC16.toInt(), 0xFF22C55E.toInt())
        val pts = data.mapIndexed { i, (mood, _) ->
            val x = pad + i * chartW / (data.size - 1).coerceAtLeast(1)
            val y = pad + chartH - (mood - 1) * chartH / 4f
            x to y
        }
        for (i in 1 until pts.size) {
            val avgMood = ((data[i-1].first + data[i].first) / 2.0).toInt().coerceIn(1,5)
            moodPaint.color = moodColors[avgMood - 1]
            canvas.drawLine(pts[i-1].first, pts[i-1].second, pts[i].first, pts[i].second, moodPaint)
        }
        pts.forEachIndexed { i, (x, y) ->
            dotPaint.color = moodColors[(data[i].first - 1).coerceIn(0,4)]
            canvas.drawCircle(x, y, 6f, dotPaint)
        }
        // Labels
        listOf("😞","😕","😐","😊","🌟").forEachIndexed { i, emoji ->
            canvas.drawText(emoji, 4f, pad + chartH - i * chartH / 4f + 8f, textPaint)
        }
    }
}
