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
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.incammino.hospiceinventory.data.repository.LocationRepository
import org.incammino.hospiceinventory.data.repository.MaintainerRepository
import org.incammino.hospiceinventory.data.repository.ProductRepository
import org.incammino.hospiceinventory.domain.model.Location
import org.incammino.hospiceinventory.domain.model.Maintainer
import org.incammino.hospiceinventory.domain.model.MaintenanceFrequency
import org.incammino.hospiceinventory.domain.model.Product
import org.incammino.hospiceinventory.service.voice.GeminiService
import org.incammino.hospiceinventory.service.voice.LocationMatch
import org.incammino.hospiceinventory.service.voice.MaintainerMatch
import org.incammino.hospiceinventory.service.voice.SaveState
import org.incammino.hospiceinventory.service.voice.VoiceService
import org.incammino.hospiceinventory.service.voice.VoiceState
import org.incammino.hospiceinventory.ui.components.voice.VoiceContinueState
import java.util.UUID
import javax.inject.Inject

/**
 * Stato UI per la creazione inline di entità nel flusso prodotto.
 */
data class ProductInlineCreationState(
    val isCreatingLocation: Boolean = false,
    val locationWasCreatedInline: Boolean = false,
    val createdLocationId: String? = null,
    val createdLocationName: String? = null,
    val isCreatingSupplier: Boolean = false,
    val supplierWasCreatedInline: Boolean = false,
    val createdSupplierId: String? = null,
    val createdSupplierName: String? = null
)

/**
 * Dati del form prodotto per voice continue.
 */
data class ProductFormData(
    var name: String = "",
    var model: String = "",
    var manufacturer: String = "",
    var serialNumber: String = "",
    var barcode: String = "",
    var category: String = "",
    var location: String = "",
    var warrantyMonths: Int? = null,
    var maintenanceFrequencyMonths: Int? = null,
    var notes: String = ""
)

/**
 * ViewModel per ProductConfirmScreen.
 * Gestisce il salvataggio del prodotto.
 *
 * Paradigma "Voice Dump + Visual Confirm" (v2.0 - Fase 2)
 */
