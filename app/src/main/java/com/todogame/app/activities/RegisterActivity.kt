package com.todogame.app.activities

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.todogame.app.R
import com.todogame.app.database.DatabaseHelper
import com.todogame.app.utils.SessionManager
import kotlinx.coroutines.*

class RegisterActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val etEmail    = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEmail)
        val etUsername = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etUsername)
        val etPassword = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPassword)
        val etConfirm  = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPasswordConfirm)
        val tvError    = findViewById<TextView>(R.id.tvError)
        val btnReg     = findViewById<Button>(R.id.btnRegister)
        val btnBack    = findViewById<Button>(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        btnReg.setOnClickListener {
            val email    = etEmail.text.toString().trim()
            val username = etUsername.text.toString().trim()
            val pass     = etPassword.text.toString()
            val confirm  = etConfirm.text.toString()

            when {
                email.isEmpty() || username.isEmpty() || pass.isEmpty() ->
                    tvError.text = "Заполни все поля"
                !email.contains("@") ->
                    tvError.text = "Некорректный email"
                pass.length < 6 ->
                    tvError.text = "Пароль минимум 6 символов"
                pass != confirm ->
                    tvError.text = "Пароли не совпадают"
                else -> {
                    btnReg.isEnabled = false
                    tvError.text = "Регистрация..."
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            DatabaseHelper.registerUser(email, username, pass)
                        }
                        if (ok) {
                            Toast.makeText(this@RegisterActivity, "Аккаунт создан!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                            finish()
                        } else {
                            tvError.text = "Email уже используется"
                            btnReg.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
