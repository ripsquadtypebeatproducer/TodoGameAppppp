package com.todogame.app.activities

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.todogame.app.R
import com.todogame.app.models.ThemeData
import com.todogame.app.utils.SessionManager
import com.todogame.app.workers.MoodReminderReceiver
import java.util.Calendar

class SettingsActivity : AppCompatActivity() {
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        session = SessionManager(this)

        val switchDark  = findViewById<Switch>(R.id.switchDarkTheme)
        val switchNotif = findViewById<Switch>(R.id.switchMoodNotif)

        switchDark.isChecked  = session.getTheme() == ThemeData.THEME_DARK
        switchNotif.isChecked = true

        switchDark.setOnCheckedChangeListener { _, isDark ->
            val newTheme = if (isDark) ThemeData.THEME_DARK else ThemeData.THEME_LIGHT
            session.setTheme(newTheme)
            // Apply immediately
            AppCompatDelegate.setDefaultNightMode(
                if (isDark) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
            // Restart all activities to apply new theme
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }

        switchNotif.setOnCheckedChangeListener { _, isOn ->
            if (isOn) scheduleMoodReminder() else cancelMoodReminder()
        }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // Task 1: Trash access
        findViewById<Button>(R.id.btnTrash).setOnClickListener {
            startActivity(Intent(this, TrashActivity::class.java))
        }
        // Categories management
        findViewById<Button>(R.id.btnCategories).setOnClickListener {
            startActivity(Intent(this, CategoriesActivity::class.java))
        }
        // Task 4: Admin panel — visible only for admin role
        val btnAdmin = findViewById<Button>(R.id.btnAdmin)
        if (session.getRole() == "admin") {
            btnAdmin.visibility = android.view.View.VISIBLE
            btnAdmin.setOnClickListener {
                startActivity(Intent(this, AdminActivity::class.java))
            }
        }

        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            session.clearSession()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }

        scheduleMoodReminder()
    }

    private fun scheduleMoodReminder() {
        val intent = PendingIntent.getBroadcast(this, 0,
            Intent(this, MoodReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        if (cal.before(Calendar.getInstance())) cal.add(Calendar.DATE, 1)
        getSystemService(AlarmManager::class.java)
            ?.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, intent)
    }

    private fun cancelMoodReminder() {
        val intent = PendingIntent.getBroadcast(this, 0,
            Intent(this, MoodReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        getSystemService(AlarmManager::class.java)?.cancel(intent)
    }
}
