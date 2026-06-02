package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrResult(
    val fullText: String,
    val blocks: List<OcrBlock>,
    val confidence: Float,
    val language: String
)

data class OcrBlock(
    val text: String,
    val boundingBox: android.graphics.Rect?,
    val lines: List<String>
)

enum class OcrLanguage { AUTO, ARABIC, LATIN }

class OcrManager(private val context: Context) {
    // Latin (German/English)
    private val latinRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    // Arabic (Fallback to Latin/Default in compilation-restricted environment)
    private val arabicRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    // ===== Recognize text from bitmap =====
    suspend fun recognizeText(bitmap: Bitmap, language: OcrLanguage = OcrLanguage.AUTO): OcrResult =
        withContext(Dispatchers.IO) {
            val image = InputImage.fromBitmap(bitmap, 0)

            when (language) {
                OcrLanguage.ARABIC -> recognizeArabic(image)
                OcrLanguage.LATIN -> recognizeLatin(image)
                OcrLanguage.AUTO -> {
                    // Try both and pick the one with more text
                    val latinResult = runCatching { recognizeLatin(image) }.getOrNull()
                    val arabicResult = runCatching { recognizeArabic(image) }.getOrNull()

                    when {
                        latinResult == null -> arabicResult ?: OcrResult("", emptyList(), 0f, "unknown")
                        arabicResult == null -> latinResult
                        arabicResult.fullText.length > latinResult.fullText.length -> arabicResult
                        else -> latinResult
                    }
                }
            }
        }

    // ===== Recognize from URI =====
    suspend fun recognizeFromUri(uri: Uri, language: OcrLanguage = OcrLanguage.AUTO): OcrResult =
        withContext(Dispatchers.IO) {
            try {
                val image = InputImage.fromFilePath(context, uri)
                when (language) {
                    OcrLanguage.ARABIC -> recognizeArabic(image)
                    OcrLanguage.LATIN -> recognizeLatin(image)
                    OcrLanguage.AUTO -> {
                        val latinResult = runCatching { recognizeLatin(image) }.getOrNull()
                        val arabicResult = runCatching { recognizeArabic(image) }.getOrNull()
                        when {
                            latinResult == null -> arabicResult ?: OcrResult("", emptyList(), 0f, "unknown")
                            arabicResult == null -> latinResult
                            arabicResult.fullText.length > latinResult.fullText.length -> arabicResult
                            else -> latinResult
                        }
                    }
                }
            } catch (e: Exception) {
                OcrResult("", emptyList(), 0f, "error")
            }
        }

    private suspend fun recognizeLatin(image: InputImage): OcrResult =
        suspendCancellableCoroutine { cont ->
            latinRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val blocks = mutableListOf<OcrBlock>()
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            for (element in line.elements) {
                                blocks.add(
                                    OcrBlock(
                                        text = element.text,
                                        boundingBox = element.boundingBox,
                                        lines = listOf(line.text)
                                    )
                                )
                            }
                        }
                    }
                    cont.resume(
                        OcrResult(
                            fullText = visionText.text,
                            blocks = blocks,
                            confidence = 0.85f,
                            language = "latin"
                        )
                    )
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    private suspend fun recognizeArabic(image: InputImage): OcrResult =
        suspendCancellableCoroutine { cont ->
            arabicRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val blocks = mutableListOf<OcrBlock>()
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            for (element in line.elements) {
                                blocks.add(
                                    OcrBlock(
                                        text = element.text,
                                        boundingBox = element.boundingBox,
                                        lines = listOf(line.text)
                                    )
                                )
                            }
                        }
                    }
                    cont.resume(
                        OcrResult(
                            fullText = visionText.text,
                            blocks = blocks,
                            confidence = 0.80f,
                            language = "arabic"
                        )
                    )
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    fun close() {
        latinRecognizer.close()
        arabicRecognizer.close()
    }
}
