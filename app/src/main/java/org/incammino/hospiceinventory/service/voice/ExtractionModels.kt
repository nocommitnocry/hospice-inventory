package org.incammino.hospiceinventory.service.voice

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.incammino.hospiceinventory.domain.model.MaintenanceType
import org.incammino.hospiceinventory.domain.model.Maintainer
import org.incammino.hospiceinventory.domain.model.Product

/**
 * Modelli per l'estrazione dati da voice dump.
 * Paradigma Voice Dump + Visual Confirm (v2.0 - 26/12/2025)
 */

// ==================== MANUTENZIONE ====================

/**
 * Risultato dell'estrazione dati manutenzione da testo vocale.
 * Viene parsato dal JSON restituito da Gemini.
 */
@Serializable
data class MaintenanceExtraction(
    val maintainer: MaintainerInfo?,
    val product: ProductSearchInfo,
    val intervention: InterventionInfo,
    val confidence: ConfidenceInfo
)

@Serializable
data class MaintainerInfo(
    val name: String? = null,
    val company: String? = null
)

@Serializable
data class ProductSearchInfo(
    val searchTerms: List<String> = emptyList(),
    val locationHint: String? = null
)

@Serializable
data class InterventionInfo(
    val type: String? = null,  // ORDINARY, EXTRAORDINARY, VERIFICATION, INSTALLATION, DISPOSAL
    val description: String? = null,
    val durationMinutes: Int? = null,
    val isWarranty: Boolean? = null,
    val date: String? = null   // "YYYY-MM-DD" o null per oggi
)

@Serializable
data class ConfidenceInfo(
    val overall: Float = 0f,
    val missingFields: List<String> = emptyList()
)

// ==================== PRODOTTO ====================

/**
 * Risultato dell'estrazione dati prodotto da testo vocale.
 * Viene parsato dal JSON restituito da Gemini.
 */
@Serializable
data class ProductExtraction(
    val product: ProductInfoExtraction,
    val location: LocationSearchInfo,
    val supplier: SupplierInfoExtraction?,
    val warranty: WarrantyInfoExtraction?,
    val maintenance: MaintenanceScheduleInfo?,
    val confidence: ConfidenceInfo
)

@Serializable
data class ProductInfoExtraction(
    val name: String? = null,
    val model: String? = null,
    val manufacturer: String? = null,
    val serialNumber: String? = null,
    val barcode: String? = null,
    val category: String? = null
)

@Serializable
data class LocationSearchInfo(
    val searchTerms: List<String> = emptyList(),
    val floor: String? = null,
    val department: String? = null
)

@Serializable
data class SupplierInfoExtraction(
    val name: String? = null,
    val isAlsoMaintainer: Boolean? = null
)

@Serializable
data class WarrantyInfoExtraction(
    val months: Int? = null,
    val maintainerName: String? = null
)

@Serializable
data class MaintenanceScheduleInfo(
    val frequencyMonths: Int? = null,
    val notes: String? = null
)

// ==================== STATI UI ====================

/**
 * Stati per VoiceMaintenanceViewModel.
 */
sealed class VoiceMaintenanceState {
    /** Pronto per iniziare */
    data object Idle : VoiceMaintenanceState()

    /** Microfono attivo, in ascolto */
    data object Listening : VoiceMaintenanceState()

    /** Mostra testo parziale durante ascolto */
    data class Transcribing(val partialText: String) : VoiceMaintenanceState()

    /** Gemini sta elaborando */
    data object Processing : VoiceMaintenanceState()

    /** Estrazione completata, pronto per conferma */
    data class Extracted(val data: MaintenanceConfirmData) : VoiceMaintenanceState()

    /** Errore */
    data class Error(val message: String) : VoiceMaintenanceState()
}

/**
 * Dati per la schermata di conferma, già risolti con EntityResolver.
 */
data class MaintenanceConfirmData(
    // Prodotto
    val productMatch: ProductMatch,

    // Manutentore
    val maintainerMatch: MaintainerMatch,

    // Dati intervento
    val type: MaintenanceType?,
    val description: String,
    val durationMinutes: Int?,
    val isWarranty: Boolean,
    val date: LocalDate,

    // Metadata
    val confidence: Float,
    val warnings: List<String>
)

/**
 * Risultato match prodotto.
 */
sealed class ProductMatch {
    data class Found(val product: Product) : ProductMatch()
    data class Ambiguous(val candidates: List<Product>, val searchTerms: String) : ProductMatch()
    data class NotFound(val searchTerms: String) : ProductMatch()
}

/**
 * Risultato match manutentore.
 */
sealed class MaintainerMatch {
    data class Found(val maintainer: Maintainer) : MaintainerMatch()
    data class Ambiguous(val candidates: List<Maintainer>, val query: String) : MaintainerMatch()
    data class NotFound(val name: String, val company: String?) : MaintainerMatch()
    /** L'utente parla in prima persona - è lui il manutentore */
    data object SelfReported : MaintainerMatch()
}

// ==================== STATI UI PRODOTTO ====================

/**
 * Stati per VoiceProductViewModel.
 */
sealed class VoiceProductState {
    /** Pronto per iniziare */
    data object Idle : VoiceProductState()

    /** Microfono attivo, in ascolto */
    data object Listening : VoiceProductState()

    /** Mostra testo parziale durante ascolto */
    data class Transcribing(val partialText: String) : VoiceProductState()

    /** Gemini sta elaborando */
    data object Processing : VoiceProductState()

    /** Estrazione completata, pronto per conferma */
    data class Extracted(val data: ProductConfirmData) : VoiceProductState()

    /** Errore */
    data class Error(val message: String) : VoiceProductState()
}

/**
 * Dati per la schermata di conferma prodotto, già risolti con EntityResolver.
 */
data class ProductConfirmData(
    // Dati prodotto
    val name: String,
    val model: String,
    val manufacturer: String,
    val serialNumber: String,
    val barcode: String,
    val category: String,

    // Ubicazione
    val locationMatch: LocationMatch,

    // Fornitore
    val supplierMatch: MaintainerMatch,

    // Garanzia
    val warrantyMonths: Int?,
    val warrantyMaintainerMatch: MaintainerMatch?,

    // Manutenzione programmata
    val maintenanceFrequencyMonths: Int?,

    // Metadata
    val confidence: Float,
    val warnings: List<String>
)

/**
 * Risultato match ubicazione.
 */
sealed class LocationMatch {
    data class Found(val location: org.incammino.hospiceinventory.domain.model.Location) : LocationMatch()
    data class Ambiguous(val candidates: List<org.incammino.hospiceinventory.domain.model.Location>, val searchTerms: String) : LocationMatch()
    data class NotFound(val searchTerms: String) : LocationMatch()
}

// ==================== STATI SALVATAGGIO ====================

/**
 * Stati per MaintenanceConfirmViewModel durante il salvataggio.
 */
sealed class SaveState {
    data object Idle : SaveState()
    data object Saving : SaveState()
    data object Success : SaveState()
    data class Error(val message: String) : SaveState()
}
