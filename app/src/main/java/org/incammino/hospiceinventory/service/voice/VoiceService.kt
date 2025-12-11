package org.incammino.hospiceinventory.service.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// ═══════════════════════════════════════════════════════════════════════════════
// VOICE STATE
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Stato del servizio vocale.
 */
sealed class VoiceState {
    /** Pronto per iniziare l'ascolto */
    data object Idle : VoiceState()

    /** In ascolto attivo */
    data object Listening : VoiceState()

    /** Elaborazione in corso */
    data object Processing : VoiceState()

    /** Risultato parziale durante l'ascolto */
    data class PartialResult(val text: String) : VoiceState()

    /** Risultato finale */
    data class Result(val text: String, val confidence: Float) : VoiceState()

    /** Errore */
    data class Error(val message: String, val errorCode: Int) : VoiceState()

    /** Servizio non disponibile */
    data object Unavailable : VoiceState()
}

/**
 * Codici di errore del riconoscimento vocale.
 */
object VoiceErrorCodes {
    const val NETWORK_ERROR = SpeechRecognizer.ERROR_NETWORK
    const val NETWORK_TIMEOUT = SpeechRecognizer.ERROR_NETWORK_TIMEOUT
    const val AUDIO_ERROR = SpeechRecognizer.ERROR_AUDIO
    const val SERVER_ERROR = SpeechRecognizer.ERROR_SERVER
    const val CLIENT_ERROR = SpeechRecognizer.ERROR_CLIENT
    const val SPEECH_TIMEOUT = SpeechRecognizer.ERROR_SPEECH_TIMEOUT
    const val NO_MATCH = SpeechRecognizer.ERROR_NO_MATCH
    const val BUSY = SpeechRecognizer.ERROR_RECOGNIZER_BUSY
    const val PERMISSION_DENIED = SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
    const val TOO_MANY_REQUESTS = SpeechRecognizer.ERROR_TOO_MANY_REQUESTS
    const val LANGUAGE_NOT_SUPPORTED = SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
    const val LANGUAGE_UNAVAILABLE = SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE

    fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            NETWORK_ERROR -> "Errore di rete. Verifica la connessione."
            NETWORK_TIMEOUT -> "Timeout di rete. Riprova."
            AUDIO_ERROR -> "Errore audio. Verifica il microfono."
            SERVER_ERROR -> "Errore del server. Riprova più tardi."
            CLIENT_ERROR -> "Errore interno. Riavvia l'app."
            SPEECH_TIMEOUT -> "Non ho sentito nulla. Riprova."
            NO_MATCH -> "Non ho capito. Puoi ripetere?"
            BUSY -> "Sistema occupato. Attendi un momento."
            PERMISSION_DENIED -> "Permesso microfono negato."
            TOO_MANY_REQUESTS -> "Troppe richieste. Attendi un momento."
            LANGUAGE_NOT_SUPPORTED -> "Lingua non supportata."
            LANGUAGE_UNAVAILABLE -> "Lingua non disponibile offline."
            else -> "Errore sconosciuto ($errorCode)"
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// VOICE SERVICE
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Servizio per il riconoscimento vocale.
 * Utilizza Android SpeechRecognizer per convertire voce in testo.
 */
@Singleton
class VoiceService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "VoiceService"
        private const val LANGUAGE = "it-IT"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isInitialized = false

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    /**
     * Callback per eventi vocali (opzionale, per integrazione UI).
     */
    var onResult: ((String) -> Unit)? = null
    var onError: ((String, Int) -> Unit)? = null
    var onPartialResult: ((String) -> Unit)? = null

