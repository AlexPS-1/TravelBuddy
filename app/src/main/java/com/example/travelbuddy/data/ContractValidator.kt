// File: com/example/travelbuddy/data/ContractValidator.kt
package com.example.travelbuddy.data

import com.example.travelbuddy.ai.dto.CandidateDto
import com.example.travelbuddy.ai.dto.CategoryDto
import com.example.travelbuddy.ai.dto.GenerateCandidatesRequestDto
import com.example.travelbuddy.ai.dto.GenerateCandidatesResponseDto
import com.example.travelbuddy.ai.dto.TimeOfDayDto

object ContractValidator {

    data class FoodPolicy(
        val presetId: String? = null,
        val targetCostTier: Int? = null,
        val mealMoments: Set<String> = emptySet(),
        val cuisines: Set<String> = emptySet()
    )

    data class FoodEnforceResult(
        val filteredResponse: GenerateCandidatesResponseDto,
        val keptFood: List<CandidateDto>,
        val droppedFood: List<CandidateDto>,
        val missingCountToReach: Int,
        val hardConstraintsBlock: String,
        val avoidGoogleQueries: List<String>
    )

    fun enforceFood(request: GenerateCandidatesRequestDto, response: GenerateCandidatesResponseDto): FoodEnforceResult {
        val policy = parseFoodPolicy(request.preferences.travelProfileText.orEmpty())
        val originalFood = response.candidatesByCategory[CategoryDto.FOOD].orEmpty()

        val (kept, dropped) = originalFood.partition { matchesFoodPolicySoft(it, policy) }

        // ✅ Fail-open: never show an empty list because enforcement got too picky.
        // If we dropped almost everything, keep the original set.
        val finalKept: List<CandidateDto>
        val finalDropped: List<CandidateDto>
        if (originalFood.isNotEmpty() && kept.size < 2) {
            finalKept = originalFood
            finalDropped = emptyList()
        } else {
            finalKept = kept
            finalDropped = dropped
        }

        val minNeeded = 6
        val missing = (minNeeded - finalKept.size).coerceAtLeast(0)

        val filteredMap = response.candidatesByCategory.toMutableMap()
        filteredMap[CategoryDto.FOOD] = finalKept

        return FoodEnforceResult(
            filteredResponse = response.copy(candidatesByCategory = filteredMap.toMap()),
            keptFood = finalKept,
            droppedFood = finalDropped,
            missingCountToReach = missing,
            hardConstraintsBlock = buildFoodConstraintsBlockStrict(policy),
            avoidGoogleQueries = finalKept.mapNotNull { it.location.googleMapsQuery }.filter { it.isNotBlank() }
        )
    }

    private fun matchesFoodPolicySoft(c: CandidateDto, p: FoodPolicy): Boolean {
        // costTier soft: accept null; accept within +/-1 when present
        if (p.targetCostTier != null) {
            val tier = c.costTier
            if (tier != null) {
                val ok = kotlin.math.abs(tier - p.targetCostTier) <= 1
                if (!ok) return false
            }
        }

        if (p.mealMoments.isNotEmpty()) {
            val tagsUpper = c.vibeTags.map { it.trim().uppercase() }.toSet()
            val pitchLower = c.pitch.lowercase()
            val tod = c.bestTimeOfDay

            val isStrictPreset = p.presetId == "food_michelin" || p.presetId == "food_50best"

            val matchesAny = p.mealMoments.any { moment ->
                val momentUpper = moment.uppercase()
                val momentLower = moment.lowercase()

                val timeOk = timeOkForMoment(momentUpper, tod)
                if (!timeOk) return@any false

                val tagHas = tagsUpper.contains(momentUpper)
                val pitchHas = pitchLower.contains(momentLower)

                if (isStrictPreset) tagHas && pitchHas else tagHas || pitchHas
            }

            if (!matchesAny) return false
        }

        if (p.cuisines.isNotEmpty()) {
            val tagsUpper = c.vibeTags.map { it.trim().uppercase() }.toSet()
            val pitchUpper = c.pitch.uppercase()
            val ok = p.cuisines.any { cu -> tagsUpper.contains(cu) || pitchUpper.contains(cu) }
            if (!ok) return false
        }

        when (p.presetId) {
            "food_michelin" -> {
                val tagsUpper = c.vibeTags.map { it.trim().uppercase() }.toSet()
                val pitch = c.pitch.lowercase()
                if (!tagsUpper.contains("MICHELIN") && !pitch.contains("michelin")) return false
            }
            "food_50best" -> {
                val tagsUpper = c.vibeTags.map { it.trim().uppercase() }.toSet()
                val pitch = c.pitch.lowercase()
                val has50 = pitch.contains("50 best") ||
                        pitch.contains("50best") ||
                        pitch.contains("best discovery") ||
                        pitch.contains("discovery")
                if (!tagsUpper.contains("50BEST") && !has50) return false
            }
        }

        return true
    }

