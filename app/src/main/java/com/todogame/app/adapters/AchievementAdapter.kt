package com.todogame.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.todogame.app.R
import com.todogame.app.models.Achievement

class AchievementAdapter(private val achievements: List<Achievement>) :
    RecyclerView.Adapter<AchievementAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvIcon: TextView  = v.findViewById(R.id.tvAchIcon)
        val tvName: TextView  = v.findViewById(R.id.tvAchName)
        val tvDesc: TextView  = v.findViewById(R.id.tvAchDesc)
        val ivLocked: ImageView = v.findViewById(R.id.ivAchLocked)
    }

    override fun getItemCount() = achievements.size
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_achievement, p, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val ach = achievements[pos]
        h.tvName.text = ach.name
        h.tvDesc.text = ach.description
        // Minimalist single-char icons
        h.tvIcon.text = when {
            ach.iconName.contains("streak")    -> "S"
            ach.iconName.contains("total")     -> "T"
            ach.iconName.contains("today")     -> "D"
            ach.iconName.contains("categor")   -> "C"
            ach.iconName.contains("mood")      -> "M"
            ach.iconName.contains("habit")     -> "H"
            ach.iconName.contains("challenge") -> "V"
            ach.iconName.contains("deadline")  -> "P"
            else -> "A"
        }
        h.tvIcon.setTextColor(
            if (ach.isUnlocked) 0xFFB388FF.toInt() else 0xFF444466.toInt()
        )
        h.itemView.alpha = if (ach.isUnlocked) 1f else 0.4f
        h.ivLocked.visibility = if (ach.isUnlocked) View.GONE else View.VISIBLE
    }
}
