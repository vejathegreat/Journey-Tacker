package com.velaphi.journeytracker.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.velaphi.journeytracker.core.designsystem.theme.JourneyTrackerTheme
import com.velaphi.journeytracker.navigation.JourneyTrackerNavHost

@Composable
fun JourneyTrackerApp() {
    JourneyTrackerTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            JourneyTrackerNavHost(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}
