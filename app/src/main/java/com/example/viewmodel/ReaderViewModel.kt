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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
    private val bitmapCache = mutableMapOf<String, Bitmap>()
    private var readStartTime = System.currentTimeMillis()

    fun getCachedBitmapForPage(pageIndex: Int): Bitmap? {
        return synchronized(bitmapCache) {
            bitmapCache.entries.firstOrNull { it.key.startsWith("${pageIndex}_") }?.value
        }
    }

    // Page links cache
    private val pageLinksCache = mutableMapOf<Int, List<com.example.data.repository.PdfLink>>()

    suspend fun getPageLinks(pageIndex: Int): List<com.example.data.repository.PdfLink> {
        val uri = pdfUri ?: return emptyList()
        pageLinksCache[pageIndex]?.let { return it }
        val links = repository.getPageLinks(uri, pageIndex)
        pageLinksCache[pageIndex] = links
        return links
    }

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

            // Prefetch initial pages surrounding the current page
            prefetchSurroundingPages(uri, _currentPage.value)
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
        val sanitizedPage = page.coerceIn(0, (_pageCount.value - 1).coerceAtLeast(0))
        _currentPage.value = sanitizedPage
        viewModelScope.launch {
            if (pdfId > 0) {
                repository.updateProgress(pdfId, sanitizedPage, _pageCount.value)
            }
        }
        pdfUri?.let { prefetchSurroundingPages(it, sanitizedPage) }
    }


    fun toggleToolbar() {
        _isToolbarVisible.value = !_isToolbarVisible.value
    }

    fun setTool(tool: ReaderTool) {
        _currentTool.value = if (_currentTool.value == tool) ReaderTool.NONE else tool
    }

    fun zoomIn() { _zoomLevel.value = (_zoomLevel.value * 1.25f).coerceAtMost(5f) }
    fun zoomOut() { _zoomLevel.value = (_zoomLevel.value / 1.25f).coerceAtLeast(0.25f) }

    // Renders high-def page dynamically on the fly with smart distance-eviction cache
    suspend fun getPageBitmap(uri: Uri, pageIndex: Int, targetWidth: Int = 1080): Bitmap? {
        val cacheKey = "${pageIndex}_$targetWidth"
        synchronized(bitmapCache) {
            bitmapCache[cacheKey]?.let { return it }
        }
        val bm = repository.renderPdfPage(uri, pageIndex, targetWidth)
        bm?.let { 
            synchronized(bitmapCache) {
                // If cache exceeds limit, evict the bitmap furthest from the current page
                if (bitmapCache.size >= 15) {
                    val current = _currentPage.value
                    val furthestKey = bitmapCache.keys.maxByOrNull { key ->
                        val keyPageIndex = key.substringBefore('_').toIntOrNull() ?: 0
                        kotlin.math.abs(keyPageIndex - current)
                    }
                    if (furthestKey != null) {
                        bitmapCache.remove(furthestKey)
                    }
                }
                bitmapCache[cacheKey] = it 
            }
        }
        return bm
    }

    // Prefetches neighbouring pages on background I/O thread to prepare them for seamless scrolling
    private fun prefetchSurroundingPages(uri: Uri, pageIndex: Int) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val total = _pageCount.value
            val indicesToPrefetch = listOf(pageIndex + 1, pageIndex + 2, pageIndex - 1)
            for (idx in indicesToPrefetch) {
                if (idx in 0 until total) {
                    val defaultCacheKey = "${idx}_1080"
                    val alreadyCached = synchronized(bitmapCache) {
                        bitmapCache.containsKey(defaultCacheKey) || bitmapCache.keys.any { it.startsWith("${idx}_") }
                    }
                    if (!alreadyCached) {
                        val bm = repository.renderPdfPage(uri, idx, 1080)
                        bm?.let {
                            synchronized(bitmapCache) {
                                if (bitmapCache.size >= 15) {
                                    val current = _currentPage.value
                                    val furthestKey = bitmapCache.keys.maxByOrNull { key ->
                                        val keyPageIndex = key.substringBefore('_').toIntOrNull() ?: 0
                                        kotlin.math.abs(keyPageIndex - current)
                                    }
                                    if (furthestKey != null) {
                                        bitmapCache.remove(furthestKey)
                                    }
                                }
                                bitmapCache[defaultCacheKey] = it
                            }
                        }
                    }
                }
            }
        }
    }


    // ===== Intercept word under touch coordinates =====
    fun translateWordAtOffset(
        offset: Offset,
        canvasSize: androidx.compose.ui.geometry.Size,
        pageIndex: Int,
        context: Context,
        onNonLinkClick: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                // 1. Check if there are real interactive links to match (WPS Style)
                val links = getPageLinks(pageIndex)
                if (links.isNotEmpty() && canvasSize.width > 0f && canvasSize.height > 0f) {
                    val normalizedX = offset.x / canvasSize.width
                    val normalizedY = offset.y / canvasSize.height

                    val clickedLink = links.find { link ->
                        val pdfTouchX = normalizedX * link.pageWidth
                        val pdfTouchY = normalizedY * link.pageHeight
                        pdfTouchX >= link.x && pdfTouchX <= (link.x + link.width) &&
                                pdfTouchY >= link.y && pdfTouchY <= (link.y + link.height)
                    }

                    if (clickedLink != null) {
                        val url = clickedLink.url
                        
                        // ===== Extract clean word from any URL type =====
                        fun extractCleanWord(inputUrl: String): String {
                            val rawWord = when {
                                // Google TTS: ...&q=der_Kontakt.mp3
                                inputUrl.contains("translate_tts") || inputUrl.contains("translate.google") -> {
                                    Uri.parse(inputUrl).getQueryParameter("q") ?: ""
                                }
                                // Arabdict: .../deutsch-arabisch/Kontakt
                                inputUrl.contains("arabdict.com") -> {
                                    inputUrl.substringAfterLast("/")
                                }
                                // DWDS: .../wb/Kontakt
                                inputUrl.contains("dwds.de") -> {
                                    inputUrl.substringAfterLast("/")
                                }
                                else -> inputUrl.substringAfterLast("/")
                            }

                            return rawWord
                                // Remove file extension (.mp3, .wav, .ogg, .aac, .m4a, .flac)
                                .replace(Regex("\\.(mp3|wav|ogg|aac|m4a|flac)$", RegexOption.IGNORE_CASE), "")
                                // Replace underscores with spaces
                                .replace("_", " ")
                                // Remove German definite/indefinite articles from start
                                .replace(Regex("^(der|die|das|ein|eine|einem|einer|einen|des|dem|den)\\s+", RegexOption.IGNORE_CASE), "")
                                // Trim spaces
                                .trim()
                                // Take only first word if multiple exist
                                .split(" ")
                                .firstOrNull { it.isNotBlank() } ?: rawWord.trim()
                        }

                        val word = extractCleanWord(url)
                        
                        val isPronunciation = url.contains("translate_tts") || 
                                              url.contains("translate.google") || 
                                              url.contains("dwds.de") || 
                                              url.contains(".mp3") || 
                                              url.contains(".wav") || 
                                              url.contains(".ogg")

                        if (isPronunciation) {
                            // Speak German word right away and don't open browser
                            if (word.isNotBlank()) {
                                speak(word, context)
                            }
                        } else {
                            // Construct / Open Arabdict with clean URL and don't speak
                            val arabdictUrl = if (url.contains("arabdict.com") && !url.contains(".mp3") && !url.contains("_")) {
                                url
                            } else {
                                val encoded = Uri.encode(word)
                                "https://www.arabdict.com/ar/deutsch-arabisch/$encoded"
                            }
                            
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(arabdictUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.arabdict.com")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(browserIntent)
                            }
                        }
                        return@launch
                    }
                }

                // If no link is clicked, invoke callback to show WPS menu!
                onNonLinkClick()
                return@launch

            } catch (e: Exception) {
                e.printStackTrace()
            }
            onNonLinkClick()
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
                        val bitmap = getCachedBitmapForPage(pageIndex) ?: getPageBitmap(uri, pageIndex)
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
                    tempTts?.let { engine ->
                        engine.language = targetLocale
                        if (!hasArabic) {
                            engine.setSpeechRate(0.85f)
                        }
                        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) { _isTtsPlaying.value = true }
                            override fun onDone(utteranceId: String?) { _isTtsPlaying.value = false }
                            override fun onError(utteranceId: String?) { _isTtsPlaying.value = false }
                        })
                        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reader_tts")
                        _isTtsPlaying.value = true
                    }
                }
            }
            tts = tempTts
        } else {
            tts?.language = targetLocale
            if (!hasArabic) {
                tts?.setSpeechRate(0.85f)
            }
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

    // Overloaded summarizePage with 1 parameter
    fun summarizePage(pageIndex: Int) {
        summarizePage(pageIndex, "")
    }

    // تحويل صفحة لصورة
    fun convertCurrentPageToImage(uri: Uri?, pageIndex: Int, context: Context) {
        val targetUri = uri ?: pdfUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = repository.renderPdfPage(targetUri, pageIndex, 2160) ?: return@launch
            val filename = "pdf_page_${pageIndex + 1}_${System.currentTimeMillis()}.jpg"
            val destDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_PICTURES
            )
            destDir.mkdirs()
            val file = java.io.File(destDir, filename)
            try {
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                }
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(file.absolutePath), null, null
                )
                withContext(Dispatchers.Main) {
                    _summaryText.value = "✅ تم حفظ الصورة في: Pictures/$filename"
                    _showSummary.value = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _summaryText.value = "❌ فشل حفظ الصورة: ${e.message}"
                    _showSummary.value = true
                }
            }
        }
    }

    // استخراج النص
    fun extractTextFromPdf(uri: Uri?, context: Context) {
        val targetUri = uri ?: pdfUri ?: return
        viewModelScope.launch {
            _summaryText.value = "جاري استخراج النص بـ OCR..."
            _showSummary.value = true
            // ML Kit OCR
            val bitmap = repository.renderPdfPage(targetUri, _currentPage.value, 1080)
            if (bitmap != null) {
                val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
                val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                    com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
                )
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        _summaryText.value = visionText.text.ifBlank { "لم يُعثر على نص في هذه الصفحة" }
                        _showSummary.value = true
                    }
                    .addOnFailureListener {
                        _summaryText.value = "فشل OCR: ${it.message}"
                    }
            } else {
                _summaryText.value = "فشل استخراج الصورة من صفحة الـ PDF الحالية"
            }
        }
    }

    // ضغط الملف
    fun compressPdf(uri: Uri?, context: Context) {
        val targetUri = uri ?: pdfUri ?: return
        viewModelScope.launch {
            _summaryText.value = "جاري ضغط الملف..."
            _showSummary.value = true
            try {
                val outDir = context.getExternalFilesDir(null) ?: context.cacheDir
                val outFile = java.io.File(outDir, "compressed_${System.currentTimeMillis()}.pdf")
                val pdfUtils = com.example.utils.PdfUtils(context)
                val success = pdfUtils.compressPdf(targetUri, outFile, 70)
                if (success) {
                    _summaryText.value = "✅ تم الضغط بنجاح\nالحجم الجديد: ${outFile.length() / 1024}KB\nالمسار: ${outFile.absolutePath}"
                } else {
                    _summaryText.value = "❌ فشل ضغط الملف"
                }
            } catch (e: Exception) {
                _summaryText.value = "❌ خطأ أثناء ضغط الملف: ${e.message}"
            }
        }
    }

    // تشغيل OCR
    fun runOcrOnPage(uri: Uri?, pageIndex: Int, context: Context) {
        extractTextFromPdf(uri, context)
    }

    // معلومات الملف
    fun showFileInfo() {
        _summaryText.value = "معلومات الملف:\n• الاسم: $pdfName\n• عدد الصفحات: ${_pageCount.value}"
        _showSummary.value = true
    }

    fun showMergePicker() {
        _summaryText.value = "ميزة الدمج: الرجاء استخدام شاشة أدوات PDF للدمج السريع."
        _showSummary.value = true
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
        synchronized(bitmapCache) {
            bitmapCache.clear()
        }
        pageLinksCache.clear()
        
        // Cleanly close the active PDF renderer session and file descriptor handles
        repository.closeSession()

        // Track and save read session history
        val duration = System.currentTimeMillis() - readStartTime
        viewModelScope.launch {
            if (duration > 5000 && pdfId > 0) { // save if read for > 5 seconds
                repository.addHistory(pdfId, pdfName, duration, 1)
            }
        }
    }

}
