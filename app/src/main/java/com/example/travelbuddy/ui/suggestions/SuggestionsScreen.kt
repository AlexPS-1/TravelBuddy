package com.example.travelbuddy.ui.suggestions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.SnackbarResult
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
import androidx.core.content.ContextCompat
import com.example.travelbuddy.ai.dto.CandidateDto
import com.example.travelbuddy.ai.dto.CategoryDto
import com.example.travelbuddy.util.MapsIntents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlin.coroutines.resume

private const val AUTO_NEAR_ME_MARKER = "[AUTO_NEAR_ME]"

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
    var isResolvingNearMe by rememberSaveable { mutableStateOf(false) }
    var nearMeResolvedLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var nearMePromptPreview by rememberSaveable { mutableStateOf<String?>(null) }

    fun runNormalGenerate() {
        nearMeResolvedLabel = null
        nearMePromptPreview = null
        viewModel.updateQuickPrefsForSelected { prefs ->
            prefs.copy(extraText = removeAutoNearMeBlock(prefs.extraText))
        }
        viewModel.generateForSelectedCategory()
    }

    suspend fun runNearMeGenerate() {
        isResolvingNearMe = true

        val location = getBestCurrentLocation(context)
        if (location == null) {
            isResolvingNearMe = false
            snackbarHostState.showSnackbar(
                message = "Could not get your location. Try again outdoors or enable location services.",
                withDismissAction = true
            )
            return
        }

        val placeInfo = getBestPlaceInfo(context, location)
        nearMeResolvedLabel = placeInfo.label ?: formatCoordinates(location)

        val nearMeText = buildNearMePrompt(
            location = location,
            placeLabel = placeInfo.label,
            exactAddress = placeInfo.exactAddress,
            category = selected,
            city = state.city.ifBlank { "current city" }
        )
        nearMePromptPreview = nearMeText

        viewModel.updateQuickPrefsForSelected { prefs ->
            prefs.copy(
                extraText = mergeAutoNearMeBlock(
                    existing = prefs.extraText,
                    nearMeBlock = nearMeText
                )
            )
        }

        isResolvingNearMe = false
        viewModel.generateForSelectedCategory()

        snackbarHostState.showSnackbar(
            message = if (placeInfo.label.isNullOrBlank()) {
                "Generating ${selected.toUiLabel()} near your current location."
            } else {
                "Generating ${selected.toUiLabel()} near ${placeInfo.label}."
            },
            withDismissAction = true
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        scope.launch {
            if (granted) {
                runNearMeGenerate()
            } else {
                snackbarHostState.showSnackbar(
                    message = "Location permission is needed for Near me suggestions.",
                    withDismissAction = true
                )
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Suggestions", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${state.city.ifBlank { "—" }} • Pinned: ${state.pinnedCandidateIds.size}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.clearAllSuggestions() }) {
                        Text("Clear all")
                    }
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Preferences • ${selected.toUiLabel()}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (prefsExpanded) {
                                    "Tweak and regenerate."
                                } else {
                                    "Tap to expand. These stay per category."
                                },
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
                        onClick = { runNormalGenerate() },
                        enabled = !state.isLoading && !isResolvingNearMe,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Generate ${selected.toUiLabel()}")
                    }

                    OutlinedButton(
                        onClick = {
                            val fineGranted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                            val coarseGranted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                            if (fineGranted || coarseGranted) {
                                scope.launch { runNearMeGenerate() }
                            } else {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        enabled = !state.isLoading && !isResolvingNearMe,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isResolvingNearMe) "Locating..." else "Near me")
                    }

                    OutlinedButton(
                        onClick = { viewModel.loadDemoSuggestions(state.city.ifBlank { "Paris" }) },
                        enabled = !state.isLoading && !isResolvingNearMe
                    ) {
                        Text("Demo")
                    }
                }
            }

            if (nearMeResolvedLabel != null) {
                item("near_me_status") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Near me active",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                nearMeResolvedLabel.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            if (nearMePromptPreview != null) {
                item("near_me_prompt_preview") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Near me prompt",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                nearMePromptPreview.orEmpty(),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (state.isLoading || isResolvingNearMe) {
                item("loading") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            state.errorMessage?.let { msg ->
                item("error") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
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
                        Column(Modifier.padding(12.dp)) {
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
                                Column {
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
                item("tips") {
                    TipsCard(tips = state.globalTips)
                }
            }

            if (!state.isLoading && !isResolvingNearMe && listForCategory.isEmpty() && state.errorMessage == null) {
                item("empty") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "No ${selected.toUiLabel()} yet.",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Tap “Generate” or “Near me” to get ideas for this category.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Swipe left to pin. Swipe right to delete.",
                                style = MaterialTheme.typography.labelMedium
                            )
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
                                if (res == SnackbarResult.ActionPerformed) {
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
                                    if (res == SnackbarResult.ActionPerformed) {
                                        viewModel.undoDelete(removed)
                                    }
                                }
                            }
                        },
                        onOpenMaps = {
                            MapsIntents.openLocationSearch(context, cand.location)
                        }
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
                    onPin()
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onDelete()
                    true
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
                Column(Modifier.padding(12.dp)) {
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
        Column(Modifier.padding(12.dp)) {
            Text("Local tips", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            tips.take(3).forEach { tip ->
                Text("• $tip", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: CandidateDto,
    onOpenMaps: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
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

            OutlinedButton(onClick = onOpenMaps) {
                Text("Open in Maps")
            }
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

private fun mergeAutoNearMeBlock(
    existing: String,
    nearMeBlock: String
): String {
    val cleaned = removeAutoNearMeBlock(existing).trim()
    return buildString {
        if (cleaned.isNotBlank()) {
            append(cleaned)
            append("\n\n")
        }
        append(AUTO_NEAR_ME_MARKER)
        append(" ")
        append(nearMeBlock.trim())
    }.trim()
}

private fun removeAutoNearMeBlock(existing: String): String {
    return existing
        .lineSequence()
        .filterNot { it.trim().startsWith(AUTO_NEAR_ME_MARKER) }
        .joinToString("\n")
        .trim()
}

private fun buildNearMePrompt(
    location: Location,
    placeLabel: String?,
    exactAddress: String?,
    category: CategoryDto,
    city: String
): String {
    val lat = String.format(Locale.US, "%.5f", location.latitude)
    val lng = String.format(Locale.US, "%.5f", location.longitude)

    return buildString {
        append("Use the user's current live location as a strict local constraint for ")
        append(category.toUiLabel())
        append(" suggestions. ")

        if (!placeLabel.isNullOrBlank()) {
            append("The user is currently near ")
            append(placeLabel)
            append(". ")
        }

        if (!exactAddress.isNullOrBlank()) {
            append("Approximate exact address or street-level location: ")
            append(exactAddress)
            append(". ")
        }

        append("The trip city may be ")
        append(city)
        append(", but prioritize what is actually near the user right now. ")
        append("Only recommend places that are realistically close, ideally within roughly 1 to 2 km or a short walk. ")
        append("Avoid suggesting places from far-away neighborhoods or across the city. ")
        append("If nearby options are limited, prefer fewer but truly local suggestions rather than broad city-wide suggestions. ")
        append("Current coordinates: ")
        append(lat)
        append(", ")
        append(lng)
        append(".")
    }
}

@SuppressLint("MissingPermission")
private suspend fun getBestCurrentLocation(context: Context): Location? {
    return withContext(Dispatchers.IO) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            return@withContext null
        }

        val providers = buildList {
            if (fineGranted) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.PASSIVE_PROVIDER)
        }.distinct()

        val cached = providers
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .sortedWith(
                compareBy<Location> { it.accuracy }
                    .thenByDescending { it.time }
            )
            .firstOrNull()

        if (cached != null) {
            return@withContext cached
        }

        for (provider in providers) {
            val current = withTimeoutOrNull(2500) {
                getCurrentLocationCompat(locationManager, provider)
            }
            if (current != null) {
                return@withContext current
            }
        }

        null
    }
}

@SuppressLint("MissingPermission")
private suspend fun getCurrentLocationCompat(
    locationManager: LocationManager,
    provider: String
): Location? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }

            runCatching {
                locationManager.getCurrentLocation(
                    provider,
                    cancellationSignal,
                    Executor { runnable -> runnable.run() },
                    Consumer<Location?> { location ->
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    }
                )
            }.onFailure {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    } else {
        runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
    }
}

private suspend fun getBestPlaceInfo(
    context: Context,
    location: Location
): PlaceInfo {
    return withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) {
            return@withContext PlaceInfo(
                label = null,
                exactAddress = null
            )
        }

        val geocoder = Geocoder(context, Locale.getDefault())
        val address = getAddressCompat(
            geocoder = geocoder,
            latitude = location.latitude,
            longitude = location.longitude
        ) ?: return@withContext PlaceInfo(
            label = null,
            exactAddress = null
        )

        val neighborhood = listOfNotNull(
            address.subLocality?.normalizePlacePart(),
            address.locality?.normalizePlacePart()
        ).firstOrNull { it.isUsefulPlacePart() && !it.looksAdministrativeRegion() }

        val city = listOfNotNull(
            address.locality?.normalizePlacePart(),
            address.adminArea?.normalizePlacePart(),
            address.countryName?.normalizePlacePart()
        ).firstOrNull {
            it.isUsefulPlacePart() &&
                    !it.equals(neighborhood, ignoreCase = true) &&
                    !it.looksAdministrativeRegion()
        }

        val broadFallback = listOfNotNull(
            address.subAdminArea?.normalizePlacePart(),
            address.adminArea?.normalizePlacePart(),
            address.countryName?.normalizePlacePart()
        ).firstOrNull {
            it.isUsefulPlacePart() &&
                    !it.equals(neighborhood, ignoreCase = true) &&
                    !it.equals(city, ignoreCase = true)
        }

        val label = when {
            !neighborhood.isNullOrBlank() && !city.isNullOrBlank() -> "$neighborhood, $city"
            !neighborhood.isNullOrBlank() -> neighborhood
            !city.isNullOrBlank() -> city
            !broadFallback.isNullOrBlank() -> broadFallback
            else -> null
        }

        val exactAddress = buildExactAddress(address)

        PlaceInfo(
            label = label,
            exactAddress = exactAddress
        )
    }
}

