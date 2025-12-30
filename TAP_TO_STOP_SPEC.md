# 🎤 Tap-to-Stop: Specifica Implementazione

> **Filosofia**: Brutti ma buoni. Funziona > Bello.

## Problema

SpeechRecognizer Android taglia l'ascolto prematuramente nel 95% dei casi per input lunghi (registrazione prodotti, manutenzioni dettagliate). Il workaround `SILENCE_DELAY_MS` non basta perché `onEndOfSpeech()` arriva troppo presto.

## Soluzione

**Tap-to-Start, Tap-to-Stop**: L'utente controlla quando iniziare e quando finire. Nessun timeout automatico.

---

## 1. Modifiche a VoiceService.kt

### 1.1 Nuova Modalità di Ascolto

```kotlin
// Aggiungi enum per modalità
enum class ListeningMode {
    AUTO_STOP,      // Comportamento legacy (timeout-based)
    MANUAL_STOP     // Nuovo: tap-to-stop
}

// Aggiungi parametro a startListening
fun startListening(mode: ListeningMode = ListeningMode.MANUAL_STOP) {
    // ... codice esistente ...
    currentMode = mode
}
```

### 1.2 Modifica createRecognitionListener()

```kotlin
private fun createRecognitionListener(): RecognitionListener {
    return object : RecognitionListener {
        
        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech detected")
            
            when (currentMode) {
                ListeningMode.AUTO_STOP -> {
                    // Comportamento legacy con silence delay
                    _state.value = VoiceState.Processing
                    silenceJob = scope.launch {
                        delay(SILENCE_DELAY_MS)
                        finalizeResult()
                    }
                }
                ListeningMode.MANUAL_STOP -> {
                    // NUOVO: Non fare nulla! Continua ad accumulare.
                    // L'utente decide quando fermare con stopListening()
                    Log.d(TAG, "Manual mode - waiting for user to stop")
                    
                    // Riavvia ascolto automaticamente per continuare
                    restartListeningQuietly()
                }
            }
        }
        
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val bestMatch = matches?.firstOrNull() ?: ""
            
            when (currentMode) {
                ListeningMode.AUTO_STOP -> {
                    // Comportamento legacy
                    silenceJob?.cancel()
                    if (bestMatch.isNotEmpty()) {
                        accumulatedText.append(" ").append(bestMatch)
                    }
                    silenceJob = scope.launch {
                        delay(SILENCE_DELAY_MS)
                        finalizeResult()
                    }
                }
                ListeningMode.MANUAL_STOP -> {
                    // NUOVO: Accumula e continua
                    if (bestMatch.isNotEmpty()) {
                        accumulatedText.append(" ").append(bestMatch)
                        // Notifica UI del testo accumulato
                        _state.value = VoiceState.PartialResult(accumulatedText.toString().trim())
                    }
                    // Riavvia per continuare ad ascoltare
                    restartListeningQuietly()
                }
            }
        }
        
        override fun onError(error: Int) {
            Log.w(TAG, "Recognition error: $error")
            
            when (currentMode) {
                ListeningMode.AUTO_STOP -> {
                    // Comportamento legacy
                    handleErrorLegacy(error)
                }
                ListeningMode.MANUAL_STOP -> {
                    // NUOVO: Errori recuperabili → riavvia silenziosamente
                    val isRecoverable = error in listOf(
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        SpeechRecognizer.ERROR_CLIENT
                    )
                    
                    if (isRecoverable && isManualListeningActive) {
                        Log.d(TAG, "Recoverable error in manual mode, restarting...")
                        restartListeningQuietly()
                    } else {
                        // Errore critico
                        handleCriticalError(error)
                    }
                }
            }
        }
        
        // ... altri override invariati ...
    }
}
```

### 1.3 Nuovi Metodi Helper

