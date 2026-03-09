// File: com/example/travelbuddy/ui/trip/TripShell.kt
package com.example.travelbuddy.ui.trip

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.travelbuddy.data.AiRepository
import com.example.travelbuddy.data.prefs.PinnedStore
import com.example.travelbuddy.data.session.TripSessionStore
import com.example.travelbuddy.data.trips.TripStore
import com.example.travelbuddy.nav.AppRoutes
import com.example.travelbuddy.ui.pinned.PinnedScreen
import com.example.travelbuddy.ui.plan.PlanScreen
import com.example.travelbuddy.ui.plan.PlanViewModel
import com.example.travelbuddy.ui.suggestions.SuggestionsScreen
import com.example.travelbuddy.ui.suggestions.SuggestionsViewModel
import com.google.gson.Gson
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map

@OptIn(FlowPreview::class)
@Composable
fun TripShell(
    tripId: String,
    repo: AiRepository,
    pinnedStore: PinnedStore,
    tripSessionStore: TripSessionStore,
    tripStore: TripStore,
    onExitTrip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabNavController = rememberNavController()
    val session = remember(tripId) { tripSessionStore.getOrCreate(tripId) }

    val planViewModel = remember(tripId) { PlanViewModel(session) }

    val suggestionsViewModel = remember(tripId) {
        SuggestionsViewModel(
            tripId = tripId,
            repo = repo,
            pinnedStore = pinnedStore,
            tripSessionStore = tripSessionStore
        )
    }

    val gson = remember { Gson() }

    LaunchedEffect(tripId) {
        val snap = tripStore.loadSession(tripId)
        if (snap != null) session.applySnapshot(snap)
    }

    LaunchedEffect(tripId) {
        combine(
            session.city,
            session.globalTips,
            session.suggestionsByCategory,
            session.quickPrefsByCategory,
            session.planBlocks // Added for schedule auto-save
        ) { _, _, _, _, _ ->
            session.toSnapshot()
        }
            .map { snapshot -> gson.toJson(snapshot) to snapshot }
            .debounce(500)
            .distinctUntilChangedBy { (fingerprint, _) -> fingerprint }
            .collect { (_, snapshot) ->
                tripStore.saveSession(tripId, snapshot)
            }
    }

    BackHandler {
        val canPop = tabNavController.popBackStack()
        if (!canPop) onExitTrip()
    }

    Scaffold(
        modifier = modifier,
        bottomBar = { TripBottomNavBar(navController = tabNavController) }
    ) { innerPadding ->
        NavHost(
            navController = tabNavController,
            startDestination = AppRoutes.TAB_SUGGESTIONS,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppRoutes.TAB_SUGGESTIONS) {
                SuggestionsScreen(viewModel = suggestionsViewModel)
            }
            composable(AppRoutes.TAB_PINNED) {
                PinnedScreen(viewModel = suggestionsViewModel)
            }
            composable(AppRoutes.TAB_SCHEDULE) {
                PlanScreen(
                    planViewModel = planViewModel,
                    suggestionsViewModel = suggestionsViewModel
                )
            }
        }
    }
}

@Composable
private fun TripBottomNavBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == AppRoutes.TAB_SUGGESTIONS,
            onClick = { navController.navigateSingleTop(AppRoutes.TAB_SUGGESTIONS) },
            label = { Text("Suggestions") },
            icon = { /* icons later */ }
        )
        NavigationBarItem(
            selected = currentRoute == AppRoutes.TAB_PINNED,
            onClick = { navController.navigateSingleTop(AppRoutes.TAB_PINNED) },
            label = { Text("Pinned") },
            icon = { /* icons later */ }
        )
        NavigationBarItem(
            selected = currentRoute == AppRoutes.TAB_SCHEDULE,
            onClick = { navController.navigateSingleTop(AppRoutes.TAB_SCHEDULE) },
            label = { Text("Schedule") },
            icon = { /* icons later */ }
        )
    }
}

private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.startDestinationId) { saveState = true }
    }
}