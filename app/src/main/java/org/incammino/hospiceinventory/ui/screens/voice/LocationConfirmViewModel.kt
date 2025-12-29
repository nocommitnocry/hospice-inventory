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
import org.incammino.hospiceinventory.service.voice.GeminiService
import org.incammino.hospiceinventory.service.voice.SaveState
import org.incammino.hospiceinventory.service.voice.VoiceService
import org.incammino.hospiceinventory.service.voice.VoiceState
import org.incammino.hospiceinventory.ui.components.voice.VoiceContinueState
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
    private val locationRepository: LocationRepository,
    private val voiceService: VoiceService,
    private val geminiService: GeminiService
) : ViewModel() {

    companion object {
        private const val TAG = "LocationConfirmVM"
    }

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    // Voice Continue State
    private val _voiceContinueState = MutableStateFlow(VoiceContinueState.Idle)
    val voiceContinueState: StateFlow<VoiceContinueState> = _voiceContinueState.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    var onVoiceUpdate: ((Map<String, String>) -> Unit)? = null

    init {
        observeVoiceState()
    }

    private fun observeVoiceState() {
        viewModelScope.launch {
            voiceService.state.collect { state ->
                when (state) {
                    is VoiceState.Idle -> {
                        _voiceContinueState.value = VoiceContinueState.Idle
                        _partialTranscript.value = ""
                    }
                    is VoiceState.Listening -> _voiceContinueState.value = VoiceContinueState.Listening
                    is VoiceState.Processing -> _voiceContinueState.value = VoiceContinueState.Processing
                    is VoiceState.PartialResult -> _partialTranscript.value = state.text
                    is VoiceState.Result -> processAdditionalVoiceInput(state.text)
                    is VoiceState.Error -> _voiceContinueState.value = VoiceContinueState.Idle
                    is VoiceState.Unavailable -> _voiceContinueState.value = VoiceContinueState.Idle
                }
            }
        }
    }

    fun toggleVoiceInput() {
        when (_voiceContinueState.value) {
            VoiceContinueState.Idle -> {
                voiceService.initialize()
                voiceService.startListening()
            }
            VoiceContinueState.Listening -> voiceService.stopListening()
            VoiceContinueState.Processing -> { }
        }
    }

    private fun processAdditionalVoiceInput(transcript: String) {
        viewModelScope.launch {
            _voiceContinueState.value = VoiceContinueState.Processing
            try {
                val updates = geminiService.updateLocationFromVoice("", transcript)
                if (updates.isNotEmpty()) {
                    onVoiceUpdate?.invoke(updates)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing voice input", e)
            } finally {
                _voiceContinueState.value = VoiceContinueState.Idle
            }
        }
    }

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
