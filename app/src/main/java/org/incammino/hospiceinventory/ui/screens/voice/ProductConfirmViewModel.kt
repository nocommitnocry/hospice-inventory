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
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.incammino.hospiceinventory.data.repository.ProductRepository
import org.incammino.hospiceinventory.domain.model.MaintenanceFrequency
import org.incammino.hospiceinventory.domain.model.Product
import org.incammino.hospiceinventory.service.voice.LocationMatch
import org.incammino.hospiceinventory.service.voice.MaintainerMatch
import org.incammino.hospiceinventory.service.voice.SaveState
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel per ProductConfirmScreen.
 * Gestisce il salvataggio del prodotto.
 *
 * Paradigma "Voice Dump + Visual Confirm" (v2.0 - Fase 2)
 */
@HiltViewModel
class ProductConfirmViewModel @Inject constructor(
    private val productRepository: ProductRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ProductConfirmVM"
    }

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

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
    }
}
