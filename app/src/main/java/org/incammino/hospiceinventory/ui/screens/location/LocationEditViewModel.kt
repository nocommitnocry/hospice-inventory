package org.incammino.hospiceinventory.ui.screens.location

import androidx.lifecycle.SavedStateHandle
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
import java.util.UUID
import javax.inject.Inject

/**
 * UI State per LocationEditScreen.
 */
data class LocationEditUiState(
    // Dati form
    val name: String = "",
    val parentId: String? = null,
    val parentName: String? = null,
    val address: String = "",
    val notes: String = "",

    // Liste per dropdown
    val availableParents: List<Location> = emptyList(),

    // Metadati
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedLocationId: String? = null
)

/**
 * ViewModel per la creazione/modifica ubicazione.
 */
@HiltViewModel
class LocationEditViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val locationId: String? = savedStateHandle.get<String>("locationId")
        ?.takeIf { it != "new" }

    private val _uiState = MutableStateFlow(LocationEditUiState(isNew = locationId == null))
    val uiState: StateFlow<LocationEditUiState> = _uiState.asStateFlow()

    init {
        loadParentLocations()
        if (locationId != null) {
            loadLocation(locationId)
        }
    }

    /**
     * Carica le ubicazioni disponibili come genitori.
     */
    private fun loadParentLocations() {
        viewModelScope.launch {
            locationRepository.getAllActive().collect { locations ->
                // Filtra l'ubicazione corrente per evitare cicli
                val filteredLocations = locations.filter { it.id != locationId }
                _uiState.update { it.copy(availableParents = filteredLocations) }
            }
        }
    }

    /**
     * Carica l'ubicazione esistente per la modifica.
     */
    private fun loadLocation(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            locationRepository.getByIdFlow(id).collect { location ->
                if (location != null) {
                    // Carica anche il nome del genitore se presente
                    val parentName = location.parentId?.let { parentId ->
                        locationRepository.getById(parentId)?.name
                    }

                    _uiState.update {
                        it.copy(
                            name = location.name,
                            parentId = location.parentId,
                            parentName = parentName,
                            address = location.address ?: "",
                            notes = location.notes ?: "",
                            isNew = false,
                            isLoading = false,
                            error = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Ubicazione non trovata"
                        )
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AGGIORNAMENTO CAMPI
    // ═══════════════════════════════════════════════════════════════════════════

    fun updateName(value: String) {
        _uiState.update { it.copy(name = value) }
    }

    fun updateParent(parentId: String?, parentName: String?) {
        _uiState.update { it.copy(parentId = parentId, parentName = parentName) }
    }

    fun updateAddress(value: String) {
        _uiState.update { it.copy(address = value) }
    }

    fun updateNotes(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VALIDAZIONE E SALVATAGGIO
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Valida il form.
     */
    private fun validate(): Boolean {
        val state = _uiState.value

        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "Il nome ubicazione è obbligatorio") }
            return false
        }

        return true
    }

    /**
     * Salva l'ubicazione.
     */
    fun save() {
        if (!validate()) return

        val state = _uiState.value

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            try {
                val location = Location(
                    id = locationId ?: UUID.randomUUID().toString(),
                    name = state.name.trim(),
                    parentId = state.parentId,
                    address = state.address.takeIf { it.isNotBlank() },
                    coordinates = null,
                    notes = state.notes.takeIf { it.isNotBlank() },
                    isActive = true
                )

                val id = if (state.isNew) {
                    locationRepository.insert(location)
                } else {
                    locationRepository.update(location)
                    location.id
                }

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        savedLocationId = id
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = "Errore durante il salvataggio: ${e.message}"
                    )
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
