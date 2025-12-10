package org.incammino.hospiceinventory.domain.model

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/**
 * Domain model per Prodotto
 * Include la logica di business per determinare il manutentore corrente
 * e lo stato della garanzia.
 */
data class Product(
    val id: String,
    val barcode: String?,
    val name: String,
    val description: String?,
    val category: String,
    val location: String,
    val assigneeId: String?,
    
    // Manutentori
    val warrantyMaintainerId: String?,
    val warrantyStartDate: LocalDate?,
    val warrantyEndDate: LocalDate?,
    val serviceMaintainerId: String?,
    
    // Manutenzione periodica
    val maintenanceFrequency: MaintenanceFrequency?,
    val maintenanceStartDate: LocalDate?,
    val maintenanceIntervalDays: Int?,
    val lastMaintenanceDate: LocalDate?,
    val nextMaintenanceDue: LocalDate?,
    
    // Acquisto
    val purchaseDate: LocalDate?,
    val price: Double?,
    val accountType: AccountType?,
    val supplier: String?,
    val invoiceNumber: String?,
    
    // Altri
    val imageUri: String?,
    val notes: String?,
    val isActive: Boolean
) {
    /**
     * Determina il manutentore da contattare in base allo stato della garanzia.
     * Se in garanzia → warrantyMaintainerId
     * Se fuori garanzia → serviceMaintainerId
     */
    fun getCurrentMaintainerId(): String? {
        return if (isUnderWarranty()) {
            warrantyMaintainerId ?: serviceMaintainerId
        } else {
            serviceMaintainerId ?: warrantyMaintainerId
        }
    }
    
    /**
     * Verifica se il prodotto è ancora in garanzia.
     */
    fun isUnderWarranty(): Boolean {
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        return warrantyEndDate != null && today <= warrantyEndDate
    }
    
    /**
     * Calcola i giorni rimanenti di garanzia.
     * Negativo se scaduta.
     */
    fun warrantyDaysRemaining(): Long? {
        if (warrantyEndDate == null) return null
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        return today.daysUntil(warrantyEndDate).toLong()
    }
    
    /**
     * Descrizione testuale dello stato garanzia.
     */
    fun getWarrantyStatusText(): String {
        if (warrantyEndDate == null) return "Nessuna garanzia"
        
        val daysRemaining = warrantyDaysRemaining() ?: return "Nessuna garanzia"
        
        return when {
            daysRemaining < 0 -> "Garanzia scaduta da ${-daysRemaining} giorni"
            daysRemaining == 0L -> "Garanzia scade oggi!"
            daysRemaining <= 30 -> "Garanzia scade tra $daysRemaining giorni"
            else -> "In garanzia fino al $warrantyEndDate"
        }
    }
    
    /**
     * Calcola i giorni alla prossima manutenzione.
     * Negativo se scaduta.
     */
    fun maintenanceDaysRemaining(): Long? {
        if (nextMaintenanceDue == null) return null
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        return today.daysUntil(nextMaintenanceDue).toLong()
    }
    
    /**
     * Verifica se la manutenzione è scaduta.
     */
    fun isMaintenanceOverdue(): Boolean {
        val days = maintenanceDaysRemaining() ?: return false
        return days < 0
    }
    
    /**
     * Descrizione testuale dello stato manutenzione.
     */
    fun getMaintenanceStatusText(): String {
        if (maintenanceFrequency == null) return "Nessuna manutenzione programmata"
        if (nextMaintenanceDue == null) return "Scadenza non calcolata"
        
        val daysRemaining = maintenanceDaysRemaining() ?: return "Errore calcolo"
        
        return when {
            daysRemaining < 0 -> "Manutenzione SCADUTA da ${-daysRemaining} giorni"
            daysRemaining == 0L -> "Manutenzione scade OGGI"
            daysRemaining <= 7 -> "Manutenzione tra $daysRemaining giorni"
            daysRemaining <= 30 -> "Manutenzione tra $daysRemaining giorni"
            else -> "Prossima manutenzione: $nextMaintenanceDue"
        }
    }
}

/**
 * Domain model per Manutentore
 */
data class Maintainer(
    val id: String,
    val name: String,
    val email: String?,
    val phone: String?,
    val address: String?,
    val city: String?,
    val postalCode: String?,
    val province: String?,
    val vatNumber: String?,
    val contactPerson: String?,
    val specialization: String?,
    val isSupplier: Boolean,
    val notes: String?,
    val isActive: Boolean
) {
    /**
     * Indirizzo completo formattato.
     */
    fun getFullAddress(): String? {
        val parts = listOfNotNull(
            address,
            listOfNotNull(postalCode, city).joinToString(" ").takeIf { it.isNotBlank() },
            province?.let { "($it)" }
        )
        return parts.joinToString(", ").takeIf { it.isNotBlank() }
    }
    
    /**
     * Contatto principale (email o telefono).
     */
    fun getPrimaryContact(): String? {
        return email ?: phone
    }
}

/**
 * Domain model per Manutenzione
 */
data class Maintenance(
    val id: String,
    val productId: String,
    val maintainerId: String?,
    val date: kotlinx.datetime.Instant,
    val type: MaintenanceType,
    val outcome: MaintenanceOutcome?,
    val notes: String?,
    val cost: Double?,
    val invoiceNumber: String?,
    val isWarrantyWork: Boolean,
    val requestEmailSent: Boolean,
    val reportEmailSent: Boolean
)

/**
 * Prodotto con informazioni aggregate (per le liste).
 */
data class ProductWithDetails(
    val product: Product,
    val warrantyMaintainer: Maintainer?,
    val serviceMaintainer: Maintainer?,
    val assignee: Assignee?,
    val lastMaintenance: Maintenance?,
    val maintenanceCount: Int,
    val pendingAlerts: Int
)

/**
 * Domain model per Assegnatario
 */
data class Assignee(
    val id: String,
    val name: String,
    val department: String?,
    val phone: String?,
    val email: String?,
    val isActive: Boolean
)

/**
 * Domain model per Location
 */
data class Location(
    val id: String,
    val name: String,
    val parentId: String?,
    val address: String?,
    val coordinates: String?,
    val isActive: Boolean
)

/**
 * Alert di manutenzione per la UI
 */
data class MaintenanceAlert(
    val id: String,
    val product: Product,
    val alertType: AlertType,
    val dueDate: LocalDate,
    val daysRemaining: Long
)
