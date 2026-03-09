// File: com/example/travelbuddy/model/PreferenceDraft.kt
package com.example.travelbuddy.model

import com.example.travelbuddy.ai.dto.CareLevelDto
import com.example.travelbuddy.ai.dto.CategoryDto
import com.example.travelbuddy.ai.dto.PreferenceProfileDto
import com.example.travelbuddy.ai.dto.SocialPrefsDto

data class PreferenceDraft(
    val categoryCare: Map<CategoryDto, CareLevelDto> = defaultCategoryCare(),
    val pace: Float = 0.55f,
    val walkingTolerance: Float = 0.65f,
    val comfortVsAdventure: Float = 0.5f,
    val iconicVsLocal: Float = 0.6f,
    val instagrammable: Boolean = false,
    val trending: Boolean = false,
    val hiddenGems: Boolean = false,
    val travelProfileText: String = ""
) {
    fun toDto(): PreferenceProfileDto {
        return PreferenceProfileDto(
            categoryCare = categoryCare,
            pace = pace,
            walkingTolerance = walkingTolerance,
            comfortVsAdventure = comfortVsAdventure,
            iconicVsLocal = iconicVsLocal,
            social = SocialPrefsDto(
                instagrammable = instagrammable,
                trending = trending,
                hiddenGems = hiddenGems
            ),
            travelProfileText = travelProfileText.ifBlank { null }
        )
    }
}

private fun defaultCategoryCare(): Map<CategoryDto, CareLevelDto> = mapOf(
    CategoryDto.FOOD to CareLevelDto.CARE_A_LOT,
    CategoryDto.DRINKS to CareLevelDto.SOMEWHAT,
    CategoryDto.SIGHTSEEING to CareLevelDto.CARE_A_LOT,
    CategoryDto.SHOPPING to CareLevelDto.SOMEWHAT,
    CategoryDto.NIGHTLIFE to CareLevelDto.SOMEWHAT,
    CategoryDto.MUSEUMS to CareLevelDto.SOMEWHAT,
    CategoryDto.EXPERIENCE to CareLevelDto.SOMEWHAT,
    CategoryDto.OTHER to CareLevelDto.DONT_CARE
)