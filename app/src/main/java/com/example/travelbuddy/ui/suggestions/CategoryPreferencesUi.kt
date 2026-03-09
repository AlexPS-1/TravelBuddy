// File: com/example/travelbuddy/ui/suggestions/CategoryPreferencesUi.kt
package com.example.travelbuddy.ui.suggestions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.travelbuddy.ai.dto.CategoryDto
import com.example.travelbuddy.data.session.CategoryQuickPrefs

internal data class ChipOption(val id: String, val label: String)

internal data class PresetSpec(
    val label: String,
    val options: List<ChipOption>,
    val getSelectedId: (CategoryQuickPrefs) -> String?,
    val setSelectedId: (CategoryQuickPrefs, String?) -> CategoryQuickPrefs
)

internal data class MultiSelectSpec(
    val label: String,
    val options: List<ChipOption>,
    val getSelectedIds: (CategoryQuickPrefs) -> List<String>,
    val toggleId: (CategoryQuickPrefs, String) -> CategoryQuickPrefs
)

internal data class SliderSpec(
    val label: String,
    val left: String,
    val right: String,
    val getValue: (CategoryQuickPrefs) -> Float,
    val setValue: (CategoryQuickPrefs, Float) -> CategoryQuickPrefs
)

internal data class CategoryPrefsUiSpec(
    val preset: PresetSpec? = null,
    val multiSelects: List<MultiSelectSpec> = emptyList(),
    val sliders: List<SliderSpec> = emptyList(),
    val extraNotePlaceholder: String
)

internal object CategoryPrefsUiSpecs {

