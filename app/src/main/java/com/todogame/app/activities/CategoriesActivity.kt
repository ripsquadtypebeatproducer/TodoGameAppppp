package com.todogame.app.activities

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.todogame.app.R
import com.todogame.app.database.DatabaseHelper
import com.todogame.app.models.Category
import com.todogame.app.utils.SessionManager
import kotlinx.coroutines.*

class CategoriesActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val categories = mutableListOf<Category>()
    private lateinit var adapter: CatAdapter
    private val colors = listOf("#7C4DFF","#FF6B6B","#4ECDC4","#F59E0B","#22C55E","#45B7D1")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_categories)
        session = SessionManager(this)
        val rv = findViewById<RecyclerView>(R.id.rvCategories)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = CatAdapter(categories) { cat -> deleteCategory(cat) }
        rv.adapter = adapter
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAddCategory)
            .setOnClickListener { showAddDialog() }
        loadCategories()
    }

    private fun loadCategories() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { DatabaseHelper.getCategories(session.getUserId()) }
            categories.clear(); categories.addAll(list); adapter.notifyDataSetChanged()
        }
    }

    private fun showAddDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_category, null)
        val colorGroup = view.findViewById<RadioGroup>(R.id.rgColors)
        colors.forEachIndexed { i, c ->
            val rb = RadioButton(this).apply {
                id = i; text = "  "; setBackgroundColor(Color.parseColor(c))
                layoutParams = RadioGroup.LayoutParams(80, 80).apply { marginEnd = 8 }
            }
            colorGroup.addView(rb)
        }
        colorGroup.check(0)
        AlertDialog.Builder(this).setTitle("Новая папка").setView(view)
            .setPositiveButton("Создать") { _, _ ->
                val name = view.findViewById<EditText>(R.id.etCategoryName).text.toString().trim()
                val colorIdx = colorGroup.checkedRadioButtonId.coerceIn(0, colors.size - 1)
                if (name.isNotEmpty()) {
                    scope.launch {
                        withContext(Dispatchers.IO) { DatabaseHelper.createCategory(session.getUserId(), name, colors[colorIdx]) }
                        loadCategories()
                    }
                }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun deleteCategory(cat: Category) {
        AlertDialog.Builder(this).setTitle("Удалить папку?")
            .setMessage("Задачи из папки «${cat.name}» останутся, но потеряют категорию")
            .setPositiveButton("Удалить") { _, _ ->
                scope.launch { withContext(Dispatchers.IO) { DatabaseHelper.deleteCategory(cat.id) }; loadCategories() }
            }.setNegativeButton("Отмена", null).show()
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}

class CatAdapter(private val cats: List<Category>, private val onDelete: (Category) -> Unit) :
    RecyclerView.Adapter<CatAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvCategoryName)
        val colorDot: View = v.findViewById(R.id.categoryColorDot)
        val btnDelete: ImageView = v.findViewById(R.id.btnDeleteCategory)
    }
    override fun getItemCount() = cats.size
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_category, p, false))
    override fun onBindViewHolder(h: VH, pos: Int) {
        val cat = cats[pos]
        h.tvName.text = cat.name
        try { h.colorDot.setBackgroundColor(Color.parseColor(cat.color)) } catch (e: Exception) {}
        h.btnDelete.setOnClickListener { onDelete(cat) }
    }
}
