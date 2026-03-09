package com.example.travelbuddy.ai.dto

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object AiJson {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    inline fun <reified T> encode(value: T): String = json.encodeToString(value)

    inline fun <reified T> decode(text: String): T = json.decodeFromString(text)

    // Convenience helpers
    fun decodeGenerateCandidates(text: String): GenerateCandidatesResponseDto =
        json.decodeFromString<GenerateCandidatesResponseDto>(text)

    fun decodeGenerateCandidatesLite(text: String): GenerateCandidatesLiteResponseDto =
        json.decodeFromString<GenerateCandidatesLiteResponseDto>(text)

    /**
     * NOTE:
     * If/when you re-add the Enrich Custom Block contract DTOs,
     * you can add this back safely:
     *
     * fun decodeEnrichCustomBlockSuccess(text: String): EnrichCustomBlockSuccessDto =
     *     json.decodeFromString<EnrichCustomBlockSuccessDto>(text)
     */
}