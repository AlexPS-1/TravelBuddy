package com.example.travelbuddy.model.plan

import com.example.travelbuddy.ai.dto.CategoryDto
import com.example.travelbuddy.ai.dto.LocationRefDto
import java.util.UUID

enum class PlanItemSource {
    USER,
    PINNED
}

enum class PlanTimingType {
    FIXED,
    OPTION
}

data class PlanBlock(
    val id: String = UUID.randomUUID().toString(),
    val dayIndex: Int = 0,
    val source: PlanItemSource,
    val timingType: PlanTimingType,
    val title: String,
    val category: CategoryDto = CategoryDto.OTHER,
    val startTime: String? = null, // "HH:mm" only for FIXED items
    val durationMin: Int = 60,
    val location: LocationRefDto? = null,
    val notes: String? = null
)