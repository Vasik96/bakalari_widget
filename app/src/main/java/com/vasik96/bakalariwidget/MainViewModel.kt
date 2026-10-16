package com.vasik96.bakalariwidget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vasik96.bakalariwidget.data.AuthException
import com.vasik96.bakalariwidget.data.Repo
import com.vasik96.bakalariwidget.data.WidgetData
import com.vasik96.bakalariwidget.widget.SyncWorker
import com.vasik96.bakalariwidget.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class UiState(
    val loggedIn: Boolean = false,
    val needsLogin: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val data: WidgetData? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repo(app)
    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    init { viewModelScope.launch { load() } }

    private suspend fun load() {
        val c = repo.loadCache()
        _ui.update { it.copy(loggedIn = c.loggedIn, needsLogin = c.needsLogin, data = c.data) }
    }

    private fun run(block: suspend () -> Unit) = viewModelScope.launch {
        _ui.update { it.copy(loading = true, error = null) }
        try { block() } catch (e: Exception) { _ui.update { it.copy(error = e.userMessage()) } }
        load()
        _ui.update { it.copy(loading = false) }
    }

    fun login(school: String, user: String, pass: String) = run {
        repo.login(school, user, pass)
        repo.sync()
        SyncWorker.schedulePeriodic(getApplication())
        WidgetUpdater.updateAll(getApplication())
    }

    fun sync() = run {
        repo.sync()
        WidgetUpdater.updateAll(getApplication())
    }

    fun logout() = run {
        repo.logout()
        SyncWorker.cancel(getApplication())
        WidgetUpdater.updateAll(getApplication())
    }

    private fun Exception.userMessage() = when (this) {
        is AuthException -> message ?: "Chyba přihlášení"
        is IOException -> "Nelze se připojit ke škole"
        is IllegalArgumentException -> message ?: "Neplatný vstup"
        else -> "Chyba: ${message ?: javaClass.simpleName}"
    }
}
