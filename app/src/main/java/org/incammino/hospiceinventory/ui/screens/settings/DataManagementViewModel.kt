package org.incammino.hospiceinventory.ui.screens.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.incammino.hospiceinventory.data.repository.LocationRepository
import org.incammino.hospiceinventory.data.repository.MaintainerRepository
import org.incammino.hospiceinventory.data.repository.MaintenanceRepository
import org.incammino.hospiceinventory.data.repository.ProductRepository
import org.incammino.hospiceinventory.service.backup.BackupInfo
import org.incammino.hospiceinventory.service.backup.BackupManager
import org.incammino.hospiceinventory.service.backup.BackupResult
import org.incammino.hospiceinventory.service.backup.GoogleDriveService
import javax.inject.Inject

/**
 * Tipo di operazione di pulizia.
 */
enum class CleanupOperation {
    SAMPLE_DATA,
    ALL_PRODUCTS,
    ALL_MAINTAINERS,
    ALL_MAINTENANCES,
    ALL_LOCATIONS,
    FULL_RESET
}

/**
 * UI State per DataManagementScreen.
 */
data class DataManagementUiState(
    val isLoading: Boolean = false,
    val productCount: Int = 0,
    val maintainerCount: Int = 0,
    val maintenanceCount: Int = 0,
    val locationCount: Int = 0,
    val showConfirmDialog: Boolean = false,
    val pendingOperation: CleanupOperation? = null,
    val operationInProgress: Boolean = false,
    val lastOperationResult: String? = null,
    val error: String? = null
)

/**
 * UI State per backup Google Drive.
 */
data class BackupUiState(
    val isSignedIn: Boolean = false,
    val accountEmail: String? = null,
    val isOperationInProgress: Boolean = false,
    val backups: List<BackupInfo> = emptyList(),
    val lastBackupResult: String? = null,
    val showRestoreConfirmDialog: Boolean = false,
    val pendingRestoreBackup: BackupInfo? = null
)

/**
 * ViewModel per la gestione dati.
 */
