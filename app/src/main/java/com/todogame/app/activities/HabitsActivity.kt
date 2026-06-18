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
import com.todogame.app.models.Habit
import com.todogame.app.utils.SessionManager
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class HabitsActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val habits = mutableListOf<Habit>()
    private lateinit var adapter: HabitAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_habits)
        session = SessionManager(this)

        val rv = findViewById<RecyclerView>(R.id.rvHabits)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = HabitAdapter(habits, { habit -> logHabit(habit) }, { habit -> deleteHabit(habit) })
        rv.adapter = adapter

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAddHabit).setOnClickListener { showAddDialog() }

        loadHabits()
    }

    private fun loadHabits() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { DatabaseHelper.getHabits(session.getUserId()) }
            habits.clear(); habits.addAll(list); adapter.notifyDataSetChanged()
        }
    }

    private fun logHabit(habit: Habit) {
        scope.launch {
            withContext(Dispatchers.IO) { DatabaseHelper.logHabit(habit.id) }
            Toast.makeText(this@HabitsActivity, "Привычка отмечена", Toast.LENGTH_SHORT).show()
            loadHabits()
        }
    }

    private fun deleteHabit(habit: Habit) {
        AlertDialog.Builder(this).setTitle("Удалить привычку?").setMessage("«${habit.name}» будет удалена безвозвратно")
            .setPositiveButton("Удалить") { _, _ ->
                scope.launch { withContext(Dispatchers.IO) { DatabaseHelper.deleteHabit(habit.id) }; loadHabits() }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun showAddDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_habit, null)
        AlertDialog.Builder(this).setTitle("Новая привычка").setView(view)
            .setPositiveButton("Создать") { _, _ ->
                val name = view.findViewById<EditText>(R.id.etHabitName).text.toString().trim()
                if (name.isNotEmpty()) {
                    scope.launch {
                        withContext(Dispatchers.IO) { DatabaseHelper.createHabit(session.getUserId(), name, "#7C4DFF") }
                        loadHabits()
                    }
                }
            }.setNegativeButton("Отмена", null).show()
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}

class HabitAdapter(
    private val habits: List<Habit>,
    private val onLog: (Habit) -> Unit,
    private val onDelete: (Habit) -> Unit
) : RecyclerView.Adapter<HabitAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvHabitName)
        val tvStreak: TextView = v.findViewById(R.id.tvHabitStreak)
        val tvTotal: TextView = v.findViewById(R.id.tvHabitTotal)
        val heatmapGrid: GridLayout = v.findViewById(R.id.heatmapGrid)
        val btnLog: Button = v.findViewById(R.id.btnLogHabit)
        val btnDelete: ImageView = v.findViewById(R.id.btnDeleteHabit)
        val colorBar: View = v.findViewById(R.id.habitColorBar)
    }

    override fun getItemCount() = habits.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_habit, parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val habit = habits[pos]
        holder.tvName.text = habit.name
        holder.tvStreak.text = "🔥 ${habit.currentStreak} дней подряд"
        holder.tvTotal.text = "Всего: ${habit.totalDays} дней"
        try { holder.colorBar.setBackgroundColor(Color.parseColor(habit.color)) } catch (e: Exception) {}
        buildHeatmap(holder.heatmapGrid, habit.id)
        holder.btnLog.setOnClickListener { onLog(habit) }
        holder.btnDelete.setOnClickListener { onDelete(habit) }
    }

    private fun buildHeatmap(grid: GridLayout, habitId: Int) {
        grid.removeAllViews()
        CoroutineScope(Dispatchers.Main).launch {
            val logs = withContext(Dispatchers.IO) { DatabaseHelper.getHabitLogs(habitId, 91) }.toSet()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -90)
            val dp4 = (4 * grid.context.resources.displayMetrics.density).toInt()
            val dp16 = (16 * grid.context.resources.displayMetrics.density).toInt()
            repeat(91) {
                val dateStr = sdf.format(cal.time)
                val cell = View(grid.context).apply {
                    layoutParams = GridLayout.LayoutParams().apply { width = dp16; height = dp16; setMargins(dp4, dp4, dp4, dp4) }
                    val isDark = grid.context.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                    setBackgroundColor(if (dateStr in logs) 0xFF7C4DFF.toInt()
                        else if (isDark) 0xFF1E1B4B.toInt() else 0xFFE8E4FF.toInt())
                }
                grid.addView(cell)
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    }
}