```kotlin
private var isManualListeningActive = false
private var consecutiveErrors = 0
private const val MAX_CONSECUTIVE_ERRORS = 3

/**
 * Riavvia l'ascolto senza notificare l'UI.
 * Usato in MANUAL_STOP per continuare dopo onEndOfSpeech/onResults.
 */
private fun restartListeningQuietly() {
    if (!isManualListeningActive) return
    
    scope.launch {
        delay(100) // Piccola pausa per evitare race condition
        try {
            speechRecognizer?.cancel()
            delay(50)
            val intent = createRecognizerIntent()
            speechRecognizer?.startListening(intent)
            // NON cambiare state - rimane Listening/PartialResult
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
 * Ferma l'ascolto manuale e finalizza.
 * Chiamato quando l'utente preme il pulsante STOP.
 */
fun stopManualListening() {
    isManualListeningActive = false
    silenceJob?.cancel()
    
    try {
        speechRecognizer?.stopListening()
    } catch (e: Exception) {
        Log.e(TAG, "Error stopping", e)
    }
    
    // Finalizza con quello che abbiamo
    val finalText = accumulatedText.toString().trim()
    if (finalText.isNotEmpty()) {
        emitFinalResult(finalText, lastConfidence)
    } else {
        _state.value = VoiceState.Idle
    }
}

/**
 * Avvia ascolto in modalità manuale.
 */
fun startManualListening() {
    consecutiveErrors = 0
    isManualListeningActive = true
    accumulatedText.clear()
    startListening(ListeningMode.MANUAL_STOP)
}

private fun handleCriticalError(error: Int) {
    isManualListeningActive = false
    val message = VoiceErrorCodes.getErrorMessage(error)
    _state.value = VoiceState.Error(message, error)
    onError?.invoke(message, error)
    
    // Se troppi errori, suggerisci riavvio
    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
        _state.value = VoiceState.Error(
            "Riconoscimento instabile. Prova a riavviare l'app.",
            error
        )
    }
}
```

### 1.4 Aggiorna startListening() Esistente

```kotlin
fun startListening(mode: ListeningMode = ListeningMode.MANUAL_STOP) {
    if (!isInitialized) {
        initialize()
    }

    if (!_isAvailable.value) {
        _state.value = VoiceState.Error("Riconoscimento vocale non disponibile", -1)
        return
    }

    // NUOVO: Gestisci già in ascolto
    if (_state.value is VoiceState.Listening || _state.value is VoiceState.PartialResult) {
        if (mode == ListeningMode.MANUAL_STOP) {
            Log.d(TAG, "Already listening in manual mode")
            return
        }
    }

    try {
        currentMode = mode
        silenceJob?.cancel()
        silenceJob = null
        accumulatedText.clear()
        lastConfidence = 0f
        consecutiveErrors = 0
        
        if (mode == ListeningMode.MANUAL_STOP) {
            isManualListeningActive = true
        }

        val intent = createRecognizerIntent()
        speechRecognizer?.startListening(intent)
        _state.value = VoiceState.Listening
        Log.d(TAG, "Started listening in $mode mode")
    } catch (e: Exception) {
        Log.e(TAG, "Error starting listening", e)
        _state.value = VoiceState.Error("Errore avvio ascolto", -1)
    }
}
```

---

## 2. Modifiche UI - Pattern Generico

### 2.1 Composable VoiceDumpButton

```kotlin
/**
 * Pulsante Tap-to-Start / Tap-to-Stop per voice dump.
 * 
 * Uso:
 *   VoiceDumpButton(
 *       voiceState = viewModel.voiceState,
 *       partialText = viewModel.partialText,
 *       onStartListening = { viewModel.startVoiceDump() },
 *       onStopListening = { viewModel.stopVoiceDump() }
 *   )
 */
@Composable
fun VoiceDumpButton(
    voiceState: VoiceState,
    partialText: String,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = voiceState is VoiceState.Listening || 
                      voiceState is VoiceState.PartialResult
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Testo parziale durante ascolto
        if (isListening && partialText.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = partialText,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        
        // Pulsante principale
        Button(
            onClick = {
                if (isListening) {
                    onStopListening()
                } else {
                    onStartListening()
                }
            },
            modifier = Modifier
                .size(if (isListening) 120.dp else 80.dp)
                .animateContentSize(),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isListening) 
                    Color.Red 
                else 
                    MaterialTheme.colorScheme.primary
            )
        ) {
            if (isListening) {
                // Stato RECORDING - mostra STOP
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                    Text(
                        "STOP",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Stato IDLE - mostra MIC
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Parla",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            }
        }
        
        // Label sotto il pulsante
        Text(
            text = if (isListening) 
                "Tap quando hai finito" 
            else 
                "Tap per parlare",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        
        // Indicatore recording animato
        if (isListening) {
            RecordingIndicator()
        }
    }
}

@Composable
private fun RecordingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "recording")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Row(
        modifier = Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    color = Color.Red.copy(alpha = alpha),
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "● REC",
            color = Color.Red.copy(alpha = alpha),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}
```

---

## 3. Uso nei ViewModel

### 3.1 Pattern Base

