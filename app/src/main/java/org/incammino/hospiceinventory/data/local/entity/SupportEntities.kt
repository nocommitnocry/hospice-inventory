package org.incammino.hospiceinventory.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.incammino.hospiceinventory.domain.model.AlertType
import org.incammino.hospiceinventory.domain.model.EmailStatus

/**
 * Entità Sede/Ubicazione
 * Rappresenta un luogo fisico dove possono essere collocati i prodotti.
 * Supporta una gerarchia (es: Edificio > Piano > Stanza).
 */
@Entity(
    tableName = "locations",
    indices = [
        Index(value = ["name"]),
        Index(value = ["parentId"]),
        Index(value = ["isActive"])
    ]
)
data class LocationEntity(
    @PrimaryKey
    val id: String,
    
    val name: String,
    val parentId: String? = null,  // Per gerarchia (Piano > Stanza)
    val address: String? = null,
    val coordinates: String? = null,  // GEO per future espansioni
    val notes: String? = null,
    val isActive: Boolean = true,
    
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Entità Assegnatario/Responsabile
 * Rappresenta una persona o reparto responsabile di uno o più prodotti.
 */
@Entity(
    tableName = "assignees",
    indices = [
        Index(value = ["name"]),
        Index(value = ["department"]),
        Index(value = ["isActive"])
    ]
)
data class AssigneeEntity(
    @PrimaryKey
    val id: String,
    
    val name: String,
    val department: String? = null,  // Direzione, Reparto, UCP-DOM, etc.
    val phone: String? = null,
    val email: String? = null,
    val notes: String? = null,
    val isActive: Boolean = true,
    
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Entità Coda Email
 * Gestisce le email in attesa di invio (per funzionamento offline).
 */
@Entity(
    tableName = "email_queue",
    indices = [
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class EmailQueueEntity(
    @PrimaryKey
    val id: String,
    
    // ─── DESTINATARI ────────────────────────────────────────────────────────
    val toAddress: String,
    val ccAddress: String? = null,
    
    // ─── CONTENUTO ──────────────────────────────────────────────────────────
    val subject: String,
    val body: String,
    val attachmentUri: String? = null,
    
    // ─── RIFERIMENTI ────────────────────────────────────────────────────────
    val relatedProductId: String? = null,
    val relatedMaintenanceId: String? = null,
    
    // ─── STATO INVIO ────────────────────────────────────────────────────────
    val status: EmailStatus = EmailStatus.PENDING,
    val retryCount: Int = 0,
    val errorMessage: String? = null,
    
    // ─── TIMESTAMP ──────────────────────────────────────────────────────────
    val createdAt: Instant,
    val sentAt: Instant? = null
)

/**
 * Entità Alert Manutenzione
 * Gestisce le notifiche per le scadenze delle manutenzioni periodiche.
 */
@Entity(
    tableName = "maintenance_alerts",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["productId"]),
        Index(value = ["alertType"]),
        Index(value = ["dueDate"]),
        Index(value = ["notifiedAt"]),
        Index(value = ["completedAt"])
    ]
)
data class MaintenanceAlertEntity(
    @PrimaryKey
    val id: String,
    
    val productId: String,
    val alertType: AlertType,
    val dueDate: LocalDate,
    
    // ─── STATO NOTIFICA ─────────────────────────────────────────────────────
    val notifiedAt: Instant? = null,      // Quando è stata mostrata la notifica
    val dismissedAt: Instant? = null,     // Se l'utente ha ignorato
    val completedAt: Instant? = null,     // Se la manutenzione è stata eseguita
    
    val createdAt: Instant
)
