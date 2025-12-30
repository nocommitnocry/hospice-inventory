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

// ==================== MANUTENTORE ====================

/**
 * Risultato dell'estrazione dati manutentore da testo vocale.
 * Fase 3 (28/12/2025)
 */
@Serializable
data class MaintainerExtraction(
    val company: MaintainerCompanyInfo,
    val contact: MaintainerContactInfo?,
    val address: MaintainerAddressInfo?,
    val business: MaintainerBusinessInfo?,
    val confidence: ConfidenceInfo
)

@Serializable
data class MaintainerCompanyInfo(
    val name: String? = null,
    val vatNumber: String? = null,   // Partita IVA
    val specialization: String? = null
)

@Serializable
data class MaintainerContactInfo(
    val email: String? = null,
    val phone: String? = null,
    val contactPerson: String? = null
)

@Serializable
data class MaintainerAddressInfo(
    val street: String? = null,
    val city: String? = null,
    val postalCode: String? = null,
    val province: String? = null
)

@Serializable
data class MaintainerBusinessInfo(
    val isSupplier: Boolean? = null,
    val notes: String? = null
)

// ==================== UBICAZIONE ====================

/**
 * Risultato dell'estrazione dati ubicazione da testo vocale.
 * Fase 3 (28/12/2025)
 */
@Serializable
data class LocationExtraction(
    val location: LocationInfoExtraction,
    val hierarchy: LocationHierarchyInfo?,
    val details: LocationDetailsInfo?,
    val confidence: ConfidenceInfo
)

@Serializable
data class LocationInfoExtraction(
    val name: String? = null,
    val type: String? = null   // BUILDING, FLOOR, ROOM, CORRIDOR, STORAGE, TECHNICAL, OFFICE, COMMON_AREA, EXTERNAL
)

@Serializable
data class LocationHierarchyInfo(
    val buildingName: String? = null,
    val floorCode: String? = null,      // PT, P1, P-1, etc.
    val floorName: String? = null,      // Piano Terra, Primo Piano
    val department: String? = null      // Degenza, Ambulatorio, etc.
)

@Serializable
data class LocationDetailsInfo(
    val hasOxygenOutlet: Boolean? = null,
    val bedCount: Int? = null,
    val notes: String? = null
)

// ==================== STATI UI MANUTENTORE ====================

/**
 * Stati per VoiceMaintainerViewModel.
 * Fase 3 (28/12/2025)
 */
sealed class VoiceMaintainerState {
    /** Pronto per iniziare */
    data object Idle : VoiceMaintainerState()

    /** Microfono attivo, in ascolto */
    data object Listening : VoiceMaintainerState()

    /** Mostra testo parziale durante ascolto */
    data class Transcribing(val partialText: String) : VoiceMaintainerState()

    /** Gemini sta elaborando */
    data object Processing : VoiceMaintainerState()

    /** Estrazione completata, pronto per conferma */
    data class Extracted(val data: MaintainerConfirmData) : VoiceMaintainerState()

    /** Errore */
    data class Error(val message: String) : VoiceMaintainerState()
}

/**
 * Dati per la schermata di conferma manutentore.
 * Fase 3 (28/12/2025)
 */
data class MaintainerConfirmData(
    // Dati azienda
    val name: String,
    val vatNumber: String,
    val specialization: String,

    // Contatti
    val email: String,
    val phone: String,
    val contactPerson: String,

    // Indirizzo
    val street: String,
    val city: String,
    val postalCode: String,
    val province: String,

    // Business
    val isSupplier: Boolean,
    val notes: String,

    // Metadata
    val confidence: Float,
    val warnings: List<String>
)

// ==================== STATI UI UBICAZIONE ====================

/**
 * Stati per VoiceLocationViewModel.
 * Fase 3 (28/12/2025)
 */
sealed class VoiceLocationState {
    /** Pronto per iniziare */
    data object Idle : VoiceLocationState()

    /** Microfono attivo, in ascolto */
    data object Listening : VoiceLocationState()

    /** Mostra testo parziale durante ascolto */
    data class Transcribing(val partialText: String) : VoiceLocationState()

    /** Gemini sta elaborando */
    data object Processing : VoiceLocationState()

    /** Estrazione completata, pronto per conferma */
    data class Extracted(val data: LocationConfirmData) : VoiceLocationState()

    /** Errore */
    data class Error(val message: String) : VoiceLocationState()
}

/**
 * Dati per la schermata di conferma ubicazione.
 * Fase 3 (28/12/2025)
 */
data class LocationConfirmData(
    // Identificazione
    val name: String,
    val type: String,

    // Gerarchia
    val buildingName: String,
    val floorCode: String,
    val floorName: String,
    val department: String,

    // Dettagli
    val hasOxygenOutlet: Boolean,
    val bedCount: Int?,
    val notes: String,

    // Metadata
    val confidence: Float,
    val warnings: List<String>
)

// ==================== FORM DATA PER VOICE CONTINUE ====================

/**
 * Stato attuale del form prodotto per context vocale.
 * Usato da VoiceContinueButton per preservare dati inseriti manualmente.
 */
data class ProductFormData(
    val name: String = "",
    val model: String = "",
    val manufacturer: String = "",
    val serialNumber: String = "",
    val barcode: String = "",
    val category: String = "",
    val location: String = "",
    val supplier: String = "",
    val warrantyMonths: Int? = null,
    val maintenanceFrequencyMonths: Int? = null,
    val notes: String = ""
)

/**
 * Stato attuale del form manutenzione per context vocale.
 * Usato da VoiceContinueButton per preservare dati inseriti manualmente.
 */
data class MaintenanceFormData(
    val productName: String = "",
    val maintainerName: String = "",
    val type: String = "",
    val description: String = "",
    val durationMinutes: Int? = null,
    val isWarranty: Boolean = false,
    val date: String = "",
    val notes: String = ""
)

/**
 * Stato attuale del form manutentore per context vocale.
 * Usato da VoiceContinueButton per preservare dati inseriti manualmente.
 */
data class MaintainerFormData(
    val name: String = "",
    val vatNumber: String = "",
    val specialization: String = "",
    val email: String = "",
    val phone: String = "",
    val contactPerson: String = "",
    val street: String = "",
    val city: String = "",
    val postalCode: String = "",
    val province: String = "",
    val isSupplier: Boolean = false,
    val notes: String = ""
)

/**
 * Stato attuale del form ubicazione per context vocale.
 * Usato da VoiceContinueButton per preservare dati inseriti manualmente.
 */
data class LocationFormData(
    val name: String = "",
    val type: String = "",
    val buildingName: String = "",
    val floorCode: String = "",
    val floorName: String = "",
    val department: String = "",
    val hasOxygenOutlet: Boolean = false,
    val bedCount: Int? = null,
    val notes: String = ""
)

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
