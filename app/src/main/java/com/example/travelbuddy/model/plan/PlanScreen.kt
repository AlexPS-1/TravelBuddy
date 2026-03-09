// File: com/example/travelbuddy/model/plan/PlanScreen.kt
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
import androidx.compose.foundation.lazy.items
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
import com.example.travelbuddy.ai.dto.CandidateDto
import com.example.travelbuddy.model.plan.PlanBlock
import com.example.travelbuddy.model.plan.PlanBlockKind
import com.example.travelbuddy.ui.suggestions.SuggestionsViewModel

@Composable
fun PlanScreen(
    planViewModel: PlanViewModel,
    suggestionsViewModel: SuggestionsViewModel,
    modifier: Modifier = Modifier
) {
    val allBlocks by planViewModel.blocks.collectAsState()
    val suggestionsState by suggestionsViewModel.state.collectAsState()
    val pinned: List<CandidateDto> = suggestionsState.pinnedCandidates

    var selectedDayIndex by remember { mutableIntStateOf(0) }
    val dayBlocks = allBlocks.filter { it.dayIndex == selectedDayIndex }

    var anchorTitle by remember { mutableStateOf("") }
    var anchorTime by remember { mutableStateOf("10:00") }
    var anchorDuration by remember { mutableIntStateOf(60) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Schedule", style = MaterialTheme.typography.headlineSmall)
        Text("Day ${selectedDayIndex + 1}", style = MaterialTheme.typography.labelMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add anchor", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = anchorTitle,
                    onValueChange = { anchorTitle = it },
                    label = { Text("Title") },
                    placeholder = { Text("Museum reservation, dinner, train...") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = anchorTime,
                        onValueChange = { anchorTime = it },
                        label = { Text("Time (HH:mm)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = anchorDuration.toString(),
                        onValueChange = { v -> anchorDuration = v.toIntOrNull() ?: anchorDuration },
                        label = { Text("Min") },
                        modifier = Modifier.weight(0.7f)
                    )
                }

                Button(
                    onClick = {
                        planViewModel.addAnchor(anchorTitle, anchorTime, anchorDuration, selectedDayIndex)
                        anchorTitle = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add anchor block") }
            }
        }

        if (pinned.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Pinned places (tap to add)", style = MaterialTheme.typography.titleMedium)

                    pinned.take(6).forEach { cand ->
                        OutlinedButton(
                            onClick = { planViewModel.addSuggestionFromPinned(cand, selectedDayIndex) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(cand.name) }
                    }
                }
            }
        }

        Text("Blocks", style = MaterialTheme.typography.titleMedium)

        if (dayBlocks.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Empty schedule", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text("Add an anchor or drop in a pinned place.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(dayBlocks, key = { it.id }) { block ->
                    BlockCard(
                        block = block,
                        onUp = { planViewModel.moveUp(block.id) },
                        onDown = { planViewModel.moveDown(block.id) },
                        onRemove = { planViewModel.remove(block.id) }
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun BlockCard(
    block: PlanBlock,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val headline = when (block.kind) {
                PlanBlockKind.ANCHOR -> "Anchor"
                PlanBlockKind.SUGGESTION -> "Suggestion"
                PlanBlockKind.CUSTOM -> "Custom"
            }
            Text("$headline • ${block.title}", style = MaterialTheme.typography.titleMedium)

            val meta = buildString {
                block.startTime?.let { append(it).append(" • ") }
                append("${block.durationMin} min")
                block.location?.areaHint?.let { append(" • ").append(it) }
            }
            Text(meta, style = MaterialTheme.typography.labelMedium)

            block.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onUp) { Text("Up") }
                OutlinedButton(onClick = onDown) { Text("Down") }
                OutlinedButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}