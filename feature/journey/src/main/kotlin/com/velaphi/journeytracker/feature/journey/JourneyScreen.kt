package com.velaphi.journeytracker.feature.journey

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velaphi.journeytracker.core.model.DisambiguationOption
import com.velaphi.journeytracker.core.model.DisambiguationTarget
import com.velaphi.journeytracker.core.model.JourneyLeg
import com.velaphi.journeytracker.core.model.Location
import com.velaphi.journeytracker.core.model.PlannedJourney

@Composable
fun JourneyScreen(
    onBusLegSelected: (lineId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JourneyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Plan your journey",
            style = MaterialTheme.typography.headlineMedium,
        )

        LocationSearchField(
            value = uiState.originQuery,
            onValueChange = viewModel::onOriginChanged,
            label = "From",
            placeholder = "e.g. Victoria",
            suggestions = uiState.originSuggestions,
            showSuggestions = uiState.activeSearchField == LocationField.ORIGIN &&
                (uiState.originSuggestions.isNotEmpty() || uiState.isSearchingSuggestions),
            isLoadingSuggestions = uiState.isSearchingSuggestions &&
                uiState.activeSearchField == LocationField.ORIGIN,
            onSuggestionSelected = { location ->
                viewModel.onLocationSelected(LocationField.ORIGIN, location)
            },
            modifier = Modifier.fillMaxWidth(),
        )

        LocationSearchField(
            value = uiState.destinationQuery,
            onValueChange = viewModel::onDestinationChanged,
            label = "To",
            placeholder = "e.g. Euston",
            suggestions = uiState.destinationSuggestions,
            showSuggestions = uiState.activeSearchField == LocationField.DESTINATION &&
                (uiState.destinationSuggestions.isNotEmpty() || uiState.isSearchingSuggestions),
            isLoadingSuggestions = uiState.isSearchingSuggestions &&
                uiState.activeSearchField == LocationField.DESTINATION,
            onSuggestionSelected = { location ->
                viewModel.onLocationSelected(LocationField.DESTINATION, location)
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = viewModel::planJourney,
            enabled = uiState.originQuery.isNotBlank() &&
                uiState.destinationQuery.isNotBlank() &&
                !uiState.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Find journeys")
        }

        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(uiState.journeys) { journey ->
                JourneyCard(
                    journey = journey,
                    onBusLegSelected = onBusLegSelected,
                )
            }
        }
    }

    uiState.locationDisambiguation?.let { disambiguation ->
        LocationDisambiguationDialog(
            title = if (disambiguation.target == LocationField.ORIGIN) "Select origin" else "Select destination",
            options = disambiguation.options,
            onDismiss = viewModel::dismissLocationDisambiguation,
            onSelected = { location ->
                viewModel.onLocationSelected(disambiguation.target, location)
            },
        )
    }

    uiState.journeyDisambiguation?.let { disambiguation ->
        JourneyDisambiguationDialog(
            title = if (disambiguation.target == DisambiguationTarget.ORIGIN) {
                "Which origin did you mean?"
            } else {
                "Which destination did you mean?"
            },
            options = disambiguation.options,
            onDismiss = viewModel::dismissJourneyDisambiguation,
            onSelected = viewModel::onJourneyDisambiguationSelected,
        )
    }
}

@Composable
private fun LocationSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    suggestions: List<Location>,
    showSuggestions: Boolean,
    isLoadingSuggestions: Boolean,
    onSuggestionSelected: (Location) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (showSuggestions) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                if (isLoadingSuggestions && suggestions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp),
                    ) {
                        items(suggestions) { location ->
                            Text(
                                text = location.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSuggestionSelected(location) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyCard(
    journey: PlannedJourney,
    onBusLegSelected: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${journey.durationMinutes} min journey",
                style = MaterialTheme.typography.titleMedium,
            )

            journey.legs.forEach { leg ->
                BusLegRow(
                    leg = leg,
                    onClick = { onBusLegSelected(leg.lineId) },
                )
            }
        }
    }
}

@Composable
private fun BusLegRow(
    leg: JourneyLeg,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Bus ${leg.lineName}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = leg.instruction.ifBlank { "Towards ${leg.destination}" },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${leg.durationMinutes} min • Tap to track live",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun LocationDisambiguationDialog(
    title: String,
    options: List<Location>,
    onDismiss: () -> Unit,
    onSelected: (Location) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options) { location ->
                    Text(
                        text = location.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(location) }
                            .padding(vertical = 8.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun JourneyDisambiguationDialog(
    title: String,
    options: List<DisambiguationOption>,
    onDismiss: () -> Unit,
    onSelected: (DisambiguationOption) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options) { option ->
                    Text(
                        text = option.displayName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(option) }
                            .padding(vertical = 8.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
