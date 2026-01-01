package org.incammino.hospiceinventory.ui.screens.home

import android.util.Log
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
import org.incammino.hospiceinventory.data.repository.AssigneeRepository
import org.incammino.hospiceinventory.data.repository.LocationRepository
import org.incammino.hospiceinventory.data.repository.MaintainerRepository
import org.incammino.hospiceinventory.data.repository.MaintenanceRepository
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
    val isRefreshing: Boolean = false,

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
    data class ToNewProduct(val prefill: Map<String, String>? = null) : NavigationAction()
    data class ToMaintenances(val filter: String?) : NavigationAction()
    data object ToScanner : NavigationAction()
    data class ToNewMaintenance(val productId: String, val prefill: Map<String, String>? = null) : NavigationAction()
    data class ToNewMaintainer(val prefill: Map<String, String>? = null) : NavigationAction()
    data class ToNewLocation(val prefill: Map<String, String>? = null) : NavigationAction()
}

/**
 * ViewModel per la schermata Home.
 * Integra VoiceAssistant per comandi vocali.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val maintainerRepository: MaintainerRepository,
    private val locationRepository: LocationRepository,
    private val assigneeRepository: AssigneeRepository,
    private val voiceAssistant: VoiceAssistant
) : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
    }

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
     * Ricarica i dati della dashboard con indicatore di refresh.
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            loadDashboardData()
            // Piccolo delay per feedback visivo
            kotlinx.coroutines.delay(500)
            _uiState.update { it.copy(isRefreshing = false) }
        }
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
        when (action) {
            // ═══════════════════════════════════════════════════════════════════
            // AZIONI DI NAVIGAZIONE
            // ═══════════════════════════════════════════════════════════════════
            is AssistantAction.SearchProducts -> {
                _uiState.update { it.copy(pendingNavigation = NavigationAction.ToSearch(action.query)) }
            }
            is AssistantAction.ShowProduct -> {
                _uiState.update { it.copy(pendingNavigation = NavigationAction.ToProduct(action.productId)) }
            }
            is AssistantAction.CreateProduct -> {
                _uiState.update { it.copy(pendingNavigation = NavigationAction.ToNewProduct(action.prefillData)) }
            }
            is AssistantAction.ShowMaintenanceList -> {
                _uiState.update { it.copy(pendingNavigation = NavigationAction.ToMaintenances(action.filter)) }
            }
            is AssistantAction.ScanBarcode -> {
                _uiState.update { it.copy(pendingNavigation = NavigationAction.ToScanner) }
            }
            is AssistantAction.ShowOverdueAlerts -> {
                _uiState.update { it.copy(pendingNavigation = NavigationAction.ToMaintenances("overdue")) }
            }
            is AssistantAction.PrepareEmail -> {
                _uiState.update { it.copy(pendingNavigation = NavigationAction.ToProduct(action.productId)) }
            }
            is AssistantAction.NavigateToNewMaintenance -> {
                _uiState.update { it.copy(pendingNavigation = NavigationAction.ToNewMaintenance(action.productId, action.prefillData)) }
            }
            is AssistantAction.NavigateToNewMaintainer -> {
                _uiState.update { it.copy(pendingNavigation = NavigationAction.ToNewMaintainer(action.prefillData)) }
            }
            is AssistantAction.NavigateToNewLocation -> {
                _uiState.update { it.copy(pendingNavigation = NavigationAction.ToNewLocation(action.prefillData)) }
            }

            // ═══════════════════════════════════════════════════════════════════
            // AZIONI DI SALVATAGGIO DIRETTO (da flusso vocale)
            // ═══════════════════════════════════════════════════════════════════
            is AssistantAction.SaveMaintenance -> {
                viewModelScope.launch {
                    try {
                        val id = maintenanceRepository.insert(action.maintenance, updateProductDates = true)
                        Log.i(TAG, "Manutenzione salvata con ID: $id")
                        // Opzionale: navigare al dettaglio prodotto dopo il salvataggio
                        _uiState.update {
                            it.copy(pendingNavigation = NavigationAction.ToProduct(action.maintenance.productId))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Errore salvataggio manutenzione", e)
                        _uiState.update { it.copy(aiResponse = "Errore durante il salvataggio: ${e.message}") }
                    }
                }
            }
            is AssistantAction.SaveMaintainer -> {
                viewModelScope.launch {
                    try {
                        val id = maintainerRepository.insert(action.maintainer)
                        Log.i(TAG, "Manutentore salvato con ID: $id")
                    } catch (e: Exception) {
                        Log.e(TAG, "Errore salvataggio manutentore", e)
                        _uiState.update { it.copy(aiResponse = "Errore durante il salvataggio: ${e.message}") }
                    }
                }
            }
            is AssistantAction.SaveLocation -> {
                viewModelScope.launch {
                    try {
                        val id = locationRepository.insert(action.location)
                        Log.i(TAG, "Ubicazione salvata con ID: $id")
                    } catch (e: Exception) {
                        Log.e(TAG, "Errore salvataggio ubicazione", e)
                        _uiState.update { it.copy(aiResponse = "Errore durante il salvataggio: ${e.message}") }
                    }
                }
            }
            is AssistantAction.SaveAssignee -> {
                viewModelScope.launch {
                    try {
                        val id = assigneeRepository.insert(action.assignee)
                        Log.i(TAG, "Assegnatario salvato con ID: $id")
                    } catch (e: Exception) {
                        Log.e(TAG, "Errore salvataggio assegnatario", e)
                        _uiState.update { it.copy(aiResponse = "Errore durante il salvataggio: ${e.message}") }
                    }
                }
            }
        }
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

    /**
     * Rilascia le risorse quando il ViewModel viene distrutto.
     *
     * NOTA IMPORTANTE: voiceAssistant.release() chiama geminiService.resetContext()
     * che cancella il ConversationContext (incluso ActiveTask per flussi multi-step).
     *
     * Questo significa che il contesto conversazionale viene PERSO quando:
     * - L'utente naviga via dalla HomeScreen
     * - L'Activity viene distrutta (rotazione, back, system kill)
     * - Cambio configurazione non gestito
     *
     * Il contesto RIMANE PERSISTENTE durante:
     * - Interazioni vocali consecutive sulla stessa schermata
     * - Flussi multi-step (creazione prodotto, registrazione manutenzione)
     *
     * Se in futuro serve persistenza oltre la vita del ViewModel,
     * considerare: SavedStateHandle + serializzazione di ActiveTask.
     *
     * @see GeminiService.resetContext
     * @see ConversationContext.activeTask
     */
    override fun onCleared() {
        super.onCleared()
        voiceAssistant.release()
    }

    /**
     * Pulisce il contesto conversazionale di Gemini.
     *
     * Chiamato quando un flusso Voice Dump termina (Salva o Annulla)
     * per evitare contaminazione tra sessioni diverse.
     *
     * @see GeminiService.resetContext
     */
    fun clearGeminiContext() {
        voiceAssistant.clearContext()
        Log.d(TAG, "Gemini context cleared after voice session")
    }
}
