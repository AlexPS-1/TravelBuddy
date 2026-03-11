package com.example.travelbuddy.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelbuddy.ai.dto.CandidateDto
import com.example.travelbuddy.ai.dto.CategoryDto
import com.example.travelbuddy.data.session.TripSession
import com.example.travelbuddy.model.plan.PlanBlock
import com.example.travelbuddy.model.plan.PlanItemSource
import com.example.travelbuddy.model.plan.PlanTimingType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class PlanDayUi(
    val index: Int,
    val dateIso: String,
    val label: String
)

data class PlanUiState(
    val days: List<PlanDayUi> = emptyList(),
    val selectedDayIndex: Int = 0,
    val selectedDay: PlanDayUi? = null,
    val scheduledItems: List<PlanBlock> = emptyList(),
    val optionItems: List<PlanBlock> = emptyList(),
    val allBlocks: List<PlanBlock> = emptyList()
)

class PlanViewModel(
    private val session: TripSession,
    startDateIso: String,
    endDateIso: String
) : ViewModel() {

    private val days = buildTripDays(startDateIso, endDateIso)
    private val selectedDayIndex = MutableStateFlow(0)

    val uiState: StateFlow<PlanUiState> =
        combine(session.planBlocks, selectedDayIndex) { blocks, selectedDay ->
            val safeSelectedDay = selectedDay.coerceIn(0, days.lastIndex.coerceAtLeast(0))
            val dayBlocks = blocks.filter { it.dayIndex == safeSelectedDay }

            PlanUiState(
                days = days,
                selectedDayIndex = safeSelectedDay,
                selectedDay = days.getOrNull(safeSelectedDay),
                scheduledItems = dayBlocks
                    .filter { it.timingType == PlanTimingType.FIXED }
                    .sortedBy { it.startTime ?: "99:99" },
                optionItems = dayBlocks
                    .filter { it.timingType == PlanTimingType.OPTION },
                allBlocks = blocks
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = PlanUiState(
                days = days,
                selectedDay = days.firstOrNull()
            )
        )

    fun selectDay(dayIndex: Int) {
        selectedDayIndex.value = dayIndex.coerceIn(0, days.lastIndex.coerceAtLeast(0))
    }

    fun previousDay() {
        selectDay(selectedDayIndex.value - 1)
    }

    fun nextDay() {
        selectDay(selectedDayIndex.value + 1)
    }

    fun addCustomFixedItem(title: String, startTime: String, durationMin: Int) {
        session.addBlock(
            PlanBlock(
                dayIndex = selectedDayIndex.value,
                source = PlanItemSource.USER,
                timingType = PlanTimingType.FIXED,
                title = title.ifBlank { "Planned activity" },
                category = CategoryDto.OTHER,
                startTime = normalizeTime(startTime),
                durationMin = durationMin.coerceIn(10, 360)
            )
        )
    }

    fun addCustomOptionItem(title: String, durationMin: Int, notes: String) {
        session.addBlock(
            PlanBlock(
                dayIndex = selectedDayIndex.value,
                source = PlanItemSource.USER,
                timingType = PlanTimingType.OPTION,
                title = title.ifBlank { "Possible activity" },
                category = CategoryDto.OTHER,
                startTime = null,
                durationMin = durationMin.coerceIn(10, 360),
                notes = notes.ifBlank { null }
            )
        )
    }

    fun addPinnedAsFixed(candidate: CandidateDto, startTime: String) {
        session.addBlock(
            PlanBlock(
                dayIndex = selectedDayIndex.value,
                source = PlanItemSource.PINNED,
                timingType = PlanTimingType.FIXED,
                title = candidate.name,
                category = candidate.category,
                startTime = normalizeTime(startTime),
                durationMin = candidate.durationMin.coerceIn(10, 360),
                location = candidate.location,
                notes = candidate.pitch
            )
        )
    }

    fun addPinnedAsOption(candidate: CandidateDto) {
        session.addBlock(
            PlanBlock(
                dayIndex = selectedDayIndex.value,
                source = PlanItemSource.PINNED,
                timingType = PlanTimingType.OPTION,
                title = candidate.name,
                category = candidate.category,
                startTime = null,
                durationMin = candidate.durationMin.coerceIn(10, 360),
                location = candidate.location,
                notes = candidate.pitch
            )
        )
    }

    fun moveScheduledUp(blockId: String) {
        session.moveBlockWithinSection(
            blockId = blockId,
            dayIndex = selectedDayIndex.value,
            timingType = PlanTimingType.FIXED,
            up = true
        )
    }

    fun moveScheduledDown(blockId: String) {
        session.moveBlockWithinSection(
            blockId = blockId,
            dayIndex = selectedDayIndex.value,
            timingType = PlanTimingType.FIXED,
            up = false
        )
    }

    fun moveOptionUp(blockId: String) {
        session.moveBlockWithinSection(
            blockId = blockId,
            dayIndex = selectedDayIndex.value,
            timingType = PlanTimingType.OPTION,
            up = true
        )
    }

    fun moveOptionDown(blockId: String) {
        session.moveBlockWithinSection(
            blockId = blockId,
            dayIndex = selectedDayIndex.value,
            timingType = PlanTimingType.OPTION,
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

    private fun normalizeTime(input: String): String {
        val trimmed = input.trim()
        return if (TIME_REGEX.matches(trimmed)) trimmed else "10:00"
    }

    private fun buildTripDays(startDateIso: String, endDateIso: String): List<PlanDayUi> {
        val parsedStart = runCatching { LocalDate.parse(startDateIso) }.getOrNull()
        val parsedEnd = runCatching { LocalDate.parse(endDateIso) }.getOrNull()

        if (parsedStart == null || parsedEnd == null || parsedEnd.isBefore(parsedStart)) {
            return listOf(
                PlanDayUi(
                    index = 0,
                    dateIso = "",
                    label = "Day 1"
                )
            )
        }

        val start: LocalDate = parsedStart
        val end: LocalDate = parsedEnd

        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())

        val result = mutableListOf<PlanDayUi>()
        var cursor: LocalDate = start
        var index = 0

        while (!cursor.isAfter(end)) {
            result += PlanDayUi(
                index = index,
                dateIso = cursor.toString(),
                label = "Day ${index + 1} • ${cursor.format(formatter)}"
            )
            cursor = cursor.plusDays(1)
            index += 1
        }

        return result
    }

    private companion object {
        private val TIME_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    }
}