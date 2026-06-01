package com.example.utils

import android.content.Context
import com.example.BuildConfig
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class TranslationPair(
    val originalText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val partOfSpeech: String = "",
    val phonetics: String = "",
    val example: String = ""
)

class TranslationManager(private val context: Context) {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private val apiKey: String
        get() = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

    private suspend fun translateWithGemini(
        text: String,
        source: String,
        target: String
    ): TranslationPair = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw Exception("Gemini API key is not configured")
        }

        val prompt = if (source == "de" && target == "ar") {
            """
            Translate the German word or sentence: "$text" to Arabic.
            Respond strictly with a JSON object in this format (no markdown blocks, no formatting outside the raw JSON object):
            {
              "translatedText": "Translation in Arabic",
              "partOfSpeech": "Heuristics and Part of speech in Arabic",
              "phonetics": "IPA or simple pronunciation guide in brackets like [lɛʁnən]",
              "example": "Useful German example sentence containing the word"
            }
            """.trimIndent()
        } else {
            """
            Translate the Arabic word or sentence: "$text" to German.
            Respond strictly with a JSON object in this format (no markdown blocks, no formatting outside the raw JSON object):
            {
              "translatedText": "Translation in German",
              "partOfSpeech": "Part of speech like Noun, Verb, Pronoun, Adjective",
              "phonetics": "",
              "example": "Useful German example sentence containing the translated word"
            }
            """.trimIndent()
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        
        val jsonPayload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonPayload.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Response failed: ${response.code}")
            val responseString = response.body?.string() ?: throw Exception("Empty response body")
            
            val jsonResponse = JSONObject(responseString)
            val candidates = jsonResponse.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val rawText = parts.getJSONObject(0).getString("text").trim()
            
            val parsedResult = JSONObject(rawText)
            TranslationPair(
                originalText = text,
                translatedText = parsedResult.optString("translatedText", ""),
                sourceLang = source,
                targetLang = target,
                partOfSpeech = parsedResult.optString("partOfSpeech", ""),
                phonetics = parsedResult.optString("phonetics", ""),
                example = parsedResult.optString("example", "")
            )
        }
    }

    private fun translateWholePageWithGemini(prompt: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        
        val jsonPayload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonPayload.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Response failed: ${response.code}")
            val responseString = response.body?.string() ?: throw Exception("Empty response body")
            
            val jsonResponse = JSONObject(responseString)
            val candidates = jsonResponse.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            return parts.getJSONObject(0).getString("text").trim()
        }
    }

    // German -> Arabic
    private val deToAr by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.GERMAN)
                .setTargetLanguage(TranslateLanguage.ARABIC)
                .build()
        )
    }

    // Arabic -> German
    private val arToDe by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ARABIC)
                .setTargetLanguage(TranslateLanguage.GERMAN)
                .build()
        )
    }

    // German -> English (fallback)
    private val deToEn by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.GERMAN)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
        )
    }

    // ===== Download models =====
    fun downloadModels(onSuccess: () -> Unit = {}, onFailure: (Exception) -> Unit = {}) {
        deToAr.downloadModelIfNeeded()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
        arToDe.downloadModelIfNeeded()
    }

    // ===== Translate German -> Arabic =====
    suspend fun translateDeToAr(text: String): TranslationPair = withContext(Dispatchers.IO) {
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                return@withContext translateWithGemini(text, "de", "ar")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val translated = translateWith(deToAr, text)
        TranslationPair(
            originalText = text,
            translatedText = translated,
            sourceLang = "de",
            targetLang = "ar",
            partOfSpeech = guessPartOfSpeech(text),
            phonetics = getGermanPhonetics(text)
        )
    }

    // ===== Translate Arabic -> German =====
    suspend fun translateArToDe(text: String): TranslationPair = withContext(Dispatchers.IO) {
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                return@withContext translateWithGemini(text, "ar", "de")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val translated = translateWith(arToDe, text)
        TranslationPair(
            originalText = text,
            translatedText = translated,
            sourceLang = "ar",
            targetLang = "de"
        )
    }

    // ===== Auto detect and translate =====
    suspend fun translateAuto(text: String, targetLang: String = "ar"): TranslationPair =
        withContext(Dispatchers.IO) {
            val isArabic = text.any { it in '\u0600'..'\u06FF' }
            if (isArabic) {
                translateArToDe(text)
            } else {
                translateDeToAr(text)
            }
        }

    // ===== Translate full page text =====
    suspend fun translatePage(pageText: String, targetLang: String = "ar"): String =
        withContext(Dispatchers.IO) {
            if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
                try {
                    val prompt = """
                        You are a professional book translator. Translate the following book page content from German into fluent, modern Arabic. Keep formatting as clean and simple as possible.
                        
                        Content:
                        ${pageText}
                    """.trimIndent()
                    return@withContext translateWholePageWithGemini(prompt)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Split into chunks to avoid ML Kit limits
            val chunks = pageText.chunked(500)
            val translated = chunks.map { chunk ->
                runCatching { translateWith(deToAr, chunk) }.getOrDefault(chunk)
            }
            translated.joinToString(" ")
        }

    private suspend fun translateWith(
        translator: com.google.mlkit.nl.translate.Translator,
        text: String
    ): String = suspendCancellableCoroutine { cont ->
        translator.translate(text)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    // Basic part-of-speech heuristics for German
    private fun guessPartOfSpeech(word: String): String {
        val lower = word.lowercase()
        return when {
            word.firstOrNull()?.isUpperCase() == true && word.length > 1 -> "اسم (Noun)"
            lower.endsWith("en") || lower.endsWith("ern") || lower.endsWith("eln") -> "فعل (Verb)"
            lower.endsWith("lich") || lower.endsWith("ig") || lower.endsWith("isch") -> "صفة (Adj)"
            lower.endsWith("ung") || lower.endsWith("heit") || lower.endsWith("keit") -> "اسم (Noun)"
            lower.endsWith("er") || lower.endsWith("ste") -> "صفة (Adj)"
            else -> "كلمة"
        }
    }

    // Basic IPA hints for common German patterns
    private fun getGermanPhonetics(word: String): String {
        // Very simplified phonetic transcription
        return word
            .replace("sch", "ʃ")
            .replace("ch", "x")
            .replace("ei", "aɪ")
            .replace("ie", "iː")
            .replace("ö", "øː")
            .replace("ü", "yː")
            .replace("ä", "ɛː")
            .replace("ss", "s")
            .replace("ß", "s")
            .let { if (it != word) "[$it]" else "" }
    }

    fun close() {
        deToAr.close()
        arToDe.close()
        deToEn.close()
    }
}
