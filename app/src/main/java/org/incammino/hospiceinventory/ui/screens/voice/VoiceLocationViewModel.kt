package org.incammino.hospiceinventory.ui.screens.voice

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.incammino.hospiceinventory.service.voice.*
import javax.inject.Inject

/**
 * ViewModel per VoiceLocationScreen.
 * Gestisce l'input vocale e l'estrazione dati con Gemini.
 *
 * Paradigma "Voice Dump + Visual Confirm" (Fase 3 - 28/12/2025)
 */
@HiltViewModel
class VoiceLocationViewModel @Inject constructor(
    private val voiceService: VoiceService,
    private val geminiService: GeminiService
) : ViewModel() {

    companion object {
        private const val TAG = "VoiceLocationVM"
    }

    private val _state = MutableStateFlow<VoiceLocationState>(VoiceLocationState.Idle)
    val state: StateFlow<VoiceLocationState> = _state.asStateFlow()

    private var fullTranscript = ""

    init {
        observeVoiceState()
    }

    /**
     * Osserva lo stato del VoiceService e reagisce ai cambiamenti.
     */
    private fun observeVoiceState() {
        viewModelScope.launch {
            voiceService.state.collect { voiceState ->
                when (voiceState) {
                    is VoiceState.Idle -> {
                        // Se abbiamo un transcript e eravamo in ascolto, processa
                        if (fullTranscript.isNotBlank() &&
                            (_state.value is VoiceLocationState.Listening ||
                             _state.value is VoiceLocationState.Transcribing)) {
                            processTranscript()
                        }
                    }
                    is VoiceState.Listening -> {
                        _state.value = VoiceLocationState.Listening
                    }
                    is VoiceState.PartialResult -> {
                        _state.value = VoiceLocationState.Transcribing(voiceState.text)
                        fullTranscript = voiceState.text
                    }
                    is VoiceState.Result -> {
                        // Processa solo se eravamo in ascolto (evita vecchi stati dal singleton)
                        if (_state.value is VoiceLocationState.Listening ||
                            _state.value is VoiceLocationState.Transcribing) {
                            fullTranscript = voiceState.text
                            processTranscript()
                        }
                    }
                    is VoiceState.Error -> {
                        _state.value = VoiceLocationState.Error(voiceState.message)
                    }
                    is VoiceState.Processing -> {
                        // Ignora, usiamo il nostro stato Processing
                    }
                    is VoiceState.Unavailable -> {
                        _state.value = VoiceLocationState.Error(
                            "Riconoscimento vocale non disponibile"
                        )
                    }
                }
            }
        }
    }

    /**
     * Toggle ascolto on/off.
     */
    fun toggleListening() {
        when (_state.value) {
            is VoiceLocationState.Idle,
            is VoiceLocationState.Error -> {
                startListening()
            }
            is VoiceLocationState.Listening,
            is VoiceLocationState.Transcribing -> {
                stopListening()
            }
            else -> {
                // Processing o Extracted - non fare nulla
            }
        }
    }

    /**
     * Avvia l'ascolto.
     */
    fun startListening() {
        fullTranscript = ""
        _state.value = VoiceLocationState.Listening
        // BUG-17 fix: Abilita modalità Voice Dump per evitare feedback confusi
        voiceService.voiceDumpMode = true
        voiceService.startListening()
    }

    /**
     * Ferma l'ascolto manualmente.
     */
    fun stopListening() {
        voiceService.stopListening()
        // Il processing verrà avviato quando VoiceState diventa Idle
    }

    /**
     * Processa il transcript con Gemini.
     */
    private fun processTranscript() {
        if (fullTranscript.isBlank()) {
            _state.value = VoiceLocationState.Error("Nessun testo riconosciuto")
            return
        }

        Log.d(TAG, "Processing transcript: $fullTranscript")
        _state.value = VoiceLocationState.Processing

        viewModelScope.launch {
            geminiService.extractLocationData(fullTranscript)
                .onSuccess { extraction ->
                    Log.d(TAG, "Extraction success: $extraction")
                    val confirmData = buildConfirmData(extraction)
                    _state.value = VoiceLocationState.Extracted(confirmData)
                }
                .onFailure { error ->
                    Log.e(TAG, "Extraction failed", error)
                    _state.value = VoiceLocationState.Error(
                        error.message ?: "Errore durante l'elaborazione"
                    )
                }
        }
    }

    /**
     * Costruisce i dati per la schermata di conferma.
     */
    private fun buildConfirmData(extraction: LocationExtraction): LocationConfirmData {
        val warnings = buildWarnings(extraction)

        return LocationConfirmData(
            // Identificazione
            name = extraction.location.name ?: "",
            type = extraction.location.type ?: "",

            // Gerarchia
            buildingName = extraction.hierarchy?.buildingName ?: "",
            floorCode = extraction.hierarchy?.floorCode ?: "",
            floorName = extraction.hierarchy?.floorName ?: "",
            department = extraction.hierarchy?.department ?: "",

            // Dettagli
            hasOxygenOutlet = extraction.details?.hasOxygenOutlet ?: false,
            bedCount = extraction.details?.bedCount,
            notes = extraction.details?.notes ?: "",

            // Metadata
            confidence = extraction.confidence.overall,
            warnings = warnings
        )
    }

    /**
     * Costruisce la lista di warnings.
     */
    private fun buildWarnings(extraction: LocationExtraction): List<String> {
        val warnings = mutableListOf<String>()

        if (extraction.confidence.overall < 0.7f) {
            warnings.add("Confidenza bassa - verifica i dati")
        }

        extraction.confidence.missingFields.forEach { field ->
            val fieldName = when (field) {
                "name" -> "Nome ubicazione"
                "type" -> "Tipo"
                "building" -> "Edificio"
                "floor" -> "Piano"
                else -> field
            }
            warnings.add("Campo non rilevato: $fieldName")
        }

        return warnings
    }

    /**
     * Reset allo stato iniziale.
     */
    fun reset() {
        fullTranscript = ""
        _state.value = VoiceLocationState.Idle
        // Resetta anche VoiceService per evitare che vecchi stati vengano riprocessati
        voiceService.resetState()
        // BUG-17 fix: Ripristina modalità normale
        voiceService.voiceDumpMode = false
    }
}
