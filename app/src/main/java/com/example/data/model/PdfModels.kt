package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "pdf_files")
data class PdfFile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val path: String,
    val uri: String,
    val size: Long = 0L,
    val pageCount: Int = 0,
    val lastOpened: Long = System.currentTimeMillis(),
    val lastPage: Int = 0,
    val readProgress: Float = 0f,  // 0.0 to 1.0
    val isFavorite: Boolean = false,
    val category: String = "documents", // "documents", "books", "reports", "tests"
    val tags: String = "",              // comma-separated tags
    val annotationCount: Int = 0,
    val bookmarkCount: Int = 0,
    val totalReadTimeMs: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis(),
    val thumbnailPath: String = "",
    val folder: String? = null
) : Serializable

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pdfId: Long,
    val pdfPath: String,
    val page: Int,
    val label: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val colorHex: String = "#3b82f6"
)

@Entity(tableName = "annotations")
data class Annotation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pdfId: Long,
    val page: Int,
    val type: AnnotationType,
    val content: String = "",
    val colorHex: String = "#fbbf24",
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    val createdAt: Long = System.currentTimeMillis()
)

enum class AnnotationType {
    HIGHLIGHT, UNDERLINE, STRIKETHROUGH, TEXT, INK, STICKY_NOTE, STAMP, SHAPE
}

@Entity(tableName = "vocabulary")
data class VocabularyWord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalWord: String,
    val translatedWord: String,
    val sourceLang: String = "de",
    val targetLang: String = "ar",
    val partOfSpeech: String = "",
    val example: String = "",
    val difficultyLevel: String = "A2", // "A1", "A2", "B1", "B2", "C1", "C2"
    val pdfSource: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val reviewCount: Int = 0,
    val nextReviewAt: Long = System.currentTimeMillis(),
    val isMastered: Boolean = false
)

@Entity(tableName = "read_history")
data class ReadHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pdfId: Long,
    val pdfName: String,
    val openedAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val pagesRead: Int = 0
)

@Entity(tableName = "ocr_page_texts")
data class OcrPageText(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pdfId: Long,
    val page: Int,
    val text: String,
    val wordCoordinatesJson: String = "" // serialised list of words with coordinates
)

data class PdfFolder(
    val name: String,
    val files: List<PdfFile>,
    val colorHex: String = "#3b82f6"
)
