package org.incammino.hospiceinventory.ui.screens.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.incammino.hospiceinventory.data.repository.ProductRepository
import org.incammino.hospiceinventory.domain.model.Product
import javax.inject.Inject

/**
 * Stato del risultato della scansione barcode.
 */
sealed class BarcodeResultState {
    data object Loading : BarcodeResultState()
    data class Found(val product: Product) : BarcodeResultState()
    data class NotFound(val barcode: String) : BarcodeResultState()
    data class Error(val message: String) : BarcodeResultState()
}

/**
 * ViewModel per BarcodeResultScreen.
 * Cerca il prodotto nel database in base al barcode scansionato.
 */
@HiltViewModel
class BarcodeResultViewModel @Inject constructor(
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BarcodeResultState>(BarcodeResultState.Loading)
    val uiState: StateFlow<BarcodeResultState> = _uiState.asStateFlow()

    /**
     * Cerca un prodotto per barcode.
     */
    fun searchByBarcode(barcode: String) {
        viewModelScope.launch {
            _uiState.value = BarcodeResultState.Loading

            try {
                val product = productRepository.getByBarcode(barcode)

                _uiState.value = if (product != null) {
                    BarcodeResultState.Found(product)
                } else {
                    BarcodeResultState.NotFound(barcode)
                }
            } catch (e: Exception) {
                _uiState.value = BarcodeResultState.Error(
                    "Errore nella ricerca: ${e.message}"
                )
            }
        }
    }
}
