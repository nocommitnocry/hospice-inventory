package org.incammino.hospiceinventory.service.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

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

// ═══════════════════════════════════════════════════════════════════════════════
// LISTENING MODE - TAP-TO-STOP
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Modalità di ascolto per il riconoscimento vocale.
 */
enum class ListeningMode {
    /** Comportamento legacy: timeout automatico basato su silenzio */
    AUTO_STOP,
    /** Tap-to-stop: l'utente controlla quando fermare */
    MANUAL_STOP
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

        // P2 FIX: Costanti per gestione timeout intelligente
        /** Delay in ms dopo fine speech prima di processare (default 2.5s) */
        private const val SILENCE_DELAY_MS = 2500L

        /** Parole chiave che terminano immediatamente l'ascolto */
        private val TRIGGER_WORDS = listOf(
            "fatto", "invia", "ok", "procedi", "basta", "stop", "fine"
        )

        // TAP-TO-STOP: Costanti per modalità manuale
        /** Numero massimo di errori consecutivi prima di segnalare problema critico */
        private const val MAX_CONSECUTIVE_ERRORS = 3

        /** Timeout assoluto di sicurezza: 5 minuti (se utente dimentica di premere Stop) */
        private const val ABSOLUTE_TIMEOUT_MS = 5 * 60 * 1000L
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isInitialized = false

    // P2 FIX: Variabili per gestione timeout intelligente
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var silenceJob: Job? = null
    private var accumulatedText = StringBuilder()
    private var lastConfidence = 0f

    // TAP-TO-STOP: Variabili per modalità manuale
    private var currentMode = ListeningMode.AUTO_STOP
    private var isManualListeningActive = false
    private var consecutiveErrors = 0
    private var listeningStartTime = 0L
    private var absoluteTimeoutJob: Job? = null

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
     * @param mode Modalità di ascolto (AUTO_STOP per legacy, MANUAL_STOP per tap-to-stop)
     */
    fun startListening(mode: ListeningMode = ListeningMode.AUTO_STOP) {
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

        // TAP-TO-STOP: Gestisci già in ascolto
        if (_state.value is VoiceState.Listening || _state.value is VoiceState.PartialResult) {
            if (mode == ListeningMode.MANUAL_STOP && isManualListeningActive) {
                Log.d(TAG, "Already listening in manual mode")
                return
            }
        }

        try {
            // P2 FIX: Reset delle variabili di accumulo
            silenceJob?.cancel()
            silenceJob = null
            accumulatedText.clear()
            lastConfidence = 0f

            // TAP-TO-STOP: Setup modalità
            currentMode = mode
            consecutiveErrors = 0

            if (mode == ListeningMode.MANUAL_STOP) {
                isManualListeningActive = true
                listeningStartTime = System.currentTimeMillis()

                // Avvia timeout assoluto di sicurezza
                absoluteTimeoutJob?.cancel()
                absoluteTimeoutJob = scope.launch {
                    delay(ABSOLUTE_TIMEOUT_MS)
                    Log.w(TAG, "Absolute timeout reached - stopping manual listening")
                    if (isManualListeningActive) {
                        stopManualListening()
                    }
                }
            }

            val intent = createRecognizerIntent()
            speechRecognizer?.startListening(intent)
            _state.value = VoiceState.Listening
            Log.d(TAG, "Started listening in $mode mode")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting listening", e)
            _state.value = VoiceState.Error("Errore avvio ascolto: ${e.message}", -1)
            onError?.invoke("Errore avvio ascolto", -1)
            isManualListeningActive = false
            absoluteTimeoutJob?.cancel()
        }
    }

    /**
     * TAP-TO-STOP: Avvia ascolto in modalità manuale.
     * L'utente controlla quando fermare chiamando stopManualListening().
     */
    fun startManualListening() {
        consecutiveErrors = 0
        isManualListeningActive = true
        accumulatedText.clear()
        startListening(ListeningMode.MANUAL_STOP)
    }

