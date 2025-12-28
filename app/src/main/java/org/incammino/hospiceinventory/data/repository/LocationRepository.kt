package org.incammino.hospiceinventory.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import org.incammino.hospiceinventory.data.local.dao.LocationDao
import org.incammino.hospiceinventory.data.local.entity.LocationEntity
import org.incammino.hospiceinventory.domain.model.Location
import org.incammino.hospiceinventory.domain.model.LocationType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository per la gestione delle Ubicazioni.
 * Gestisce la gerarchia di sedi (Edificio > Piano > Stanza).
 */
@Singleton
class LocationRepository @Inject constructor(
    private val locationDao: LocationDao
) {
    // ═══════════════════════════════════════════════════════════════════════════
    // QUERY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tutte le ubicazioni attive.
     */
    fun getAllActive(): Flow<List<Location>> =
        locationDao.getAllActive().map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Tutte le ubicazioni attive (sincrono, per EntityResolver).
     */
    suspend fun getAllActiveSync(): List<Location> =
        locationDao.getAllActiveSync().map { it.toDomain() }

    /**
     * Tutte le ubicazioni (incluse inattive).
     */
    fun getAll(): Flow<List<Location>> =
        locationDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Ubicazione per ID (one-shot).
     */
    suspend fun getById(id: String): Location? =
        locationDao.getById(id)?.toDomain()

    /**
     * Ubicazione per ID (Flow per observe).
     */
    fun getByIdFlow(id: String): Flow<Location?> =
        locationDao.getByIdFlow(id).map { it?.toDomain() }

    /**
     * Sedi principali (senza genitore).
     */
    fun getRootLocations(): Flow<List<Location>> =
        locationDao.getRootLocations().map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Sotto-ubicazioni di una sede.
     */
    fun getChildren(parentId: String): Flow<List<Location>> =
        locationDao.getChildren(parentId).map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Conteggio ubicazioni attive.
     */
    suspend fun countActive(): Int = locationDao.countActive()

    /**
     * Conteggio totale ubicazioni.
     */
    suspend fun countAll(): Int = locationDao.countAll()

    // ═══════════════════════════════════════════════════════════════════════════
    // CRUD
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Inserisce una nuova ubicazione.
     */
    suspend fun insert(location: Location): String {
        val now = Clock.System.now()
        val id = location.id.ifEmpty { UUID.randomUUID().toString() }
        val entity = location.toEntity().copy(
            id = id,
            createdAt = now,
            updatedAt = now
        )
        locationDao.insert(entity)
        return id
    }

    /**
     * Aggiorna un'ubicazione esistente.
     */
    suspend fun update(location: Location) {
        val now = Clock.System.now()
        val entity = location.toEntity().copy(
            updatedAt = now
        )
        locationDao.update(entity)
    }

    /**
     * Soft delete.
     */
    suspend fun softDelete(id: String) {
        locationDao.softDelete(id, Clock.System.now())
    }

    /**
     * Delete definitivo.
     */
    suspend fun delete(id: String) {
        locationDao.deleteById(id)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BULK OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Inserisce più ubicazioni (per import).
     */
    suspend fun insertAll(locations: List<Location>) {
        val now = Clock.System.now()
        val entities = locations.map { location ->
            val id = location.id.ifEmpty { UUID.randomUUID().toString() }
            location.toEntity().copy(
                id = id,
                createdAt = now,
                updatedAt = now
            )
        }
        locationDao.insertAll(entities)
    }

    /**
     * Elimina tutte le ubicazioni.
     */
    suspend fun deleteAll() {
        locationDao.deleteAll()
    }

    /**
     * Elimina ubicazioni con ID che corrispondono al pattern.
     */
    suspend fun deleteByIdPattern(pattern: String) {
        locationDao.deleteByIdPattern(pattern)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MAPPING EXTENSIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Converte LocationEntity in Location (domain model).
 * Aggiornato Fase 3 (28/12/2025): mapping gerarchia completa
 */
fun LocationEntity.toDomain(): Location = Location(
    id = id,
    name = name,
    type = type?.let { typeName ->
        LocationType.entries.find { it.name == typeName }
    },
    parentId = parentId,
    floor = floor,
    floorName = floorName,
    department = department,
    building = building,
    hasOxygenOutlet = hasOxygenOutlet,
    bedCount = bedCount,
    address = address,
    coordinates = coordinates,
    notes = notes,
    isActive = isActive,
    needsCompletion = needsCompletion
)

/**
 * Converte Location (domain model) in LocationEntity.
 * Aggiornato Fase 3 (28/12/2025): mapping gerarchia completa
 */
fun Location.toEntity(): LocationEntity = LocationEntity(
    id = id,
    name = name,
    type = type?.name,
    parentId = parentId,
    floor = floor,
    floorName = floorName,
    department = department,
    building = building,
    hasOxygenOutlet = hasOxygenOutlet,
    bedCount = bedCount,
    address = address,
    coordinates = coordinates,
    notes = notes,
    isActive = isActive,
    needsCompletion = needsCompletion,
    createdAt = Clock.System.now(),  // Placeholder, sovrascritto in insert/update
    updatedAt = Clock.System.now()   // Placeholder, sovrascritto in insert/update
)
