package com.todogame.app.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.todogame.app.R
import com.todogame.app.database.DatabaseHelper
import com.todogame.app.models.Category
import com.todogame.app.utils.SessionManager
import kotlinx.coroutines.*
import java.util.Calendar

class TaskDetailActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var selectedDate: String? = null
    private var selectedCategoryId: Int? = null
    private var categories = listOf<Category>()
    private var isSaving = false
    private var editTaskId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        setContentView(R.layout.activity_task_detail)

        editTaskId = intent.getIntExtra("task_id", -1)
        if (editTaskId != -1) {
            findViewById<TextView>(R.id.tvTitle).text = "Редактировать задачу"
        }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        loadCategories()
        setupNewCategoryButton()
        setupDatePicker()
        setupSaveButton()
    }

    private fun loadCategories(selectId: Int? = null) {
        scope.launch {
            categories = withContext(Dispatchers.IO) { DatabaseHelper.getCategories(session.getUserId()) }
            val spinner = findViewById<Spinner>(R.id.spinnerCategory)
            val names = mutableListOf("Без папки") + categories.map { it.name }
            spinner.adapter = ArrayAdapter(this@TaskDetailActivity, android.R.layout.simple_spinner_item, names)
                .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                    selectedCategoryId = if (pos == 0) null else categories[pos - 1].id
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
            // If a category was just created, select it
            if (selectId != null) {
                val idx = categories.indexOfFirst { it.id == selectId }
                if (idx >= 0) spinner.setSelection(idx + 1)
            }
        }
    }

    private fun setupNewCategoryButton() {
        findViewById<Button>(R.id.btnNewCategory).setOnClickListener {
            val input = EditText(this).apply {
                hint = "Название папки"
                setSingleLine()
            }
            val container = FrameLayout(this).apply {
                setPadding(48, 16, 48, 0)
                addView(input)
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Новая папка")
                .setView(container)
                .setPositiveButton("Создать") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isEmpty()) {
                        Toast.makeText(this@TaskDetailActivity, "Введи название папки", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    // Pick a random color from the palette
                    val colors = listOf("#7C4DFF","#FF6B6B","#4ECDC4","#45B7D1","#96CEB4","#FFEAA7","#DDA0DD","#82E0AA")
                    val color = colors.random()
                    scope.launch {
                        val created = withContext(Dispatchers.IO) {
                            DatabaseHelper.createCategory(session.getUserId(), name, color)
                        }
                        if (created != null) {
                            Toast.makeText(this@TaskDetailActivity, "Папка «$name» создана", Toast.LENGTH_SHORT).show()
                            loadCategories(created.id)
                        } else {
                            Toast.makeText(this@TaskDetailActivity, "Не удалось создать папку", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun setupDatePicker() {
        val btnDate = findViewById<Button>(R.id.btnPickDate)
        val tvDate  = findViewById<TextView>(R.id.tvDueDate)
        btnDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                selectedDate = "%04d-%02d-%02d".format(y, m + 1, d)
                tvDate.text = "%02d.%02d.%04d".format(d, m + 1, y)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun setupSaveButton() {
        val btnSave = findViewById<Button>(R.id.btnSave)
        val etTitle = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTaskTitle)
        val etDesc  = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDescription)
        val rgPriority = findViewById<RadioGroup>(R.id.rgPriority)

        btnSave.setOnClickListener {
            if (isSaving) return@setOnClickListener
            val title = etTitle.text.toString().trim()
            if (title.isEmpty()) { etTitle.error = "Введи название"; return@setOnClickListener }
            val priority = when (rgPriority.checkedRadioButtonId) {
                R.id.rbLow -> 1; R.id.rbHigh -> 3; else -> 2
            }
            isSaving = true; btnSave.isEnabled = false
            scope.launch {
                withContext(Dispatchers.IO) {
                    if (editTaskId == -1) {
                        DatabaseHelper.createTask(session.getUserId(), title, etDesc.text.toString(), priority, selectedDate, selectedCategoryId)
                    } else {
                        DatabaseHelper.updateTask(editTaskId, title, etDesc.text.toString(), priority, selectedDate, selectedCategoryId)
                    }
                }
                finish()
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
