package com.lion.haberakisi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lion.haberakisi.data.Article
import com.lion.haberakisi.data.NewsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface NewsUiState {
    data object Loading : NewsUiState
    data class Success(val articles: List<Article>) : NewsUiState
    data class Error(val message: String) : NewsUiState
}

class NewsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("bookmarks", Application.MODE_PRIVATE)
    private val _state = MutableStateFlow<NewsUiState>(NewsUiState.Loading)
    val state: StateFlow<NewsUiState> = _state.asStateFlow()

    private val _bookmarks = MutableStateFlow(loadBookmarks())
    val bookmarks: StateFlow<Set<String>> = _bookmarks.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = NewsUiState.Loading
        viewModelScope.launch {
            NewsRepository.fetchAll()
                .onSuccess { _state.value = NewsUiState.Success(it) }
                .onFailure { _state.value = NewsUiState.Error(it.message ?: "Bilinmeyen hata") }
        }
    }

    fun setQuery(q: String) { _query.value = q }

    fun toggleBookmark(link: String) {
        val current = _bookmarks.value.toMutableSet()
        if (!current.add(link)) current.remove(link)
        _bookmarks.value = current
        prefs.edit().putStringSet("links", current).apply()
    }

    private fun loadBookmarks(): Set<String> =
        prefs.getStringSet("links", emptySet())?.toSet() ?: emptySet()
}
