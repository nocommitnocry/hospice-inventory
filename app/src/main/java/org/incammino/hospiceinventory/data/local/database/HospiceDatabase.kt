package org.incammino.hospiceinventory.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.incammino.hospiceinventory.data.local.dao.*
import org.incammino.hospiceinventory.data.local.entity.*
import org.incammino.hospiceinventory.domain.model.*

/**
 * Database Room principale per Hospice Inventory.
 * Contiene tutte le tabelle e i DAO necessari.
 */
@Database(
    entities = [
        ProductEntity::class,
        MaintainerEntity::class,
        MaintenanceEntity::class,
        LocationEntity::class,
        AssigneeEntity::class,
        EmailQueueEntity::class,
        MaintenanceAlertEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class HospiceDatabase : RoomDatabase() {
    
    abstract fun productDao(): ProductDao
    abstract fun maintainerDao(): MaintainerDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun locationDao(): LocationDao
    abstract fun assigneeDao(): AssigneeDao
    abstract fun emailQueueDao(): EmailQueueDao
    abstract fun maintenanceAlertDao(): MaintenanceAlertDao
    
    companion object {
        const val DATABASE_NAME = "hospice_inventory.db"
    }
}

/**
 * Type Converters per Room.
 * Converte tipi complessi in tipi primitivi supportati da SQLite.
 */
class Converters {
    
    // ═══════════════════════════════════════════════════════════════════════
    // KOTLINX DATETIME
    // ═══════════════════════════════════════════════════════════════════════
    
    @TypeConverter
    fun fromInstant(value: Instant?): Long? {
        return value?.toEpochMilliseconds()
    }
    
    @TypeConverter
    fun toInstant(value: Long?): Instant? {
        return value?.let { Instant.fromEpochMilliseconds(it) }
    }
    
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? {
        return value?.toString()
    }
    
    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? {
        return value?.let { LocalDate.parse(it) }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // ENUMS
    // ═══════════════════════════════════════════════════════════════════════
    
    @TypeConverter
    fun fromMaintenanceFrequency(value: MaintenanceFrequency?): String? {
        return value?.name
    }
    
    @TypeConverter
    fun toMaintenanceFrequency(value: String?): MaintenanceFrequency? {
        return value?.let { MaintenanceFrequency.valueOf(it) }
    }
    
    @TypeConverter
    fun fromAccountType(value: AccountType?): String? {
        return value?.name
    }
    
    @TypeConverter
    fun toAccountType(value: String?): AccountType? {
        return value?.let { AccountType.valueOf(it) }
    }
    
    @TypeConverter
    fun fromMaintenanceType(value: MaintenanceType?): String? {
        return value?.name
    }
    
    @TypeConverter
    fun toMaintenanceType(value: String?): MaintenanceType? {
        return value?.let { MaintenanceType.valueOf(it) }
    }
    
    @TypeConverter
    fun fromMaintenanceOutcome(value: MaintenanceOutcome?): String? {
        return value?.name
    }
    
    @TypeConverter
    fun toMaintenanceOutcome(value: String?): MaintenanceOutcome? {
        return value?.let { MaintenanceOutcome.valueOf(it) }
    }
    
    @TypeConverter
    fun fromSyncStatus(value: SyncStatus?): String? {
        return value?.name
    }
    
    @TypeConverter
    fun toSyncStatus(value: String?): SyncStatus? {
        return value?.let { SyncStatus.valueOf(it) }
    }
    
    @TypeConverter
    fun fromEmailStatus(value: EmailStatus?): String? {
        return value?.name
    }
    
    @TypeConverter
    fun toEmailStatus(value: String?): EmailStatus? {
        return value?.let { EmailStatus.valueOf(it) }
    }
    
    @TypeConverter
    fun fromAlertType(value: AlertType?): String? {
        return value?.name
    }
    
    @TypeConverter
    fun toAlertType(value: String?): AlertType? {
        return value?.let { AlertType.valueOf(it) }
    }
}
