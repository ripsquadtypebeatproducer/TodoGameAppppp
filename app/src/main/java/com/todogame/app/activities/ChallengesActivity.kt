package com.todogame.app.activities

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.todogame.app.R
import com.todogame.app.database.DatabaseHelper
import com.todogame.app.models.DailyChallenge
import com.todogame.app.utils.SessionManager
import kotlinx.coroutines.*

class ChallengesActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_challenges)
        session = SessionManager(this)
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        loadChallenges()
    }

    private fun loadChallenges() {
        scope.launch {
            val challenges = withContext(Dispatchers.IO) { DatabaseHelper.getTodayChallenges(session.getUserId()) }
            val rv = findViewById<RecyclerView>(R.id.rvChallenges)
            rv.layoutManager = LinearLayoutManager(this@ChallengesActivity)
            rv.adapter = ChallengeAdapter(challenges)
            val completed = challenges.count { it.isCompleted }
            findViewById<TextView>(R.id.tvChallengeStatus).text = "$completed/3 вызовов выполнено"
            if (completed == 3) {
                findViewById<TextView>(R.id.tvStarEarned).visibility = View.VISIBLE
                findViewById<TextView>(R.id.tvStarEarned).text = "Звезда дня получена!"
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}

class ChallengeAdapter(private val challenges: List<DailyChallenge>) : RecyclerView.Adapter<ChallengeAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvDesc: TextView = v.findViewById(R.id.tvChallengeDesc)
        val tvProgress: TextView = v.findViewById(R.id.tvChallengeProgress)
        val progressBar: ProgressBar = v.findViewById(R.id.challengeProgressBar)
        val ivDone: ImageView = v.findViewById(R.id.ivChallengeDone)
    }
    override fun getItemCount() = challenges.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_challenge, parent, false))
    override fun onBindViewHolder(holder: VH, pos: Int) {
        val c = challenges[pos]
        holder.tvDesc.text = c.description
        holder.tvProgress.text = "${c.currentValue}/${c.targetValue}"
        holder.progressBar.max = c.targetValue
        holder.progressBar.progress = c.currentValue
        holder.ivDone.visibility = if (c.isCompleted) View.VISIBLE else View.GONE
        holder.itemView.alpha = if (c.isCompleted) 0.6f else 1f
    }
}
