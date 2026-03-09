// File: com/example/travelbuddy/data/session/TripSessionStore.kt
package com.example.travelbuddy.data.session

import com.example.travelbuddy.ai.dto.CandidateDto
import com.example.travelbuddy.ai.dto.CategoryDto
import com.example.travelbuddy.ai.dto.GenerateCandidatesRequestDto
import com.example.travelbuddy.data.trips.TripSessionSnapshot
import com.example.travelbuddy.model.plan.PlanBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TripSessionStore {

    private val sessions: MutableMap<String, TripSession> = mutableMapOf()

    fun getOrCreate(tripId: String): TripSession =
        sessions.getOrPut(tripId) { TripSession() }

    fun clear(tripId: String) {
        sessions.remove(tripId)
    }
}

/**
 * Per-category quick preferences.
 * These are persisted via TripSessionSnapshot.
 */
data class CategoryQuickPrefs(
    val presetId: String? = null,
    val budgetLevel: Float = 0.5f,

    val pace: Float = 0.55f,
    val walkingTolerance: Float = 0.65f,
    val iconicVsLocal: Float = 0.6f,

    // FOOD-only
    val foodMealMoments: List<String> = emptyList(),
    val foodCuisines: List<String> = emptyList(),

    // SIGHTSEEING-only
    val sightseeingInterests: List<String> = emptyList(),

    // SHOPPING-only
    val shoppingInterests: List<String> = emptyList(),

    // NIGHTLIFE-only
    val nightlifeInterests: List<String> = emptyList(),

    // MUSEUMS-only
    val museumInterests: List<String> = emptyList(),

    // EXPERIENCE-only
    val experienceInterests: List<String> = emptyList(),

    // Shared high-weight preference channel
    val extraText: String = ""
)

private fun CategoryQuickPrefs.safeCopy(): CategoryQuickPrefs {
    fun <T> safeList(getter: () -> List<T>): List<T> = runCatching(getter).getOrNull() ?: emptyList()
    return copy(
        foodMealMoments = safeList { foodMealMoments },
        foodCuisines = safeList { foodCuisines },
        sightseeingInterests = safeList { sightseeingInterests },
        shoppingInterests = safeList { shoppingInterests },
        nightlifeInterests = safeList { nightlifeInterests },
        museumInterests = safeList { museumInterests },
        experienceInterests = safeList { experienceInterests }
    )
}

class TripSession {

    private val _city = MutableStateFlow("")
    val city: StateFlow<String> = _city.asStateFlow()

    private val _suggestionsByCategory =
        MutableStateFlow<Map<CategoryDto, List<CandidateDto>>>(emptyMap())
    val suggestionsByCategory: StateFlow<Map<CategoryDto, List<CandidateDto>>> =
        _suggestionsByCategory.asStateFlow()

    private val _globalTips = MutableStateFlow<List<String>>(emptyList())
    val globalTips: StateFlow<List<String>> = _globalTips.asStateFlow()

    private val _lastRequest = MutableStateFlow<GenerateCandidatesRequestDto?>(null)
    val lastRequest: StateFlow<GenerateCandidatesRequestDto?> = _lastRequest.asStateFlow()

    private val _quickPrefsByCategory =
        MutableStateFlow<Map<CategoryDto, CategoryQuickPrefs>>(emptyMap())
    val quickPrefsByCategory: StateFlow<Map<CategoryDto, CategoryQuickPrefs>> =
        _quickPrefsByCategory.asStateFlow()

    // --- Added for Schedule Persistence ---
    private val _planBlocks = MutableStateFlow<List<PlanBlock>>(emptyList())
    val planBlocks: StateFlow<List<PlanBlock>> = _planBlocks.asStateFlow()

    fun setCity(city: String) {
        _city.value = city
    }

    fun setLastRequest(req: GenerateCandidatesRequestDto?) {
        _lastRequest.value = req
    }

    fun clearSuggestions() {
        _suggestionsByCategory.value = emptyMap()
        _globalTips.value = emptyList()
    }

    fun setSuggestionsForCategory(
        category: CategoryDto,
        list: List<CandidateDto>,
        tips: List<String> = _globalTips.value
    ) {
        _suggestionsByCategory.value =
            _suggestionsByCategory.value + (category to list.distinctBy { it.stableKey() })
        _globalTips.value = tips
    }

    fun mergeSuggestionsForCategory(
        category: CategoryDto,
        newItems: List<CandidateDto>,
        tips: List<String> = _globalTips.value
    ) {
        val existing = _suggestionsByCategory.value[category].orEmpty()
        val merged = (existing + newItems).distinctBy { it.stableKey() }
        _suggestionsByCategory.value = _suggestionsByCategory.value + (category to merged)
        _globalTips.value = tips
    }

    fun removeSuggestion(category: CategoryDto, candidateId: String) {
        val existing = _suggestionsByCategory.value[category].orEmpty()
        val updated = existing.filterNot { it.candidateId == candidateId }
        _suggestionsByCategory.value = _suggestionsByCategory.value + (category to updated)
    }

    fun prependSuggestion(category: CategoryDto, candidate: CandidateDto) {
        val existing = _suggestionsByCategory.value[category].orEmpty()
        val updated = (listOf(candidate) + existing).distinctBy { it.stableKey() }
        _suggestionsByCategory.value = _suggestionsByCategory.value + (category to updated)
    }

    fun setQuickPrefs(category: CategoryDto, prefs: CategoryQuickPrefs) {
        _quickPrefsByCategory.value = _quickPrefsByCategory.value + (category to prefs)
    }

    fun resetQuickPrefs(category: CategoryDto) {
        _quickPrefsByCategory.value = _quickPrefsByCategory.value - category
    }

    // --- Schedule Methods ---
    fun addBlock(block: PlanBlock) {
        _planBlocks.value = _planBlocks.value + block
    }

    fun removeBlock(blockId: String) {
        _planBlocks.value = _planBlocks.value.filterNot { it.id == blockId }
    }

    fun moveBlock(blockId: String, up: Boolean) {
        val list = _planBlocks.value.toMutableList()
        val idx = list.indexOfFirst { it.id == blockId }
        if (idx == -1) return
        val targetIdx = if (up) idx - 1 else idx + 1
        if (targetIdx in list.indices) {
            val tmp = list[targetIdx]
            list[targetIdx] = list[idx]
            list[idx] = tmp
            _planBlocks.value = list
        }
    }

    fun clearSchedule() {
        _planBlocks.value = emptyList()
    }

    fun toSnapshot(): TripSessionSnapshot {
        return TripSessionSnapshot(
            city = _city.value,
            globalTips = _globalTips.value,
            suggestionsByCategory = _suggestionsByCategory.value,
            quickPrefsByCategory = _quickPrefsByCategory.value,
            planBlocks = _planBlocks.value // Added to snapshot
        )
    }

    fun applySnapshot(snapshot: TripSessionSnapshot) {
        _city.value = snapshot.city
        _globalTips.value = snapshot.globalTips
        _suggestionsByCategory.value = snapshot.suggestionsByCategory
        _quickPrefsByCategory.value =
            snapshot.quickPrefsByCategory.mapValues { (_, v) -> v.safeCopy() }
        _planBlocks.value = snapshot.planBlocks // Loaded from snapshot
    }
}

private fun CandidateDto.stableKey(): String {
    val pid = location.googlePlaceId ?: ""
    val q = location.googleMapsQuery ?: ""
    val addr = location.addressHint ?: ""
    val area = location.areaHint ?: ""
    return listOf(candidateId, name, location.displayName, pid, q, addr, area)
        .joinToString("|")
        .lowercase()
        .trim()
}