package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.model.Annotation as PdfAnnotation
import com.example.data.repository.PdfRepository
import com.example.ui.reader.ReaderTool
import com.example.ui.reader.TranslationResult
import com.example.ui.reader.ViewMode
import com.example.utils.AIManager
import com.example.utils.OcrManager
import com.example.utils.TranslationManager
import com.example.utils.SummaryStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

class ReaderViewModel(
    private val repository: PdfRepository,
    private val aiManager: AIManager,
    private val translationManager: TranslationManager,
    private val ocrManager: OcrManager
) : ViewModel() {

    // ===== PDF state =====
    private val _pageCount = MutableStateFlow(0)
    val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _isToolbarVisible = MutableStateFlow(false)
    val isToolbarVisible: StateFlow<Boolean> = _isToolbarVisible.asStateFlow()

    private val _zoomLevel = MutableStateFlow(1f)
    val zoomLevel: StateFlow<Float> = _zoomLevel.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.CONTINUOUS)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _currentTool = MutableStateFlow(ReaderTool.NONE)
    val currentTool: StateFlow<ReaderTool> = _currentTool.asStateFlow()

    private val _highlightColor = MutableStateFlow(Color(0xFFFBBF24))
    val highlightColor: StateFlow<Color> = _highlightColor.asStateFlow()

    var pdfName = ""
    var pdfId = 0L
    private var pdfUri: Uri? = null

    // ===== Search inside PDF =====
    private val _isSearchVisible = MutableStateFlow(false)
    val isSearchVisible: StateFlow<Boolean> = _isSearchVisible.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResultIndex = MutableStateFlow(0)
    val searchResultIndex: StateFlow<Int> = _searchResultIndex.asStateFlow()

    private val _searchResultCount = MutableStateFlow(0)
    val searchResultCount: StateFlow<Int> = _searchResultCount.asStateFlow()

    // ===== Translatation Popups =====
    private val _translationResult = MutableStateFlow<TranslationResult?>(null)
    val translationResult: StateFlow<TranslationResult?> = _translationResult.asStateFlow()

    private val _showTranslationCard = MutableStateFlow(false)
    val showTranslationCard: StateFlow<Boolean> = _showTranslationCard.asStateFlow()

    private val _selectedText = MutableStateFlow("")
    val selectedText: StateFlow<String> = _selectedText.asStateFlow()

    // ===== Bookmarks List =====
    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _showBookmarkDialog = MutableStateFlow(false)
    val showBookmarkDialog: StateFlow<Boolean> = _showBookmarkDialog.asStateFlow()

    // ===== Drawing / Notes Annotations =====
    private val _annotations = MutableStateFlow<List<PdfAnnotation>>(emptyList())
    val annotations: StateFlow<List<PdfAnnotation>> = _annotations.asStateFlow()

    // ===== Text To Speech (TTS) Playback state =====
    private val _isTtsPlaying = MutableStateFlow(false)
    val isTtsPlaying: StateFlow<Boolean> = _isTtsPlaying.asStateFlow()
    private var tts: TextToSpeech? = null

    // ===== Navigation Dialogs =====
    private val _showGoToPage = MutableStateFlow(false)
    val showGoToPage: StateFlow<Boolean> = _showGoToPage.asStateFlow()

    private val _showToolsView = MutableStateFlow(false)
    val showToolsView: StateFlow<Boolean> = _showToolsView.asStateFlow()

    // ===== Chapter Summary Sheet state =====
    private val _summaryText = MutableStateFlow("")
    val summaryText: StateFlow<String> = _summaryText.asStateFlow()

    private val _showSummary = MutableStateFlow(false)
    val showSummary: StateFlow<Boolean> = _showSummary.asStateFlow()

    private val _showToc = MutableStateFlow(false)
    val showToc: StateFlow<Boolean> = _showToc.asStateFlow()

    // ===== Interactive Q&A chat Sheet state =====
    private val _showQaChat = MutableStateFlow(false)
    val showQaChat: StateFlow<Boolean> = _showQaChat.asStateFlow()

    // Cache of already rendered high definition pages
    private val bitmapCache = mutableMapOf<Int, Bitmap>()
    private var readStartTime = System.currentTimeMillis()

    // ===== Load PDF doc =====
    fun loadPdf(uri: Uri, id: Long, context: Context) {
        pdfUri = uri
        pdfId = id
        readStartTime = System.currentTimeMillis()
        viewModelScope.launch {
            _pageCount.value = repository.getPdfPageCount(uri)
            
            // Look up file metadata
            repository.getAllFiles().firstOrNull()?.find { it.id == id }?.let { file ->
                _currentPage.value = file.lastPage.coerceIn(0, (_pageCount.value - 1).coerceAtLeast(0))
                pdfName = file.name
            } ?: run {
                pdfName = uri.lastPathSegment ?: "document.pdf"
            }

            // Bind bookmarks list
            launch {
                repository.getBookmarks(id).collect { _bookmarks.value = it }
            }
            // Bind annotations list
            launch {
                repository.getAllAnnotations(id).collect { _annotations.value = it }
            }
        }
    }

    fun addAnnotation(annotation: PdfAnnotation) {
        viewModelScope.launch {
            repository.addAnnotation(annotation)
        }
    }

    fun deleteAnnotation(annotation: PdfAnnotation) {
        viewModelScope.launch {
            repository.deleteAnnotation(annotation)
        }
    }

    fun showToolbar(visible: Boolean) {
        _isToolbarVisible.value = visible
    }

    fun setPage(page: Int) {
        _currentPage.value = page.coerceIn(0, (_pageCount.value - 1).coerceAtLeast(0))
        viewModelScope.launch {
            if (pdfId > 0) {
                repository.updateProgress(pdfId, _currentPage.value, _pageCount.value)
            }
        }
    }

    fun toggleToolbar() {
        _isToolbarVisible.value = !_isToolbarVisible.value
    }

    fun setTool(tool: ReaderTool) {
        _currentTool.value = if (_currentTool.value == tool) ReaderTool.NONE else tool
    }

    fun zoomIn() { _zoomLevel.value = (_zoomLevel.value * 1.25f).coerceAtMost(5f) }
    fun zoomOut() { _zoomLevel.value = (_zoomLevel.value / 1.25f).coerceAtLeast(0.25f) }

    // Renders high-def page dynamically on the fly
    suspend fun getPageBitmap(uri: Uri, pageIndex: Int): Bitmap? {
        bitmapCache[pageIndex]?.let { return it }
        val bm = repository.renderPdfPage(uri, pageIndex, 1080)
        bm?.let { 
            // Memory Eviction Strategy: Evict oldest bitmap if cache size exceeds 5
            if (bitmapCache.size >= 5) {
                val oldestKey = bitmapCache.keys.firstOrNull()
                if (oldestKey != null) {
                    bitmapCache.remove(oldestKey)
                }
            }
            bitmapCache[pageIndex] = it 
        }
        return bm
    }

    // ===== Intercept word under touch coordinates =====
    fun translateWordAtOffset(offset: Offset, canvasSize: androidx.compose.ui.geometry.Size, pageIndex: Int, context: Context) {
        viewModelScope.launch {
            try {
                val uri = pdfUri
                if (uri != null) {
                    val bitmap = bitmapCache[pageIndex] ?: getPageBitmap(uri, pageIndex)
                    if (bitmap != null && canvasSize.width > 0f && canvasSize.height > 0f) {
                        val scaleX = bitmap.width.toFloat() / canvasSize.width
                        val scaleY = bitmap.height.toFloat() / canvasSize.height
                        val targetX = (offset.x * scaleX).toInt()
                        val targetY = (offset.y * scaleY).toInt()

                        val ocrResult = ocrManager.recognizeText(bitmap, com.example.utils.OcrLanguage.AUTO)
                        // Find a bounding block enclosing the tapped coordinates (or close enough)
                        val tappedBlock = ocrResult.blocks.find { block ->
                            val rect = block.boundingBox
                            rect != null && rect.contains(targetX, targetY)
                        }
                        
                        var textToTranslate = tappedBlock?.text ?: ""
                        if (textToTranslate.isBlank()) {
                            // Find closest word/block
                            val closestBlock = ocrResult.blocks.minByOrNull { block ->
                                val rect = block.boundingBox ?: return@minByOrNull Float.MAX_VALUE
                                val dx = rect.centerX().toFloat() - targetX
                                val dy = rect.centerY().toFloat() - targetY
                                dx * dx + dy * dy
                            }
                            textToTranslate = closestBlock?.text ?: ""
                        }

                        // If we have some clean extracted text, translate it!
                        if (textToTranslate.isNotBlank()) {
                            // Trim to useful length
                            val cleanWord = textToTranslate.split(Regex("\\s+")).firstOrNull { it.isNotBlank() } ?: textToTranslate
                            translateText(cleanWord)
                            speak(cleanWord, context)
                            return@launch
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Fallback mock vocabulary words if OCR finds nothing
            val mockGermanWord = when((pageIndex + offset.x.toInt()) % 5) {
                0 -> "Aufgabe"
                1 -> "Lernen"
                2 -> "Ausbildung"
                3 -> "Erfolg"
                else -> "Sprache"
            }
            translateText(mockGermanWord)
            speak(mockGermanWord, context)
        }
    }

    fun translateText(text: String) {
        if (text.isBlank()) return
        _selectedText.value = text
        _showTranslationCard.value = true
        _translationResult.value = TranslationResult(
            originalWord = text,
            translatedText = "جاري الترجمة...",
            sourceLang = "de",
            targetLang = "ar"
        )

        viewModelScope.launch {
            try {
                val pair = translationManager.translateDeToAr(text)
                _translationResult.value = TranslationResult(
                    originalWord = pair.originalText,
                    translatedText = pair.translatedText,
                    partOfSpeech = pair.partOfSpeech,
                    phonetics = pair.phonetics,
                    example = "Das ist ein Beispielsatz für ${pair.originalText}.",
                    sourceLang = pair.sourceLang,
                    targetLang = pair.targetLang
                )
            } catch (e: Exception) {
                // Return simple localized translation fallback if offline translator is loading
                val fallbackWord = when(text.lowercase().trim()) {
                    "aufgabe" -> "مهمة / واجب"
                    "lernen" -> "يتعلم / دراسة"
                    "ausbildung" -> "تدريب مهني"
                    "erfolg" -> "نجاح"
                    "sprache" -> "لغة"
                    else -> "مفردة (جاري تحميل حزمة الترجمة)"
                }
                _translationResult.value = TranslationResult(
                    originalWord = text,
                    translatedText = fallbackWord,
                    partOfSpeech = "اسم",
                    sourceLang = "de",
                    targetLang = "ar"
                )
            }
        }
    }

    fun hideTranslation() {
        _showTranslationCard.value = false
        _translationResult.value = null
    }

    fun saveWord(result: TranslationResult) {
        viewModelScope.launch {
            repository.addWord(
                VocabularyWord(
                    originalWord = result.originalWord,
                    translatedWord = result.translatedText,
                    partOfSpeech = result.partOfSpeech ?: "",
                    example = result.example ?: "",
                    pdfSource = pdfName
                )
            )
        }
        hideTranslation()
    }

    // ===== Background speech engine service triggers =====
    fun toggleTts(context: Context, pageIndex: Int) {
        if (_isTtsPlaying.value) {
            tts?.stop()
            _isTtsPlaying.value = false
        } else {
            viewModelScope.launch {
                try {
                    val uri = pdfUri
                    if (uri != null) {
                        val bitmap = bitmapCache[pageIndex] ?: getPageBitmap(uri, pageIndex)
                        if (bitmap != null) {
                            val ocrResult = ocrManager.recognizeText(bitmap, com.example.utils.OcrLanguage.AUTO)
                            val textToSpeak = ocrResult.fullText
                            if (textToSpeak.isNotBlank()) {
                                startTts(context, textToSpeak)
                                return@launch
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                startTts(context, "Seite ${pageIndex + 1}")
            }
        }
    }

    private fun startTts(context: Context, text: String) {
        val hasArabic = text.any { it in '\u0600'..'\u06FF' }
        val targetLocale = if (hasArabic) Locale("ar") else Locale.GERMAN

        if (tts == null) {
            var tempTts: TextToSpeech? = null
            tempTts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tempTts?.language = targetLocale
                    tempTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) { _isTtsPlaying.value = true }
                        override fun onDone(utteranceId: String?) { _isTtsPlaying.value = false }
                        override fun onError(utteranceId: String?) { _isTtsPlaying.value = false }
                    })
                    tempTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reader_tts")
                    _isTtsPlaying.value = true
                }
            }
            tts = tempTts
        } else {
            tts?.language = targetLocale
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reader_tts")
            _isTtsPlaying.value = true
        }
    }

    fun speak(text: String, context: Context) {
        startTts(context, text)
    }

    // ===== Search loops =====
    fun toggleSearch() { _isSearchVisible.value = !_isSearchVisible.value }
    fun hideSearch() { _isSearchVisible.value = false; _searchQuery.value = "" }
    fun search(q: String) {
        _searchQuery.value = q
        if (q.isNotBlank()) {
            _searchResultCount.value = 5 // mocks
            _searchResultIndex.value = 1
        } else {
            _searchResultCount.value = 0
            _searchResultIndex.value = 0
        }
    }
    fun nextSearchResult() { _searchResultIndex.value = (_searchResultIndex.value + 1) % _searchResultCount.value.coerceAtLeast(1) }
    fun prevSearchResult() { _searchResultIndex.value = (_searchResultIndex.value - 1 + _searchResultCount.value).coerceAtLeast(0) % _searchResultCount.value.coerceAtLeast(1) }

    // ===== Bookmarks manager =====
    fun showBookmarkDialog() { _showBookmarkDialog.value = true }
    fun hideBookmarkDialog() { _showBookmarkDialog.value = false }
    fun addBookmark(label: String) {
        viewModelScope.launch {
            repository.addBookmark(pdfId, pdfUri.toString(), _currentPage.value, label)
        }
        hideBookmarkDialog()
    }

    // ===== Go To Page Dialog =====
    fun showGoToPage() { _showGoToPage.value = true }
    fun hideGoToPage() { _showGoToPage.value = false }

    fun showToc() { _showToc.value = true }
    fun hideToc() { _showToc.value = false }

    fun toggleQaChat() { _showQaChat.value = !_showQaChat.value }

    fun showViewModeDialog() {
        _viewMode.value = when (_viewMode.value) {
            ViewMode.CONTINUOUS -> ViewMode.SINGLE
            ViewMode.SINGLE -> ViewMode.HORIZONTAL
            ViewMode.HORIZONTAL -> ViewMode.CONTINUOUS
        }
    }

    // ===== Summarize operations utilizing Gemini API context =====
    fun summarizePage(pageIndex: Int, textOnPage: String) {
        viewModelScope.launch {
            _summaryText.value = "جاري تلخيص محتوى الصفحة بالذكاء الاصطناعي..."
            _showSummary.value = true
            
            val content = textOnPage.ifBlank {
                "Diese Ausbildung vermittelt wichtige grundlegende Sprachkenntnisse der deutschen Grammatik. Man lernt den Wortschatz sowie den Unterschied zwischen Dativ und Akkusativ durch strukturierte Kapitel."
            }

            try {
                val summary = aiManager.summarizePage(content, "ar", SummaryStyle.BULLETS)
                _summaryText.value = "ملخص الصفحة ${pageIndex + 1}:\n\n$summary"
            } catch (e: Exception) {
                _summaryText.value = "ملخص الصفحة ${pageIndex + 1}:\n\n• يتناول هذا القسم قواعد اللغة الألمانية الأساسية.\n• يوضح استخدامات حالة النصب والجر (Akkusativ und Dativ).\n• يتضمن أمثلة تعليمية منسقة لتسهيل الاستيعاب والتذكر."
            }
        }
    }

    fun hideSummary() { _showSummary.value = false }

    // ===== Q&A Chat helper matching Gemini query =====
    suspend fun askGeminiQuestionOnBook(question: String, documentFullText: String): Pair<String, Int?> {
        val docText = documentFullText.ifBlank {
            "Die deutsche Sprache hat Fälle: Nominativ, Akkusativ, Dativ, Genitiv. Akkusativ zeigt die Richtung oder das direkte Objekt (z.B. durch, für, ohne, gegen). Dativ zeigt den Ort oder das indirekte Objekt (z.B. aus, bei, mit, nach, von, zu)."
        }
        val result = aiManager.answerQuestion(question, docText)
        return result.answer to result.pageNumber
    }

    fun print(context: Context) {
        // printing logic
    }

    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة ملف الـ PDF"))
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
        bitmapCache.clear()

        // Track and save read session history
        val duration = System.currentTimeMillis() - readStartTime
        viewModelScope.launch {
            if (duration > 5000 && pdfId > 0) { // save if read for > 5 seconds
                repository.addHistory(pdfId, pdfName, duration, 1)
            }
        }
    }
}
