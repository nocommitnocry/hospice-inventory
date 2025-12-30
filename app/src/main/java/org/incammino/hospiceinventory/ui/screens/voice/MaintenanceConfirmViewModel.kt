package org.incammino.hospiceinventory.ui.screens.voice

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.incammino.hospiceinventory.data.repository.MaintenanceRepository
import org.incammino.hospiceinventory.data.repository.MaintainerRepository
import org.incammino.hospiceinventory.data.repository.ProductRepository
import org.incammino.hospiceinventory.domain.model.Maintainer
import org.incammino.hospiceinventory.domain.model.Maintenance
import org.incammino.hospiceinventory.domain.model.MaintenanceType
import org.incammino.hospiceinventory.domain.model.Product
import org.incammino.hospiceinventory.service.voice.GeminiService
import org.incammino.hospiceinventory.service.voice.MaintenanceFormData
import org.incammino.hospiceinventory.service.voice.MaintainerMatch
import org.incammino.hospiceinventory.service.voice.ProductMatch
import org.incammino.hospiceinventory.service.voice.SaveState
import org.incammino.hospiceinventory.service.voice.VoiceService
import org.incammino.hospiceinventory.service.voice.VoiceState
import org.incammino.hospiceinventory.ui.components.voice.VoiceContinueState
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel per MaintenanceConfirmScreen.
 * Gestisce il salvataggio della manutenzione.
 *
 * Paradigma "Voice Dump + Visual Confirm" (v2.0 - 26/12/2025)
 */
/**
 * Stato UI per la creazione inline di entità.
 */
data class InlineCreationState(
    val isCreatingMaintainer: Boolean = false,
    val maintainerWasCreatedInline: Boolean = false,
    val createdMaintainerId: String? = null,
    val createdMaintainerName: String? = null
)

