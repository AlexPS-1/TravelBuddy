// File: com/example/travelbuddy/MainActivity.kt
package com.example.travelbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.navigation.compose.rememberNavController
import com.example.travelbuddy.data.AiRepository
import com.example.travelbuddy.data.prefs.PinnedStore
import com.example.travelbuddy.data.session.TripSessionStore
import com.example.travelbuddy.data.trips.TripStore
import com.example.travelbuddy.model.PreferenceDraftStore
import com.example.travelbuddy.model.TripDraftStore
import com.example.travelbuddy.nav.TravelBuddyNavGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repo = AiRepository()
        val pinnedStore = PinnedStore(applicationContext)
        val tripSessionStore = TripSessionStore()
        val tripStore = TripStore(applicationContext)

        val tripDraftStore = TripDraftStore()
        val preferenceDraftStore = PreferenceDraftStore()

        setContent {
            MaterialTheme {
                Surface {
                    val navController = rememberNavController()
                    TravelBuddyNavGraph(
                        navController = navController,
                        repo = repo,
                        pinnedStore = pinnedStore,
                        tripSessionStore = tripSessionStore,
                        tripStore = tripStore,
                        tripDraftStore = tripDraftStore,
                        preferenceDraftStore = preferenceDraftStore
                    )
                }
            }
        }
    }
}