    fun forCategory(category: CategoryDto): CategoryPrefsUiSpec? {
        return when (category) {

            CategoryDto.FOOD -> CategoryPrefsUiSpec(
                preset = PresetSpec(
                    label = "Style (focus)",
                    options = listOf(
                        ChipOption("food_michelin", "Michelin"),
                        ChipOption("food_50best", "50 Best"),
                        ChipOption("food_street", "Street"),
                        ChipOption("food_local_gems", "Local gems"),
                        ChipOption("food_trendy", "Trendy"),
                        ChipOption("food_classic", "Classic"),
                        ChipOption("food_experimental", "Modern")
                    ),
                    getSelectedId = { it.presetId },
                    setSelectedId = { prefs, id -> prefs.copy(presetId = id) }
                ),
                multiSelects = listOf(
                    MultiSelectSpec(
                        label = "Meal moments (tags)",
                        options = listOf(
                            ChipOption("BREAKFAST", "Breakfast"),
                            ChipOption("BRUNCH", "Brunch"),
                            ChipOption("LUNCH", "Lunch"),
                            ChipOption("DINNER", "Dinner"),
                            ChipOption("LATE_NIGHT", "Late night"),
                            ChipOption("BAKERY", "Bakery"),
                            ChipOption("FOOD_MARKET", "Food market")
                        ),
                        getSelectedIds = { it.foodMealMoments },
                        toggleId = { prefs, key ->
                            val next = prefs.foodMealMoments.toMutableSet()
                            if (next.contains(key)) next.remove(key) else next.add(key)
                            prefs.copy(foodMealMoments = next.toList().sorted())
                        }
                    ),
                    MultiSelectSpec(
                        label = "Cuisine & diet (tags)",
                        options = listOf(
                            ChipOption("VEGETARIAN", "Vegetarian"),
                            ChipOption("VEGAN", "Vegan"),
                            ChipOption("LOCAL", "Local / regional"),
                            ChipOption("INTERNATIONAL", "International"),
                            ChipOption("EXPERIMENTAL", "Experimental"),
                            ChipOption("SEAFOOD", "Seafood"),
                            ChipOption("COMFORT", "Comfort")
                        ),
                        getSelectedIds = { it.foodCuisines },
                        toggleId = { prefs, key ->
                            val next = prefs.foodCuisines.toMutableSet()
                            if (next.contains(key)) next.remove(key) else next.add(key)
                            prefs.copy(foodCuisines = next.toList().sorted())
                        }
                    )
                ),
                sliders = listOf(
                    SliderSpec(
                        label = "Budget",
                        left = "Cheap",
                        right = "Splurge",
                        getValue = { it.budgetLevel },
                        setValue = { prefs, v -> prefs.copy(budgetLevel = v) }
                    )
                ),
                extraNotePlaceholder = "e.g., Soho, vegetarian, quiet vibe, no reservations…"
            )

            CategoryDto.DRINKS -> CategoryPrefsUiSpec(
                preset = PresetSpec(
                    label = "Focus (what)",
                    options = listOf(
                        ChipOption("drinks_coffee", "Coffee"),
                        ChipOption("drinks_cocktails", "Cocktails"),
                        ChipOption("drinks_beer", "Beer"),
                        ChipOption("drinks_wine", "Wine"),
                        ChipOption("drinks_organic_wine", "Organic wine"),
                        ChipOption("drinks_tea", "Tea")
                    ),
                    getSelectedId = { it.presetId },
                    setSelectedId = { prefs, id -> prefs.copy(presetId = id) }
                ),
                sliders = listOf(
                    SliderSpec(
                        label = "Budget",
                        left = "Cheap",
                        right = "Fancy",
                        getValue = { it.budgetLevel },
                        setValue = { prefs, v -> prefs.copy(budgetLevel = v) }
                    )
                ),
                extraNotePlaceholder = "e.g., natural wine, cozy bar, Soho, no reservations…"
            )

            CategoryDto.SIGHTSEEING -> CategoryPrefsUiSpec(
                preset = PresetSpec(
                    label = "Focus (what)",
                    options = listOf(
                        ChipOption("sight_must_sees", "Must-sees"),
                        ChipOption("sight_local_gems", "Local gems"),
                        ChipOption("sight_views", "Views"),
                        ChipOption("sight_architecture", "Architecture"),
                        ChipOption("sight_history", "History"),
                        ChipOption("sight_walk", "Walks"),
                        ChipOption("sight_day_trip", "Day trip")
                    ),
                    getSelectedId = { it.presetId },
                    setSelectedId = { prefs, id -> prefs.copy(presetId = id) }
                ),
                multiSelects = listOf(
                    MultiSelectSpec(
                        label = "Interests (tags)",
                        options = listOf(
                            ChipOption("LANDMARKS", "Landmarks"),
                            ChipOption("VIEWS", "Views"),
                            ChipOption("ARCHITECTURE", "Architecture"),
                            ChipOption("HISTORY", "History"),
                            ChipOption("NEIGHBORHOODS", "Neighborhoods"),
                            ChipOption("PARKS", "Parks"),
                            ChipOption("MARKETS", "Markets"),
                            ChipOption("PHOTO_SPOTS", "Photo spots"),
                            ChipOption("SCENIC_ROUTE", "Scenic route")
                        ),
                        getSelectedIds = { it.sightseeingInterests },
                        toggleId = { prefs, key ->
                            val next = prefs.sightseeingInterests.toMutableSet()
                            if (next.contains(key)) next.remove(key) else next.add(key)
                            prefs.copy(sightseeingInterests = next.toList().sorted())
                        }
                    )
                ),
                sliders = emptyList(),
                extraNotePlaceholder = "e.g., near Old Town, sunset viewpoint, minimal crowds…"
            )

            CategoryDto.SHOPPING -> CategoryPrefsUiSpec(
                preset = PresetSpec(
                    label = "Focus (what)",
                    options = listOf(
                        ChipOption("shop_vintage", "Vintage"),
                        ChipOption("shop_secondhand", "2nd hand"),
                        ChipOption("shop_books", "Books"),
                        ChipOption("shop_records", "Records (LPs)"),
                        ChipOption("shop_indie_design", "Indie design"),
                        ChipOption("shop_fashion", "Fashion"),
                        ChipOption("shop_sneakers", "Sneakers"),
                        ChipOption("shop_beauty", "Beauty"),
                        ChipOption("shop_home", "Home & decor"),
                        ChipOption("shop_markets", "Markets"),
                        ChipOption("shop_gifts", "Gifts"),
                        ChipOption("shop_outdoor", "Outdoor / gear")
                    ),
                    getSelectedId = { it.presetId },
                    setSelectedId = { prefs, id -> prefs.copy(presetId = id) }
                ),
                multiSelects = listOf(
                    MultiSelectSpec(
                        label = "Vibe (how)",
                        options = listOf(
                            ChipOption("VIBE_HIPSTER", "Hipster"),
                            ChipOption("VIBE_QUIRKY", "Quirky"),
                            ChipOption("VIBE_LOCAL_MAKERS", "Local makers"),
                            ChipOption("VIBE_SUSTAINABLE", "Sustainable"),
                            ChipOption("VIBE_CURATED", "Curated"),
                            ChipOption("VIBE_BUDGET", "Budget"),
                            ChipOption("VIBE_LUXURY", "Luxury"),
                            ChipOption("VIBE_MINIMAL", "Minimal"),
                            ChipOption("VIBE_COLORFUL", "Colorful"),
                            ChipOption("VIBE_FAST_BROWSE", "Fast browse"),
                            ChipOption("VIBE_SLOW_BROWSE", "Slow browse"),
                            ChipOption("VIBE_RAINY_DAY", "Rainy-day friendly")
                        ),
                        getSelectedIds = { it.shoppingInterests },
                        toggleId = { prefs, key ->
                            val next = prefs.shoppingInterests.toMutableSet()
                            if (next.contains(key)) next.remove(key) else next.add(key)
                            prefs.copy(shoppingInterests = next.toList().sorted())
                        }
                    )
                ),
                sliders = emptyList(),
                extraNotePlaceholder = "e.g., Soho, vintage denim, art books, independent designers…"
            )

            CategoryDto.NIGHTLIFE -> CategoryPrefsUiSpec(
                preset = PresetSpec(
                    label = "Focus (what)",
                    options = listOf(
                        ChipOption("night_club", "Club"),
                        ChipOption("night_djs", "DJ nights"),
                        ChipOption("night_live_music", "Live music"),
                        ChipOption("night_jazz", "Jazz"),
                        ChipOption("night_techno", "Techno / house"),
                        ChipOption("night_hiphop", "Hip-hop / R&B"),
                        ChipOption("night_cocktail_bars", "Cocktail bars"),
                        ChipOption("night_speakeasy", "Speakeasy"),
                        ChipOption("night_pubs", "Pubs"),
                        ChipOption("night_rooftop", "Rooftops"),
                        ChipOption("night_wine_bars", "Wine bars"),
                        ChipOption("night_comedy", "Comedy"),
                        ChipOption("night_late_eats", "Late-night food")
                    ),
                    getSelectedId = { it.presetId },
                    setSelectedId = { prefs, id -> prefs.copy(presetId = id) }
                ),
                multiSelects = listOf(
                    MultiSelectSpec(
                        label = "Vibe (how)",
                        options = listOf(
                            ChipOption("N_VIBE_UNDERGROUND", "Underground"),
                            ChipOption("N_VIBE_LOCAL", "Local"),
                            ChipOption("N_VIBE_UPSCALE", "Upscale"),
                            ChipOption("N_VIBE_DANCEY", "Dancey"),
                            ChipOption("N_VIBE_CHILL", "Chill"),
                            ChipOption("N_VIBE_LOUD", "Loud"),
                            ChipOption("N_VIBE_INTIMATE", "Intimate"),
                            ChipOption("N_VIBE_SOCIAL", "Social"),
                            ChipOption("N_VIBE_ROMANTIC", "Romantic"),
                            ChipOption("N_VIBE_QUEER_FRIENDLY", "Queer-friendly"),
                            ChipOption("N_VIBE_STYLISH", "Stylish"),
                            ChipOption("N_VIBE_LATE", "Open late"),
                            ChipOption("N_VIBE_CASUAL", "Casual"),
                            ChipOption("N_VIBE_VIEW", "Great views"),
                            ChipOption("N_VIBE_NO_RESERVATIONS", "No reservations"),
                            ChipOption("N_VIBE_DOOR_EASY", "Easy door"),
                            ChipOption("N_VIBE_DOOR_STRICT", "Strict door")
                        ),
                        getSelectedIds = { it.nightlifeInterests },
                        toggleId = { prefs, key ->
                            val next = prefs.nightlifeInterests.toMutableSet()
                            if (next.contains(key)) next.remove(key) else next.add(key)
                            prefs.copy(nightlifeInterests = next.toList().sorted())
                        }
                    )
                ),
                sliders = listOf(
                    SliderSpec(
                        label = "Budget",
                        left = "Cheap",
                        right = "Splurge",
                        getValue = { it.budgetLevel },
                        setValue = { prefs, v -> prefs.copy(budgetLevel = v) }
                    )
                ),
                extraNotePlaceholder = "e.g., Soho, queer-friendly, not too loud, open late…"
            )

            CategoryDto.MUSEUMS -> CategoryPrefsUiSpec(
                preset = PresetSpec(
                    label = "Focus (what)",
                    options = listOf(
                        ChipOption("museum_modern_art", "Modern art"),
                        ChipOption("museum_classic_art", "Classic art"),
                        ChipOption("museum_history", "History"),
                        ChipOption("museum_science", "Science"),
                        ChipOption("museum_design", "Design"),
                        ChipOption("museum_photography", "Photography"),
                        ChipOption("museum_fashion", "Fashion"),
                        ChipOption("museum_architecture", "Architecture"),
                        ChipOption("museum_interactive", "Interactive"),
                        ChipOption("museum_local_gems", "Local gems"),
                        ChipOption("museum_iconic", "Iconic")
                    ),
                    getSelectedId = { it.presetId },
                    setSelectedId = { prefs, id -> prefs.copy(presetId = id) }
                ),
                multiSelects = listOf(
                    MultiSelectSpec(
                        label = "Vibe (how)",
                        options = listOf(
                            ChipOption("M_VIBE_SMALL", "Small"),
                            ChipOption("M_VIBE_BIG_COLLECTION", "Big collection"),
                            ChipOption("M_VIBE_QUIET", "Quiet"),
                            ChipOption("M_VIBE_LESS_CROWDED", "Less crowded"),
                            ChipOption("M_VIBE_IMMERSIVE", "Immersive"),
                            ChipOption("M_VIBE_FAMILY", "Family-friendly"),
                            ChipOption("M_VIBE_RAINY_DAY", "Rainy-day"),
                            ChipOption("M_VIBE_ARCHITECTURE", "Great building"),
                            ChipOption("M_VIBE_FREE", "Free / low-cost"),
                            ChipOption("M_VIBE_QUICK_VISIT", "Quick visit"),
                            ChipOption("M_VIBE_DEEP_DIVE", "Deep dive"),
                            ChipOption("M_VIBE_PHOTO_FRIENDLY", "Photo-friendly")
                        ),
                        getSelectedIds = { it.museumInterests },
                        toggleId = { prefs, key ->
                            val next = prefs.museumInterests.toMutableSet()
                            if (next.contains(key)) next.remove(key) else next.add(key)
                            prefs.copy(museumInterests = next.toList().sorted())
                        }
                    )
                ),
                sliders = listOf(
                    SliderSpec(
                        label = "Budget",
                        left = "Cheap",
                        right = "Splurge",
                        getValue = { it.budgetLevel },
                        setValue = { prefs, v -> prefs.copy(budgetLevel = v) }
                    )
                ),
                extraNotePlaceholder = "e.g., small photography museum, less crowded, near center…"
            )

            CategoryDto.EXPERIENCE -> CategoryPrefsUiSpec(
                preset = PresetSpec(
                    label = "Focus (what)",
                    options = listOf(
                        ChipOption("exp_food", "Food experience"),
                        ChipOption("exp_cooking", "Cooking class"),
                        ChipOption("exp_drinks", "Wine / beer tasting"),
                        ChipOption("exp_tour", "Guided tour"),
                        ChipOption("exp_walk", "Walking tour"),
                        ChipOption("exp_boat", "Boat tour"),
                        ChipOption("exp_outdoor", "Outdoor adventure"),
                        ChipOption("exp_sport", "Sports / activity"),
                        ChipOption("exp_art", "Art / creative"),
                        ChipOption("exp_culture", "Local culture"),
                        ChipOption("exp_music", "Music experience"),
                        ChipOption("exp_unique", "Something unique")
                    ),
                    getSelectedId = { it.presetId },
                    setSelectedId = { prefs, id -> prefs.copy(presetId = id) }
                ),
                multiSelects = listOf(
                    MultiSelectSpec(
                        label = "Vibe (how)",
                        options = listOf(
                            ChipOption("EXP_SMALL_GROUP", "Small group"),
                            ChipOption("EXP_PRIVATE", "Private"),
                            ChipOption("EXP_LOCAL_HOST", "Local host"),
                            ChipOption("EXP_SOCIAL", "Social"),
                            ChipOption("EXP_RELAXED", "Relaxed"),
                            ChipOption("EXP_ACTIVE", "Active"),
                            ChipOption("EXP_OUTDOOR", "Outdoor"),
                            ChipOption("EXP_INDOOR", "Indoor"),
                            ChipOption("EXP_SCENIC", "Scenic"),
                            ChipOption("EXP_LEARNING", "Learning"),
                            ChipOption("EXP_HANDS_ON", "Hands-on"),
                            ChipOption("EXP_PHOTO", "Photo-friendly"),
                            ChipOption("EXP_ROMANTIC", "Romantic"),
                            ChipOption("EXP_FAMILY", "Family-friendly"),
                            ChipOption("EXP_LOCAL_GEMS", "Local gems")
                        ),
                        getSelectedIds = { it.experienceInterests },
                        toggleId = { prefs, key ->
                            val next = prefs.experienceInterests.toMutableSet()
                            if (next.contains(key)) next.remove(key) else next.add(key)
                            prefs.copy(experienceInterests = next.toList().sorted())
                        }
                    )
                ),
                sliders = listOf(
                    SliderSpec(
                        label = "Budget",
                        left = "Cheap",
                        right = "Premium",
                        getValue = { it.budgetLevel },
                        setValue = { prefs, v -> prefs.copy(budgetLevel = v) }
                    )
                ),
                extraNotePlaceholder = "e.g., small cooking class, sunset boat tour, local host, unique experience…"
            )

            else -> null
        }
    }
}

