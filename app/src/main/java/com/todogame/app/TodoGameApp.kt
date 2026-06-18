package com.todogame.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.multidex.MultiDexApplication
import com.todogame.app.models.ThemeData
import com.todogame.app.utils.SessionManager

class TodoGameApp : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        val theme = SessionManager(this).getTheme()
        AppCompatDelegate.setDefaultNightMode(
            if (theme == ThemeData.THEME_LIGHT) AppCompatDelegate.MODE_NIGHT_NO
            else AppCompatDelegate.MODE_NIGHT_YES  // dark by default
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel("mood_reminder", "Напоминание о настроении",
                NotificationManager.IMPORTANCE_DEFAULT)
                .also { getSystemService(NotificationManager::class.java)?.createNotificationChannel(it) }
        }
    }
}
