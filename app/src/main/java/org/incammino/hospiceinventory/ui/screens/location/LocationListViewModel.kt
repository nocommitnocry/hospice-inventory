package org.incammino.hospiceinventory.ui.screens.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.incammino.hospiceinventory.data.repository.LocationRepository
import org.incammino.hospiceinventory.domain.model.Location
import javax.inject.Inject

/**
 * UI State per LocationListScreen.
 */
data class LocationListUiState(
    val locations: List<Location> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel per la lista ubicazioni.
 */
@HiltViewModel
class LocationListViewModel @Inject constructor(
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationListUiState())
    val uiState: StateFlow<LocationListUiState> = _uiState.asStateFlow()

    init {
        loadLocations()
    }

    /**
     * Carica tutte le ubicazioni attive.
     */
    private fun loadLocations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            locationRepository.getAllActive().collect { locations ->
                _uiState.update {
                    it.copy(
                        locations = locations,
                        isLoading = false,
                        error = null
                    )
                }
            }
        }
    }

    /**
     * Elimina un'ubicazione (soft delete).
     */
    fun deleteLocation(id: String) {
        viewModelScope.launch {
            try {
                locationRepository.softDelete(id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Errore durante l'eliminazione: ${e.message}")
                }
            }
        }
    }

    /**
     * Pulisce l'errore.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
