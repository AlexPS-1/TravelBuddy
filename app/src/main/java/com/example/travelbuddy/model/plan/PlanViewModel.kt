// File: com/example/travelbuddy/ui/plan/PlanViewModel.kt
package com.example.travelbuddy.ui.plan

import androidx.lifecycle.ViewModel
import com.example.travelbuddy.ai.dto.CandidateDto
import com.example.travelbuddy.ai.dto.CategoryDto
import com.example.travelbuddy.data.session.TripSession
import com.example.travelbuddy.model.plan.PlanBlock
import com.example.travelbuddy.model.plan.PlanBlockKind
import kotlinx.coroutines.flow.StateFlow

class PlanViewModel(
    private val session: TripSession
) : ViewModel() {

    val blocks: StateFlow<List<PlanBlock>> = session.planBlocks

    fun addAnchor(title: String, startTime: String, durationMin: Int, dayIndex: Int) {
        session.addBlock(
            PlanBlock(
                kind = PlanBlockKind.ANCHOR,
                title = title.ifBlank { "Anchor" },
                dayIndex = dayIndex,
                category = CategoryDto.OTHER,
                startTime = startTime,
                durationMin = durationMin.coerceIn(10, 360),
                location = null
            )
        )
    }

    fun addCustom(title: String, durationMin: Int, dayIndex: Int) {
        session.addBlock(
            PlanBlock(
                kind = PlanBlockKind.CUSTOM,
                title = title.ifBlank { "Activity" },
                dayIndex = dayIndex,
                category = CategoryDto.OTHER,
                durationMin = durationMin.coerceIn(10, 360)
            )
        )
    }

    fun addSuggestionFromPinned(candidate: CandidateDto, dayIndex: Int) {
        session.addBlock(
            PlanBlock(
                kind = PlanBlockKind.SUGGESTION,
                title = candidate.name,
                dayIndex = dayIndex,
                category = candidate.category,
                startTime = null,
                durationMin = candidate.durationMin.coerceIn(10, 360),
                location = candidate.location,
                notes = candidate.pitch
            )
        )
    }

    fun remove(blockId: String) = session.removeBlock(blockId)
    fun moveUp(blockId: String) = session.moveBlock(blockId, up = true)
    fun moveDown(blockId: String) = session.moveBlock(blockId, up = false)
    fun clear() = session.clearSchedule()
}