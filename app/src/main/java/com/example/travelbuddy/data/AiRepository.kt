package com.example.travelbuddy.data

import com.example.travelbuddy.BuildConfig
import com.example.travelbuddy.ai.dto.*
import com.example.travelbuddy.data.gemini.GeminiClient
import com.example.travelbuddy.data.gemini.GeminiJsonRepair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.UUID

class AiRepository {

    private val apiKey: String = BuildConfig.GEMINI_API_KEY.orEmpty()

    private val gemini: GeminiClient? =
        apiKey.takeIf { it.isNotBlank() }?.let { GeminiClient(apiKey = it, model = "gemini-2.0-flash") }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Volatile private var lastRawFirst: String? = null
    @Volatile private var lastRawRepaired: String? = null

    fun isAiEnabled(): Boolean = gemini != null

    fun getLastRawFirstClipped(maxChars: Int = 9000): String? = lastRawFirst?.take(maxChars)
    fun getLastRawRepairedClipped(maxChars: Int = 9000): String? = lastRawRepaired?.take(maxChars)

    suspend fun generateCandidates(request: GenerateCandidatesRequestDto): GenerateCandidatesResponseDto {
        val client = gemini ?: throw IllegalStateException(
            "Gemini API key missing. Add GEMINI_API_KEY to local.properties and sync."
        )

        val prompt = PromptBuilder.buildGenerateCandidatesPrompt(request)

        val firstText = withContext(Dispatchers.IO) { client.generateJsonText(prompt) }
        lastRawFirst = firstText
        lastRawRepaired = null

        val parsed1 = tryParseAnyShape(firstText, request)
        if (parsed1 != null) return postProcess(request, parsed1)

        val repairPrompt = PromptBuilder.buildRepairJsonPrompt(firstText)
        val repairedText = withContext(Dispatchers.IO) { client.generateJsonText(repairPrompt) }
        lastRawRepaired = repairedText

        val parsed2 = tryParseAnyShape(repairedText, request)
        if (parsed2 != null) return postProcess(request, parsed2)

        throw IllegalStateException(
            "TravelBuddy couldn’t read the AI response (invalid JSON). Please try again."
        )
    }

    /**
     * Tries:
     * 1) Full contract
     * 2) Extract JSON object then full contract
     * 3) Lite contract -> convert
     * 4) Extract JSON object then lite contract -> convert
     */
    private fun tryParseAnyShape(rawText: String, request: GenerateCandidatesRequestDto): GenerateCandidatesResponseDto? {
        val normalized = GeminiJsonRepair.normalize(rawText)

        // 1) full direct
        runCatching { json.decodeFromString<GenerateCandidatesResponseDto>(normalized) }
            .getOrNull()
            ?.let { if (looksValid(it)) return it }

        // 2) full extracted
        GeminiJsonRepair.extractFirstJsonObject(normalized)?.let { extracted ->
            runCatching { json.decodeFromString<GenerateCandidatesResponseDto>(extracted) }
                .getOrNull()
                ?.let { if (looksValid(it)) return it }
        }

        // 3) lite direct -> convert
        runCatching { json.decodeFromString<GenerateCandidatesLiteResponseDto>(normalized) }
            .getOrNull()
            ?.let { lite ->
                val converted = convertLiteToFull(lite, request)
                if (looksValid(converted)) return converted
            }

        // 4) lite extracted -> convert
        GeminiJsonRepair.extractFirstJsonObject(normalized)?.let { extracted ->
            runCatching { json.decodeFromString<GenerateCandidatesLiteResponseDto>(extracted) }
                .getOrNull()
                ?.let { lite ->
                    val converted = convertLiteToFull(lite, request)
                    if (looksValid(converted)) return converted
                }
        }

        return null
    }

    private fun looksValid(resp: GenerateCandidatesResponseDto): Boolean {
        if (resp.city.isBlank()) return false
        if (resp.generatedAtUtc <= 0L) return false

        val anyBad = resp.candidatesByCategory.values.flatten().any { c ->
            c.candidateId.isBlank() ||
                    c.name.isBlank() ||
                    c.pitch.isBlank() ||
                    c.durationMin <= 0 ||
                    c.location.displayName.isBlank() ||
                    c.location.city.isBlank()
        }
        return !anyBad
    }

