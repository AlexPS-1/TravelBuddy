package com.example.travelbuddy.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.travelbuddy.ai.dto.CandidateDto
import com.example.travelbuddy.model.plan.PlanBlock
import com.example.travelbuddy.model.plan.PlanItemSource
import com.example.travelbuddy.model.plan.PlanTimingType
import com.example.travelbuddy.ui.suggestions.SuggestionsViewModel

@Composable
fun PlanScreen(
    planViewModel: PlanViewModel,
    suggestionsViewModel: SuggestionsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by planViewModel.uiState.collectAsState()
    val suggestionsState by suggestionsViewModel.state.collectAsState()
    val pinned = suggestionsState.pinnedCandidates

    var addExpanded by remember { mutableStateOf(false) }
    var fixedTitle by remember { mutableStateOf("") }
    var fixedTime by remember { mutableStateOf("10:00") }
    var fixedDuration by remember { mutableIntStateOf(60) }

    val pinnedFixedTimes = remember { mutableStateMapOf<String, String>() }

    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Schedule",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        item {
            DayHeaderCard(
                uiState = uiState,
                onPreviousDay = { planViewModel.previousDay() },
                onNextDay = { planViewModel.nextDay() },
                onSelectDay = { planViewModel.selectDay(it) },
                onClearDay = { planViewModel.clearSelectedDay() },
                onClearAll = { planViewModel.clearAll() }
            )
        }

        item {
            AddScheduledCard(
                expanded = addExpanded,
                title = fixedTitle,
                time = fixedTime,
                duration = fixedDuration,
                onExpandToggle = { addExpanded = !addExpanded },
                onTitleChange = { fixedTitle = it },
                onTimeChange = { fixedTime = it },
                onDurationChange = { fixedDuration = it.toIntOrNull() ?: fixedDuration },
                onAdd = {
                    planViewModel.addCustomFixedItem(
                        title = fixedTitle,
                        startTime = fixedTime,
                        durationMin = fixedDuration
                    )
                    fixedTitle = ""
                    addExpanded = false
                }
            )
        }

        if (pinned.isNotEmpty()) {
            item {
                PinnedSection(
                    pinned = pinned.take(6),
                    pinnedFixedTimes = pinnedFixedTimes,
                    onAddFixed = { candidate, time ->
                        planViewModel.addPinnedAsFixed(candidate, time)
                    },
                    onAddOption = { candidate ->
                        planViewModel.addPinnedAsOption(candidate)
                    }
                )
            }
        }

        item {
            SectionHeader(
                title = "Scheduled",
                subtitle = when {
                    uiState.scheduledItems.isEmpty() -> "Nothing fixed yet"
                    uiState.firstScheduledStart != null && uiState.lastScheduledEnd != null ->
                        "${uiState.firstScheduledStart} → ${uiState.lastScheduledEnd}"
                    else -> "${uiState.scheduledItems.size} items"
                }
            )
        }

        if (uiState.scheduledItems.isEmpty()) {
            item {
                EmptySectionCard(
                    title = "No scheduled items",
                    subtitle = "Add a fixed plan item or schedule one of your pinned places."
                )
            }
        } else {
            itemsIndexed(
                items = uiState.scheduledItems,
                key = { _, item -> item.block.id }
            ) { index, item ->
                ScheduledTimelineCard(
                    scheduledItem = item,
                    isFirst = index == 0,
                    isLast = index == uiState.scheduledItems.lastIndex,
                    onUp = { planViewModel.moveScheduledUp(item.block.id) },
                    onDown = { planViewModel.moveScheduledDown(item.block.id) },
                    onRemove = { planViewModel.remove(item.block.id) },
                    onSave = { title, time, duration, notes ->
                        planViewModel.updateScheduledItem(
                            blockId = item.block.id,
                            title = title,
                            startTime = time,
                            durationMin = duration,
                            notes = notes
                        )
                    },
                    onConvertToOption = {
                        planViewModel.convertScheduledToOption(item.block.id)
                    },
                    canMoveUp = index > 0,
                    canMoveDown = index < uiState.scheduledItems.lastIndex
                )
            }
        }

        item {
            SectionHeader(
                title = "Options / Maybe",
                subtitle = if (uiState.optionItems.isEmpty()) "No flexible ideas yet" else "${uiState.optionItems.size} possible stops"
            )
        }

        if (uiState.optionItems.isEmpty()) {
            item {
                EmptySectionCard(
                    title = "No options yet",
                    subtitle = "Add pinned places as options to keep alternatives for this day."
                )
            }
        } else {
            itemsIndexed(
                items = uiState.optionItems,
                key = { _, block -> block.id }
            ) { index, block ->
                OptionPlannerCard(
                    block = block,
                    onUp = { planViewModel.moveOptionUp(block.id) },
                    onDown = { planViewModel.moveOptionDown(block.id) },
                    onRemove = { planViewModel.remove(block.id) },
                    onSave = { title, duration, notes ->
                        planViewModel.updateOptionItem(
                            blockId = block.id,
                            title = title,
                            durationMin = duration,
                            notes = notes
                        )
                    },
                    onConvertToScheduled = { time ->
                        planViewModel.convertOptionToScheduled(
                            blockId = block.id,
                            startTime = time
                        )
                    },
                    canMoveUp = index > 0,
                    canMoveDown = index < uiState.optionItems.lastIndex
                )
            }
        }
    }
}

