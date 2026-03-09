package com.example.travelbuddy.ui.plan

import androidx.lifecycle.ViewModel
import com.example.travelbuddy.ai.dto.CandidateDto
import com.example.travelbuddy.ai.dto.CategoryDto
import com.example.travelbuddy.model.plan.PlanBlock
import com.example.travelbuddy.model.plan.PlanBlockKind
import com.example.travelbuddy.model.plan.PlanStore
import kotlinx.coroutines.flow.StateFlow

class PlanViewModel(
    private val planStore: PlanStore
) : ViewModel() {

    val blocks: StateFlow<List<PlanBlock>> = planStore.blocks

    fun addAnchor(title: String, startTime: String, durationMin: Int) {
        planStore.add(
            PlanBlock(
                kind = PlanBlockKind.ANCHOR,
                title = title.ifBlank { "Anchor" },
                category = CategoryDto.OTHER,
                startTime = startTime,
                durationMin = durationMin.coerceIn(10, 360),
                location = null
            )
        )
    }

    fun addSuggestionFromPinned(candidate: CandidateDto) {
        planStore.add(
            PlanBlock(
                kind = PlanBlockKind.SUGGESTION,
                title = candidate.name,
                category = candidate.category,
                startTime = null,
                durationMin = candidate.durationMin.coerceIn(10, 360),
                location = candidate.location,
                notes = candidate.pitch
            )
        )
    }

    fun remove(blockId: String) = planStore.remove(blockId)
    fun moveUp(blockId: String) = planStore.moveUp(blockId)
    fun moveDown(blockId: String) = planStore.moveDown(blockId)
    fun clear() = planStore.clear()
}
