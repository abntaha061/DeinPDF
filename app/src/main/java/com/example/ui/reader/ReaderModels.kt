package com.example.ui.reader

import android.graphics.RectF
import androidx.compose.ui.geometry.Offset

enum class ViewMode {
    CONTINUOUS,
    SINGLE,
    HORIZONTAL
}

enum class ReaderTool {
    NONE,
    TRANSLATE,
    SPEECH,
    NOTE,
    DRAW,
    HIGHLIGHT
}

data class TranslationResult(
    val originalWord: String,
    val translatedText: String,
    val partOfSpeech: String? = null,
    val phonetics: String? = null,
    val example: String? = null,
    val sourceLang: String = "de",
    val targetLang: String = "ar"
)

data class WordPosition(
    val word: String,
    val rect: RectF,
    val pageIndex: Int
)

data class TextSelection(
    val startWordIndex: Int,
    val endWordIndex: Int,
    val words: List<WordPosition>
) {
    val selectedText: String
        get() {
            val start = minOf(startWordIndex, endWordIndex)
            val end = maxOf(startWordIndex, endWordIndex)
            return words.subList(start, end + 1).joinToString(" ") { it.word }
        }
    
    val startHandle: Offset
        get() = words.getOrNull(startWordIndex)?.let { word ->
            Offset(word.rect.left, word.rect.bottom)
        } ?: Offset.Zero
    
    val endHandle: Offset
        get() = words.getOrNull(endWordIndex)?.let { word ->
            Offset(word.rect.right, word.rect.bottom)
        } ?: Offset.Zero
}

