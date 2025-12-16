# Hospice Inventory - Bug Fix Voice Assistant

**Data**: 14 Dicembre 2025  
**Contesto**: Sessione di debug del sistema vocale con test su dispositivo reale  
**Destinatario**: Claude Code per implementazione fix

---

## Executive Summary

Il sistema vocale presenta **7 problemi distinti** emersi dai test. Alcuni sono risolvibili con modifiche al codice applicativo, altri richiedono workaround per limitazioni di Android. La priorità è ristabilire il flusso di registrazione manutenzione che attualmente va in loop.

---

## Problemi Identificati

### 🔴 P1 - CRITICO: Flusso MaintenanceRegistration Spezzato

**Sintomo**: Dopo aver detto "registra manutenzione sull'UPS APC", Gemini trova il prodotto ma poi chiede di nuovo "su quale prodotto vuoi registrare la manutenzione?"

**Causa root**: Quando è attivo un `ActiveTask.MaintenanceRegistration`, l'azione `SearchProducts` provoca navigazione a `SearchScreen` tramite `NavigationAction.ToSearch()`. Questo:
1. Esce dal contesto conversazionale
2. Il prodotto trovato non viene agganciato a `MaintenanceRegistration.productId`
3. Al ritorno, il task non sa che il prodotto è stato identificato

**File coinvolti**:
- `HomeViewModel.kt` → `handleAssistantAction()`
- `GeminiService.kt` → `parseResponse()`, gestione `SearchProducts`
- `ConversationContext.kt` → `ActiveTask.MaintenanceRegistration`

**Soluzione richiesta**:
```kotlin
// In GeminiService o HomeViewModel, quando c'è un task attivo:
when {
    conversationContext.activeTask is MaintenanceRegistration && action is SearchProducts -> {
        // NON navigare! Cerca internamente
        val results = productRepository.search(action.query)
        when {
            results.isEmpty() -> // Gemini: "Non ho trovato prodotti con quel nome"
            results.size == 1 -> {
                // Aggancia automaticamente
                updateActiveTask(mapOf("productId" to results[0].id, "productName" to results[0].name))
                // Gemini: "Perfetto, registro la manutenzione su {nome}. Che tipo di intervento?"
            }
            results.size > 1 -> // Gemini: "Ho trovato più prodotti: ... Quale intendi?"
        }
    }
    else -> // Comportamento normale: naviga a SearchScreen
}
```

---

### 🔴 P2 - CRITICO: Timeout STT Ignorato dal Device

**Sintomo**: L'utente non riesce a completare frasi lunghe. Il sistema invia il testo a Gemini dopo pause brevissime (~400ms), anche se i parametri sono configurati per 2-3 secondi.

**Evidenza dal logcat**:
```
19:47:46.323  End of speech detected
19:47:46.403  Beginning of speech detected  ← riprende dopo 80ms
19:47:46.546  Result: 'è sbagliato UPS a'   ← ma ha già inviato il partial
```

**Causa**: I parametri `EXTRA_SPEECH_INPUT_*` sono suggerimenti che molti dispositivi Android ignorano.

**File coinvolti**:
- `VoiceService.kt` → `createRecognizerIntent()`, `RecognitionListener`

**Soluzione richiesta** (delay post-silenzio + parola chiave opzionale):
```kotlin
// In VoiceService
private var silenceJob: Job? = null
private val SILENCE_DELAY_MS = 2500L
private val TRIGGER_WORDS = listOf("fatto", "invia", "ok", "procedi")

override fun onEndOfSpeech() {
    Log.d(TAG, "End of speech detected - starting silence timer")
    _state.value = VoiceState.Processing
    
    silenceJob = scope.launch {
        delay(SILENCE_DELAY_MS)
        // Se non è stato cancellato, processa
        finalizeResult()
    }
}

override fun onBeginningOfSpeech() {
    Log.d(TAG, "Beginning of speech detected")
    silenceJob?.cancel() // Utente ha ripreso a parlare
}

override fun onResults(results: Bundle?) {
    val text = results?.getStringArrayList(RESULTS_RECOGNITION)?.firstOrNull() ?: return
    
    // Se contiene parola chiave, processa subito
    if (TRIGGER_WORDS.any { text.lowercase().endsWith(it) }) {
        silenceJob?.cancel()
        val cleanText = text.removeSuffix(/* trigger word */)
        processResult(cleanText)
    } else {
        // Accumula e aspetta il silence timer
        accumulatedText += " $text"
    }
}
```

