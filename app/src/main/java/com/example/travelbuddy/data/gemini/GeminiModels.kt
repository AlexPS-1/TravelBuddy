package com.example.travelbuddy.data.gemini

import com.google.gson.annotations.SerializedName

data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>,
    @SerializedName("generationConfig")
    val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiContent(
    val role: String,
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiGenerationConfig(
    val temperature: Double? = null,
    @SerializedName("maxOutputTokens")
    val maxOutputTokens: Int? = null,
    @SerializedName("responseMimeType")
    val responseMimeType: String? = null
)

data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate>? = null
)

data class GeminiCandidate(
    val content: GeminiContentOut? = null
)

data class GeminiContentOut(
    val parts: List<GeminiPartOut>? = null
)

data class GeminiPartOut(
    val text: String? = null
)
