package org.incammino.hospiceinventory.service.backup

import android.content.Context
import android.content.Intent
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import com.google.api.services.drive.model.File as DriveFile

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
 * Gestisce autenticazione, upload/download file, e struttura cartelle.
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
     * Ottiene l'intent per il sign-in.
     */
    fun getSignInIntent(): Intent = getSignInClient().signInIntent

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

            Log.i(TAG, "Drive service inizializzato per: ${account.email}")
            DriveResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Errore inizializzazione Drive", e)
            DriveResult.Error("Impossibile inizializzare Google Drive: ${e.message}", e)
        }
    }

    /**
     * Inizializza automaticamente se l'utente è già loggato.
     */
    suspend fun initializeIfSignedIn(): Boolean {
        val account = getCurrentAccount() ?: return false
        if (!GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
            return false
        }
        return when (initialize(account)) {
            is DriveResult.Success -> true
            else -> false
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

            Log.i(TAG, "Backup caricato: ${uploadedFile.name} (${uploadedFile.id})")
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

            Log.i(TAG, "Export caricato: ${uploadedFile.name} (${uploadedFile.id})")
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

            Log.d(TAG, "Trovati ${backups.size} backup")
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

            Log.i(TAG, "Backup scaricato: ${destinationFile.absolutePath}")
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
                var deletedCount = 0
                toDelete.forEach { backup ->
                    try {
                        drive.files().delete(backup.id).execute()
                        Log.d(TAG, "Eliminato vecchio backup: ${backup.name}")
                        deletedCount++
                    } catch (e: Exception) {
                        Log.w(TAG, "Impossibile eliminare backup ${backup.name}: ${e.message}")
                    }
                }
                DriveResult.Success(deletedCount)
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
        try {
            getSignInClient().signOut().await()
        } catch (e: Exception) {
            Log.w(TAG, "Errore durante sign out: ${e.message}")
        }
        driveService = null
        appFolderId = null
        backupsFolderId = null
        exportsFolderId = null
        Log.i(TAG, "Disconnesso da Google Drive")
    }
}
