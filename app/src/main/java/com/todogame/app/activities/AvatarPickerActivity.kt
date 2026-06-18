package com.todogame.app.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.todogame.app.R
import com.todogame.app.database.DatabaseHelper
import com.todogame.app.utils.SessionManager
import com.todogame.app.views.AvatarView
import kotlinx.coroutines.*

class AvatarPickerActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val totalAvatars = 12          // 0..7 бесплатные, 8..11 покупные
    private val freeCount = 8
    private var ownedPurchased = setOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_avatar_picker)
        session = SessionManager(this)
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvAvatars)
        rv.layoutManager = GridLayoutManager(this, 4)

        scope.launch {
            ownedPurchased = withContext(Dispatchers.IO) { DatabaseHelper.getOwnedAvatarIndices(session.getUserId()) }
            rv.adapter = AvatarAdapter()
        }
    }

    private fun isUnlocked(pos: Int): Boolean = pos < freeCount || ownedPurchased.contains(pos)

    inner class AvatarAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = totalAvatars
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_avatar, parent, false)
            return object : RecyclerView.ViewHolder(v) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
            val av = holder.itemView.findViewById<AvatarView>(R.id.avatarView)
            val lock = holder.itemView.findViewById<ImageView>(R.id.ivLock)
            av.setAvatar(pos)

            val unlocked = isUnlocked(pos)
            lock.visibility = if (unlocked) View.GONE else View.VISIBLE
            val isSelected = pos == session.getAvatarId()
            holder.itemView.alpha = when { !unlocked -> 0.5f; isSelected -> 1f; else -> 0.75f }
            holder.itemView.scaleX = if (isSelected) 1.1f else 1f
            holder.itemView.scaleY = if (isSelected) 1.1f else 1f

            holder.itemView.setOnClickListener {
                if (!unlocked) {
                    Toast.makeText(this@AvatarPickerActivity,
                        "Этот аватар можно купить в магазине", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                scope.launch {
                    withContext(Dispatchers.IO) { DatabaseHelper.updateAvatar(session.getUserId(), pos) }
                    session.setAvatarId(pos)
                    notifyDataSetChanged()
                    Toast.makeText(this@AvatarPickerActivity, "Аватар обновлён!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