```kotlin
// In qualsiasi ViewModel che usa voice dump

@HiltViewModel
class VoiceProductViewModel @Inject constructor(
    private val voiceService: VoiceService,
    // ...
) : ViewModel() {

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()
    
    val voiceState: StateFlow<VoiceState> = voiceService.state

    init {
        // Osserva partial results
        viewModelScope.launch {
            voiceService.state.collect { state ->
                when (state) {
                    is VoiceState.PartialResult -> {
                        _partialText.value = state.text
                    }
                    is VoiceState.Result -> {
                        // Testo finale pronto
                        processVoiceDump(state.text)
                    }
                    else -> {}
                }
            }
        }
    }

    fun startVoiceDump() {
        _partialText.value = ""
        voiceService.startManualListening()
    }

    fun stopVoiceDump() {
        voiceService.stopManualListening()
        // Il risultato arriverà via state collector
    }

    private fun processVoiceDump(text: String) {
        viewModelScope.launch {
            // Invia a Gemini per parsing...
        }
    }
}
```

---

## 4. Schermate da Aggiornare

### Lista file da modificare:

1. **VoiceService.kt** - Aggiungere `ListeningMode`, `startManualListening()`, `stopManualListening()`

2. **VoiceProductScreen.kt** (o equivalente) - Usare nuovo `VoiceDumpButton`

3. **VoiceMaintenanceScreen.kt** (o equivalente) - Usare nuovo `VoiceDumpButton`

4. **Qualsiasi altra schermata Voice*** - Pattern identico

### Template cambio schermata:

```kotlin
// PRIMA (con vecchio pattern auto-stop)
VoiceButton(
    isListening = isListening,
    onClick = { viewModel.toggleVoice() }  // Toggle confuso
)

// DOPO (con tap-to-stop)
VoiceDumpButton(
    voiceState = voiceState,
    partialText = partialText,
    onStartListening = { viewModel.startVoiceDump() },
    onStopListening = { viewModel.stopVoiceDump() }
)
```

---

## 5. Test Checklist

### Scenari da testare:

- [ ] **Voice dump corto** (5 secondi) - Start → parla → Stop → risultato OK
- [ ] **Voice dump lungo** (60+ secondi) - Verifica che accumuli tutto
- [ ] **Pause naturali** - Utente pausa 5s, poi continua → non deve tagliare
- [ ] **Silenzio prolungato** - 30s silenzio → nessun crash, continua ad aspettare
- [ ] **Errore rete** - Perde connessione → errore graceful, non blocco
- [ ] **Stop durante silenzio** - Utente smette di parlare, poi tap Stop → usa ultimo testo
- [ ] **Annulla** - Utente cambia idea → cancel funziona, nessun risultato

### Dispositivi target:
- [ ] Tablet Hospice (Samsung?)
- [ ] Telefono backup
- [ ] Emulatore (sanity check)

---

## 6. Fallback & Edge Cases

### Se SpeechRecognizer si blocca:

```kotlin
// Dopo MAX_CONSECUTIVE_ERRORS (3)
// 1. Mostra messaggio "Riconoscimento instabile"
// 2. Offri pulsante "Riprova" che fa:
fun resetVoiceService() {
    voiceService.release()
    delay(500)
    voiceService.initialize()
}
```

### Se utente dimentica di premere Stop:

```kotlin
// Timeout di sicurezza MOLTO lungo (5 minuti)
private const val ABSOLUTE_TIMEOUT_MS = 5 * 60 * 1000L

// Nel restartListeningQuietly()
if (System.currentTimeMillis() - listeningStartTime > ABSOLUTE_TIMEOUT_MS) {
    Log.w(TAG, "Absolute timeout reached")
    stopManualListening()
}
```

---

## 7. Priorità Implementazione

### Fase 1 (MVP - 2-3 ore)
1. Modifica `VoiceService.kt` con modalità MANUAL_STOP
2. Crea `VoiceDumpButton` componente base
3. Testa su UNA schermata (es. VoiceMaintenanceScreen)

### Fase 2 (Rollout - 1-2 ore)
4. Applica a tutte le schermate Voice*
5. Test su device reale

### Fase 3 (Polish - se serve)
6. Animazioni più belle (opzionale)
7. Haptic feedback al tap (opzionale)

---

## Note

- **Non toccare** il comportamento legacy `AUTO_STOP` — serve come fallback
- **Partial results** mostrati live danno feedback che "sta funzionando"
- **Pulsante grande e rosso** durante recording = impossibile sbagliare
- Il testo "Tap quando hai finito" è più chiaro di icone
