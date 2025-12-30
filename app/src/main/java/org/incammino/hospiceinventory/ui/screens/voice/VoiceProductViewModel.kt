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
import org.incammino.hospiceinventory.data.repository.MaintainerRepository
import org.incammino.hospiceinventory.service.voice.*
import javax.inject.Inject

/**
 * ViewModel per VoiceProductScreen.
 * Gestisce l'input vocale e l'estrazione dati con Gemini.
 *
 * Paradigma "Voice Dump + Visual Confirm" (v2.0 - Fase 2)
 */
@HiltViewModel
class VoiceProductViewModel @Inject constructor(
    private val voiceService: VoiceService,
    private val geminiService: GeminiService,
    private val entityResolver: EntityResolver,
    private val locationRepository: LocationRepository,
    private val maintainerRepository: MaintainerRepository
) : ViewModel() {

    companion object {
        private const val TAG = "VoiceProductVM"
    }

    private val _state = MutableStateFlow<VoiceProductState>(VoiceProductState.Idle)
    val state: StateFlow<VoiceProductState> = _state.asStateFlow()

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
                            (_state.value is VoiceProductState.Listening ||
                             _state.value is VoiceProductState.Transcribing)) {
                            processTranscript()
                        }
                    }
                    is VoiceState.Listening -> {
                        _state.value = VoiceProductState.Listening
                    }
                    is VoiceState.PartialResult -> {
                        _state.value = VoiceProductState.Transcribing(voiceState.text)
                        fullTranscript = voiceState.text
                    }
                    is VoiceState.Result -> {
                        // Processa solo se eravamo in ascolto (evita vecchi stati dal singleton)
                        if (_state.value is VoiceProductState.Listening ||
                            _state.value is VoiceProductState.Transcribing) {
                            fullTranscript = voiceState.text
                            processTranscript()
                        }
                    }
                    is VoiceState.Error -> {
                        _state.value = VoiceProductState.Error(voiceState.message)
                    }
                    is VoiceState.Processing -> {
                        // Ignora, usiamo il nostro stato Processing
                    }
                    is VoiceState.Unavailable -> {
                        _state.value = VoiceProductState.Error(
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
            is VoiceProductState.Idle,
            is VoiceProductState.Error -> {
                startListening()
            }
            is VoiceProductState.Listening,
            is VoiceProductState.Transcribing -> {
                stopListening()
            }
            else -> {
                // Processing o Extracted - non fare nulla
            }
        }
    }

    /**
     * Avvia l'ascolto in modalità TAP-TO-STOP.
     * L'utente controlla quando fermare premendo nuovamente il pulsante.
     */
    fun startListening() {
        fullTranscript = ""
        _state.value = VoiceProductState.Listening
        voiceService.startManualListening()
    }

    /**
     * Ferma l'ascolto manualmente (TAP-TO-STOP).
     * VoiceService finalizzerà il risultato accumulato.
     */
    fun stopListening() {
        voiceService.stopManualListening()
        // Il processing verrà avviato quando riceviamo VoiceState.Result
    }

    /**
     * Processa il transcript con Gemini.
     */
    private fun processTranscript() {
        if (fullTranscript.isBlank()) {
            _state.value = VoiceProductState.Error("Nessun testo riconosciuto")
            return
        }

        Log.d(TAG, "Processing transcript: $fullTranscript")
        _state.value = VoiceProductState.Processing

        viewModelScope.launch {
            geminiService.extractProductData(fullTranscript)
                .onSuccess { extraction ->
                    Log.d(TAG, "Extraction success: $extraction")
                    val confirmData = resolveExtraction(extraction)
                    _state.value = VoiceProductState.Extracted(confirmData)
                }
                .onFailure { error ->
                    Log.e(TAG, "Extraction failed", error)
                    _state.value = VoiceProductState.Error(
                        error.message ?: "Errore durante l'elaborazione"
                    )
                }
        }
    }

    /**
     * Risolve l'estrazione in dati pronti per la conferma.
     * Usa EntityResolver per trovare ubicazioni e fornitori.
     */
    private suspend fun resolveExtraction(extraction: ProductExtraction): ProductConfirmData {
        // 1. Risolvi ubicazione
        val locationMatch = resolveLocation(extraction.location)

        // 2. Risolvi fornitore
        val supplierMatch = resolveSupplier(extraction.supplier)

        // 3. Risolvi manutentore garanzia (se diverso dal fornitore)
        val warrantyMaintainerMatch = resolveWarrantyMaintainer(extraction.warranty)

        // 4. Costruisci warnings
        val warnings = buildWarnings(extraction)

        return ProductConfirmData(
            name = extraction.product.name ?: "",
            model = extraction.product.model ?: "",
            manufacturer = extraction.product.manufacturer ?: "",
            serialNumber = extraction.product.serialNumber ?: "",
            barcode = extraction.product.barcode ?: "",
            category = extraction.product.category ?: "",
            locationMatch = locationMatch,
            supplierMatch = supplierMatch,
            warrantyMonths = extraction.warranty?.months,
            warrantyMaintainerMatch = warrantyMaintainerMatch,
            maintenanceFrequencyMonths = extraction.maintenance?.frequencyMonths,
            confidence = extraction.confidence.overall,
            warnings = warnings
        )
    }

    /**
     * Risolve l'ubicazione dai termini di ricerca.
     */
    private suspend fun resolveLocation(info: LocationSearchInfo): LocationMatch {
        if (info.searchTerms.isEmpty()) {
            return LocationMatch.NotFound("")
        }

        val query = info.searchTerms.joinToString(" ")
        Log.d(TAG, "Resolving location with query: $query")

        try {
            val resolution = entityResolver.resolveLocation(query)

            return when (resolution) {
                is EntityResolver.Resolution.Found ->
                    LocationMatch.Found(resolution.entity)
                is EntityResolver.Resolution.Ambiguous ->
                    LocationMatch.Ambiguous(resolution.candidates, query)
                is EntityResolver.Resolution.NotFound ->
                    LocationMatch.NotFound(query)
                is EntityResolver.Resolution.NeedsConfirmation ->
                    LocationMatch.Ambiguous(listOf(resolution.candidate), query)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Location resolution failed", e)
            return LocationMatch.NotFound(query)
        }
    }

    /**
     * Risolve il fornitore dal nome.
     */
    private suspend fun resolveSupplier(info: SupplierInfoExtraction?): MaintainerMatch {
        if (info?.name.isNullOrBlank()) {
            return MaintainerMatch.NotFound("", null)
        }

        val searchName = info!!.name!!
        Log.d(TAG, "Resolving supplier: $searchName")

        try {
            val resolution = entityResolver.resolveMaintainer(searchName)

            return when (resolution) {
                is EntityResolver.Resolution.Found ->
                    MaintainerMatch.Found(resolution.entity)
                is EntityResolver.Resolution.Ambiguous ->
                    MaintainerMatch.Ambiguous(resolution.candidates, searchName)
                is EntityResolver.Resolution.NotFound ->
                    MaintainerMatch.NotFound(searchName, null)
                is EntityResolver.Resolution.NeedsConfirmation ->
                    MaintainerMatch.Ambiguous(listOf(resolution.candidate), searchName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Supplier resolution failed", e)
            return MaintainerMatch.NotFound(searchName, null)
        }
    }

    /**
     * Risolve il manutentore per la garanzia (se specificato).
     */
    private suspend fun resolveWarrantyMaintainer(info: WarrantyInfoExtraction?): MaintainerMatch? {
        if (info?.maintainerName.isNullOrBlank()) {
            return null
        }

        val searchName = info!!.maintainerName!!
        Log.d(TAG, "Resolving warranty maintainer: $searchName")

        try {
            val resolution = entityResolver.resolveMaintainer(searchName)

            return when (resolution) {
                is EntityResolver.Resolution.Found ->
                    MaintainerMatch.Found(resolution.entity)
                is EntityResolver.Resolution.Ambiguous ->
                    MaintainerMatch.Ambiguous(resolution.candidates, searchName)
                is EntityResolver.Resolution.NotFound ->
                    MaintainerMatch.NotFound(searchName, null)
                is EntityResolver.Resolution.NeedsConfirmation ->
                    MaintainerMatch.Ambiguous(listOf(resolution.candidate), searchName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Warranty maintainer resolution failed", e)
            return MaintainerMatch.NotFound(searchName, null)
        }
    }

    /**
     * Costruisce la lista di warnings.
     */
    private fun buildWarnings(extraction: ProductExtraction): List<String> {
        val warnings = mutableListOf<String>()

        if (extraction.confidence.overall < 0.7f) {
            warnings.add("Confidenza bassa - verifica i dati")
        }

        extraction.confidence.missingFields.forEach { field ->
            val fieldName = when (field) {
                "name" -> "Nome prodotto"
                "location" -> "Ubicazione"
                "supplier" -> "Fornitore"
                "warranty" -> "Garanzia"
                "maintenance" -> "Manutenzione"
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
        _state.value = VoiceProductState.Idle
        // Resetta anche VoiceService per evitare che vecchi stati vengano riprocessati
        voiceService.resetState()
    }
}
