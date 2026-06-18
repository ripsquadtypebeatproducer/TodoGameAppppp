package com.todogame.app.activities

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.todogame.app.R
import com.todogame.app.database.DatabaseHelper
import com.todogame.app.utils.SessionManager
import kotlinx.coroutines.*

class LoginActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        if (session.isLoggedIn()) { goMain(); return }
        setContentView(R.layout.activity_login)

        val etEmail    = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPassword)
        val tvStatus   = findViewById<TextView>(R.id.tvStatus)
        val btnLogin   = findViewById<Button>(R.id.btnLogin)
        val btnGoReg   = findViewById<Button>(R.id.btnGoRegister)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass  = etPassword.text.toString()
            if (email.isEmpty() || pass.isEmpty()) {
                tvStatus.text = "Заполни все поля"; return@setOnClickListener
            }
            tvStatus.text = "Подключение..."
            btnLogin.isEnabled = false
            scope.launch {
                val user = withContext(Dispatchers.IO) { DatabaseHelper.loginUser(email, pass) }
                btnLogin.isEnabled = true
                if (user != null) {
                    session.saveSession(user.id, user.email, user.username, user.role, user.avatarId)
                    tvStatus.text = "Добро пожаловать, ${user.username}!"
                    goMain()
                } else {
                    tvStatus.text = "Неверный email или пароль"
                }
            }
        }

        btnGoReg.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
        finish()
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
