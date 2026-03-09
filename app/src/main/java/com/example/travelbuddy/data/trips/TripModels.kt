package com.example.travelbuddy.data.trips

import com.example.travelbuddy.ai.dto.CandidateDto
import com.example.travelbuddy.ai.dto.CategoryDto
import com.example.travelbuddy.data.session.CategoryQuickPrefs

data class TripSummary(
    val tripId: String,
    val title: String,
    val city: String,
    val startDateIso: String,
    val endDateIso: String,
    val createdAtEpochMs: Long
)

data class TripSessionSnapshot(
    val city: String = "",
    val globalTips: List<String> = emptyList(),
    val suggestionsByCategory: Map<CategoryDto, List<CandidateDto>> = emptyMap(),
    val quickPrefsByCategory: Map<CategoryDto, CategoryQuickPrefs> = emptyMap()
)