    /**
     * TAP-TO-STOP: Ferma l'ascolto manuale e finalizza il risultato.
     * Chiamato quando l'utente preme il pulsante STOP.
     */
    fun stopManualListening() {
        Log.d(TAG, "stopManualListening() called")
        isManualListeningActive = false
        silenceJob?.cancel()
        absoluteTimeoutJob?.cancel()

        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping", e)
        }

        // Finalizza con quello che abbiamo accumulato
        val finalText = accumulatedText.toString().trim()
        if (finalText.isNotEmpty()) {
            Log.d(TAG, "Emitting final result from manual stop: $finalText")
            emitFinalResult(finalText, lastConfidence)
        } else {
            Log.d(TAG, "No text accumulated, returning to Idle")
            _state.value = VoiceState.Idle
        }
    }

    /**
     * TAP-TO-STOP: Riavvia l'ascolto senza notificare l'UI.
     * Usato in MANUAL_STOP per continuare dopo onEndOfSpeech/onResults/onError.
     */
    private fun restartListeningQuietly() {
        if (!isManualListeningActive) {
            Log.d(TAG, "restartListeningQuietly: not in manual mode, skipping")
            return
        }

        // Check timeout assoluto
        if (System.currentTimeMillis() - listeningStartTime > ABSOLUTE_TIMEOUT_MS) {
            Log.w(TAG, "Absolute timeout reached in restartListeningQuietly")
            stopManualListening()
            return
        }

        scope.launch {
            delay(100) // Piccola pausa per evitare race condition
            if (!isManualListeningActive) return@launch

            try {
                speechRecognizer?.cancel()
                delay(50)
                val intent = createRecognizerIntent()
                speechRecognizer?.startListening(intent)
                // NON cambiare state - rimane Listening/PartialResult
                Log.d(TAG, "Restarted listening quietly")
            } catch (e: Exception) {
                Log.e(TAG, "Error restarting quietly", e)
                consecutiveErrors++
                if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                    handleCriticalError(SpeechRecognizer.ERROR_CLIENT)
                }
            }
        }
    }

    /**
     * TAP-TO-STOP: Gestisce errori critici in modalità manuale.
     */
    private fun handleCriticalError(error: Int) {
        Log.e(TAG, "Critical error in manual mode: $error (consecutive: $consecutiveErrors)")
        isManualListeningActive = false
        absoluteTimeoutJob?.cancel()

        val message = if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            "Riconoscimento instabile. Prova a riavviare l'app."
        } else {
            VoiceErrorCodes.getErrorMessage(error)
        }

        _state.value = VoiceState.Error(message, error)
        onError?.invoke(message, error)
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
            // TAP-TO-STOP: Reset variabili modalità manuale
            isManualListeningActive = false
            absoluteTimeoutJob?.cancel()

            speechRecognizer?.cancel()
            _state.value = VoiceState.Idle
            Log.d(TAG, "Listening cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling", e)
        }
    }

    /**
     * Resetta lo stato a Idle senza interagire con SpeechRecognizer.
     * Utile quando un ViewModel vuole assicurarsi di partire da uno stato pulito.
     */
    fun resetState() {
        silenceJob?.cancel()
        silenceJob = null
        absoluteTimeoutJob?.cancel()
        absoluteTimeoutJob = null
        accumulatedText.clear()
        lastConfidence = 0f
        isManualListeningActive = false
        consecutiveErrors = 0
        currentMode = ListeningMode.AUTO_STOP
        _state.value = VoiceState.Idle
        Log.d(TAG, "State reset to Idle")
    }

    /**
     * Rilascia le risorse del servizio.
     * Chiamare quando il servizio non è più necessario.
     */
    fun release() {
        try {
            // TAP-TO-STOP: Cancella jobs e reset variabili
            isManualListeningActive = false
            silenceJob?.cancel()
            silenceJob = null
            absoluteTimeoutJob?.cancel()
            absoluteTimeoutJob = null
            scope.cancel()

            speechRecognizer?.destroy()
            speechRecognizer = null
            isInitialized = false
            accumulatedText.clear()
            consecutiveErrors = 0
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

            // Timeout estesi per permettere frasi più lunghe e pause naturali.
            // Utile quando l'utente deve dettare informazioni dettagliate
            // (es. "OxyGen 3000, Philips, stanza 12, manutenzione annuale").
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
    }

    /**
     * Crea il listener per gli eventi di riconoscimento.
     * P2 FIX: Implementa silence delay e trigger words per gestire frasi lunghe.
     */
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech")
                _state.value = VoiceState.Listening
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Beginning of speech detected")
                // P2 FIX: Cancella il timer di silenzio - l'utente sta parlando
                silenceJob?.cancel()
                silenceJob = null
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Volume del microfono - può essere usato per animazione
                // Log.v(TAG, "RMS: $rmsdB")
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Buffer audio ricevuto
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "End of speech detected (mode: $currentMode)")

                when (currentMode) {
                    ListeningMode.AUTO_STOP -> {
                        // Comportamento legacy con silence delay
                        _state.value = VoiceState.Processing
                        silenceJob?.cancel()
                        silenceJob = scope.launch {
                            delay(SILENCE_DELAY_MS)
                            Log.d(TAG, "Silence timeout reached - finalizing result")
                            finalizeResult()
                        }
                    }
                    ListeningMode.MANUAL_STOP -> {
                        // TAP-TO-STOP: Non fare nulla! L'utente decide quando fermare.
                        Log.d(TAG, "Manual mode - waiting for user to stop")
                        // Riavvia ascolto automaticamente per continuare
                        restartListeningQuietly()
                    }
                }
            }

            override fun onError(error: Int) {
                val errorMessage = VoiceErrorCodes.getErrorMessage(error)
                Log.w(TAG, "Recognition error: $error - $errorMessage (mode: $currentMode)")

                // P2 FIX: Cancella il timer
                silenceJob?.cancel()
                silenceJob = null

                when (currentMode) {
                    ListeningMode.AUTO_STOP -> {
                        // Comportamento legacy
                        val isCritical = error !in listOf(
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        )
                        _state.value = VoiceState.Error(errorMessage, error)
                        onError?.invoke(errorMessage, error)
                        if (!isCritical) {
                            _state.value = VoiceState.Idle
                        }
                    }
                    ListeningMode.MANUAL_STOP -> {
                        // TAP-TO-STOP: Errori recuperabili → riavvia silenziosamente
                        val isRecoverable = error in listOf(
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                            SpeechRecognizer.ERROR_CLIENT
                        )

                        if (isRecoverable && isManualListeningActive) {
                            Log.d(TAG, "Recoverable error in manual mode, restarting...")
                            consecutiveErrors++
                            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                                handleCriticalError(error)
                            } else {
                                restartListeningQuietly()
                            }
                        } else {
                            // Errore critico
                            handleCriticalError(error)
                        }
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                if (matches.isNullOrEmpty()) {
                    Log.w(TAG, "No results (mode: $currentMode)")
                    when (currentMode) {
                        ListeningMode.AUTO_STOP -> {
                            if (accumulatedText.isNotEmpty()) {
                                finalizeResult()
                            } else {
                                _state.value = VoiceState.Error("Nessun risultato", SpeechRecognizer.ERROR_NO_MATCH)
                                onError?.invoke("Nessun risultato", SpeechRecognizer.ERROR_NO_MATCH)
                            }
                        }
                        ListeningMode.MANUAL_STOP -> {
                            // In manual mode, nessun risultato = riprova
                            if (isManualListeningActive) {
                                restartListeningQuietly()
                            }
                        }
                    }
                    return
                }

                val bestMatch = matches[0]
                val confidence = confidences?.getOrNull(0) ?: 0f

                Log.i(TAG, "Result: '$bestMatch' (confidence: $confidence, mode: $currentMode)")

                // Accumula il testo
                accumulatedText.append(" ").append(bestMatch)
                lastConfidence = confidence

                when (currentMode) {
                    ListeningMode.AUTO_STOP -> {
                        // P2 FIX: Controlla se contiene una trigger word
                        val normalizedText = bestMatch.lowercase().trim()
                        val hasTriggerWord = TRIGGER_WORDS.any { trigger ->
                            normalizedText.endsWith(trigger) ||
                            normalizedText.endsWith("$trigger.") ||
                            normalizedText == trigger
                        }

                        if (hasTriggerWord) {
                            Log.d(TAG, "P2: Trigger word detected - processing immediately")
                            silenceJob?.cancel()
                            silenceJob = null
                            val cleanedText = removeTriggerWord(accumulatedText.toString().trim())
                            emitFinalResult(cleanedText, confidence)
                        } else {
                            // Mostra risultato parziale mentre aspettiamo
                            _state.value = VoiceState.PartialResult(accumulatedText.toString().trim())
                            onPartialResult?.invoke(accumulatedText.toString().trim())
                        }
                    }
                    ListeningMode.MANUAL_STOP -> {
                        // TAP-TO-STOP: Notifica UI del testo accumulato e continua
                        _state.value = VoiceState.PartialResult(accumulatedText.toString().trim())
                        onPartialResult?.invoke(accumulatedText.toString().trim())
                        // Riavvia per continuare ad ascoltare
                        restartListeningQuietly()
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                if (!matches.isNullOrEmpty()) {
                    val partialText = matches[0]
                    Log.d(TAG, "Partial: '$partialText'")

                    // P2 FIX: Mostra combinazione di accumulato + parziale
                    val displayText = if (accumulatedText.isNotEmpty()) {
                        "${accumulatedText.toString().trim()} $partialText"
                    } else {
                        partialText
                    }

                    _state.value = VoiceState.PartialResult(displayText)
                    onPartialResult?.invoke(displayText)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d(TAG, "Event: $eventType")
            }
        }
    }

    /**
     * P2 FIX: Finalizza il risultato dopo il timeout di silenzio.
     */
    private fun finalizeResult() {
        val finalText = accumulatedText.toString().trim()
        if (finalText.isNotEmpty()) {
            emitFinalResult(finalText, lastConfidence)
        } else {
            _state.value = VoiceState.Idle
        }
    }

    /**
     * P2 FIX: Emette il risultato finale e resetta lo stato.
     * P4/P5 FIX: Applica post-processing per correggere sigle e spelling fonetico.
     */
    private fun emitFinalResult(text: String, confidence: Float) {
        // P4/P5 FIX: Applica post-processing
        val processedText = SttPostProcessor.process(text)
        Log.i(TAG, "Final result: '$text' -> processed: '$processedText' (confidence: $confidence)")

        _state.value = VoiceState.Result(processedText, confidence)
        onResult?.invoke(processedText)

        // Reset
        accumulatedText.clear()
        lastConfidence = 0f
        _state.value = VoiceState.Idle
    }

    /**
     * P2 FIX: Rimuove la trigger word dal testo finale.
     */
    private fun removeTriggerWord(text: String): String {
        val normalized = text.lowercase()
        for (trigger in TRIGGER_WORDS) {
            if (normalized.endsWith(trigger)) {
                return text.dropLast(trigger.length).trim()
            }
            if (normalized.endsWith("$trigger.")) {
                return text.dropLast(trigger.length + 1).trim()
            }
        }
        return text
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TEXT TO SPEECH SERVICE
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Stato del TTS.
 */
sealed class TtsState {
    data object Idle : TtsState()
    data object Initializing : TtsState()
    data object Ready : TtsState()
    data class Speaking(val text: String) : TtsState()
    data class Error(val message: String) : TtsState()
    data object Unavailable : TtsState()
}

/**
 * Servizio per la sintesi vocale (Text-to-Speech).
 * Utilizza Android TTS nativo - gratuito e funziona offline.
 */
@Singleton
class TextToSpeechService @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TextToSpeechService"
        private val ITALIAN_LOCALE = Locale.ITALIAN
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var utteranceId = 0

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    // Callback per eventi TTS
    var onSpeakingStart: (() -> Unit)? = null
    var onSpeakingDone: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    /**
     * Inizializza il servizio TTS.
     */
    fun initialize() {
        if (isInitialized) return

        _state.value = TtsState.Initializing
        try {
            tts = TextToSpeech(context, this)
            Log.i(TAG, "TextToSpeech initialization started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create TextToSpeech", e)
            _state.value = TtsState.Unavailable
            _isAvailable.value = false
        }
    }

    /**
     * Callback di inizializzazione TTS.
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(ITALIAN_LOCALE)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Italian language not supported, trying default")
                // Prova con italiano generico
                val fallbackResult = tts?.setLanguage(Locale("it"))
                if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Italian not available")
                    _state.value = TtsState.Error("Italiano non disponibile per TTS")
                    _isAvailable.value = false
                    return
                }
            }

            // Configura il listener per gli eventi
            tts?.setOnUtteranceProgressListener(createUtteranceListener())

            // Imposta velocità e tono (valori di default, personalizzabili)
            tts?.setSpeechRate(1.0f)  // Velocità normale
            tts?.setPitch(1.0f)      // Tono normale

            isInitialized = true
            _isAvailable.value = true
            _state.value = TtsState.Ready
            Log.i(TAG, "TextToSpeech initialized successfully")
        } else {
            Log.e(TAG, "TextToSpeech initialization failed: $status")
            _state.value = TtsState.Unavailable
            _isAvailable.value = false
        }
    }

    /**
     * Crea il listener per il progresso della sintesi.
     */
    private fun createUtteranceListener(): UtteranceProgressListener {
        return object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS started: $utteranceId")
                onSpeakingStart?.invoke()
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS done: $utteranceId")
                _state.value = TtsState.Ready
                onSpeakingDone?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error: $utteranceId")
                _state.value = TtsState.Error("Errore sintesi vocale")
                onError?.invoke("Errore sintesi vocale")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                val errorMessage = when (errorCode) {
                    TextToSpeech.ERROR_SYNTHESIS -> "Errore sintesi"
                    TextToSpeech.ERROR_SERVICE -> "Errore servizio TTS"
                    TextToSpeech.ERROR_OUTPUT -> "Errore output audio"
                    TextToSpeech.ERROR_NETWORK -> "Errore rete"
                    TextToSpeech.ERROR_NETWORK_TIMEOUT -> "Timeout rete"
                    TextToSpeech.ERROR_INVALID_REQUEST -> "Richiesta non valida"
                    TextToSpeech.ERROR_NOT_INSTALLED_YET -> "TTS non installato"
                    else -> "Errore sconosciuto ($errorCode)"
                }
                Log.e(TAG, "TTS error $errorCode: $errorMessage")
                _state.value = TtsState.Error(errorMessage)
                onError?.invoke(errorMessage)
            }
        }
    }

    /**
     * Legge il testo ad alta voce.
     * @param text Il testo da leggere
     * @param flush Se true, interrompe qualsiasi sintesi in corso
     */
    fun speak(text: String, flush: Boolean = true) {
        if (!isInitialized || !_isAvailable.value) {
            Log.w(TAG, "TTS not available, cannot speak")
            return
        }

        if (text.isBlank()) {
            Log.w(TAG, "Empty text, nothing to speak")
            return
        }

        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val id = "utterance_${++utteranceId}"

        _state.value = TtsState.Speaking(text)

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
        }

        val result = tts?.speak(text, queueMode, params, id)
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "TTS speak failed")
            _state.value = TtsState.Error("Impossibile leggere il testo")
        }
    }

    /**
     * Legge il testo e attende il completamento.
     */
    suspend fun speakAndWait(text: String): Boolean {
        if (!isInitialized || !_isAvailable.value) {
            return false
        }

        if (text.isBlank()) {
            return true
        }

        return suspendCancellableCoroutine { continuation ->
            val id = "utterance_${++utteranceId}"
            _state.value = TtsState.Speaking(text)

            val listener = object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id) {
                        _state.value = TtsState.Ready
                        continuation.resume(true)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id) {
                        _state.value = TtsState.Ready
                        continuation.resume(false)
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == id) {
                        _state.value = TtsState.Ready
                        continuation.resume(false)
                    }
                }
            }

            tts?.setOnUtteranceProgressListener(listener)

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
            }

            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
            if (result == TextToSpeech.ERROR) {
                _state.value = TtsState.Ready
                continuation.resume(false)
            }

            continuation.invokeOnCancellation {
                stop()
            }
        }
    }

    /**
     * Ferma la sintesi in corso.
     */
    fun stop() {
        tts?.stop()
        _state.value = TtsState.Ready
    }

    /**
     * Imposta la velocità di lettura.
     * @param rate 0.5 = metà velocità, 1.0 = normale, 2.0 = doppia velocità
     */
    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.25f, 4.0f))
    }

    /**
     * Imposta il tono della voce.
     * @param pitch 0.5 = più basso, 1.0 = normale, 2.0 = più alto
     */
    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch.coerceIn(0.25f, 4.0f))
    }

    /**
     * Verifica se sta parlando.
     */
    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    /**
     * Rilascia le risorse.
     */
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _state.value = TtsState.Idle
        _isAvailable.value = false
        Log.i(TAG, "TextToSpeechService released")
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// VOICE ASSISTANT (Integrazione VoiceService + GeminiService + TTS)
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
 * Assistente vocale che integra riconoscimento vocale, AI e sintesi vocale.
 */
