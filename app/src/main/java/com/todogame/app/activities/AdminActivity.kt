package com.todogame.app.activities

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.todogame.app.R
import com.todogame.app.database.DatabaseHelper
import com.todogame.app.utils.SessionManager
import kotlinx.coroutines.*

class AdminActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        if (session.getRole() != "admin") { finish(); return }
        setContentView(R.layout.activity_admin)
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        loadData()
    }

    private fun loadData() {
        scope.launch {
            val stats = withContext(Dispatchers.IO) { DatabaseHelper.getAdminStatsExtended() }
            val leaderboard = withContext(Dispatchers.IO) { DatabaseHelper.getLeaderboard() }
            val users = withContext(Dispatchers.IO) { DatabaseHelper.getAllUsers() }

            // Statistics cards
            findViewById<TextView>(R.id.tvTotalUsers).text = (stats["users"] ?: 0).toString()
            findViewById<TextView>(R.id.tvTotalTasks).text = (stats["tasks"] ?: 0).toString()
            findViewById<TextView>(R.id.tvCompleted).text = (stats["completed"] ?: 0).toString()
            findViewById<TextView>(R.id.tvHabits).text = (stats["habits"] ?: 0).toString()
            findViewById<TextView>(R.id.tvMoods).text = (stats["moods"] ?: 0).toString()
            findViewById<TextView>(R.id.tvCompletedToday).text = (stats["completedToday"] ?: 0).toString()

            // Completion percentage
            val total = stats["tasks"] ?: 0
            val done = stats["completed"] ?: 0
            val pct = if (total > 0) (done * 100 / total) else 0
            findViewById<TextView>(R.id.tvCompletionLabel).text = "Процент выполнения: $pct%"
            findViewById<ProgressBar>(R.id.progressCompletion).progress = pct

            // Leaderboard
            val rvLead = findViewById<RecyclerView>(R.id.rvLeaderboard)
            rvLead.layoutManager = LinearLayoutManager(this@AdminActivity)
            rvLead.adapter = LeaderboardAdapter(leaderboard)

            // User management
            val rvUsers = findViewById<RecyclerView>(R.id.rvUsers)
            rvUsers.layoutManager = LinearLayoutManager(this@AdminActivity)
            rvUsers.adapter = UserAdapter(users,
                onToggleRole = { user -> toggleRole(user) },
                onDelete = { user -> confirmDeleteUser(user) }
            )
        }
    }

    private fun toggleRole(user: DatabaseHelper.AdminUser) {
        val newRole = if (user.role == "admin") "user" else "admin"
        val action = if (newRole == "admin") "повысить до администратора" else "понизить до пользователя"
        AlertDialog.Builder(this)
            .setTitle("Изменить роль")
            .setMessage("Точно $action пользователя ${user.username}?")
            .setPositiveButton("Да") { _, _ ->
                scope.launch {
                    withContext(Dispatchers.IO) { DatabaseHelper.setUserRole(user.id, newRole) }
                    loadData()
                }
            }
            .setNegativeButton("Отмена", null).show()
    }

    private fun confirmDeleteUser(user: DatabaseHelper.AdminUser) {
        if (user.role == "admin") {
            Toast.makeText(this, "Нельзя удалить администратора", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Удалить пользователя?")
            .setMessage("Все данные ${user.username} будут удалены безвозвратно.")
            .setPositiveButton("Удалить") { _, _ ->
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { DatabaseHelper.deleteUser(user.id) }
                    Toast.makeText(this@AdminActivity,
                        if (ok) "Пользователь удалён" else "Не удалось удалить",
                        Toast.LENGTH_SHORT).show()
                    loadData()
                }
            }
            .setNegativeButton("Отмена", null).show()
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}

class LeaderboardAdapter(private val items: List<Pair<String, Int>>) :
    RecyclerView.Adapter<LeaderboardAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tv: TextView = v as TextView
    }
    override fun getItemCount() = items.size
    override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
        val tv = TextView(p.context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(24, 16, 24, 16)
            textSize = 15f
        }
        return VH(tv)
    }
    override fun onBindViewHolder(h: VH, pos: Int) {
        val (name, cnt) = items[pos]
        val medal = when (pos) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${pos + 1}." }
        h.tv.text = "$medal  $name — $cnt задач"
        h.tv.setTextColor(0xFFCCCCCC.toInt())
    }
}

class UserAdapter(
    private val users: List<DatabaseHelper.AdminUser>,
    private val onToggleRole: (DatabaseHelper.AdminUser) -> Unit,
    private val onDelete: (DatabaseHelper.AdminUser) -> Unit
) : RecyclerView.Adapter<UserAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvUserName)
        val tvRole: TextView = v.findViewById(R.id.tvUserRole)
        val tvEmail: TextView = v.findViewById(R.id.tvUserEmail)
        val tvStats: TextView = v.findViewById(R.id.tvUserStats)
        val btnToggle: Button = v.findViewById(R.id.btnToggleRole)
        val btnDelete: ImageButton = v.findViewById(R.id.btnDeleteUser)
    }
    override fun getItemCount() = users.size
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_admin_user, p, false))
    override fun onBindViewHolder(h: VH, pos: Int) {
        val u = users[pos]
        h.tvName.text = u.username
        h.tvEmail.text = u.email
        h.tvStats.text = "🔥 серия ${u.streak} · ✅ ${u.completedTasks}/${u.totalTasks} задач"
        if (u.role == "admin") {
            h.tvRole.text = "ADMIN"
            h.tvRole.setBackgroundColor(0xFFFF6B6B.toInt())
            h.btnToggle.text = "↓ User"
            h.btnToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFF59E0B.toInt())
        } else {
            h.tvRole.text = "USER"
            h.tvRole.setBackgroundColor(0xFF7C4DFF.toInt())
            h.btnToggle.text = "↑ Admin"
            h.btnToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF4ECDC4.toInt())
        }
        h.btnToggle.setOnClickListener { onToggleRole(u) }
        h.btnDelete.setOnClickListener { onDelete(u) }
    }
}