@HiltViewModel
class ProductConfirmViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val locationRepository: LocationRepository,
    private val maintainerRepository: MaintainerRepository,
    private val voiceService: VoiceService,
    private val geminiService: GeminiService
) : ViewModel() {

    companion object {
        private const val TAG = "ProductConfirmVM"
    }

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _inlineCreationState = MutableStateFlow(ProductInlineCreationState())
    val inlineCreationState: StateFlow<ProductInlineCreationState> = _inlineCreationState.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════════════════
    // VOICE CONTINUE STATE
    // ═══════════════════════════════════════════════════════════════════════════════

    private val _voiceContinueState = MutableStateFlow(VoiceContinueState.Idle)
    val voiceContinueState: StateFlow<VoiceContinueState> = _voiceContinueState.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    /** Callback per aggiornamenti form dalla voce */
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
     * Processa input vocale aggiuntivo e aggiorna i campi.
     */
    private fun processAdditionalVoiceInput(transcript: String) {
        viewModelScope.launch {
            _voiceContinueState.value = VoiceContinueState.Processing

            try {
                // Chiedi a Gemini di estrarre aggiornamenti
                val updates = geminiService.updateProductFromVoice(
                    currentData = "", // La Screen passerà i dati attuali
                    newInput = transcript
                )

                if (updates.isNotEmpty()) {
                    Log.d(TAG, "Voice updates: $updates")
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
     * Processa input vocale con contesto dei dati attuali.
     */
    fun processVoiceWithContext(transcript: String, currentData: ProductFormData) {
        viewModelScope.launch {
            _voiceContinueState.value = VoiceContinueState.Processing

            try {
                val context = """
                    Nome: ${currentData.name}
                    Modello: ${currentData.model}
                    Produttore: ${currentData.manufacturer}
                    Seriale: ${currentData.serialNumber}
                    Barcode: ${currentData.barcode}
                    Categoria: ${currentData.category}
                    Ubicazione: ${currentData.location}
                    Garanzia: ${currentData.warrantyMonths ?: "non specificata"} mesi
                    Frequenza manutenzione: ${currentData.maintenanceFrequencyMonths ?: "non specificata"} mesi
                    Note: ${currentData.notes}
                """.trimIndent()

                val updates = geminiService.updateProductFromVoice(
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
     * Salva il prodotto nel database.
     */
    fun save(
        name: String,
        model: String,
        manufacturer: String,
        serialNumber: String,
        barcode: String,
        category: String,
        locationMatch: LocationMatch,
        supplierMatch: MaintainerMatch,
        warrantyMonths: Int?,
        warrantyMaintainerMatch: MaintainerMatch?,
        maintenanceFrequencyMonths: Int?,
        notes: String?
    ) {
        // Validazione
        if (name.isBlank()) {
            _saveState.value = SaveState.Error("Inserisci il nome del prodotto")
            return
        }

        if (locationMatch !is LocationMatch.Found && locationMatch !is LocationMatch.NotFound) {
            // Se ambiguo, non possiamo salvare
            _saveState.value = SaveState.Error("Seleziona un'ubicazione")
            return
        }

        _saveState.value = SaveState.Saving

        viewModelScope.launch {
            try {
                val today = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date

                // Calcola ubicazione
                val locationName = when (locationMatch) {
                    is LocationMatch.Found -> locationMatch.location.name
                    is LocationMatch.NotFound -> locationMatch.searchTerms.ifBlank { "Non specificata" }
                    is LocationMatch.Ambiguous -> locationMatch.searchTerms
                }

                // Calcola fornitore
                val supplierId = when (supplierMatch) {
                    is MaintainerMatch.Found -> supplierMatch.maintainer.id
                    else -> null
                }
                val supplierName = when (supplierMatch) {
                    is MaintainerMatch.Found -> supplierMatch.maintainer.name
                    is MaintainerMatch.NotFound -> supplierMatch.name.ifBlank { null }
                    else -> null
                }

                // Calcola manutentore garanzia
                val warrantyMaintainerId = when (warrantyMaintainerMatch) {
                    is MaintainerMatch.Found -> warrantyMaintainerMatch.maintainer.id
                    else -> supplierId // Default: fornitore è anche manutentore garanzia
                }

                // Calcola date garanzia
                val warrantyStartDate = if (warrantyMonths != null && warrantyMonths > 0) today else null
                val warrantyEndDate = warrantyStartDate?.plus(DatePeriod(months = warrantyMonths ?: 0))

                // Calcola frequenza manutenzione
                val maintenanceFrequency = maintenanceFrequencyMonths?.let { months ->
                    when (months) {
                        3 -> MaintenanceFrequency.TRIMESTRALE
                        6 -> MaintenanceFrequency.SEMESTRALE
                        12 -> MaintenanceFrequency.ANNUALE
                        24 -> MaintenanceFrequency.BIENNALE
                        36 -> MaintenanceFrequency.TRIENNALE
                        48 -> MaintenanceFrequency.QUADRIENNALE
                        60 -> MaintenanceFrequency.QUINQUENNALE
                        else -> MaintenanceFrequency.CUSTOM
                    }
                }

                // Costruisci descrizione
                val description = buildDescription(model, manufacturer, serialNumber)

                val product = Product(
                    id = UUID.randomUUID().toString(),
                    barcode = barcode.takeIf { it.isNotBlank() },
                    name = name.trim(),
                    description = description,
                    category = category.ifBlank { "Altro" },
                    location = locationName,
                    assigneeId = null,
                    warrantyMaintainerId = warrantyMaintainerId,
                    warrantyStartDate = warrantyStartDate,
                    warrantyEndDate = warrantyEndDate,
                    serviceMaintainerId = supplierId,
                    maintenanceFrequency = maintenanceFrequency,
                    maintenanceStartDate = if (maintenanceFrequency != null) today else null,
                    maintenanceIntervalDays = if (maintenanceFrequency == MaintenanceFrequency.CUSTOM) {
                        (maintenanceFrequencyMonths ?: 12) * 30
                    } else null,
                    lastMaintenanceDate = null,
                    nextMaintenanceDue = maintenanceFrequency?.let {
                        today.plus(DatePeriod(months = maintenanceFrequencyMonths ?: 12))
                    },
                    purchaseDate = today,
                    price = null,
                    accountType = null,
                    supplier = supplierName,
                    invoiceNumber = null,
                    imageUri = null,
                    notes = notes?.takeIf { it.isNotBlank() },
                    isActive = true
                )

                Log.d(TAG, "Saving product: $product")

                productRepository.insert(product)

                Log.d(TAG, "Product saved successfully")
                _saveState.value = SaveState.Success

            } catch (e: Exception) {
                Log.e(TAG, "Failed to save product", e)
                _saveState.value = SaveState.Error(
                    e.message ?: "Errore durante il salvataggio"
                )
            }
        }
    }

    /**
     * Costruisce la descrizione combinando modello, produttore e seriale.
     */
    private fun buildDescription(model: String, manufacturer: String, serialNumber: String): String? {
        val parts = mutableListOf<String>()

        if (manufacturer.isNotBlank()) {
            parts.add(manufacturer)
        }
        if (model.isNotBlank()) {
            parts.add("Modello: $model")
        }
        if (serialNumber.isNotBlank()) {
            parts.add("S/N: $serialNumber")
        }

        return if (parts.isNotEmpty()) parts.joinToString("\n") else null
    }

    /**
     * Reset dello stato.
     */
    fun reset() {
        _saveState.value = SaveState.Idle
        _inlineCreationState.value = ProductInlineCreationState()
    }

    /**
     * Crea una ubicazione inline con dati minimi.
     * L'ubicazione avrà needsCompletion=true e dovrà essere completata successivamente.
     *
     * @param name Il nome dell'ubicazione da creare
     * @param onSuccess Callback con il nuovo LocationMatch.Found
     */
    fun createLocationInline(
        name: String,
        onSuccess: (LocationMatch.Found) -> Unit
    ) {
        if (name.isBlank()) return

        _inlineCreationState.update { it.copy(isCreatingLocation = true) }

        viewModelScope.launch {
            try {
                val newLocation = Location(
                    id = "",  // Repository genera UUID
                    name = name.trim(),
                    type = null,
                    parentId = null,
                    floor = null,
                    floorName = null,
                    department = null,
                    building = null,
                    hasOxygenOutlet = false,
                    bedCount = null,
                    address = null,
                    coordinates = null,
                    notes = "Creata da registrazione vocale - da completare",
                    isActive = true,
                    needsCompletion = true
                )

                val id = locationRepository.insert(newLocation)
                val createdLocation = newLocation.copy(id = id)

                Log.d(TAG, "Location created inline: $id - $name")

                _inlineCreationState.update {
                    it.copy(
                        isCreatingLocation = false,
                        locationWasCreatedInline = true,
                        createdLocationId = id,
                        createdLocationName = name
                    )
                }

                onSuccess(LocationMatch.Found(createdLocation))

            } catch (e: Exception) {
                Log.e(TAG, "Failed to create location inline", e)
                _inlineCreationState.update { it.copy(isCreatingLocation = false) }
            }
        }
    }

    /**
     * Crea un fornitore inline con dati minimi.
     * Il fornitore avrà needsCompletion=true e isSupplier=true.
     *
     * @param name Il nome del fornitore da creare
     * @param onSuccess Callback con il nuovo MaintainerMatch.Found
     */
    fun createSupplierInline(
        name: String,
        onSuccess: (MaintainerMatch.Found) -> Unit
    ) {
        if (name.isBlank()) return

        _inlineCreationState.update { it.copy(isCreatingSupplier = true) }

        viewModelScope.launch {
            try {
                val newMaintainer = Maintainer(
                    id = "",  // Repository genera UUID
                    name = name.trim(),
                    email = null,
                    phone = null,
                    address = null,
                    city = null,
                    postalCode = null,
                    province = null,
                    vatNumber = null,
                    contactPerson = null,
                    specialization = null,
                    isSupplier = true,  // È un fornitore
                    notes = "Creato da registrazione vocale - da completare",
                    isActive = true,
                    needsCompletion = true
                )

                val id = maintainerRepository.insert(newMaintainer)
                val createdMaintainer = newMaintainer.copy(id = id)

                Log.d(TAG, "Supplier created inline: $id - $name")

                _inlineCreationState.update {
                    it.copy(
                        isCreatingSupplier = false,
                        supplierWasCreatedInline = true,
                        createdSupplierId = id,
                        createdSupplierName = name
                    )
                }

                onSuccess(MaintainerMatch.Found(createdMaintainer))

            } catch (e: Exception) {
                Log.e(TAG, "Failed to create supplier inline", e)
                _inlineCreationState.update { it.copy(isCreatingSupplier = false) }
            }
        }
    }
}
