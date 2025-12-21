package org.incammino.hospiceinventory.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * ViewModel per la gestione dati.
 */
@HiltViewModel
class DataManagementViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val maintainerRepository: MaintainerRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataManagementUiState())
    val uiState: StateFlow<DataManagementUiState> = _uiState.asStateFlow()

    init {
        loadCounts()
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
}
