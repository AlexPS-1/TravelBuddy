package com.example.travelbuddy.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
                                onAddFixed = {
                                    planViewModel.addPinnedAsFixed(candidate, timeValue)
                                },
                                onAddOption = {
                                    planViewModel.addPinnedAsOption(candidate)
                                }
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("No scheduled items yet.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            itemsIndexed(
                items = uiState.scheduledItems,
                key = { _, block -> block.id }
            ) { index, block ->
                BlockCard(
                    block = block,
                    onUp = { planViewModel.moveScheduledUp(block.id) },
                    onDown = { planViewModel.moveScheduledDown(block.id) },
                    onRemove = { planViewModel.remove(block.id) },
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("No options yet.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            itemsIndexed(
                items = uiState.optionItems,
                key = { _, block -> block.id }
            ) { index, block ->
                BlockCard(
                    block = block,
                    onUp = { planViewModel.moveOptionUp(block.id) },
                    onDown = { planViewModel.moveOptionDown(block.id) },
                    onRemove = { planViewModel.remove(block.id) },
                    canMoveUp = index > 0,
                    canMoveDown = index < uiState.optionItems.lastIndex
                )
            }
        }

        item {
            Spacer(modifier = Modifier.padding(bottom = 12.dp))
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
private fun BlockCard(
    block: PlanBlock,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean
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
                block.startTime?.let { append(it).append(" • ") }
                append("${block.durationMin} min")
                block.location?.areaHint?.let { append(" • ").append(it) }
            }

            Text(meta, style = MaterialTheme.typography.labelMedium)

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
        }
    }
}