**Nota UX**: Gli operatori interni impareranno che "fatto" velocizza. I manutentori esterni aspetteranno 2.5 secondi - funziona comunque. No touch richiesto (importante per guanti/igiene).

---

### 🟡 P3 - ALTO: Contesto Incompleto nel Prompt Gemini

**Sintomo**: 
- Gemini non inferisce il manutentore quando l'utente dice "sono il tecnico di Elettro Impianti"
- Gemini allucina la data come "giugno 2024" invece di dicembre 2025

**Causa**: `buildContextPrompt()` non include:
1. Data corrente
2. Lista manutentori con i loro prodotti/aziende

**File coinvolti**:
- `GeminiService.kt` → costruttore (aggiungere `MaintainerRepository`) + `buildContextPrompt()`
- `ConversationContext.kt` → aggiungere `toCollectedDataString()` se mancante

**Soluzione richiesta**:

**1. Aggiungere dipendenza nel costruttore di GeminiService:**
```kotlin
@Singleton
class GeminiService @Inject constructor(
    private val generativeModel: GenerativeModel,
    private val productRepository: ProductRepository,
    private val maintainerRepository: MaintainerRepository  // NUOVA DIPENDENZA
) {
```

**2. Aggiungere import:**
```kotlin
import kotlinx.coroutines.flow.first
import org.incammino.hospiceinventory.data.repository.MaintainerRepository
```

**3. Sostituire `buildContextPrompt()`:**
```kotlin
/**
 * Costruisce il prompt di contesto basato sullo stato attuale.
 */
private suspend fun buildContextPrompt(): String {
    val parts = mutableListOf<String>()

    // 1. DATA CORRENTE (Cruciale per evitare allucinazioni temporali)
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    parts.add("DATA ODIERNA: ${today.date} (${today.dayOfWeek})")

    // 2. PRODOTTO ATTUALMENTE VISUALIZZATO
    conversationContext.currentProduct?.let { product ->
        parts.add("""
            |PRODOTTO ATTUALMENTE VISUALIZZATO:
            |Nome: ${product.name}
            |ID: ${product.id}
            |Categoria: ${product.category}
            |Ubicazione: ${product.location}
            |Stato garanzia: ${product.getWarrantyStatusText()}
            |Manutentore garanzia: ${product.warrantyMaintainerId ?: "N/A"}
            |Manutentore service: ${product.serviceMaintainerId ?: "N/A"}
        """.trimMargin())
    }

    // 3. MANUTENTORI REGISTRATI (Per inferenza speaker e assegnazione)
    try {
        val maintainers = maintainerRepository.getAllActive().first()
        if (maintainers.isNotEmpty()) {
            val maintainerList = maintainers.joinToString("\n") { m ->
                "- ${m.name} (${m.company ?: "indipendente"}): ${m.email ?: ""} ${m.phone ?: ""}"
            }
            parts.add("""
                |MANUTENTORI REGISTRATI NEL SISTEMA:
                |$maintainerList
                |
                |ISTRUZIONE: Se l'utente si presenta come tecnico o dipendente di una di queste aziende
                |(es. "Sono Mario di TechMed", "Sono il tecnico di Elettro Impianti"),
                |consideralo come l'esecutore dell'intervento (LIKELY_MAINTAINER).
                |Non chiedere "chi ha eseguito l'intervento" se l'utente si è già identificato.
            """.trimMargin())
        }
    } catch (e: Exception) {
        Log.w(TAG, "Impossibile recuperare lista manutentori per prompt", e)
    }

    // 4. ALERT SCADENZE
    try {
        val overdueCount = productRepository.countOverdueMaintenance()
        if (overdueCount > 0) {
            parts.add("⚠️ ATTENZIONE: $overdueCount manutenzioni scadute.")
        }
    } catch (_: Exception) { }

    // 5. TASK ATTIVO (Se presente)
    conversationContext.activeTask?.let { task ->
        parts.add(buildActiveTaskContext(task))
    }

    return if (parts.isEmpty()) {
        "Nessun contesto specifico."
    } else {
        parts.joinToString("\n\n")
    }
}

/**
 * Helper per formattare il contesto del task attivo.
 */
private fun buildActiveTaskContext(task: ActiveTask): String = when (task) {
    is ActiveTask.MaintenanceRegistration -> """
        |TASK IN CORSO: Registrazione Manutenzione
        |Prodotto target: ${task.productName} (ID: ${task.productId})
        |Dati già raccolti:
        |${task.toCollectedDataString()}
        |
        |Campi mancanti obbligatori: ${task.requiredMissing.joinToString(", ")}
    """.trimMargin()
    is ActiveTask.ProductCreation -> """
        |TASK IN CORSO: Creazione Nuovo Prodotto
        |Dati già raccolti:
        |${task.toCollectedDataString()}
        |
        |Campi mancanti obbligatori: ${task.requiredMissing.joinToString(", ")}
    """.trimMargin()
    is ActiveTask.MaintainerCreation -> """
        |TASK IN CORSO: Registrazione Manutentore
        |Dati già raccolti:
        |${task.toCollectedDataString()}
        |
        |Campi mancanti: ${task.requiredMissing.joinToString(", ")}
    """.trimMargin()
}
```

