package com.example.travelbuddy.ui.plan

import androidx.lifecycle.ViewModel
import com.example.travelbuddy.ai.dto.CandidateDto
import com.example.travelbuddy.ai.dto.CategoryDto
import com.example.travelbuddy.data.session.TripSession
import com.example.travelbuddy.model.plan.PlanBlock
import com.example.travelbuddy.model.plan.PlanBlockKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

data class PlanUiState(
    val selectedDayIndex: Int = 0,
    val totalDays: Int = 1,
    val allBlocks: List<PlanBlock> = emptyList(),
    val dayBlocks: List<PlanBlock> = emptyList()
)

class PlanViewModel(
    private val session: TripSession
) : ViewModel() {

    private val selectedDayIndex = MutableStateFlow(0)

    val uiState: StateFlow<PlanUiState> =
        combine(session.planBlocks, selectedDayIndex) { blocks, selectedDay ->
            val normalizedDay = selectedDay.coerceAtLeast(0)
            val highestDay = blocks.maxOfOrNull { it.dayIndex } ?: 0
            val totalDays = maxOf(1, highestDay + 1, normalizedDay + 1)

            PlanUiState(
                selectedDayIndex = normalizedDay,
                totalDays = totalDays,
                allBlocks = blocks,
                dayBlocks = blocks.filter { it.dayIndex == normalizedDay }
            )
        }.stateIn(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate),
            started = SharingStarted.Eagerly,
            initialValue = PlanUiState()
        )

    fun selectDay(dayIndex: Int) {
        selectedDayIndex.value = dayIndex.coerceAtLeast(0)
    }

    fun previousDay() {
        selectedDayIndex.value = (selectedDayIndex.value - 1).coerceAtLeast(0)
    }

    fun nextDay() {
        selectedDayIndex.value = selectedDayIndex.value + 1
    }

    fun addAnchor(title: String, startTime: String, durationMin: Int) {
        session.addBlock(
            PlanBlock(
                kind = PlanBlockKind.ANCHOR,
                title = title.ifBlank { "Anchor" },
                dayIndex = selectedDayIndex.value,
                category = CategoryDto.OTHER,
                startTime = startTime.ifBlank { "10:00" },
                durationMin = durationMin.coerceIn(10, 360),
                location = null
            )
        )
    }

    fun addCustom(title: String, durationMin: Int) {
        session.addBlock(
            PlanBlock(
                kind = PlanBlockKind.CUSTOM,
                title = title.ifBlank { "Activity" },
                dayIndex = selectedDayIndex.value,
                category = CategoryDto.OTHER,
                durationMin = durationMin.coerceIn(10, 360)
            )
        )
    }

    fun addSuggestionFromPinned(candidate: CandidateDto) {
        session.addBlock(
            PlanBlock(
                kind = PlanBlockKind.SUGGESTION,
                title = candidate.name,
                dayIndex = selectedDayIndex.value,
                category = candidate.category,
                startTime = null,
                durationMin = candidate.durationMin.coerceIn(10, 360),
                location = candidate.location,
                notes = candidate.pitch
            )
        )
    }

    fun moveUp(blockId: String) {
        session.moveBlockWithinDay(
            blockId = blockId,
            dayIndex = selectedDayIndex.value,
            up = true
        )
    }

    fun moveDown(blockId: String) {
        session.moveBlockWithinDay(
            blockId = blockId,
            dayIndex = selectedDayIndex.value,
            up = false
        )
    }

    fun remove(blockId: String) {
        session.removeBlock(blockId)
    }

    fun clearSelectedDay() {
        session.clearDay(selectedDayIndex.value)
    }

    fun clearAll() {
        session.clearSchedule()
    }
}