@HiltViewModel
class DataManagementViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val maintainerRepository: MaintainerRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val locationRepository: LocationRepository,
    private val backupManager: BackupManager,
    private val driveService: GoogleDriveService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataManagementUiState())
    val uiState: StateFlow<DataManagementUiState> = _uiState.asStateFlow()

    private val _backupState = MutableStateFlow(BackupUiState())
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    init {
        loadCounts()
        checkSignInStatus()
    }

    /**
     * Carica i conteggi delle entità.
     */
    fun loadCounts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val productCount = productRepository.countAll()
                val maintainerCount = maintainerRepository.countAll()
                val maintenanceCount = maintenanceRepository.count()
                val locationCount = locationRepository.countAll()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        productCount = productCount,
                        maintainerCount = maintainerCount,
                        maintenanceCount = maintenanceCount,
                        locationCount = locationCount,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Errore nel caricamento: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Richiede conferma per un'operazione.
     */
    fun requestConfirmation(operation: CleanupOperation) {
        _uiState.update {
            it.copy(
                showConfirmDialog = true,
                pendingOperation = operation
            )
        }
    }

    /**
     * Annulla l'operazione pendente.
     */
    fun cancelOperation() {
        _uiState.update {
            it.copy(
                showConfirmDialog = false,
                pendingOperation = null
            )
        }
    }

    /**
     * Conferma ed esegue l'operazione.
     */
    fun confirmOperation() {
        val operation = _uiState.value.pendingOperation ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showConfirmDialog = false,
                    operationInProgress = true,
                    lastOperationResult = null
                )
            }

            try {
                when (operation) {
                    CleanupOperation.SAMPLE_DATA -> clearSampleDataOnly()
                    CleanupOperation.ALL_PRODUCTS -> clearProducts()
                    CleanupOperation.ALL_MAINTAINERS -> clearMaintainers()
                    CleanupOperation.ALL_MAINTENANCES -> clearMaintenances()
                    CleanupOperation.ALL_LOCATIONS -> clearLocations()
                    CleanupOperation.FULL_RESET -> clearAllData()
                }

                _uiState.update {
                    it.copy(
                        operationInProgress = false,
                        pendingOperation = null,
                        lastOperationResult = "Operazione completata con successo"
                    )
                }

                // Ricarica i conteggi
                loadCounts()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        operationInProgress = false,
                        pendingOperation = null,
                        error = "Errore durante l'operazione: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Elimina solo i dati di esempio (pattern prod-*, maint-*, loc-*).
     */
    private suspend fun clearSampleDataOnly() {
        // Prima le manutenzioni (dipendenze)
        maintenanceRepository.deleteByProductIdPattern("prod-%")

        // Poi prodotti
        productRepository.deleteByIdPattern("prod-%")

        // Poi manutentori
        maintainerRepository.deleteByIdPattern("maint-%")

        // Poi locations
        locationRepository.deleteByIdPattern("loc-%")
    }

    /**
     * Elimina tutti i prodotti e relative manutenzioni.
     */
    private suspend fun clearProducts() {
        maintenanceRepository.deleteAll()
        productRepository.deleteAll()
    }

    /**
     * Elimina tutti i manutentori.
     */
    private suspend fun clearMaintainers() {
        maintainerRepository.deleteAll()
    }

    /**
     * Elimina tutte le manutenzioni.
     */
    private suspend fun clearMaintenances() {
        maintenanceRepository.deleteAll()
    }

    /**
     * Elimina tutte le ubicazioni.
     */
    private suspend fun clearLocations() {
        locationRepository.deleteAll()
    }

    /**
     * Reset completo del database.
     */
    private suspend fun clearAllData() {
        maintenanceRepository.deleteAll()
        productRepository.deleteAll()
        maintainerRepository.deleteAll()
        locationRepository.deleteAll()
    }

    /**
     * Pulisce l'errore.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Pulisce il messaggio di risultato.
     */
    fun clearResult() {
        _uiState.update { it.copy(lastOperationResult = null) }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GOOGLE DRIVE BACKUP
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Verifica lo stato del login Google.
     */
    fun checkSignInStatus() {
        val isSignedIn = driveService.isSignedIn()
        val email = driveService.getCurrentAccount()?.email
        _backupState.update { it.copy(isSignedIn = isSignedIn, accountEmail = email) }

        if (isSignedIn) {
            loadBackupsList()
        }
    }

    /**
     * Ottiene l'intent per il sign-in Google.
     */
    fun getSignInIntent(): Intent = driveService.getSignInIntent()

    /**
     * Callback dopo sign-in riuscito.
     */
    fun onSignInResult(account: GoogleSignInAccount) {
        viewModelScope.launch {
            driveService.initialize(account)
            checkSignInStatus()
        }
    }

    /**
     * Esegue backup manuale.
     */
    fun performBackup() {
        viewModelScope.launch {
            _backupState.update { it.copy(isOperationInProgress = true, lastBackupResult = null) }

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

    /**
     * Esporta dati in Excel e carica su Drive.
     */
    fun exportToExcel() {
        viewModelScope.launch {
            _backupState.update { it.copy(isOperationInProgress = true, lastBackupResult = null) }

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

    /**
     * Richiede conferma per il ripristino.
     */
    fun requestRestoreConfirmation(backup: BackupInfo) {
        _backupState.update {
            it.copy(
                showRestoreConfirmDialog = true,
                pendingRestoreBackup = backup
            )
        }
    }

    /**
     * Annulla il ripristino pendente.
     */
    fun cancelRestore() {
        _backupState.update {
            it.copy(
                showRestoreConfirmDialog = false,
                pendingRestoreBackup = null
            )
        }
    }

    /**
     * Conferma ed esegue il ripristino.
     */
    fun confirmRestore() {
        val backup = _backupState.value.pendingRestoreBackup ?: return

        viewModelScope.launch {
            _backupState.update {
                it.copy(
                    showRestoreConfirmDialog = false,
                    isOperationInProgress = true,
                    lastBackupResult = null
                )
            }

            when (val result = backupManager.restoreBackup(backup.id, backup.name)) {
                is BackupResult.Success -> {
                    _backupState.update {
                        it.copy(
                            isOperationInProgress = false,
                            pendingRestoreBackup = null,
                            lastBackupResult = result.message
                        )
                    }
                }
                is BackupResult.Error -> {
                    _backupState.update {
                        it.copy(
                            isOperationInProgress = false,
                            pendingRestoreBackup = null,
                            lastBackupResult = "Errore: ${result.message}"
                        )
                    }
                }
                BackupResult.NotSignedIn -> {
                    _backupState.update {
                        it.copy(
                            isOperationInProgress = false,
                            pendingRestoreBackup = null,
                            lastBackupResult = "Effettua il login prima"
                        )
                    }
                }
            }
        }
    }

    /**
     * Carica la lista dei backup disponibili.
     */
    private fun loadBackupsList() {
        viewModelScope.launch {
            val backups = backupManager.listBackups()
            _backupState.update { it.copy(backups = backups) }
        }
    }

    /**
     * Logout da Google Drive.
     */
    fun signOut() {
        viewModelScope.launch {
            driveService.signOut()
            checkSignInStatus()
            _backupState.update { it.copy(backups = emptyList()) }
        }
    }

    /**
     * Pulisce il risultato backup.
     */
    fun clearBackupResult() {
        _backupState.update { it.copy(lastBackupResult = null) }
    }
}
