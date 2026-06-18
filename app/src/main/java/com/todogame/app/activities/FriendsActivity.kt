package com.todogame.app.activities

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.todogame.app.R
import com.todogame.app.database.DatabaseHelper
import com.todogame.app.models.AvatarData
import com.todogame.app.models.Friend
import com.todogame.app.models.FriendRequest
import com.todogame.app.utils.SessionManager
import kotlinx.coroutines.*

class FriendsActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private enum class Mode { FRIENDS, REQUESTS, SEARCH }
    private var mode = Mode.FRIENDS

    private val rows = mutableListOf<FriendRow>()
    private lateinit var adapter: FriendAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var tabFriends: TextView
    private lateinit var tabRequests: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friends)
        session = SessionManager(this)

        tvEmpty = findViewById(R.id.tvEmpty)
        tabFriends = findViewById(R.id.tabFriends)
        tabRequests = findViewById(R.id.tabRequests)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvFriends)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = FriendAdapter(rows) { row, secondary -> onRowAction(row, secondary) }
        rv.adapter = adapter

        tabFriends.setOnClickListener { switchMode(Mode.FRIENDS) }
        tabRequests.setOnClickListener { switchMode(Mode.REQUESTS) }

        val etSearch = findViewById<EditText>(R.id.etSearch)
        findViewById<Button>(R.id.btnSearch).setOnClickListener {
            val q = etSearch.text.toString().trim()
            if (q.isNotEmpty()) doSearch(q)
        }

        switchMode(Mode.FRIENDS)
    }

    private fun switchMode(m: Mode) {
        mode = m
        val accent = android.graphics.Color.parseColor("#7C4DFF")
        val muted = resolveAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
        tabFriends.setTextColor(if (m == Mode.FRIENDS) accent else muted)
        tabRequests.setTextColor(if (m == Mode.REQUESTS) accent else muted)
        when (m) {
            Mode.FRIENDS -> loadFriends()
            Mode.REQUESTS -> loadRequests()
            Mode.SEARCH -> {}
        }
    }

    private fun loadFriends() {
        val uid = session.getUserId()
        scope.launch {
            val friends = withContext(Dispatchers.IO) { DatabaseHelper.getFriends(uid) }
            rows.clear()
            friends.forEach { rows.add(FriendRow.FriendItem(it)) }
            adapter.notifyDataSetChanged()
            tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            tvEmpty.text = "У тебя пока нет друзей.\nНайди кого-нибудь через поиск!"
        }
    }

    private fun loadRequests() {
        val uid = session.getUserId()
        scope.launch {
            val reqs = withContext(Dispatchers.IO) { DatabaseHelper.getFriendRequests(uid) }
            rows.clear()
            reqs.forEach { rows.add(FriendRow.RequestItem(it)) }
            adapter.notifyDataSetChanged()
            tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            tvEmpty.text = "Нет входящих заявок"
        }
    }

    private fun doSearch(query: String) {
        mode = Mode.SEARCH
        val uid = session.getUserId()
        scope.launch {
            val results = withContext(Dispatchers.IO) { DatabaseHelper.searchUsers(query, uid) }
            rows.clear()
            results.forEach { rows.add(FriendRow.SearchItem(it)) }
            adapter.notifyDataSetChanged()
            tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            tvEmpty.text = "Никого не найдено"
        }
    }

    private fun onRowAction(row: FriendRow, secondary: Boolean) {
        val uid = session.getUserId()
        when (row) {
            is FriendRow.SearchItem -> scope.launch {
                val r = withContext(Dispatchers.IO) { DatabaseHelper.sendFriendRequest(uid, row.friend.userId) }
                Toast.makeText(this@FriendsActivity,
                    if (r == 0) "Заявка отправлена" else if (r == 1) "Заявка уже существует" else "Ошибка",
                    Toast.LENGTH_SHORT).show()
            }
            is FriendRow.RequestItem -> scope.launch {
                withContext(Dispatchers.IO) { DatabaseHelper.respondFriendRequest(row.req.requestId, accept = !secondary) }
                loadRequests()
            }
            is FriendRow.FriendItem -> {
                if (secondary) {
                    scope.launch {
                        withContext(Dispatchers.IO) { DatabaseHelper.removeFriend(uid, row.friend.userId) }
                        loadFriends()
                    }
                } else {
                    showFriendStats(row.friend)
                }
            }
        }
    }

    private fun showFriendStats(friend: Friend) {
        scope.launch {
            val stats = withContext(Dispatchers.IO) { DatabaseHelper.getFriendStats(friend.userId) }
            val avatar = if (stats.avatarId in AvatarData.avatarEmojis.indices) AvatarData.avatarEmojis[stats.avatarId] else "🦊"
            val msg = """
                $avatar  ${stats.username}

                Уровень: ${stats.level}
                Опыт: ${stats.xp} XP
                Серия дней: ${stats.taskStreak} 🔥
                Выполнено задач: ${stats.tasksCompleted}
                Достижений: ${stats.achievements}
            """.trimIndent()
            androidx.appcompat.app.AlertDialog.Builder(this@FriendsActivity)
                .setTitle("Статистика друга")
                .setMessage(msg)
                .setPositiveButton("Закрыть", null)
                .show()
        }
    }

    private fun resolveAttr(attr: Int): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}

