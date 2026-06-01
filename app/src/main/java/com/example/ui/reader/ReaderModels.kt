package com.example.ui.reader

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