**4. Verificare/aggiungere `toCollectedDataString()` in `ConversationContext.kt`:**

Se non esiste, aggiungere nelle classi `ActiveTask`:
```kotlin
sealed class ActiveTask {
    abstract val startedAt: Instant
    abstract val requiredMissing: List<String>
    abstract fun toCollectedDataString(): String
}

data class MaintenanceRegistration(...) : ActiveTask() {
    override fun toCollectedDataString(): String = buildString {
        type?.let { appendLine("- Tipo: ${it.displayName}") }
        description?.let { appendLine("- Descrizione: $it") }
        performedBy?.let { appendLine("- Eseguito da: $it") }
        cost?.let { appendLine("- Costo: €$it") }
        date?.let { appendLine("- Data: $it") }
    }.ifEmpty { "(nessun dato raccolto)" }
}

data class ProductCreation(...) : ActiveTask() {
    override fun toCollectedDataString(): String = buildString {
        name?.let { appendLine("- Nome: $it") }
        category?.let { appendLine("- Categoria: $it") }
        brand?.let { appendLine("- Marca: $it") }
        location?.let { appendLine("- Ubicazione: $it") }
        barcode?.let { appendLine("- Barcode: $it") }
    }.ifEmpty { "(nessun dato raccolto)" }
}

data class MaintainerCreation(...) : ActiveTask() {
    override fun toCollectedDataString(): String = buildString {
        name?.let { appendLine("- Nome: $it") }
        company?.let { appendLine("- Azienda: $it") }
        email?.let { appendLine("- Email: $it") }
        phone?.let { appendLine("- Telefono: $it") }
    }.ifEmpty { "(nessun dato raccolto)" }
}
```

---

### 🟡 P4 - ALTO: STT Distorce Sigle (APC → ABC)

**Sintomo**: "UPS APC" viene riconosciuto come "UPS ABC" con confidenza 0.90+

**Causa**: Google Speech Recognition ottimizza per parole comuni. "ABC" è molto più frequente di "APC" nel corpus italiano.

**Workaround possibili** (in ordine di complessità):

**A) Post-processing con vocabolario noto** (raccomandato):
```kotlin
object SttPostProcessor {
    // Sigle note nel dominio
    private val KNOWN_TERMS = mapOf(
        "ABC" to "APC",      // APC è produttore UPS
        "UBS" to "UPS",      // UPS spesso mal riconosciuto
        "ossigeno" to "O2",  // normalizzazione
        // ... altre mappature dal contesto hospice
    )
    
    // Nomi prodotti dal DB (caricati all'avvio)
    private var productNames: Set<String> = emptySet()
    
    fun process(input: String): String {
        var result = input
        
        // 1. Sostituzioni note
        KNOWN_TERMS.forEach { (wrong, correct) ->
            result = result.replace(wrong, correct, ignoreCase = true)
        }
        
        // 2. Fuzzy match con nomi prodotti esistenti
        // Se "ABC" è vicino a un prodotto "APC Smart 3000", suggerisci correzione
        
        return result
    }
}
```

