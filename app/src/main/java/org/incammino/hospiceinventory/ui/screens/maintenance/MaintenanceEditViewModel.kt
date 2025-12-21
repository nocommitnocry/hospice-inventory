package org.incammino.hospiceinventory.ui.screens.maintenance

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.incammino.hospiceinventory.data.repository.MaintainerRepository
import org.incammino.hospiceinventory.data.repository.MaintenanceRepository
import org.incammino.hospiceinventory.data.repository.ProductRepository
import org.incammino.hospiceinventory.domain.model.Maintainer
import org.incammino.hospiceinventory.domain.model.Maintenance
import org.incammino.hospiceinventory.domain.model.MaintenanceOutcome
import org.incammino.hospiceinventory.domain.model.MaintenanceType
import org.incammino.hospiceinventory.domain.model.Product
import java.util.UUID
import javax.inject.Inject

/**
 * UI State per MaintenanceEditScreen.
 */
data class MaintenanceEditUiState(
    // Prodotto
    val productId: String? = null,
    val productName: String = "",
    val productCategory: String = "",
    val searchQuery: String = "",
    val searchResults: List<Product> = emptyList(),
    val showProductSearch: Boolean = false,

    // Dati manutenzione
    val date: LocalDate = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date,
    val type: MaintenanceType? = null,
    val outcome: MaintenanceOutcome? = null,
    val notes: String = "",

    // Costi
    val cost: String = "",
    val invoiceNumber: String = "",
    val isWarrantyWork: Boolean = false,

    // Manutentore
    val maintainerId: String? = null,
    val maintainerName: String? = null,

    // Liste per dropdown
    val availableMaintainers: List<Maintainer> = emptyList(),
    val maintenanceTypes: List<MaintenanceType> = MaintenanceType.entries,
    val outcomeTypes: List<MaintenanceOutcome> = MaintenanceOutcome.entries,

    // UI state
    val showDatePicker: Boolean = false,
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMaintenanceId: String? = null
)

/**
 * ViewModel per la creazione/modifica manutenzione.
 */
