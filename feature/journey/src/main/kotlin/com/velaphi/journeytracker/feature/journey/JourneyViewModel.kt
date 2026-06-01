package com.velaphi.journeytracker.feature.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velaphi.journeytracker.core.model.DisambiguationOption
import com.velaphi.journeytracker.core.model.DisambiguationTarget
import com.velaphi.journeytracker.core.model.JourneyLeg
import com.velaphi.journeytracker.core.model.JourneyPlanResult
import com.velaphi.journeytracker.core.model.JourneyRepository
import com.velaphi.journeytracker.core.model.Location
import com.velaphi.journeytracker.core.model.PlannedJourney
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MIN_SUGGESTION_QUERY_LENGTH = 2
private const val SUGGESTION_DEBOUNCE_MS = 300L
private const val MAX_SUGGESTIONS = 8

data class JourneyUiState(
    val originQuery: String = "",
    val destinationQuery: String = "",
    val resolvedOrigin: String? = null,
    val resolvedDestination: String? = null,
    val originSuggestions: List<Location> = emptyList(),
    val destinationSuggestions: List<Location> = emptyList(),
    val activeSearchField: LocationField? = null,
    val isSearchingSuggestions: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val locationDisambiguation: LocationDisambiguationState? = null,
    val journeyDisambiguation: JourneyDisambiguationState? = null,
    val journeys: List<PlannedJourney> = emptyList(),
)

data class LocationDisambiguationState(
    val target: LocationField,
    val options: List<Location>,
)

data class JourneyDisambiguationState(
    val target: DisambiguationTarget,
    val options: List<DisambiguationOption>,
)

enum class LocationField {
    ORIGIN,
    DESTINATION,
}

