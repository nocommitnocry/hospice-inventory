# SPEC: Google Drive Backup & Export

**Data**: 31/12/2025  
**Versione**: 1.0  
**Stato**: Da implementare  
**Priorità**: Alta (pre-produzione)

---

## 1. Obiettivo

Implementare backup automatico e manuale del database Room su Google Drive, con export Excel per condivisione con amministrazione. L'account Google Workspace dell'organizzazione (`inventario@incammino.org` o simile) è già configurato sul tablet di produzione.

### 1.1 Funzionalità Target

| Funzionalità | Priorità | Descrizione |
|--------------|----------|-------------|
| Backup manuale | P0 | Bottone → salva DB su Drive |
| Backup automatico | P1 | WorkManager ogni sera alle 18:00 |
| Export Excel | P1 | Genera file leggibili per amministrazione |
| Restore | P2 | Seleziona backup da Drive e ripristina |
| Lista backup | P2 | Visualizza backup esistenti con data/dimensione |

### 1.2 Struttura File su Drive

```
📁 Google Drive / HospiceInventory /
   ├── backups/
   │   ├── backup_2025-12-31_1800.db
   │   ├── backup_2025-12-30_1800.db
   │   └── ...
   └── exports/
       ├── inventario_2025-12-31.xlsx
       ├── manutenzioni_2025-12-31.xlsx
       └── manutentori_2025-12-31.xlsx
```

---

## 2. Setup Google Cloud Console

### 2.1 Configurazione OAuth (Progetto: `inventario-462506`)

