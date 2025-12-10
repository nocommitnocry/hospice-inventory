package org.incammino.hospiceinventory.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

/**
 * Entità Manutentore
 * Rappresenta una ditta o persona che effettua manutenzioni sui prodotti.
 * Può essere sia un fornitore (manutentore garanzia) che un riparatore (service).
 */
@Entity(
    tableName = "maintainers",
    indices = [
        Index(value = ["name"]),
        Index(value = ["email"]),
        Index(value = ["isActive"])
    ]
)
data class MaintainerEntity(
    @PrimaryKey
    val id: String,
    
    // ─── ANAGRAFICA ─────────────────────────────────────────────────────────
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val city: String? = null,
    val postalCode: String? = null,
    val province: String? = null,
    val vatNumber: String? = null,
    
    // ─── CONTATTO ───────────────────────────────────────────────────────────
    val contactPerson: String? = null,
    val contactPhone: String? = null,
    val contactEmail: String? = null,
    
    // ─── CLASSIFICAZIONE ────────────────────────────────────────────────────
    val specialization: String? = null,  // es: "IT", "Elettrico", "Idraulico"
    val isSupplier: Boolean = false,     // È anche fornitore?
    
    // ─── NOTE ───────────────────────────────────────────────────────────────
    val notes: String? = null,
    
    // ─── STATO ──────────────────────────────────────────────────────────────
    val isActive: Boolean = true,
    
    // ─── METADATA ───────────────────────────────────────────────────────────
    val createdAt: Instant,
    val updatedAt: Instant
)
