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

class GeminiRepository @Inject constructor(
    private val client: OkHttpClient
) {
    companion object {
        private const val BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
    }

    suspend fun getAnswer(question: String): String = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL?key=${BuildConfig.GEMINI_API_KEY}"
            Timber.d("Sending question to Gemini (${question.length} chars): ${question.take(80)}...")

            val requestBody = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(
                        JSONObject().put("text",
                            "Answer this question in max 5 words: $question"
                        )
                    ))
                ))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0)
                    put("maxOutputTokens", 20)
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(
                    requestBody.toString()
                        .toRequestBody("application/json".toMediaType())
                )
                .build()

            Timber.d("Making API request...")
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext "No response"

            Timber.d("API response code: ${response.code}")

            if (!response.isSuccessful) {
                Timber.e("API error ${response.code}: $body")
                return@withContext "API error: ${response.code}"
            }

            val json = JSONObject(body)
            val answer = json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

            Timber.d("Got answer: $answer")
            answer
        } catch (e: Exception) {
            Timber.e(e, "Gemini API call failed")
            "Error: ${e.message?.take(30)}"
        }
    }
}
