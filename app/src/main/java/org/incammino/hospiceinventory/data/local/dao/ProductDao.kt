package org.incammino.hospiceinventory.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import org.incammino.hospiceinventory.data.local.entity.ProductEntity
import org.incammino.hospiceinventory.domain.model.SyncStatus

/**
 * Data Access Object per i Prodotti.
 * Fornisce tutte le operazioni CRUD e le query specializzate.
 */
@Dao
interface ProductDao {
    
    // ═══════════════════════════════════════════════════════════════════════
    // QUERY BASE
    // ═══════════════════════════════════════════════════════════════════════
    
    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActive(): Flow<List<ProductEntity>>
    
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAll(): Flow<List<ProductEntity>>
    
    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: String): ProductEntity?
    
    @Query("SELECT * FROM products WHERE id = :id")
    fun getByIdFlow(id: String): Flow<ProductEntity?>
    
    @Query("SELECT * FROM products WHERE barcode = :barcode")
    suspend fun getByBarcode(barcode: String): ProductEntity?
    
    // ═══════════════════════════════════════════════════════════════════════
    // RICERCA
    // ═══════════════════════════════════════════════════════════════════════
    
    @Query("""
        SELECT * FROM products 
        WHERE isActive = 1 
        AND (
            name LIKE '%' || :query || '%' 
            OR description LIKE '%' || :query || '%'
            OR category LIKE '%' || :query || '%'
            OR barcode LIKE '%' || :query || '%'
            OR location LIKE '%' || :query || '%'
        )
        ORDER BY name ASC
    """)
    fun search(query: String): Flow<List<ProductEntity>>
    
    @Query("""
        SELECT * FROM products 
        WHERE isActive = 1 
        AND category = :category
        ORDER BY name ASC
    """)
    fun getByCategory(category: String): Flow<List<ProductEntity>>
    
    @Query("""
        SELECT * FROM products 
        WHERE isActive = 1 
        AND location = :location
        ORDER BY name ASC
    """)
    fun getByLocation(location: String): Flow<List<ProductEntity>>
    
    @Query("""
        SELECT * FROM products 
        WHERE isActive = 1 
        AND (warrantyMaintainerId = :maintainerId OR serviceMaintainerId = :maintainerId)
        ORDER BY name ASC
    """)
    fun getByMaintainer(maintainerId: String): Flow<List<ProductEntity>>
    
    // ═══════════════════════════════════════════════════════════════════════
    // SCADENZE MANUTENZIONE
    // ═══════════════════════════════════════════════════════════════════════
    
    @Query("""
        SELECT * FROM products 
        WHERE isActive = 1 
        AND nextMaintenanceDue IS NOT NULL
        AND nextMaintenanceDue <= :date
        ORDER BY nextMaintenanceDue ASC
    """)
    fun getWithMaintenanceDueBefore(date: LocalDate): Flow<List<ProductEntity>>
    
    @Query("""
        SELECT * FROM products 
        WHERE isActive = 1 
        AND nextMaintenanceDue IS NOT NULL
        AND nextMaintenanceDue BETWEEN :startDate AND :endDate
        ORDER BY nextMaintenanceDue ASC
    """)
    fun getWithMaintenanceDueBetween(
        startDate: LocalDate, 
        endDate: LocalDate
    ): Flow<List<ProductEntity>>
    
    @Query("""
        SELECT * FROM products 
        WHERE isActive = 1 
        AND maintenanceFrequency IS NOT NULL
        AND (lastMaintenanceDate IS NULL OR nextMaintenanceDue < :today)
        ORDER BY nextMaintenanceDue ASC
    """)
    fun getWithOverdueMaintenance(today: LocalDate): Flow<List<ProductEntity>>
    
    // ═══════════════════════════════════════════════════════════════════════
    // GARANZIA
    // ═══════════════════════════════════════════════════════════════════════
    
    @Query("""
        SELECT * FROM products 
        WHERE isActive = 1 
        AND warrantyEndDate IS NOT NULL
        AND warrantyEndDate >= :today
        ORDER BY warrantyEndDate ASC
    """)
    fun getUnderWarranty(today: LocalDate): Flow<List<ProductEntity>>
    
    @Query("""
        SELECT * FROM products 
        WHERE isActive = 1 
        AND warrantyEndDate IS NOT NULL
        AND warrantyEndDate BETWEEN :today AND :futureDate
        ORDER BY warrantyEndDate ASC
    """)
    fun getWithWarrantyExpiringSoon(
        today: LocalDate, 
        futureDate: LocalDate
    ): Flow<List<ProductEntity>>
    
    // ═══════════════════════════════════════════════════════════════════════
    // STATISTICHE
    // ═══════════════════════════════════════════════════════════════════════
    
    @Query("SELECT COUNT(*) FROM products WHERE isActive = 1")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM products")
    suspend fun countAll(): Int
    
    @Query("SELECT DISTINCT category FROM products WHERE isActive = 1 ORDER BY category")
    fun getAllCategories(): Flow<List<String>>
    
    @Query("SELECT DISTINCT location FROM products WHERE isActive = 1 ORDER BY location")
    fun getAllLocations(): Flow<List<String>>
    
    @Query("""
        SELECT COUNT(*) FROM products 
        WHERE isActive = 1 
        AND nextMaintenanceDue IS NOT NULL
        AND nextMaintenanceDue < :today
    """)
    suspend fun countOverdueMaintenance(today: LocalDate): Int
    
    // ═══════════════════════════════════════════════════════════════════════
    // SYNC
    // ═══════════════════════════════════════════════════════════════════════
    
    @Query("SELECT * FROM products WHERE syncStatus = :status")
    suspend fun getBySyncStatus(status: SyncStatus): List<ProductEntity>
    
    @Query("UPDATE products SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)
    
    @Query("UPDATE products SET syncStatus = :status WHERE id IN (:ids)")
    suspend fun updateSyncStatusBatch(ids: List<String>, status: SyncStatus)
    
    // ═══════════════════════════════════════════════════════════════════════
    // CRUD
    // ═══════════════════════════════════════════════════════════════════════
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: ProductEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)
    
    @Update
    suspend fun update(product: ProductEntity)
    
    @Query("UPDATE products SET isActive = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: kotlinx.datetime.Instant)
    
    @Delete
    suspend fun delete(product: ProductEntity)
    
    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: String)
    
    @Query("DELETE FROM products")
    suspend fun deleteAll()
    
    // ═══════════════════════════════════════════════════════════════════════
    // AGGIORNAMENTI SPECIFICI
    // ═══════════════════════════════════════════════════════════════════════
    
    @Query("""
        UPDATE products 
        SET lastMaintenanceDate = :date, 
            nextMaintenanceDue = :nextDue,
            updatedAt = :updatedAt,
            syncStatus = 'PENDING'
        WHERE id = :productId
    """)
    suspend fun updateMaintenanceDates(
        productId: String,
        date: LocalDate,
        nextDue: LocalDate?,
        updatedAt: kotlinx.datetime.Instant
    )
}
