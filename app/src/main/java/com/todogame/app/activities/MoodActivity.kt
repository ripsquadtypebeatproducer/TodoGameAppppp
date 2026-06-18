package com.todogame.app.activities

import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.todogame.app.R
import com.todogame.app.database.DatabaseHelper
import com.todogame.app.utils.SessionManager
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date

class MoodActivity : AppCompatActivity() {
    private var selectedMood = 3
    private lateinit var session: SessionManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mood)
        session = SessionManager(this)

        val moodBtns = listOf<TextView>(
            findViewById(R.id.mood1), findViewById(R.id.mood2), findViewById(R.id.mood3),
            findViewById(R.id.mood4), findViewById(R.id.mood5)
        )
        val tvLabel  = findViewById<TextView>(R.id.tvMoodLabel)
        val etNote   = findViewById<TextInputEditText>(R.id.etNote)
        val btnSave  = findViewById<Button>(R.id.btnSaveMood)
        val btnSkip  = findViewById<TextView>(R.id.btnSkipMood)

        val labels = listOf("Плохо", "Не очень", "Нормально", "Хорошо", "Отлично")
        val colors = listOf(0xFFEF4444.toInt(), 0xFFF97316.toInt(), 0xFFF59E0B.toInt(),
                            0xFF84CC16.toInt(), 0xFF22C55E.toInt())

        fun select(idx: Int) {
            selectedMood = idx + 1
            moodBtns.forEachIndexed { i, btn ->
                btn.alpha  = if (i == idx) 1f else 0.35f
                btn.scaleX = if (i == idx) 1.15f else 1f
                btn.scaleY = if (i == idx) 1.15f else 1f
            }
            tvLabel.text = labels[idx]
            tvLabel.setTextColor(colors[idx])
        }
        moodBtns.forEachIndexed { i, btn -> btn.setOnClickListener { select(i) } }
        select(2)

        btnSave.setOnClickListener {
            scope.launch {
                withContext(Dispatchers.IO) {
                    DatabaseHelper.saveMood(session.getUserId(), selectedMood, etNote.text.toString())
                }
                val today = SimpleDateFormat("yyyy-MM-dd").format(Date())
                session.setMoodAsked(today)
                Toast.makeText(this@MoodActivity, "Настроение сохранено", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        btnSkip.setOnClickListener { finish() }
    }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
