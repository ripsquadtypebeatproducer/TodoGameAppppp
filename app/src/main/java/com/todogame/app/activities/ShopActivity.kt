package com.todogame.app.activities

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.todogame.app.R
import com.todogame.app.database.DatabaseHelper
import com.todogame.app.models.ShopItem
import com.todogame.app.utils.SessionManager
import kotlinx.coroutines.*

class ShopActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val items = mutableListOf<ShopItem>()
    private lateinit var adapter: ShopAdapter
    private lateinit var tvCoins: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shop)
        session = SessionManager(this)
        tvCoins = findViewById(R.id.tvCoins)
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvShop)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = ShopAdapter(items) { item -> buy(item) }
        rv.adapter = adapter

        loadShop()
    }

    private fun loadShop() {
        val uid = session.getUserId()
        scope.launch {
            val list = withContext(Dispatchers.IO) { DatabaseHelper.getShopItems(uid) }
            val coins = withContext(Dispatchers.IO) { DatabaseHelper.getCoins(uid) }
            items.clear(); items.addAll(list); adapter.notifyDataSetChanged()
            tvCoins.text = coins.toString()
        }
    }

    private fun buy(item: ShopItem) {
        val uid = session.getUserId()
        scope.launch {
            val result = withContext(Dispatchers.IO) { DatabaseHelper.buyItem(uid, item.id) }
            val msg = when (result) {
                0 -> "Куплено: ${item.name}!"
                1 -> "Недостаточно монет"
                2 -> "Уже куплено"
                else -> "Ошибка покупки"
            }
            Toast.makeText(this@ShopActivity, msg, Toast.LENGTH_SHORT).show()
            if (result == 0) loadShop()
        }
    }
}

class ShopAdapter(private val items: List<ShopItem>, private val onBuy: (ShopItem) -> Unit) :
    RecyclerView.Adapter<ShopAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: TextView = v.findViewById(R.id.tvItemIcon)
        val name: TextView = v.findViewById(R.id.tvItemName)
        val type: TextView = v.findViewById(R.id.tvItemType)
        val price: TextView = v.findViewById(R.id.tvPrice)
        val btnBuy: Button = v.findViewById(R.id.btnBuy)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_shop, p, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        // Иконка: эмодзи для badge/avatar, символ для pet/theme
        h.icon.text = when (item.itemType) {
            "badge", "avatar" -> item.iconValue
            "pet" -> when (item.iconValue) { "cat" -> "🐱"; "panda" -> "🐼"; "dragon" -> "🐉"; else -> "🦊" }
            "theme" -> "🎨"
            else -> "🎁"
        }
        h.name.text = item.name
        h.type.text = when (item.itemType) {
            "pet" -> "Питомец"; "theme" -> "Тема"; "badge" -> "Бейдж"; "avatar" -> "Аватар"; else -> ""
        }
        if (item.isOwned) {
            h.price.text = "✓ Куплено"
            h.price.setTextColor(android.graphics.Color.parseColor("#22C55E"))
            h.btnBuy.visibility = View.GONE
        } else {
            h.price.text = "${item.price} 🪙"
            h.price.setTextColor(android.graphics.Color.parseColor("#F59E0B"))
            h.btnBuy.visibility = View.VISIBLE
            h.btnBuy.setOnClickListener { onBuy(item) }
        }
    }
}
