package com.example.travelbuddy.nav

object AppRoutes {
    // App-level
    const val TRIP_HOME = "trip_home"
    const val CREATE_TRIP = "create_trip"

    const val TRIP_SHELL = "trip/{tripId}"
    fun tripShell(tripId: String): String = "trip/$tripId"

    // Trip tabs (inside TripShell)
    const val TAB_SUGGESTIONS = "tab_suggestions"
    const val TAB_PINNED = "tab_pinned"
    const val TAB_SCHEDULE = "tab_schedule"
}
