package org.incammino.hospiceinventory.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import org.incammino.hospiceinventory.data.local.dao.AssigneeDao
import org.incammino.hospiceinventory.data.local.entity.AssigneeEntity
import org.incammino.hospiceinventory.domain.model.Assignee
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository per la gestione degli Assegnatari/Responsabili.
 * Gestisce persone o reparti responsabili di prodotti.
 */
@Singleton
class AssigneeRepository @Inject constructor(
    private val assigneeDao: AssigneeDao
) {
    // ═══════════════════════════════════════════════════════════════════════════
    // QUERY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tutti gli assegnatari attivi.
     */
    fun getAllActive(): Flow<List<Assignee>> =
        assigneeDao.getAllActive().map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Tutti gli assegnatari attivi (sincrono, per EntityResolver).
     */
    suspend fun getAllActiveSync(): List<Assignee> =
        assigneeDao.getAllActiveSync().map { it.toDomain() }

    /**
     * Assegnatario per ID (one-shot).
     */
    suspend fun getById(id: String): Assignee? =
        assigneeDao.getById(id)?.toDomain()

    /**
     * Assegnatari per dipartimento.
     */
    fun getByDepartment(department: String): Flow<List<Assignee>> =
        assigneeDao.getByDepartment(department).map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Lista dipartimenti unici.
     */
    fun getAllDepartments(): Flow<List<String>> =
        assigneeDao.getAllDepartments()

    // ═══════════════════════════════════════════════════════════════════════════
    // CRUD
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Inserisce un nuovo assegnatario.
     */
    suspend fun insert(assignee: Assignee): String {
        val now = Clock.System.now()
        val id = assignee.id.ifEmpty { UUID.randomUUID().toString() }
        val entity = assignee.toEntity().copy(
            id = id,
            createdAt = now,
            updatedAt = now
        )
        assigneeDao.insert(entity)
        return id
    }

    /**
     * Aggiorna un assegnatario esistente.
     */
    suspend fun update(assignee: Assignee) {
        val now = Clock.System.now()
        val entity = assignee.toEntity().copy(
            updatedAt = now
        )
        assigneeDao.update(entity)
    }

    /**
     * Delete definitivo.
     */
    suspend fun delete(assignee: Assignee) {
        assigneeDao.delete(assignee.toEntity())
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BULK OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Inserisce più assegnatari (per import).
     */
    suspend fun insertAll(assignees: List<Assignee>) {
        val now = Clock.System.now()
        val entities = assignees.map { assignee ->
            val id = assignee.id.ifEmpty { UUID.randomUUID().toString() }
            assignee.toEntity().copy(
                id = id,
                createdAt = now,
                updatedAt = now
            )
        }
        assigneeDao.insertAll(entities)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MAPPING EXTENSIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Converte AssigneeEntity in Assignee (domain model).
 */
fun AssigneeEntity.toDomain(): Assignee = Assignee(
    id = id,
    name = name,
    department = department,
    phone = phone,
    email = email,
    isActive = isActive
)

/**
 * Converte Assignee (domain model) in AssigneeEntity.
 */
fun Assignee.toEntity(): AssigneeEntity = AssigneeEntity(
    id = id,
    name = name,
    department = department,
    phone = phone,
    email = email,
    notes = null,
    isActive = isActive,
    createdAt = Clock.System.now(),  // Placeholder
    updatedAt = Clock.System.now()   // Placeholder
)
