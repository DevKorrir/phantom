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
        private const val MODEL = "llama-3.1-8b-instant" // llama-3.3-70b-versatile
    }

    suspend fun getAnswer(question: String): String = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.GROQ_API_KEY
            if (apiKey.isEmpty()) {
                Timber.e("GROQ_API_KEY is empty")
                return@withContext "Error: API Key missing"
            }

            Timber.d("Sending question to Groq (${question.length} chars): ${question.take(80)}...")

            val requestBody = JSONObject().apply {
                put("model", MODEL)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", """
                            You are a Kahoot Assistant. The user will provide text extracted via OCR from a screen.
                            1. Identify the question.
                            2. If multiple-choice options (usually color-coded or listed) are present, select the correct one.
                            3. provide ONLY the text of the correct answer. 
                            4. If no options are found, provide a factual answer in max 5 words.
                            5. Be extremely concise.
                            6. DO NOT use any emojis in your response.
                        """.trimIndent())
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "OCR Text: $question")
                    })
                })
                put("temperature", 0.0)
                put("max_tokens", 50)
            }

            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(
                    requestBody.toString()
                        .toRequestBody("application/json".toMediaType())
                )
                .build()

            Timber.d("Making Groq API request...")
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext "No response"

            Timber.d("Groq API response code: ${response.code}")

            if (!response.isSuccessful) {
                Timber.e("Groq API error ${response.code}: $body")
                return@withContext "API error: ${response.code}"
            }

            val json = JSONObject(body)
            val answer = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            Timber.d("Got Groq answer: $answer")
            answer
        } catch (e: Exception) {
            Timber.e(e, "Groq API call failed")
            "Error: ${e.message?.take(30)}"
        }
    }
}