@HiltViewModel
class MaintenanceEditViewModel @Inject constructor(
    private val maintenanceRepository: MaintenanceRepository,
    private val productRepository: ProductRepository,
    private val maintainerRepository: MaintainerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val maintenanceId: String? = savedStateHandle.get<String>("maintenanceId")
        ?.takeIf { it != "new" }
    private val preselectedProductId: String? = savedStateHandle.get<String>("productId")
        ?.takeIf { it.isNotEmpty() }

    private val _uiState = MutableStateFlow(MaintenanceEditUiState(isNew = maintenanceId == null))
    val uiState: StateFlow<MaintenanceEditUiState> = _uiState.asStateFlow()

    init {
        loadMaintainers()

        if (maintenanceId != null) {
            loadMaintenance(maintenanceId)
        } else if (preselectedProductId != null) {
            loadProduct(preselectedProductId)
        }
    }

    /**
     * Carica i manutentori disponibili.
     */
    private fun loadMaintainers() {
        viewModelScope.launch {
            maintainerRepository.getAllActive().collect { maintainers ->
                _uiState.update { it.copy(availableMaintainers = maintainers) }
            }
        }
    }

    /**
     * Carica la manutenzione esistente per la modifica.
     */
    private fun loadMaintenance(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val maintenance = maintenanceRepository.getById(id)
                if (maintenance != null) {
                    // Carica anche il prodotto
                    val product = productRepository.getById(maintenance.productId)
                    val maintainer = maintenance.maintainerId?.let {
                        maintainerRepository.getById(it)
                    }

                    val maintenanceDate = maintenance.date
                        .toLocalDateTime(TimeZone.currentSystemDefault()).date

                    _uiState.update {
                        it.copy(
                            productId = maintenance.productId,
                            productName = product?.name ?: "",
                            productCategory = product?.category ?: "",
                            date = maintenanceDate,
                            type = maintenance.type,
                            outcome = maintenance.outcome,
                            notes = maintenance.notes ?: "",
                            cost = maintenance.cost?.toString() ?: "",
                            invoiceNumber = maintenance.invoiceNumber ?: "",
                            isWarrantyWork = maintenance.isWarrantyWork,
                            maintainerId = maintenance.maintainerId,
                            maintainerName = maintainer?.name,
                            isNew = false,
                            isLoading = false,
                            error = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Manutenzione non trovata"
                        )
                    }
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
     * Carica il prodotto preselezionato.
     */
    private fun loadProduct(productId: String) {
        viewModelScope.launch {
            try {
                val product = productRepository.getById(productId)
                if (product != null) {
                    _uiState.update {
                        it.copy(
                            productId = product.id,
                            productName = product.name,
                            productCategory = product.category,
                            showProductSearch = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Errore nel caricamento prodotto: ${e.message}")
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RICERCA PRODOTTO
    // ═══════════════════════════════════════════════════════════════════════════

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.length >= 2) {
            searchProducts(query)
        } else {
            _uiState.update { it.copy(searchResults = emptyList()) }
        }
    }

    private fun searchProducts(query: String) {
        viewModelScope.launch {
            try {
                val results = productRepository.searchSync(query)
                _uiState.update { it.copy(searchResults = results) }
            } catch (e: Exception) {
                // Ignora errori di ricerca
            }
        }
    }

    fun selectProduct(product: Product) {
        _uiState.update {
            it.copy(
                productId = product.id,
                productName = product.name,
                productCategory = product.category,
                searchQuery = "",
                searchResults = emptyList(),
                showProductSearch = false
            )
        }
    }

    fun clearProduct() {
        _uiState.update {
            it.copy(
                productId = null,
                productName = "",
                productCategory = "",
                showProductSearch = true
            )
        }
    }

    fun showProductSearch() {
        _uiState.update { it.copy(showProductSearch = true) }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AGGIORNAMENTO CAMPI
    // ═══════════════════════════════════════════════════════════════════════════

    fun updateDate(date: LocalDate) {
        _uiState.update { it.copy(date = date) }
    }

    fun updateType(type: MaintenanceType?) {
        _uiState.update { it.copy(type = type) }
    }

    fun updateOutcome(outcome: MaintenanceOutcome?) {
        _uiState.update { it.copy(outcome = outcome) }
    }

    fun updateNotes(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun updateCost(cost: String) {
        _uiState.update { it.copy(cost = cost) }
    }

    fun updateInvoiceNumber(invoiceNumber: String) {
        _uiState.update { it.copy(invoiceNumber = invoiceNumber) }
    }

    fun updateIsWarrantyWork(isWarrantyWork: Boolean) {
        _uiState.update { it.copy(isWarrantyWork = isWarrantyWork) }
    }

    fun updateMaintainer(maintainerId: String?, maintainerName: String?) {
        _uiState.update { it.copy(maintainerId = maintainerId, maintainerName = maintainerName) }
    }

    fun toggleDatePicker(show: Boolean) {
        _uiState.update { it.copy(showDatePicker = show) }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VALIDAZIONE E SALVATAGGIO
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Valida il form.
     */
    private fun validate(): Boolean {
        val state = _uiState.value

        if (state.productId == null) {
            _uiState.update { it.copy(error = "Seleziona un prodotto") }
            return false
        }

        if (state.type == null) {
            _uiState.update { it.copy(error = "Seleziona il tipo di intervento") }
            return false
        }

        // Verifica costo se inserito
        if (state.cost.isNotBlank()) {
            state.cost.toDoubleOrNull() ?: run {
                _uiState.update { it.copy(error = "Costo non valido") }
                return false
            }
        }

        return true
    }

    /**
     * Salva la manutenzione.
     */
    fun save() {
        if (!validate()) return

        val state = _uiState.value

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            try {
                val now = Clock.System.now()

                // Converti la data in Instant
                val dateInstant = kotlinx.datetime.LocalDateTime(
                    state.date.year,
                    state.date.monthNumber,
                    state.date.dayOfMonth,
                    12, 0, 0
                ).toInstant(TimeZone.currentSystemDefault())

                val maintenance = Maintenance(
                    id = maintenanceId ?: UUID.randomUUID().toString(),
                    productId = state.productId!!,
                    maintainerId = state.maintainerId,
                    date = dateInstant,
                    type = state.type!!,
                    outcome = state.outcome,
                    notes = state.notes.takeIf { it.isNotBlank() },
                    cost = if (state.isWarrantyWork) null else state.cost.toDoubleOrNull(),
                    invoiceNumber = if (state.isWarrantyWork) null else state.invoiceNumber.takeIf { it.isNotBlank() },
                    isWarrantyWork = state.isWarrantyWork,
                    requestEmailSent = false,
                    reportEmailSent = false
                )

                val id = if (state.isNew) {
                    maintenanceRepository.insert(maintenance, updateProductDates = true)
                } else {
                    maintenanceRepository.update(maintenance)
                    maintenance.id
                }

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        savedMaintenanceId = id
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = "Errore durante il salvataggio: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Pulisce l'errore.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
