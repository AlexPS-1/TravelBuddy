// File: com/example/travelbuddy/ai/dto/AiDtos.kt
package com.example.travelbuddy.ai.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ----------------------------
// Shared primitives
// ----------------------------

@Serializable
data class LocationRefDto(
    val displayName: String,
    val city: String,
    val areaHint: String? = null,
    val addressHint: String? = null,
    val googleMapsQuery: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val googlePlaceId: String? = null,
    val confidence: Float? = null
)

@Serializable
data class SocialFlagsDto(
    val instagrammable: Boolean? = null,
    val trending: Boolean? = null,
    val hiddenGem: Boolean? = null
)

@Serializable
data class CandidateDto(
    val candidateId: String,
    val category: CategoryDto,
    val name: String,
    val pitch: String,
    val durationMin: Int,
    val bestTimeOfDay: TimeOfDayDto,
    val costTier: Int? = null,
    val vibeTags: List<String> = emptyList(),
    val social: SocialFlagsDto? = null,
    val location: LocationRefDto
)

@Serializable
data class TravelHintDto(
    val mode: TransportModeDto,
    val durationMinApprox: Int,
    val basis: String? = null
)

@Serializable
data class BlockDraftDto(
    val kind: BlockKindDto,
    val title: String,
    val category: CategoryDto,
    val startTime: String? = null,     // "HH:mm"
    val durationMin: Int,
    val location: LocationRefDto? = null,
    val rationale: String? = null,
    val sourceCandidateId: String? = null,
    val timeOfDayHint: TimeOfDayDto? = null,
    val costTier: Int? = null,
    val vibeTags: List<String> = emptyList(),
    val travelFromPrevHint: TravelHintDto? = null
)

@Serializable
data class NeedsUserInputDto(
    val needsUserInput: Boolean = true,
    val missingFields: List<String> = emptyList(),
    val question: String,
    val suggestedOptions: List<String> = emptyList()
)

// ----------------------------
// Enums
// ----------------------------

@Serializable
enum class BlockKindDto {
    @SerialName("ANCHOR") ANCHOR,
    @SerialName("SUGGESTION") SUGGESTION,
    @SerialName("CUSTOM") CUSTOM,
    @SerialName("TRANSPORT") TRANSPORT
}

@Serializable
enum class CategoryDto {
    @SerialName("FOOD") FOOD,
    @SerialName("DRINKS") DRINKS,
    @SerialName("SIGHTSEEING") SIGHTSEEING,
    @SerialName("SHOPPING") SHOPPING,
    @SerialName("NIGHTLIFE") NIGHTLIFE,
    @SerialName("MUSEUMS") MUSEUMS,
    @SerialName("EXPERIENCE") EXPERIENCE,
    @SerialName("OTHER") OTHER
}

@Serializable
enum class CareLevelDto {
    @SerialName("DONT_CARE") DONT_CARE,
    @SerialName("SOMEWHAT") SOMEWHAT,
    @SerialName("CARE_A_LOT") CARE_A_LOT
}

@Serializable
enum class TimeOfDayDto {
    @SerialName("MORNING") MORNING,
    @SerialName("AFTERNOON") AFTERNOON,
    @SerialName("EVENING") EVENING,
    @SerialName("NIGHT") NIGHT,
    @SerialName("ANY") ANY
}

@Serializable
enum class TransportModeDto {
    @SerialName("WALK") WALK,
    @SerialName("TRANSIT") TRANSIT,
    @SerialName("TAXI") TAXI,
    @SerialName("DRIVE") DRIVE,
    @SerialName("MIXED") MIXED,
    @SerialName("UNKNOWN") UNKNOWN
}

@Serializable
enum class AiActionDto {
    @SerialName("GENERATE_CANDIDATES") GENERATE_CANDIDATES,
    @SerialName("FILL_WINDOW") FILL_WINDOW,
    @SerialName("OPTIMIZE_DAY") OPTIMIZE_DAY,
    @SerialName("REPLACE_BLOCK") REPLACE_BLOCK,
    @SerialName("NEAR_THIS_BLOCK") NEAR_THIS_BLOCK,
    @SerialName("ENRICH_CUSTOM_BLOCK") ENRICH_CUSTOM_BLOCK
}

// ----------------------------
// Preferences DTOs (minimal for contracts)
// ----------------------------

@Serializable
data class SocialPrefsDto(
    val instagrammable: Boolean = false,
    val trending: Boolean = false,
    val hiddenGems: Boolean = false
)

@Serializable
data class PreferenceProfileDto(
    val categoryCare: Map<CategoryDto, CareLevelDto> = emptyMap(),
    val pace: Float? = null,
    val walkingTolerance: Float? = null,
    val comfortVsAdventure: Float? = null,
    val iconicVsLocal: Float? = null,
    val social: SocialPrefsDto = SocialPrefsDto(),
    val travelProfileText: String? = null
)

@Serializable
data class ToneDto(
    val style: String = "FRIENDLY_CONCIERGE",
    val humorLevel: Float = 0.3f
)

// ----------------------------
// Contract 1: Generate Candidates
// ----------------------------

@Serializable
data class GenerateCandidatesRequestDto(
    val action: AiActionDto = AiActionDto.GENERATE_CANDIDATES,
    val trip: TripLiteDto,
    val leg: LegLiteDto,
    val preferences: PreferenceProfileDto,
    val limits: GenerateLimitsDto = GenerateLimitsDto(),
    val tone: ToneDto = ToneDto()
)

@Serializable
data class TripLiteDto(
    val destinationCity: String,
    val days: Int
)

@Serializable
data class LegLiteDto(
    val city: String,
    val startDate: String,
    val endDate: String,
    val homebase: HomebaseLiteDto? = null
)

@Serializable
data class HomebaseLiteDto(
    val name: String,
    val areaHint: String? = null
)

@Serializable
data class GenerateLimitsDto(
    val perCategory: Int = 10,
    val totalMax: Int = 60,
    val priceTiersAllowed: List<Int> = listOf(1, 2, 3)
)

@Serializable
data class GenerateCandidatesResponseDto(
    val version: String = "1.0",
    val city: String,
    val generatedAtUtc: Long,
    val candidatesByCategory: Map<CategoryDto, List<CandidateDto>> = emptyMap(),
    val globalTips: List<String> = emptyList()
)

// ----------------------------
// (Other contracts omitted here — keep your existing ones)
// ----------------------------

object AiValidation {
    fun isValidDuration(durationMin: Int): Boolean = durationMin in 10..360

    fun isTimeStringHHmm(value: String?): Boolean {
        if (value == null) return false
        return Regex("""^\d{2}:\d{2}$""").matches(value)
    }

    fun clampHumorLevel(x: Float?): Float? = x?.coerceIn(0f, 1f)
}

// ----------------------------
// LITE FALLBACK CONTRACT (for robustness)
// ----------------------------

@Serializable
data class GenerateCandidatesLiteResponseDto(
    val candidates: List<LiteCandidateDto> = emptyList()
)

@Serializable
data class LiteCandidateDto(
    val category: CategoryDto? = null,
    val name: String? = null,
    val location: LiteLocationDto? = null,
    val priceTier: Int? = null,
    val rating: Double? = null,
    val vibeTags: List<String> = emptyList(),
    val pitch: String? = null,
    val description: String? = null,
    val priority: Double? = null
)

@Serializable
data class LiteLocationDto(
    val googleMapsQuery: String? = null,
    val areaHint: String? = null,
    val address: String? = null
)