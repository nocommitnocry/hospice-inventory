package org.incammino.hospiceinventory.ui.screens.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.incammino.hospiceinventory.data.repository.ProductRepository
import org.incammino.hospiceinventory.domain.model.Product
import javax.inject.Inject

/**
 * UI State per la SearchScreen.
 */
data class SearchUiState(
    val query: String = "",
    val results: List<Product> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val isLoading: Boolean = false,
    val isEmpty: Boolean = false,
    val hasSearched: Boolean = false
)

/**
 * ViewModel per la schermata di ricerca.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _query = MutableStateFlow(savedStateHandle.get<String>("query") ?: "")
    private val _selectedCategory = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
        setupSearch()
    }

    /**
     * Carica le categorie disponibili per il filtro.
     */
    private fun loadCategories() {
        viewModelScope.launch {
            productRepository.getAllCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
    }

    /**
     * Configura la ricerca reattiva con debounce.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun setupSearch() {
        viewModelScope.launch {
            combine(_query.debounce(300), _selectedCategory) { query, category ->
                Pair(query, category)
            }.flatMapLatest { (query, category) ->
                _uiState.update { it.copy(isLoading = true, query = query, selectedCategory = category) }

                when {
                    query.isNotBlank() -> productRepository.search(query)
                    category != null -> productRepository.getByCategory(category)
                    else -> productRepository.getAllActive()
                }
            }.collect { products ->
                val filtered = _selectedCategory.value?.let { cat ->
                    products.filter { it.category == cat }
                } ?: products

                _uiState.update {
                    it.copy(
                        results = filtered,
                        isLoading = false,
                        isEmpty = filtered.isEmpty() && (_query.value.isNotBlank() || _selectedCategory.value != null),
                        hasSearched = _query.value.isNotBlank() || _selectedCategory.value != null
                    )
                }
            }
        }
    }

    /**
     * Aggiorna la query di ricerca.
     */
    fun updateQuery(query: String) {
        _query.value = query
        _uiState.update { it.copy(query = query) }
    }

    /**
     * Seleziona/deseleziona una categoria.
     */
    fun toggleCategory(category: String) {
        val newCategory = if (_selectedCategory.value == category) null else category
        _selectedCategory.value = newCategory
        _uiState.update { it.copy(selectedCategory = newCategory) }
    }

    /**
     * Pulisce la ricerca.
     */
    fun clearSearch() {
        _query.value = ""
        _selectedCategory.value = null
        _uiState.update { it.copy(query = "", selectedCategory = null) }
    }
}
