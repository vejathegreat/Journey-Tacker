package com.velaphi.journeytracker.feature.journey.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.velaphi.journeytracker.feature.journey.JourneyScreen

object JourneyRoute {
    const val ROUTE = "journey"
}

fun NavGraphBuilder.journeyScreen(
    onBusLegSelected: (lineId: String) -> Unit,
) {
    composable(route = JourneyRoute.ROUTE) {
        JourneyScreen(onBusLegSelected = onBusLegSelected)
    }
}