@Composable
private fun DayHeaderCard(
    uiState: PlanUiState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onSelectDay: (Int) -> Unit,
    onClearDay: () -> Unit,
    onClearAll: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.selectedDay?.label ?: "Day 1",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append("${uiState.scheduledCount} scheduled")
                            append(" · ${uiState.optionCount} options")
                            if (uiState.totalScheduledMinutes > 0) {
                                append(" · ${formatMinutes(uiState.totalScheduledMinutes)} planned")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onPreviousDay,
                        enabled = uiState.selectedDayIndex > 0
                    ) {
                        Text("Prev")
                    }
                    OutlinedButton(
                        onClick = onNextDay,
                        enabled = uiState.selectedDayIndex < uiState.days.lastIndex
                    ) {
                        Text("Next")
                    }
                }
            }

            if (uiState.days.size > 1) {
                DayChipsRow(
                    days = uiState.days,
                    selectedDayIndex = uiState.selectedDayIndex,
                    onSelectDay = onSelectDay
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SummaryChip(label = "${uiState.days.size} day trip")
                uiState.firstScheduledStart?.let { start ->
                    val span = uiState.lastScheduledEnd?.let { end -> "$start–$end" } ?: start
                    SummaryChip(label = span)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onClearDay,
                    enabled = uiState.scheduledItems.isNotEmpty() || uiState.optionItems.isNotEmpty()
                ) {
                    Text("Clear day")
                }

                OutlinedButton(
                    onClick = onClearAll,
                    enabled = uiState.allBlocks.isNotEmpty()
                ) {
                    Text("Clear all")
                }
            }
        }
    }
}

@Composable
private fun DayChipsRow(
    days: List<PlanDayUi>,
    selectedDayIndex: Int,
    onSelectDay: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 0.dp)
    ) { }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { day ->
            FilterChip(
                selected = day.index == selectedDayIndex,
                onClick = { onSelectDay(day.index) },
                label = {
                    Text(
                        text = "D${day.index + 1}",
                        maxLines = 1
                    )
                }
            )
        }
    }
}

@Composable
private fun SummaryChip(label: String) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        enabled = false,
        colors = AssistChipDefaults.assistChipColors()
    )
}

@Composable
private fun AddScheduledCard(
    expanded: Boolean,
    title: String,
    time: String,
    duration: Int,
    onExpandToggle: () -> Unit,
    onTitleChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onDurationChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quick add",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Create a fixed item for this day",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                TextButton(onClick = onExpandToggle) {
                    Text(if (expanded) "Hide" else "Add item")
                }
            }

            if (expanded) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Title") },
                    placeholder = { Text("Dinner, train, museum...") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = time,
                        onValueChange = onTimeChange,
                        label = { Text("Time") },
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = duration.toString(),
                        onValueChange = onDurationChange,
                        label = { Text("Min") },
                        modifier = Modifier.weight(0.8f)
                    )
                }

                Button(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add scheduled item")
                }
            }
        }
    }
}

