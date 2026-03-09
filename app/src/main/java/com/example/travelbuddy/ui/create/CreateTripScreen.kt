package com.example.travelbuddy.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.travelbuddy.model.TripDraftStore

@Composable
fun CreateTripScreen(
    tripDraftStore: TripDraftStore,
    onBack: () -> Unit,
    onStartTrip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val draft by tripDraftStore.draft.collectAsState()

    val startOk = isIsoDate(draft.startDateIso)
    val endOk = isIsoDate(draft.endDateIso)

    val canStart =
        draft.city.isNotBlank() &&
                startOk &&
                endOk

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Create trip", style = MaterialTheme.typography.headlineSmall)
                Text("Where & when are we going?", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(onClick = onBack) { Text("Back") }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { v -> tripDraftStore.update { it.copy(title = v) } },
                    label = { Text("Trip name") },
                    placeholder = { Text("Spring break, Honeymoon, City sprint…") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = draft.city,
                    onValueChange = { v -> tripDraftStore.update { it.copy(city = v) } },
                    label = { Text("Destination city") },
                    placeholder = { Text("Paris") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = draft.startDateIso,
                    onValueChange = { v -> tripDraftStore.update { it.copy(startDateIso = v.trim()) } },
                    label = { Text("Start date (YYYY-MM-DD)") },
                    supportingText = {
                        if (draft.startDateIso.isNotBlank() && !startOk) {
                            Text("Use format YYYY-MM-DD, e.g. 2026-04-12")
                        }
                    },
                    isError = draft.startDateIso.isNotBlank() && !startOk,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = draft.endDateIso,
                    onValueChange = { v -> tripDraftStore.update { it.copy(endDateIso = v.trim()) } },
                    label = { Text("End date (YYYY-MM-DD)") },
                    supportingText = {
                        if (draft.endDateIso.isNotBlank() && !endOk) {
                            Text("Use format YYYY-MM-DD, e.g. 2026-04-16")
                        }
                    },
                    isError = draft.endDateIso.isNotBlank() && !endOk,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Multi-city trip", style = MaterialTheme.typography.titleSmall)
                        Text("We’ll add multiple homebases next.", style = MaterialTheme.typography.labelMedium)
                    }
                    Switch(
                        checked = draft.isMultiCity,
                        onCheckedChange = { checked -> tripDraftStore.update { it.copy(isMultiCity = checked) } }
                    )
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = onStartTrip,
                    enabled = canStart,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start trip")
                }
            }
        }

        Text(
            "Tip: Dates are text for now. We’ll add a date picker once the flow is stable.",
            style = MaterialTheme.typography.labelMedium
        )
    }
}

private fun isIsoDate(text: String): Boolean {
    val t = text.trim()
    if (t.length != 10) return false
    if (t[4] != '-' || t[7] != '-') return false
    val y = t.substring(0, 4).toIntOrNull() ?: return false
    val m = t.substring(5, 7).toIntOrNull() ?: return false
    val d = t.substring(8, 10).toIntOrNull() ?: return false
    if (y < 1900 || y > 2100) return false
    if (m !in 1..12) return false
    if (d !in 1..31) return false
    return true
}
