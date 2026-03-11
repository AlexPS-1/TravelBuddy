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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class PlanDayUi(
    val index: Int,
    val dateIso: String,
    val label: String
)

data class ScheduledPlanItemUi(
    val block: PlanBlock,
    val endTime: String?,
    val warnings: List<String>
)

data class PlanUiState(
    val days: List<PlanDayUi> = emptyList(),
    val selectedDayIndex: Int = 0,
    val selectedDay: PlanDayUi? = null,
    val scheduledItems: List<ScheduledPlanItemUi> = emptyList(),
    val optionItems: List<PlanBlock> = emptyList(),
    val allBlocks: List<PlanBlock> = emptyList(),
    val scheduledCount: Int = 0,
    val optionCount: Int = 0,
    val totalScheduledMinutes: Int = 0,
    val firstScheduledStart: String? = null,
    val lastScheduledEnd: String? = null
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

            val scheduledBlocks = dayBlocks
                .filter { it.timingType == PlanTimingType.FIXED }
                .sortedBy { it.startTime ?: "99:99" }

            val scheduledTimeline = buildScheduledTimeline(scheduledBlocks)

            PlanUiState(
                days = days,
                selectedDayIndex = safeSelectedDay,
                selectedDay = days.getOrNull(safeSelectedDay),
                scheduledItems = scheduledTimeline,
                optionItems = dayBlocks.filter { it.timingType == PlanTimingType.OPTION },
                allBlocks = blocks,
                scheduledCount = scheduledTimeline.size,
                optionCount = dayBlocks.count { it.timingType == PlanTimingType.OPTION },
                totalScheduledMinutes = scheduledTimeline.sumOf { it.block.durationMin },
                firstScheduledStart = scheduledTimeline.firstOrNull()?.block?.startTime,
                lastScheduledEnd = scheduledTimeline.lastOrNull()?.endTime
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

    fun updateScheduledItem(
        blockId: String,
        title: String,
        startTime: String,
        durationMin: Int,
        notes: String
    ) {
        val existing = session.planBlocks.value.firstOrNull { it.id == blockId } ?: return
        session.updateBlock(
            existing.copy(
                title = title.ifBlank { existing.title },
                startTime = normalizeTime(startTime),
                durationMin = durationMin.coerceIn(10, 360),
                notes = notes.ifBlank { null },
                timingType = PlanTimingType.FIXED
            )
        )
    }

    fun updateOptionItem(
        blockId: String,
        title: String,
        durationMin: Int,
        notes: String
    ) {
        val existing = session.planBlocks.value.firstOrNull { it.id == blockId } ?: return
        session.updateBlock(
            existing.copy(
                title = title.ifBlank { existing.title },
                startTime = null,
                durationMin = durationMin.coerceIn(10, 360),
                notes = notes.ifBlank { null },
                timingType = PlanTimingType.OPTION
            )
        )
    }

    fun convertOptionToScheduled(
        blockId: String,
        startTime: String
    ) {
        val existing = session.planBlocks.value.firstOrNull { it.id == blockId } ?: return
        session.updateBlock(
            existing.copy(
                timingType = PlanTimingType.FIXED,
                startTime = normalizeTime(startTime)
            )
        )
    }

    fun convertScheduledToOption(blockId: String) {
        val existing = session.planBlocks.value.firstOrNull { it.id == blockId } ?: return
        session.updateBlock(
            existing.copy(
                timingType = PlanTimingType.OPTION,
                startTime = null
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

    private fun buildScheduledTimeline(blocks: List<PlanBlock>): List<ScheduledPlanItemUi> {
        val parsed = blocks.map { block ->
            val start = parseTime(block.startTime)
            val end = start?.plusMinutes(block.durationMin.toLong())
            ParsedScheduledItem(
                block = block,
                start = start,
                end = end
            )
        }

        return parsed.mapIndexed { index, current ->
            val warnings = mutableListOf<String>()

            if (current.start == null) {
                warnings += "Invalid or missing start time"
            }

            val previous = parsed.getOrNull(index - 1)
            if (
                previous?.start != null &&
                previous.end != null &&
                current.start != null &&
                current.start.isBefore(previous.start)
            ) {
                warnings += "Starts before the previous item"
            }

            if (
                previous?.end != null &&
                current.start != null &&
                current.start.isBefore(previous.end)
            ) {
                warnings += "Overlaps the previous item"
            }

            val next = parsed.getOrNull(index + 1)
            if (
                current.end != null &&
                next?.start != null &&
                current.end.isAfter(next.start)
            ) {
                warnings += "Overlaps the next item"
            }

            ScheduledPlanItemUi(
                block = current.block,
                endTime = current.end?.format(TIME_FORMATTER),
                warnings = warnings.distinct()
            )
        }
    }

    private fun parseTime(value: String?): LocalTime? {
        if (value.isNullOrBlank()) return null
        return runCatching { LocalTime.parse(value, TIME_FORMATTER) }.getOrNull()
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

    private data class ParsedScheduledItem(
        val block: PlanBlock,
        val start: LocalTime?,
        val end: LocalTime?
    )

    private companion object {
        private val TIME_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}