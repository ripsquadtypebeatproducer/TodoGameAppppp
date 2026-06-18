package com.todogame.app.models

data class User(
    val id: Int = 0, val email: String = "", val username: String = "",
    val role: String = "user", val taskStreak: Int = 0,
    val lastTaskDate: String? = null, val avatarId: Int = 0,
    // Геймификация: опыт, монеты, уровень
    val xp: Int = 0, val coins: Int = 0, val level: Int = 1,
    // Питомец-маскот
    val petType: String = "fox", val petName: String = "Лис",
    val petHappiness: Int = 80,
    // Тема оформления
    val theme: String = "light"
)
data class Task(
    val id: Int = 0, val userId: Int = 0, val title: String = "",
    val description: String = "", val status: String = "pending",
    val priority: Int = 2, val dueDate: String? = null,
    val categoryId: Int? = null, val categoryName: String? = null,
    val completedAt: String? = null,
    // Совместные задачи: владелец и признак "расшарена мне"
    val sharedByUserId: Int? = null, val sharedByName: String? = null
)
data class Category(val id: Int = 0, val userId: Int = 0, val name: String = "", val color: String = "#7C4DFF")
data class Achievement(val id: Int = 0, val name: String = "", val description: String = "", val iconName: String = "", val isUnlocked: Boolean = false, val unlockedAt: String? = null)
data class MoodLog(val id: Int = 0, val userId: Int = 0, val mood: Int = 3, val note: String = "", val logDate: String = "", val tasksCompleted: Int = 0)
data class DailyChallenge(val id: Int = 0, val userId: Int = 0, val challengeType: String = "", val description: String = "", val targetValue: Int = 1, val currentValue: Int = 0, val isCompleted: Boolean = false, val challengeDate: String = "")
data class Habit(val id: Int = 0, val userId: Int = 0, val name: String = "", val color: String = "#7C4DFF", val currentStreak: Int = 0, val totalDays: Int = 0)
data class HabitLog(val habitId: Int = 0, val logDate: String = "", val completed: Boolean = false)

// === НОВЫЕ МОДЕЛИ ===

// Друзья
data class Friend(
    val userId: Int = 0, val username: String = "",
    val avatarId: Int = 0, val level: Int = 1, val taskStreak: Int = 0,
    val status: String = "accepted" // pending / accepted
)
data class FriendRequest(
    val requestId: Int = 0, val fromUserId: Int = 0,
    val fromUsername: String = "", val fromAvatarId: Int = 0
)

// Магазин наград
data class ShopItem(
    val id: Int = 0, val itemType: String = "", // avatar / theme / badge
    val name: String = "", val iconValue: String = "",
    val price: Int = 0, val isOwned: Boolean = false
)

// Статистика продуктивности (точка на графике)
data class StatPoint(val label: String = "", val value: Int = 0)

// Статистика друга (для просмотра)
data class FriendStats(
    val username: String = "", val avatarId: Int = 0,
    val level: Int = 0, val xp: Int = 0, val taskStreak: Int = 0,
    val tasksCompleted: Int = 0, val achievements: Int = 0
)

object AvatarData {
    val avatarColors = listOf("#7C4DFF","#FF6B6B","#4ECDC4","#45B7D1","#96CEB4","#FFEAA7","#DDA0DD","#98D8C8","#F7DC6F","#BB8FCE","#85C1E9","#82E0AA")
    val avatarEmojis = listOf("🦊","🐺","🦁","🐯","🐻","🐼","🦄","🐲","🦅","🦋","🌙","⚡")
}
object ThemeData { const val THEME_DARK = "dark"; const val THEME_LIGHT = "light" }

// Виды питомцев (для магазина и отрисовки)
object PetData {
    // тип -> отображаемое имя
    val pets = listOf("fox" to "Лисёнок", "cat" to "Котёнок", "panda" to "Панда", "dragon" to "Дракончик")
}

// Уровни: сколько XP нужно для каждого уровня
object LevelData {
    // XP для достижения уровня N = 100 * (N-1) * N / 2 (нарастающая)
    fun xpForLevel(level: Int): Int = 100 * (level - 1) * level / 2
    fun levelForXp(xp: Int): Int {
        var lvl = 1
        while (xpForLevel(lvl + 1) <= xp) lvl++
        return lvl
    }
    // прогресс до следующего уровня (0.0..1.0)
    fun progressInLevel(xp: Int): Float {
        val lvl = levelForXp(xp)
        val cur = xpForLevel(lvl); val next = xpForLevel(lvl + 1)
        if (next == cur) return 0f
        return (xp - cur).toFloat() / (next - cur).toFloat()
    }
}
