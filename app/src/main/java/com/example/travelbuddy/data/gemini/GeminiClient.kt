// File: com/example/travelbuddy/data/gemini/GeminiClient.kt
package com.example.travelbuddy.data.gemini

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiClient(
    private val apiKey: String,
    private val model: String = "gemini-2.0-flash"
) {
    private val gson = Gson()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                // BASIC is enough; BODY may leak user text into logs.
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    /**
     * Sends a single-turn request to Gemini generateContent and returns the model text.
     * We instruct the model to return JSON only, and we also set responseMimeType to application/json.
     */
    @Throws(IOException::class)
    fun generateJsonText(
        prompt: String,
        temperature: Double = 0.6,
        maxOutputTokens: Int = 4096
    ): String {
        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val payload = GeminiGenerateContentRequest(
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = prompt))
                )
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = temperature,
                maxOutputTokens = maxOutputTokens,
                responseMimeType = "application/json"
            )
        )

        val jsonBody = gson.toJson(payload)
        val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                throw IOException("Gemini HTTP ${resp.code}: $err")
            }
            val raw = resp.body?.string().orEmpty()
            val parsed = gson.fromJson(raw, GeminiGenerateContentResponse::class.java)

            val text = parsed.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                ?.trim()
                .orEmpty()

            if (text.isBlank()) {
                throw IOException("Gemini response had no text payload.")
            }
            return text
        }
    }
}
