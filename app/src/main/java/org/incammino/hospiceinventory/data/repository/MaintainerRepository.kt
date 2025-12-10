package org.incammino.hospiceinventory.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import org.incammino.hospiceinventory.data.local.dao.MaintainerDao
import org.incammino.hospiceinventory.data.local.entity.MaintainerEntity
import org.incammino.hospiceinventory.domain.model.Maintainer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository per la gestione dei Manutentori.
 * Gestisce sia i fornitori (garanzia) che i riparatori (service).
 */
@Singleton
class MaintainerRepository @Inject constructor(
    private val maintainerDao: MaintainerDao
) {
    // ═══════════════════════════════════════════════════════════════════════════
    // QUERY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tutti i manutentori attivi.
     */
    fun getAllActive(): Flow<List<Maintainer>> =
        maintainerDao.getAllActive().map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Tutti i manutentori (inclusi inattivi).
     */
    fun getAll(): Flow<List<Maintainer>> =
        maintainerDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Manutentore per ID (one-shot).
     */
    suspend fun getById(id: String): Maintainer? =
        maintainerDao.getById(id)?.toDomain()

    /**
     * Manutentore per ID (Flow per observe).
     */
    fun getByIdFlow(id: String): Flow<Maintainer?> =
        maintainerDao.getByIdFlow(id).map { it?.toDomain() }

    /**
     * Ricerca per nome o specializzazione.
     */
    fun search(query: String): Flow<List<Maintainer>> =
        maintainerDao.search(query).map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Solo i fornitori (usati per garanzia).
     */
    fun getSuppliers(): Flow<List<Maintainer>> =
        maintainerDao.getSuppliers().map { entities ->
            entities.map { it.toDomain() }
        }

    // ═══════════════════════════════════════════════════════════════════════════
    // CRUD
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Inserisce un nuovo manutentore.
     */
    suspend fun insert(maintainer: Maintainer): String {
        val now = Clock.System.now()
        val id = maintainer.id.ifEmpty { UUID.randomUUID().toString() }
        val entity = maintainer.toEntity().copy(
            id = id,
            createdAt = now,
            updatedAt = now
        )
        maintainerDao.insert(entity)
        return id
    }

    /**
     * Aggiorna un manutentore esistente.
     */
    suspend fun update(maintainer: Maintainer) {
        val now = Clock.System.now()
        val entity = maintainer.toEntity().copy(
            updatedAt = now
        )
        maintainerDao.update(entity)
    }

    /**
     * Soft delete.
     */
    suspend fun softDelete(id: String) {
        maintainerDao.softDelete(id, Clock.System.now())
    }

    /**
     * Delete definitivo.
     */
    suspend fun delete(maintainer: Maintainer) {
        maintainerDao.delete(maintainer.toEntity())
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BULK OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Inserisce più manutentori (per import).
     */
    suspend fun insertAll(maintainers: List<Maintainer>) {
        val now = Clock.System.now()
        val entities = maintainers.map { maintainer ->
            val id = maintainer.id.ifEmpty { UUID.randomUUID().toString() }
            maintainer.toEntity().copy(
                id = id,
                createdAt = now,
                updatedAt = now
            )
        }
        maintainerDao.insertAll(entities)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MAPPING EXTENSIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Converte MaintainerEntity in Maintainer (domain model).
 */
fun MaintainerEntity.toDomain(): Maintainer = Maintainer(
    id = id,
    name = name,
    email = email,
    phone = phone,
    address = address,
    city = city,
    postalCode = postalCode,
    province = province,
    vatNumber = vatNumber,
    contactPerson = contactPerson,
    specialization = specialization,
    isSupplier = isSupplier,
    notes = notes,
    isActive = isActive
)

/**
 * Converte Maintainer (domain model) in MaintainerEntity.
 */
fun Maintainer.toEntity(): MaintainerEntity = MaintainerEntity(
    id = id,
    name = name,
    email = email,
    phone = phone,
    address = address,
    city = city,
    postalCode = postalCode,
    province = province,
    vatNumber = vatNumber,
    contactPerson = contactPerson,
    contactPhone = null,  // Non presente nel domain model
    contactEmail = null,  // Non presente nel domain model
    specialization = specialization,
    isSupplier = isSupplier,
    notes = notes,
    isActive = isActive,
    createdAt = Clock.System.now(),  // Placeholder
    updatedAt = Clock.System.now()   // Placeholder
)
