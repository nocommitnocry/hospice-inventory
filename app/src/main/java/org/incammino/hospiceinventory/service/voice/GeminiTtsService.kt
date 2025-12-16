package org.incammino.hospiceinventory.service.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * P6 FIX: Servizio TTS avanzato con fallback.
 *
 * Architettura:
 * - Primario: Cloud TTS (Google Cloud Text-to-Speech) - voce neurale naturale [TODO: implementare]
 * - Fallback: Android TTS nativo - gratuito, funziona offline
 *
 * Il servizio sceglie automaticamente il provider migliore disponibile:
 * 1. Se online e Cloud TTS configurato -> usa Cloud TTS
 * 2. Altrimenti -> fallback ad Android TTS
 *
 * Per abilitare Cloud TTS in futuro:
 * 1. Aggiungere dependency: com.google.cloud:google-cloud-texttospeech
 * 2. Configurare credenziali Google Cloud
 * 3. Implementare `CloudTtsProvider`
 *
 * Costi stimati Cloud TTS: ~$6/mese per uso tipico hospice
 * (100 interazioni/giorno, ~200 char/risposta)
 */
@Singleton
class GeminiTtsService @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "GeminiTtsService"

        // Configurazione voce (per futuro Cloud TTS)
        private const val VOICE_LANGUAGE = "it-IT"
        private const val VOICE_NAME = "it-IT-Neural2-A"  // Voce italiana naturale Google Cloud
        private const val SPEAKING_RATE = 1.1f  // Leggermente più veloce
        private const val PITCH = 0.0f
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════════════════

    sealed class TtsProviderState {
        data object Initializing : TtsProviderState()
        data object Ready : TtsProviderState()
        data class Speaking(val text: String) : TtsProviderState()
        data class Error(val message: String) : TtsProviderState()
        data object Unavailable : TtsProviderState()
    }

    private val _state = MutableStateFlow<TtsProviderState>(TtsProviderState.Initializing)
    val state: StateFlow<TtsProviderState> = _state.asStateFlow()

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════════════════
    // ANDROID TTS (Fallback)
    // ═══════════════════════════════════════════════════════════════════════════════

    private var androidTts: TextToSpeech? = null
    private var isAndroidTtsReady = false
    private var utteranceId = 0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Callbacks
    var onSpeakingStart: (() -> Unit)? = null
    var onSpeakingDone: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    // ═══════════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Inizializza il servizio TTS.
     */
    fun initialize() {
        _state.value = TtsProviderState.Initializing

        // Per ora usiamo solo Android TTS come fallback
        // TODO: Aggiungere inizializzazione Cloud TTS quando implementato
        initializeAndroidTts()
    }

    private fun initializeAndroidTts() {
        try {
            androidTts = TextToSpeech(context, this)
            Log.i(TAG, "Android TTS initialization started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Android TTS", e)
            _state.value = TtsProviderState.Unavailable
            _isAvailable.value = false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = androidTts?.setLanguage(Locale.ITALIAN)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Italian not supported, trying fallback")
                val fallbackResult = androidTts?.setLanguage(Locale("it"))
                if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Italian not available")
                    _state.value = TtsProviderState.Error("Italiano non disponibile")
                    _isAvailable.value = false
                    return
                }
            }

            // Configura listener e parametri
            androidTts?.setOnUtteranceProgressListener(createUtteranceListener())
            androidTts?.setSpeechRate(SPEAKING_RATE)
            androidTts?.setPitch(1.0f + PITCH)

            isAndroidTtsReady = true
            _isAvailable.value = true
            _state.value = TtsProviderState.Ready
            Log.i(TAG, "Android TTS initialized successfully (fallback ready)")
        } else {
            Log.e(TAG, "Android TTS initialization failed: $status")
            _state.value = TtsProviderState.Unavailable
            _isAvailable.value = false
        }
    }

    private fun createUtteranceListener(): UtteranceProgressListener {
        return object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS started: $utteranceId")
                onSpeakingStart?.invoke()
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS done: $utteranceId")
                _state.value = TtsProviderState.Ready
                onSpeakingDone?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error: $utteranceId")
                _state.value = TtsProviderState.Error("Errore sintesi vocale")
                onError?.invoke("Errore sintesi vocale")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                val errorMessage = "Errore TTS: $errorCode"
                Log.e(TAG, errorMessage)
                _state.value = TtsProviderState.Error(errorMessage)
                onError?.invoke(errorMessage)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // SPEAK METHODS
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Legge il testo ad alta voce.
     *
     * Sceglie automaticamente il provider migliore disponibile.
     *
     * @param text Testo da leggere
     * @param flush Se true, interrompe qualsiasi sintesi in corso
     */
    fun speak(text: String, flush: Boolean = true) {
        if (!_isAvailable.value) {
            Log.w(TAG, "TTS not available")
            return
        }

        if (text.isBlank()) {
            Log.w(TAG, "Empty text, nothing to speak")
            return
        }

        // TODO: Quando Cloud TTS è disponibile, usarlo come primario
        // Per ora, usa sempre Android TTS
        speakWithAndroidTts(text, flush)
    }

    private fun speakWithAndroidTts(text: String, flush: Boolean) {
        if (!isAndroidTtsReady) {
            Log.w(TAG, "Android TTS not ready")
            return
        }

        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val id = "utterance_${++utteranceId}"

        _state.value = TtsProviderState.Speaking(text)

        val params = android.os.Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
        }

        val result = androidTts?.speak(text, queueMode, params, id)
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "Android TTS speak failed")
            _state.value = TtsProviderState.Error("Impossibile leggere il testo")
        }
    }

    /**
     * Legge il testo e attende il completamento.
     */
    suspend fun speakAndWait(text: String): Boolean {
        if (!_isAvailable.value || text.isBlank()) {
            return false
        }

        return suspendCancellableCoroutine { continuation ->
            val id = "utterance_${++utteranceId}"
            _state.value = TtsProviderState.Speaking(text)

            val listener = object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id) {
                        _state.value = TtsProviderState.Ready
                        continuation.resume(true)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id) {
                        _state.value = TtsProviderState.Ready
                        continuation.resume(false)
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == id) {
                        _state.value = TtsProviderState.Ready
                        continuation.resume(false)
                    }
                }
            }

            androidTts?.setOnUtteranceProgressListener(listener)

            val params = android.os.Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
            }

            val result = androidTts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
            if (result == TextToSpeech.ERROR) {
                _state.value = TtsProviderState.Ready
                continuation.resume(false)
            }

            continuation.invokeOnCancellation {
                stop()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // CONTROL METHODS
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Ferma la sintesi in corso.
     */
    fun stop() {
        androidTts?.stop()
        _state.value = TtsProviderState.Ready
    }

    /**
     * Imposta la velocità di lettura.
     */
    fun setSpeechRate(rate: Float) {
        androidTts?.setSpeechRate(rate.coerceIn(0.25f, 4.0f))
    }

    /**
     * Imposta il tono della voce.
     */
    fun setPitch(pitch: Float) {
        androidTts?.setPitch(pitch.coerceIn(0.25f, 4.0f))
    }

    /**
     * Verifica se sta parlando.
     */
    fun isSpeaking(): Boolean = androidTts?.isSpeaking == true

    /**
     * Rilascia le risorse.
     */
    fun release() {
        androidTts?.stop()
        androidTts?.shutdown()
        androidTts = null
        isAndroidTtsReady = false
        _state.value = TtsProviderState.Initializing
        _isAvailable.value = false
        Log.i(TAG, "GeminiTtsService released")
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // CLOUD TTS (TODO: Future implementation)
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * TODO: Implementare quando si decide di usare Google Cloud TTS.
     *
     * Richiede:
     * 1. Dependency: implementation("com.google.cloud:google-cloud-texttospeech:...")
     * 2. Credenziali Google Cloud (service account JSON)
     * 3. Abilitare Cloud Text-to-Speech API nel progetto GCP
     *
     * Esempio implementazione:
     * ```kotlin
     * private suspend fun speakWithCloudTts(text: String) {
     *     val client = TextToSpeechClient.create()
     *     val input = SynthesisInput.newBuilder().setText(text).build()
     *     val voice = VoiceSelectionParams.newBuilder()
     *         .setLanguageCode("it-IT")
     *         .setName("it-IT-Neural2-A")
     *         .build()
     *     val audioConfig = AudioConfig.newBuilder()
     *         .setAudioEncoding(AudioEncoding.LINEAR16)
     *         .setSpeakingRate(1.1)
     *         .build()
     *
     *     val response = client.synthesizeSpeech(input, voice, audioConfig)
     *     playAudio(response.audioContent.toByteArray())
     * }
     * ```
     */
    private fun cloudTtsNotImplemented() {
        Log.w(TAG, "Cloud TTS not yet implemented - using Android TTS fallback")
    }
}
