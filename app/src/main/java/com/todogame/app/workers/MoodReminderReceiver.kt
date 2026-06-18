package com.todogame.app.workers

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.todogame.app.activities.MoodActivity
import com.todogame.app.utils.SessionManager
import java.text.SimpleDateFormat
import java.util.Date

class MoodReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val session = SessionManager(context)
        if (!session.isLoggedIn()) return
        val today = SimpleDateFormat("yyyy-MM-dd").format(Date())
        if (session.isMoodAsked(today)) return
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MoodActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationCompat.Builder(context, "mood_reminder")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Как прошёл день? 🌙")
            .setContentText("Отметь своё настроение — это займёт 5 секунд")
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
            .also { context.getSystemService(NotificationManager::class.java)?.notify(42, it) }
    }
}
