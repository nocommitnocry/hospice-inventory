package org.incammino.hospiceinventory.ui.screens.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.incammino.hospiceinventory.data.repository.MaintainerRepository
import org.incammino.hospiceinventory.data.repository.ProductRepository
import org.incammino.hospiceinventory.domain.model.AccountType
import org.incammino.hospiceinventory.domain.model.Maintainer
import org.incammino.hospiceinventory.domain.model.MaintenanceFrequency
import org.incammino.hospiceinventory.domain.model.Product
import java.util.UUID
import javax.inject.Inject

/**
 * UI State per ProductEditScreen.
 */
data class ProductEditUiState(
    // Dati form
    val name: String = "",
    val barcode: String = "",
    val category: String = "",
    val location: String = "",
    val description: String = "",
    val supplier: String = "",
    val price: String = "",
    val accountType: AccountType? = null,
    val notes: String = "",

    // Garanzia
    val warrantyEndDate: LocalDate? = null,
    val warrantyMaintainerId: String? = null,

    // Manutenzione
    val maintenanceFrequency: MaintenanceFrequency? = null,
    val customIntervalDays: String = "",
    val serviceMaintainerId: String? = null,

    // Metadati
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedProductId: String? = null,

    // Dati di supporto
    val categories: List<String> = emptyList(),
    val locations: List<String> = emptyList(),
    val maintainers: List<Maintainer> = emptyList()
)

/**
 * ViewModel per la creazione/modifica prodotto.
 */
