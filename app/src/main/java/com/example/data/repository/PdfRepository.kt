package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.example.data.db.*
import com.example.data.model.*
import com.example.data.model.Annotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.os.ParcelFileDescriptor

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI

data class PdfLink(
    val url: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val pageWidth: Float,
    val pageHeight: Float
)

class PdfRepository(
    private val context: Context,
    private val db: AppDatabase
) {
    private val pdfFileDao = db.pdfFileDao()
    private val bookmarkDao = db.bookmarkDao()
    private val annotationDao = db.annotationDao()
    private val vocabularyDao = db.vocabularyDao()
    private val readHistoryDao = db.readHistoryDao()
    private val ocrPageTextDao = db.ocrPageTextDao()

    // ========== OCR Page Text ==========
    suspend fun getPageOcrText(pdfId: Long, page: Int): OcrPageText? = withContext(Dispatchers.IO) {
        ocrPageTextDao.getPageText(pdfId, page)
    }

    suspend fun getAllOcrPageTexts(pdfId: Long): List<OcrPageText> = withContext(Dispatchers.IO) {
        ocrPageTextDao.getAllForPdf(pdfId)
    }

    suspend fun savePageOcrText(pdfId: Long, page: Int, text: String, coordsJson: String) = withContext(Dispatchers.IO) {
        ocrPageTextDao.insert(OcrPageText(pdfId = pdfId, page = page, text = text, wordCoordinatesJson = coordsJson))
    }

    // Reusable active PdfRenderer session to achieve fast 60FPS WPS Office style rendering
    @Volatile
    private var activeUri: Uri? = null
    private var activePfd: ParcelFileDescriptor? = null
    private var activeRenderer: PdfRenderer? = null

    @Synchronized
    private fun getCachedRenderer(uri: Uri): PdfRenderer? {
        if (activeUri == uri && activeRenderer != null) {
            return activeRenderer
        }
        closeActiveRendererInternal()
        try {
            val pfd = openPfdForUri(uri)
            if (pfd != null) {
                activeUri = uri
                activePfd = pfd
                activeRenderer = PdfRenderer(pfd)
                return activeRenderer
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun closeActiveRendererInternal() {
        try { activeRenderer?.close() } catch (_: Exception) {}
        try { activePfd?.close() } catch (_: Exception) {}
        activeRenderer = null
        activePfd = null
        activeUri = null
    }

    fun closeSession() {
        synchronized(this) {
            closeActiveRendererInternal()
        }
    }


    // ========== Files ==========
    fun getAllFiles(): Flow<List<PdfFile>> = pdfFileDao.getAllFiles()
    fun getRecentFiles(): Flow<List<PdfFile>> = pdfFileDao.getRecent()
    fun getFavorites(): Flow<List<PdfFile>> = pdfFileDao.getFavorites()
    fun searchFiles(query: String): Flow<List<PdfFile>> = pdfFileDao.search(query)
    fun getByCategory(cat: String): Flow<List<PdfFile>> = pdfFileDao.getByCategory(cat)
    
    fun getAllPdfFiles(): Flow<List<PdfFile>> = pdfFileDao.getAllFiles()

    suspend fun getPdfFileByPath(path: String): PdfFile? = withContext(Dispatchers.IO) {
        pdfFileDao.getByPath(path)
    }

    suspend fun insertPdfFile(file: PdfFile): Long = withContext(Dispatchers.IO) {
        pdfFileDao.insert(file)
    }

    suspend fun addFile(uri: Uri): PdfFile? = withContext(Dispatchers.IO) {
        try {
            val name = getFileNameFromUri(uri) ?: "doc_${System.currentTimeMillis()}.pdf"
            val size = getFileSizeFromUri(uri)
            val path = uri.toString()

            // Check if already loaded
            val existing = pdfFileDao.getByPath(path)
            if (existing != null) {
                pdfFileDao.update(existing.copy(lastOpened = System.currentTimeMillis()))
                return@withContext existing
            }

            val pageCount = getPdfPageCount(uri)
            val thumbnailPath = generateThumbnail(uri, name)

            val file = PdfFile(
                name = name,
                path = path,
                uri = path,
                size = size,
                pageCount = pageCount,
                thumbnailPath = thumbnailPath,
                category = guessCategory(name)
            )
            val id = pdfFileDao.insert(file)
            file.copy(id = id)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun updatePdfFile(file: PdfFile) {
        pdfFileDao.update(file)
    }

    suspend fun updateProgress(id: Long, page: Int, totalPages: Int) {
        val progress = if (totalPages > 0) page.toFloat() / totalPages else 0f
        pdfFileDao.updateProgress(id, page, progress)
    }

    suspend fun toggleFavorite(file: PdfFile) {
        pdfFileDao.setFavorite(file.id, !file.isFavorite)
    }

    suspend fun deleteFile(file: PdfFile) {
        pdfFileDao.delete(file)
    }

    suspend fun deletePdfFile(file: PdfFile) {
        pdfFileDao.delete(file)
    }

    // ========== Bookmarks ==========
    fun getBookmarks(pdfId: Long): Flow<List<Bookmark>> = bookmarkDao.getForPdf(pdfId)
    fun getAllBookmarks(): Flow<List<Bookmark>> = bookmarkDao.getAll()

    suspend fun addBookmark(pdfId: Long, pdfPath: String, page: Int, label: String): Long {
        return bookmarkDao.insert(Bookmark(pdfId = pdfId, pdfPath = pdfPath, page = page, label = label))
    }

    suspend fun addBookmark(bookmark: Bookmark): Long {
        return bookmarkDao.insert(bookmark)
    }

    suspend fun deleteBookmark(bookmark: Bookmark) = bookmarkDao.delete(bookmark)

    // ========== Annotations ==========
    fun getAnnotations(pdfId: Long, page: Int): Flow<List<Annotation>> =
        annotationDao.getForPage(pdfId, page)

    fun getAllAnnotations(pdfId: Long): Flow<List<Annotation>> =
        annotationDao.getForPdf(pdfId)

    suspend fun addAnnotation(annotation: Annotation): Long =
        annotationDao.insert(annotation)

    suspend fun deleteAnnotation(annotation: Annotation) =
        annotationDao.delete(annotation)

    // ========== Vocabulary ==========
    fun getVocabulary(): Flow<List<VocabularyWord>> = vocabularyDao.getAll()
    fun getAllVocabularyWords(): Flow<List<VocabularyWord>> = vocabularyDao.getAll()
    suspend fun getAllVocabularyWordsOnce(): List<VocabularyWord> = vocabularyDao.getAllWordsOnce()
    fun getDueForReview(): Flow<List<VocabularyWord>> = vocabularyDao.getDueForReview()
    fun searchVocabulary(q: String): Flow<List<VocabularyWord>> = vocabularyDao.search(q)

    suspend fun addWord(word: VocabularyWord): Long = vocabularyDao.insert(word)
    suspend fun updateWord(word: VocabularyWord) = vocabularyDao.update(word)
    suspend fun deleteVocabularyWord(word: VocabularyWord) = vocabularyDao.delete(word)

    suspend fun updateWordReviewCount(wordId: Long, correct: Boolean) {
        // Mock lookup first
        val words = vocabularyDao.getAllWordsOnce()
        val word = words.find { it.id == wordId } ?: return
        markWordReviewed(word, correct)
    }

    suspend fun markWordReviewed(word: VocabularyWord, correct: Boolean) {
        val interval = if (correct) {
            when (word.reviewCount) {
                0 -> 1L * 24 * 60 * 60 * 1000   // 1 day
                1 -> 3L * 24 * 60 * 60 * 1000   // 3 days
                2 -> 7L * 24 * 60 * 60 * 1000   // 1 week
                3 -> 14L * 24 * 60 * 60 * 1000  // 2 weeks
                else -> 30L * 24 * 60 * 60 * 1000 // 1 month
            }
        } else {
            3L * 60 * 60 * 1000 // 3 hours if review failed
        }
        vocabularyDao.update(
            word.copy(
                reviewCount = word.reviewCount + 1,
                nextReviewAt = System.currentTimeMillis() + interval,
                isMastered = word.reviewCount >= 5 && correct
            )
        )
    }

    // ========== History ==========
    fun getHistory(): Flow<List<ReadHistory>> = readHistoryDao.getAll()

    suspend fun addHistory(pdfId: Long, pdfName: String, durationMs: Long, pagesRead: Int) {
        readHistoryDao.insert(
            ReadHistory(pdfId = pdfId, pdfName = pdfName, durationMs = durationMs, pagesRead = pagesRead)
        )
    }

    suspend fun getWeeklyReadTimeMs(): Long {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        return readHistoryDao.totalTimeMs(weekAgo) ?: 0L
    }

    suspend fun getWeeklyPages(): Int {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        return readHistoryDao.totalPages(weekAgo) ?: 0
    }

    // ========== PDF Utilities ==========
    private suspend fun copyUriToLocalFile(uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val tmpFile = File(context.cacheDir, "tmp_pdf_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmpFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (tmpFile.exists() && tmpFile.length() > 0) tmpFile else null
        } catch (e: Exception) {
            null
        }
    }

    private fun openPfdForUri(uri: Uri): ParcelFileDescriptor? {
        // 1. Direct content resolver openFileDescriptor attempt
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) return pfd
        } catch (_: Exception) {}

        // 2. Fixed content resolver attempt (dealing with SAF document id colon double-decoding issues)
        var fixedUri: Uri? = null
        try {
            val uriString = uri.toString()
            if (uriString.startsWith("content://")) {
                if (uriString.contains("document/")) {
                    val docPart = uriString.substringAfter("document/")
                    if (docPart.contains(":")) {
                        // Re-encode colons in the document ID portion
                        val fixedDocPart = docPart.replace(":", "%3A")
                        val fixedString = uriString.substringBefore("document/") + "document/" + fixedDocPart
                        val fUri = Uri.parse(fixedString)
                        val pfd = context.contentResolver.openFileDescriptor(fUri, "r")
                        if (pfd != null) {
                            fixedUri = fUri
                            return pfd
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Fallback for direct local file paths (e.g. file:// scheme or direct absolute paths)
        try {
            val path = uri.path
            if (path != null) {
                val file = java.io.File(path)
                if (file.exists() && file.isFile) {
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    if (pfd != null) return pfd
                }
            }
            val rawPath = uri.toString()
            if (rawPath.startsWith("/") || rawPath.startsWith("file://")) {
                val cleanPath = rawPath.replace("file://", "")
                val fileRaw = java.io.File(cleanPath)
                if (fileRaw.exists() && fileRaw.isFile) {
                    val pfd = ParcelFileDescriptor.open(fileRaw, ParcelFileDescriptor.MODE_READ_ONLY)
                    if (pfd != null) return pfd
                }
            }
        } catch (_: Exception) {}

        // 4. Copied content cache-file approach using openInputStream (tries original then fixed URI)
        for (targetUri in listOfNotNull(uri, fixedUri)) {
            try {
                val tmpFile = java.io.File(context.cacheDir, "tmp_pdf_resolver_${System.currentTimeMillis()}.pdf")
                context.contentResolver.openInputStream(targetUri)?.use { input ->
                    tmpFile.outputStream().use { output -> input.copyTo(output) }
                }
                if (tmpFile.exists() && tmpFile.length() > 0) {
                    val pfd = ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    if (pfd != null) return pfd
                }
            } catch (_: Exception) {}
        }

        // 5. Web Preview Fallback inside Web Studio environment
        try {
            val cacheAssetFile = java.io.File(context.cacheDir, "sample_preview.pdf")
            if (!cacheAssetFile.exists() || cacheAssetFile.length() == 0L) {
                context.assets.open("sample_preview.pdf").use { input ->
                    cacheAssetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            if (cacheAssetFile.exists() && cacheAssetFile.length() > 0L) {
                val pfd = ParcelFileDescriptor.open(cacheAssetFile, ParcelFileDescriptor.MODE_READ_ONLY)
                if (pfd != null) return pfd
            }
        } catch (_: Exception) {}

        return null
    }

    suspend fun getPdfPageCount(uri: Uri): Int = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = openPfdForUri(uri)
            if (pfd != null) {
                renderer = PdfRenderer(pfd)
                renderer.pageCount
            } else {
                0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    suspend fun renderPdfPage(uri: Uri, pageIndex: Int, width: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            synchronized(this@PdfRepository) {
                try {
                    val renderer = getCachedRenderer(uri) ?: return@withContext null
                    if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                        return@withContext null
                    }
                    val page = renderer.openPage(pageIndex)
                    val scale = width.toFloat() / page.width.coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmapWidth = width.coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmap
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }


    private suspend fun generateThumbnail(uri: Uri, name: String): String =
        withContext(Dispatchers.IO) {
            try {
                val bitmap = renderPdfPage(uri, 0, 300) ?: return@withContext ""
                val dir = File(context.cacheDir, "thumbnails")
                dir.mkdirs()
                val file = File(dir, "${name.hashCode()}.jpg")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                file.absolutePath
            } catch (e: Exception) {
                ""
            }
        }

    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            }
        } catch (e: Exception) { null }
    }

    private fun getFileSizeFromUri(uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
            } ?: 0L
        } catch (e: Exception) { 0L }
    }

    private fun guessCategory(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("book") || lower.contains("كتاب") -> "books"
            lower.contains("report") || lower.contains("تقرير") -> "reports"
            lower.contains("test") || lower.contains("exam") || lower.contains("اختبار") -> "tests"
            else -> "documents"
        }
    }

    suspend fun getPageLinks(uri: Uri, pageIndex: Int): List<PdfLink> = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        var inputStream: java.io.InputStream? = null
        try {
            try {
                val clazz = Class.forName("com.tom_roush.pdfbox.util.PDFBox")
                clazz.getMethod("init", android.content.Context::class.java).invoke(null, context)
            } catch (e1: Exception) {
                try {
                    val clazz = Class.forName("com.tom_roush.pdfbox.android.PDFBox")
                    clazz.getMethod("init", android.content.Context::class.java).invoke(null, context)
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
            inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext emptyList()
            document = PDDocument.load(inputStream)
            if (pageIndex < 0 || pageIndex >= document.numberOfPages) {
                return@withContext emptyList()
            }
            val page = document.getPage(pageIndex) ?: return@withContext emptyList()
            val mediaBox = page.cropBox ?: page.mediaBox ?: return@withContext emptyList()
            val pdfHeight = mediaBox.height
            val pdfWidth = mediaBox.width

            val links = mutableListOf<PdfLink>()
            val annotationsList = try { page.annotations } catch (e: Exception) { emptyList() }
            for (annotation in annotationsList) {
                if (annotation is PDAnnotationLink) {
                    val action = annotation.action
                    var url = ""
                    if (action is PDActionURI) {
                        url = action.uri ?: ""
                    }
                    if (url.isNotEmpty()) {
                        val rect = annotation.rectangle
                        if (rect != null) {
                            val x = rect.lowerLeftX
                            val y = pdfHeight - rect.upperRightY
                            val w = rect.width
                            val h = rect.height
                            links.add(
                                PdfLink(
                                    url = url,
                                    x = x,
                                    y = y,
                                    width = w,
                                    height = h,
                                    pageWidth = pdfWidth,
                                    pageHeight = pdfHeight
                                )
                            )
                        }
                    }
                }
            }
            links
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        } finally {
            try { document?.close() } catch (_: Exception) {}
            try { inputStream?.close() } catch (_: Exception) {}
        }
    }

    suspend fun extractPageTextWithPdfBox(uri: java.net.URI, pageIndex: Int): String {
        return extractPageTextWithPdfBox(Uri.parse(uri.toString()), pageIndex)
    }

    suspend fun extractPageTextWithPdfBox(uri: Uri, pageIndex: Int): String = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        var inputStream: java.io.InputStream? = null
        try {
            try {
                val clazz = Class.forName("com.tom_roush.pdfbox.util.PDFBox")
                clazz.getMethod("init", android.content.Context::class.java).invoke(null, context)
            } catch (_: Exception) {
                try {
                    val clazz = Class.forName("com.tom_roush.pdfbox.android.PDFBox")
                    clazz.getMethod("init", android.content.Context::class.java).invoke(null, context)
                } catch (_: Exception) {}
            }
            inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext ""
            document = PDDocument.load(inputStream)
            if (pageIndex < 0 || pageIndex >= document.numberOfPages) return@withContext ""
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            stripper.startPage = pageIndex + 1
            stripper.endPage = pageIndex + 1
            stripper.getText(document) ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        } finally {
            try { document?.close() } catch (_: Exception) {}
            try { inputStream?.close() } catch (_: Exception) {}
        }
    }
}
