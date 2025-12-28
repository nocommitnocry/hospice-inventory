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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.incammino.hospiceinventory.data.repository.MaintenanceRepository
import org.incammino.hospiceinventory.domain.model.Maintenance
import org.incammino.hospiceinventory.domain.model.MaintenanceType
import org.incammino.hospiceinventory.service.voice.MaintainerMatch
import org.incammino.hospiceinventory.service.voice.ProductMatch
import org.incammino.hospiceinventory.service.voice.SaveState
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel per MaintenanceConfirmScreen.
 * Gestisce il salvataggio della manutenzione.
 *
 * Paradigma "Voice Dump + Visual Confirm" (v2.0 - 26/12/2025)
 */
@HiltViewModel
class MaintenanceConfirmViewModel @Inject constructor(
    private val maintenanceRepository: MaintenanceRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MaintenanceConfirmVM"
    }

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

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
    }
}