@Composable
internal fun CategoryPreferencesCard(
    category: CategoryDto,
    prefs: CategoryQuickPrefs,
    onReset: () -> Unit,
    onUpdate: ((CategoryQuickPrefs) -> CategoryQuickPrefs) -> Unit,
    modifier: Modifier = Modifier
) {
    val spec = CategoryPrefsUiSpecs.forCategory(category)

    Card(modifier = modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Settings", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onReset) { Text("Reset") }
            }

            if (spec == null) {
                ExtraNoteField(
                    value = prefs.extraText,
                    placeholder = "e.g., family-friendly, near center, minimal walking…",
                    onValueChange = { v -> onUpdate { it.copy(extraText = v) } }
                )
            } else {
                spec.preset?.let { preset ->
                    SingleSelectChipsRow(
                        label = preset.label,
                        selectedId = preset.getSelectedId(prefs),
                        options = preset.options,
                        onSelect = { id -> onUpdate { cur -> preset.setSelectedId(cur, id) } }
                    )
                }

                spec.multiSelects.forEach { row ->
                    MultiSelectChipsRow(
                        label = row.label,
                        selectedIds = row.getSelectedIds(prefs),
                        options = row.options,
                        onToggle = { id -> onUpdate { cur -> row.toggleId(cur, id) } }
                    )
                }

                spec.sliders.forEach { slider ->
                    SliderRow(
                        label = slider.label,
                        value = slider.getValue(prefs),
                        left = slider.left,
                        right = slider.right,
                        onChange = { v -> onUpdate { cur -> slider.setValue(cur, v) } }
                    )
                }

                ExtraNoteField(
                    value = prefs.extraText,
                    placeholder = spec.extraNotePlaceholder,
                    onValueChange = { v -> onUpdate { it.copy(extraText = v) } }
                )
            }
        }
    }
}

