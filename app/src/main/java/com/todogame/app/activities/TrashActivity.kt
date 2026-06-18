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
import com.todogame.app.models.Task
import com.todogame.app.utils.SessionManager
import kotlinx.coroutines.*

class TrashActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val tasks = mutableListOf<Task>()
    private lateinit var adapter: TrashAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trash)
        session = SessionManager(this)
        val rv = findViewById<RecyclerView>(R.id.rvTrash)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = TrashAdapter(tasks,
            onRestore = { task -> restoreTask(task) },
            onDelete  = { task -> deleteTask(task) }
        )
        rv.adapter = adapter
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnClearAll).setOnClickListener { clearAll() }
        loadTrash()
    }

    private fun loadTrash() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { DatabaseHelper.getTrashTasks(session.getUserId()) }
            tasks.clear(); tasks.addAll(list); adapter.notifyDataSetChanged()
            findViewById<TextView>(R.id.tvEmpty).visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun restoreTask(task: Task) {
        scope.launch {
            withContext(Dispatchers.IO) { DatabaseHelper.restoreTask(task.id) }
            Toast.makeText(this@TrashActivity, "Задача восстановлена", Toast.LENGTH_SHORT).show()
            loadTrash()
        }
    }

    private fun deleteTask(task: Task) {
        AlertDialog.Builder(this).setTitle("Удалить навсегда?")
            .setMessage("«${task.title}» будет удалена безвозвратно")
            .setPositiveButton("Удалить") { _, _ ->
                scope.launch {
                    withContext(Dispatchers.IO) { DatabaseHelper.deleteTaskPermanently(task.id) }
                    loadTrash()
                }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun clearAll() {
        AlertDialog.Builder(this).setTitle("Очистить корзину?")
            .setMessage("Все задачи будут удалены навсегда")
            .setPositiveButton("Очистить") { _, _ ->
                scope.launch {
                    tasks.forEach { withContext(Dispatchers.IO) { DatabaseHelper.deleteTaskPermanently(it.id) } }
                    loadTrash()
                }
            }.setNegativeButton("Отмена", null).show()
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}

class TrashAdapter(
    private val tasks: List<Task>,
    private val onRestore: (Task) -> Unit,
    private val onDelete: (Task) -> Unit
) : RecyclerView.Adapter<TrashAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTitle: TextView = v.findViewById(R.id.tvTaskTitle)
        val tvDesc: TextView  = v.findViewById(R.id.tvTaskDescription)
        val btnRestore: Button    = v.findViewById(R.id.btnRestore)
        val btnDelete: ImageButton = v.findViewById(R.id.btnDelete)
    }
    override fun getItemCount() = tasks.size
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_trash, p, false))
    override fun onBindViewHolder(h: VH, pos: Int) {
        val task = tasks[pos]
        h.tvTitle.text = task.title
        h.tvDesc.text  = task.description
        h.tvDesc.visibility = if (task.description.isBlank()) View.GONE else View.VISIBLE
        h.btnRestore.setOnClickListener { onRestore(task) }
        h.btnDelete.setOnClickListener  { onDelete(task) }
    }
}
