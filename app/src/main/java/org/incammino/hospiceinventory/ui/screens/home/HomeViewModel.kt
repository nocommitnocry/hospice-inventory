package org.incammino.hospiceinventory.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.incammino.hospiceinventory.data.local.dao.MaintenanceAlertDao
import org.incammino.hospiceinventory.data.local.dao.ProductDao
import org.incammino.hospiceinventory.domain.model.SyncStatus
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
    val isOnline: Boolean = true,
    val pendingSyncCount: Int = 0
)

/**
 * ViewModel per la schermata Home.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val productDao: ProductDao,
    private val maintenanceAlertDao: MaintenanceAlertDao
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    init {
        loadAlertCounts()
        loadSyncStatus()
    }
    
    /**
     * Carica i conteggi degli alert manutenzione.
     */
    private fun loadAlertCounts() {
        viewModelScope.launch {
            // Manutenzioni scadute
            maintenanceAlertDao.countOverdue().collect { overdue ->
                _uiState.update { it.copy(overdueCount = overdue) }
            }
        }
        
        viewModelScope.launch {
            // Manutenzioni in scadenza (prossimi 7 giorni)
            val today = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
            val nextWeek = today.plus(DatePeriod(days = 7))
            
            productDao.getWithMaintenanceDueBetween(today, nextWeek).collect { products ->
                _uiState.update { it.copy(upcomingCount = products.size) }
            }
        }
    }
    
    /**
     * Carica lo stato di sincronizzazione.
     */
    private fun loadSyncStatus() {
        viewModelScope.launch {
            val pending = productDao.getBySyncStatus(SyncStatus.PENDING)
            _uiState.update { it.copy(pendingSyncCount = pending.size) }
        }
        
        // TODO: Implementare controllo connettività reale
        _uiState.update { it.copy(isOnline = true) }
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
