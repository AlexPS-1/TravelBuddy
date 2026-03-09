// File: com/example/travelbuddy/ui/suggestions/SuggestionsScreen.kt
package com.example.travelbuddy.ui.suggestions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.travelbuddy.ai.dto.CandidateDto
import com.example.travelbuddy.ai.dto.CategoryDto
import com.example.travelbuddy.util.MapsIntents
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionsScreen(
    viewModel: SuggestionsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val selected = state.selectedCategory
    val quick = state.quickPrefsForSelected
    val listForCategory = state.suggestionsByCategory[selected].orEmpty()

    val listState = rememberLazyListState()
    var prefsExpanded by rememberSaveable(selected.name) { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Column {
                        Text("Suggestions", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${state.city.ifBlank { "—" }} • Pinned: ${state.pinnedCandidateIds.size}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.clearAllSuggestions() }) { Text("Clear all") }
                }
            )
        }
    ) { innerPadding: PaddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item("categories") {
                CategoryChipsRow(
                    selected = selected,
                    onSelect = {
                        viewModel.selectCategory(it)
                        prefsExpanded = false
                    }
                )
            }

            item("pref_header") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Preferences • ${selected.toUiLabel()}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (prefsExpanded) "Tweak and regenerate."
                                else "Tap to expand. These stay per category.",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        TextButton(onClick = { prefsExpanded = !prefsExpanded }) {
                            Text(if (prefsExpanded) "Collapse" else "Expand")
                        }
                    }
                }
            }

            item("prefs_body") {
                AnimatedVisibility(
                    visible = prefsExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    CategoryPreferencesCard(
                        category = selected,
                        prefs = quick,
                        onReset = { viewModel.resetQuickPrefsForSelected() },
                        onUpdate = { update -> viewModel.updateQuickPrefsForSelected(update) }
                    )
                }
            }

            item("actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.generateForSelectedCategory() },
                        enabled = !state.isLoading,
                        modifier = Modifier.weight(1f)
                    ) { Text("Generate ${selected.toUiLabel()}") }

                    OutlinedButton(
                        onClick = { viewModel.loadDemoSuggestions(state.city.ifBlank { "Paris" }) },
                        enabled = !state.isLoading
                    ) { Text("Demo") }
                }
            }

            if (state.isLoading) {
                item("loading") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                }
            }

            state.errorMessage?.let { msg ->
                item("error") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.foundation.layout.Column(Modifier.padding(12.dp)) {
                            Text("Oops", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            Text(msg, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            val first = state.debugRawFirst
            val repaired = state.debugRawRepaired
            if (!first.isNullOrBlank() || !repaired.isNullOrBlank()) {
                item("debug") {
                    var debugExpanded by rememberSaveable { mutableStateOf(false) }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.foundation.layout.Column(Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Debug: raw model output",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                TextButton(onClick = { debugExpanded = !debugExpanded }) {
                                    Text(if (debugExpanded) "Hide" else "Show")
                                }
                            }

                            AnimatedVisibility(
                                visible = debugExpanded,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                androidx.compose.foundation.layout.Column {
                                    Spacer(Modifier.height(8.dp))
                                    Text("First attempt:", style = MaterialTheme.typography.labelMedium)
                                    Text(first ?: "—", style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(10.dp))
                                    Text("Repair attempt:", style = MaterialTheme.typography.labelMedium)
                                    Text(repaired ?: "—", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            if (state.globalTips.isNotEmpty()) {
                item("tips") { TipsCard(tips = state.globalTips) }
            }

            if (!state.isLoading && listForCategory.isEmpty() && state.errorMessage == null) {
                item("empty") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.foundation.layout.Column(Modifier.padding(12.dp)) {
                            Text("No ${selected.toUiLabel()} yet.", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            Text("Tap “Generate” to get ideas for this category.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(6.dp))
                            Text("Swipe left to pin. Swipe right to delete.", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            } else {
                items(listForCategory, key = { it.candidateId }) { cand ->
                    SwipeableSuggestionCard(
                        candidate = cand,
                        onPin = {
                            viewModel.pinSuggestion(cand)
                            scope.launch {
                                val res = snackbarHostState.showSnackbar(
                                    message = "Pinned: ${cand.name}",
                                    actionLabel = "Undo",
                                    withDismissAction = true
                                )
                                if (res == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                    viewModel.undoPin(cand)
                                }
                            }
                        },
                        onDelete = {
                            val removed = viewModel.deleteSuggestionWithUndo(selected, cand.candidateId)
                            if (removed != null) {
                                scope.launch {
                                    val res = snackbarHostState.showSnackbar(
                                        message = "Deleted: ${removed.name}",
                                        actionLabel = "Undo",
                                        withDismissAction = true
                                    )
                                    if (res == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                        viewModel.undoDelete(removed)
                                    }
                                }
                            }
                        },
                        onOpenMaps = { MapsIntents.openLocationSearch(context, cand.location) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableSuggestionCard(
    candidate: CandidateDto,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onOpenMaps: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onPin(); true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onDelete(); true
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = true,
        enableDismissFromStartToEnd = true,
        backgroundContent = {
            Card(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Column(Modifier.padding(12.dp)) {
                    val label = when (dismissState.targetValue) {
                        SwipeToDismissBoxValue.EndToStart -> "Pin"
                        SwipeToDismissBoxValue.StartToEnd -> "Delete"
                        else -> " "
                    }
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    ) {
        CandidateCard(candidate = candidate, onOpenMaps = onOpenMaps)
    }
}

@Composable
private fun TipsCard(tips: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column(Modifier.padding(12.dp)) {
            Text("Local tips", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            tips.take(3).forEach { tip -> Text("• $tip", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun CandidateCard(candidate: CandidateDto, onOpenMaps: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column(Modifier.padding(12.dp)) {
            Text(candidate.name, style = MaterialTheme.typography.titleMedium)

            val sub = buildString {
                val area = candidate.location.areaHint
                if (!area.isNullOrBlank()) append(area).append(" • ")
                append("${candidate.durationMin}min")
                candidate.costTier?.let { append(" • €".repeat(it.coerceIn(1, 3))) }
            }
            Text(sub, style = MaterialTheme.typography.labelMedium)

            Spacer(Modifier.height(6.dp))
            Text(candidate.pitch, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onOpenMaps) { Text("Open in Maps") }
        }
    }
}

@Composable
private fun CategoryChipsRow(
    selected: CategoryDto,
    onSelect: (CategoryDto) -> Unit
) {
    val all = CategoryDto.values().toList()
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(all, key = { it.name }) { cat ->
            FilterChip(
                selected = selected == cat,
                onClick = { onSelect(cat) },
                label = { Text(cat.toUiLabel()) }
            )
        }
    }
}

fun CategoryDto.toUiLabel(): String = when (this) {
    CategoryDto.FOOD -> "Food"
    CategoryDto.DRINKS -> "Drinks"
    CategoryDto.SIGHTSEEING -> "Sightseeing"
    CategoryDto.SHOPPING -> "Shopping"
    CategoryDto.NIGHTLIFE -> "Nightlife"
    CategoryDto.MUSEUMS -> "Museums"
    CategoryDto.EXPERIENCE -> "Experiences"
    CategoryDto.OTHER -> "Other"
}