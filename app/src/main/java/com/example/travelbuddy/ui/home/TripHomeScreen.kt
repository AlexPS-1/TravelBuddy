package com.example.travelbuddy.ui.home

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.travelbuddy.data.trips.TripStore
import com.example.travelbuddy.data.trips.TripSummary
import kotlinx.coroutines.launch

@Composable
fun TripHomeScreen(
    tripStore: TripStore,
    onCreateTrip: () -> Unit,
    onOpenTrip: (tripId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val trips by tripStore.observeTrips().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var pendingDelete by remember { mutableStateOf<TripSummary?>(null) }

    if (pendingDelete != null) {
        val trip = pendingDelete!!
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete trip?") },
            text = {
                Text(
                    "This removes the trip from your saved list and deletes its saved suggestions/preferences.\n\n" +
                            "${trip.title.ifBlank { trip.city }} • ${trip.city}"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { tripStore.deleteTrip(trip.tripId) }
                        pendingDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("TravelBuddy", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Create new trip", style = MaterialTheme.typography.titleSmall)
                Button(onClick = onCreateTrip, modifier = Modifier.fillMaxWidth()) {
                    Text("Create new trip")
                }
            }
        }

        Text("Saved trips", style = MaterialTheme.typography.titleMedium)

        if (trips.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No saved trips yet.", style = MaterialTheme.typography.bodyMedium)
                    Text("Create one above — suggestions + prefs will persist.", style = MaterialTheme.typography.labelMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(trips, key = { it.tripId }) { trip ->
                    TripRow(
                        trip = trip,
                        onOpen = { onOpenTrip(trip.tripId) },
                        onDelete = { pendingDelete = trip }
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun TripRow(
    trip: TripSummary,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(trip.title.ifBlank { trip.city }, style = MaterialTheme.typography.titleMedium)
                Text("${trip.city} • ${trip.startDateIso} → ${trip.endDateIso}", style = MaterialTheme.typography.labelMedium)
            }

            OutlinedButton(onClick = onOpen) { Text("Open") }
            OutlinedButton(onClick = onDelete) { Text("Delete") }
        }
    }
}
