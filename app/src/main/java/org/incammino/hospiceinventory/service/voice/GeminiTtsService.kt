package org.incammino.hospiceinventory.service.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.incammino.hospiceinventory.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Servizio TTS avanzato con Gemini 2.5 Flash TTS e fallback Android.
 *
 * Architettura:
 * - Primario: Gemini 2.5 Flash Preview TTS - voce neurale italiana di alta qualità
 * - Fallback: Android TTS nativo - funziona offline
 *
 * Il servizio sceglie automaticamente il provider migliore:
 * 1. Se online -> usa Gemini TTS (voce Kore)
 * 2. Se offline o errore -> fallback ad Android TTS
 *
 * @see <a href="https://ai.google.dev/gemini-api/docs/speech-generation">Gemini TTS Docs</a>
 */
@Singleton
class GeminiTtsService @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "GeminiTtsService"

        // Gemini TTS Configuration
        private const val GEMINI_TTS_MODEL = "gemini-2.5-flash-preview-tts"
        private const val GEMINI_TTS_VOICE = "Kore"  // Voce aziendale italiana
        private const val GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"

        // Audio Configuration (PCM output from Gemini)
        private const val SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Android TTS Configuration
        private const val ANDROID_TTS_SPEAKING_RATE = 1.1f
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
    // HTTP CLIENT (for Gemini TTS API)
    // ═══════════════════════════════════════════════════════════════════════════════

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)  // TTS può richiedere tempo
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // ═══════════════════════════════════════════════════════════════════════════════
    // AUDIO PLAYBACK
    // ═══════════════════════════════════════════════════════════════════════════════

    private var audioTrack: AudioTrack? = null

    // ═══════════════════════════════════════════════════════════════════════════════
    // ANDROID TTS (Fallback)
    // ═══════════════════════════════════════════════════════════════════════════════

    private var androidTts: TextToSpeech? = null
    private var isAndroidTtsReady = false
    private var utteranceId = 0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Callbacks
    var onSpeakingStart: (() -> Unit)? = null
    var onSpeakingDone: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    // Flag per preferenza provider
    private var useGeminiTts = true  // Prova prima Gemini

    // ═══════════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Inizializza il servizio TTS.
     */
    fun initialize() {
        _state.value = TtsProviderState.Initializing

        // Inizializza Android TTS come fallback
        initializeAndroidTts()

        // Verifica disponibilità Gemini TTS
        useGeminiTts = BuildConfig.GEMINI_API_KEY.isNotBlank()
        if (!useGeminiTts) {
            Log.w(TAG, "Gemini API key not configured, using Android TTS only")
        }
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
            androidTts?.setSpeechRate(ANDROID_TTS_SPEAKING_RATE)

            isAndroidTtsReady = true
            _isAvailable.value = true
            _state.value = TtsProviderState.Ready
            Log.i(TAG, "TTS service ready (Gemini primary: $useGeminiTts, Android fallback: ready)")
        } else {
            Log.e(TAG, "Android TTS initialization failed: $status")
            // Se abbiamo Gemini, siamo comunque disponibili
            if (useGeminiTts) {
                _isAvailable.value = true
                _state.value = TtsProviderState.Ready
            } else {
                _state.value = TtsProviderState.Unavailable
                _isAvailable.value = false
            }
        }
    }

    private fun createUtteranceListener(): UtteranceProgressListener {
        return object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "Android TTS started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "Android TTS done: $utteranceId")
                _state.value = TtsProviderState.Ready
                onSpeakingDone?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "Android TTS error: $utteranceId")
                _state.value = TtsProviderState.Error("Errore sintesi vocale")
                onError?.invoke("Errore sintesi vocale")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "Android TTS error code: $errorCode")
                _state.value = TtsProviderState.Error("Errore TTS: $errorCode")
                onError?.invoke("Errore TTS: $errorCode")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // SPEAK METHODS
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Legge il testo ad alta voce.
     *
     * Prova prima con Gemini TTS, fallback ad Android TTS se fallisce.
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

        if (flush) {
            stop()
        }

        scope.launch {
            // Pulisci il testo da tag e markdown prima di sintetizzare
            val cleanText = TtsTextCleaner.clean(text)

            if (useGeminiTts) {
                val success = speakWithGeminiTts(cleanText)
                if (!success && isAndroidTtsReady) {
                    Log.w(TAG, "Gemini TTS failed, falling back to Android TTS")
                    withContext(Dispatchers.Main) {
                        speakWithAndroidTts(cleanText, flush = false)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    speakWithAndroidTts(cleanText, flush)
                }
            }
        }
    }

    /**
     * Sintetizza audio con Gemini 2.5 Flash TTS.
     *
     * @return true se successo, false se fallito
     */
    private suspend fun speakWithGeminiTts(text: String): Boolean {
        return try {
            _state.value = TtsProviderState.Speaking(text)
            withContext(Dispatchers.Main) {
                onSpeakingStart?.invoke()
            }

            val audioData = callGeminiTtsApi(text)

            if (audioData != null) {
                playPcmAudio(audioData)
                true
            } else {
                Log.w(TAG, "No audio data received from Gemini TTS")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini TTS error", e)
            _state.value = TtsProviderState.Error(e.message ?: "Gemini TTS error")
            false
        }
    }

    /**
     * Chiama l'API Gemini TTS e restituisce i dati audio PCM.
     */
    private suspend fun callGeminiTtsApi(text: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank()) {
                Log.e(TAG, "Gemini API key not configured")
                return@withContext null
            }

            val url = "$GEMINI_API_BASE/$GEMINI_TTS_MODEL:generateContent?key=$apiKey"

            // Costruisci il body JSON per la richiesta TTS
            val requestBody = buildTtsRequestBody(text)

            val request = Request.Builder()
                .url(url)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            Log.d(TAG, "Calling Gemini TTS API for text: ${text.take(50)}...")

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e(TAG, "Gemini TTS API error ${response.code}: $errorBody")
                return@withContext null
            }

            val responseBody = response.body?.string() ?: return@withContext null
            parseAudioFromResponse(responseBody)

        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini TTS API", e)
            null
        }
    }

    /**
     * Costruisce il body JSON per la richiesta TTS.
     */
    private fun buildTtsRequestBody(text: String): String {
        val requestJson = JSONObject().apply {
            // Contents
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", text)
                        })
                    })
                })
            })

            // Generation config con audio output
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().apply {
                    put("AUDIO")
                })
                put("speechConfig", JSONObject().apply {
                    put("voiceConfig", JSONObject().apply {
                        put("prebuiltVoiceConfig", JSONObject().apply {
                            put("voiceName", GEMINI_TTS_VOICE)
                        })
                    })
                })
            })
        }

        return requestJson.toString()
    }

    /**
     * Estrae i dati audio dalla risposta JSON di Gemini.
     */
    private fun parseAudioFromResponse(responseBody: String): ByteArray? {
        return try {
            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null

            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            if (parts.length() == 0) return null

            val part = parts.getJSONObject(0)
            val inlineData = part.optJSONObject("inlineData") ?: return null

            val mimeType = inlineData.optString("mimeType", "")
            val base64Data = inlineData.optString("data", "")

            if (base64Data.isBlank()) {
                Log.w(TAG, "No audio data in response")
                return null
            }

            Log.d(TAG, "Received audio data, mimeType: $mimeType, size: ${base64Data.length} chars")

            Base64.decode(base64Data, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing audio from response", e)
            null
        }
    }

    /**
     * Riproduce audio PCM usando AudioTrack.
     */
    private suspend fun playPcmAudio(pcmData: ByteArray) = withContext(Dispatchers.Main) {
        try {
            // Rilascia AudioTrack precedente
            audioTrack?.release()

            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .setEncoding(AUDIO_FORMAT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(bufferSize, pcmData.size))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack?.apply {
                // Scrivi i dati audio
                val written = write(pcmData, 0, pcmData.size)
                Log.d(TAG, "Written $written bytes to AudioTrack")

                // Imposta listener per notifica fine riproduzione
                val samplesCount = pcmData.size / 2  // 16-bit = 2 bytes per sample
                setNotificationMarkerPosition(samplesCount)
                setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(track: AudioTrack?) {
                        Log.d(TAG, "Gemini TTS playback completed")
                        _state.value = TtsProviderState.Ready
                        onSpeakingDone?.invoke()
                        track?.release()
                        audioTrack = null
                    }

                    override fun onPeriodicNotification(track: AudioTrack?) {}
                })

                // Avvia riproduzione
                play()
                Log.d(TAG, "Gemini TTS playback started, duration: ${samplesCount / SAMPLE_RATE.toFloat()}s")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing PCM audio", e)
            _state.value = TtsProviderState.Error("Errore riproduzione audio")
            onError?.invoke("Errore riproduzione audio")
        }
    }

    private fun speakWithAndroidTts(text: String, flush: Boolean) {
        if (!isAndroidTtsReady) {
            Log.w(TAG, "Android TTS not ready")
            onError?.invoke("TTS non disponibile")
            return
        }

        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val id = "utterance_${++utteranceId}"

        _state.value = TtsProviderState.Speaking(text)
        onSpeakingStart?.invoke()

        val params = android.os.Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
        }

        val result = androidTts?.speak(text, queueMode, params, id)
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "Android TTS speak failed")
            _state.value = TtsProviderState.Error("Impossibile leggere il testo")
            onError?.invoke("Impossibile leggere il testo")
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
            val originalOnDone = onSpeakingDone
            val originalOnError = onError

            onSpeakingDone = {
                onSpeakingDone = originalOnDone
                onError = originalOnError
                originalOnDone?.invoke()
                if (continuation.isActive) {
                    continuation.resume(true)
                }
            }

            onError = { error ->
                onSpeakingDone = originalOnDone
                onError = originalOnError
                originalOnError?.invoke(error)
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }

            continuation.invokeOnCancellation {
                stop()
                onSpeakingDone = originalOnDone
                onError = originalOnError
            }

            speak(text)
        }
    }

    /**
     * Legge il testo con retry in caso di errore.
     */
    suspend fun speakWithRetry(text: String, maxRetries: Int = 3): Boolean {
        repeat(maxRetries) { attempt ->
            try {
                if (speakAndWait(text)) {
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "TTS attempt ${attempt + 1} failed", e)
            }
            if (attempt < maxRetries - 1) {
                delay((attempt + 1) * 1000L)  // Exponential backoff
            }
        }
        return false
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // CONTROL METHODS
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Ferma la sintesi in corso.
     */
    fun stop() {
        // Stop AudioTrack (Gemini TTS)
        audioTrack?.apply {
            try {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping AudioTrack", e)
            }
        }
        audioTrack = null

        // Stop Android TTS
        androidTts?.stop()

        _state.value = TtsProviderState.Ready
    }

    /**
     * Imposta la velocità di lettura (solo Android TTS).
     */
    fun setSpeechRate(rate: Float) {
        androidTts?.setSpeechRate(rate.coerceIn(0.25f, 4.0f))
    }

    /**
     * Imposta il tono della voce (solo Android TTS).
     */
    fun setPitch(pitch: Float) {
        androidTts?.setPitch(pitch.coerceIn(0.25f, 4.0f))
    }

    /**
     * Verifica se sta parlando.
     */
    fun isSpeaking(): Boolean {
        return audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING ||
                androidTts?.isSpeaking == true
    }

    /**
     * Forza l'uso di Android TTS (disabilita Gemini TTS).
     */
    fun setUseAndroidTtsOnly(value: Boolean) {
        useGeminiTts = !value && BuildConfig.GEMINI_API_KEY.isNotBlank()
        Log.i(TAG, "TTS provider: ${if (useGeminiTts) "Gemini" else "Android"}")
    }

    /**
     * Rilascia le risorse.
     */
    fun release() {
        stop()
        scope.cancel()
        androidTts?.shutdown()
        androidTts = null
        isAndroidTtsReady = false
        _state.value = TtsProviderState.Initializing
        _isAvailable.value = false
        Log.i(TAG, "GeminiTtsService released")
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TTS TEXT CLEANER
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Pulisce il testo prima della sintesi vocale.
 * Rimuove tag interni, markdown e altri elementi non pronunciabili.
 */
object TtsTextCleaner {

    /**
     * Pulisce il testo rimuovendo elementi non pronunciabili.
     */
    fun clean(text: String): String {
        var result = text

        // 1. Rimuovi tag interni [ACTION:...], [TASK_UPDATE:...]
        result = result.replace(Regex("""\[(?:ACTION|TASK_UPDATE):[^\]]*\]"""), "")

        // 2. Rimuovi markdown bold/italic
        result = result.replace(Regex("""\*\*([^*]+)\*\*"""), "$1")  // **bold**
        result = result.replace(Regex("""\*([^*]+)\*"""), "$1")      // *italic*
        result = result.replace(Regex("""__([^_]+)__"""), "$1")      // __bold__
        result = result.replace(Regex("""_([^_]+)_"""), "$1")        // _italic_

        // 3. Rimuovi headers markdown
        result = result.replace(Regex("""^#{1,6}\s+""", RegexOption.MULTILINE), "")

        // 4. Rimuovi liste markdown
        result = result.replace(Regex("""^[-*+]\s+""", RegexOption.MULTILINE), "")
        result = result.replace(Regex("""^\d+\.\s+""", RegexOption.MULTILINE), "")

        // 5. Rimuovi code blocks e inline code
        result = result.replace(Regex("""```[^`]*```""", RegexOption.DOT_MATCHES_ALL), "")
        result = result.replace(Regex("""`([^`]+)`"""), "$1")

        // 6. Rimuovi links markdown [text](url)
        result = result.replace(Regex("""\[([^\]]+)\]\([^)]+\)"""), "$1")

        // 7. Normalizza spazi multipli
        result = result.replace(Regex("""\s+"""), " ")

        return result.trim()
    }
}
