package com.todogame.app.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.todogame.app.R
import com.todogame.app.adapters.TaskAdapter
import com.todogame.app.database.DatabaseHelper
import com.todogame.app.models.AvatarData
import com.todogame.app.models.Category
import com.todogame.app.models.Task
import com.todogame.app.utils.SessionManager
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val tasks = mutableListOf<Task>()
    private val categories = mutableListOf<Category>()
    private lateinit var taskAdapter: TaskAdapter
    private var selectedCategoryId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        if (!session.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)
        setupRecyclerView()
        setupBottomNav()
        setupFab()
        loadData()
        checkMoodBanner()
    }

    private fun setupRecyclerView() {
        val rv = findViewById<RecyclerView>(R.id.rvTasks)
        rv.layoutManager = LinearLayoutManager(this)
        taskAdapter = TaskAdapter(
            tasks,
            onComplete = { task -> confirmComplete(task) },
            onDelete   = { task -> trashTask(task) },
            onClick    = { task -> openTask(task) },
            onLongClick = { task -> shareTaskWithFriend(task) }
        )
        rv.adapter = taskAdapter
    }

    private fun setupBottomNav() {
        val activity = this
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener { }
        findViewById<LinearLayout>(R.id.navHabits).setOnClickListener {
            activity.startActivity(Intent(activity, HabitsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.navChallenges).setOnClickListener {
            activity.startActivity(Intent(activity, ChallengesActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            activity.startActivity(Intent(activity, ProfileActivity::class.java))
        }
    }

    private fun setupFab() {
        val activity = this
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            activity.startActivity(Intent(activity, TaskDetailActivity::class.java))
        }
    }

    private fun loadData() {
        val activity = this
        scope.launch {
            val userId = session.getUserId()
            val taskList = withContext(Dispatchers.IO) {
                if (selectedCategoryId != null)
                    DatabaseHelper.getTasksByCategory(userId, selectedCategoryId!!)
                else DatabaseHelper.getTasks(userId)
            }
            // Совместные задачи (от друзей) показываем только в общем списке
            val sharedList = withContext(Dispatchers.IO) {
                if (selectedCategoryId == null) DatabaseHelper.getSharedTasks(userId) else emptyList()
            }
            val catList  = withContext(Dispatchers.IO) { DatabaseHelper.getCategories(userId) }
            val profile  = withContext(Dispatchers.IO) { DatabaseHelper.getUserProfile(userId) }

            tasks.clear(); tasks.addAll(taskList); tasks.addAll(sharedList); taskAdapter.notifyDataSetChanged()
            categories.clear(); categories.addAll(catList)
            updateHeader(profile)
            updateChips()
            loadChallengesPreview(userId)
        }
    }

    private fun updateHeader(profile: Map<String, Any>) {
        // Заголовок «Сегодня» статичный; подзаголовок — кол-во задач + день недели
        val taskCount = tasks.count { it.sharedByName == null }
        val dayFmt = java.text.SimpleDateFormat("EEEE", java.util.Locale("ru"))
        val dayName = dayFmt.format(java.util.Date())
        findViewById<TextView>(R.id.tvUsername).text = "Сегодня"
        findViewById<TextView>(R.id.tvChallengesPreview).text =
            "${taskCount} ${taskWord(taskCount)} · $dayName"

        val streak = (profile["streak"] as? Int) ?: 0
        findViewById<TextView>(R.id.tvStreakCount).text = "$streak"

        // Settings icon
        val activity = this
        val settingsBtn = findViewById<android.widget.ImageView>(R.id.ivSettings)
        settingsBtn?.setOnClickListener { activity.startActivity(Intent(activity, SettingsActivity::class.java)) }
    }

    private fun taskWord(n: Int): String {
        val mod10 = n % 10; val mod100 = n % 100
        return when {
            mod10 == 1 && mod100 != 11 -> "задача"
            mod10 in 2..4 && mod100 !in 12..14 -> "задачи"
            else -> "задач"
        }
    }

    private fun loadChallengesPreview(userId: Int) {
        scope.launch {
            val challenges = withContext(Dispatchers.IO) { DatabaseHelper.getTodayChallenges(userId) }
            val done = challenges.count { it.isCompleted }
            findViewById<TextView>(R.id.tvChallengesPreview)?.text = "Вызовы дня: $done/3"
        }
    }

    private fun updateChips() {
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroup)
        chipGroup.removeAllViews()
        Chip(this).apply {
            text = "Все"; isCheckable = true; isChecked = selectedCategoryId == null
            setOnClickListener { selectedCategoryId = null; loadData() }
        }.also { chipGroup.addView(it) }
        categories.forEach { cat ->
            Chip(this).apply {
                text = cat.name; isCheckable = true; isChecked = selectedCategoryId == cat.id
                setOnClickListener { selectedCategoryId = cat.id; loadData() }
            }.also { chipGroup.addView(it) }
        }
    }

    private fun confirmComplete(task: Task) {
        AlertDialog.Builder(this)
            .setTitle("Выполнить задачу?")
            .setMessage("\"${task.title}\"")
            .setPositiveButton("Выполнить") { _, _ ->
                scope.launch {
                    val (_, achievements) = withContext(Dispatchers.IO) {
                        DatabaseHelper.completeTask(task.id, session.getUserId())
                    }
                    loadData()
                    if (achievements.isNotEmpty()) {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Новые достижения!")
                            .setMessage(achievements.joinToString("\n") { "🏆 ${it.name}" })
                            .setPositiveButton("Отлично!", null).show()
                    }
                }
            }
            .setNegativeButton("Отмена", null).show()
    }

    private fun trashTask(task: Task) {
        scope.launch {
            withContext(Dispatchers.IO) { DatabaseHelper.moveToTrash(task.id, session.getUserId()) }
            loadData()
        }
    }

    private fun shareTaskWithFriend(task: Task) {
        val activity = this
        scope.launch {
            val friends = withContext(Dispatchers.IO) { DatabaseHelper.getFriends(session.getUserId()) }
            if (friends.isEmpty()) {
                Toast.makeText(activity, "Сначала добавь друзей", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val names = friends.map { it.username }.toTypedArray()
            androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle("Поделиться задачей «${task.title}»")
                .setItems(names) { _, which ->
                    val friend = friends[which]
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { DatabaseHelper.shareTask(task.id, friend.userId) }
                        Toast.makeText(activity,
                            if (ok) "Задача отправлена другу ${friend.username}" else "Уже отправлена этому другу",
                            Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun openTask(task: Task) {
        startActivity(Intent(this, TaskDetailActivity::class.java).putExtra("task_id", task.id))
    }

    private fun checkMoodBanner() {
        scope.launch {
            val hasMood = withContext(Dispatchers.IO) { DatabaseHelper.hasMoodToday(session.getUserId()) }
            val banner = findViewById<View>(R.id.moodBanner)
            if (!hasMood && banner != null) {
                banner.visibility = View.VISIBLE
                banner.setOnClickListener { startActivity(Intent(this@MainActivity, MoodActivity::class.java)) }
            }
        }
    }

    fun openSettings(v: View) { startActivity(Intent(this, SettingsActivity::class.java)) }

    override fun onResume() { super.onResume(); loadData() }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