**B) Istruire Gemini a fare fuzzy matching**:
Nel system prompt, aggiungere:
```
Quando cerchi prodotti, considera che il riconoscimento vocale può distorcere sigle.
"ABC" potrebbe essere "APC", "UBS" potrebbe essere "UPS".
Usa fuzzy matching e suggerisci correzioni se trovi corrispondenze parziali.
```

---

### 🟡 P5 - ALTO: Spelling Fonetico Non Interpretato

**Sintomo**: "A come Ancona, P come Padova, C come Como" → trascritto letteralmente invece di "APC"

**Causa**: Android STT non ha logica per interpretare spelling fonetico italiano.

**File coinvolti**:
- Nuovo file: `SttPostProcessor.kt` o dentro `VoiceService.kt`

**Soluzione richiesta**:
```kotlin
object SpellingNormalizer {
    // Alfabeto fonetico italiano
    private val PHONETIC_ALPHABET = mapOf(
        "ancona" to "A", "bari" to "B", "como" to "C", "domodossola" to "D",
        "empoli" to "E", "firenze" to "F", "genova" to "G", "hotel" to "H",
        "imola" to "I", "jolly" to "J", "kappa" to "K", "livorno" to "L",
        "milano" to "M", "napoli" to "N", "otranto" to "O", "padova" to "P",
        "quarto" to "Q", "roma" to "R", "savona" to "S", "torino" to "T",
        "udine" to "U", "venezia" to "V", "washington" to "W", "xilofono" to "X",
        "york" to "Y", "zara" to "Z"
    )
    
    // Pattern: "X come Città" 
    private val SPELLING_PATTERN = Regex(
        """([a-zA-Z])\s*come\s+(\w+)""",
        RegexOption.IGNORE_CASE
    )
    
    fun normalize(input: String): String {
        // Se non contiene pattern di spelling, ritorna input originale
        if (!input.contains("come", ignoreCase = true)) return input
        
        val matches = SPELLING_PATTERN.findAll(input)
        if (matches.count() == 0) return input
        
        // Estrai le lettere dallo spelling
        val spelledLetters = StringBuilder()
        var lastEnd = 0
        val result = StringBuilder()
        
        for (match in SPELLING_PATTERN.findAll(input)) {
            // Aggiungi testo prima del match
            result.append(input.substring(lastEnd, match.range.first))
            
            // Estrai la lettera (dalla città o dalla lettera stessa)
            val letter = match.groupValues[1].uppercase()
            val city = match.groupValues[2].lowercase()
            
            val resolvedLetter = PHONETIC_ALPHABET[city] ?: letter
            spelledLetters.append(resolvedLetter)
            
            lastEnd = match.range.last + 1
        }
        
        // Se abbiamo trovato spelling, sostituisci con la sigla
        return if (spelledLetters.isNotEmpty()) {
            // Rimuovi tutto il pattern di spelling e metti la sigla
            input.replace(SPELLING_PATTERN, "").trim() + " " + spelledLetters.toString()
        } else {
            input
        }
    }
}

// Esempio:
// "UPS A come Ancona P come Padova C come Como Smart 3000"
// → "UPS APC Smart 3000"
```

---

### 🟢 P6 - MEDIO: Android TTS di Bassa Qualità

**Sintomo**: La voce robotica dà impressione di sistema lento e datato, anche quando le risposte sono veloci.

**Soluzione proposta**: Sostituire `TextToSpeechService` con `GeminiTtsService` usando `gemini-2.5-flash-preview-tts`.

**File coinvolti**:
- Nuovo file: `GeminiTtsService.kt`
- `VoiceAssistant.kt` → sostituire dipendenza TTS
- `build.gradle` → eventuale dipendenza per audio streaming

**Specifiche**:
```kotlin
@Singleton
class GeminiTtsService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val model = GenerativeModel(
        modelName = "gemini-2.5-flash-preview-tts",
        apiKey = BuildConfig.GEMINI_API_KEY
    )
    
    // Configurazione voce
    private val voiceConfig = mapOf(
        "voice" to "it-IT-Neural2-A",  // o altra voce italiana naturale
        "speakingRate" to 1.1f,        // leggermente più veloce
        "pitch" to 0.0f
    )
    
    suspend fun speak(text: String): Flow<ByteArray> {
        // Streaming audio per bassa latenza
        return model.generateAudioStream(text, voiceConfig)
    }
    
    // Fallback ad Android TTS se offline o errore
    private val fallbackTts: TextToSpeechService by lazy { ... }
}
```

