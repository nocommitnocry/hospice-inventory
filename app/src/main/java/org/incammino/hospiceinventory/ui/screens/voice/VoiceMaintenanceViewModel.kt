package org.incammino.hospiceinventory.ui.screens.voice

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.incammino.hospiceinventory.data.repository.MaintainerRepository
import org.incammino.hospiceinventory.data.repository.ProductRepository
import org.incammino.hospiceinventory.domain.model.MaintenanceType
import org.incammino.hospiceinventory.service.voice.*
import javax.inject.Inject

/**
 * ViewModel per VoiceMaintenanceScreen.
 * Gestisce l'input vocale e l'estrazione dati con Gemini.
 *
 * Paradigma "Voice Dump + Visual Confirm" (v2.0 - 26/12/2025)
 */
@HiltViewModel
class VoiceMaintenanceViewModel @Inject constructor(
    private val voiceService: VoiceService,
    private val geminiService: GeminiService,
    private val entityResolver: EntityResolver,
    private val productRepository: ProductRepository,
    private val maintainerRepository: MaintainerRepository
) : ViewModel() {

    companion object {
        private const val TAG = "VoiceMaintenanceVM"
    }

    private val _state = MutableStateFlow<VoiceMaintenanceState>(VoiceMaintenanceState.Idle)
    val state: StateFlow<VoiceMaintenanceState> = _state.asStateFlow()

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
                            (_state.value is VoiceMaintenanceState.Listening ||
                             _state.value is VoiceMaintenanceState.Transcribing)) {
                            processTranscript()
                        }
                    }
                    is VoiceState.Listening -> {
                        _state.value = VoiceMaintenanceState.Listening
                    }
                    is VoiceState.PartialResult -> {
                        _state.value = VoiceMaintenanceState.Transcribing(voiceState.text)
                        fullTranscript = voiceState.text
                    }
                    is VoiceState.Result -> {
                        fullTranscript = voiceState.text
                        processTranscript()
                    }
                    is VoiceState.Error -> {
                        _state.value = VoiceMaintenanceState.Error(voiceState.message)
                    }
                    is VoiceState.Processing -> {
                        // Ignora, usiamo il nostro stato Processing
                    }
                    is VoiceState.Unavailable -> {
                        _state.value = VoiceMaintenanceState.Error(
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
            is VoiceMaintenanceState.Idle,
            is VoiceMaintenanceState.Error -> {
                startListening()
            }
            is VoiceMaintenanceState.Listening,
            is VoiceMaintenanceState.Transcribing -> {
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
        _state.value = VoiceMaintenanceState.Listening
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
            _state.value = VoiceMaintenanceState.Error("Nessun testo riconosciuto")
            return
        }

        Log.d(TAG, "Processing transcript: $fullTranscript")
        _state.value = VoiceMaintenanceState.Processing

        viewModelScope.launch {
            geminiService.extractMaintenanceData(fullTranscript)
                .onSuccess { extraction ->
                    Log.d(TAG, "Extraction success: $extraction")
                    val confirmData = resolveExtraction(extraction)
                    _state.value = VoiceMaintenanceState.Extracted(confirmData)
                }
                .onFailure { error ->
                    Log.e(TAG, "Extraction failed", error)
                    _state.value = VoiceMaintenanceState.Error(
                        error.message ?: "Errore durante l'elaborazione"
                    )
                }
        }
    }

    /**
     * Risolve l'estrazione in dati pronti per la conferma.
     * Usa EntityResolver per trovare prodotti e manutentori.
     */
    private suspend fun resolveExtraction(extraction: MaintenanceExtraction): MaintenanceConfirmData {
        // 1. Risolvi prodotto
        val productMatch = resolveProduct(extraction.product)

        // 2. Risolvi manutentore
        val maintainerMatch = resolveMaintainer(extraction.maintainer)

        // 3. Parsa tipo manutenzione
        val maintenanceType = parseMaintenanceType(extraction.intervention.type)

        // 4. Parsa data
        val date = parseDate(extraction.intervention.date)

        // 5. Costruisci warnings
        val warnings = buildWarnings(extraction)

        return MaintenanceConfirmData(
            productMatch = productMatch,
            maintainerMatch = maintainerMatch,
            type = maintenanceType,
            description = extraction.intervention.description ?: "",
            durationMinutes = extraction.intervention.durationMinutes,
            isWarranty = extraction.intervention.isWarranty ?: false,
            date = date,
            confidence = extraction.confidence.overall,
            warnings = warnings
        )
    }

    /**
     * Risolve il prodotto dai termini di ricerca.
     */
    private suspend fun resolveProduct(info: ProductSearchInfo): ProductMatch {
        if (info.searchTerms.isEmpty()) {
            return ProductMatch.NotFound("")
        }

        val query = info.searchTerms.joinToString(" ")
        Log.d(TAG, "Searching products with query: $query")

        try {
            val results = productRepository.searchSync(query)
            Log.d(TAG, "Found ${results.size} products")

            return when {
                results.isEmpty() -> ProductMatch.NotFound(query)
                results.size == 1 -> ProductMatch.Found(results.first())
                else -> ProductMatch.Ambiguous(results.take(5), query)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Product search failed", e)
            return ProductMatch.NotFound(query)
        }
    }

    /**
     * Risolve il manutentore dal nome/ditta.
     */
    private suspend fun resolveMaintainer(info: MaintainerInfo?): MaintainerMatch {
        if (info == null) {
            // L'utente parla in prima persona
            return MaintainerMatch.SelfReported
        }

        val searchName = info.company ?: info.name
        if (searchName.isNullOrBlank()) {
            return MaintainerMatch.SelfReported
        }

        Log.d(TAG, "Resolving maintainer: $searchName")

        try {
            val resolution = entityResolver.resolveMaintainer(searchName)

            return when (resolution) {
                is EntityResolver.Resolution.Found ->
                    MaintainerMatch.Found(resolution.entity)
                is EntityResolver.Resolution.Ambiguous ->
                    MaintainerMatch.Ambiguous(resolution.candidates, searchName)
                is EntityResolver.Resolution.NotFound ->
                    MaintainerMatch.NotFound(info.name ?: "", info.company)
                is EntityResolver.Resolution.NeedsConfirmation ->
                    MaintainerMatch.Ambiguous(listOf(resolution.candidate), searchName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Maintainer resolution failed", e)
            return MaintainerMatch.NotFound(info.name ?: "", info.company)
        }
    }

    /**
     * Parsa il tipo di manutenzione dalla stringa Gemini.
     */
    private fun parseMaintenanceType(typeStr: String?): MaintenanceType? {
        if (typeStr == null) return null

        // Mappa i tipi Gemini ai MaintenanceType dell'app
        val mapped = ExtractionPrompts.mapInterventionType(typeStr) ?: typeStr

        return MaintenanceType.entries.find {
            it.name.equals(mapped, ignoreCase = true) ||
            it.label.equals(mapped, ignoreCase = true)
        }
    }

    /**
     * Parsa la data dalla stringa.
     */
    private fun parseDate(dateStr: String?): kotlinx.datetime.LocalDate {
        if (dateStr == null) {
            return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        }

        return try {
            kotlinx.datetime.LocalDate.parse(dateStr)
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse date: $dateStr")
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        }
    }

    /**
     * Costruisce la lista di warnings.
     */
    private fun buildWarnings(extraction: MaintenanceExtraction): List<String> {
        val warnings = mutableListOf<String>()

        if (extraction.confidence.overall < 0.7f) {
            warnings.add("Confidenza bassa - verifica i dati")
        }

        extraction.confidence.missingFields.forEach { field ->
            val fieldName = when (field) {
                "maintainer" -> "Manutentore"
                "product" -> "Prodotto"
                "type" -> "Tipo intervento"
                "description" -> "Descrizione"
                "duration" -> "Durata"
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
        _state.value = VoiceMaintenanceState.Idle
    }
}
