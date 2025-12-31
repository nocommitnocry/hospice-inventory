package org.incammino.hospiceinventory.service.backup

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.incammino.hospiceinventory.data.local.database.HospiceDatabase
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Risultato operazione backup.
 */
sealed class BackupResult {
    data class Success(val message: String) : BackupResult()
    data class Error(val message: String) : BackupResult()
    data object NotSignedIn : BackupResult()
}

/**
 * Manager per orchestrare backup e restore.
 * Coordina GoogleDriveService ed ExcelExportService per eseguire
 * le operazioni di backup/restore/export.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveService: GoogleDriveService,
    private val excelExportService: ExcelExportService,
    private val database: HospiceDatabase
) {
    companion object {
        private const val TAG = "BackupManager"
    }

    /**
     * Esegue backup completo del database.
     */
    suspend fun performBackup(): BackupResult = withContext(Dispatchers.IO) {
        if (!driveService.isSignedIn()) {
            return@withContext BackupResult.NotSignedIn
        }

        try {
            // 1. Assicurati che Drive sia inizializzato
            if (!driveService.initializeIfSignedIn()) {
                return@withContext BackupResult.Error("Impossibile inizializzare Google Drive")
            }

            // 2. Copia il database in una location temporanea
            val dbFile = context.getDatabasePath(HospiceDatabase.DATABASE_NAME)
            if (!dbFile.exists()) {
                return@withContext BackupResult.Error("Database non trovato")
            }

            val timestamp = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .let { "${it.date}_${it.hour.toString().padStart(2, '0')}${it.minute.toString().padStart(2, '0')}" }

            val backupFileName = "backup_$timestamp.db"
            val tempFile = File(context.cacheDir, backupFileName)

            // Forza checkpoint WAL - scrive tutti i dati pending nel .db principale
            database.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
            Log.d(TAG, "WAL checkpoint completato")

            // Copia il database
            dbFile.copyTo(tempFile, overwrite = true)
            Log.d(TAG, "Database copiato: ${tempFile.length()} bytes")

            // Log file WAL/SHM se presenti
            val walFile = File(dbFile.parent, "${HospiceDatabase.DATABASE_NAME}-wal")
            val shmFile = File(dbFile.parent, "${HospiceDatabase.DATABASE_NAME}-shm")
            if (walFile.exists()) {
                Log.d(TAG, "WAL file presente: ${walFile.length()} bytes")
            }
            if (shmFile.exists()) {
                Log.d(TAG, "SHM file presente: ${shmFile.length()} bytes")
            }

            // 3. Carica su Drive
            val uploadResult = driveService.uploadBackup(tempFile, backupFileName)

            // 4. Pulisci file temporaneo
            tempFile.delete()

            // 5. Pulizia vecchi backup (mantieni ultimi 7)
            driveService.cleanupOldBackups(keepCount = 7)

            when (uploadResult) {
                is DriveResult.Success -> {
                    Log.i(TAG, "Backup completato: $backupFileName")
                    BackupResult.Success("Backup completato: $backupFileName")
                }
                is DriveResult.Error -> BackupResult.Error(uploadResult.message)
                is DriveResult.NotSignedIn -> BackupResult.NotSignedIn
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore backup", e)
            BackupResult.Error("Errore durante il backup: ${e.message}")
        }
    }

    /**
     * Ripristina da un backup.
     * ATTENZIONE: Questa operazione sovrascrive il database attuale!
     */
    suspend fun restoreBackup(backupId: String, backupName: String): BackupResult = withContext(Dispatchers.IO) {
        if (!driveService.isSignedIn()) {
            return@withContext BackupResult.NotSignedIn
        }

        try {
            // 1. Assicurati che Drive sia inizializzato
            if (!driveService.initializeIfSignedIn()) {
                return@withContext BackupResult.Error("Impossibile inizializzare Google Drive")
            }

            // 2. Scarica il backup
            val tempFile = File(context.cacheDir, "restore_temp.db")
            val downloadResult = driveService.downloadBackup(backupId, tempFile)

            if (downloadResult !is DriveResult.Success) {
                return@withContext BackupResult.Error("Errore download backup")
            }

            // 3. Verifica integrità (basic check)
            if (tempFile.length() < 1024) {
                tempFile.delete()
                return@withContext BackupResult.Error("File backup corrotto o troppo piccolo")
            }

            // 4. Sostituisci database
            val dbFile = context.getDatabasePath(HospiceDatabase.DATABASE_NAME)
            val walFile = File(dbFile.parent, "${HospiceDatabase.DATABASE_NAME}-wal")
            val shmFile = File(dbFile.parent, "${HospiceDatabase.DATABASE_NAME}-shm")

            // Elimina WAL e SHM
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            // Sovrascrivi database
            tempFile.copyTo(dbFile, overwrite = true)
            tempFile.delete()

            Log.i(TAG, "Restore completato da: $backupName")
            BackupResult.Success("Ripristino completato da: $backupName\n\nRiavvia l'app per applicare le modifiche.")
        } catch (e: Exception) {
            Log.e(TAG, "Errore restore", e)
            BackupResult.Error("Errore durante il ripristino: ${e.message}")
        }
    }

    /**
     * Esporta dati in Excel e carica su Drive.
     */
    suspend fun exportToExcel(): BackupResult = withContext(Dispatchers.IO) {
        if (!driveService.isSignedIn()) {
            return@withContext BackupResult.NotSignedIn
        }

        try {
            // 1. Assicurati che Drive sia inizializzato
            if (!driveService.initializeIfSignedIn()) {
                return@withContext BackupResult.Error("Impossibile inizializzare Google Drive")
            }

            val timestamp = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date.toString()

            // 2. Genera file Excel
            val excelFile = excelExportService.generateInventoryExcel()
            val fileName = "inventario_$timestamp.xlsx"

            // 3. Carica su Drive
            val uploadResult = driveService.uploadExport(excelFile, fileName)

            // 4. Pulisci file temporaneo
            excelFile.delete()

            when (uploadResult) {
                is DriveResult.Success -> {
                    Log.i(TAG, "Export completato: $fileName")
                    BackupResult.Success("Export completato: $fileName")
                }
                is DriveResult.Error -> BackupResult.Error(uploadResult.message)
                is DriveResult.NotSignedIn -> BackupResult.NotSignedIn
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore export", e)
            BackupResult.Error("Errore durante l'export: ${e.message}")
        }
    }

    /**
     * Lista i backup disponibili su Drive.
     */
    suspend fun listBackups(): List<BackupInfo> {
        if (!driveService.isSignedIn()) return emptyList()

        // Assicurati che Drive sia inizializzato
        if (!driveService.initializeIfSignedIn()) return emptyList()

        return when (val result = driveService.listBackups()) {
            is DriveResult.Success -> result.data
            else -> emptyList()
        }
    }
}
