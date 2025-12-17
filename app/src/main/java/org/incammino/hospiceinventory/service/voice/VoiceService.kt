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
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isInitialized = false

    // P2 FIX: Variabili per gestione timeout intelligente
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var silenceJob: Job? = null
    private var accumulatedText = StringBuilder()
    private var lastConfidence = 0f

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
            // P2 FIX: Reset delle variabili di accumulo
            silenceJob?.cancel()
            silenceJob = null
            accumulatedText.clear()
            lastConfidence = 0f

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
            // P2 FIX: Cancella il job di silenzio e lo scope
            silenceJob?.cancel()
            silenceJob = null
            scope.cancel()

            speechRecognizer?.destroy()
            speechRecognizer = null
            isInitialized = false
            accumulatedText.clear()
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
                Log.d(TAG, "End of speech detected - starting silence timer")
                _state.value = VoiceState.Processing

                // P2 FIX: Invece di processare subito, avvia timer di attesa
                // Questo permette all'utente di riprendere a parlare dopo una pausa
                silenceJob?.cancel()
                silenceJob = scope.launch {
                    delay(SILENCE_DELAY_MS)
                    // Se arriviamo qui, l'utente non ha ripreso a parlare
                    Log.d(TAG, "Silence timeout reached - finalizing result")
                    finalizeResult()
                }
            }

            override fun onError(error: Int) {
                val errorMessage = VoiceErrorCodes.getErrorMessage(error)
                Log.w(TAG, "Recognition error: $error - $errorMessage")

                // P2 FIX: Cancella il timer
                silenceJob?.cancel()
                silenceJob = null

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
                    // Se abbiamo testo accumulato, usalo comunque
                    if (accumulatedText.isNotEmpty()) {
                        finalizeResult()
                    } else {
                        _state.value = VoiceState.Error("Nessun risultato", SpeechRecognizer.ERROR_NO_MATCH)
                        onError?.invoke("Nessun risultato", SpeechRecognizer.ERROR_NO_MATCH)
                    }
                    return
                }

                val bestMatch = matches[0]
                val confidence = confidences?.getOrNull(0) ?: 0f

                Log.i(TAG, "Result: '$bestMatch' (confidence: $confidence)")

                // P2 FIX: Accumula il testo invece di processarlo subito
                accumulatedText.append(" ").append(bestMatch)
                lastConfidence = confidence

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

                    // Rimuovi la trigger word dal testo finale
                    val cleanedText = removeTriggerWord(accumulatedText.toString().trim())
                    emitFinalResult(cleanedText, confidence)
                } else {
                    // Mostra risultato parziale mentre aspettiamo
                    _state.value = VoiceState.PartialResult(accumulatedText.toString().trim())
                    onPartialResult?.invoke(accumulatedText.toString().trim())
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

// ═══════════════════════════════════════════════════════════════════════════════
// TTS TEXT CLEANER - Rimuove markdown per TTS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * BUGFIX: Rimuove la formattazione markdown e i tag interni dal testo per il TTS.
 * Evita che il TTS legga "asterisco asterisco parola asterisco asterisco" o
 * "TASK UPDATE type RIPARAZIONE".
 */
object TtsTextCleaner {

    /**
     * Pulisce il testo rimuovendo markdown e tag interni per una lettura naturale.
     */
    fun clean(text: String): String {
        return text
            // TAG INTERNI (rimuovere PRIMA di tutto il resto)
            .replace(Regex("""\[TASK_UPDATE:[^\]]*\]"""), "")
            .replace(Regex("""\[ACTION:[^\]]*\]"""), "")

            // Bold e italic
            .replace(Regex("""\*\*\*(.+?)\*\*\*"""), "$1")  // ***bold italic***
            .replace(Regex("""\*\*(.+?)\*\*"""), "$1")       // **bold**
            .replace(Regex("""\*(.+?)\*"""), "$1")           // *italic*
            .replace(Regex("""__(.+?)__"""), "$1")           // __bold__
            .replace(Regex("""_(.+?)_"""), "$1")             // _italic_

            // Headers
            .replace(Regex("""^#{1,6}\s*""", RegexOption.MULTILINE), "")

            // Liste
            .replace(Regex("""^\s*[-*+]\s+""", RegexOption.MULTILINE), "")
            .replace(Regex("""^\s*\d+\.\s+""", RegexOption.MULTILINE), "")

            // Links [text](url) → text
            .replace(Regex("""\[([^\]]+)\]\([^)]+\)"""), "$1")

            // Code blocks e inline code
            .replace(Regex("""```[^`]*```""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""`([^`]+)`"""), "$1")

            // Caratteri residui
            .replace("*", "")
            .replace("_", " ")
            .replace("#", "")

            // Cleanup spazi multipli e newline
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
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