@HiltViewModel
class ProductEditViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val maintainerRepository: MaintainerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val productId: String? = savedStateHandle["productId"]

    private val _uiState = MutableStateFlow(ProductEditUiState(isNew = productId == null))
    val uiState: StateFlow<ProductEditUiState> = _uiState.asStateFlow()

    init {
        loadSupportData()
        if (productId != null) {
            loadProduct(productId)
        }
    }

    /**
     * Carica categorie, ubicazioni e manutentori.
     */
    private fun loadSupportData() {
        viewModelScope.launch {
            productRepository.getAllCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }

        viewModelScope.launch {
            productRepository.getAllLocations().collect { locations ->
                _uiState.update { it.copy(locations = locations) }
            }
        }

        viewModelScope.launch {
            maintainerRepository.getAllActive().collect { maintainers ->
                _uiState.update { it.copy(maintainers = maintainers) }
            }
        }
    }

    /**
     * Carica il prodotto esistente per la modifica.
     */
    private fun loadProduct(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            productRepository.getByIdFlow(id).collect { product ->
                if (product != null) {
                    _uiState.update {
                        it.copy(
                            name = product.name,
                            barcode = product.barcode ?: "",
                            category = product.category,
                            location = product.location,
                            description = product.description ?: "",
                            supplier = product.supplier ?: "",
                            price = product.price?.toString() ?: "",
                            accountType = product.accountType,
                            notes = product.notes ?: "",
                            warrantyEndDate = product.warrantyEndDate,
                            warrantyMaintainerId = product.warrantyMaintainerId,
                            maintenanceFrequency = product.maintenanceFrequency,
                            customIntervalDays = product.maintenanceIntervalDays?.toString() ?: "",
                            serviceMaintainerId = product.serviceMaintainerId,
                            isNew = false,
                            isLoading = false,
                            error = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Prodotto non trovato"
                        )
                    }
                }
            }
        }
    }

    // Funzioni di aggiornamento campi
    fun updateName(value: String) {
        _uiState.update { it.copy(name = value) }
    }

    fun updateBarcode(value: String) {
        _uiState.update { it.copy(barcode = value) }
    }

    fun updateCategory(value: String) {
        _uiState.update { it.copy(category = value) }
    }

    fun updateLocation(value: String) {
        _uiState.update { it.copy(location = value) }
    }

    fun updateDescription(value: String) {
        _uiState.update { it.copy(description = value) }
    }

    fun updateSupplier(value: String) {
        _uiState.update { it.copy(supplier = value) }
    }

    fun updatePrice(value: String) {
        _uiState.update { it.copy(price = value) }
    }

    fun updateAccountType(value: AccountType?) {
        _uiState.update { it.copy(accountType = value) }
    }

    fun updateNotes(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun updateWarrantyEndDate(value: LocalDate?) {
        _uiState.update { it.copy(warrantyEndDate = value) }
    }

    fun updateWarrantyMaintainer(id: String?) {
        _uiState.update { it.copy(warrantyMaintainerId = id) }
    }

    fun updateMaintenanceFrequency(value: MaintenanceFrequency?) {
        _uiState.update { it.copy(maintenanceFrequency = value) }
    }

    fun updateCustomIntervalDays(value: String) {
        _uiState.update { it.copy(customIntervalDays = value) }
    }

    fun updateServiceMaintainer(id: String?) {
        _uiState.update { it.copy(serviceMaintainerId = id) }
    }

    /**
     * Valida il form.
     */
    private fun validate(): Boolean {
        val state = _uiState.value

        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "Il nome è obbligatorio") }
            return false
        }

        if (state.category.isBlank()) {
            _uiState.update { it.copy(error = "La categoria è obbligatoria") }
            return false
        }

        if (state.location.isBlank()) {
            _uiState.update { it.copy(error = "L'ubicazione è obbligatoria") }
            return false
        }

        // Verifica prezzo se inserito
        if (state.price.isNotBlank()) {
            state.price.toDoubleOrNull() ?: run {
                _uiState.update { it.copy(error = "Prezzo non valido") }
                return false
            }
        }

        // Verifica intervallo custom
        if (state.maintenanceFrequency == MaintenanceFrequency.CUSTOM && state.customIntervalDays.isNotBlank()) {
            state.customIntervalDays.toIntOrNull() ?: run {
                _uiState.update { it.copy(error = "Intervallo manutenzione non valido") }
                return false
            }
        }

        return true
    }

    /**
     * Salva il prodotto.
     */
    fun save() {
        if (!validate()) return

        val state = _uiState.value

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            try {
                val now = Clock.System.now()
                val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date

                val product = Product(
                    id = productId ?: UUID.randomUUID().toString(),
                    barcode = state.barcode.takeIf { it.isNotBlank() },
                    name = state.name.trim(),
                    description = state.description.takeIf { it.isNotBlank() },
                    category = state.category.trim(),
                    location = state.location.trim(),
                    assigneeId = null,
                    warrantyMaintainerId = state.warrantyMaintainerId,
                    warrantyStartDate = null, // Da implementare se necessario
                    warrantyEndDate = state.warrantyEndDate,
                    serviceMaintainerId = state.serviceMaintainerId,
                    maintenanceFrequency = state.maintenanceFrequency,
                    maintenanceStartDate = if (state.maintenanceFrequency != null) today else null,
                    maintenanceIntervalDays = if (state.maintenanceFrequency == MaintenanceFrequency.CUSTOM) {
                        state.customIntervalDays.toIntOrNull()
                    } else null,
                    lastMaintenanceDate = null,
                    nextMaintenanceDue = calculateNextMaintenance(state, today),
                    purchaseDate = null, // Da implementare se necessario
                    price = state.price.toDoubleOrNull(),
                    accountType = state.accountType,
                    supplier = state.supplier.takeIf { it.isNotBlank() },
                    invoiceNumber = null,
                    imageUri = null,
                    notes = state.notes.takeIf { it.isNotBlank() },
                    isActive = true
                )

                if (state.isNew) {
                    productRepository.insert(product)
                } else {
                    productRepository.update(product)
                }

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        savedProductId = product.id
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
     * Calcola la prossima data di manutenzione.
     */
    private fun calculateNextMaintenance(state: ProductEditUiState, today: LocalDate): LocalDate? {
        val frequency = state.maintenanceFrequency ?: return null

        val days = if (frequency == MaintenanceFrequency.CUSTOM) {
            state.customIntervalDays.toIntOrNull() ?: return null
        } else {
            frequency.days
        }

        return today.plus(DatePeriod(days = days))
    }

    /**
     * Pulisce l'errore.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
