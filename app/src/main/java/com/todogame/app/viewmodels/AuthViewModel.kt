package com.todogame.app.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todogame.app.database.DatabaseHelper
import com.todogame.app.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthViewModel : ViewModel() {
    private val _loginResult = MutableLiveData<User?>()
    val loginResult: LiveData<User?> = _loginResult

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _dbReady = MutableLiveData(false)
    val dbReady: LiveData<Boolean> = _dbReady

    fun initDatabase() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    DatabaseHelper.initDatabase()
                    DatabaseHelper.seedDemoAccount()
                }
                _dbReady.value = true
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Ошибка подключения к БД:\n${e.message}\n\nПроверьте SQL Server и порт 1433"
                _dbReady.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) { _error.value = "Заполни все поля"; return }
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val user = withContext(Dispatchers.IO) { DatabaseHelper.loginUser(email.trim(), password) }
                _loginResult.value = user
                _error.value = if (user == null) "Неверный email или пароль" else null
            } catch (e: Exception) {
                _error.value = "Ошибка входа: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun register(email: String, username: String, password: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) { DatabaseHelper.registerUser(email.trim(), username.trim(), password) }
                if (ok) _error.value = null else _error.value = "Email уже используется"
                _loginResult.value = if (ok) withContext(Dispatchers.IO) { DatabaseHelper.loginUser(email.trim(), password) } else null
            } catch (e: Exception) {
                _error.value = "Ошибка регистрации: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() { _error.value = null }
}
