package com.example.utils

import android.content.Context
import com.example.BuildConfig
import com.example.data.model.VocabularyWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class QAResult(
    val answer: String,
    val pageNumber: Int,
    val confidence: Float = 1.0f
)

enum class SummaryStyle { SHORT, MEDIUM, DETAILED, BULLETS }

class AIManager(private val context: Context) {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val apiKey: String
        get() = try {
            // Securely fetch Gemini API key injected from AI Studio Secrets panel
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

    // ===== Chapter / Page Summarization =====
    suspend fun summarizePage(text: String, lang: String = "ar", style: SummaryStyle = SummaryStyle.BULLETS): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext "لا يوجد نص للتلخيص"

        val prompt = when(style) {
            SummaryStyle.SHORT -> "لخص النص التالي باختصار شديد في سطرين مركبين باللغة العربية (أو لغة الوجهة: $lang):"
            SummaryStyle.MEDIUM -> "لخص النص التالي في فقرة غنية ومكتملة باللغة العربية (أو لغة الوجهة: $lang):"
            SummaryStyle.DETAILED -> "لخص أهم أفكار هذا النص بالتفصيل في فقرات منسقة ومترابطة باللغة العربية (أو لغة الوجهة: $lang):"
            SummaryStyle.BULLETS -> "لخص النص التالي في شكل نقاط (Bullets) رئيسية واضحة باللغة العربية (أو لغة الوجهة: $lang) تتضمن التفاصيل الهامة:"
        }

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                return@withContext callGemini(prompt = "$prompt\n\n$text")
            } catch (e: Exception) {
                // Return fallback on failure
                e.printStackTrace()
            }
        }

        // Extractive NLP Fallback (Local Algorithm)
        return@withContext localNlpSummary(text, style)
    }

    suspend fun summarizeDocument(pages: List<String>, lang: String = "ar"): String {
        val combinedText = pages.take(5).joinToString("\n\n") // summarize first 5 pages local/server limit
        return summarizePage(combinedText, lang, SummaryStyle.DETAILED)
    }

    // ===== Interactive Q&A chat on document content =====
    suspend fun answerQuestion(question: String, documentText: String, lang: String = "ar"): QAResult = withContext(Dispatchers.IO) {
        if (documentText.isBlank()) {
            return@withContext QAResult("لا يوجد محتوى في المستند للإجابة عليه", -1)
        }

        val prompt = """
            أنت مساعد قراءة ذكي وخبير في التعلم. الإجابة يجب أن تبنى تماما وبكل أمانة على محتوى المستند المرفق أدناه.
            إذا لم تكن الإجابة موجودة بالمستند فاذكر ذلك بوضوح.
            مستعيناً بالوثيقة أجب على السؤال التالي باللغة العربية: "$question"
            
            الوثيقة المرفقة:
            $documentText
        """.trimIndent()

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val apiResponse = callGemini(prompt)
                // Estimate source page (rough approximation based on keyword presence)
                val estimatedPage = findApproximatePage(question, documentText)
                return@withContext QAResult(apiResponse, estimatedPage)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Local keyword search fallback
        return@withContext localNlpQandA(question, documentText)
    }

    // ===== Extract Vocabulary Words (German Rules) =====
    suspend fun extractGermanVocabulary(text: String): List<VocabularyWord> = withContext(Dispatchers.Default) {
        val list = mutableListOf<VocabularyWord>()
        val words = text.split(Regex("[\\s،,;:.!?؟()\\[\\]\"']"))
            .map { it.trim() }
            .filter { it.length > 2 }
            .distinct()

        // Filter German nouns, verbs with common suffixes
        val germanLooking = words.filter { word ->
            word.any { it in "äöüÄÖÜß" } ||
            (word.firstOrNull()?.isUpperCase() == true && word.length > 3) ||
            word.endsWith("ung") || word.endsWith("keit") || word.endsWith("heit") ||
            word.endsWith("en") || word.endsWith("ieren")
        }.take(15)

        germanLooking.forEach { word ->
            val type = when {
                word.firstOrNull()?.isUpperCase() == true -> "Nomen (اسم)"
                word.endsWith("en") || word.endsWith("ieren") -> "Verb (فعل)"
                word.endsWith("lich") || word.endsWith("isch") || word.endsWith("ig") -> "Adjektiv (صفة)"
                else -> "Wort (كلمة)"
            }
            val diff = when {
                word.length <= 4 -> "A1"
                word.length <= 6 -> "A2"
                word.length <= 9 -> "B1"
                word.contains("schaft") || word.contains("keit") -> "B2"
                else -> "C1"
            }
            list.add(
                VocabularyWord(
                    originalWord = word,
                    translatedWord = "", // will be translated by Translator
                    partOfSpeech = type,
                    difficultyLevel = diff,
                    example = "Das ist ein Beispiel für $word",
                    pdfSource = "مستخرج تلقائياً"
                )
            )
        }
        list
    }

    // ===== Language Detection =====
    fun detectLanguage(text: String): String {
        val arabicCount = text.count { it in '\u0600'..'\u06FF' }
        val latinCount = text.count { it.isLetter() && it !in '\u0600'..'\u06FF' }
        return when {
            arabicCount > latinCount * 1.5 -> "ar"
            text.any { it in "äöüÄÖÜß" } -> "de"
            latinCount > arabicCount -> "de" // de default
            else -> "auto"
        }
    }

    // ===== Private OkHttp Gemini REST Caller =====
    private fun callGemini(prompt: String): String {
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
            val responseString = response.body?.string() ?: throw Exception("Empty body response")
            
            val jsonResponse = JSONObject(responseString)
            val candidates = jsonResponse.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val firstPart = parts.getJSONObject(0)
            return firstPart.getString("text")
        }
    }

    // ===== Helper local extraction mechanisms =====
    private fun localNlpSummary(text: String, style: SummaryStyle): String {
        val sentences = text.split(Regex("[.!?؟]\n?"))
            .map { it.trim() }
            .filter { it.length > 15 }

        if (sentences.isEmpty()) return "النص قصير جداً للتلخيص."

        // Pick distinct key sentences
        val summarySentences = sentences.take(if (style == SummaryStyle.SHORT) 2 else if (style == SummaryStyle.MEDIUM) 4 else 6)
        
        return when(style) {
            SummaryStyle.BULLETS -> summarySentences.joinToString("\n") { "• $it" }
            else -> summarySentences.joinToString(". ") + "."
        }
    }

    private fun localNlpQandA(question: String, text: String): QAResult {
        val keywords = question.split(" ", "؟", "؟", "?")
            .map { it.trim().lowercase() }
            .filter { it.length > 3 && !it.contains(Regex("(ما|هو|هي|في|من|على|هل|كيف|أين)")) }

        val sentences = text.split(Regex("[.!?؟}\n]")).map { it.trim() }.filter { it.isNotBlank() }
        var bestSentence = ""
        var maxMatches = 0

        for (s in sentences) {
            val lowercaseS = s.lowercase()
            val matches = keywords.count { lowercaseS.contains(it) }
            if (matches > maxMatches) {
                maxMatches = matches
                bestSentence = s
            }
        }

        val estimatedPage = findApproximatePage(question, text)
        return if (bestSentence.isNotBlank()) {
            QAResult("بناءً على المستند المرفق:\n\n$bestSentence.", estimatedPage)
        } else {
            QAResult("عذراً، لم يذكر المستند المرفق إجابة واضحة عن سؤالك.", estimatedPage)
        }
    }

    private fun findApproximatePage(question: String, fullText: String): Int {
        val paragraphs = fullText.split("\n\n")
        val index = paragraphs.indexOfFirst { p ->
            question.split(" ").filter { it.length > 4 }.any { word -> p.contains(word, ignoreCase = true) }
        }
        return if (index != -1) (index / 3) + 1 else 1
    }
}
