# Specifica Tecnica: Implementazione Gemini TTS

## Contesto del Problema

L'app Hospice Inventory utilizza ancora **Android TTS nativo** invece del TTS di Gemini. Nonostante esista già un `GeminiTtsService.kt`, questo è attualmente solo un wrapper per Android TTS con commenti TODO per l'implementazione cloud.

## Obiettivo

Sostituire l'uso di Android TTS con **Gemini 2.5 Flash Preview TTS**, che offre voci neurali di alta qualità e supporto nativo per l'italiano.

## Specifiche Tecniche dell'API Gemini TTS

### Modello da Utilizzare
```
gemini-2.5-flash-preview-tts
```

### Voci Consigliate (Italiano)
- **Kore** - Voce aziendale/professionale (consigliata come primaria)
- **Orus** - Voce aziendale alternativa

### Formato Audio Output
- **Formato**: PCM audio raw
- **Sample Rate**: 24000 Hz
- **Canali**: 1 (mono)
- **Bit Depth**: 16-bit (s16le)

### Lingue Supportate
L'italiano è completamente supportato con codice: `it-IT`

## Implementazione Richiesta

### 1. Dipendenze da Aggiungere (build.gradle.kts)

```kotlin
// Gemini AI SDK (già presente se usi GeminiService)
implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
```

### 2. Configurazione API Call

La chiamata API deve essere configurata con:

```kotlin
val config = GenerationConfig.builder()
    .responseModalities(listOf("AUDIO"))
    .speechConfig(
        SpeechConfig(
            voiceConfig = VoiceConfig(
                prebuiltVoiceConfig = PrebuiltVoiceConfig(
                    voiceName = "Kore"  // oppure "Orus"
                )
            )
        )
    )
    .build()

val response = model.generateContent(
    content {
        text(textToSpeak)
    }
)

// L'audio è in response.candidates[0].content.parts[0].inlineData.data
// come ByteArray in formato PCM raw
```

### 3. Parametri Critici per la Request

| Parametro | Valore | Note |
|-----------|--------|------|
| `model` | `gemini-2.5-flash-preview-tts` | Modello TTS dedicato |
| `responseModalities` | `["AUDIO"]` | **OBBLIGATORIO** per output audio |
| `voiceName` | `Kore` o `Orus` | Voci aziendali italiane |

### 4. Struttura della Response

```json
{
  "candidates": [{
    "content": {
      "parts": [{
        "inlineData": {
          "mimeType": "audio/pcm",
          "data": "<base64_encoded_pcm_audio>"
        }
      }]
    }
  }]
}
```

### 5. Riproduzione Audio PCM su Android

L'audio PCM ricevuto deve essere riprodotto con `AudioTrack`:

```kotlin
private fun playPcmAudio(pcmData: ByteArray) {
    val sampleRate = 24000
    val channelConfig = AudioFormat.CHANNEL_OUT_MONO
    val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    
    val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        channelConfig,
        audioFormat
    )
    
    val audioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .setEncoding(audioFormat)
                .build()
        )
        .setBufferSizeInBytes(bufferSize.coerceAtLeast(pcmData.size))
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
    
    audioTrack.write(pcmData, 0, pcmData.size)
    audioTrack.play()
}
```

## File da Modificare

### `GeminiTtsService.kt`
Sostituire l'implementazione Android TTS con chiamate all'API Gemini TTS.

### Struttura Proposta del Servizio