    private fun timeOkForMoment(momentUpper: String, tod: TimeOfDayDto): Boolean {
        if (tod == TimeOfDayDto.ANY) return true
        return when (momentUpper) {
            "BRUNCH" -> tod == TimeOfDayDto.MORNING || tod == TimeOfDayDto.AFTERNOON
            "BREAKFAST" -> tod == TimeOfDayDto.MORNING
            "LUNCH" -> tod == TimeOfDayDto.AFTERNOON
            "DINNER" -> tod == TimeOfDayDto.EVENING || tod == TimeOfDayDto.NIGHT
            "LATE_NIGHT" -> tod == TimeOfDayDto.NIGHT
            "BAKERY" -> tod == TimeOfDayDto.MORNING || tod == TimeOfDayDto.AFTERNOON
            "FOOD_MARKET" -> tod == TimeOfDayDto.MORNING || tod == TimeOfDayDto.AFTERNOON
            else -> true
        }
    }

    private fun parseFoodPolicy(text: String): FoodPolicy {
        fun readLineAfter(label: String): String? {
            val idx = text.indexOf(label)
            if (idx < 0) return null
            val after = text.substring(idx + label.length)
            return after.lineSequence().firstOrNull()?.trim()
        }

        val presetId = readLineAfter("FOOD_PRESET_ID:")?.takeIf { it.isNotBlank() }
        val targetTier = readLineAfter("FOOD_TARGET_COST_TIER:")?.toIntOrNull()

        val moments = readLineAfter("FOOD_MEAL_MOMENTS:")
            ?.split(",")
            ?.map { it.trim().uppercase() }
            ?.filter { it.isNotBlank() && it != "ANY" }
            ?.toSet()
            ?: emptySet()

        val cuisines = readLineAfter("FOOD_CUISINES:")
            ?.split(",")
            ?.map { it.trim().uppercase() }
            ?.filter { it.isNotBlank() && it != "ANY" }
            ?.toSet()
            ?: emptySet()

        return FoodPolicy(
            presetId = presetId,
            targetCostTier = targetTier,
            mealMoments = moments,
            cuisines = cuisines
        )
    }

    private fun buildFoodConstraintsBlockStrict(p: FoodPolicy): String {
        val lines = mutableListOf<String>()

        p.presetId?.let {
            when (it) {
                "food_michelin" -> lines += """- Each candidate must include vibeTags containing "MICHELIN" OR pitch must mention "Michelin" explicitly."""
                "food_50best" -> lines += """- Each candidate must include vibeTags containing "50BEST" OR pitch must mention "50 Best" / "Discovery" explicitly."""
            }
        }

        p.targetCostTier?.let { tier ->
            lines += "- Each candidate must have costTier=$tier."
        }

        if (p.mealMoments.isNotEmpty()) {
            lines += "- Each candidate must match at least one meal moment: ${p.mealMoments.joinToString()}."
            lines += """- Matching means: vibeTags includes the moment AND pitch includes the moment word AND bestTimeOfDay is compatible (or ANY)."""
        }

        if (p.cuisines.isNotEmpty()) {
            lines += "- Each candidate must match at least one cuisine: ${p.cuisines.joinToString()}."
            lines += """- Matching means: include cuisine token in vibeTags OR explicitly mention it in pitch."""
        }

        lines += "- Always set location.displayName and location.city."
        lines += "- Always set location.googleMapsQuery as 'Place Name, Neighborhood, City'."

        return lines.joinToString("\n")
    }
}
