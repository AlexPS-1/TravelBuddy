package com.example.travelbuddy.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    var fixedTitle by remember { mutableStateOf("") }
    var fixedTime by remember { mutableStateOf("10:00") }
    var fixedDuration by remember { mutableIntStateOf(60) }

    val pinnedFixedTimes = remember { mutableStateMapOf<String, String>() }

    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Schedule", style = MaterialTheme.typography.headlineSmall)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Trip days", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { planViewModel.previousDay() },
                            enabled = uiState.selectedDayIndex > 0
                        ) {
                            Text("Previous")
                        }

                        Text(
                            text = uiState.selectedDay?.label ?: "Day 1",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedButton(
                            onClick = { planViewModel.nextDay() },
                            enabled = uiState.selectedDayIndex < uiState.days.lastIndex
                        ) {
                            Text("Next")
                        }
                    }

                    if (uiState.days.isNotEmpty()) {
                        Text(
                            text = "Trip length: ${uiState.days.size} day(s)",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { planViewModel.clearSelectedDay() },
                            enabled = uiState.scheduledItems.isNotEmpty() || uiState.optionItems.isNotEmpty()
                        ) {
                            Text("Clear day")
                        }

                        OutlinedButton(
                            onClick = { planViewModel.clearAll() },
                            enabled = uiState.allBlocks.isNotEmpty()
                        ) {
                            Text("Clear all")
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Add scheduled item", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = fixedTitle,
                        onValueChange = { fixedTitle = it },
                        label = { Text("Title") },
                        placeholder = { Text("Dinner, museum, train, reservation...") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = fixedTime,
                            onValueChange = { fixedTime = it },
                            label = { Text("Time (HH:mm)") },
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = fixedDuration.toString(),
                            onValueChange = { value ->
                                fixedDuration = value.toIntOrNull() ?: fixedDuration
                            },
                            label = { Text("Min") },
                            modifier = Modifier.weight(0.7f)
                        )
                    }

                    Button(
                        onClick = {
                            planViewModel.addCustomFixedItem(
                                title = fixedTitle,
                                startTime = fixedTime,
                                durationMin = fixedDuration
                            )
                            fixedTitle = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add scheduled item")
                    }
                }
            }
        }

        if (pinned.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Pinned places", style = MaterialTheme.typography.titleMedium)

                        pinned.take(6).forEach { candidate ->
                            val timeValue = pinnedFixedTimes[candidate.candidateId] ?: "20:00"

                            CandidatePlannerRow(
                                candidate = candidate,
                                fixedTime = timeValue,
                                onFixedTimeChange = { pinnedFixedTimes[candidate.candidateId] = it },
                                onAddFixed = { planViewModel.addPinnedAsFixed(candidate, timeValue) },
                                onAddOption = { planViewModel.addPinnedAsOption(candidate) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = uiState.selectedDay?.label ?: "Current day",
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            Text("Scheduled", style = MaterialTheme.typography.titleMedium)
        }

        if (uiState.scheduledItems.isEmpty()) {
            item {
                EmptySectionCard(text = "No scheduled items yet.")
            }
        } else {
            itemsIndexed(
                items = uiState.scheduledItems,
                key = { _, item -> item.block.id }
            ) { index, item ->
                ScheduledBlockCard(
                    scheduledItem = item,
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
                    onConvertToOption = { planViewModel.convertScheduledToOption(item.block.id) },
                    canMoveUp = index > 0,
                    canMoveDown = index < uiState.scheduledItems.lastIndex
                )
            }
        }

        item {
            Text("Options / Maybe", style = MaterialTheme.typography.titleMedium)
        }

        if (uiState.optionItems.isEmpty()) {
            item {
                EmptySectionCard(text = "No options yet.")
            }
        } else {
            itemsIndexed(
                items = uiState.optionItems,
                key = { _, block -> block.id }
            ) { index, block ->
                OptionBlockCard(
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
private fun CandidatePlannerRow(
    candidate: CandidateDto,
    fixedTime: String,
    onFixedTimeChange: (String) -> Unit,
    onAddFixed: () -> Unit,
    onAddOption: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(candidate.name, style = MaterialTheme.typography.titleSmall)

            candidate.pitch?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            candidate.location.areaHint?.let {
                Text(it, style = MaterialTheme.typography.labelMedium)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = fixedTime,
                    onValueChange = onFixedTimeChange,
                    label = { Text("Time") },
                    modifier = Modifier.weight(1f)
                )

                OutlinedButton(
                    onClick = onAddFixed,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Add scheduled")
                }
            }

            OutlinedButton(
                onClick = onAddOption,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add as option")
            }
        }
    }
}

@Composable
private fun EmptySectionCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ScheduledBlockCard(
    scheduledItem: ScheduledPlanItemUi,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
    onSave: (String, String, Int, String) -> Unit,
    onConvertToOption: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean
) {
    val block = scheduledItem.block

    var isEditing by remember(block.id) { mutableStateOf(false) }
    var title by remember(block.id, block.title) { mutableStateOf(block.title) }
    var time by remember(block.id, block.startTime) { mutableStateOf(block.startTime ?: "10:00") }
    var durationText by remember(block.id, block.durationMin) { mutableStateOf(block.durationMin.toString()) }
    var notes by remember(block.id, block.notes) { mutableStateOf(block.notes.orEmpty()) }

    BlockCardFrame(
        block = block,
        timeRange = buildString {
            append(block.startTime ?: "--:--")
            scheduledItem.endTime?.let {
                append(" → ").append(it)
            }
        },
        warnings = scheduledItem.warnings,
        canMoveUp = canMoveUp,
        canMoveDown = canMoveDown,
        onUp = onUp,
        onDown = onDown,
        onRemove = onRemove
    ) {
        if (!isEditing) {
            OutlinedButton(
                onClick = { isEditing = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Edit")
            }

            OutlinedButton(
                onClick = onConvertToOption,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Move to options")
            }
        } else {
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
                    isEditing = false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save changes")
            }

            OutlinedButton(
                onClick = {
                    title = block.title
                    time = block.startTime ?: "10:00"
                    durationText = block.durationMin.toString()
                    notes = block.notes.orEmpty()
                    isEditing = false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }

            OutlinedButton(
                onClick = onConvertToOption,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Move to options")
            }
        }
    }
}

@Composable
private fun OptionBlockCard(
    block: PlanBlock,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
    onSave: (String, Int, String) -> Unit,
    onConvertToScheduled: (String) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean
) {
    var isEditing by remember(block.id) { mutableStateOf(false) }
    var title by remember(block.id, block.title) { mutableStateOf(block.title) }
    var durationText by remember(block.id, block.durationMin) { mutableStateOf(block.durationMin.toString()) }
    var notes by remember(block.id, block.notes) { mutableStateOf(block.notes.orEmpty()) }
    var convertTime by remember(block.id) { mutableStateOf("20:00") }

    BlockCardFrame(
        block = block,
        timeRange = null,
        warnings = emptyList(),
        canMoveUp = canMoveUp,
        canMoveDown = canMoveDown,
        onUp = onUp,
        onDown = onDown,
        onRemove = onRemove
    ) {
        if (!isEditing) {
            OutlinedButton(
                onClick = { isEditing = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Edit")
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

                OutlinedButton(
                    onClick = { onConvertToScheduled(convertTime) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Schedule it")
                }
            }
        } else {
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
                    isEditing = false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save changes")
            }

            OutlinedButton(
                onClick = {
                    title = block.title
                    durationText = block.durationMin.toString()
                    notes = block.notes.orEmpty()
                    isEditing = false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
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

                OutlinedButton(
                    onClick = { onConvertToScheduled(convertTime) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Schedule it")
                }
            }
        }
    }
}

@Composable
private fun BlockCardFrame(
    block: PlanBlock,
    timeRange: String?,
    warnings: List<String>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
    extraContent: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val sourceLabel = when (block.source) {
                PlanItemSource.USER -> "Custom"
                PlanItemSource.PINNED -> "Pinned"
            }

            val timingLabel = when (block.timingType) {
                PlanTimingType.FIXED -> "Scheduled"
                PlanTimingType.OPTION -> "Option"
            }

            Text(
                text = "$timingLabel • $sourceLabel • ${block.title}",
                style = MaterialTheme.typography.titleMedium
            )

            val meta = buildString {
                if (!timeRange.isNullOrBlank()) {
                    append(timeRange).append(" • ")
                } else {
                    block.startTime?.let { append(it).append(" • ") }
                }
                append("${block.durationMin} min")
                block.location?.areaHint?.let { append(" • ").append(it) }
            }

            Text(meta, style = MaterialTheme.typography.labelMedium)

            if (warnings.isNotEmpty()) {
                warnings.forEach { warning ->
                    Text(
                        text = "Warning: $warning",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            block.notes?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onUp,
                    enabled = canMoveUp
                ) {
                    Text("Up")
                }

                OutlinedButton(
                    onClick = onDown,
                    enabled = canMoveDown
                ) {
                    Text("Down")
                }

                OutlinedButton(onClick = onRemove) {
                    Text("Remove")
                }
            }

            extraContent()
        }
    }
}