// Тип строки списка
sealed class FriendRow {
    data class FriendItem(val friend: Friend) : FriendRow()
    data class RequestItem(val req: FriendRequest) : FriendRow()
    data class SearchItem(val friend: Friend) : FriendRow()
}

class FriendAdapter(
    private val rows: List<FriendRow>,
    private val onAction: (FriendRow, Boolean) -> Unit  // (row, secondaryButton)
) : RecyclerView.Adapter<FriendAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: com.todogame.app.views.AvatarView = v.findViewById(R.id.avatarView)
        val username: TextView = v.findViewById(R.id.tvUsername)
        val subtitle: TextView = v.findViewById(R.id.tvSubtitle)
        val btnAction: Button = v.findViewById(R.id.btnAction)
        val btnSecondary: ImageButton = v.findViewById(R.id.btnSecondary)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_friend, p, false))

    override fun getItemCount() = rows.size

    private fun avatarEmoji(id: Int): String =
        if (id in AvatarData.avatarEmojis.indices) AvatarData.avatarEmojis[id] else "🦊"

    override fun onBindViewHolder(h: VH, pos: Int) {
        when (val row = rows[pos]) {
            is FriendRow.FriendItem -> {
                h.avatar.setAvatar(row.friend.avatarId)
                h.username.text = row.friend.username
                h.subtitle.text = "Уровень ${row.friend.level} · серия ${row.friend.taskStreak} 🔥"
                h.btnAction.text = "Профиль"
                h.btnAction.visibility = View.VISIBLE
                h.btnSecondary.visibility = View.VISIBLE
                h.btnAction.setOnClickListener { onAction(row, false) }
                h.btnSecondary.setOnClickListener { onAction(row, true) }
            }
            is FriendRow.RequestItem -> {
                h.avatar.setAvatar(row.req.fromAvatarId)
                h.username.text = row.req.fromUsername
                h.subtitle.text = "Хочет добавить в друзья"
                h.btnAction.text = "Принять"
                h.btnAction.visibility = View.VISIBLE
                h.btnSecondary.visibility = View.VISIBLE
                h.btnAction.setOnClickListener { onAction(row, false) }
                h.btnSecondary.setOnClickListener { onAction(row, true) }
            }
            is FriendRow.SearchItem -> {
                h.avatar.setAvatar(row.friend.avatarId)
                h.username.text = row.friend.username
                h.subtitle.text = "Уровень ${row.friend.level}"
                h.btnAction.text = "Добавить"
                h.btnAction.visibility = View.VISIBLE
                h.btnSecondary.visibility = View.GONE
                h.btnAction.setOnClickListener { onAction(row, false) }
            }
        }
    }
}
