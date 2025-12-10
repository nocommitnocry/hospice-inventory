package org.incammino.hospiceinventory.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.incammino.hospiceinventory.domain.model.AccountType
import org.incammino.hospiceinventory.domain.model.MaintenanceFrequency
import org.incammino.hospiceinventory.domain.model.SyncStatus

/**
 * Entità Prodotto/Asset
 * Rappresenta un singolo item dell'inventario con tutte le sue proprietà,
 * inclusa la gestione dei manutentori (garanzia vs post-garanzia) e
 * le scadenze delle manutenzioni periodiche.
 */
@Entity(
    tableName = "products",
    indices = [
        Index(value = ["barcode"]),
        Index(value = ["category"]),
        Index(value = ["location"]),
        Index(value = ["warrantyMaintainerId"]),
        Index(value = ["serviceMaintainerId"]),
        Index(value = ["nextMaintenanceDue"]),
        Index(value = ["isActive"])
    ]
)
data class ProductEntity(
    @PrimaryKey
    val id: String,
    
    // ─── IDENTIFICAZIONE ────────────────────────────────────────────────────
    val barcode: String? = null,
    val name: String,
    val description: String? = null,
    val category: String,
    val location: String,
    val assigneeId: String? = null,
    
    // ─── GESTIONE MANUTENTORI ───────────────────────────────────────────────
    // Manutentore GARANZIA (fornitore/produttore)
    val warrantyMaintainerId: String? = null,
    val warrantyStartDate: LocalDate? = null,
    val warrantyEndDate: LocalDate? = null,
    
    // Manutentore POST-GARANZIA (riparatore abituale)
    val serviceMaintainerId: String? = null,
    
    // ─── MANUTENZIONE PERIODICA ─────────────────────────────────────────────
    val maintenanceFrequency: MaintenanceFrequency? = null,
    val maintenanceStartDate: LocalDate? = null,
    val maintenanceIntervalDays: Int? = null,  // Per frequenza CUSTOM
    val lastMaintenanceDate: LocalDate? = null,
    val nextMaintenanceDue: LocalDate? = null,
    
    // ─── DATI ACQUISTO ──────────────────────────────────────────────────────
    val purchaseDate: LocalDate? = null,
    val price: Double? = null,
    val accountType: AccountType? = null,
    val supplier: String? = null,
    val invoiceNumber: String? = null,
    
    // ─── ALTRI CAMPI ────────────────────────────────────────────────────────
    val imageUri: String? = null,
    val notes: String? = null,
    val isActive: Boolean = true,
    
    // ─── METADATA ───────────────────────────────────────────────────────────
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)
