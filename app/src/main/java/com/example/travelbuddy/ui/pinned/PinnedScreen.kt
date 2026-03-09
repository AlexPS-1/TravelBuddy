// File: com/example/travelbuddy/ui/pinned/PinnedScreen.kt
package com.example.travelbuddy.ui.pinned

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.travelbuddy.ai.dto.CandidateDto
import com.example.travelbuddy.ai.dto.CategoryDto
import com.example.travelbuddy.ui.suggestions.SuggestionsViewModel
import com.example.travelbuddy.ui.suggestions.toUiLabel
import com.example.travelbuddy.util.MapsIntents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinnedScreen(
    viewModel: SuggestionsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val grouped: Map<CategoryDto, List<CandidateDto>> = state.pinnedCandidates
        .groupBy { it.category }
        .toSortedMap(compareBy<CategoryDto> { it.ordinal })

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Pinned") },
                actions = {
                    if (state.pinnedCandidates.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearPinned() }) {
                            Text("Clear all")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (state.pinnedCandidates.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("No pinned items yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Swipe left on a suggestion to pin it here.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                grouped.forEach { (category, itemsForCategory) ->
                    item(key = "header_${category.name}") {
                        Text(
                            text = category.toUiLabel(),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    items(
                        items = itemsForCategory,
                        key = { candidate -> "${candidate.category.name}_${candidate.candidateId}" }
                    ) { candidate ->
                        PinnedCandidateCard(
                            candidate = candidate,
                            onOpenMaps = {
                                MapsIntents.openLocationSearch(context, candidate.location)
                            },
                            onRemove = {
                                viewModel.undoPin(candidate)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinnedCandidateCard(
    candidate: CandidateDto,
    onOpenMaps: () -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(candidate.name, style = MaterialTheme.typography.titleMedium)

            val subtitle = buildString {
                val area = candidate.location.areaHint
                if (!area.isNullOrBlank()) append(area).append(" • ")
                append("${candidate.durationMin}min")
                candidate.costTier?.let { append(" • ").append("€".repeat(it.coerceIn(1, 3))) }
            }

            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.labelMedium)

            Spacer(Modifier.height(8.dp))
            Text(candidate.pitch, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenMaps) {
                    Text("Open in Maps")
                }
                OutlinedButton(onClick = onRemove) {
                    Text("Remove")
                }
            }
        }
    }
}