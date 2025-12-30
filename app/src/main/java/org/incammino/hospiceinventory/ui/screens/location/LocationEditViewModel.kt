package org.incammino.hospiceinventory.ui.screens.location

import android.util.Log
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
import org.incammino.hospiceinventory.domain.model.LocationDefaults
import org.incammino.hospiceinventory.domain.model.LocationType
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

    // Nuovi campi gerarchia/caratteristiche
    val type: LocationType? = null,
    val building: String = "",
    val floor: String = "",
    val floorName: String = "",
    val department: String = "",
    val hasOxygenOutlet: Boolean = false,
    val bedCount: Int? = null,

    // Liste per dropdown/autocomplete
    val availableParents: List<Location> = emptyList(),
    val buildingSuggestions: List<String> = emptyList(),
    val floorSuggestions: List<String> = emptyList(),
    val floorNameSuggestions: List<String> = emptyList(),
    val departmentSuggestions: List<String> = emptyList(),

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

    companion object {
        private const val TAG = "LocationEditVM"
    }

    private val locationId: String? = savedStateHandle.get<String>("locationId")
        ?.takeIf { it != "new" }

    private val _uiState = MutableStateFlow(LocationEditUiState(isNew = locationId == null))
    val uiState: StateFlow<LocationEditUiState> = _uiState.asStateFlow()

    init {
        loadParentLocations()
        loadSuggestions()
        if (locationId != null) {
            loadLocation(locationId)
        }
    }

    /**
     * Carica i suggerimenti per autocomplete.
     */
    private fun loadSuggestions() {
        viewModelScope.launch {
            try {
                val suggestions = locationRepository.getAllSuggestions()
                _uiState.update { state ->
                    state.copy(
                        buildingSuggestions = (LocationDefaults.COMMON_BUILDINGS + suggestions.buildings).distinct(),
                        floorSuggestions = (LocationDefaults.COMMON_FLOORS + suggestions.floors).distinct(),
                        floorNameSuggestions = (LocationDefaults.COMMON_FLOOR_NAMES + suggestions.floorNames).distinct(),
                        departmentSuggestions = (LocationDefaults.COMMON_DEPARTMENTS + suggestions.departments).distinct()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load suggestions", e)
                // Fallback ai soli default
                _uiState.update { state ->
                    state.copy(
                        buildingSuggestions = LocationDefaults.COMMON_BUILDINGS,
                        floorSuggestions = LocationDefaults.COMMON_FLOORS,
                        floorNameSuggestions = LocationDefaults.COMMON_FLOOR_NAMES,
                        departmentSuggestions = LocationDefaults.COMMON_DEPARTMENTS
                    )
                }
            }
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
                            type = location.type,
                            parentId = location.parentId,
                            parentName = parentName,
                            building = location.building ?: "",
                            floor = location.floor ?: "",
                            floorName = location.floorName ?: "",
                            department = location.department ?: "",
                            hasOxygenOutlet = location.hasOxygenOutlet,
                            bedCount = location.bedCount,
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

    fun updateType(value: LocationType?) {
        _uiState.update { it.copy(type = value) }
    }

    fun updateBuilding(value: String) {
        _uiState.update { it.copy(building = value) }
    }

    fun updateFloor(value: String) {
        _uiState.update { it.copy(floor = value.uppercase()) }
    }

    fun updateFloorName(value: String) {
        _uiState.update { it.copy(floorName = value) }
    }

    fun updateDepartment(value: String) {
        _uiState.update { it.copy(department = value) }
    }

    fun updateHasOxygenOutlet(value: Boolean) {
        _uiState.update { it.copy(hasOxygenOutlet = value) }
    }

    fun updateBedCount(value: Int?) {
        _uiState.update { it.copy(bedCount = value) }
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
                    type = state.type,
                    parentId = state.parentId,
                    floor = state.floor.takeIf { it.isNotBlank() },
                    floorName = state.floorName.takeIf { it.isNotBlank() },
                    department = state.department.takeIf { it.isNotBlank() },
                    building = state.building.takeIf { it.isNotBlank() },
                    hasOxygenOutlet = state.hasOxygenOutlet,
                    bedCount = state.bedCount,
                    address = state.address.takeIf { it.isNotBlank() },
                    coordinates = null,
                    notes = state.notes.takeIf { it.isNotBlank() },
                    isActive = true,
                    needsCompletion = false
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