@HiltViewModel
class MaintenanceConfirmViewModel @Inject constructor(
    private val maintenanceRepository: MaintenanceRepository,
    private val maintainerRepository: MaintainerRepository,
    private val productRepository: ProductRepository,
    private val voiceService: VoiceService,
    private val geminiService: GeminiService
) : ViewModel() {

    companion object {
        private const val TAG = "MaintenanceConfirmVM"
    }

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _inlineCreationState = MutableStateFlow(InlineCreationState())
    val inlineCreationState: StateFlow<InlineCreationState> = _inlineCreationState.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════════════════
    // RICERCA PRODOTTO INLINE
    // ═══════════════════════════════════════════════════════════════════════════════

    private val _productSearchQuery = MutableStateFlow("")
    val productSearchQuery: StateFlow<String> = _productSearchQuery.asStateFlow()

    private val _productSearchResults = MutableStateFlow<List<Product>>(emptyList())
    val productSearchResults: StateFlow<List<Product>> = _productSearchResults.asStateFlow()

    /**
     * Aggiorna la query di ricerca prodotto.
     * Avvia la ricerca automaticamente se query >= 2 caratteri.
     */
    fun updateProductSearchQuery(query: String) {
        _productSearchQuery.value = query
        if (query.length >= 2) {
            searchProducts(query)
        } else {
            _productSearchResults.value = emptyList()
        }
    }

    /**
     * Esegue la ricerca prodotti nel repository.
     */
    private fun searchProducts(query: String) {
        viewModelScope.launch {
            try {
                val results = productRepository.searchSync(query)
                _productSearchResults.value = results
            } catch (e: Exception) {
                Log.e(TAG, "Product search failed", e)
                _productSearchResults.value = emptyList()
            }
        }
    }

    /**
     * Pulisce la ricerca prodotto (query e risultati).
     */
    fun clearProductSearch() {
        _productSearchQuery.value = ""
        _productSearchResults.value = emptyList()
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // VOICE CONTINUE STATE
    // ═══════════════════════════════════════════════════════════════════════════════

    private val _voiceContinueState = MutableStateFlow(VoiceContinueState.Idle)
    val voiceContinueState: StateFlow<VoiceContinueState> = _voiceContinueState.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    /** Callback per aggiornamenti form dalla voce */
    var onVoiceUpdate: ((Map<String, String>) -> Unit)? = null

    /**
     * Callback invocato quando il transcript è pronto.
     * La Screen deve rispondere chiamando processVoiceWithContext con i dati attuali del form.
     */
    var onProcessVoiceWithContext: ((String) -> Unit)? = null

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
                    is VoiceState.Listening -> {
                        _voiceContinueState.value = VoiceContinueState.Listening
                    }
                    is VoiceState.Processing -> {
                        _voiceContinueState.value = VoiceContinueState.Processing
                    }
                    is VoiceState.PartialResult -> {
                        _partialTranscript.value = state.text
                    }
                    is VoiceState.Result -> {
                        processAdditionalVoiceInput(state.text)
                    }
                    is VoiceState.Error -> {
                        _voiceContinueState.value = VoiceContinueState.Idle
                        Log.w(TAG, "Voice error: ${state.message}")
                    }
                    is VoiceState.Unavailable -> {
                        _voiceContinueState.value = VoiceContinueState.Idle
                    }
                }
            }
        }
    }

    /**
     * Toggle ascolto vocale.
     */
    fun toggleVoiceInput() {
        when (_voiceContinueState.value) {
            VoiceContinueState.Idle -> {
                voiceService.initialize()
                voiceService.startListening()
            }
            VoiceContinueState.Listening -> voiceService.stopListening()
            VoiceContinueState.Processing -> { /* Ignora durante elaborazione */ }
        }
    }

    /**
     * Processa input vocale aggiuntivo.
     * Invoca il callback onProcessVoiceWithContext per permettere alla Screen
     * di passare i dati attuali del form (inclusi quelli inseriti manualmente).
     */
    private fun processAdditionalVoiceInput(transcript: String) {
        val callback = onProcessVoiceWithContext
        if (callback != null) {
            callback.invoke(transcript)
        } else {
            // Fallback: processa senza contesto (comportamento legacy)
            Log.w(TAG, "onProcessVoiceWithContext not set, processing without context")
            viewModelScope.launch {
                _voiceContinueState.value = VoiceContinueState.Processing
                try {
                    val updates = geminiService.updateMaintenanceFromVoice(
                        currentData = "",
                        newInput = transcript
                    )
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
    }

    /**
     * Processa input vocale con contesto dei dati attuali.
     */
    fun processVoiceWithContext(transcript: String, currentData: MaintenanceFormData) {
        viewModelScope.launch {
            _voiceContinueState.value = VoiceContinueState.Processing

            try {
                val context = """
                    Prodotto: ${currentData.productName}
                    Manutentore: ${currentData.maintainerName}
                    Tipo intervento: ${currentData.type}
                    Descrizione: ${currentData.description}
                    Durata: ${currentData.durationMinutes?.let { "$it minuti" } ?: "non specificata"}
                    In garanzia: ${if (currentData.isWarranty) "sì" else "no"}
                    Data: ${currentData.date}
                    Note: ${currentData.notes}
                """.trimIndent()

                Log.d(TAG, "Processing voice with context:\n$context\nNew input: $transcript")

                val updates = geminiService.updateMaintenanceFromVoice(
                    currentData = context,
                    newInput = transcript
                )

                if (updates.isNotEmpty()) {
                    Log.d(TAG, "Voice updates with context: $updates")
                    onVoiceUpdate?.invoke(updates)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing voice input with context", e)
            } finally {
                _voiceContinueState.value = VoiceContinueState.Idle
            }
        }
    }

    /**
     * Salva la manutenzione nel database.
     *
     * @param productMatch Il prodotto selezionato (deve essere Found)
     * @param maintainerMatch Il manutentore (opzionale)
     * @param type Il tipo di manutenzione
     * @param description La descrizione dell'intervento
     * @param durationMinutes La durata in minuti
     * @param isWarranty Se l'intervento è in garanzia
     * @param date La data dell'intervento
     * @param notes Note aggiuntive
     */
    fun save(
        productMatch: ProductMatch,
        maintainerMatch: MaintainerMatch,
        type: MaintenanceType?,
        description: String,
        durationMinutes: Int?,
        isWarranty: Boolean,
        date: LocalDate,
        notes: String? = null
    ) {
        // Validazione
        if (productMatch !is ProductMatch.Found) {
            _saveState.value = SaveState.Error("Seleziona un prodotto")
            return
        }

        if (type == null) {
            _saveState.value = SaveState.Error("Seleziona il tipo di intervento")
            return
        }

        if (description.isBlank()) {
            _saveState.value = SaveState.Error("Inserisci una descrizione")
            return
        }

        _saveState.value = SaveState.Saving

        viewModelScope.launch {
            try {
                val product = productMatch.product
                val maintainerId = when (maintainerMatch) {
                    is MaintainerMatch.Found -> maintainerMatch.maintainer.id
                    else -> null // SelfReported, Ambiguous, NotFound
                }

                // Converti data in Instant (inizio giornata nel fuso orario locale)
                val dateInstant = date.atStartOfDayIn(TimeZone.currentSystemDefault())

                val maintenance = Maintenance(
                    id = UUID.randomUUID().toString(),
                    productId = product.id,
                    maintainerId = maintainerId,
                    date = dateInstant,
                    type = type,
                    outcome = null, // L'utente può aggiungere dopo
                    notes = buildNotes(description, durationMinutes, notes),
                    cost = null, // L'utente può aggiungere dopo
                    invoiceNumber = null,
                    isWarrantyWork = isWarranty,
                    requestEmailSent = false,
                    reportEmailSent = false
                )

                Log.d(TAG, "Saving maintenance: $maintenance")

                maintenanceRepository.insert(maintenance, updateProductDates = true)

                Log.d(TAG, "Maintenance saved successfully")
                _saveState.value = SaveState.Success

            } catch (e: Exception) {
                Log.e(TAG, "Failed to save maintenance", e)
                _saveState.value = SaveState.Error(
                    e.message ?: "Errore durante il salvataggio"
                )
            }
        }
    }

    /**
     * Costruisce le note combinando descrizione, durata e note extra.
     */
    private fun buildNotes(description: String, durationMinutes: Int?, notes: String?): String {
        val parts = mutableListOf<String>()

        parts.add(description)

        if (durationMinutes != null && durationMinutes > 0) {
            val hours = durationMinutes / 60
            val mins = durationMinutes % 60
            val durationStr = when {
                hours > 0 && mins > 0 -> "${hours}h ${mins}min"
                hours > 0 -> "${hours}h"
                else -> "${mins}min"
            }
            parts.add("Durata: $durationStr")
        }

        if (!notes.isNullOrBlank()) {
            parts.add(notes)
        }

        return parts.joinToString("\n")
    }

    /**
     * Reset dello stato.
     */
    fun reset() {
        _saveState.value = SaveState.Idle
        _inlineCreationState.value = InlineCreationState()
    }

    /**
     * Crea un manutentore inline con dati minimi.
     * Il manutentore avrà needsCompletion=true e dovrà essere completato successivamente.
     *
     * @param name Il nome del manutentore da creare
     * @param company L'azienda (opzionale)
     * @return Il MaintainerMatch.Found con il nuovo manutentore, oppure null in caso di errore
     */
    fun createMaintainerInline(
        name: String,
        company: String? = null,
        onSuccess: (MaintainerMatch.Found) -> Unit
    ) {
        if (name.isBlank()) return

        _inlineCreationState.update { it.copy(isCreatingMaintainer = true) }

        viewModelScope.launch {
            try {
                val maintainerName = company?.takeIf { it.isNotBlank() && it != name }
                    ?.let { "$name ($it)" }
                    ?: name

                val newMaintainer = Maintainer(
                    id = "",  // Repository genera UUID
                    name = maintainerName,
                    email = null,
                    phone = null,
                    address = null,
                    city = null,
                    postalCode = null,
                    province = null,
                    vatNumber = null,
                    contactPerson = null,
                    specialization = null,
                    isSupplier = false,
                    notes = "Creato da registrazione vocale - da completare",
                    isActive = true,
                    needsCompletion = true
                )

                val id = maintainerRepository.insert(newMaintainer)
                val createdMaintainer = newMaintainer.copy(id = id)

                Log.d(TAG, "Maintainer created inline: $id - $maintainerName")

                _inlineCreationState.update {
                    it.copy(
                        isCreatingMaintainer = false,
                        maintainerWasCreatedInline = true,
                        createdMaintainerId = id,
                        createdMaintainerName = maintainerName
                    )
                }

                onSuccess(MaintainerMatch.Found(createdMaintainer))

            } catch (e: Exception) {
                Log.e(TAG, "Failed to create maintainer inline", e)
                _inlineCreationState.update { it.copy(isCreatingMaintainer = false) }
            }
        }
    }
}
