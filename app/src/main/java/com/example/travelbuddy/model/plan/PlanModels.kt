package com.example.travelbuddy.model.plan

import com.example.travelbuddy.ai.dto.CategoryDto
import com.example.travelbuddy.ai.dto.LocationRefDto
import java.util.UUID

enum class PlanBlockKind {
    ANCHOR,
    SUGGESTION,
    CUSTOM
}

data class PlanBlock(
    val id: String = UUID.randomUUID().toString(),
    val kind: PlanBlockKind,
    val title: String,
    val dayIndex: Int = 0,
    val category: CategoryDto = CategoryDto.OTHER,
    val startTime: String? = null, // "HH:mm" for anchors
    val durationMin: Int = 60,
    val location: LocationRefDto? = null,
    val notes: String? = null
)