    /**
     * Inizializza il servizio vocale.
     * Deve essere chiamato prima di startListening().
     */
    fun initialize() {
        if (isInitialized) return

        val available = SpeechRecognizer.isRecognitionAvailable(context)
        _isAvailable.value = available

        if (!available) {
            Log.w(TAG, "Speech recognition not available on this device")
            _state.value = VoiceState.Unavailable
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createRecognitionListener())
            }
            isInitialized = true
            _state.value = VoiceState.Idle
            Log.i(TAG, "VoiceService initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SpeechRecognizer", e)
            _isAvailable.value = false
            _state.value = VoiceState.Unavailable
        }
    }

    /**
     * Inizia l'ascolto vocale.
     */
    fun startListening() {
        if (!isInitialized) {
            initialize()
        }

        if (!_isAvailable.value) {
            _state.value = VoiceState.Error(
                "Riconoscimento vocale non disponibile",
                -1
            )
            onError?.invoke("Riconoscimento vocale non disponibile", -1)
            return
        }

        if (_state.value is VoiceState.Listening) {
            Log.w(TAG, "Already listening, ignoring startListening()")
            return
        }

        try {
            val intent = createRecognizerIntent()
            speechRecognizer?.startListening(intent)
            _state.value = VoiceState.Listening
            Log.d(TAG, "Started listening")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting listening", e)
            _state.value = VoiceState.Error("Errore avvio ascolto: ${e.message}", -1)
            onError?.invoke("Errore avvio ascolto", -1)
        }
    }

    /**
     * Ferma l'ascolto vocale.
     */
    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            _state.value = VoiceState.Processing
            Log.d(TAG, "Stopped listening")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping listening", e)
        }
    }

    /**
     * Annulla l'ascolto vocale corrente.
     */
    fun cancel() {
        try {
            speechRecognizer?.cancel()
            _state.value = VoiceState.Idle
            Log.d(TAG, "Listening cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling", e)
        }
    }

    /**
     * Rilascia le risorse del servizio.
     * Chiamare quando il servizio non è più necessario.
     */
    fun release() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            isInitialized = false
            _state.value = VoiceState.Idle
            Log.i(TAG, "VoiceService released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing VoiceService", e)
        }
    }

    /**
     * Verifica se il servizio è pronto per l'ascolto.
     */
    fun isReady(): Boolean {
        return isInitialized && _isAvailable.value && _state.value is VoiceState.Idle
    }

    /**
     * Crea l'intent per il riconoscimento vocale.
     */
    private fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // Modello di riconoscimento
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            // Lingua italiana
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, LANGUAGE)

            // Risultati parziali per feedback in tempo reale
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

            // Numero massimo di risultati alternativi
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

            // Prompt per l'utente (non mostrato ma utile per il riconoscitore)
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Parla per cercare o comandare..."
            )

            // Timeout
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }
    }

    /**
     * Crea il listener per gli eventi di riconoscimento.
     */
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech")
                _state.value = VoiceState.Listening
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Beginning of speech detected")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Volume del microfono - può essere usato per animazione
                // Log.v(TAG, "RMS: $rmsdB")
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Buffer audio ricevuto
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "End of speech detected")
                _state.value = VoiceState.Processing
            }

            override fun onError(error: Int) {
                val errorMessage = VoiceErrorCodes.getErrorMessage(error)
                Log.w(TAG, "Recognition error: $error - $errorMessage")

                // Alcuni errori non sono critici
                val isCritical = error !in listOf(
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                )

                _state.value = VoiceState.Error(errorMessage, error)
                onError?.invoke(errorMessage, error)

                // Reset a Idle dopo errore non critico
                if (!isCritical) {
                    _state.value = VoiceState.Idle
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                if (matches.isNullOrEmpty()) {
                    Log.w(TAG, "No results")
                    _state.value = VoiceState.Error("Nessun risultato", SpeechRecognizer.ERROR_NO_MATCH)
                    onError?.invoke("Nessun risultato", SpeechRecognizer.ERROR_NO_MATCH)
                    return
                }

                val bestMatch = matches[0]
                val confidence = confidences?.getOrNull(0) ?: 0f

                Log.i(TAG, "Result: '$bestMatch' (confidence: $confidence)")

                _state.value = VoiceState.Result(bestMatch, confidence)
                onResult?.invoke(bestMatch)

                // Reset a Idle dopo aver processato il risultato
                _state.value = VoiceState.Idle
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                if (!matches.isNullOrEmpty()) {
                    val partialText = matches[0]
                    Log.d(TAG, "Partial: '$partialText'")

                    _state.value = VoiceState.PartialResult(partialText)
                    onPartialResult?.invoke(partialText)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d(TAG, "Event: $eventType")
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// VOICE ASSISTANT (Integrazione VoiceService + GeminiService)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Stato dell'assistente vocale completo.
 */
sealed class AssistantState {
    data object Idle : AssistantState()
    data object Listening : AssistantState()
    data class Recognizing(val partialText: String) : AssistantState()
    data object Thinking : AssistantState()
    data class Speaking(val text: String) : AssistantState()
    data class WaitingConfirmation(val message: String) : AssistantState()
    data class Error(val message: String) : AssistantState()
}

/**
 * Assistente vocale che integra riconoscimento vocale e AI.
 */
@Singleton
class VoiceAssistant @Inject constructor(
    private val voiceService: VoiceService,
    private val geminiService: GeminiService
) {
    companion object {
        private const val TAG = "VoiceAssistant"
    }

    // CoroutineScope per operazioni asincrone
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    private val _lastResponse = MutableStateFlow<String?>(null)
    val lastResponse: StateFlow<String?> = _lastResponse.asStateFlow()

    private val _pendingAction = MutableStateFlow<AssistantAction?>(null)
    val pendingAction: StateFlow<AssistantAction?> = _pendingAction.asStateFlow()

    /**
     * Callback quando un'azione deve essere eseguita dalla UI.
     */
    var onActionRequired: ((AssistantAction) -> Unit)? = null

    /**
     * Inizializza l'assistente.
     */
    fun initialize() {
        voiceService.initialize()

        // Configura callback dal VoiceService
        voiceService.onResult = { text ->
            processVoiceInput(text)
        }

        voiceService.onError = { message, _ ->
            _state.value = AssistantState.Error(message)
        }

        voiceService.onPartialResult = { partial ->
            _state.value = AssistantState.Recognizing(partial)
        }
    }

    /**
     * Inizia l'ascolto vocale.
     */
    fun startListening() {
        if (!voiceService.isAvailable.value) {
            _state.value = AssistantState.Error("Riconoscimento vocale non disponibile")
            return
        }

        _state.value = AssistantState.Listening
        voiceService.startListening()
    }

    /**
     * Ferma l'ascolto.
     */
    fun stopListening() {
        voiceService.stopListening()
    }

    /**
     * Annulla l'operazione corrente.
     */
    fun cancel() {
        voiceService.cancel()
        geminiService.cancelPendingAction()
        _pendingAction.value = null
        _state.value = AssistantState.Idle
    }

    /**
     * Processa l'input vocale riconosciuto.
     */
    private fun processVoiceInput(text: String) {
        _state.value = AssistantState.Thinking

        // Usa coroutine per chiamare Gemini
        scope.launch(Dispatchers.IO) {
            try {
                val result = geminiService.processMessage(text)
                handleGeminiResult(result)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing voice input", e)
                _state.value = AssistantState.Error("Errore elaborazione: ${e.message}")
            }
        }
    }

    /**
     * Gestisce il risultato di Gemini.
     */
    private fun handleGeminiResult(result: GeminiResult) {
        when (result) {
            is GeminiResult.Success -> {
                _lastResponse.value = result.response
                _state.value = AssistantState.Speaking(result.response)
                // Dopo un po' torna a Idle
                resetToIdleAfterDelay()
            }

            is GeminiResult.Error -> {
                _lastResponse.value = result.message
                _state.value = AssistantState.Error(result.message)
            }

            is GeminiResult.ActionRequired -> {
                _lastResponse.value = result.response
                _pendingAction.value = result.action
                _state.value = AssistantState.Speaking(result.response)

                // Notifica la UI per eseguire l'azione
                onActionRequired?.invoke(result.action)
                resetToIdleAfterDelay()
            }

            is GeminiResult.ConfirmationNeeded -> {
                _lastResponse.value = "${result.response}\n\n${result.confirmationMessage}"
                _pendingAction.value = result.action
                _state.value = AssistantState.WaitingConfirmation(result.confirmationMessage)
            }
        }
    }

    /**
     * Invia una risposta di conferma (sì/no).
     */
    suspend fun sendConfirmation(confirmed: Boolean) {
        _state.value = AssistantState.Thinking

        val response = if (confirmed) "sì" else "no"
        val result = geminiService.processMessage(response)
        handleGeminiResult(result)
    }

    /**
     * Invia un messaggio testuale (per test o input da tastiera).
     */
    suspend fun sendTextMessage(text: String) {
        _state.value = AssistantState.Thinking

        try {
            val result = geminiService.processMessage(text)
            handleGeminiResult(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text message", e)
            _state.value = AssistantState.Error("Errore: ${e.message}")
        }
    }

    /**
     * Reset allo stato Idle dopo un delay.
     */
    private fun resetToIdleAfterDelay() {
        scope.launch {
            delay(3000) // 3 secondi per leggere la risposta
            if (_state.value is AssistantState.Speaking) {
                _state.value = AssistantState.Idle
            }
        }
    }

    /**
     * Rilascia le risorse.
     */
    fun release() {
        scope.cancel()
        voiceService.release()
        geminiService.resetContext()
    }

    /**
     * Verifica se l'assistente è disponibile.
     */
    fun isAvailable(): Boolean = voiceService.isAvailable.value
}
