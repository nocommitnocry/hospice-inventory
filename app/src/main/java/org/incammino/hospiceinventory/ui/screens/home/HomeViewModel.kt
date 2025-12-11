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
import org.incammino.hospiceinventory.data.repository.ProductRepository
import org.incammino.hospiceinventory.service.voice.AssistantAction
import org.incammino.hospiceinventory.service.voice.AssistantState
import org.incammino.hospiceinventory.service.voice.VoiceAssistant
import javax.inject.Inject

/**
 * UI State per la HomeScreen.
 */
data class HomeUiState(
    // Voice assistant
    val assistantState: AssistantState = AssistantState.Idle,
    val transcription: String = "",
    val aiResponse: String = "",
    val isVoiceAvailable: Boolean = true,

    // Dashboard
    val overdueCount: Int = 0,
    val upcomingCount: Int = 0,
    val totalProducts: Int = 0,

    // Connectivity
    val isOnline: Boolean = true,
    val pendingSyncCount: Int = 0,

    // Navigation actions from AI
    val pendingNavigation: NavigationAction? = null
)

/**
 * Azioni di navigazione richieste dall'AI.
 */
sealed class NavigationAction {
    data class ToSearch(val query: String) : NavigationAction()
    data class ToProduct(val productId: String) : NavigationAction()
    data object ToNewProduct : NavigationAction()
    data class ToMaintenances(val filter: String?) : NavigationAction()
    data object ToScanner : NavigationAction()
}

/**
 * ViewModel per la schermata Home.
 * Integra VoiceAssistant per comandi vocali.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val voiceAssistant: VoiceAssistant
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        initializeVoiceAssistant()
        loadDashboardData()
        observeAssistantState()
    }

    /**
     * Inizializza l'assistente vocale.
     */
    private fun initializeVoiceAssistant() {
        voiceAssistant.initialize()
        _uiState.update { it.copy(isVoiceAvailable = voiceAssistant.isAvailable()) }

        // Configura callback per azioni
        voiceAssistant.onActionRequired = { action ->
            handleAssistantAction(action)
        }
    }

    /**
     * Osserva lo stato dell'assistente vocale.
     */
    private fun observeAssistantState() {
        viewModelScope.launch {
            voiceAssistant.state.collect { state ->
                _uiState.update { it.copy(assistantState = state) }

                // Aggiorna la trascrizione in base allo stato
                when (state) {
                    is AssistantState.Recognizing -> {
                        _uiState.update { it.copy(transcription = state.partialText) }
                    }
                    is AssistantState.Speaking -> {
                        _uiState.update { it.copy(aiResponse = state.text) }
                    }
                    is AssistantState.WaitingConfirmation -> {
                        _uiState.update { it.copy(aiResponse = state.message) }
                    }
                    is AssistantState.Error -> {
                        _uiState.update { it.copy(aiResponse = state.message) }
                    }
                    else -> {}
                }
            }
        }

        // Osserva le risposte dell'assistente
        viewModelScope.launch {
            voiceAssistant.lastResponse.collect { response ->
                response?.let {
                    _uiState.update { state -> state.copy(aiResponse = it) }
                }
            }
        }
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
        val currentState = _uiState.value.assistantState

        when (currentState) {
            is AssistantState.Idle, is AssistantState.Error -> {
                startListening()
            }
            is AssistantState.Listening, is AssistantState.Recognizing -> {
                stopListening()
            }
            is AssistantState.WaitingConfirmation -> {
                // Se in attesa di conferma, annulla
                cancelVoice()
            }
            else -> {
                // In altri stati (Thinking, Speaking) non fare nulla
            }
        }
    }

    /**
     * Inizia l'ascolto vocale.
     */
    private fun startListening() {
        _uiState.update {
            it.copy(
                transcription = "",
                aiResponse = ""
            )
        }
        voiceAssistant.startListening()
    }

    /**
     * Ferma l'ascolto.
     */
    private fun stopListening() {
        voiceAssistant.stopListening()
    }

    /**
     * Annulla l'operazione vocale corrente.
     */
    fun cancelVoice() {
        voiceAssistant.cancel()
        _uiState.update {
            it.copy(
                transcription = "",
                aiResponse = ""
            )
        }
    }

    /**
     * Conferma l'azione pendente.
     */
    fun confirmAction() {
        viewModelScope.launch {
            voiceAssistant.sendConfirmation(true)
        }
    }

    /**
     * Rifiuta l'azione pendente.
     */
    fun rejectAction() {
        viewModelScope.launch {
            voiceAssistant.sendConfirmation(false)
        }
    }

    /**
     * Invia un messaggio testuale (per debug o input tastiera).
     */
    fun sendTextMessage(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(transcription = text) }
            voiceAssistant.sendTextMessage(text)
        }
    }

    /**
     * Gestisce le azioni richieste dall'assistente AI.
     */
    private fun handleAssistantAction(action: AssistantAction) {
        val navAction = when (action) {
            is AssistantAction.SearchProducts -> NavigationAction.ToSearch(action.query)
            is AssistantAction.ShowProduct -> NavigationAction.ToProduct(action.productId)
            is AssistantAction.CreateProduct -> NavigationAction.ToNewProduct
            is AssistantAction.ShowMaintenanceList -> NavigationAction.ToMaintenances(action.filter)
            is AssistantAction.ScanBarcode -> NavigationAction.ToScanner
            is AssistantAction.ShowOverdueAlerts -> NavigationAction.ToMaintenances("overdue")
            is AssistantAction.PrepareEmail -> {
                // Per email, vai al dettaglio prodotto
                NavigationAction.ToProduct(action.productId)
            }
        }

        _uiState.update { it.copy(pendingNavigation = navAction) }
    }

    /**
     * Consuma l'azione di navigazione (chiamato dalla UI dopo la navigazione).
     */
    fun consumeNavigation() {
        _uiState.update { it.copy(pendingNavigation = null) }
    }

    /**
     * Aggiorna lo stato di connessione.
     */
    fun updateConnectivity(isOnline: Boolean) {
        _uiState.update { it.copy(isOnline = isOnline) }
    }

    override fun onCleared() {
        super.onCleared()
        voiceAssistant.release()
    }
}
