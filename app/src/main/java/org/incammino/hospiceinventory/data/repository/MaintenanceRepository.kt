package org.incammino.hospiceinventory.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.incammino.hospiceinventory.data.local.dao.MaintenanceDao
import org.incammino.hospiceinventory.data.local.dao.ProductDao
import org.incammino.hospiceinventory.data.local.entity.MaintenanceEntity
import org.incammino.hospiceinventory.domain.model.Maintenance
import org.incammino.hospiceinventory.domain.model.MaintenanceFrequency
import org.incammino.hospiceinventory.domain.model.MaintenanceType
import org.incammino.hospiceinventory.domain.model.SyncStatus
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository per la gestione delle Manutenzioni.
 * Gestisce il ciclo di vita degli interventi e l'aggiornamento
 * delle date di scadenza sui prodotti.
 */
@Singleton
class MaintenanceRepository @Inject constructor(
    private val maintenanceDao: MaintenanceDao,
    private val productDao: ProductDao
) {
    // ═══════════════════════════════════════════════════════════════════════════
    // QUERY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tutte le manutenzioni (ordinate per data decrescente).
     */
    fun getAll(): Flow<List<Maintenance>> =
        maintenanceDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Manutenzione per ID.
     */
    suspend fun getById(id: String): Maintenance? =
        maintenanceDao.getById(id)?.toDomain()

    /**
     * Manutenzioni di un prodotto (storico).
     */
    fun getByProduct(productId: String): Flow<List<Maintenance>> =
        maintenanceDao.getByProduct(productId).map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Ultima manutenzione di un prodotto.
     */
    suspend fun getLastByProduct(productId: String): Maintenance? =
        maintenanceDao.getLastByProduct(productId)?.toDomain()

    /**
     * Manutenzioni effettuate da un manutentore.
     */
    fun getByMaintainer(maintainerId: String): Flow<List<Maintenance>> =
        maintenanceDao.getByMaintainer(maintainerId).map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Manutenzioni in un range di date.
     */
    fun getByDateRange(startDate: Instant, endDate: Instant): Flow<List<Maintenance>> =
        maintenanceDao.getByDateRange(startDate, endDate).map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Manutenzioni per tipo.
     */
    fun getByType(type: MaintenanceType): Flow<List<Maintenance>> =
        maintenanceDao.getByType(type).map { entities ->
            entities.map { it.toDomain() }
        }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATISTICHE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Conteggio totale manutenzioni.
     */
    suspend fun count(): Int = maintenanceDao.count()

    /**
     * Conteggio manutenzioni di un prodotto.
     */
    suspend fun countByProduct(productId: String): Int =
        maintenanceDao.countByProduct(productId)

    /**
     * Conteggio manutenzioni in un periodo.
     */
    suspend fun countByDateRange(startDate: Instant, endDate: Instant): Int =
        maintenanceDao.countByDateRange(startDate, endDate)

    // ═══════════════════════════════════════════════════════════════════════════
    // CRUD
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Inserisce una nuova manutenzione.
     * Aggiorna automaticamente le date sul prodotto se necessario.
     */
    suspend fun insert(maintenance: Maintenance, updateProductDates: Boolean = true): String {
        val now = Clock.System.now()
        val id = maintenance.id.ifEmpty { UUID.randomUUID().toString() }

        val entity = maintenance.toEntity().copy(
            id = id,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING
        )
        maintenanceDao.insert(entity)

        // Aggiorna le date di manutenzione sul prodotto
        if (updateProductDates) {
            updateProductMaintenanceDates(maintenance.productId, maintenance.date)
        }

        return id
    }

    /**
     * Aggiorna una manutenzione esistente.
     */
    suspend fun update(maintenance: Maintenance) {
        val now = Clock.System.now()
        val entity = maintenance.toEntity().copy(
            updatedAt = now,
            syncStatus = SyncStatus.PENDING
        )
        maintenanceDao.update(entity)
    }

    /**
     * Elimina una manutenzione.
     */
    suspend fun delete(maintenance: Maintenance) {
        maintenanceDao.delete(maintenance.toEntity())
    }

    /**
     * Elimina tutte le manutenzioni di un prodotto.
     */
    suspend fun deleteByProduct(productId: String) {
        maintenanceDao.deleteByProduct(productId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BUSINESS LOGIC
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Aggiorna le date di manutenzione sul prodotto dopo un intervento.
     * Calcola la prossima scadenza in base alla frequenza configurata.
     */
    private suspend fun updateProductMaintenanceDates(productId: String, maintenanceDate: Instant) {
        val product = productDao.getById(productId) ?: return

        val maintenanceLocalDate = maintenanceDate
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date

        // Calcola la prossima scadenza
        val nextDue = product.maintenanceFrequency?.let { frequency ->
            calculateNextMaintenanceDue(maintenanceLocalDate, frequency, product.maintenanceIntervalDays)
        }

        productDao.updateMaintenanceDates(
            productId = productId,
            date = maintenanceLocalDate,
            nextDue = nextDue,
            updatedAt = Clock.System.now()
        )
    }

    /**
     * Calcola la prossima data di scadenza manutenzione.
     */
    private fun calculateNextMaintenanceDue(
        lastDate: LocalDate,
        frequency: MaintenanceFrequency,
        customDays: Int?
    ): LocalDate {
        val days = when (frequency) {
            MaintenanceFrequency.CUSTOM -> customDays ?: 365
            else -> frequency.days
        }
        return lastDate.plus(DatePeriod(days = days))
    }

    /**
     * Registra l'invio dell'email di richiesta intervento.
     */
    suspend fun markRequestEmailSent(maintenanceId: String) {
        val maintenance = maintenanceDao.getById(maintenanceId) ?: return
        val updated = maintenance.copy(
            requestEmailSent = true,
            requestEmailDate = Clock.System.now(),
            updatedAt = Clock.System.now(),
            syncStatus = SyncStatus.PENDING
        )
        maintenanceDao.update(updated)
    }

    /**
     * Registra l'invio dell'email di report intervento.
     */
    suspend fun markReportEmailSent(maintenanceId: String) {
        val maintenance = maintenanceDao.getById(maintenanceId) ?: return
        val updated = maintenance.copy(
            reportEmailSent = true,
            reportEmailDate = Clock.System.now(),
            updatedAt = Clock.System.now(),
            syncStatus = SyncStatus.PENDING
        )
        maintenanceDao.update(updated)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BULK OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Inserisce più manutenzioni (per import).
     */
    suspend fun insertAll(maintenances: List<Maintenance>) {
        val now = Clock.System.now()
        val entities = maintenances.map { maintenance ->
            val id = maintenance.id.ifEmpty { UUID.randomUUID().toString() }
            maintenance.toEntity().copy(
                id = id,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING
            )
        }
        maintenanceDao.insertAll(entities)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MAPPING EXTENSIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Converte MaintenanceEntity in Maintenance (domain model).
 */
fun MaintenanceEntity.toDomain(): Maintenance = Maintenance(
    id = id,
    productId = productId,
    maintainerId = maintainerId,
    date = date,
    type = type,
    outcome = outcome,
    notes = notes,
    cost = cost,
    invoiceNumber = invoiceNumber,
    isWarrantyWork = isWarrantyWork,
    requestEmailSent = requestEmailSent,
    reportEmailSent = reportEmailSent
)

/**
 * Converte Maintenance (domain model) in MaintenanceEntity.
 */
fun Maintenance.toEntity(): MaintenanceEntity = MaintenanceEntity(
    id = id,
    productId = productId,
    maintainerId = maintainerId,
    date = date,
    type = type,
    outcome = outcome,
    notes = notes,
    cost = cost,
    invoiceNumber = invoiceNumber,
    isWarrantyWork = isWarrantyWork,
    requestEmailSent = requestEmailSent,
    requestEmailDate = null,  // Gestito separatamente
    reportEmailSent = reportEmailSent,
    reportEmailDate = null,   // Gestito separatamente
    createdAt = Clock.System.now(),  // Placeholder
    updatedAt = Clock.System.now(),  // Placeholder
    syncStatus = SyncStatus.PENDING
)