@HiltViewModel
class JourneyViewModel @Inject constructor(
    private val journeyRepository: JourneyRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JourneyUiState())
    val uiState: StateFlow<JourneyUiState> = _uiState.asStateFlow()

    private var originSuggestionsJob: Job? = null
    private var destinationSuggestionsJob: Job? = null

    fun onOriginChanged(value: String) {
        _uiState.update {
            it.copy(
                originQuery = value,
                resolvedOrigin = null,
                originSuggestions = emptyList(),
                activeSearchField = LocationField.ORIGIN,
                errorMessage = null,
                journeys = emptyList(),
            )
        }
        fetchSuggestions(query = value, field = LocationField.ORIGIN)
    }

    fun onDestinationChanged(value: String) {
        _uiState.update {
            it.copy(
                destinationQuery = value,
                resolvedDestination = null,
                destinationSuggestions = emptyList(),
                activeSearchField = LocationField.DESTINATION,
                errorMessage = null,
                journeys = emptyList(),
            )
        }
        fetchSuggestions(query = value, field = LocationField.DESTINATION)
    }

    fun dismissSuggestions() {
        _uiState.update {
            it.copy(
                originSuggestions = emptyList(),
                destinationSuggestions = emptyList(),
                activeSearchField = null,
                isSearchingSuggestions = false,
            )
        }
        originSuggestionsJob?.cancel()
        destinationSuggestionsJob?.cancel()
    }

    fun planJourney() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val resolvedOrigin = state.resolvedOrigin
            val resolvedDestination = state.resolvedDestination
            if (resolvedOrigin != null && resolvedDestination != null) {
                fetchJourney(resolvedOrigin, resolvedDestination)
                return@launch
            }

            val origin = resolvedOrigin ?: state.originQuery.trim()
            val destination = resolvedDestination ?: state.destinationQuery.trim()

            resolveLocationIfNeeded(
                query = origin,
                field = LocationField.ORIGIN,
                onResolved = { resolvedOrigin ->
                    resolveLocationIfNeeded(
                        query = destination,
                        field = LocationField.DESTINATION,
                        onResolved = { resolvedDestination ->
                            fetchJourney(resolvedOrigin, resolvedDestination)
                        },
                    )
                },
            )
        }
    }

    fun onLocationSelected(field: LocationField, location: Location) {
        dismissSuggestions()
        _uiState.update {
            when (field) {
                LocationField.ORIGIN -> it.copy(
                    originQuery = location.name,
                    resolvedOrigin = location.id,
                    locationDisambiguation = null,
                )

                LocationField.DESTINATION -> it.copy(
                    destinationQuery = location.name,
                    resolvedDestination = location.id,
                    locationDisambiguation = null,
                )
            }
        }

        val state = _uiState.value
        if (state.resolvedOrigin != null && state.resolvedDestination != null) {
            viewModelScope.launch {
                fetchJourney(state.resolvedOrigin, state.resolvedDestination)
            }
        } else if (field == LocationField.ORIGIN && state.destinationQuery.isNotBlank()) {
            viewModelScope.launch {
                resolveLocationIfNeeded(
                    query = state.destinationQuery.trim(),
                    field = LocationField.DESTINATION,
                    onResolved = { resolvedDestination ->
                        fetchJourney(state.resolvedOrigin ?: location.id, resolvedDestination)
                    },
                )
            }
        }
    }

    fun onJourneyDisambiguationSelected(option: DisambiguationOption) {
        val state = _uiState.value
        val origin = state.resolvedOrigin ?: state.originQuery.trim()
        val destination = state.resolvedDestination ?: state.destinationQuery.trim()

        val updatedOrigin = if (state.journeyDisambiguation?.target == DisambiguationTarget.ORIGIN) {
            option.parameterValue
        } else {
            origin
        }
        val updatedDestination = if (state.journeyDisambiguation?.target == DisambiguationTarget.DESTINATION) {
            option.parameterValue
        } else {
            destination
        }

        _uiState.update {
            it.copy(
                journeyDisambiguation = null,
                resolvedOrigin = updatedOrigin,
                resolvedDestination = updatedDestination,
                originQuery = if (state.journeyDisambiguation?.target == DisambiguationTarget.ORIGIN) {
                    option.displayName
                } else {
                    it.originQuery
                },
                destinationQuery = if (state.journeyDisambiguation?.target == DisambiguationTarget.DESTINATION) {
                    option.displayName
                } else {
                    it.destinationQuery
                },
            )
        }

        viewModelScope.launch {
            fetchJourney(updatedOrigin, updatedDestination)
        }
    }

    fun dismissLocationDisambiguation() {
        _uiState.update { it.copy(locationDisambiguation = null, isLoading = false) }
    }

    fun dismissJourneyDisambiguation() {
        _uiState.update { it.copy(journeyDisambiguation = null, isLoading = false) }
    }

    private fun fetchSuggestions(query: String, field: LocationField) {
        when (field) {
            LocationField.ORIGIN -> originSuggestionsJob?.cancel()
            LocationField.DESTINATION -> destinationSuggestionsJob?.cancel()
        }

        val trimmed = query.trim()
        if (trimmed.length < MIN_SUGGESTION_QUERY_LENGTH) {
            _uiState.update {
                when (field) {
                    LocationField.ORIGIN -> it.copy(originSuggestions = emptyList(), isSearchingSuggestions = false)
                    LocationField.DESTINATION -> it.copy(destinationSuggestions = emptyList(), isSearchingSuggestions = false)
                }
            }
            return
        }

        val job = viewModelScope.launch {
            delay(SUGGESTION_DEBOUNCE_MS)
            _uiState.update { it.copy(isSearchingSuggestions = true) }

            journeyRepository.searchLocations(trimmed)
                .onSuccess { locations ->
                    val suggestions = locations
                        .distinctBy { it.id }
                        .take(MAX_SUGGESTIONS)
                    _uiState.update { state ->
                        when (field) {
                            LocationField.ORIGIN -> state.copy(
                                originSuggestions = suggestions,
                                isSearchingSuggestions = false,
                            )

                            LocationField.DESTINATION -> state.copy(
                                destinationSuggestions = suggestions,
                                isSearchingSuggestions = false,
                            )
                        }
                    }
                }
                .onFailure {
                    _uiState.update { state ->
                        when (field) {
                            LocationField.ORIGIN -> state.copy(originSuggestions = emptyList())
                            LocationField.DESTINATION -> state.copy(destinationSuggestions = emptyList())
                        }.copy(isSearchingSuggestions = false)
                    }
                }
        }

        when (field) {
            LocationField.ORIGIN -> originSuggestionsJob = job
            LocationField.DESTINATION -> destinationSuggestionsJob = job
        }
    }

    private suspend fun resolveLocationIfNeeded(
        query: String,
        field: LocationField,
        onResolved: suspend (String) -> Unit,
    ) {
        journeyRepository.searchLocations(query)
            .onSuccess { locations ->
                when {
                    locations.isEmpty() -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "No locations found for \"$query\"",
                            )
                        }
                    }

                    locations.size == 1 -> {
                        val location = locations.first()
                        _uiState.update {
                            when (field) {
                                LocationField.ORIGIN -> it.copy(
                                    resolvedOrigin = location.id,
                                    originQuery = location.name,
                                )

                                LocationField.DESTINATION -> it.copy(
                                    resolvedDestination = location.id,
                                    destinationQuery = location.name,
                                )
                            }
                        }
                        onResolved(location.id)
                    }

                    else -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                locationDisambiguation = LocationDisambiguationState(
                                    target = field,
                                    options = locations,
                                ),
                            )
                        }
                    }
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "Location search failed")
                }
            }
    }

    private suspend fun fetchJourney(from: String, to: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, journeys = emptyList()) }

        journeyRepository.planJourney(from = from, to = to)
            .onSuccess { result ->
                when (result) {
                    is JourneyPlanResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                journeys = result.journeys,
                                errorMessage = if (result.journeys.isEmpty()) {
                                    "No bus journeys found between these locations"
                                } else {
                                    null
                                },
                            )
                        }
                    }

                    is JourneyPlanResult.DisambiguationRequired -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                journeyDisambiguation = JourneyDisambiguationState(
                                    target = result.target,
                                    options = result.options,
                                ),
                            )
                        }
                    }
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "Journey planning failed")
                }
            }
    }
}