@Composable
private fun ExtraNoteField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Extra note") },
        placeholder = { Text(placeholder) },
        minLines = 2
    )
}

@Composable
private fun SingleSelectChipsRow(
    label: String,
    selectedId: String?,
    options: List<ChipOption>,
    onSelect: (String?) -> Unit
) {
    Text(label, style = MaterialTheme.typography.titleSmall)

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item("any") {
            FilterChip(
                selected = selectedId == null,
                onClick = { onSelect(null) },
                label = { Text("Any") }
            )
        }
        items(options, key = { it.id }) { opt ->
            FilterChip(
                selected = selectedId == opt.id,
                onClick = { onSelect(opt.id) },
                label = { Text(opt.label) }
            )
        }
    }
}

@Composable
private fun MultiSelectChipsRow(
    label: String,
    selectedIds: List<String>,
    options: List<ChipOption>,
    onToggle: (String) -> Unit
) {
    Text(label, style = MaterialTheme.typography.titleSmall)

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options, key = { it.id }) { opt ->
            FilterChip(
                selected = selectedIds.contains(opt.id),
                onClick = { onToggle(opt.id) },
                label = { Text(opt.label) }
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    left: String,
    right: String,
    onChange: (Float) -> Unit
) {
    androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = { onChange(it.coerceIn(0f, 1f)) }
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(left, style = MaterialTheme.typography.labelMedium)
            Text(right, style = MaterialTheme.typography.labelMedium)
        }
    }
}