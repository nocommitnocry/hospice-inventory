package org.incammino.hospiceinventory.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.incammino.hospiceinventory.data.local.dao.ProductDao
import org.incammino.hospiceinventory.data.local.entity.ProductEntity
import org.incammino.hospiceinventory.domain.model.Product
import org.incammino.hospiceinventory.domain.model.SyncStatus
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository per la gestione dei Prodotti.
 * Funge da single source of truth, mediando tra il DAO locale
 * e i domain models usati dalla UI.
 */
@Singleton
class ProductRepository @Inject constructor(
    private val productDao: ProductDao
) {
    // ═══════════════════════════════════════════════════════════════════════════
    // QUERY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tutti i prodotti attivi.
     */
    fun getAllActive(): Flow<List<Product>> =
        productDao.getAllActive().map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Tutti i prodotti (inclusi inattivi).
     */
    fun getAll(): Flow<List<Product>> =
        productDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Prodotto per ID (one-shot).
     */
    suspend fun getById(id: String): Product? =
        productDao.getById(id)?.toDomain()

    /**
     * Prodotto per ID (Flow per observe).
     */
    fun getByIdFlow(id: String): Flow<Product?> =
        productDao.getByIdFlow(id).map { it?.toDomain() }

    /**
     * Prodotto per barcode.
     */
    suspend fun getByBarcode(barcode: String): Product? =
        productDao.getByBarcode(barcode)?.toDomain()

    // ═══════════════════════════════════════════════════════════════════════════
    // RICERCA
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Ricerca full-text su nome, descrizione, categoria, barcode, ubicazione.
     */
    fun search(query: String): Flow<List<Product>> =
        productDao.search(query).map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Ricerca sincrona (one-shot) per query vocali.
     * Usata da GeminiService per ricerche interne durante task multi-step.
     */
    suspend fun searchSync(query: String): List<Product> =
        productDao.searchSync(query).map { it.toDomain() }

    /**
     * Prodotti per categoria.
     */
    fun getByCategory(category: String): Flow<List<Product>> =
        productDao.getByCategory(category).map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Prodotti per ubicazione.
     */
    fun getByLocation(location: String): Flow<List<Product>> =
        productDao.getByLocation(location).map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Prodotti associati a un manutentore (garanzia o service).
     */
    fun getByMaintainer(maintainerId: String): Flow<List<Product>> =
        productDao.getByMaintainer(maintainerId).map { entities ->
            entities.map { it.toDomain() }
        }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCADENZE MANUTENZIONE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Prodotti con manutenzione in scadenza entro una data.
     */
    fun getWithMaintenanceDueBefore(date: LocalDate): Flow<List<Product>> =
        productDao.getWithMaintenanceDueBefore(date).map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Prodotti con manutenzione in scadenza in un range di date.
     */
    fun getWithMaintenanceDueBetween(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<Product>> =
        productDao.getWithMaintenanceDueBetween(startDate, endDate).map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Prodotti con manutenzione scaduta.
     */
    fun getWithOverdueMaintenance(today: LocalDate): Flow<List<Product>> =
        productDao.getWithOverdueMaintenance(today).map { entities ->
            entities.map { it.toDomain() }
        }

    // ═══════════════════════════════════════════════════════════════════════════
    // GARANZIA
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Prodotti ancora in garanzia.
     */
    fun getUnderWarranty(today: LocalDate): Flow<List<Product>> =
        productDao.getUnderWarranty(today).map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Prodotti con garanzia in scadenza.
     */
    fun getWithWarrantyExpiringSoon(
        today: LocalDate,
        futureDate: LocalDate
    ): Flow<List<Product>> =
        productDao.getWithWarrantyExpiringSoon(today, futureDate).map { entities ->
            entities.map { it.toDomain() }
        }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATISTICHE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Conteggio prodotti attivi.
     */
    suspend fun countActive(): Int = productDao.countActive()

    /**
     * Tutte le categorie distinte.
     */
    fun getAllCategories(): Flow<List<String>> = productDao.getAllCategories()

    /**
     * Tutte le ubicazioni distinte.
     */
    fun getAllLocations(): Flow<List<String>> = productDao.getAllLocations()

    /**
     * Conteggio manutenzioni scadute (con data specifica).
     */
    suspend fun countOverdueMaintenance(today: LocalDate): Int =
        productDao.countOverdueMaintenance(today)

    /**
     * Conteggio manutenzioni scadute (usa data odierna).
     */
    suspend fun countOverdueMaintenance(): Int {
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        return productDao.countOverdueMaintenance(today)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CRUD
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Inserisce un nuovo prodotto.
     * Genera automaticamente ID e timestamp se non presenti.
     */
    suspend fun insert(product: Product): String {
        val now = Clock.System.now()
        val id = product.id.ifEmpty { UUID.randomUUID().toString() }
        val entity = product.toEntity().copy(
            id = id,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING
        )
        productDao.insert(entity)
        return id
    }

    /**
     * Aggiorna un prodotto esistente.
     */
    suspend fun update(product: Product) {
        val now = Clock.System.now()
        val entity = product.toEntity().copy(
            updatedAt = now,
            syncStatus = SyncStatus.PENDING
        )
        productDao.update(entity)
    }

    /**
     * Soft delete (mantiene il record ma lo disattiva).
     */
    suspend fun softDelete(id: String) {
        productDao.softDelete(id, Clock.System.now())
    }

    /**
     * Delete definitivo.
     */
    suspend fun delete(id: String) {
        productDao.deleteById(id)
    }

    /**
     * Aggiorna le date di manutenzione dopo un intervento.
     */
    suspend fun updateMaintenanceDates(
        productId: String,
        lastDate: LocalDate,
        nextDue: LocalDate?
    ) {
        productDao.updateMaintenanceDates(
            productId = productId,
            date = lastDate,
            nextDue = nextDue,
            updatedAt = Clock.System.now()
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SYNC
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Prodotti in attesa di sincronizzazione.
     */
    suspend fun getPendingSync(): List<Product> =
        productDao.getBySyncStatus(SyncStatus.PENDING).map { it.toDomain() }

    /**
     * Aggiorna lo stato di sync.
     */
    suspend fun updateSyncStatus(id: String, status: SyncStatus) {
        productDao.updateSyncStatus(id, status)
    }

    /**
     * Aggiorna lo stato di sync per più prodotti.
     */
    suspend fun updateSyncStatusBatch(ids: List<String>, status: SyncStatus) {
        productDao.updateSyncStatusBatch(ids, status)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BULK OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Inserisce più prodotti (per import).
     */
    suspend fun insertAll(products: List<Product>) {
        val now = Clock.System.now()
        val entities = products.map { product ->
            val id = product.id.ifEmpty { UUID.randomUUID().toString() }
            product.toEntity().copy(
                id = id,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING
            )
        }
        productDao.insertAll(entities)
    }

    /**
     * Elimina tutti i prodotti.
     */
    suspend fun deleteAll() {
        productDao.deleteAll()
    }

    /**
     * Elimina prodotti con ID che corrispondono al pattern.
     */
    suspend fun deleteByIdPattern(pattern: String) {
        productDao.deleteByIdPattern(pattern)
    }

    /**
     * Conteggio totale prodotti.
     */
    suspend fun countAll(): Int = productDao.countAll()
}

// ═══════════════════════════════════════════════════════════════════════════════
// MAPPING EXTENSIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Converte ProductEntity in Product (domain model).
 */
fun ProductEntity.toDomain(): Product = Product(
    id = id,
    barcode = barcode,
    name = name,
    description = description,
    category = category,
    location = location,
    assigneeId = assigneeId,
    warrantyMaintainerId = warrantyMaintainerId,
    warrantyStartDate = warrantyStartDate,
    warrantyEndDate = warrantyEndDate,
    serviceMaintainerId = serviceMaintainerId,
    maintenanceFrequency = maintenanceFrequency,
    maintenanceStartDate = maintenanceStartDate,
    maintenanceIntervalDays = maintenanceIntervalDays,
    lastMaintenanceDate = lastMaintenanceDate,
    nextMaintenanceDue = nextMaintenanceDue,
    purchaseDate = purchaseDate,
    price = price,
    accountType = accountType,
    supplier = supplier,
    invoiceNumber = invoiceNumber,
    imageUri = imageUri,
    notes = notes,
    isActive = isActive
)

/**
 * Converte Product (domain model) in ProductEntity.
 * Nota: createdAt, updatedAt e syncStatus vengono gestiti dal repository.
 */
fun Product.toEntity(): ProductEntity = ProductEntity(
    id = id,
    barcode = barcode,
    name = name,
    description = description,
    category = category,
    location = location,
    assigneeId = assigneeId,
    warrantyMaintainerId = warrantyMaintainerId,
    warrantyStartDate = warrantyStartDate,
    warrantyEndDate = warrantyEndDate,
    serviceMaintainerId = serviceMaintainerId,
    maintenanceFrequency = maintenanceFrequency,
    maintenanceStartDate = maintenanceStartDate,
    maintenanceIntervalDays = maintenanceIntervalDays,
    lastMaintenanceDate = lastMaintenanceDate,
    nextMaintenanceDue = nextMaintenanceDue,
    purchaseDate = purchaseDate,
    price = price,
    accountType = accountType,
    supplier = supplier,
    invoiceNumber = invoiceNumber,
    imageUri = imageUri,
    notes = notes,
    isActive = isActive,
    createdAt = Clock.System.now(),  // Placeholder, verrà sovrascritto
    updatedAt = Clock.System.now(),  // Placeholder, verrà sovrascritto
    syncStatus = SyncStatus.PENDING
)
