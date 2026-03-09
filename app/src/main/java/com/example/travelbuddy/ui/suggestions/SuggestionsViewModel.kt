// File: com/example/travelbuddy/ui/suggestions/SuggestionsViewModel.kt
package com.example.travelbuddy.ui.suggestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelbuddy.ai.dto.CandidateDto
import com.example.travelbuddy.ai.dto.CareLevelDto
import com.example.travelbuddy.ai.dto.CategoryDto
import com.example.travelbuddy.ai.dto.GenerateCandidatesRequestDto
import com.example.travelbuddy.ai.dto.LegLiteDto
import com.example.travelbuddy.ai.dto.PreferenceProfileDto
import com.example.travelbuddy.ai.dto.SocialPrefsDto
import com.example.travelbuddy.ai.dto.ToneDto
import com.example.travelbuddy.ai.dto.TripLiteDto
import com.example.travelbuddy.data.AiRepository
import com.example.travelbuddy.data.prefs.PinnedStore
import com.example.travelbuddy.data.session.CategoryQuickPrefs
import com.example.travelbuddy.data.session.TripSessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

data class SuggestionsUiState(
    val city: String = "",
    val selectedCategory: CategoryDto = CategoryDto.FOOD,
    val suggestionsByCategory: Map<CategoryDto, List<CandidateDto>> = emptyMap(),
    val pinnedCandidateIds: Set<String> = emptySet(),
    val pinnedCandidates: List<CandidateDto> = emptyList(),
    val quickPrefsByCategory: Map<CategoryDto, CategoryQuickPrefs> = emptyMap(),
    val quickPrefsForSelected: CategoryQuickPrefs = CategoryQuickPrefs(extraText = ""),
    val globalTips: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val debugRawFirst: String? = null,
    val debugRawRepaired: String? = null
)

