package org.incammino.hospiceinventory.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.incammino.hospiceinventory.data.local.entity.*
import org.incammino.hospiceinventory.domain.model.AlertType
import org.incammino.hospiceinventory.domain.model.EmailStatus
import org.incammino.hospiceinventory.domain.model.MaintenanceType

/**
 * DAO per i Manutentori
 */
@Dao
interface MaintainerDao {
    
    @Query("SELECT * FROM maintainers WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActive(): Flow<List<MaintainerEntity>>
    
    @Query("SELECT * FROM maintainers ORDER BY name ASC")
    fun getAll(): Flow<List<MaintainerEntity>>
    
    @Query("SELECT * FROM maintainers WHERE id = :id")
    suspend fun getById(id: String): MaintainerEntity?
    
    @Query("SELECT * FROM maintainers WHERE id = :id")
    fun getByIdFlow(id: String): Flow<MaintainerEntity?>
    
    @Query("""
        SELECT * FROM maintainers 
        WHERE isActive = 1 
        AND (name LIKE '%' || :query || '%' OR specialization LIKE '%' || :query || '%')
        ORDER BY name ASC
    """)
    fun search(query: String): Flow<List<MaintainerEntity>>
    
    @Query("SELECT * FROM maintainers WHERE isActive = 1 AND isSupplier = 1 ORDER BY name ASC")
    fun getSuppliers(): Flow<List<MaintainerEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(maintainer: MaintainerEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(maintainers: List<MaintainerEntity>)
    
    @Update
    suspend fun update(maintainer: MaintainerEntity)
    
    @Query("UPDATE maintainers SET isActive = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: Instant)
    
    @Delete
    suspend fun delete(maintainer: MaintainerEntity)
}

/**
 * DAO per le Manutenzioni
 */
@Dao
interface MaintenanceDao {
    
    @Query("SELECT * FROM maintenances ORDER BY date DESC")
    fun getAll(): Flow<List<MaintenanceEntity>>
    
    @Query("SELECT * FROM maintenances WHERE id = :id")
    suspend fun getById(id: String): MaintenanceEntity?
    
    @Query("SELECT * FROM maintenances WHERE productId = :productId ORDER BY date DESC")
    fun getByProduct(productId: String): Flow<List<MaintenanceEntity>>
    
    @Query("SELECT * FROM maintenances WHERE productId = :productId ORDER BY date DESC LIMIT 1")
    suspend fun getLastByProduct(productId: String): MaintenanceEntity?
    
    @Query("SELECT * FROM maintenances WHERE maintainerId = :maintainerId ORDER BY date DESC")
    fun getByMaintainer(maintainerId: String): Flow<List<MaintenanceEntity>>
    
    @Query("SELECT * FROM maintenances WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getByDateRange(startDate: Instant, endDate: Instant): Flow<List<MaintenanceEntity>>
    
    @Query("SELECT * FROM maintenances WHERE type = :type ORDER BY date DESC")
    fun getByType(type: MaintenanceType): Flow<List<MaintenanceEntity>>
    
    // Statistiche
    @Query("SELECT COUNT(*) FROM maintenances")
    suspend fun count(): Int
    
    @Query("SELECT COUNT(*) FROM maintenances WHERE productId = :productId")
    suspend fun countByProduct(productId: String): Int
    
    @Query("SELECT COUNT(*) FROM maintenances WHERE date BETWEEN :startDate AND :endDate")
    suspend fun countByDateRange(startDate: Instant, endDate: Instant): Int
    
    @Query("""
        SELECT COUNT(*) FROM maintenances 
        WHERE date BETWEEN :startDate AND :endDate 
        AND type = :type
    """)
    suspend fun countByDateRangeAndType(
        startDate: Instant, 
        endDate: Instant, 
        type: MaintenanceType
    ): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(maintenance: MaintenanceEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(maintenances: List<MaintenanceEntity>)
    
    @Update
    suspend fun update(maintenance: MaintenanceEntity)
    
    @Delete
    suspend fun delete(maintenance: MaintenanceEntity)
    
    @Query("DELETE FROM maintenances WHERE productId = :productId")
    suspend fun deleteByProduct(productId: String)
}

/**
 * DAO per le Ubicazioni
 */
@Dao
interface LocationDao {
    
    @Query("SELECT * FROM locations WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActive(): Flow<List<LocationEntity>>
    
    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getById(id: String): LocationEntity?
    
    @Query("SELECT * FROM locations WHERE parentId = :parentId ORDER BY name ASC")
    fun getChildren(parentId: String): Flow<List<LocationEntity>>
    
    @Query("SELECT * FROM locations WHERE parentId IS NULL AND isActive = 1 ORDER BY name ASC")
    fun getRootLocations(): Flow<List<LocationEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: LocationEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(locations: List<LocationEntity>)
    
    @Update
    suspend fun update(location: LocationEntity)
    
    @Delete
    suspend fun delete(location: LocationEntity)
}

/**
 * DAO per gli Assegnatari
 */
@Dao
interface AssigneeDao {
    
    @Query("SELECT * FROM assignees WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActive(): Flow<List<AssigneeEntity>>
    
    @Query("SELECT * FROM assignees WHERE id = :id")
    suspend fun getById(id: String): AssigneeEntity?
    
    @Query("SELECT * FROM assignees WHERE department = :department AND isActive = 1 ORDER BY name ASC")
    fun getByDepartment(department: String): Flow<List<AssigneeEntity>>
    
    @Query("SELECT DISTINCT department FROM assignees WHERE isActive = 1 AND department IS NOT NULL ORDER BY department")
    fun getAllDepartments(): Flow<List<String>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assignee: AssigneeEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(assignees: List<AssigneeEntity>)
    
    @Update
    suspend fun update(assignee: AssigneeEntity)
    
    @Delete
    suspend fun delete(assignee: AssigneeEntity)
}

/**
 * DAO per la Coda Email
 */
@Dao
interface EmailQueueDao {
    
    @Query("SELECT * FROM email_queue WHERE status = :status ORDER BY createdAt ASC")
    fun getByStatus(status: EmailStatus): Flow<List<EmailQueueEntity>>
    
    @Query("SELECT * FROM email_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPending(): List<EmailQueueEntity>
    
    @Query("SELECT * FROM email_queue WHERE status = 'FAILED' AND retryCount < 3 ORDER BY createdAt ASC")
    suspend fun getRetryable(): List<EmailQueueEntity>
    
    @Query("SELECT COUNT(*) FROM email_queue WHERE status = 'PENDING'")
    fun countPending(): Flow<Int>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(email: EmailQueueEntity)
    
    @Update
    suspend fun update(email: EmailQueueEntity)
    
    @Query("""
        UPDATE email_queue 
        SET status = :status, sentAt = :sentAt 
        WHERE id = :id
    """)
    suspend fun updateStatus(id: String, status: EmailStatus, sentAt: Instant?)
    
    @Query("""
        UPDATE email_queue 
        SET status = 'FAILED', retryCount = retryCount + 1, errorMessage = :error 
        WHERE id = :id
    """)
    suspend fun markFailed(id: String, error: String)
    
    @Delete
    suspend fun delete(email: EmailQueueEntity)
    
    @Query("DELETE FROM email_queue WHERE status = 'SENT' AND sentAt < :before")
    suspend fun deleteOldSent(before: Instant)
}

/**
 * DAO per gli Alert Manutenzione
 */
@Dao
interface MaintenanceAlertDao {
    
    @Query("""
        SELECT * FROM maintenance_alerts 
        WHERE completedAt IS NULL AND dismissedAt IS NULL
        ORDER BY dueDate ASC
    """)
    fun getActiveAlerts(): Flow<List<MaintenanceAlertEntity>>
    
    @Query("""
        SELECT * FROM maintenance_alerts 
        WHERE productId = :productId 
        AND completedAt IS NULL AND dismissedAt IS NULL
        ORDER BY dueDate ASC
    """)
    fun getActiveAlertsForProduct(productId: String): Flow<List<MaintenanceAlertEntity>>
    
    @Query("""
        SELECT * FROM maintenance_alerts 
        WHERE alertType = :alertType 
        AND notifiedAt IS NULL
        ORDER BY dueDate ASC
    """)
    suspend fun getUnnotifiedByType(alertType: AlertType): List<MaintenanceAlertEntity>
    
    @Query("SELECT COUNT(*) FROM maintenance_alerts WHERE completedAt IS NULL AND dismissedAt IS NULL")
    fun countActive(): Flow<Int>
    
    @Query("""
        SELECT COUNT(*) FROM maintenance_alerts 
        WHERE alertType = 'OVERDUE' 
        AND completedAt IS NULL AND dismissedAt IS NULL
    """)
    fun countOverdue(): Flow<Int>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: MaintenanceAlertEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(alerts: List<MaintenanceAlertEntity>)
    
    @Query("UPDATE maintenance_alerts SET notifiedAt = :notifiedAt WHERE id = :id")
    suspend fun markNotified(id: String, notifiedAt: Instant)
    
    @Query("UPDATE maintenance_alerts SET dismissedAt = :dismissedAt WHERE id = :id")
    suspend fun dismiss(id: String, dismissedAt: Instant)
    
    @Query("UPDATE maintenance_alerts SET completedAt = :completedAt WHERE productId = :productId AND completedAt IS NULL")
    suspend fun completeForProduct(productId: String, completedAt: Instant)
    
    @Delete
    suspend fun delete(alert: MaintenanceAlertEntity)
    
    @Query("DELETE FROM maintenance_alerts WHERE productId = :productId")
    suspend fun deleteByProduct(productId: String)
}