    private fun convertLiteToFull(
        lite: GenerateCandidatesLiteResponseDto,
        request: GenerateCandidatesRequestDto
    ): GenerateCandidatesResponseDto {
        val city = request.leg.city.ifBlank { request.trip.destinationCity }.ifBlank { "Unknown" }
        val now = System.currentTimeMillis() / 1000L

        val candidates = lite.candidates.mapNotNull { lc ->
            val name = lc.name?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null

            val category = lc.category ?: CategoryDto.OTHER

            val pitch = (lc.pitch ?: lc.description ?: "")
                .trim()
                .ifBlank { "A solid pick in $city." }
                .take(220)

            val vibeTags = lc.vibeTags
                .mapNotNull { it.trim().takeIf { s -> s.isNotBlank() } }
                .take(12)

            val loc = lc.location
            val area = loc?.areaHint?.trim()?.takeIf { it.isNotBlank() }

            val query = loc?.googleMapsQuery
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "$name, $city"

            val addressHint = loc?.address?.trim()?.takeIf { it.isNotBlank() }

            CandidateDto(
                candidateId = "lite_" + UUID.randomUUID().toString().take(12),
                category = category,
                name = name,
                pitch = pitch,
                durationMin = 90,
                bestTimeOfDay = TimeOfDayDto.ANY,
                costTier = (lc.priceTier ?: 2).coerceIn(1, 3),
                vibeTags = vibeTags,
                social = null,
                location = LocationRefDto(
                    displayName = name,
                    city = city,
                    areaHint = area,
                    addressHint = addressHint,
                    googleMapsQuery = query,
                    lat = null,
                    lng = null,
                    googlePlaceId = null,
                    confidence = null
                )
            )
        }

        val byCategory = candidates.groupBy { it.category }

        return GenerateCandidatesResponseDto(
            version = "1.0",
            city = city,
            generatedAtUtc = now,
            candidatesByCategory = byCategory,
            globalTips = emptyList()
        )
    }

    private fun postProcess(
        request: GenerateCandidatesRequestDto,
        resp: GenerateCandidatesResponseDto
    ): GenerateCandidatesResponseDto {
        val neighborhood = neighborhoodFromTravelText(request.preferences.travelProfileText.orEmpty())
            ?: return resp

        val updated = resp.candidatesByCategory.mapValues { (_, list) ->
            enforceNeighborhood(list, neighborhood)
        }
        return resp.copy(candidatesByCategory = updated)
    }

    private fun neighborhoodFromTravelText(text: String): String? {
        val line = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("NEIGHBORHOOD:", ignoreCase = true) }
            ?: return null

        val token = line.substringAfter(":").trim()
        return token.takeIf { it.isNotBlank() }?.uppercase()
    }

    private fun enforceNeighborhood(list: List<CandidateDto>, neighborhoodUpper: String): List<CandidateDto> {
        if (list.isEmpty()) return list
        val matches = list.filter { matchesNeighborhood(it, neighborhoodUpper) }
        if (matches.size >= 5) return matches.take(10)
        val fallback = list.filterNot { matchesNeighborhood(it, neighborhoodUpper) }.take(2)
        return (matches + fallback).take(10)
    }

    private fun matchesNeighborhood(c: CandidateDto, neighborhoodUpper: String): Boolean {
        val n = neighborhoodUpper
        val areaHint = c.location.areaHint?.uppercase().orEmpty()
        val addrHint = c.location.addressHint?.uppercase().orEmpty()
        val mapsQuery = c.location.googleMapsQuery?.uppercase().orEmpty()
        val display = c.location.displayName.uppercase()
        val name = c.name.uppercase()
        val pitch = c.pitch.uppercase()
        val vibe = c.vibeTags.joinToString(" ").uppercase()

        val alt = "NEIGHBORHOOD_${n.replace(" ", "_")}"

        return areaHint.contains(n) ||
                addrHint.contains(n) ||
                mapsQuery.contains(n) ||
                display.contains(n) ||
                name.contains(n) ||
                pitch.contains(n) ||
                vibe.contains(n) ||
                vibe.contains(alt) ||
                pitch.contains(alt)
    }
}