class SuggestionsViewModel(
    private val tripId: String,
    private val repo: AiRepository,
    private val pinnedStore: PinnedStore,
    private val tripSessionStore: TripSessionStore
) : ViewModel() {

    private val session = tripSessionStore.getOrCreate(tripId)

    private val pinnedIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val pinnedCandidatesFlow = MutableStateFlow<List<CandidateDto>>(emptyList())

    private val _state = MutableStateFlow(SuggestionsUiState())
    val state: StateFlow<SuggestionsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            pinnedIdsFlow.value = pinnedStore.loadPinnedIds(tripId)
            pinnedCandidatesFlow.value = pinnedStore.loadPinnedCandidates(tripId)

            val pinnedStateFlow = combine(pinnedIdsFlow, pinnedCandidatesFlow) { pinnedIds, pinnedCandidates ->
                pinnedIds to pinnedCandidates
            }

            combine(
                session.city,
                session.suggestionsByCategory,
                session.globalTips,
                session.quickPrefsByCategory,
                pinnedStateFlow
            ) { city, suggs, tips, quickMap, pinnedState ->
                val (pinnedIds, pinnedCandidates) = pinnedState

                val selected = _state.value.selectedCategory
                val quickForSelected = quickMap[selected] ?: defaultQuickPrefs(selected)

                SuggestionsUiState(
                    city = city,
                    selectedCategory = selected,
                    suggestionsByCategory = suggs,
                    globalTips = tips,
                    pinnedCandidateIds = pinnedIds,
                    pinnedCandidates = pinnedCandidates,
                    quickPrefsByCategory = quickMap,
                    quickPrefsForSelected = quickForSelected,
                    isLoading = _state.value.isLoading,
                    errorMessage = _state.value.errorMessage,
                    debugRawFirst = _state.value.debugRawFirst,
                    debugRawRepaired = _state.value.debugRawRepaired
                )
            }.collect { _state.value = it }
        }
    }

    fun selectCategory(category: CategoryDto) {
        _state.value = _state.value.copy(selectedCategory = category)
    }

    fun updateQuickPrefsForSelected(update: (CategoryQuickPrefs) -> CategoryQuickPrefs) {
        val cat = _state.value.selectedCategory
        val cur = session.quickPrefsByCategory.value[cat] ?: defaultQuickPrefs(cat)
        session.setQuickPrefs(cat, update(cur))
    }

    fun resetQuickPrefsForSelected() {
        session.resetQuickPrefs(_state.value.selectedCategory)
    }

    fun clearAllSuggestions() {
        session.clearSuggestions()
    }

    fun pinSuggestion(candidate: CandidateDto) {
        viewModelScope.launch {
            val res = pinnedStore.pin(tripId, candidate)
            pinnedIdsFlow.value = res.pinnedIds
            pinnedCandidatesFlow.value = res.pinnedCandidates
        }
    }

    fun undoPin(candidate: CandidateDto) {
        viewModelScope.launch {
            val res = pinnedStore.unpin(tripId, candidate)
            pinnedIdsFlow.value = res.pinnedIds
            pinnedCandidatesFlow.value = res.pinnedCandidates
        }
    }

    fun clearPinned() {
        viewModelScope.launch {
            pinnedStore.clear(tripId)
            pinnedIdsFlow.value = emptySet()
            pinnedCandidatesFlow.value = emptyList()
        }
    }

    fun deleteSuggestionWithUndo(category: CategoryDto, candidateId: String): CandidateDto? {
        val existing = _state.value.suggestionsByCategory[category].orEmpty()
        val removed = existing.firstOrNull { it.candidateId == candidateId } ?: return null
        session.removeSuggestion(category, candidateId)
        return removed
    }

    fun undoDelete(candidate: CandidateDto) {
        val cat = candidate.category
        session.prependSuggestion(cat, candidate)
    }

    fun generateForSelectedCategory() = generateForCategory(_state.value.selectedCategory)

    fun generateForCategory(category: CategoryDto) {
        val baseReq = session.lastRequest.value ?: demoBaseRequest(_state.value.city.ifBlank { "Paris" })
        val quick = session.quickPrefsByCategory.value[category] ?: defaultQuickPrefs(category)

        val updatedCare = buildMap {
            CategoryDto.values().forEach { put(it, CareLevelDto.DONT_CARE) }
            put(category, CareLevelDto.CARE_A_LOT)
        }

        val baseUserText = stripAutoBlocks((baseReq.preferences.travelProfileText ?: "").trim())

        val preferenceBrief = buildPreferenceBrief(
            category = category,
            quick = quick,
            city = baseReq.leg.city,
            existingForCategory = _state.value.suggestionsByCategory[category].orEmpty()
        )

        val extraRaw = quick.extraText.trim()
        val safeExtra = extraRaw.takeIf { it.isNotBlank() }?.let { sanitizeForModel(it) }
        val neighborhoodUpper = safeExtra?.let { extractNeighborhoodToken(it) }
        val wishTerms = safeExtra?.let { extractWishTerms(it) }.orEmpty()

        val commentPolicyBlock = buildString {
            if (safeExtra.isNullOrBlank()) return@buildString

            appendLine("COMMENT_POLICY:")
            appendLine("- Comments are IMPORTANT and should strongly influence the selection.")
            appendLine("- Treat BUTTON_PREFERENCES as MUST.")
            appendLine("- Treat COMMENT constraints as MUST-WHEN-POSSIBLE:")
            appendLine("  - If there is no conflict with button preferences, prefer comment match even over generic diversity.")
            appendLine("  - If a conflict exists, still satisfy buttons, but try to partially satisfy the comment.")
            appendLine("- Target: at least 70% of returned candidates should reflect the comment if possible.")
        }

        val mergedText = buildString {
            if (baseUserText.isNotBlank()) append(baseUserText)

            if (preferenceBrief.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(preferenceBrief)
            }

            if (commentPolicyBlock.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(commentPolicyBlock.trim())
            }

            if (isNotEmpty()) append("\n\n")
            append(
                """
PRIORITY RULES:
1) MUST satisfy BUTTON_PREFERENCES for every candidate. (Hard constraints.)
2) Comments are MUST-WHEN-POSSIBLE (strong constraints):
   - If no conflict: enforce comment on most candidates.
   - If conflict: do not break buttons; satisfy comment where possible.
3) Never ignore buttons due to comments.
""".trimIndent()
            )

            neighborhoodUpper?.let { append("\nNEIGHBORHOOD_PREFERRED: ").append(it) }
            if (wishTerms.isNotEmpty()) append("\nCOMMENT_KEYWORDS_PREFERRED: ").append(wishTerms.joinToString())
            safeExtra?.let { append("\nCOMMENT_TEXT: ").append(it) }
        }

        val req = baseReq.copy(
            preferences = baseReq.preferences.copy(
                categoryCare = updatedCare,
                social = SocialPrefsDto(instagrammable = false, trending = false, hiddenGems = false),
                travelProfileText = mergedText
            )
        )

        loadSuggestions(
            request = req,
            onlyCategory = category,
            quick = quick,
            neighborhoodUpper = neighborhoodUpper,
            wishes = safeExtra,
            wishTerms = wishTerms,
            baseUserTextForSession = baseUserText
        )
    }

    fun loadDemoSuggestions(city: String = "Paris") {
        val req = demoBaseRequest(city)
        session.setCity(city)
        session.setLastRequest(req)
        generateForSelectedCategory()
    }

    private fun loadSuggestions(
        request: GenerateCandidatesRequestDto,
        onlyCategory: CategoryDto,
        quick: CategoryQuickPrefs,
        neighborhoodUpper: String?,
        wishes: String?,
        wishTerms: List<String>,
        baseUserTextForSession: String
    ) {
        _state.value = _state.value.copy(
            isLoading = true,
            errorMessage = null,
            debugRawFirst = null,
            debugRawRepaired = null
        )

        session.setCity(request.leg.city)

        val stableReq = request.copy(
            preferences = request.preferences.copy(
                travelProfileText = baseUserTextForSession
            )
        )
        session.setLastRequest(stableReq)

        viewModelScope.launch {
            try {
                val resp = repo.generateCandidates(request)

                _state.value = _state.value.copy(
                    debugRawFirst = repo.getLastRawFirstClipped(),
                    debugRawRepaired = repo.getLastRawRepairedClipped()
                )

                val list = resp.candidatesByCategory[onlyCategory].orEmpty()

                val reranked = rerankByPrefsAndComments(
                    category = onlyCategory,
                    list = list,
                    quick = quick,
                    neighborhoodUpper = neighborhoodUpper,
                    commentText = wishes,
                    commentTerms = wishTerms
                )

                val finalList = applyNeighborhoodPreferenceFallback(
                    neighborhoodUpper = neighborhoodUpper,
                    list = reranked,
                    minNeighborhoodHitsToPrioritize = 4
                )

                session.mergeSuggestionsForCategory(onlyCategory, finalList, tips = resp.globalTips)

                _state.value = _state.value.copy(isLoading = false, errorMessage = null)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = t.message ?: "Error generating suggestions.",
                    debugRawFirst = repo.getLastRawFirstClipped(),
                    debugRawRepaired = repo.getLastRawRepairedClipped()
                )
            }
        }
    }

    private fun applyNeighborhoodPreferenceFallback(
        neighborhoodUpper: String?,
        list: List<CandidateDto>,
        minNeighborhoodHitsToPrioritize: Int
    ): List<CandidateDto> {
        val nb = neighborhoodUpper?.trim()?.lowercase().takeIf { !it.isNullOrBlank() } ?: return list

        fun isInNeighborhood(c: CandidateDto): Boolean {
            val pitch = c.pitch.lowercase()
            val tags = c.vibeTags.joinToString(" ").lowercase()
            val area = (c.location.areaHint ?: "").lowercase()
            val q = (c.location.googleMapsQuery ?: "").lowercase()
            val disp = c.location.displayName.lowercase()
            return area.contains(nb) || q.contains(nb) || disp.contains(nb) || pitch.contains(nb) || tags.contains(nb)
        }

        val (inNb, outNb) = list.partition { isInNeighborhood(it) }
        return if (inNb.size >= minNeighborhoodHitsToPrioritize) inNb + outNb else inNb + outNb
    }

    private fun rerankByPrefsAndComments(
        category: CategoryDto,
        list: List<CandidateDto>,
        quick: CategoryQuickPrefs,
        neighborhoodUpper: String?,
        commentText: String?,
        commentTerms: List<String>
    ): List<CandidateDto> {
        if (list.isEmpty()) return list

        val nb = neighborhoodUpper?.lowercase()

        fun containsAny(hay: String, needles: List<String>): Boolean {
            if (needles.isEmpty()) return false
            return needles.any { n -> n.isNotBlank() && hay.contains(n) }
        }

        fun score(c: CandidateDto): Int {
            var s = 0
            val pitch = c.pitch.lowercase()
            val tagsJoined = c.vibeTags.joinToString(" ").lowercase()
            val area = (c.location.areaHint ?: "").lowercase()
            val q = (c.location.googleMapsQuery ?: "").lowercase()
            val disp = c.location.displayName.lowercase()

            if (!nb.isNullOrBlank()) {
                val hit = area.contains(nb) || q.contains(nb) || disp.contains(nb) || pitch.contains(nb) || tagsJoined.contains(nb)
                if (hit) s += 10
            }

            if (commentTerms.isNotEmpty()) {
                val hitCount = commentTerms.count { term ->
                    val t = term.lowercase()
                    pitch.contains(t) || tagsJoined.contains(t) || q.contains(t) || disp.contains(t) || area.contains(t)
                }
                s += (hitCount.coerceAtMost(4) * 2)
            }

            if (!commentText.isNullOrBlank()) {
                val t = commentText.lowercase().take(40)
                if (t.length >= 6 && (pitch.contains(t) || q.contains(t))) s += 2
            }

            when (category) {

                CategoryDto.FOOD -> {
                    when (quick.presetId) {
                        "food_michelin" -> if (pitch.contains("michelin") || tagsJoined.contains("michelin")) s += 6
                        "food_50best" -> if (pitch.contains("50 best") || pitch.contains("discovery") || tagsJoined.contains("50best")) s += 6
                    }

                    if (quick.foodMealMoments.isNotEmpty()) {
                        val anyMomentMatch = quick.foodMealMoments.any { m ->
                            val ml = m.lowercase()
                            tagsJoined.contains(ml) || pitch.contains(ml)
                        }
                        if (anyMomentMatch) s += 4
                    }

                    if (quick.foodCuisines.isNotEmpty()) {
                        val anyCuisineMatch = quick.foodCuisines.any { cu ->
                            val cl = cu.lowercase()
                            tagsJoined.contains(cl) || pitch.contains(cl)
                        }
                        if (anyCuisineMatch) s += 2
                    }

                    val targetTier = budgetTierFromSlider(quick.budgetLevel)
                    c.costTier?.let { tier ->
                        val d = abs(tier - targetTier)
                        s += when (d) {
                            0 -> 2
                            1 -> 1
                            else -> 0
                        }
                    }
                }

                CategoryDto.DRINKS -> {
                    when (quick.presetId) {
                        "drinks_coffee" -> if (pitch.contains("coffee") || tagsJoined.contains("coffee") || tagsJoined.contains("cafe")) s += 6
                        "drinks_cocktails" -> if (pitch.contains("cocktail") || tagsJoined.contains("cocktail") || pitch.contains("bar")) s += 6
                        "drinks_beer" -> if (pitch.contains("beer") || tagsJoined.contains("beer") || pitch.contains("tap")) s += 6
                        "drinks_wine" -> if (pitch.contains("wine") || tagsJoined.contains("wine")) s += 6
                        "drinks_organic_wine" -> if (pitch.contains("natural") || pitch.contains("organic") || pitch.contains("biodynamic")) s += 6
                        "drinks_tea" -> if (pitch.contains("tea") || tagsJoined.contains("tea") || pitch.contains("afternoon tea")) s += 6
                    }

                    val targetTier = budgetTierFromSlider(quick.budgetLevel)
                    c.costTier?.let { tier ->
                        val d = abs(tier - targetTier)
                        s += when (d) {
                            0 -> 2
                            1 -> 1
                            else -> 0
                        }
                    }
                }

                CategoryDto.SHOPPING -> {
                    val text = "$pitch $tagsJoined $q $disp $area"

                    when (quick.presetId) {
                        "shop_vintage" -> if (containsAny(text, listOf("vintage", "archive", "retro"))) s += 8
                        "shop_secondhand" -> if (containsAny(text, listOf("second", "second-hand", "thrift", "charity shop", "resale", "pre-loved"))) s += 8
                        "shop_books" -> if (containsAny(text, listOf("book", "bookshop", "bookstore", "zine"))) s += 8
                        "shop_records" -> if (containsAny(text, listOf("vinyl", "record", "records", "lp", "lps"))) s += 8
                        "shop_indie_design" -> if (containsAny(text, listOf("independent", "indie", "concept store", "studio", "local designers", "curated"))) s += 8
                        "shop_fashion" -> if (containsAny(text, listOf("fashion", "boutique", "ready-to-wear", "rtw"))) s += 6
                        "shop_sneakers" -> if (containsAny(text, listOf("sneaker", "sneakers", "streetwear", "drops"))) s += 7
                        "shop_beauty" -> if (containsAny(text, listOf("beauty", "skincare", "perfume", "fragrance", "cosmetics"))) s += 6
                        "shop_home" -> if (containsAny(text, listOf("home", "decor", "interiors", "ceramics", "furniture", "design"))) s += 6
                        "shop_markets" -> if (containsAny(text, listOf("market", "arcade", "stalls", "vendors"))) s += 6
                        "shop_gifts" -> if (containsAny(text, listOf("gifts", "souvenir", "cards", "stationery"))) s += 5
                        "shop_outdoor" -> if (containsAny(text, listOf("outdoor", "hiking", "gear", "camp", "technical"))) s += 6
                        null -> {
                            if (containsAny(text, listOf("mall", "shopping centre", "flagship", "chain"))) s -= 2
                        }
                    }

                    if (quick.shoppingInterests.isNotEmpty()) {
                        val vibeScore = quick.shoppingInterests.fold(0) { acc, vibeId ->
                            acc + when (vibeId) {
                                "VIBE_HIPSTER" -> if (containsAny(text, listOf("vintage", "thrift", "vinyl", "zine", "concept store", "independent"))) 3 else 0
                                "VIBE_QUIRKY" -> if (containsAny(text, listOf("quirky", "weird", "offbeat", "oddities", "kitsch"))) 3 else 0
                                "VIBE_LOCAL_MAKERS" -> if (containsAny(text, listOf("makers", "handmade", "artisan", "studio", "local designers"))) 3 else 0
                                "VIBE_SUSTAINABLE" -> if (containsAny(text, listOf("sustainable", "ethical", "upcycled", "recycled", "pre-loved"))) 3 else 0
                                "VIBE_CURATED" -> if (containsAny(text, listOf("curated", "edit", "selection", "concept store"))) 2 else 0
                                "VIBE_BUDGET" -> if (containsAny(text, listOf("budget", "cheap", "affordable", "bargain"))) 2 else 0
                                "VIBE_LUXURY" -> if (containsAny(text, listOf("luxury", "designer", "high-end", "couture"))) 2 else 0
                                "VIBE_MINIMAL" -> if (containsAny(text, listOf("minimal", "clean lines", "scandi", "simple"))) 1 else 0
                                "VIBE_COLORFUL" -> if (containsAny(text, listOf("colorful", "colourful", "playful", "bold"))) 1 else 0
                                "VIBE_FAST_BROWSE" -> if (containsAny(text, listOf("quick", "fast browse", "grab-and-go"))) 1 else 0
                                "VIBE_SLOW_BROWSE" -> if (containsAny(text, listOf("browse", "slow", "linger", "hours"))) 1 else 0
                                "VIBE_RAINY_DAY" -> if (containsAny(text, listOf("indoors", "covered", "arcade", "rainy day"))) 1 else 0
                                else -> 0
                            }
                        }
                        s += vibeScore.coerceAtMost(10)
                    }

                    if (containsAny(text, listOf("independent", "family-run", "local", "neighborhood", "neighbourhood"))) s += 1
                }

                CategoryDto.NIGHTLIFE -> {
                    val text = "$pitch $tagsJoined $q $disp $area"

                    when (quick.presetId) {
                        "night_club" -> if (containsAny(text, listOf("club", "nightclub", "dancefloor", "bouncer"))) s += 8
                        "night_djs" -> if (containsAny(text, listOf("dj", "djs", "set", "line-up", "lineup", "resident"))) s += 8
                        "night_live_music" -> if (containsAny(text, listOf("live music", "gig", "band", "concert"))) s += 8
                        "night_jazz" -> if (containsAny(text, listOf("jazz", "swing", "blue note"))) s += 9
                        "night_techno" -> if (containsAny(text, listOf("techno", "house", "electronic", "warehouse"))) s += 9
                        "night_hiphop" -> if (containsAny(text, listOf("hip-hop", "hip hop", "r&b", "rap"))) s += 9
                        "night_cocktail_bars" -> if (containsAny(text, listOf("cocktail", "mixology", "martini", "negroni"))) s += 8
                        "night_speakeasy" -> if (containsAny(text, listOf("speakeasy", "hidden bar", "secret bar"))) s += 9
                        "night_pubs" -> if (containsAny(text, listOf("pub", "pints", "ale", "beer"))) s += 7
                        "night_rooftop" -> if (containsAny(text, listOf("rooftop", "skyline", "terrace", "views"))) s += 8
                        "night_wine_bars" -> if (containsAny(text, listOf("wine bar", "natural wine", "bottles"))) s += 8
                        "night_comedy" -> if (containsAny(text, listOf("comedy", "stand-up", "stand up", "improv"))) s += 9
                        "night_late_eats" -> if (containsAny(text, listOf("late-night", "late night", "kitchen open", "open late", "after-hours", "after hours"))) s += 7
                        null -> {
                            if (containsAny(text, listOf("tourist", "top-rated", "must-visit", "famous"))) s -= 1
                        }
                    }

                    if (quick.nightlifeInterests.isNotEmpty()) {
                        val vibeScore = quick.nightlifeInterests.fold(0) { acc, vibeId ->
                            acc + when (vibeId) {
                                "N_VIBE_UNDERGROUND" -> if (containsAny(text, listOf("underground", "warehouse", "secret", "intimate", "dive"))) 3 else 0
                                "N_VIBE_LOCAL" -> if (containsAny(text, listOf("local", "neighborhood", "neighbourhood", "regulars"))) 2 else 0
                                "N_VIBE_UPSCALE" -> if (containsAny(text, listOf("upscale", "high-end", "dress code", "luxury"))) 2 else 0
                                "N_VIBE_DANCEY" -> if (containsAny(text, listOf("dance", "dancefloor", "dj", "set"))) 3 else 0
                                "N_VIBE_CHILL" -> if (containsAny(text, listOf("chill", "laid-back", "relaxed", "low-key", "low key"))) 2 else 0
                                "N_VIBE_LOUD" -> if (containsAny(text, listOf("loud", "high-energy", "packed", "rowdy"))) 2 else 0
                                "N_VIBE_INTIMATE" -> if (containsAny(text, listOf("intimate", "small", "cozy", "cosy"))) 2 else 0
                                "N_VIBE_SOCIAL" -> if (containsAny(text, listOf("social", "mingle", "communal", "crowd"))) 1 else 0
                                "N_VIBE_ROMANTIC" -> if (containsAny(text, listOf("romantic", "date night", "candle", "moody"))) 2 else 0
                                "N_VIBE_QUEER_FRIENDLY" -> if (containsAny(text, listOf("queer", "lgbt", "lgbtq", "gay", "drag"))) 3 else 0
                                "N_VIBE_STYLISH" -> if (containsAny(text, listOf("stylish", "design", "beautiful", "instagrammable"))) 1 else 0
                                "N_VIBE_LATE" -> if (containsAny(text, listOf("open late", "late-night", "late night", "after-hours", "after hours"))) 2 else 0
                                "N_VIBE_CASUAL" -> if (containsAny(text, listOf("casual", "no fuss", "easygoing", "easy going"))) 1 else 0
                                "N_VIBE_VIEW" -> if (containsAny(text, listOf("view", "views", "skyline", "rooftop"))) 2 else 0
                                "N_VIBE_NO_RESERVATIONS" -> if (containsAny(text, listOf("walk-in", "walk in", "no reservations", "no reservation"))) 1 else 0
                                "N_VIBE_DOOR_EASY" -> if (containsAny(text, listOf("easy door", "friendly door", "no dress code"))) 1 else 0
                                "N_VIBE_DOOR_STRICT" -> if (containsAny(text, listOf("strict door", "door policy", "guest list", "dress code"))) 1 else 0
                                else -> 0
                            }
                        }
                        s += vibeScore.coerceAtMost(10)
                    }

                    val targetTier = budgetTierFromSlider(quick.budgetLevel)
                    c.costTier?.let { tier ->
                        val d = abs(tier - targetTier)
                        s += when (d) {
                            0 -> 2
                            1 -> 1
                            else -> 0
                        }
                    }
                }

                CategoryDto.MUSEUMS -> {
                    val text = "$pitch $tagsJoined $q $disp $area"

                    when (quick.presetId) {
                        "museum_modern_art" -> if (containsAny(text, listOf("modern art", "contemporary", "installation", "gallery"))) s += 8
                        "museum_classic_art" -> if (containsAny(text, listOf("classical", "old masters", "painting", "renaissance", "baroque"))) s += 8
                        "museum_history" -> if (containsAny(text, listOf("history", "historic", "heritage", "civilization", "archaeology"))) s += 8
                        "museum_science" -> if (containsAny(text, listOf("science", "technology", "space", "natural history"))) s += 8
                        "museum_design" -> if (containsAny(text, listOf("design", "industrial design", "product design", "decorative arts"))) s += 8
                        "museum_photography" -> if (containsAny(text, listOf("photography", "photo", "photographic"))) s += 9
                        "museum_fashion" -> if (containsAny(text, listOf("fashion", "couture", "textile", "costume"))) s += 9
                        "museum_architecture" -> if (containsAny(text, listOf("architecture", "architect", "building", "structural"))) s += 8
                        "museum_interactive" -> if (containsAny(text, listOf("interactive", "hands-on", "hands on", "immersive"))) s += 8
                        "museum_local_gems" -> if (containsAny(text, listOf("small museum", "local", "hidden", "less known", "niche"))) s += 8
                        "museum_iconic" -> if (containsAny(text, listOf("iconic", "famous", "must-see", "flagship museum"))) s += 8
                    }

                    if (quick.museumInterests.isNotEmpty()) {
                        val vibeScore = quick.museumInterests.fold(0) { acc, vibeId ->
                            acc + when (vibeId) {
                                "M_VIBE_SMALL" -> if (containsAny(text, listOf("small", "compact", "boutique museum"))) 2 else 0
                                "M_VIBE_BIG_COLLECTION" -> if (containsAny(text, listOf("large collection", "huge collection", "extensive", "major collection"))) 2 else 0
                                "M_VIBE_QUIET" -> if (containsAny(text, listOf("quiet", "calm", "peaceful"))) 2 else 0
                                "M_VIBE_LESS_CROWDED" -> if (containsAny(text, listOf("less crowded", "under the radar", "hidden", "local"))) 3 else 0
                                "M_VIBE_IMMERSIVE" -> if (containsAny(text, listOf("immersive", "interactive", "hands-on", "multimedia"))) 3 else 0
                                "M_VIBE_FAMILY" -> if (containsAny(text, listOf("family-friendly", "kids", "children"))) 2 else 0
                                "M_VIBE_RAINY_DAY" -> if (containsAny(text, listOf("rainy day", "indoors", "indoor"))) 1 else 0
                                "M_VIBE_ARCHITECTURE" -> if (containsAny(text, listOf("beautiful building", "architecture", "architectural", "historic building"))) 2 else 0
                                "M_VIBE_FREE" -> if (containsAny(text, listOf("free", "donation", "low-cost", "low cost"))) 2 else 0
                                "M_VIBE_QUICK_VISIT" -> if (containsAny(text, listOf("quick visit", "1 hour", "one hour", "compact"))) 1 else 0
                                "M_VIBE_DEEP_DIVE" -> if (containsAny(text, listOf("hours", "half-day", "deep dive", "extensive"))) 1 else 0
                                "M_VIBE_PHOTO_FRIENDLY" -> if (containsAny(text, listOf("photo-friendly", "photogenic", "instagrammable"))) 1 else 0
                                else -> 0
                            }
                        }
                        s += vibeScore.coerceAtMost(10)
                    }

                    val targetTier = budgetTierFromSlider(quick.budgetLevel)
                    c.costTier?.let { tier ->
                        val d = abs(tier - targetTier)
                        s += when (d) {
                            0 -> 2
                            1 -> 1
                            else -> 0
                        }
                    }
                }

                CategoryDto.EXPERIENCE -> {
                    val text = "$pitch $tagsJoined $q $disp $area"

                    when (quick.presetId) {
                        "exp_food" -> if (containsAny(text, listOf("food experience", "market tour", "tasting", "street food", "dining experience"))) s += 8
                        "exp_cooking" -> if (containsAny(text, listOf("cooking class", "cookery", "chef", "hands-on kitchen"))) s += 9
                        "exp_drinks" -> if (containsAny(text, listOf("wine tasting", "beer tasting", "brewery", "vineyard", "distillery"))) s += 9
                        "exp_tour" -> if (containsAny(text, listOf("guided tour", "tour", "guide", "host-led"))) s += 7
                        "exp_walk" -> if (containsAny(text, listOf("walking tour", "walk", "stroll", "city walk"))) s += 8
                        "exp_boat" -> if (containsAny(text, listOf("boat", "cruise", "sailing", "river tour", "canal"))) s += 8
                        "exp_outdoor" -> if (containsAny(text, listOf("outdoor", "adventure", "hike", "kayak", "climb"))) s += 8
                        "exp_sport" -> if (containsAny(text, listOf("sport", "activity", "surf", "bike", "climbing", "paddle"))) s += 8
                        "exp_art" -> if (containsAny(text, listOf("art workshop", "creative", "painting", "ceramics", "craft"))) s += 8
                        "exp_culture" -> if (containsAny(text, listOf("local culture", "cultural", "tradition", "neighborhood host", "community"))) s += 8
                        "exp_music" -> if (containsAny(text, listOf("music experience", "jam session", "music workshop", "concert experience"))) s += 8
                        "exp_unique" -> if (containsAny(text, listOf("unique", "unusual", "one-of-a-kind", "offbeat", "special"))) s += 8
                    }

                    if (quick.experienceInterests.isNotEmpty()) {
                        val vibeScore = quick.experienceInterests.fold(0) { acc, vibeId ->
                            acc + when (vibeId) {
                                "EXP_SMALL_GROUP" -> if (containsAny(text, listOf("small group", "intimate group", "limited spots"))) 2 else 0
                                "EXP_PRIVATE" -> if (containsAny(text, listOf("private", "just your group", "exclusive"))) 2 else 0
                                "EXP_LOCAL_HOST" -> if (containsAny(text, listOf("local host", "hosted by locals", "local guide"))) 3 else 0
                                "EXP_SOCIAL" -> if (containsAny(text, listOf("social", "meet people", "group", "shared table"))) 1 else 0
                                "EXP_RELAXED" -> if (containsAny(text, listOf("relaxed", "easygoing", "laid-back", "slow-paced"))) 2 else 0
                                "EXP_ACTIVE" -> if (containsAny(text, listOf("active", "energetic", "physical", "adventure"))) 2 else 0
                                "EXP_OUTDOOR" -> if (containsAny(text, listOf("outdoor", "outside", "nature", "open air"))) 2 else 0
                                "EXP_INDOOR" -> if (containsAny(text, listOf("indoor", "inside", "studio", "covered"))) 1 else 0
                                "EXP_SCENIC" -> if (containsAny(text, listOf("scenic", "views", "panoramic", "beautiful route"))) 2 else 0
                                "EXP_LEARNING" -> if (containsAny(text, listOf("learn", "learning", "class", "workshop", "educational"))) 2 else 0
                                "EXP_HANDS_ON" -> if (containsAny(text, listOf("hands-on", "hands on", "make your own", "interactive"))) 3 else 0
                                "EXP_PHOTO" -> if (containsAny(text, listOf("photo", "photography", "photogenic", "instagrammable"))) 1 else 0
                                "EXP_ROMANTIC" -> if (containsAny(text, listOf("romantic", "date", "sunset", "couples"))) 2 else 0
                                "EXP_FAMILY" -> if (containsAny(text, listOf("family-friendly", "kids", "children"))) 2 else 0
                                "EXP_LOCAL_GEMS" -> if (containsAny(text, listOf("local gem", "hidden", "offbeat", "under the radar", "local favorite"))) 3 else 0
                                else -> 0
                            }
                        }
                        s += vibeScore.coerceAtMost(10)
                    }

                    val targetTier = budgetTierFromSlider(quick.budgetLevel)
                    c.costTier?.let { tier ->
                        val d = abs(tier - targetTier)
                        s += when (d) {
                            0 -> 2
                            1 -> 1
                            else -> 0
                        }
                    }
                }

                else -> Unit
            }

            return s
        }

        return list.sortedWith(
            compareByDescending<CandidateDto> { score(it) }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun budgetTierFromSlider(x: Float): Int = when {
        x <= 0.33f -> 1
        x <= 0.66f -> 2
        else -> 3
    }

    private fun buildPreferenceBrief(
        category: CategoryDto,
        quick: CategoryQuickPrefs,
        city: String,
        existingForCategory: List<CandidateDto>
    ): String {
        val seed = UUID.randomUUID().toString().take(8)
        val avoidNames = existingForCategory
            .map { it.location.displayName.ifBlank { it.name } }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)
        val avoidText = if (avoidNames.isNotEmpty()) avoidNames.joinToString("; ") else "none yet"

        return when (category) {

            CategoryDto.FOOD -> {
                val budgetTier = budgetTierFromSlider(quick.budgetLevel)
                val moments = if (quick.foodMealMoments.isNotEmpty()) quick.foodMealMoments.joinToString() else "ANY"
                val cuisines = if (quick.foodCuisines.isNotEmpty()) quick.foodCuisines.joinToString() else "ANY"

                val style = when (quick.presetId) {
                    "food_michelin" -> "MICHELIN_ONLY (must explicitly mention Michelin in pitch)"
                    "food_50best" -> "FIFTYBEST_ONLY (must explicitly mention 50 Best / Discovery in pitch)"
                    null -> "ANY_STYLE (do NOT bias toward Michelin/50Best)"
                    else -> "ANY_STYLE"
                }

                """
BUTTON_PREFERENCES:
Category: FOOD
Style: $style
Meal moments (MUST match at least one if not ANY): $moments
Cuisine prefs (MUST match at least one if not ANY): $cuisines
Budget: prefer costTier around $budgetTier (1 cheap, 2 mid, 3 high)
Avoid repeats: $avoidText
Seed: $seed
""".trim()
            }

            CategoryDto.DRINKS -> {
                val budgetTier = budgetTierFromSlider(quick.budgetLevel)
                val preset = when (quick.presetId) {
                    "drinks_coffee" -> "COFFEE"
                    "drinks_cocktails" -> "COCKTAILS"
                    "drinks_beer" -> "BEER"
                    "drinks_wine" -> "WINE"
                    "drinks_organic_wine" -> "ORGANIC_NATURAL_WINE"
                    "drinks_tea" -> "TEA"
                    null -> "ANY"
                    else -> "ANY"
                }

                """
BUTTON_PREFERENCES:
Category: DRINKS
Focus: $preset
Budget: prefer costTier around $budgetTier (1 cheap, 2 mid, 3 high)
Avoid repeats: $avoidText
Seed: $seed
""".trim()
            }

            CategoryDto.SIGHTSEEING -> {
                val preset = when (quick.presetId) {
                    "sight_must_sees" -> "MUST_SEES (iconic landmarks + top viewpoints)"
                    "sight_local_gems" -> "LOCAL_GEMS (less touristy, more neighborhood feel)"
                    "sight_views" -> "VIEWS (lookouts, panoramas, sunsets)"
                    "sight_architecture" -> "ARCHITECTURE (notable buildings + design)"
                    "sight_history" -> "HISTORY (historic sites, old town, heritage)"
                    "sight_walk" -> "WALKS (pleasant routes + neighborhoods)"
                    "sight_day_trip" -> "DAY_TRIP (short excursion reachable from city)"
                    null -> "ANY"
                    else -> "ANY"
                }

                val interests = quick.sightseeingInterests
                val interestText = if (interests.isNotEmpty()) interests.joinToString() else "ANY"

                val antiFamous = when (quick.presetId) {
                    "sight_must_sees" -> ""
                    else -> """
FAMOUSNESS_RULES (IMPORTANT):
- Do NOT default to the city's most famous landmarks.
- Max 2 "top-10 iconic" attractions in the whole list.
- At least 70% must be neighborhood-level / local-feeling / less touristy.
""".trim()
                }

                val cityAvoid = if (city.trim().equals("London", ignoreCase = true) && quick.presetId != "sight_must_sees") """
CITY_AVOID_IF_POSSIBLE:
- Big Ben, London Eye, Tower Bridge, Buckingham Palace, Westminster Abbey, British Museum, Trafalgar Square, St Paul's Cathedral
""".trim() else ""

                """
BUTTON_PREFERENCES:
Category: SIGHTSEEING
Preset: $preset
Interests (MUST match at least one if not ANY): $interestText
$antiFamous
$cityAvoid
Avoid repeats: $avoidText
Seed: $seed
""".trim()
            }

            CategoryDto.SHOPPING -> {
                val focus = when (quick.presetId) {
                    "shop_vintage" -> "VINTAGE"
                    "shop_secondhand" -> "SECOND_HAND_THRIFT"
                    "shop_books" -> "BOOKS"
                    "shop_records" -> "RECORDS_VINYL"
                    "shop_indie_design" -> "INDIE_DESIGN_CONCEPT"
                    "shop_fashion" -> "FASHION_BOUTIQUES"
                    "shop_sneakers" -> "SNEAKERS_STREETWEAR"
                    "shop_beauty" -> "BEAUTY_FRAGRANCE"
                    "shop_home" -> "HOME_DECOR"
                    "shop_markets" -> "MARKETS"
                    "shop_gifts" -> "GIFTS_STATIONERY"
                    "shop_outdoor" -> "OUTDOOR_GEAR"
                    null -> "ANY (do NOT default to generic chains/malls)"
                    else -> "ANY"
                }

                val vibes = if (quick.shoppingInterests.isNotEmpty()) quick.shoppingInterests.joinToString() else "ANY"

                """
BUTTON_PREFERENCES:
Category: SHOPPING
Focus (MUST match if not ANY): $focus
Vibe (MUST match at least one if not ANY): $vibes

SHOPPING_OUTPUT_RULES (IMPORTANT):
- You MUST reflect Focus/Vibe in each candidate using vibeTags OR the pitch.
- Prefer independent shops, clusters/streets, and neighborhoods over generic malls.
- Avoid suggesting obvious chains/flagships unless the user comment explicitly asks for them.
- If Focus is ANY: include at least 3 "hipster" options (vintage / 2nd hand / books / records / concept stores).

Avoid repeats: $avoidText
Seed: $seed
""".trim()
            }

            CategoryDto.NIGHTLIFE -> {
                val budgetTier = budgetTierFromSlider(quick.budgetLevel)

                val focus = when (quick.presetId) {
                    "night_club" -> "CLUBS"
                    "night_djs" -> "DJ_NIGHTS"
                    "night_live_music" -> "LIVE_MUSIC"
                    "night_jazz" -> "JAZZ"
                    "night_techno" -> "TECHNO_HOUSE"
                    "night_hiphop" -> "HIPHOP_RNB"
                    "night_cocktail_bars" -> "COCKTAIL_BARS"
                    "night_speakeasy" -> "SPEAKEASY"
                    "night_pubs" -> "PUBS"
                    "night_rooftop" -> "ROOFTOPS"
                    "night_wine_bars" -> "WINE_BARS"
                    "night_comedy" -> "COMEDY"
                    "night_late_eats" -> "LATE_NIGHT_FOOD"
                    null -> "ANY (do NOT default to tourist traps)"
                    else -> "ANY"
                }

                val vibes = if (quick.nightlifeInterests.isNotEmpty()) quick.nightlifeInterests.joinToString() else "ANY"

                """
BUTTON_PREFERENCES:
Category: NIGHTLIFE
Focus (MUST match if not ANY): $focus
Vibe (MUST match at least one if not ANY): $vibes
Budget: prefer costTier around $budgetTier (1 cheap, 2 mid, 3 high)

NIGHTLIFE_OUTPUT_RULES (IMPORTANT):
- You MUST reflect Focus/Vibe in each candidate using vibeTags OR the pitch.
- Prefer neighborhood spots and real local nights over generic “top tourist bar” lists (unless comment asks for famous).
- Mix the list: at least 30% should be non-obvious picks when Focus is ANY.

Avoid repeats: $avoidText
Seed: $seed
""".trim()
            }

            CategoryDto.MUSEUMS -> {
                val budgetTier = budgetTierFromSlider(quick.budgetLevel)

                val focus = when (quick.presetId) {
                    "museum_modern_art" -> "MODERN_ART"
                    "museum_classic_art" -> "CLASSIC_ART"
                    "museum_history" -> "HISTORY"
                    "museum_science" -> "SCIENCE"
                    "museum_design" -> "DESIGN"
                    "museum_photography" -> "PHOTOGRAPHY"
                    "museum_fashion" -> "FASHION"
                    "museum_architecture" -> "ARCHITECTURE"
                    "museum_interactive" -> "INTERACTIVE"
                    "museum_local_gems" -> "LOCAL_GEMS"
                    "museum_iconic" -> "ICONIC"
                    null -> "ANY (do NOT default only to the biggest famous museums)"
                    else -> "ANY"
                }

                val vibes = if (quick.museumInterests.isNotEmpty()) quick.museumInterests.joinToString() else "ANY"

                """
BUTTON_PREFERENCES:
Category: MUSEUMS
Focus (MUST match if not ANY): $focus
Vibe (MUST match at least one if not ANY): $vibes
Budget: prefer costTier around $budgetTier (1 cheap, 2 mid, 3 high)

MUSEUM_OUTPUT_RULES (IMPORTANT):
- You MUST reflect Focus/Vibe in each candidate using vibeTags OR the pitch.
- If Focus is not ICONIC, do NOT default only to the city's most famous museums.
- Mix larger institutions with smaller or more distinctive picks when possible.
- Prefer a strong match over fame.

Avoid repeats: $avoidText
Seed: $seed
""".trim()
            }

            CategoryDto.EXPERIENCE -> {
                val budgetTier = budgetTierFromSlider(quick.budgetLevel)

                val focus = when (quick.presetId) {
                    "exp_food" -> "FOOD_EXPERIENCE"
                    "exp_cooking" -> "COOKING_CLASS"
                    "exp_drinks" -> "WINE_BEER_TASTING"
                    "exp_tour" -> "GUIDED_TOUR"
                    "exp_walk" -> "WALKING_TOUR"
                    "exp_boat" -> "BOAT_TOUR"
                    "exp_outdoor" -> "OUTDOOR_ADVENTURE"
                    "exp_sport" -> "SPORT_ACTIVITY"
                    "exp_art" -> "ART_CREATIVE"
                    "exp_culture" -> "LOCAL_CULTURE"
                    "exp_music" -> "MUSIC_EXPERIENCE"
                    "exp_unique" -> "SOMETHING_UNIQUE"
                    null -> "ANY (do NOT default to generic tourist experiences)"
                    else -> "ANY"
                }

                val vibes = if (quick.experienceInterests.isNotEmpty()) quick.experienceInterests.joinToString() else "ANY"

                """
BUTTON_PREFERENCES:
Category: EXPERIENCE
Focus (MUST match if not ANY): $focus
Vibe (MUST match at least one if not ANY): $vibes
Budget: prefer costTier around $budgetTier (1 cheap, 2 mid, 3 high)

EXPERIENCE_OUTPUT_RULES (IMPORTANT):
- You MUST reflect Focus/Vibe in each candidate using vibeTags OR the pitch.
- Prefer distinctive, memorable experiences over generic mass-tourism offerings.
- If Focus is ANY, mix classic crowd-pleasers with at least 3 more original/local-feeling picks.
- Prioritize fit, originality, and local relevance over fame.

Avoid repeats: $avoidText
Seed: $seed
""".trim()
            }

            else -> """
BUTTON_PREFERENCES:
Category: ${category.name}
Avoid repeats: $avoidText
Seed: $seed
""".trim()
        }
    }

    private fun stripAutoBlocks(text: String): String {
        if (text.isBlank()) return ""
        val lower = text.lowercase()
        val idxButtons = lower.indexOf("button_preferences:")
        val idxPriority = lower.indexOf("priority rules:")
        val idxComment = lower.indexOf("comment_policy:")
        val idxCommentText = lower.indexOf("comment_text:")
        val idxNeighborhood = lower.indexOf("neighborhood_preferred:")
        val cutIdx = listOf(idxButtons, idxPriority, idxComment, idxCommentText, idxNeighborhood)
            .filter { it >= 0 }
            .minOrNull()
        return if (cutIdx != null) text.substring(0, cutIdx).trim() else text.trim()
    }

    private fun sanitizeForModel(text: String): String {
        return text
            .replace("\"", "'")
            .replace(Regex("[\\u0000-\\u001F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(240)
    }

    private fun extractNeighborhoodToken(text: String): String? {
        val t = text.trim()
        if (t.isBlank()) return null

        Regex("""(?i)\bneighbou?rhood\s*:\s*([A-Za-z][A-Za-z'\- ]{2,})""")
            .find(t)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.length in 3..30 }
            ?.let { return it.uppercase() }

        Regex("""(?i)\b(in|near|around)\s+([A-Za-z][A-Za-z'\- ]{2,})""")
            .find(t)
            ?.groupValues
            ?.getOrNull(2)
            ?.trim()
            ?.takeIf { it.length in 3..30 }
            ?.let { return it.uppercase() }

        if (Regex("""^[A-Za-z][A-Za-z'\- ]{2,}$""").matches(t) && t.length <= 30) {
            return t.uppercase()
        }

        return null
    }

    private fun extractWishTerms(text: String): List<String> {
        val cleaned = text
            .lowercase()
            .replace(Regex("[^a-z0-9,'\\- ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val tokens = cleaned
            .split(",", ";", "|", ".")
            .map { it.trim() }
            .filter { it.length >= 3 }
            .flatMap { part -> part.split(" ").map { it.trim() } }
            .filter { it.length in 3..18 }
            .filterNot { it in setOf("with", "near", "around", "in", "and", "the", "for", "good", "nice", "best") }

        return tokens.distinct().take(6)
    }

    fun defaultQuickPrefs(category: CategoryDto): CategoryQuickPrefs {
        return when (category) {

            CategoryDto.FOOD -> CategoryQuickPrefs(
                presetId = null,
                budgetLevel = 0.7f,
                foodMealMoments = listOf("DINNER", "BRUNCH"),
                foodCuisines = emptyList(),
                extraText = ""
            )

            CategoryDto.DRINKS -> CategoryQuickPrefs(
                presetId = null,
                budgetLevel = 0.55f,
                extraText = ""
            )

            CategoryDto.SIGHTSEEING -> CategoryQuickPrefs(
                presetId = null,
                budgetLevel = 0.5f,
                pace = 0.55f,
                walkingTolerance = 0.65f,
                iconicVsLocal = 0.7f,
                sightseeingInterests = emptyList(),
                extraText = ""
            )

            CategoryDto.SHOPPING -> CategoryQuickPrefs(
                presetId = null,
                budgetLevel = 0.5f,
                shoppingInterests = listOf("VIBE_HIPSTER", "VIBE_LOCAL_MAKERS"),
                extraText = ""
            )

            CategoryDto.NIGHTLIFE -> CategoryQuickPrefs(
                presetId = null,
                budgetLevel = 0.6f,
                nightlifeInterests = listOf("N_VIBE_LOCAL", "N_VIBE_CHILL"),
                extraText = ""
            )

            CategoryDto.MUSEUMS -> CategoryQuickPrefs(
                presetId = null,
                budgetLevel = 0.45f,
                museumInterests = listOf("M_VIBE_LESS_CROWDED", "M_VIBE_RAINY_DAY"),
                extraText = ""
            )

            CategoryDto.EXPERIENCE -> CategoryQuickPrefs(
                presetId = null,
                budgetLevel = 0.55f,
                experienceInterests = listOf("EXP_LOCAL_GEMS", "EXP_LEARNING"),
                extraText = ""
            )

            else -> CategoryQuickPrefs(extraText = "")
        }
    }

    private fun demoBaseRequest(city: String): GenerateCandidatesRequestDto {
        return GenerateCandidatesRequestDto(
            trip = TripLiteDto(destinationCity = city, days = 4),
            leg = LegLiteDto(
                city = city,
                startDate = "2026-04-12",
                endDate = "2026-04-16",
                homebase = null
            ),
            preferences = PreferenceProfileDto(
                categoryCare = mapOf(CategoryDto.FOOD to CareLevelDto.CARE_A_LOT),
                pace = 0.55f,
                walkingTolerance = 0.65f,
                iconicVsLocal = 0.6f,
                social = SocialPrefsDto(instagrammable = false, trending = false, hiddenGems = false),
                travelProfileText = "Help me plan something fun."
            ),
            tone = ToneDto(style = "FRIENDLY_CONCIERGE", humorLevel = 0.3f)
        )
    }
}