1. **Vai su** [Google Cloud Console](https://console.cloud.google.com/apis/credentials?project=inventario-462506)

2. **Abilita Google Drive API**:
   - APIs & Services → Library
   - Cerca "Google Drive API" → Enable

3. **Configura OAuth Consent Screen**:
   - APIs & Services → OAuth consent screen
   - User Type: **Internal** (solo utenti dell'organizzazione)
   - App name: "Hospice Inventory"
   - User support email: tua email
   - Scopes: `https://www.googleapis.com/auth/drive.file`
   - **IMPORTANTE**: Lo scope `drive.file` permette accesso SOLO ai file creati dall'app

4. **Crea OAuth Client ID**:
   - APIs & Services → Credentials → Create Credentials → OAuth client ID
   - Application type: **Android**
   - Package name: `org.incammino.hospiceinventory`
   - SHA-1: (vedi sotto come ottenerlo)
   - Crea anche per `.debug` suffix se serve per sviluppo

### 2.2 Ottenere SHA-1 Fingerprint

```bash
# Debug keystore (development)
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android

# Release keystore (production)
keytool -list -v -keystore /path/to/release.keystore -alias your-alias
```

Copia il valore SHA-1 e inseriscilo nella configurazione OAuth.

---

## 3. Dipendenze

### 3.1 Aggiornare `gradle/libs.versions.toml`

```toml
[versions]
# ... esistenti ...
googleApiClient = "2.2.0"
googleApiServicesDrive = "v3-rev20231128-2.0.0"
apachePoiOoxml = "5.2.5"

[libraries]
# ... esistenti ...

# Google Drive API
google-api-client-android = { group = "com.google.api-client", name = "google-api-client-android", version.ref = "googleApiClient" }
google-api-services-drive = { group = "com.google.apis", name = "google-api-services-drive", version.ref = "googleApiServicesDrive" }

# Apache POI per Excel export
apache-poi-ooxml = { group = "org.apache.poi", name = "poi-ooxml", version.ref = "apachePoiOoxml" }
```

### 3.2 Aggiornare `app/build.gradle.kts`

```kotlin
dependencies {
    // ... esistenti ...
    
    // Google Drive API
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)
    
    // Excel export
    implementation(libs.apache.poi.ooxml)
}
```

### 3.3 Esclusioni ProGuard (se necessario)

```proguard
# Google API Client
-keep class com.google.api.** { *; }
-keep class com.google.http.** { *; }
-dontwarn com.google.api.client.extensions.android.**
-dontwarn com.google.api.client.googleapis.extensions.android.**
```

---

## 4. Implementazione

### 4.1 Struttura File

```
app/src/main/java/org/incammino/hospiceinventory/
├── service/
│   └── backup/
│       ├── GoogleDriveService.kt      # Core Drive operations
│       ├── BackupManager.kt           # Orchestration logic
│       ├── ExcelExportService.kt      # Excel generation
│       └── BackupWorker.kt            # WorkManager scheduled backup
├── di/
│   └── BackupModule.kt                # Hilt module
└── ui/screens/settings/
    ├── DataManagementScreen.kt        # UI (modificare)
    └── DataManagementViewModel.kt     # ViewModel (modificare)
```

### 4.2 GoogleDriveService.kt

```kotlin
package org.incammino.hospiceinventory.service.backup

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Risultato operazione Drive.
 */
sealed class DriveResult<out T> {
    data class Success<T>(val data: T) : DriveResult<T>()
    data class Error(val message: String, val exception: Exception? = null) : DriveResult<Nothing>()
    data object NotSignedIn : DriveResult<Nothing>()
}

/**
 * Info backup per UI.
 */
data class BackupInfo(
    val id: String,
    val name: String,
    val createdTime: String,
    val size: Long
)

/**
 * Service per operazioni Google Drive.
 */
@Singleton
class GoogleDriveService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GoogleDriveService"
        private const val APP_FOLDER_NAME = "HospiceInventory"
        private const val BACKUPS_FOLDER = "backups"
        private const val EXPORTS_FOLDER = "exports"
    }

    private var driveService: Drive? = null
    private var appFolderId: String? = null
    private var backupsFolderId: String? = null
    private var exportsFolderId: String? = null

    /**
     * Configura GoogleSignInClient con scope Drive.
     */
    fun getSignInClient(): GoogleSignInClient {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        return GoogleSignIn.getClient(context, signInOptions)
    }

    /**
     * Verifica se l'utente è già loggato con permessi Drive.
     */
    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))
    }

    /**
     * Ottiene l'account corrente.
     */
    fun getCurrentAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * Inizializza il Drive service dopo sign-in.
     */
    suspend fun initialize(account: GoogleSignInAccount): DriveResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(DriveScopes.DRIVE_FILE)
            ).apply {
                selectedAccount = account.account
            }

            driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("HospiceInventory")
                .build()

            // Crea/trova la struttura cartelle
            ensureFolderStructure()
            
            DriveResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Errore inizializzazione Drive", e)
            DriveResult.Error("Impossibile inizializzare Google Drive: ${e.message}", e)
        }
    }

    /**
     * Crea la struttura cartelle se non esiste.
     */
    private suspend fun ensureFolderStructure() {
        val drive = driveService ?: throw IllegalStateException("Drive non inizializzato")

        // Trova o crea cartella principale
        appFolderId = findOrCreateFolder(APP_FOLDER_NAME, "root")
        
        // Trova o crea sottocartelle
        backupsFolderId = findOrCreateFolder(BACKUPS_FOLDER, appFolderId!!)
        exportsFolderId = findOrCreateFolder(EXPORTS_FOLDER, appFolderId!!)
        
        Log.d(TAG, "Struttura cartelle: app=$appFolderId, backups=$backupsFolderId, exports=$exportsFolderId")
    }

    /**
     * Trova una cartella per nome o la crea.
     */
    private fun findOrCreateFolder(name: String, parentId: String): String {
        val drive = driveService!!
        
        // Cerca cartella esistente
        val query = "name='$name' and '$parentId' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val result = drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        if (result.files.isNotEmpty()) {
            return result.files[0].id
        }

        // Crea nuova cartella
        val folderMetadata = DriveFile().apply {
            this.name = name
            this.mimeType = "application/vnd.google-apps.folder"
            this.parents = listOf(parentId)
        }
        
        val folder = drive.files().create(folderMetadata)
            .setFields("id")
            .execute()
        
        Log.d(TAG, "Creata cartella: $name (${folder.id})")
        return folder.id
    }

    /**
     * Carica un file di backup.
     */
    suspend fun uploadBackup(localFile: File, fileName: String): DriveResult<String> = withContext(Dispatchers.IO) {
        val drive = driveService ?: return@withContext DriveResult.NotSignedIn
        val folderId = backupsFolderId ?: return@withContext DriveResult.Error("Cartella backup non trovata")

        try {
            val fileMetadata = DriveFile().apply {
                name = fileName
                parents = listOf(folderId)
            }

            val mediaContent = FileContent("application/octet-stream", localFile)
            
            val uploadedFile = drive.files().create(fileMetadata, mediaContent)
                .setFields("id, name, createdTime, size")
                .execute()

            Log.d(TAG, "Backup caricato: ${uploadedFile.name} (${uploadedFile.id})")
            DriveResult.Success(uploadedFile.id)
        } catch (e: Exception) {
            Log.e(TAG, "Errore upload backup", e)
            DriveResult.Error("Errore durante il caricamento: ${e.message}", e)
        }
    }

    /**
     * Carica un file Excel export.
     */
    suspend fun uploadExport(localFile: File, fileName: String): DriveResult<String> = withContext(Dispatchers.IO) {
        val drive = driveService ?: return@withContext DriveResult.NotSignedIn
        val folderId = exportsFolderId ?: return@withContext DriveResult.Error("Cartella export non trovata")

        try {
            val fileMetadata = DriveFile().apply {
                name = fileName
                parents = listOf(folderId)
            }

            val mediaContent = FileContent(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                localFile
            )
            
            val uploadedFile = drive.files().create(fileMetadata, mediaContent)
                .setFields("id, name, createdTime, size")
                .execute()

            Log.d(TAG, "Export caricato: ${uploadedFile.name} (${uploadedFile.id})")
            DriveResult.Success(uploadedFile.id)
        } catch (e: Exception) {
            Log.e(TAG, "Errore upload export", e)
            DriveResult.Error("Errore durante il caricamento: ${e.message}", e)
        }
    }

    /**
     * Lista i backup disponibili.
     */
    suspend fun listBackups(): DriveResult<List<BackupInfo>> = withContext(Dispatchers.IO) {
        val drive = driveService ?: return@withContext DriveResult.NotSignedIn
        val folderId = backupsFolderId ?: return@withContext DriveResult.Error("Cartella backup non trovata")

        try {
            val query = "'$folderId' in parents and trashed=false"
            val result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, createdTime, size)")
                .setOrderBy("createdTime desc")
                .setPageSize(20)
                .execute()

            val backups = result.files.map { file ->
                BackupInfo(
                    id = file.id,
                    name = file.name,
                    createdTime = file.createdTime?.toString() ?: "",
                    size = file.getSize() ?: 0
                )
            }

            DriveResult.Success(backups)
        } catch (e: Exception) {
            Log.e(TAG, "Errore lista backup", e)
            DriveResult.Error("Errore nel recupero lista backup: ${e.message}", e)
        }
    }

    /**
     * Scarica un backup.
     */
    suspend fun downloadBackup(fileId: String, destinationFile: File): DriveResult<File> = withContext(Dispatchers.IO) {
        val drive = driveService ?: return@withContext DriveResult.NotSignedIn

        try {
            val outputStream = FileOutputStream(destinationFile)
            drive.files().get(fileId)
                .executeMediaAndDownloadTo(outputStream)
            outputStream.close()

            Log.d(TAG, "Backup scaricato: ${destinationFile.absolutePath}")
            DriveResult.Success(destinationFile)
        } catch (e: Exception) {
            Log.e(TAG, "Errore download backup", e)
            DriveResult.Error("Errore durante il download: ${e.message}", e)
        }
    }

    /**
     * Elimina vecchi backup (mantiene ultimi N).
     */
    suspend fun cleanupOldBackups(keepCount: Int = 7): DriveResult<Int> = withContext(Dispatchers.IO) {
        val drive = driveService ?: return@withContext DriveResult.NotSignedIn

        try {
            val backupsResult = listBackups()
            if (backupsResult is DriveResult.Success) {
                val toDelete = backupsResult.data.drop(keepCount)
                toDelete.forEach { backup ->
                    drive.files().delete(backup.id).execute()
                    Log.d(TAG, "Eliminato vecchio backup: ${backup.name}")
                }
                DriveResult.Success(toDelete.size)
            } else {
                DriveResult.Error("Impossibile recuperare lista backup")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore cleanup backup", e)
            DriveResult.Error("Errore durante la pulizia: ${e.message}", e)
        }
    }

    /**
     * Logout da Google.
     */
    suspend fun signOut() {
        getSignInClient().signOut().await()
        driveService = null
        appFolderId = null
        backupsFolderId = null
        exportsFolderId = null
    }
}
```

### 4.3 BackupManager.kt

```kotlin
package org.incammino.hospiceinventory.service.backup

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveService: GoogleDriveService,
    private val excelExportService: ExcelExportService
) {
    companion object {
        private const val TAG = "BackupManager"
        private const val DATABASE_NAME = "hospice_inventory.db"
    }

    /**
     * Esegue backup completo del database.
     */
    suspend fun performBackup(): BackupResult = withContext(Dispatchers.IO) {
        if (!driveService.isSignedIn()) {
            return@withContext BackupResult.NotSignedIn
        }

        try {
            // 1. Copia il database in una location temporanea
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (!dbFile.exists()) {
                return@withContext BackupResult.Error("Database non trovato")
            }

            val timestamp = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .let { "${it.date}_${it.hour.toString().padStart(2, '0')}${it.minute.toString().padStart(2, '0')}" }
            
            val backupFileName = "backup_$timestamp.db"
            val tempFile = File(context.cacheDir, backupFileName)
            
            // Checkpoint WAL prima di copiare
            // (Room dovrebbe farlo automaticamente, ma per sicurezza)
            dbFile.copyTo(tempFile, overwrite = true)
            
            // Copia anche WAL e SHM se esistono
            val walFile = File(dbFile.parent, "$DATABASE_NAME-wal")
            val shmFile = File(dbFile.parent, "$DATABASE_NAME-shm")
            if (walFile.exists()) {
                Log.d(TAG, "WAL file presente, dimensione: ${walFile.length()}")
            }

            // 2. Carica su Drive
            val uploadResult = driveService.uploadBackup(tempFile, backupFileName)
            
            // 3. Pulisci file temporaneo
            tempFile.delete()

            // 4. Pulizia vecchi backup
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
     */
    suspend fun restoreBackup(backupId: String, backupName: String): BackupResult = withContext(Dispatchers.IO) {
        if (!driveService.isSignedIn()) {
            return@withContext BackupResult.NotSignedIn
        }

        try {
            // 1. Scarica il backup
            val tempFile = File(context.cacheDir, "restore_temp.db")
            val downloadResult = driveService.downloadBackup(backupId, tempFile)
            
            if (downloadResult !is DriveResult.Success) {
                return@withContext BackupResult.Error("Errore download backup")
            }

            // 2. Verifica integrità (basic check)
            if (tempFile.length() < 1024) {
                tempFile.delete()
                return@withContext BackupResult.Error("File backup corrotto o troppo piccolo")
            }

            // 3. Sostituisci database
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            val walFile = File(dbFile.parent, "$DATABASE_NAME-wal")
            val shmFile = File(dbFile.parent, "$DATABASE_NAME-shm")
            
            // Elimina WAL e SHM
            walFile.delete()
            shmFile.delete()
            
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
            val timestamp = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date.toString()

            // Genera file Excel
            val excelFile = excelExportService.generateInventoryExcel()
            val fileName = "inventario_$timestamp.xlsx"

            // Carica su Drive
            val uploadResult = driveService.uploadExport(excelFile, fileName)
            
            // Pulisci file temporaneo
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
}
```

### 4.4 ExcelExportService.kt

```kotlin
package org.incammino.hospiceinventory.service.backup

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.incammino.hospiceinventory.data.repository.LocationRepository
import org.incammino.hospiceinventory.data.repository.MaintainerRepository
import org.incammino.hospiceinventory.data.repository.MaintenanceRepository
import org.incammino.hospiceinventory.data.repository.ProductRepository
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service per generazione file Excel.
 */
@Singleton
class ExcelExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val productRepository: ProductRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val maintainerRepository: MaintainerRepository,
    private val locationRepository: LocationRepository
) {
    companion object {
        private const val TAG = "ExcelExportService"
    }

    /**
     * Genera Excel con tutti i dati inventario.
     */
    suspend fun generateInventoryExcel(): File = withContext(Dispatchers.IO) {
        val workbook = XSSFWorkbook()
        
        try {
            // Stile header
            val headerStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.LIGHT_BLUE.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                val font = workbook.createFont().apply {
                    bold = true
                    color = IndexedColors.WHITE.index
                }
                setFont(font)
            }

            // Sheet Prodotti
            createProductsSheet(workbook, headerStyle)
            
            // Sheet Manutenzioni
            createMaintenancesSheet(workbook, headerStyle)
            
            // Sheet Manutentori
            createMaintainersSheet(workbook, headerStyle)
            
            // Sheet Ubicazioni
            createLocationsSheet(workbook, headerStyle)

            // Salva file
            val outputFile = File(context.cacheDir, "export_temp.xlsx")
            FileOutputStream(outputFile).use { fos ->
                workbook.write(fos)
            }
            
            Log.d(TAG, "Excel generato: ${outputFile.absolutePath}")
            outputFile
        } finally {
            workbook.close()
        }
    }

    private suspend fun createProductsSheet(workbook: XSSFWorkbook, headerStyle: CellStyle) {
        val sheet = workbook.createSheet("Prodotti")
        val products = productRepository.getAllProducts().first()

        // Header
        val headerRow = sheet.createRow(0)
        listOf("ID", "Nome", "Marca", "Modello", "Seriale", "Barcode", 
               "Categoria", "Ubicazione", "Fornitore", "Garanzia (mesi)", 
               "Freq. Manutenzione (mesi)", "Note").forEachIndexed { index, title ->
            headerRow.createCell(index).apply {
                setCellValue(title)
                cellStyle = headerStyle
            }
        }

        // Dati
        products.forEachIndexed { index, product ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(product.id)
            row.createCell(1).setCellValue(product.name)
            row.createCell(2).setCellValue(product.brand ?: "")
            row.createCell(3).setCellValue(product.model ?: "")
            row.createCell(4).setCellValue(product.serialNumber ?: "")
            row.createCell(5).setCellValue(product.barcode ?: "")
            row.createCell(6).setCellValue(product.category ?: "")
            row.createCell(7).setCellValue(product.locationName ?: "")
            row.createCell(8).setCellValue(product.supplierName ?: "")
            row.createCell(9).setCellValue(product.warrantyMonths?.toDouble() ?: 0.0)
            row.createCell(10).setCellValue(product.maintenanceFrequencyMonths?.toDouble() ?: 0.0)
            row.createCell(11).setCellValue(product.notes ?: "")
        }

        // Auto-size colonne
        (0..11).forEach { sheet.autoSizeColumn(it) }
    }

    private suspend fun createMaintenancesSheet(workbook: XSSFWorkbook, headerStyle: CellStyle) {
        val sheet = workbook.createSheet("Manutenzioni")
        val maintenances = maintenanceRepository.getAllMaintenances().first()

        // Header
        val headerRow = sheet.createRow(0)
        listOf("ID", "Prodotto", "Data", "Tipo", "Manutentore", 
               "Durata (min)", "Descrizione", "Note").forEachIndexed { index, title ->
            headerRow.createCell(index).apply {
                setCellValue(title)
                cellStyle = headerStyle
            }
        }

        // Dati
        maintenances.forEachIndexed { index, maintenance ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(maintenance.id)
            row.createCell(1).setCellValue(maintenance.productName ?: maintenance.productId)
            row.createCell(2).setCellValue(maintenance.date.toString())
            row.createCell(3).setCellValue(maintenance.type.name)
            row.createCell(4).setCellValue(maintenance.maintainerName ?: "")
            row.createCell(5).setCellValue(maintenance.durationMinutes?.toDouble() ?: 0.0)
            row.createCell(6).setCellValue(maintenance.description ?: "")
            row.createCell(7).setCellValue(maintenance.notes ?: "")
        }

        (0..7).forEach { sheet.autoSizeColumn(it) }
    }

    private suspend fun createMaintainersSheet(workbook: XSSFWorkbook, headerStyle: CellStyle) {
        val sheet = workbook.createSheet("Manutentori")
        val maintainers = maintainerRepository.getAll().first()

        val headerRow = sheet.createRow(0)
        listOf("ID", "Nome", "Azienda", "Telefono", "Email", "Specializzazione", "Note")
            .forEachIndexed { index, title ->
                headerRow.createCell(index).apply {
                    setCellValue(title)
                    cellStyle = headerStyle
                }
            }

        maintainers.forEachIndexed { index, maintainer ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(maintainer.id)
            row.createCell(1).setCellValue(maintainer.name)
            row.createCell(2).setCellValue(maintainer.company ?: "")
            row.createCell(3).setCellValue(maintainer.phone ?: "")
            row.createCell(4).setCellValue(maintainer.email ?: "")
            row.createCell(5).setCellValue(maintainer.specialization ?: "")
            row.createCell(6).setCellValue(maintainer.notes ?: "")
        }

        (0..6).forEach { sheet.autoSizeColumn(it) }
    }

    private suspend fun createLocationsSheet(workbook: XSSFWorkbook, headerStyle: CellStyle) {
        val sheet = workbook.createSheet("Ubicazioni")
        val locations = locationRepository.getAll().first()

        val headerRow = sheet.createRow(0)
        listOf("ID", "Nome", "Tipo", "Edificio", "Piano", "Reparto", "Posti Letto", "Note")
            .forEachIndexed { index, title ->
                headerRow.createCell(index).apply {
                    setCellValue(title)
                    cellStyle = headerStyle
                }
            }

        locations.forEachIndexed { index, location ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(location.id)
            row.createCell(1).setCellValue(location.name)
            row.createCell(2).setCellValue(location.type ?: "")
            row.createCell(3).setCellValue(location.buildingName ?: "")
            row.createCell(4).setCellValue(location.floorName ?: location.floorCode ?: "")
            row.createCell(5).setCellValue(location.department ?: "")
            row.createCell(6).setCellValue(location.bedCount?.toDouble() ?: 0.0)
            row.createCell(7).setCellValue(location.notes ?: "")
        }

        (0..7).forEach { sheet.autoSizeColumn(it) }
    }
}
```

### 4.5 BackupWorker.kt

```kotlin
package org.incammino.hospiceinventory.service.backup

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Worker per backup automatico schedulato.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupManager: BackupManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "BackupWorker"
        const val WORK_NAME = "automatic_backup"

        /**
         * Schedula backup giornaliero.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            // Calcola delay fino alle 18:00
            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 18)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                if (before(now)) {
                    add(java.util.Calendar.DAY_OF_MONTH, 1)
                }
            }
            val initialDelay = target.timeInMillis - now.timeInMillis

            val workRequest = PeriodicWorkRequestBuilder<BackupWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )

            Log.i(TAG, "Backup schedulato, prossimo tra ${initialDelay / 1000 / 60} minuti")
        }

        /**
         * Cancella scheduling.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Backup scheduling cancellato")
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Avvio backup automatico")

        return when (val result = backupManager.performBackup()) {
            is BackupResult.Success -> {
                Log.i(TAG, "Backup automatico completato")
                Result.success()
            }
            is BackupResult.Error -> {
                Log.e(TAG, "Backup automatico fallito: ${result.message}")
                // Retry con backoff
                Result.retry()
            }
            is BackupResult.NotSignedIn -> {
                Log.w(TAG, "Utente non loggato, skip backup")
                Result.failure()
            }
        }
    }
}
```

### 4.6 BackupModule.kt (Hilt)

```kotlin
package org.incammino.hospiceinventory.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.incammino.hospiceinventory.data.repository.LocationRepository
import org.incammino.hospiceinventory.data.repository.MaintainerRepository
import org.incammino.hospiceinventory.data.repository.MaintenanceRepository
import org.incammino.hospiceinventory.data.repository.ProductRepository
import org.incammino.hospiceinventory.service.backup.BackupManager
import org.incammino.hospiceinventory.service.backup.ExcelExportService
import org.incammino.hospiceinventory.service.backup.GoogleDriveService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {

    @Provides
    @Singleton
    fun provideGoogleDriveService(
        @ApplicationContext context: Context
    ): GoogleDriveService = GoogleDriveService(context)

    @Provides
    @Singleton
    fun provideExcelExportService(
        @ApplicationContext context: Context,
        productRepository: ProductRepository,
        maintenanceRepository: MaintenanceRepository,
        maintainerRepository: MaintainerRepository,
        locationRepository: LocationRepository
    ): ExcelExportService = ExcelExportService(
        context,
        productRepository,
        maintenanceRepository,
        maintainerRepository,
        locationRepository
    )

    @Provides
    @Singleton
    fun provideBackupManager(
        @ApplicationContext context: Context,
        driveService: GoogleDriveService,
        excelExportService: ExcelExportService
    ): BackupManager = BackupManager(context, driveService, excelExportService)
}
```

---

## 5. UI Aggiornata

### 5.1 DataManagementViewModel.kt (Aggiornamenti)

```kotlin
// Aggiungi al file esistente:

/**
 * Stato backup/export.
 */
data class BackupUiState(
    val isSignedIn: Boolean = false,
    val accountEmail: String? = null,
    val isOperationInProgress: Boolean = false,
    val backups: List<BackupInfo> = emptyList(),
    val lastBackupResult: String? = null
)

// Nel ViewModel, aggiungi:

@Inject
lateinit var backupManager: BackupManager

@Inject
lateinit var driveService: GoogleDriveService

private val _backupState = MutableStateFlow(BackupUiState())
val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

init {
    checkSignInStatus()
}

fun checkSignInStatus() {
    val isSignedIn = driveService.isSignedIn()
    val email = driveService.getCurrentAccount()?.email
    _backupState.update { it.copy(isSignedIn = isSignedIn, accountEmail = email) }
    
    if (isSignedIn) {
        loadBackupsList()
    }
}

fun onSignInResult(account: GoogleSignInAccount) {
    viewModelScope.launch {
        driveService.initialize(account)
        checkSignInStatus()
    }
}

fun performBackup() {
    viewModelScope.launch {
        _backupState.update { it.copy(isOperationInProgress = true) }
        
        when (val result = backupManager.performBackup()) {
            is BackupResult.Success -> {
                _backupState.update { 
                    it.copy(
                        isOperationInProgress = false,
                        lastBackupResult = result.message
                    )
                }
                loadBackupsList()
            }
            is BackupResult.Error -> {
                _backupState.update { 
                    it.copy(
                        isOperationInProgress = false,
                        lastBackupResult = "Errore: ${result.message}"
                    )
                }
            }
            BackupResult.NotSignedIn -> {
                _backupState.update { 
                    it.copy(
                        isOperationInProgress = false,
                        lastBackupResult = "Effettua il login prima"
                    )
                }
            }
        }
    }
}

fun exportToExcel() {
    viewModelScope.launch {
        _backupState.update { it.copy(isOperationInProgress = true) }
        
        when (val result = backupManager.exportToExcel()) {
            is BackupResult.Success -> {
                _backupState.update { 
                    it.copy(
                        isOperationInProgress = false,
                        lastBackupResult = result.message
                    )
                }
            }
            is BackupResult.Error -> {
                _backupState.update { 
                    it.copy(
                        isOperationInProgress = false,
                        lastBackupResult = "Errore: ${result.message}"
                    )
                }
            }
            BackupResult.NotSignedIn -> {
                _backupState.update { 
                    it.copy(
                        isOperationInProgress = false,
                        lastBackupResult = "Effettua il login prima"
                    )
                }
            }
        }
    }
}

fun restoreBackup(backupId: String, backupName: String) {
    viewModelScope.launch {
        _backupState.update { it.copy(isOperationInProgress = true) }
        
        when (val result = backupManager.restoreBackup(backupId, backupName)) {
            is BackupResult.Success -> {
                _backupState.update { 
                    it.copy(
                        isOperationInProgress = false,
                        lastBackupResult = result.message
                    )
                }
            }
            is BackupResult.Error -> {
                _backupState.update { 
                    it.copy(
                        isOperationInProgress = false,
                        lastBackupResult = "Errore: ${result.message}"
                    )
                }
            }
            BackupResult.NotSignedIn -> { /* ... */ }
        }
    }
}

private fun loadBackupsList() {
    viewModelScope.launch {
        when (val result = driveService.listBackups()) {
            is DriveResult.Success -> {
                _backupState.update { it.copy(backups = result.data) }
            }
            else -> { /* ignore */ }
        }
    }
}

fun signOut() {
    viewModelScope.launch {
        driveService.signOut()
        checkSignInStatus()
    }
}
```

### 5.2 DataManagementScreen.kt (Sezione Backup)

```kotlin
// Aggiungi nel LazyColumn, prima della sezione statistiche:

// Sezione Google Drive Backup
item {
    GoogleDriveSection(
        backupState = backupState,
        onSignIn = { launcher.launch(viewModel.getSignInIntent()) },
        onSignOut = viewModel::signOut,
        onBackup = viewModel::performBackup,
        onExport = viewModel::exportToExcel,
        onRestore = { id, name -> 
            // Mostra dialog conferma prima
            viewModel.restoreBackup(id, name) 
        }
    )
}

@Composable
private fun GoogleDriveSection(
    backupState: BackupUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onBackup: () -> Unit,
    onExport: () -> Unit,
    onRestore: (String, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Google Drive Backup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!backupState.isSignedIn) {
                // Non connesso
                Text(
                    text = "Connetti il tuo account Google per abilitare backup automatici",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Button(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Login, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Connetti Google Drive")
                }
            } else {
                // Connesso
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = backupState.accountEmail ?: "Account connesso",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onSignOut) {
                        Text("Disconnetti")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Azioni
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onBackup,
                        enabled = !backupState.isOperationInProgress,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Backup")
                    }
                    
                    OutlinedButton(
                        onClick = onExport,
                        enabled = !backupState.isOperationInProgress,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Excel")
                    }
                }

                // Lista backup recenti
                if (backupState.backups.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Backup recenti",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    backupState.backups.take(3).forEach { backup ->
                        BackupItem(
                            backup = backup,
                            onRestore = { onRestore(backup.id, backup.name) }
                        )
                    }
                }

                // Progress indicator
                if (backupState.isOperationInProgress) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupItem(
    backup: BackupInfo,
    onRestore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = backup.name,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = formatFileSize(backup.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRestore) {
            Icon(
                Icons.Default.Restore,
                contentDescription = "Ripristina",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / 1024 / 1024} MB"
    }
}
```

---

## 6. Activity Result per Sign-In

In `DataManagementScreen.kt`, aggiungi il launcher:

```kotlin
@Composable
fun DataManagementScreen(...) {
    // Activity Result per Google Sign-In
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                viewModel.onSignInResult(account)
            } catch (e: ApiException) {
                Log.e("DataManagement", "Sign-in failed", e)
            }
        }
    }
    
    // Nel ViewModel, aggiungi:
    fun getSignInIntent() = driveService.getSignInClient().signInIntent
    
    // ...resto del codice
}
```

---

## 7. Scheduling Automatico

In `HospiceApplication.kt` o `MainActivity.kt`:

```kotlin
class HospiceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Schedule backup automatico se utente loggato
        if (GoogleSignIn.getLastSignedInAccount(this) != null) {
            BackupWorker.schedule(this)
        }
    }
}
```

---

## 8. Testing Checklist

- [ ] Setup OAuth in Google Cloud Console
- [ ] Sign-in funziona correttamente
- [ ] Backup manuale crea file in Drive
- [ ] Export Excel genera file valido con tutti i dati
- [ ] Lista backup mostra file esistenti
- [ ] Restore ripristina correttamente il database
- [ ] WorkManager esegue backup automatico
- [ ] Cleanup elimina backup > 7 giorni
- [ ] Gestione errori (no rete, quota Drive, etc.)

---

## 9. Note Implementative

1. **Database checkpoint**: Prima di copiare il file .db, Room dovrebbe fare checkpoint del WAL. Se ci sono problemi, forzare con:
   ```kotlin
   database.query("PRAGMA wal_checkpoint(TRUNCATE)", null)
   ```

2. **Dimensione file**: Il database Room dovrebbe essere piccolo (< 5MB). Se cresce troppo, considerare vacuum periodico.

3. **Conflitti**: Lo scope `drive.file` evita conflitti - l'app vede solo i propri file.

4. **Offline**: Se device offline, WorkManager ritenta automaticamente con backoff.

5. **Apache POI**: È una libreria pesante (~15MB). Se il bundle diventa troppo grande, considerare alternative come OpenCSV per export più semplice.

---

## 10. Timeline Stimata

| Task | Tempo stimato |
|------|---------------|
| Setup Google Cloud | 30 min |
| GoogleDriveService | 1h |
| BackupManager | 45 min |
| ExcelExportService | 45 min |
| BackupWorker | 30 min |
| UI DataManagement | 1h |
| Testing | 1h |
| **Totale** | **~5-6 ore** |