private suspend fun getAddressCompat(
    geocoder: Geocoder,
    latitude: Double,
    longitude: Double
): Address? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { continuation ->
            runCatching {
                geocoder.getFromLocation(
                    latitude,
                    longitude,
                    1
                ) { addresses ->
                    if (continuation.isActive) {
                        continuation.resume(addresses.firstOrNull())
                    }
                }
            }.onFailure {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    } else {
        runCatching {
            geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
        }.getOrNull()
    }
}

private fun buildExactAddress(address: Address): String? {
    val addressLine = runCatching { address.getAddressLine(0) }.getOrNull()
        ?.normalizeAddressLine()
        ?.takeIf { it.isUsefulExactAddress() }

    if (!addressLine.isNullOrBlank()) {
        return addressLine
    }

    return listOfNotNull(
        address.thoroughfare?.normalizePlacePart(),
        address.subLocality?.normalizePlacePart(),
        address.locality?.normalizePlacePart(),
        address.adminArea?.normalizePlacePart(),
        address.countryName?.normalizePlacePart()
    ).distinct().takeIf { it.isNotEmpty() }?.joinToString(", ")
}

private fun String.isUsefulPlacePart(): Boolean {
    val trimmed = trim()
    if (trimmed.isBlank()) return false
    if (trimmed.length < 2) return false

    val mostlyDigits = trimmed.count { it.isDigit() } >= trimmed.length / 2
    if (mostlyDigits) return false

    val startsWithNumber = trimmed.firstOrNull()?.isDigit() == true
    if (startsWithNumber) return false

    return true
}

private fun String.normalizePlacePart(): String {
    return trim()
        .replace(Regex("\\s+"), " ")
        .removePrefix("Stadt ")
        .removePrefix("District of ")
        .removePrefix("Region ")
        .trim()
}

private fun String.normalizeAddressLine(): String {
    return trim()
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun String.looksAdministrativeRegion(): Boolean {
    val value = lowercase(Locale.getDefault())
    return value.contains("mittelland") ||
            value.contains("district") ||
            value.contains("county") ||
            value.contains("canton") ||
            value.contains("administrative") ||
            value.contains("region")
}

private fun String.isUsefulExactAddress(): Boolean {
    val trimmed = trim()
    if (trimmed.isBlank()) return false
    if (trimmed.length < 5) return false
    return true
}

private fun formatCoordinates(location: Location): String {
    val lat = String.format(Locale.US, "%.5f", location.latitude)
    val lng = String.format(Locale.US, "%.5f", location.longitude)
    return "$lat, $lng"
}

private data class PlaceInfo(
    val label: String?,
    val exactAddress: String?
)