**Costi stimati**: ~$6/mese per uso tipico (100 interazioni/giorno, 200 char/risposta).

**Priorità**: Può essere implementato dopo i fix critici P1-P3.

---

### 🟢 P7 - BASSO: Reset Contesto Durante Navigazione

**Sintomo potenziale**: Se l'utente naviga manualmente durante un task vocale, il contesto potrebbe perdersi.

**File coinvolti**:
- `HomeViewModel.kt` → `onCleared()` chiama `voiceAssistant.release()` che chiama `geminiService.resetContext()`

**Verifica necessaria**: Controllare quando viene chiamato `resetContext()` e se è appropriato. Il contesto dovrebbe persistere durante la navigazione tra schermate, ma resettarsi solo su:
- Logout (non implementato)
- Timeout inattività (es. 10 minuti)
- Reset esplicito dall'utente

---

## Priorità Implementazione

| # | Problema | Impatto | Effort | Priorità |
|---|----------|---------|--------|----------|
| P1 | Flusso MaintenanceRegistration | Bloccante | Medio | 🔴 1 |
| P2 | Timeout STT | UX critica | Medio | 🔴 2 |
| P3 | Contesto prompt incompleto | Funzionalità mancante | Basso | 🟡 3 |
| P4 | Sigle distorte | UX degradata | Basso | 🟡 4 |
| P5 | Spelling fonetico | Nice-to-have | Basso | 🟡 5 |
| P6 | TTS qualità | UX polish | Alto | 🟢 6 |
| P7 | Reset contesto | Preventivo | Basso | 🟢 7 |

---

## Modifica Configurazione Gemini

In `AppModules.kt`, abbassare la temperature da 0.7 a **0.4**:

```kotlin
generationConfig = generationConfig {
    temperature = 0.4f  // Era 0.7f - ridotto per maggiore precisione
    topK = 40
    topP = 0.95f
    maxOutputTokens = 2048
}
```

**Motivazione**: 0.7 è troppo alto per estrazione dati strutturati (rischio invenzioni). 0.4 mantiene risposte naturali e "umane" senza diventare un gendarme, adatto al contesto hospice.

---

## File Modificati (Checklist)

```
app/src/main/java/org/incammino/hospiceinventory/
├── di/
│   └── AppModules.kt            ← Temperature 0.7→0.4
├── service/voice/
│   ├── VoiceService.kt          ← P2: silence delay, trigger words
│   ├── GeminiService.kt         ← P1: ricerca interna, P3: contesto + MaintainerRepository
│   ├── ConversationContext.kt   ← P1: metodi helper, P3: toCollectedDataString()
│   ├── GeminiTtsService.kt      ← P6: NUOVO FILE
│   └── SttPostProcessor.kt      ← P4, P5: NUOVO FILE
├── ui/screens/home/
│   └── HomeViewModel.kt         ← P1: handleAssistantAction condizionale
```

---

## Test di Validazione

Dopo l'implementazione, verificare:

1. **P1**: "Registra manutenzione sull'UPS APC" → deve trovare e agganciare senza navigare
2. **P2**: Frase lunga con pausa → deve aspettare 2.5s prima di processare
3. **P2**: "Cerca UPS fatto" → deve processare immediatamente
4. **P3**: "Sono il tecnico di Elettro Impianti" → deve associare il manutentore
5. **P3**: Chiedere la data → deve rispondere con data corretta
6. **P4**: "Cerca UPS ABC" → deve suggerire "Intendi APC?"
7. **P5**: "A come Ancona P come Padova C come Como" → deve interpretare come "APC"

---

## Note Architetturali

### Dependency Injection
I nuovi servizi (`GeminiTtsService`, `SttPostProcessor`) devono essere annotati con `@Singleton` e `@Inject` per Hilt.

### Repository Sync Methods
Per `buildContextPrompt()` serve un metodo sincrono `getAllActiveSync()` in `MaintainerRepository`. Alternativa: pre-caricare i manutentori all'init di `GeminiService` e aggiornarli su cambio.

### Testabilità
Considerare l'estrazione di `SttPostProcessor` e `SpellingNormalizer` come oggetti statici (già fatto nel design sopra) per facilitare unit testing.
