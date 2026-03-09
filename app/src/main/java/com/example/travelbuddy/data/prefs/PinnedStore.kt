// File: com/example/travelbuddy/data/prefs/PinnedStore.kt
package com.example.travelbuddy.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.travelbuddy.ai.dto.CandidateDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

private val Context.pinnedDataStore by preferencesDataStore(name = "travelbuddy_pinned")

data class PinnedStoreResult(
    val pinnedIds: Set<String>,
    val pinnedCandidates: List<CandidateDto>
)

class PinnedStore(
    private val appContext: Context
) {
    private val gson = Gson()

    private fun keyPinnedIds(tripId: String) = stringPreferencesKey("pinned_ids_$tripId")
    private fun keyPinnedCandidates(tripId: String) = stringPreferencesKey("pinned_candidates_$tripId")

    suspend fun loadPinnedIds(tripId: String): Set<String> {
        val prefs = appContext.pinnedDataStore.data.first()
        val json = prefs[keyPinnedIds(tripId)] ?: return emptySet()
        return decodeStringSet(json)
    }

    suspend fun loadPinnedCandidates(tripId: String): List<CandidateDto> {
        val prefs = appContext.pinnedDataStore.data.first()
        val json = prefs[keyPinnedCandidates(tripId)] ?: return emptyList()
        return decodeCandidates(json)
            .map { it.normalized() }
            .distinctBy { it.stablePinKey() }
    }

    suspend fun pin(tripId: String, candidate: CandidateDto): PinnedStoreResult {
        val normalized = candidate.normalized()
        val currentIds = loadPinnedIds(tripId).toMutableSet()
        val currentCandidates = loadPinnedCandidates(tripId).toMutableList()

        currentIds.add(normalized.candidateId)
        currentCandidates.removeAll { it.stablePinKey() == normalized.stablePinKey() }
        currentCandidates.add(0, normalized)

        val finalCandidates = currentCandidates.distinctBy { it.stablePinKey() }
        save(tripId, currentIds, finalCandidates)
        return PinnedStoreResult(
            pinnedIds = currentIds.toSet(),
            pinnedCandidates = finalCandidates
        )
    }

    suspend fun unpin(tripId: String, candidate: CandidateDto): PinnedStoreResult {
        val normalized = candidate.normalized()
        val currentIds = loadPinnedIds(tripId).toMutableSet()
        val currentCandidates = loadPinnedCandidates(tripId).toMutableList()

        currentIds.remove(normalized.candidateId)
        currentCandidates.removeAll {
            it.candidateId == normalized.candidateId || it.stablePinKey() == normalized.stablePinKey()
        }

        val finalCandidates = currentCandidates.distinctBy { it.stablePinKey() }
        save(tripId, currentIds, finalCandidates)
        return PinnedStoreResult(
            pinnedIds = currentIds.toSet(),
            pinnedCandidates = finalCandidates
        )
    }

    suspend fun toggle(tripId: String, candidate: CandidateDto): PinnedStoreResult {
        val ids = loadPinnedIds(tripId)
        return if (ids.contains(candidate.candidateId)) {
            unpin(tripId, candidate)
        } else {
            pin(tripId, candidate)
        }
    }

    suspend fun clear(tripId: String) {
        appContext.pinnedDataStore.edit { prefs ->
            prefs.remove(keyPinnedIds(tripId))
            prefs.remove(keyPinnedCandidates(tripId))
        }
    }

    private suspend fun save(
        tripId: String,
        ids: Set<String>,
        candidates: List<CandidateDto>
    ) {
        appContext.pinnedDataStore.edit { prefs ->
            prefs[keyPinnedIds(tripId)] = gson.toJson(ids.toList().sorted())
            prefs[keyPinnedCandidates(tripId)] = gson.toJson(candidates)
        }
    }

    private fun decodeStringSet(json: String): Set<String> {
        return runCatching {
            val type = object : TypeToken<List<String>>() {}.type
            val list = gson.fromJson<List<String>>(json, type).orEmpty()
            list.toSet()
        }.getOrDefault(emptySet())
    }

    private fun decodeCandidates(json: String): List<CandidateDto> {
        return runCatching {
            val type = object : TypeToken<List<CandidateDto>>() {}.type
            gson.fromJson<List<CandidateDto>>(json, type).orEmpty()
        }.getOrDefault(emptyList())
    }
}

private fun CandidateDto.normalized(): CandidateDto {
    return copy(
        candidateId = candidateId.trim(),
        name = name.trim(),
        pitch = pitch.trim(),
        vibeTags = vibeTags.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    )
}

private fun CandidateDto.stablePinKey(): String {
    val pid = location.googlePlaceId ?: ""
    val q = location.googleMapsQuery ?: ""
    val addr = location.addressHint ?: ""
    val area = location.areaHint ?: ""
    return listOf(category.name, candidateId, name, location.displayName, pid, q, addr, area)
        .joinToString("|")
        .lowercase()
        .trim()
}