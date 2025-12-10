package org.incammino.hospiceinventory.ui.screens.home

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
import org.incammino.hospiceinventory.data.repository.ProductRepository
import javax.inject.Inject

/**
 * UI State per la HomeScreen.
 */
data class HomeUiState(
    val isListening: Boolean = false,
    val transcription: String = "",
    val isProcessing: Boolean = false,
    val aiResponse: String = "",
    val overdueCount: Int = 0,
    val upcomingCount: Int = 0,
    val totalProducts: Int = 0,
    val isOnline: Boolean = true,
    val pendingSyncCount: Int = 0
)

/**
 * ViewModel per la schermata Home.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    /**
     * Carica i dati della dashboard.
     */
    private fun loadDashboardData() {
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date

        // Manutenzioni scadute
        viewModelScope.launch {
            productRepository.getWithOverdueMaintenance(today).collect { products ->
                _uiState.update { it.copy(overdueCount = products.size) }
            }
        }

        // Manutenzioni in scadenza (prossimi 7 giorni)
        viewModelScope.launch {
            val nextWeek = today.plus(DatePeriod(days = 7))
            productRepository.getWithMaintenanceDueBetween(today, nextWeek).collect { products ->
                _uiState.update { it.copy(upcomingCount = products.size) }
            }
        }

        // Conteggio totale prodotti
        viewModelScope.launch {
            val count = productRepository.countActive()
            _uiState.update { it.copy(totalProducts = count) }
        }

        // Sync status
        viewModelScope.launch {
            val pending = productRepository.getPendingSync()
            _uiState.update { it.copy(pendingSyncCount = pending.size) }
        }

        // TODO: Implementare controllo connettività reale
        _uiState.update { it.copy(isOnline = true) }
    }

    /**
     * Ricarica i dati della dashboard.
     */
    fun refresh() {
        loadDashboardData()
    }

    /**
     * Attiva/disattiva l'ascolto vocale.
     */
    fun toggleVoice() {
        val isCurrentlyListening = _uiState.value.isListening

        if (isCurrentlyListening) {
            stopListening()
        } else {
            startListening()
        }
    }

    /**
     * Inizia l'ascolto vocale.
     */
    private fun startListening() {
        _uiState.update {
            it.copy(
                isListening = true,
                transcription = "",
                aiResponse = ""
            )
        }

        // TODO: Implementare Speech-to-Text reale
        // Per ora simuliamo
    }

    /**
     * Ferma l'ascolto e processa il comando.
     */
    private fun stopListening() {
        _uiState.update { it.copy(isListening = false, isProcessing = true) }

        // TODO: Inviare la trascrizione a Gemini per processing
        viewModelScope.launch {
            // Simula processing
            kotlinx.coroutines.delay(500)
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    /**
     * Aggiorna la trascrizione in tempo reale.
     */
    fun updateTranscription(text: String) {
        _uiState.update { it.copy(transcription = text) }
    }

    /**
     * Aggiorna lo stato di connessione.
     */
    fun updateConnectivity(isOnline: Boolean) {
        _uiState.update { it.copy(isOnline = isOnline) }
    }
}