@Singleton
class VoiceAssistant @Inject constructor(
    private val voiceService: VoiceService,
    private val geminiService: GeminiService,
    private val ttsService: TextToSpeechService
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

    private val _isTtsEnabled = MutableStateFlow(true)
    val isTtsEnabled: StateFlow<Boolean> = _isTtsEnabled.asStateFlow()

    /**
     * Callback quando un'azione deve essere eseguita dalla UI.
     */
    var onActionRequired: ((AssistantAction) -> Unit)? = null

    /**
     * Inizializza l'assistente.
     */
    fun initialize() {
        voiceService.initialize()
        ttsService.initialize()

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

        // Configura callback TTS
        ttsService.onSpeakingDone = {
            if (_state.value is AssistantState.Speaking) {
                _state.value = AssistantState.Idle
            }
        }
    }

    /**
     * Abilita/disabilita il TTS.
     */
    fun setTtsEnabled(enabled: Boolean) {
        _isTtsEnabled.value = enabled
        if (!enabled) {
            ttsService.stop()
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
                speakResponse(result.response)
            }

            is GeminiResult.Error -> {
                _lastResponse.value = result.message
                _state.value = AssistantState.Error(result.message)
                speakResponse(result.message)
            }

            is GeminiResult.ActionRequired -> {
                _lastResponse.value = result.response
                _pendingAction.value = result.action
                _state.value = AssistantState.Speaking(result.response)

                // Parla la risposta
                speakResponse(result.response)

                // Notifica la UI per eseguire l'azione
                onActionRequired?.invoke(result.action)
            }

            is GeminiResult.ConfirmationNeeded -> {
                val fullMessage = "${result.response}\n\n${result.confirmationMessage}"
                _lastResponse.value = fullMessage
                _pendingAction.value = result.action
                _state.value = AssistantState.WaitingConfirmation(result.confirmationMessage)
                speakResponse(fullMessage)
            }
        }
    }

    /**
     * Parla la risposta usando TTS se abilitato.
     * BUGFIX: Applica TtsTextCleaner per rimuovere markdown dal testo.
     */
    private fun speakResponse(text: String) {
        if (_isTtsEnabled.value && ttsService.isAvailable.value) {
            val cleanText = TtsTextCleaner.clean(text)
            ttsService.speak(cleanText)
        } else {
            // Se TTS disabilitato, torna a Idle dopo un delay
            resetToIdleAfterDelay()
        }
    }

    /**
     * Ferma il TTS se sta parlando.
     */
    fun stopSpeaking() {
        ttsService.stop()
        _state.value = AssistantState.Idle
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
        ttsService.release()
        geminiService.resetContext()
    }

    /**
     * Verifica se l'assistente vocale (STT) è disponibile.
     */
    fun isAvailable(): Boolean = voiceService.isAvailable.value

    /**
     * Verifica se il TTS è disponibile.
     */
    fun isTtsAvailable(): Boolean = ttsService.isAvailable.value
}
