package com.example.travelbuddy.data.trips

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.tripDataStore by preferencesDataStore(name = "travelbuddy_trips")

class TripStore(
    private val appContext: Context
) {
    private val gson = Gson()

    private val KEY_TRIPS_INDEX = stringPreferencesKey("trips_index_json")
    private fun keySession(tripId: String) = stringPreferencesKey("trip_session_$tripId")

    fun observeTrips(): Flow<List<TripSummary>> {
        return appContext.tripDataStore.data.map { prefs ->
            decodeTrips(prefs[KEY_TRIPS_INDEX])
        }
    }

    suspend fun upsertTrip(summary: TripSummary) {
        appContext.tripDataStore.edit { prefs ->
            val list = decodeTrips(prefs[KEY_TRIPS_INDEX]).toMutableList()
            val idx = list.indexOfFirst { it.tripId == summary.tripId }
            if (idx >= 0) list[idx] = summary else list.add(0, summary)
            prefs[KEY_TRIPS_INDEX] = gson.toJson(list)
        }
    }

    suspend fun deleteTrip(tripId: String) {
        appContext.tripDataStore.edit { prefs ->
            val list = decodeTrips(prefs[KEY_TRIPS_INDEX]).filterNot { it.tripId == tripId }
            prefs[KEY_TRIPS_INDEX] = gson.toJson(list)
            prefs.remove(keySession(tripId))
        }
    }

    suspend fun saveSession(tripId: String, snapshot: TripSessionSnapshot) {
        appContext.tripDataStore.edit { prefs ->
            prefs[keySession(tripId)] = gson.toJson(snapshot)
        }
    }

    suspend fun loadSession(tripId: String): TripSessionSnapshot? {
        val prefs = appContext.tripDataStore.data.first()
        val json = prefs[keySession(tripId)] ?: return null
        return runCatching { gson.fromJson(json, TripSessionSnapshot::class.java) }.getOrNull()
    }

    private fun decodeTrips(json: String?): List<TripSummary> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<TripSummary>>() {}.type
            gson.fromJson<List<TripSummary>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }
}
