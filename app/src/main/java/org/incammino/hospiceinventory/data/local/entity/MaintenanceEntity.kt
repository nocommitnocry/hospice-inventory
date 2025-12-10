package org.incammino.hospiceinventory.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import org.incammino.hospiceinventory.domain.model.MaintenanceOutcome
import org.incammino.hospiceinventory.domain.model.MaintenanceType
import org.incammino.hospiceinventory.domain.model.SyncStatus

/**
 * Entità Manutenzione
 * Rappresenta un singolo intervento di manutenzione su un prodotto.
 */
@Entity(
    tableName = "maintenances",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MaintainerEntity::class,
            parentColumns = ["id"],
            childColumns = ["maintainerId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["productId"]),
        Index(value = ["maintainerId"]),
        Index(value = ["date"]),
        Index(value = ["type"]),
        Index(value = ["outcome"])
    ]
)
data class MaintenanceEntity(
    @PrimaryKey
    val id: String,
    
    // ─── RIFERIMENTI ────────────────────────────────────────────────────────
    val productId: String,
    val maintainerId: String? = null,
    
    // ─── DETTAGLI INTERVENTO ────────────────────────────────────────────────
    val date: Instant,
    val type: MaintenanceType,
    val outcome: MaintenanceOutcome? = null,
    val notes: String? = null,
    
    // ─── COSTI ──────────────────────────────────────────────────────────────
    val cost: Double? = null,
    val invoiceNumber: String? = null,
    val isWarrantyWork: Boolean = false,
    
    // ─── EMAIL TRACKING ─────────────────────────────────────────────────────
    val requestEmailSent: Boolean = false,
    val requestEmailDate: Instant? = null,
    val reportEmailSent: Boolean = false,
    val reportEmailDate: Instant? = null,
    
    // ─── METADATA ───────────────────────────────────────────────────────────
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)
