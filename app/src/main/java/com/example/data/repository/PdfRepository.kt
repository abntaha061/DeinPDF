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

class PdfRepository(
    private val context: Context,
    private val db: AppDatabase
) {
    private val pdfFileDao = db.pdfFileDao()
    private val bookmarkDao = db.bookmarkDao()
    private val annotationDao = db.annotationDao()
    private val vocabularyDao = db.vocabularyDao()
    private val readHistoryDao = db.readHistoryDao()

    // ========== Files ==========
    fun getAllFiles(): Flow<List<PdfFile>> = pdfFileDao.getAllFiles()
    fun getRecentFiles(): Flow<List<PdfFile>> = pdfFileDao.getRecent()
    fun getFavorites(): Flow<List<PdfFile>> = pdfFileDao.getFavorites()
    fun searchFiles(query: String): Flow<List<PdfFile>> = pdfFileDao.search(query)
    fun getByCategory(cat: String): Flow<List<PdfFile>> = pdfFileDao.getByCategory(cat)

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
    suspend fun getPdfPageCount(uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext 0
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            renderer.close()
            pfd.close()
            count
        } catch (e: Exception) {
            0
        }
    }

    suspend fun renderPdfPage(uri: Uri, pageIndex: Int, width: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
                val renderer = PdfRenderer(pfd)
                if (pageIndex >= renderer.pageCount) {
                    renderer.close()
                    pfd.close()
                    return@withContext null
                }
                val page = renderer.openPage(pageIndex)
                val ratio = width.toFloat() / page.width
                val height = (page.height * ratio).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                pfd.close()
                bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
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
}
