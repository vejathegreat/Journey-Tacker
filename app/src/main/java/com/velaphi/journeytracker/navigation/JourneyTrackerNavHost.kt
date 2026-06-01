package com.velaphi.journeytracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.velaphi.journeytracker.feature.journey.navigation.JourneyRoute
import com.velaphi.journeytracker.feature.journey.navigation.journeyScreen
import com.velaphi.journeytracker.feature.tracking.navigation.TrackingRoute
import com.velaphi.journeytracker.feature.tracking.navigation.trackingScreen

@Composable
fun JourneyTrackerNavHost(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = JourneyRoute.ROUTE,
        modifier = modifier,
    ) {
        journeyScreen(
            onBusLegSelected = { lineId ->
                navController.navigate(TrackingRoute.create(lineId))
            },
        )
        trackingScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
