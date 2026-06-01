package com.example.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.PdfRepository
import com.example.utils.SettingsDataStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Suppress("OPT_IN_USAGE")
class MainViewModel(
    private val repository: PdfRepository,
    private val settings: SettingsDataStore
) : ViewModel() {

    // ===== Settings state =====
    val isDarkTheme: StateFlow<Boolean> = settings.isDarkTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val isFirstLaunch: StateFlow<Boolean> = settings.isFirstLaunch
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)

    val appLock: StateFlow<Boolean> = settings.appLock
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ===== Files state =====
    val recentFiles: StateFlow<List<PdfFile>> = repository.getRecentFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFiles: StateFlow<List<PdfFile>> = repository.getAllFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<PdfFile>> = repository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<PdfFile>> = _searchQuery
        .debounce(300)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList())
            else repository.searchFiles(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ===== Bookmarks state =====
    val allBookmarks: StateFlow<List<Bookmark>> = repository.getAllBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ===== Vocabulary state =====
    val vocabulary: StateFlow<List<VocabularyWord>> = repository.getVocabulary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dueForReview: StateFlow<List<VocabularyWord>> = repository.getDueForReview()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ===== History / Stats state =====
    val readHistory: StateFlow<List<ReadHistory>> = repository.getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _weeklyReadTimeMin = MutableStateFlow(0L)
    val weeklyReadTimeMin: StateFlow<Long> = _weeklyReadTimeMin.asStateFlow()

    private val _weeklyPages = MutableStateFlow(0)
    val weeklyPages: StateFlow<Int> = _weeklyPages.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _weeklyReadTimeMin.value = repository.getWeeklyReadTimeMs() / 60000
            _weeklyPages.value = repository.getWeeklyPages()
        }
    }

    // ===== Actions =====
    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun setFirstLaunchDone() {
        viewModelScope.launch { settings.setFirstLaunchDone() }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setAppLock(enabled: Boolean) {
        viewModelScope.launch { settings.setAppLock(enabled) }
    }

    fun addPdfFromUri(uri: Uri, onResult: (PdfFile?) -> Unit) {
        viewModelScope.launch {
            val file = repository.addFile(uri)
            onResult(file)
        }
    }

    fun toggleFavorite(file: PdfFile) {
        viewModelScope.launch { repository.toggleFavorite(file) }
    }

    fun deleteFile(file: PdfFile) {
        viewModelScope.launch { repository.deleteFile(file) }
    }

    fun addBookmark(pdfId: Long, pdfPath: String, page: Int, label: String) {
        viewModelScope.launch { repository.addBookmark(pdfId, pdfPath, page, label) }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { repository.deleteBookmark(bookmark) }
    }

    fun addVocabularyWord(word: VocabularyWord) {
        viewModelScope.launch { repository.addWord(word) }
    }

    fun markWordReviewed(word: VocabularyWord, correct: Boolean) {
        viewModelScope.launch { repository.markWordReviewed(word, correct) }
    }
}
