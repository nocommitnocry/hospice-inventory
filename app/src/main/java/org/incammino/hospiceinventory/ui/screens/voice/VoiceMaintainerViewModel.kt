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
 * ViewModel per VoiceMaintainerScreen.
 * Gestisce l'input vocale e l'estrazione dati con Gemini.
 *
 * Paradigma "Voice Dump + Visual Confirm" (Fase 3 - 28/12/2025)
 */
@HiltViewModel
class VoiceMaintainerViewModel @Inject constructor(
    private val voiceService: VoiceService,
    private val geminiService: GeminiService
) : ViewModel() {

    companion object {
        private const val TAG = "VoiceMaintainerVM"
    }

    private val _state = MutableStateFlow<VoiceMaintainerState>(VoiceMaintainerState.Idle)
    val state: StateFlow<VoiceMaintainerState> = _state.asStateFlow()

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
                            (_state.value is VoiceMaintainerState.Listening ||
                             _state.value is VoiceMaintainerState.Transcribing)) {
                            processTranscript()
                        }
                    }
                    is VoiceState.Listening -> {
                        _state.value = VoiceMaintainerState.Listening
                    }
                    is VoiceState.PartialResult -> {
                        _state.value = VoiceMaintainerState.Transcribing(voiceState.text)
                        fullTranscript = voiceState.text
                    }
                    is VoiceState.Result -> {
                        // Processa solo se eravamo in ascolto (evita vecchi stati dal singleton)
                        if (_state.value is VoiceMaintainerState.Listening ||
                            _state.value is VoiceMaintainerState.Transcribing) {
                            fullTranscript = voiceState.text
                            processTranscript()
                        }
                    }
                    is VoiceState.Error -> {
                        _state.value = VoiceMaintainerState.Error(voiceState.message)
                    }
                    is VoiceState.Processing -> {
                        // Ignora, usiamo il nostro stato Processing
                    }
                    is VoiceState.Unavailable -> {
                        _state.value = VoiceMaintainerState.Error(
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
            is VoiceMaintainerState.Idle,
            is VoiceMaintainerState.Error -> {
                startListening()
            }
            is VoiceMaintainerState.Listening,
            is VoiceMaintainerState.Transcribing -> {
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
        _state.value = VoiceMaintainerState.Listening
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
            _state.value = VoiceMaintainerState.Error("Nessun testo riconosciuto")
            return
        }

        Log.d(TAG, "Processing transcript: $fullTranscript")
        _state.value = VoiceMaintainerState.Processing

        viewModelScope.launch {
            geminiService.extractMaintainerData(fullTranscript)
                .onSuccess { extraction ->
                    Log.d(TAG, "Extraction success: $extraction")
                    val confirmData = buildConfirmData(extraction)
                    _state.value = VoiceMaintainerState.Extracted(confirmData)
                }
                .onFailure { error ->
                    Log.e(TAG, "Extraction failed", error)
                    _state.value = VoiceMaintainerState.Error(
                        error.message ?: "Errore durante l'elaborazione"
                    )
                }
        }
    }

    /**
     * Costruisce i dati per la schermata di conferma.
     */
    private fun buildConfirmData(extraction: MaintainerExtraction): MaintainerConfirmData {
        val warnings = buildWarnings(extraction)

        return MaintainerConfirmData(
            // Dati azienda
            name = extraction.company.name ?: "",
            vatNumber = extraction.company.vatNumber ?: "",
            specialization = extraction.company.specialization ?: "",

            // Contatti
            email = extraction.contact?.email ?: "",
            phone = extraction.contact?.phone ?: "",
            contactPerson = extraction.contact?.contactPerson ?: "",

            // Indirizzo
            street = extraction.address?.street ?: "",
            city = extraction.address?.city ?: "",
            postalCode = extraction.address?.postalCode ?: "",
            province = extraction.address?.province ?: "",

            // Business
            isSupplier = extraction.business?.isSupplier ?: false,
            notes = extraction.business?.notes ?: "",

            // Metadata
            confidence = extraction.confidence.overall,
            warnings = warnings
        )
    }

    /**
     * Costruisce la lista di warnings.
     */
    private fun buildWarnings(extraction: MaintainerExtraction): List<String> {
        val warnings = mutableListOf<String>()

        if (extraction.confidence.overall < 0.7f) {
            warnings.add("Confidenza bassa - verifica i dati")
        }

        extraction.confidence.missingFields.forEach { field ->
            val fieldName = when (field) {
                "name" -> "Nome azienda"
                "email" -> "Email"
                "phone" -> "Telefono"
                "address" -> "Indirizzo"
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
        _state.value = VoiceMaintainerState.Idle
        // Resetta anche VoiceService per evitare che vecchi stati vengano riprocessati
        voiceService.resetState()
    }
}
