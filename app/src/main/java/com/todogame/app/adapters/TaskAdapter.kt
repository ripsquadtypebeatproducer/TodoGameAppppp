package com.todogame.app.adapters

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.todogame.app.R
import com.todogame.app.models.Task
import java.text.SimpleDateFormat
import java.util.*

class TaskAdapter(
    private val tasks: List<Task>,
    private val onComplete: (Task) -> Unit,
    private val onDelete: (Task) -> Unit,
    private val onClick: (Task) -> Unit,
    private val onLongClick: ((Task) -> Unit)? = null
) : RecyclerView.Adapter<TaskAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTitle: TextView = v.findViewById(R.id.tvTaskTitle)
        val tvDesc: TextView = v.findViewById(R.id.tvTaskDescription)
        val tvCategory: TextView = v.findViewById(R.id.tvCategory)
        val tvDueDate: TextView = v.findViewById(R.id.tvDueDate)
        val tvSharedBadge: TextView = v.findViewById(R.id.tvSharedBadge)
        val btnComplete: ImageButton = v.findViewById(R.id.btnComplete)
        val btnDelete: ImageButton = v.findViewById(R.id.btnDelete)
    }

    override fun getItemCount() = tasks.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val task = tasks[pos]
        holder.tvTitle.text = task.title
        holder.tvDesc.text = task.description
        holder.tvDesc.visibility = if (task.description.isBlank()) View.GONE else View.VISIBLE
        holder.tvCategory.text = task.categoryName ?: ""
        holder.tvCategory.visibility = if (task.categoryName != null) View.VISIBLE else View.GONE

        // Due date
        if (task.dueDate != null) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val due = sdf.parse(task.dueDate)
                val today = Calendar.getInstance().time
                val isOverdue = due != null && due.before(today)
                val displaySdf = SimpleDateFormat("d MMM", Locale("ru"))
                holder.tvDueDate.text = if (isOverdue) "⚠ ${displaySdf.format(due!!)}" else displaySdf.format(due!!)
                holder.tvDueDate.setTextColor(if (isOverdue) Color.parseColor("#FF6B6B") else Color.parseColor("#9794B0"))
                holder.tvDueDate.visibility = View.VISIBLE
            } catch (e: Exception) {
                holder.tvDueDate.visibility = View.GONE
            }
        } else {
            holder.tvDueDate.visibility = View.GONE
        }

        // Цвет приоритета теперь на контуре круглого чекбокса
        val priorityColor = when (task.priority) {
            3 -> Color.parseColor("#FF6B6B")
            2 -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#22C55E")
        }
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(dp(holder, 2), priorityColor)
        }
        holder.btnComplete.background = ring

        // Бейдж совместной задачи
        if (task.sharedByName != null) {
            holder.tvSharedBadge.visibility = View.VISIBLE
            holder.tvSharedBadge.text = "от ${task.sharedByName}"
        } else {
            holder.tvSharedBadge.visibility = View.GONE
        }

        holder.btnComplete.setOnClickListener { onComplete(task) }
        holder.btnDelete.setOnClickListener { onDelete(task) }
        holder.itemView.setOnClickListener { onClick(task) }
        // Долгое нажатие — поделиться (только для своих задач, не расшаренных мне)
        holder.itemView.setOnLongClickListener {
            if (task.sharedByName == null) { onLongClick?.invoke(task); true } else false
        }
    }

    private fun dp(holder: VH, value: Int): Int =
        (value * holder.itemView.resources.displayMetrics.density).toInt()
}
