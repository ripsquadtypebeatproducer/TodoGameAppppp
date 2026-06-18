package com.todogame.app.activities

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.todogame.app.R
import com.todogame.app.database.DatabaseHelper
import com.todogame.app.models.LevelData
import com.todogame.app.models.PetData
import com.todogame.app.utils.SessionManager
import com.todogame.app.views.PetView
import kotlinx.coroutines.*

class PetActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var petView: PetView
    private lateinit var tvPetName: TextView
    private lateinit var tvPetMood: TextView
    private lateinit var pbHappiness: ProgressBar
    private lateinit var tvLevel: TextView
    private lateinit var tvXp: TextView
    private lateinit var pbXp: ProgressBar
    private lateinit var petChooser: LinearLayout

    private var ownedPets = mutableListOf("fox") // fox всегда доступен

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pet)
        session = SessionManager(this)

        petView = findViewById(R.id.petView)
        tvPetName = findViewById(R.id.tvPetName)
        tvPetMood = findViewById(R.id.tvPetMood)
        pbHappiness = findViewById(R.id.pbHappiness)
        tvLevel = findViewById(R.id.tvLevel)
        tvXp = findViewById(R.id.tvXp)
        pbXp = findViewById(R.id.pbXp)
        petChooser = findViewById(R.id.petChooser)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        loadPet()
    }

    private fun loadPet() {
        val uid = session.getUserId()
        scope.launch {
            val (type, name, happiness) = withContext(Dispatchers.IO) { DatabaseHelper.updatePetHappinessAndGet(uid) }
            val profile = withContext(Dispatchers.IO) { DatabaseHelper.getUserProfile(uid) }
            val owned = withContext(Dispatchers.IO) { DatabaseHelper.getOwnedPetTypes(uid) }

            ownedPets = (listOf("fox") + owned).distinct().toMutableList()

            // Питомец
            petView.setPet(type, happiness)
            petView.startAnimating()
            tvPetName.text = name
            tvPetMood.text = moodLabel(happiness)
            pbHappiness.progress = happiness
            pbHappiness.progressTintList = android.content.res.ColorStateList.valueOf(
                when { happiness >= 70 -> Color.parseColor("#22C55E"); happiness >= 40 -> Color.parseColor("#F59E0B"); else -> Color.parseColor("#EF4444") }
            )

            // Уровень/опыт
            val xp = (profile["xp"] as? Int) ?: 0
            val lvl = LevelData.levelForXp(xp)
            val curBase = LevelData.xpForLevel(lvl)
            val nextBase = LevelData.xpForLevel(lvl + 1)
            tvLevel.text = "Уровень $lvl"
            tvXp.text = "${xp - curBase} / ${nextBase - curBase} XP"
            pbXp.max = (nextBase - curBase).coerceAtLeast(1)
            pbXp.progress = xp - curBase

            buildPetChooser(type)
        }
    }

    private fun buildPetChooser(currentType: String) {
        petChooser.removeAllViews()
        val nameMap = PetData.pets.toMap()
        for (pType in ownedPets) {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                lp.marginEnd = 8
                layoutParams = lp
                setPadding(0, 12, 0, 12)
                background = if (pType == currentType)
                    androidx.core.content.ContextCompat.getDrawable(this@PetActivity, R.drawable.bg_card_minimal) else null
                setOnClickListener { choosePet(pType) }
            }
            val pv = PetView(this).apply {
                layoutParams = LinearLayout.LayoutParams(64, 64)
                setPet(pType, 80)
            }
            val label = TextView(this).apply {
                text = nameMap[pType] ?: pType
                textSize = 11f
                setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnSurface))
            }
            col.addView(pv); col.addView(label)
            petChooser.addView(col)
        }
    }

    private fun choosePet(type: String) {
        val uid = session.getUserId()
        val name = PetData.pets.toMap()[type] ?: "Питомец"
        scope.launch {
            withContext(Dispatchers.IO) { DatabaseHelper.setPet(uid, type, name) }
            loadPet()
        }
    }

    private fun moodLabel(h: Int): String = when {
        h >= 70 -> "Счастлив 🔥"
        h >= 40 -> "Спокоен"
        else -> "Грустит"
    }

    private fun resolveAttr(attr: Int): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    override fun onResume() { super.onResume(); petView.startAnimating() }
    override fun onPause() { super.onPause(); petView.stopAnimating() }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
