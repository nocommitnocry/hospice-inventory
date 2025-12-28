package org.incammino.hospiceinventory.ui.screens.voice

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.incammino.hospiceinventory.data.repository.LocationRepository
import org.incammino.hospiceinventory.domain.model.Location
import org.incammino.hospiceinventory.domain.model.LocationType
import org.incammino.hospiceinventory.service.voice.SaveState
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel per LocationConfirmScreen.
 * Gestisce il salvataggio dell'ubicazione.
 *
 * Paradigma "Voice Dump + Visual Confirm" (Fase 3 - 28/12/2025)
 */
@HiltViewModel
class LocationConfirmViewModel @Inject constructor(
    private val locationRepository: LocationRepository
) : ViewModel() {

    companion object {
        private const val TAG = "LocationConfirmVM"
    }

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    /**
     * Salva l'ubicazione nel database.
     */
    fun save(
        name: String,
        type: String,
        buildingName: String,
        floorCode: String,
        floorName: String,
        department: String,
        hasOxygenOutlet: Boolean,
        bedCount: Int?,
        notes: String
    ) {
        // Validazione
        if (name.isBlank()) {
            _saveState.value = SaveState.Error("Inserisci il nome dell'ubicazione")
            return
        }

        _saveState.value = SaveState.Saving

        viewModelScope.launch {
            try {
                // Parsa il tipo ubicazione
                val locationType = parseLocationType(type)

                val location = Location(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    type = locationType,
                    parentId = null, // TODO: gestire gerarchia
                    floor = floorCode.takeIf { it.isNotBlank() },
                    floorName = floorName.takeIf { it.isNotBlank() },
                    department = department.takeIf { it.isNotBlank() },
                    building = buildingName.takeIf { it.isNotBlank() },
                    hasOxygenOutlet = hasOxygenOutlet,
                    bedCount = bedCount,
                    address = null,
                    coordinates = null,
                    notes = notes.takeIf { it.isNotBlank() },
                    isActive = true,
                    needsCompletion = false
                )

                Log.d(TAG, "Saving location: $location")

                locationRepository.insert(location)

                Log.d(TAG, "Location saved successfully")
                _saveState.value = SaveState.Success

            } catch (e: Exception) {
                Log.e(TAG, "Failed to save location", e)
                _saveState.value = SaveState.Error(
                    e.message ?: "Errore durante il salvataggio"
                )
            }
        }
    }

    /**
     * Parsa il tipo ubicazione dalla stringa.
     */
    private fun parseLocationType(typeStr: String): LocationType? {
        if (typeStr.isBlank()) return null

        return LocationType.entries.find {
            it.name.equals(typeStr, ignoreCase = true) ||
            it.label.equals(typeStr, ignoreCase = true)
        }
    }

    /**
     * Reset dello stato.
     */
    fun reset() {
        _saveState.value = SaveState.Idle
    }
}
