package com.example.viewmodel

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.PdfRepository
import com.example.utils.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Suppress("OPT_IN_USAGE")
class MainViewModel(
    private val context: Context,
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

    private val _homeState = MutableStateFlow(HomeState())
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()

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
        loadRecentFiles()
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

    // ===== PDF Tools State =====
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _successMessage = MutableStateFlow("")
    val successMessage: StateFlow<String> = _successMessage.asStateFlow()

    private val _showSuccess = MutableStateFlow(false)
    val showSuccess: StateFlow<Boolean> = _showSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private val _splitPdfUri = MutableStateFlow<Uri?>(null)
    val splitPdfUri: StateFlow<Uri?> = _splitPdfUri.asStateFlow()

    private val _splitTotalPages = MutableStateFlow(0)
    val splitTotalPages: StateFlow<Int> = _splitTotalPages.asStateFlow()

    fun dismissSuccess() { _showSuccess.value = false; _successMessage.value = "" }
    fun dismissError() { _errorMessage.value = "" }

    fun clearSplitData() {
        _splitPdfUri.value = null
        _splitTotalPages.value = 0
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    }

    fun loadPdfForSplit(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    val renderer = android.graphics.pdf.PdfRenderer(pfd)
                    val pageCount = renderer.pageCount
                    renderer.close()
                    pfd.close()
                    _splitPdfUri.value = uri
                    _splitTotalPages.value = pageCount
                } else {
                    _errorMessage.value = "لا يمكن فتح الملف المحدد"
                }
            } catch (e: Exception) {
                _errorMessage.value = "خطأ في قراءة ملف PDF: ${e.message}"
            }
        }
    }

    fun compressPdf(inputUri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _progress.value = 0f
            
            try {
                val outDir = File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    ), "PDFReader"
                ).also { it.mkdirs() }
                
                val outFile = File(outDir, "compressed_${System.currentTimeMillis()}.pdf")
                
                val pfd = context.contentResolver.openFileDescriptor(inputUri, "r")!!
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                
                _progress.value = 0.1f
                
                val doc = android.graphics.pdf.PdfDocument()
                val pageCount = renderer.pageCount
                
                for (i in 0 until pageCount) {
                    _progress.value = (i.toFloat() / pageCount) * 0.8f + 0.1f
                    
                    val page = renderer.openPage(i)
                    
                    val scale = 0.6f
                    val width = (page.width * scale).toInt()
                    val height = (page.height * scale).toInt()
                    
                    val bitmap = Bitmap.createBitmap(
                        width, height, Bitmap.Config.RGB_565
                    )
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, 
                        android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page.close()
                    
                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(
                        width, height, i + 1
                    ).create()
                    val docPage = doc.startPage(pageInfo)
                    docPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    doc.finishPage(docPage)
                    bitmap.recycle()
                }
                
                renderer.close()
                pfd.close()
                
                _progress.value = 0.95f
                FileOutputStream(outFile).use { doc.writeTo(it) }
                doc.close()
                _progress.value = 1f
                
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(outFile.absolutePath), null, null
                )
                
                val originalSize = getFileSize(context, inputUri)
                val newSize = outFile.length()
                val reduction = ((originalSize - newSize) * 100 / originalSize.coerceAtLeast(1))
                
                repository.addFile(Uri.fromFile(outFile))
                
                withContext(Dispatchers.Main) {
                    _successMessage.value = """
                        ✅ تم ضغط الملف بنجاح!
                        📦 الحجم الأصلي: ${formatSize(originalSize)}
                        🗜️ الحجم الجديد: ${formatSize(newSize)}
                        📉 نسبة التوفير: $reduction%
                        📂 محفوظ في: Download/PDFReader/
                        ✨ وتم إضافته إلى مكتبتك تلقائياً!
                    """.trimIndent()
                    _showSuccess.value = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "فشل الضغط: ${e.message}"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun mergePdfs(inputUris: List<Uri>, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _progress.value = 0f
            
            try {
                val outDir = File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    ), "PDFReader"
                ).also { it.mkdirs() }
                
                val outFile = File(outDir, "merged_${System.currentTimeMillis()}.pdf")
                val doc = android.graphics.pdf.PdfDocument()
                var globalPageNum = 1
                val totalFiles = inputUris.size
                
                inputUris.forEachIndexed { fileIdx, uri ->
                    _progress.value = fileIdx.toFloat() / totalFiles
                    
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@forEachIndexed
                    val renderer = android.graphics.pdf.PdfRenderer(pfd)
                    
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        val bitmap = Bitmap.createBitmap(
                            page.width, page.height, Bitmap.Config.ARGB_8888
                        )
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null,
                            android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        page.close()
                        
                        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(
                            bitmap.width, bitmap.height, globalPageNum++
                        ).create()
                        val docPage = doc.startPage(pageInfo)
                        docPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                        doc.finishPage(docPage)
                        bitmap.recycle()
                    }
                    
                    renderer.close()
                    pfd.close()
                }
                
                _progress.value = 0.95f
                FileOutputStream(outFile).use { doc.writeTo(it) }
                doc.close()
                _progress.value = 1f
                
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(outFile.absolutePath), null, null
                )
                
                repository.addFile(Uri.fromFile(outFile))
                
                withContext(Dispatchers.Main) {
                    _successMessage.value = """
                        ✅ تم دمج ${inputUris.size} ملفات بنجاح!
                        📄 عدد الصفحات الكلي: ${globalPageNum - 1}
                        📂 محفوظ في: Download/PDFReader/
                        📝 الاسم: ${outFile.name}
                        ✨ وتم إضافته إلى مكتبتك تلقائياً!
                    """.trimIndent()
                    _showSuccess.value = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "فشل الدمج: ${e.message}"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun imagesToPdf(imageUris: List<Uri>, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _progress.value = 0f
            
            try {
                val outDir = File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    ), "PDFReader"
                ).also { it.mkdirs() }
                
                val outFile = File(outDir, "images_to_pdf_${System.currentTimeMillis()}.pdf")
                val doc = android.graphics.pdf.PdfDocument()
                val totalImages = imageUris.size
                
                imageUris.forEachIndexed { idx, uri ->
                    _progress.value = idx.toFloat() / totalImages
                    
                    val stream = context.contentResolver.openInputStream(uri) ?: return@forEachIndexed
                    val bitmap = BitmapFactory.decodeStream(stream)
                    stream.close()
                    
                    if (bitmap == null) return@forEachIndexed
                    
                    val a4Width = 595
                    val a4Height = 842
                    
                    val scale = minOf(
                        a4Width.toFloat() / bitmap.width,
                        a4Height.toFloat() / bitmap.height
                    )
                    val scaledWidth = (bitmap.width * scale).toInt()
                    val scaledHeight = (bitmap.height * scale).toInt()
                    val scaledBitmap = Bitmap.createScaledBitmap(
                        bitmap, scaledWidth, scaledHeight, true
                    )
                    bitmap.recycle()
                    
                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(
                        a4Width, a4Height, idx + 1
                    ).create()
                    val page = doc.startPage(pageInfo)
                    
                    val left = (a4Width - scaledWidth) / 2f
                    val top = (a4Height - scaledHeight) / 2f
                    
                    page.canvas.drawColor(android.graphics.Color.WHITE)
                    page.canvas.drawBitmap(scaledBitmap, left, top, null)
                    doc.finishPage(page)
                    scaledBitmap.recycle()
                }
                
                _progress.value = 0.95f
                FileOutputStream(outFile).use { doc.writeTo(it) }
                doc.close()
                _progress.value = 1f
                
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(outFile.absolutePath), null, null
                )
                
                repository.addFile(Uri.fromFile(outFile))
                
                withContext(Dispatchers.Main) {
                    _successMessage.value = """
                        ✅ تم تحويل ${imageUris.size} صور إلى PDF!
                        📂 محفوظ في: Download/PDFReader/
                        📝 الاسم: ${outFile.name}
                        ✨ وتم إضافته إلى مكتبتك تلقائياً!
                    """.trimIndent()
                    _showSuccess.value = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "فشل التحويل: ${e.message}"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun splitPdf(inputUri: Uri, fromPage: Int, toPage: Int, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _progress.value = 0f
            
            try {
                val outDir = File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    ), "PDFReader"
                ).also { it.mkdirs() }
                
                val outFile = File(outDir, "split_p${fromPage}_p${toPage}_${System.currentTimeMillis()}.pdf")
                
                val pfd = context.contentResolver.openFileDescriptor(inputUri, "r")!!
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                val doc = android.graphics.pdf.PdfDocument()
                
                val startIdx = (fromPage - 1).coerceAtLeast(0)
                val endIdx = (toPage - 1).coerceAtMost(renderer.pageCount - 1)
                val totalPages = endIdx - startIdx + 1
                
                for (i in startIdx..endIdx) {
                    _progress.value = (i - startIdx).toFloat() / totalPages
                    
                    val page = renderer.openPage(i)
                    val bitmap = Bitmap.createBitmap(
                        page.width, page.height, Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null,
                        android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page.close()
                    
                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(
                        bitmap.width, bitmap.height, i - startIdx + 1
                    ).create()
                    val docPage = doc.startPage(pageInfo)
                    docPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    doc.finishPage(docPage)
                    bitmap.recycle()
                }
                
                renderer.close()
                pfd.close()
                
                _progress.value = 0.95f
                FileOutputStream(outFile).use { doc.writeTo(it) }
                doc.close()
                _progress.value = 1f
                
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(outFile.absolutePath), null, null
                )
                
                repository.addFile(Uri.fromFile(outFile))
                
                withContext(Dispatchers.Main) {
                    _successMessage.value = """
                        ✅ تم تقسيم الملف بنجاح!
                        📄 الصفحات المستخرجة: $fromPage → $toPage ($totalPages صفحة)
                        📂 محفوظ في: Download/PDFReader/
                        ✨ وتم إضافته إلى مكتبتك تلقائياً!
                    """.trimIndent()
                    _showSuccess.value = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "فشل التقسيم: ${e.message}"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadRecentFiles() {
        viewModelScope.launch {
            repository.getAllPdfFiles().collect { files ->
                _homeState.update {
                    it.copy(
                        recentFiles = files
                            .sortedByDescending { f -> f.lastOpened }
                            .take(10),   // آخر 10 ملفات
                        favoriteCount = files.count { f -> f.isFavorite },
                        totalCount = files.size
                    )
                }
            }
        }
    }

    // ═══ دالة المسح الحقيقية ═══
    fun scanForPdfs() {
        android.util.Log.d("SCAN_DEBUG", "scanForPdfs() called")
        if (_homeState.value.isScanning) {
            android.util.Log.d("SCAN_DEBUG", "scanForPdfs() aborted - already scanning")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _homeState.update { it.copy(isScanning = true, scanMessage = "") }
            android.util.Log.d("SCAN_DEBUG", "scanForPdfs: started coroutine")

            try {
                val foundPdfs = mutableListOf<PdfFile>()

                // 1. MediaStore Scan (Great for standard indexed files)
                android.util.Log.d("SCAN_DEBUG", "Querying MediaStore...")
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.DATA,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.DATE_MODIFIED
                )
                val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
                val selectionArgs = arrayOf("application/pdf")
                val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

                try {
                    context.contentResolver.query(
                        MediaStore.Files.getContentUri("external"),
                        projection,
                        selection,
                        selectionArgs,
                        sortOrder
                    )?.use { cursor ->
                        val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                        val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                        android.util.Log.d("SCAN_DEBUG", "MediaStore returned ${cursor.count} initial entries")

                        while (cursor.moveToNext()) {
                            val id       = cursor.getLong(idCol)
                            val name     = cursor.getString(nameCol) ?: continue
                            val path     = cursor.getString(dataCol) ?: continue
                            val size     = cursor.getLong(sizeCol)
                            val modified = cursor.getLong(dateCol) * 1000L

                            // بناء Content URI صحيح
                            val contentUri = ContentUris.withAppendedId(
                                MediaStore.Files.getContentUri("external"), id
                            )

                            // خذ persistent permission
                            try {
                                context.contentResolver.takePersistableUriPermission(
                                    contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            } catch (_: Exception) {}

                            // تحقق لو الملف موجود مسبقاً في Room بـ path أو contentUri
                            val existing = repository.getPdfFileByPath(contentUri.toString()) ?: repository.getPdfFileByPath(path)
                            if (existing == null) {
                                val pageCount = getPageCount(contentUri)
                                val thumbPath = generateThumbnail(contentUri, name)

                                android.util.Log.d("SCAN_DEBUG", "MediaStore found new PDF: $name, path: $path")
                                foundPdfs.add(
                                    PdfFile(
                                        name        = name.removeSuffix(".pdf").removeSuffix(".PDF"),
                                        path        = path,
                                        uri         = contentUri.toString(),
                                        size        = size,
                                        pageCount   = pageCount,
                                        thumbnailPath = thumbPath ?: "",
                                        lastOpened  = modified,
                                        dateAdded   = System.currentTimeMillis(),
                                        category    = guessCategory(name)
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SCAN_DEBUG", "Error during MediaStore scan: ${e.message}", e)
                }

                // 2. Direct File System Scan (Runs if MANAGE_EXTERNAL_STORAGE is granted, to find unindexed PDFs immediately)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val isManager = android.os.Environment.isExternalStorageManager()
                    android.util.Log.d("SCAN_DEBUG", "All Files Access Manager Check: $isManager")
                    if (isManager) {
                        try {
                            android.util.Log.d("SCAN_DEBUG", "Starting direct filesystem scan of common directories...")
                            // scan Documents and Downloads first (highly likely to hold PDFs, and very fast to traverse)
                            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                            
                            android.util.Log.d("SCAN_DEBUG", "Direct scan: Scanning Downloads dir: ${downloadsDir.absolutePath}")
                            scanDirRecursively(downloadsDir, 1, foundPdfs)

                            android.util.Log.d("SCAN_DEBUG", "Direct scan: Scanning Documents dir: ${documentsDir.absolutePath}")
                            scanDirRecursively(documentsDir, 1, foundPdfs)
                            
                            // Let's also scan the root of storage but with a shallow limit (depth <= 3) so it doesn't hang
                            val rootDir = Environment.getExternalStorageDirectory()
                            android.util.Log.d("SCAN_DEBUG", "Direct scan: Scanning Root Storage: ${rootDir.absolutePath}")
                            scanDirRecursively(rootDir, 1, foundPdfs)
                        } catch (e: Exception) {
                            android.util.Log.e("SCAN_DEBUG", "Error during direct filesystem scan: ${e.message}", e)
                        }
                    }
                }

                //احفظ الملفات المكتشفة في قاعدة البيانات
                android.util.Log.d("SCAN_DEBUG", "Saving ${foundPdfs.size} scanned PDFs to DB")
                foundPdfs.forEach { repository.insertPdfFile(it) }

                _homeState.update {
                    it.copy(
                        isScanning = false,
                        scanMessage = "تم العثور على ${foundPdfs.size} ملف جديد"
                    )
                }

            } catch (e: Exception) {
                android.util.Log.e("SCAN_DEBUG", "Scanning exception: ${e.message}", e)
                _homeState.update {
                    it.copy(
                        isScanning = false,
                        scanMessage = "خطأ في المسح: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun scanDirRecursively(dir: File, depth: Int, foundPdfs: MutableList<PdfFile>) {
        if (depth > 4) return // Depth limit to keep it fast and responsive
        if (!dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                val nameLower = file.name.lowercase()
                if (!file.name.startsWith(".") &&
                    nameLower != "android" &&
                    nameLower != "data" &&
                    nameLower != "cache" &&
                    nameLower != "obb" &&
                    nameLower != "mipmaps"
                ) {
                    scanDirRecursively(file, depth + 1, foundPdfs)
                }
            } else if (file.isFile && file.name.lowercase().endsWith(".pdf")) {
                val path = file.absolutePath
                val fileUri = Uri.fromFile(file).toString()
                
                // تحقق إذا كان الملف موجود مسبقاً في Room بـ path أو uri
                val existing = repository.getPdfFileByPath(fileUri) ?: repository.getPdfFileByPath(path)
                if (existing == null) {
                    val pageCount = getPageCount(Uri.fromFile(file))
                    val thumbPath = generateThumbnail(Uri.fromFile(file), file.name)
                    
                    // تأكد من عدم الإضافة المزدوجة في نفس العملية
                    val alreadyAddedInThisScan = foundPdfs.any { it.path == path || it.uri == fileUri }
                    if (!alreadyAddedInThisScan) {
                        android.util.Log.d("SCAN_DEBUG", "Direct Scan found new PDF: ${file.name}")
                        foundPdfs.add(
                            PdfFile(
                                name        = file.name.removeSuffix(".pdf").removeSuffix(".PDF"),
                                path        = path,
                                uri         = fileUri,
                                size        = file.length(),
                                pageCount   = pageCount,
                                thumbnailPath = thumbPath ?: "",
                                lastOpened  = file.lastModified(),
                                dateAdded   = System.currentTimeMillis(),
                                category    = guessCategory(file.name)
                            )
                        )
                    }
                }
            }
        }
    }

    // ═══ دوال مساعدة ═══

    private fun getPageCount(uri: Uri): Int {
        return try {
            val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return 0
            val renderer = PdfRenderer(fd)
            val count = renderer.pageCount
            renderer.close(); fd.close()
            count
        } catch (_: Exception) { 0 }
    }

    private fun generateThumbnail(uri: Uri, name: String): String? {
        return try {
            val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            val renderer = PdfRenderer(fd)
            if (renderer.pageCount == 0) { renderer.close(); fd.close(); return null }
            val page = renderer.openPage(0)
            val w = 200
            val h = (page.height * (w.toFloat() / page.width)).toInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close(); renderer.close(); fd.close()

            val dir = File(context.cacheDir, "thumbs").also { it.mkdirs() }
            val file = File(dir, "${name.hashCode()}.jpg")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 75, it) }
            file.absolutePath
        } catch (_: Exception) { null }
    }

    private fun guessCategory(name: String): String {
        val l = name.lowercase()
        return when {
            l.contains("exam") || l.contains("test") || l.contains("امتحان") -> "tests"
            l.contains("book") || l.contains("كتاب") || l.contains("buch") -> "books"
            l.contains("lektion") || l.contains("netzwerk") || l.contains("deutsch") -> "books"
            l.contains("report") || l.contains("تقرير") -> "reports"
            else -> "documents"
        }
    }
}

data class HomeState(
    val recentFiles: List<PdfFile> = emptyList(),
    val isScanning: Boolean = false,
    val favoriteCount: Int = 0,
    val totalCount: Int = 0,
    val scanMessage: String = ""
)
