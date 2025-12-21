package org.incammino.hospiceinventory.ui.screens.maintainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.incammino.hospiceinventory.data.repository.MaintainerRepository
import org.incammino.hospiceinventory.domain.model.Maintainer
import javax.inject.Inject

/**
 * UI State per MaintainerListScreen.
 */
data class MaintainerListUiState(
    val maintainers: List<Maintainer> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel per la lista manutentori.
 */
@HiltViewModel
class MaintainerListViewModel @Inject constructor(
    private val maintainerRepository: MaintainerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MaintainerListUiState())
    val uiState: StateFlow<MaintainerListUiState> = _uiState.asStateFlow()

    init {
        loadMaintainers()
    }

    /**
     * Carica tutti i manutentori attivi.
     */
    private fun loadMaintainers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            maintainerRepository.getAllActive().collect { maintainers ->
                _uiState.update {
                    it.copy(
                        maintainers = maintainers,
                        isLoading = false,
                        error = null
                    )
                }
            }
        }
    }

    /**
     * Elimina un manutentore (soft delete).
     */
    fun deleteMaintainer(id: String) {
        viewModelScope.launch {
            try {
                maintainerRepository.softDelete(id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Errore durante l'eliminazione: ${e.message}")
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