@Composable
private fun PinnedSection(
    pinned: List<CandidateDto>,
    pinnedFixedTimes: SnapshotStateMap<String, String>,
    onAddFixed: (CandidateDto, String) -> Unit,
    onAddOption: (CandidateDto) -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader(
                title = "Pinned places",
                subtitle = "Add them as fixed plans or keep them as options"
            )

            pinned.forEach { candidate ->
                val timeValue = pinnedFixedTimes[candidate.candidateId] ?: "20:00"

                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = candidate.name,
                            style = MaterialTheme.typography.titleSmall
                        )

                        candidate.pitch?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            candidate.location.areaHint?.let { SummaryChip(label = it) }
                            SummaryChip(label = "${candidate.durationMin} min")
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = timeValue,
                                onValueChange = { pinnedFixedTimes[candidate.candidateId] = it },
                                label = { Text("Time") },
                                modifier = Modifier.weight(1f)
                            )

                            Button(
                                onClick = { onAddFixed(candidate, timeValue) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Schedule")
                            }
                        }

                        TextButton(
                            onClick = { onAddOption(candidate) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Add as option")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun EmptySectionCard(
    title: String,
    subtitle: String
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ScheduledTimelineCard(
    scheduledItem: ScheduledPlanItemUi,
    isFirst: Boolean,
    isLast: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
    onSave: (String, String, Int, String) -> Unit,
    onConvertToOption: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean
) {
    val block = scheduledItem.block

    var expanded by remember(block.id) { mutableStateOf(false) }
    var title by remember(block.id, block.title) { mutableStateOf(block.title) }
    var time by remember(block.id, block.startTime) { mutableStateOf(block.startTime ?: "10:00") }
    var durationText by remember(block.id, block.durationMin) { mutableStateOf(block.durationMin.toString()) }
    var notes by remember(block.id, block.notes) { mutableStateOf(block.notes.orEmpty()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        TimelineRail(
            startLabel = block.startTime ?: "--:--",
            endLabel = scheduledItem.endTime ?: "",
            isFirst = isFirst,
            isLast = isLast
        )

        Spacer(modifier = Modifier.width(12.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = block.title,
                            style = MaterialTheme.typography.titleMedium
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetaChip(label = block.source.toUiLabel())
                            MetaChip(label = "${block.durationMin} min")
                            block.location?.areaHint?.let { MetaChip(label = it) }
                        }
                    }

                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Hide" else "Details")
                    }
                }

                if (scheduledItem.warnings.isNotEmpty()) {
                    WarningStrip(warnings = scheduledItem.warnings)
                }

                block.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                CompactActionRow(
                    canMoveUp = canMoveUp,
                    canMoveDown = canMoveDown,
                    onUp = onUp,
                    onDown = onDown,
                    onRemove = onRemove,
                    convertLabel = "To option",
                    onConvert = onConvertToOption
                )

                if (expanded) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = time,
                            onValueChange = { time = it },
                            label = { Text("Time") },
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = durationText,
                            onValueChange = { durationText = it },
                            label = { Text("Min") },
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            onSave(
                                title,
                                time,
                                durationText.toIntOrNull() ?: block.durationMin,
                                notes
                            )
                            expanded = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save changes")
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRail(
    startLabel: String,
    endLabel: String,
    isFirst: Boolean,
    isLast: Boolean
) {
    Column(
        modifier = Modifier.width(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = startLabel,
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            text = endLabel,
            style = MaterialTheme.typography.labelSmall
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (!isFirst) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(14.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        } else {
            Spacer(modifier = Modifier.height(14.dp))
        }

        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primary)
        )

        if (!isLast) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(72.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

@Composable
private fun OptionPlannerCard(
    block: PlanBlock,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
    onSave: (String, Int, String) -> Unit,
    onConvertToScheduled: (String) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean
) {
    var expanded by remember(block.id) { mutableStateOf(false) }
    var title by remember(block.id, block.title) { mutableStateOf(block.title) }
    var durationText by remember(block.id, block.durationMin) { mutableStateOf(block.durationMin.toString()) }
    var notes by remember(block.id, block.notes) { mutableStateOf(block.notes.orEmpty()) }
    var convertTime by remember(block.id) { mutableStateOf("20:00") }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = block.title,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetaChip(label = "Option")
                        MetaChip(label = block.source.toUiLabel())
                        MetaChip(label = "${block.durationMin} min")
                        block.location?.areaHint?.let { MetaChip(label = it) }
                    }
                }

                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "Details")
                }
            }

            block.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = convertTime,
                    onValueChange = { convertTime = it },
                    label = { Text("Time") },
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = { onConvertToScheduled(convertTime) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Schedule it")
                }
            }

            CompactActionRow(
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onUp = onUp,
                onDown = onDown,
                onRemove = onRemove,
                convertLabel = null,
                onConvert = null
            )

            if (expanded) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it },
                    label = { Text("Min") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        onSave(
                            title,
                            durationText.toIntOrNull() ?: block.durationMin,
                            notes
                        )
                        expanded = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save changes")
                }
            }
        }
    }
}

@Composable
private fun CompactActionRow(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
    convertLabel: String?,
    onConvert: (() -> Unit)?
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onUp,
            enabled = canMoveUp
        ) {
            Text("Up")
        }

        TextButton(
            onClick = onDown,
            enabled = canMoveDown
        ) {
            Text("Down")
        }

        if (convertLabel != null && onConvert != null) {
            TextButton(onClick = onConvert) {
                Text(convertLabel)
            }
        }

        TextButton(onClick = onRemove) {
            Text("Remove")
        }
    }
}

@Composable
private fun WarningStrip(warnings: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Schedule warning",
                style = MaterialTheme.typography.labelLarge
            )
            warnings.forEach { warning ->
                Text(
                    text = "• $warning",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun MetaChip(label: String) {
    AssistChip(
        onClick = {},
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        enabled = false
    )
}

private fun PlanItemSource.toUiLabel(): String {
    return when (this) {
        PlanItemSource.USER -> "Custom"
        PlanItemSource.PINNED -> "Pinned"
    }
}

private fun formatMinutes(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> "${minutes}m"
        minutes == 0 -> "${hours}h"
        else -> "${hours}h ${minutes}m"
    }
}