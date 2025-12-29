package org.incammino.hospiceinventory.ui.screens.voice

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.incammino.hospiceinventory.data.repository.MaintainerRepository
import org.incammino.hospiceinventory.domain.model.Maintainer
import org.incammino.hospiceinventory.service.voice.GeminiService
import org.incammino.hospiceinventory.service.voice.SaveState
import org.incammino.hospiceinventory.service.voice.VoiceService
import org.incammino.hospiceinventory.service.voice.VoiceState
import org.incammino.hospiceinventory.ui.components.voice.VoiceContinueState
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel per MaintainerConfirmScreen.
 * Gestisce il salvataggio del manutentore.
 *
 * Paradigma "Voice Dump + Visual Confirm" (Fase 3 - 28/12/2025)
 */
@HiltViewModel
class MaintainerConfirmViewModel @Inject constructor(
    private val maintainerRepository: MaintainerRepository,
    private val voiceService: VoiceService,
    private val geminiService: GeminiService
) : ViewModel() {

    companion object {
        private const val TAG = "MaintainerConfirmVM"
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
                val updates = geminiService.updateMaintainerFromVoice("", transcript)
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
     * Salva il manutentore nel database.
     */
    fun save(
        name: String,
        vatNumber: String,
        specialization: String,
        email: String,
        phone: String,
        contactPerson: String,
        street: String,
        city: String,
        postalCode: String,
        province: String,
        isSupplier: Boolean,
        notes: String
    ) {
        // Validazione
        if (name.isBlank()) {
            _saveState.value = SaveState.Error("Inserisci il nome dell'azienda")
            return
        }

        _saveState.value = SaveState.Saving

        viewModelScope.launch {
            try {
                val maintainer = Maintainer(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    email = email.takeIf { it.isNotBlank() },
                    phone = phone.takeIf { it.isNotBlank() },
                    address = street.takeIf { it.isNotBlank() },
                    city = city.takeIf { it.isNotBlank() },
                    postalCode = postalCode.takeIf { it.isNotBlank() },
                    province = province.takeIf { it.isNotBlank() },
                    vatNumber = vatNumber.takeIf { it.isNotBlank() },
                    contactPerson = contactPerson.takeIf { it.isNotBlank() },
                    specialization = specialization.takeIf { it.isNotBlank() },
                    isSupplier = isSupplier,
                    notes = notes.takeIf { it.isNotBlank() },
                    isActive = true
                )

                Log.d(TAG, "Saving maintainer: $maintainer")

                maintainerRepository.insert(maintainer)

                Log.d(TAG, "Maintainer saved successfully")
                _saveState.value = SaveState.Success

            } catch (e: Exception) {
                Log.e(TAG, "Failed to save maintainer", e)
                _saveState.value = SaveState.Error(
                    e.message ?: "Errore durante il salvataggio"
                )
            }
        }
    }

    /**
     * Reset dello stato.
     */
    fun reset() {
        _saveState.value = SaveState.Idle
    }
}
