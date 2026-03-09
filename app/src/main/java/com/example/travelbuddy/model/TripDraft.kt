package com.example.travelbuddy.model

data class TripDraft(
    val title: String = "My Trip",
    val city: String = "",
    val startDateIso: String = "",
    val endDateIso: String = "",
    val isMultiCity: Boolean = false
)
