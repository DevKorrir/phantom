package dev.korryr.phantom.ai

import dev.korryr.phantom.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

class GroqRepository @Inject constructor(
    private val client: OkHttpClient
) {
    companion object {
        private const val BASE_URL = "https://api.groq.com/openai/v1/chat/completions"

        // llama-3.3-70b-versatile: smarter model for better answer accuracy
        private const val MODEL = "llama-3.3-70b-versatile"

        // Keep OCR text short so the model processes it faster and with less noise
        private const val MAX_OCR_CHARS = 600
    }

    suspend fun getAnswer(rawOcrText: String): String = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.GROQ_API_KEY
            if (apiKey.isEmpty()) return@withContext "Error: API Key missing"

            // ── Pre-clean OCR text ─────────────────────────────────────────────
            // Remove blank lines, very-short fragments (single chars, lone numbers),
            // and trim to MAX_OCR_CHARS to reduce prompt size and improve accuracy.
            val cleanedText = rawOcrText
                .lines()
                .map { it.trim() }
                .filter { it.length > 2 }           // drop single chars / noise
                .joinToString("\n")
                .take(MAX_OCR_CHARS)

            Timber.d("Sending to Groq (${cleanedText.length} chars): ${cleanedText.take(120)}")

            val requestBody = JSONObject().apply {
                put("model", MODEL)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", """
                            You are answering a Kahoot quiz question.
                            The input is OCR text from a Kahoot game screen.
                            Kahoot layout: question text appears first, then 4 answer options (often preceded by shape names or color labels like Triangle, Diamond, Circle, Square).
                            
                            Rules:
                            - Output ONLY the exact text of the correct answer option.
                            - If 4 options are visible, pick the correct one.
                            - If no options are visible, give a direct factual answer in 4 words max.
                            - No punctuation, no explanation, no emojis, no labels.
                            - Be decisive. Always output something.
                        """.trimIndent())
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", cleanedText)
                    })
                })
                put("temperature", 0.0)    // deterministic — best for factual Q&A
                put("max_tokens", 30)      // answers are short; 30 tokens is plenty
            }

            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext "No response"

            if (!response.isSuccessful) {
                Timber.e("Groq error ${response.code}: $body")
                return@withContext "API error: ${response.code}"
            }

            val answer = JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            Timber.d("Groq answer: $answer")
            answer
        } catch (e: Exception) {
            Timber.e(e, "Groq call failed")
            "Error: ${e.message?.take(30)}"
        }
    }
}
