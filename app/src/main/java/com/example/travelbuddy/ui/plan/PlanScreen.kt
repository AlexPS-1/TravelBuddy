package com.example.travelbuddy.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.travelbuddy.model.plan.PlanBlock
import com.example.travelbuddy.model.plan.PlanBlockKind
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

    var anchorTitle by remember { mutableStateOf("") }
    var anchorTime by remember { mutableStateOf("10:00") }
    var anchorDuration by remember { mutableIntStateOf(60) }

    var customTitle by remember { mutableStateOf("") }
    var customDuration by remember { mutableIntStateOf(60) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Schedule", style = MaterialTheme.typography.headlineSmall)

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
                        text = "Day ${uiState.selectedDayIndex + 1}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedButton(onClick = { planViewModel.nextDay() }) {
                        Text("Next")
                    }
                }

                Text(
                    text = "Available days in schedule: ${uiState.totalDays}",
                    style = MaterialTheme.typography.labelMedium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { planViewModel.clearSelectedDay() },
                        enabled = uiState.dayBlocks.isNotEmpty()
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

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Add anchor", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = anchorTitle,
                    onValueChange = { anchorTitle = it },
                    label = { Text("Title") },
                    placeholder = { Text("Museum reservation, dinner, train...") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = anchorTime,
                        onValueChange = { anchorTime = it },
                        label = { Text("Time (HH:mm)") },
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = anchorDuration.toString(),
                        onValueChange = { value ->
                            anchorDuration = value.toIntOrNull() ?: anchorDuration
                        },
                        label = { Text("Min") },
                        modifier = Modifier.weight(0.7f)
                    )
                }

                Button(
                    onClick = {
                        planViewModel.addAnchor(anchorTitle, anchorTime, anchorDuration)
                        anchorTitle = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add anchor block")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Add custom activity", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text("Title") },
                    placeholder = { Text("Walk around old town, coffee break...") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = customDuration.toString(),
                    onValueChange = { value ->
                        customDuration = value.toIntOrNull() ?: customDuration
                    },
                    label = { Text("Min") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        planViewModel.addCustom(customTitle, customDuration)
                        customTitle = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add custom block")
                }
            }
        }

        if (pinned.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Pinned places (tap to add)", style = MaterialTheme.typography.titleMedium)

                    pinned.take(6).forEach { candidate ->
                        OutlinedButton(
                            onClick = { planViewModel.addSuggestionFromPinned(candidate) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(candidate.name)
                        }
                    }
                }
            }
        }

        Text(
            text = "Blocks for Day ${uiState.selectedDayIndex + 1}",
            style = MaterialTheme.typography.titleMedium
        )

        if (uiState.dayBlocks.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Empty schedule", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Add an anchor, a custom activity, or a pinned place.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(
                    items = uiState.dayBlocks,
                    key = { _, block -> block.id }
                ) { index, block ->
                    BlockCard(
                        block = block,
                        onUp = { planViewModel.moveUp(block.id) },
                        onDown = { planViewModel.moveDown(block.id) },
                        onRemove = { planViewModel.remove(block.id) },
                        canMoveUp = index > 0,
                        canMoveDown = index < uiState.dayBlocks.lastIndex
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
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
            val headline = when (block.kind) {
                PlanBlockKind.ANCHOR -> "Anchor"
                PlanBlockKind.SUGGESTION -> "Suggestion"
                PlanBlockKind.CUSTOM -> "Custom"
            }

            Text(
                text = "$headline • ${block.title}",
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