```kotlin
@Singleton
class GeminiTtsService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GeminiTtsService"
        private const val MODEL_NAME = "gemini-2.5-flash-preview-tts"
        private const val VOICE_NAME = "Kore"  // Voce primaria
        private const val SAMPLE_RATE = 24000
    }

    private val generativeModel: GenerativeModel by lazy {
        GenerativeModel(
            modelName = MODEL_NAME,
            apiKey = BuildConfig.GEMINI_API_KEY,
            generationConfig = generationConfig {
                responseModalities = listOf("AUDIO")
                speechConfig {
                    voiceConfig {
                        prebuiltVoiceConfig {
                            voiceName = VOICE_NAME
                        }
                    }
                }
            }
        )
    }
    
    private var audioTrack: AudioTrack? = null
    
    // State management
    private val _state = MutableStateFlow<TtsProviderState>(TtsProviderState.Ready)
    val state: StateFlow<TtsProviderState> = _state.asStateFlow()
    
    private val _isAvailable = MutableStateFlow(true)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()
    
    // Callbacks
    var onSpeakingStart: (() -> Unit)? = null
    var onSpeakingDone: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * Genera e riproduce audio dal testo usando Gemini TTS.
     */
    fun speak(text: String, flush: Boolean = true) {
        if (text.isBlank()) return
        
        if (flush) {
            stop()
        }
        
        scope.launch {
            try {
                _state.value = TtsProviderState.Speaking(text)
                withContext(Dispatchers.Main) {
                    onSpeakingStart?.invoke()
                }
                
                val response = generativeModel.generateContent(text)
                val audioData = response.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.inlineData
                    ?.data
                
                if (audioData != null) {
                    val pcmBytes = Base64.decode(audioData, Base64.DEFAULT)
                    playPcmAudio(pcmBytes)
                } else {
                    throw Exception("No audio data in response")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "TTS error", e)
                _state.value = TtsProviderState.Error(e.message ?: "Unknown error")
                withContext(Dispatchers.Main) {
                    onError?.invoke(e.message ?: "Errore sintesi vocale")
                }
            }
        }
    }
    
    /**
     * Versione suspend per attendere il completamento.
     */
    suspend fun speakAndWait(text: String): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val originalOnDone = onSpeakingDone
            val originalOnError = onError
            
            onSpeakingDone = {
                originalOnDone?.invoke()
                if (continuation.isActive) {
                    continuation.resume(true)
                }
            }
            
            onError = { error ->
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
    
    private fun playPcmAudio(pcmData: ByteArray) {
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            channelConfig,
            audioFormat
        )
        
        audioTrack?.release()
        
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
                    .setChannelMask(channelConfig)
                    .setEncoding(audioFormat)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(bufferSize, pcmData.size))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .apply {
                setPlaybackPositionUpdateListener(object : 
                    AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(track: AudioTrack?) {
                        _state.value = TtsProviderState.Ready
                        onSpeakingDone?.invoke()
                        track?.release()
                    }
                    override fun onPeriodicNotification(track: AudioTrack?) {}
                })
                
                write(pcmData, 0, pcmData.size)
                // Imposta marker alla fine per callback
                notificationMarkerPosition = pcmData.size / 2  // 2 bytes per sample
                play()
            }
    }
    
    fun stop() {
        audioTrack?.apply {
            if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                stop()
            }
            release()
        }
        audioTrack = null
        _state.value = TtsProviderState.Ready
    }
    
    fun isSpeaking(): Boolean = 
        audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING
    
    fun release() {
        stop()
        scope.cancel()
    }
}
```

## Controllare lo Stile della Voce (Opzionale)

Gemini TTS supporta il controllo dello stile tramite prompt. Per un'app hospice, si può usare:

```kotlin
// Invece di:
speak("L'apparecchiatura è stata registrata con successo")

// Usare prompt con indicazioni di stile:
speak("Say calmly and reassuringly: L'apparecchiatura è stata registrata con successo")
```

## Gestione Errori e Fallback

Se Gemini TTS fallisce (es. offline), considerare:

1. **Retry con backoff** per errori di rete
2. **Coda messaggi** per riprovare quando la connessione torna
3. **Fallback ad Android TTS** solo come ultima risorsa (opzionale)

```kotlin
suspend fun speakWithRetry(text: String, maxRetries: Int = 3): Boolean {
    repeat(maxRetries) { attempt ->
        try {
            return speakAndWait(text)
        } catch (e: Exception) {
            if (attempt < maxRetries - 1) {
                delay((attempt + 1) * 1000L)  // Exponential backoff
            }
        }
    }
    return false
}
```

## Testing

### Test Manuali Prioritari
1. Verifica che l'audio venga riprodotto correttamente
2. Verifica pronuncia italiana corretta (es. nomi prodotti, numeri)
3. Verifica interruzione audio quando necessario (flush)
4. Verifica callback onSpeakingStart/onSpeakingDone
5. Verifica gestione errori di rete

### Test Unitari
```kotlin
@Test
fun `speak should emit Speaking state`() = runTest {
    val service = GeminiTtsService(mockContext)
    service.speak("Test")
    assertEquals(TtsProviderState.Speaking("Test"), service.state.first())
}
```

## Note Importanti

1. **Rate Limiting**: Il modello TTS ha un limite di 32.000 token per sessione
2. **Latenza**: Aspettarsi 200-500ms di latenza per la generazione audio
3. **Costi**: Verificare i costi API per uso in produzione
4. **Network**: Richiede connessione internet (nessun fallback offline nativo)

## Riferimenti

- [Documentazione Gemini TTS](https://ai.google.dev/gemini-api/docs/speech-generation)
- [Cookbook TTS](https://colab.research.google.com/github/google-gemini/cookbook/blob/main/quickstarts/Get_started_TTS.ipynb)
- [AI Studio - Test Voci](https://aistudio.google.com/generate-speech)
