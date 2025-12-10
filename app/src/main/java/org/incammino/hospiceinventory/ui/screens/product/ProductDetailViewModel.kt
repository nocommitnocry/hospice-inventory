package org.incammino.hospiceinventory.ui.screens.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.incammino.hospiceinventory.data.repository.MaintainerRepository
import org.incammino.hospiceinventory.data.repository.MaintenanceRepository
import org.incammino.hospiceinventory.data.repository.ProductRepository
import org.incammino.hospiceinventory.domain.model.Maintainer
import org.incammino.hospiceinventory.domain.model.Maintenance
import org.incammino.hospiceinventory.domain.model.Product
import javax.inject.Inject

/**
 * UI State per ProductDetailScreen.
 */
data class ProductDetailUiState(
    val product: Product? = null,
    val warrantyMaintainer: Maintainer? = null,
    val serviceMaintainer: Maintainer? = null,
    val maintenanceHistory: List<Maintenance> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * ViewModel per il dettaglio prodotto.
 */
@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val maintainerRepository: MaintainerRepository,
    private val maintenanceRepository: MaintenanceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val productId: String = checkNotNull(savedStateHandle["productId"])

    private val _uiState = MutableStateFlow(ProductDetailUiState())
    val uiState: StateFlow<ProductDetailUiState> = _uiState.asStateFlow()

    init {
        loadProduct()
    }

    /**
     * Carica i dati del prodotto.
     */
    private fun loadProduct() {
        viewModelScope.launch {
            productRepository.getByIdFlow(productId).collect { product ->
                if (product != null) {
                    _uiState.update {
                        it.copy(
                            product = product,
                            isLoading = false,
                            error = null
                        )
                    }
                    // Carica i dati correlati
                    loadRelatedData(product)
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

    /**
     * Carica manutentori e storico manutenzioni.
     */
    private fun loadRelatedData(product: Product) {
        // Manutentore garanzia
        product.warrantyMaintainerId?.let { id ->
            viewModelScope.launch {
                val maintainer = maintainerRepository.getById(id)
                _uiState.update { it.copy(warrantyMaintainer = maintainer) }
            }
        }

        // Manutentore service
        product.serviceMaintainerId?.let { id ->
            viewModelScope.launch {
                val maintainer = maintainerRepository.getById(id)
                _uiState.update { it.copy(serviceMaintainer = maintainer) }
            }
        }

        // Storico manutenzioni
        viewModelScope.launch {
            maintenanceRepository.getByProduct(productId).collect { history ->
                _uiState.update { it.copy(maintenanceHistory = history) }
            }
        }
    }

    /**
     * Elimina il prodotto (soft delete).
     */
    fun deleteProduct(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                productRepository.softDelete(productId)
                onDeleted()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Errore durante l'eliminazione") }
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
