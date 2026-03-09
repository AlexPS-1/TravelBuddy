package com.example.travelbuddy.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.travelbuddy.data.AiRepository
import com.example.travelbuddy.data.prefs.PinnedStore
import com.example.travelbuddy.data.session.TripSessionStore
import com.example.travelbuddy.data.trips.TripStore
import com.example.travelbuddy.data.trips.TripSummary
import com.example.travelbuddy.model.PreferenceDraftStore
import com.example.travelbuddy.model.TripDraftStore
import com.example.travelbuddy.ui.create.CreateTripScreen
import com.example.travelbuddy.ui.home.TripHomeScreen
import com.example.travelbuddy.ui.trip.TripShell
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun TravelBuddyNavGraph(
    navController: NavHostController,
    repo: AiRepository,
    pinnedStore: PinnedStore,
    tripSessionStore: TripSessionStore,
    tripStore: TripStore,
    tripDraftStore: TripDraftStore,
    preferenceDraftStore: PreferenceDraftStore
) {
    val scope = rememberCoroutineScope()

    NavHost(
        navController = navController,
        startDestination = AppRoutes.TRIP_HOME
    ) {
        composable(AppRoutes.TRIP_HOME) {
            TripHomeScreen(
                tripStore = tripStore,
                onCreateTrip = { navController.navigate(AppRoutes.CREATE_TRIP) },
                onOpenTrip = { tripId -> navController.navigate(AppRoutes.tripShell(tripId)) }
            )
        }

        composable(AppRoutes.CREATE_TRIP) {
            CreateTripScreen(
                tripDraftStore = tripDraftStore,
                onBack = { navController.popBackStack() },
                onStartTrip = {
                    val draft = tripDraftStore.draft.value
                    val tripId = UUID.randomUUID().toString()

                    // Seed in-memory trip session so Suggestions header + generation use correct city immediately
                    tripSessionStore.getOrCreate(tripId).setCity(draft.city)

                    // Persist trip summary (DataStore)
                    val summary = TripSummary(
                        tripId = tripId,
                        title = draft.title.ifBlank { draft.city },
                        city = draft.city,
                        startDateIso = draft.startDateIso,
                        endDateIso = draft.endDateIso,
                        createdAtEpochMs = System.currentTimeMillis()
                    )
                    scope.launch {
                        tripStore.upsertTrip(summary)
                    }

                    navController.navigate(AppRoutes.tripShell(tripId)) {
                        popUpTo(AppRoutes.TRIP_HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = AppRoutes.TRIP_SHELL,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
        ) { entry ->
            val tripId = entry.arguments?.getString("tripId") ?: "unknown"

            TripShell(
                tripId = tripId,
                repo = repo,
                pinnedStore = pinnedStore,
                tripSessionStore = tripSessionStore,
                tripStore = tripStore,
                onExitTrip = { navController.popBackStack() }
            )
        }
    }
}
