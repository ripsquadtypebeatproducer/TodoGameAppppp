package com.todogame.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.todogame.app.models.ThemeData

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("todogame_prefs", Context.MODE_PRIVATE)

    fun saveSession(userId: Int, email: String, username: String, role: String, avatarId: Int) {
        prefs.edit().apply {
            putInt("user_id", userId); putString("email", email)
            putString("username", username); putString("role", role)
            putInt("avatar_id", avatarId); putBoolean("is_logged_in", true)
        }.apply()
    }
    fun isLoggedIn() = prefs.getBoolean("is_logged_in", false)
    fun getUserId() = prefs.getInt("user_id", -1)
    fun getEmail() = prefs.getString("email", "") ?: ""
    fun getUsername() = prefs.getString("username", "") ?: ""
    fun getRole() = prefs.getString("role", "user") ?: "user"
    fun getAvatarId() = prefs.getInt("avatar_id", 0)
    fun setAvatarId(id: Int) = prefs.edit().putInt("avatar_id", id).apply()
    fun clearSession() = prefs.edit().clear().apply()
    fun isOnboardingDone() = prefs.getBoolean("onboarding_done", false)
    fun setOnboardingDone() = prefs.edit().putBoolean("onboarding_done", true).apply()
    fun getTheme() = prefs.getString("theme", ThemeData.THEME_DARK) ?: ThemeData.THEME_DARK
    fun setTheme(theme: String) = prefs.edit().putString("theme", theme).apply()
    fun isMoodAsked(date: String) = prefs.getString("mood_asked_date", "") == date
    fun setMoodAsked(date: String) = prefs.edit().putString("mood_asked_date", date).apply()
}
