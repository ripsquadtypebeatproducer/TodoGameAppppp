package com.todogame.app.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todogame.app.database.DatabaseHelper
import com.todogame.app.models.Achievement
import com.todogame.app.models.Category
import com.todogame.app.models.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {
    private val _tasks = MutableLiveData<List<Task>>(emptyList())
    val tasks: LiveData<List<Task>> = _tasks

    private val _categories = MutableLiveData<List<Category>>(emptyList())
    val categories: LiveData<List<Category>> = _categories

    private val _profile = MutableLiveData<Map<String, Any>>(emptyMap())
    val profile: LiveData<Map<String, Any>> = _profile

    private val _newAchievements = MutableLiveData<List<Achievement>>(emptyList())
    val newAchievements: LiveData<List<Achievement>> = _newAchievements

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadTasks(userId: Int, categoryId: Int? = null) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (categoryId != null) DatabaseHelper.getTasksByCategory(userId, categoryId)
                    else DatabaseHelper.getTasks(userId)
                }
                _tasks.value = result
            } catch (e: Exception) { _error.value = e.message }
        }
    }

    fun loadCategories(userId: Int) {
        viewModelScope.launch {
            try {
                _categories.value = withContext(Dispatchers.IO) { DatabaseHelper.getCategories(userId) }
            } catch (e: Exception) { _error.value = e.message }
        }
    }

    fun loadProfile(userId: Int) {
        viewModelScope.launch {
            try {
                _profile.value = withContext(Dispatchers.IO) { DatabaseHelper.getUserProfile(userId) }
            } catch (e: Exception) { _error.value = e.message }
        }
    }

    fun completeTask(taskId: Int, userId: Int) {
        viewModelScope.launch {
            try {
                val (_, achievements) = withContext(Dispatchers.IO) { DatabaseHelper.completeTask(taskId, userId) }
                if (achievements.isNotEmpty()) _newAchievements.value = achievements
                loadTasks(userId)
                loadProfile(userId)
            } catch (e: Exception) { _error.value = e.message }
        }
    }

    fun moveToTrash(taskId: Int, userId: Int) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { DatabaseHelper.moveToTrash(taskId, userId) }
                loadTasks(userId)
            } catch (e: Exception) { _error.value = e.message }
        }
    }

    fun clearAchievements() { _newAchievements.value = emptyList() }
    fun clearError() { _error.value = null }
}
