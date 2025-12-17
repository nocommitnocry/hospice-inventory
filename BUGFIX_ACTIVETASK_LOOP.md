# Bugfix: Loop Conferma ActiveTask + TTS Markdown

**Data**: 14 Dicembre 2025  
**Priorità**: 🔴 CRITICO  
**Contesto**: Il flusso di registrazione manutenzione va in loop dopo la conferma utente

---

## Sintomi Osservati

1. **Loop post-conferma**: Gemini chiede prodotto → tipo → data → descrizione → conferma. L'utente dice "sì". Gemini ricomincia chiedendo tipo e descrizione come se non li avesse mai ricevuti.

2. **TTS legge markdown**: La risposta vocale dice "asterisco asterisco tipo intervento asterisco asterisco" invece di enfatizzare le parole.

---

## Diagnosi

### Problema 1: Dati Mai Salvati nel Task

Il flusso attuale:

```
Utente: "Riparazione, sostituita la batteria, oggi"
                    ↓
Gemini riceve e CAPISCE i dati
                    ↓
Gemini risponde: "Ok, registro **riparazione** per **oggi**: **sostituita batteria**. Confermi?"
                    ↓
parseResponse() cerca [ACTION:...] → NON TROVA NULLA
                    ↓
updateActiveTask() → MAI CHIAMATO ❌
                    ↓
ActiveTask.MaintenanceRegistration rimane con:
    - type = null
    - description = null  
    - date = null
                    ↓
Utente: "Sì, confermo"
                    ↓
UserIntentDetector.detect("sì") → PROCEED
                    ↓
isActiveTaskComplete() → FALSE (perché type e description sono null!)
                    ↓
"Mi mancano ancora: tipo intervento, descrizione"
                    ↓
LOOP! 🔄
```

**Causa root**: Gemini estrae i dati e li menziona nella risposta testuale, ma non li restituisce in un formato strutturato che il codice possa parsare e salvare nel task.

### Problema 2: TTS Legge Asterischi

Gemini usa markdown (`**bold**`) nelle risposte. `TextToSpeechService.speak()` riceve il testo raw e Android TTS lo legge letteralmente.

---

## Soluzione

### Fix 1: Formato Strutturato per Estrazione Dati

**Opzione A - Tag TASK_UPDATE (raccomandato)**

Modificare il system prompt per istruire Gemini a emettere un tag con i dati estratti:

**File**: `AppModules.kt` → `SYSTEM_INSTRUCTION`

Aggiungere alla fine del system prompt:
```
IMPORTANTE - ESTRAZIONE DATI TASK:
Quando sei in un task multi-step (creazione prodotto, registrazione manutenzione, ecc.) 
e l'utente fornisce informazioni, DEVI includere un tag di aggiornamento nella risposta:

[TASK_UPDATE:campo1=valore1,campo2=valore2]

Esempi:
- Utente dice "riparazione" → [TASK_UPDATE:type=RIPARAZIONE]
- Utente dice "oggi ho sostituito la batteria" → [TASK_UPDATE:type=SOSTITUZIONE,description=Sostituita batteria,date=TODAY]
- Utente dice "l'ha fatto il tecnico di Elettro Impianti" → [TASK_UPDATE:performedBy=Elettro Impianti]

Valori speciali:
- date=TODAY → usa la data odierna
- date=YESTERDAY → usa ieri

Tipi manutenzione validi: PROGRAMMATA, VERIFICA, RIPARAZIONE, SOSTITUZIONE, INSTALLAZIONE, COLLAUDO, DISMISSIONE, STRAORDINARIA

Includi SEMPRE il tag quando estrai nuove informazioni, anche se chiedi conferma nella stessa risposta.
```

**File**: `GeminiService.kt` → `parseResponse()`

Aggiungere parsing del tag TASK_UPDATE:

```kotlin
private fun parseResponse(response: String): Pair<String, AssistantAction?> {
    var cleanResponse = response
    
    // 1. NUOVO: Parsing TASK_UPDATE
    val taskUpdateRegex = """\[TASK_UPDATE:([^\]]+)\]""".toRegex()
    val taskUpdateMatch = taskUpdateRegex.find(response)
    
    if (taskUpdateMatch != null && conversationContext.hasActiveTask()) {
        cleanResponse = cleanResponse.replace(taskUpdateRegex, "").trim()
        val updates = parseTaskUpdateParams(taskUpdateMatch.groupValues[1])
        applyTaskUpdates(updates)
    }
    
    // 2. Parsing ACTION esistente
    val actionRegex = """\[ACTION:([A-Z_]+):?([^\]]*)\]""".toRegex()
    val actionMatch = actionRegex.find(cleanResponse)
    
    if (actionMatch == null) {
        return cleanResponse to null
    }
    
    cleanResponse = cleanResponse.replace(actionRegex, "").trim()
    // ... resto del parsing ACTION esistente ...
}

/**
 * Parsa i parametri del tag TASK_UPDATE.
 */
private fun parseTaskUpdateParams(params: String): Map<String, String> {
    return params.split(",").mapNotNull { param ->
        val parts = param.split("=", limit = 2)
        if (parts.size == 2) {
            parts[0].trim().lowercase() to parts[1].trim()
        } else null
    }.toMap()
}

/**
 * Applica gli aggiornamenti al task attivo.
 */
private fun applyTaskUpdates(updates: Map<String, String>) {
    val task = conversationContext.activeTask ?: return
    
    when (task) {
        is ActiveTask.MaintenanceRegistration -> {
            val newTask = task.copy(
                type = updates["type"]?.let { parseMaintenanceType(it) } ?: task.type,
                description = updates["description"] ?: task.description,
                performedBy = updates["performedby"] ?: task.performedBy,
                cost = updates["cost"]?.toDoubleOrNull() ?: task.cost,
                date = updates["date"]?.let { parseTaskDate(it) } ?: task.date
            )
            conversationContext = conversationContext.copy(activeTask = newTask)
            Log.d(TAG, "Task updated via TASK_UPDATE: $newTask")
        }
        
        is ActiveTask.ProductCreation -> {
            val newTask = task.copy(
                name = updates["name"] ?: task.name,
                category = updates["category"] ?: task.category,
                brand = updates["brand"] ?: task.brand,
                location = updates["location"] ?: task.location,
                barcode = updates["barcode"] ?: task.barcode
            )
            conversationContext = conversationContext.copy(activeTask = newTask)
            Log.d(TAG, "Task updated via TASK_UPDATE: $newTask")
        }
        
        is ActiveTask.MaintainerCreation -> {
            val newTask = task.copy(
                name = updates["name"] ?: task.name,
                company = updates["company"] ?: task.company,
                email = updates["email"] ?: task.email,
                phone = updates["phone"] ?: task.phone
            )
            conversationContext = conversationContext.copy(activeTask = newTask)
            Log.d(TAG, "Task updated via TASK_UPDATE: $newTask")
        }
    }
}

/**
 * Parsa il tipo di manutenzione dal testo.
 */
private fun parseMaintenanceType(text: String): MaintenanceType? {
    val normalized = text.uppercase().trim()
    return MaintenanceType.entries.find { 
        it.name == normalized || 
        it.displayName.equals(text, ignoreCase = true) ||
        it.synonyms.any { syn -> syn.equals(text, ignoreCase = true) }
    }
}

/**
 * Parsa la data dal testo (supporta TODAY, YESTERDAY, o date ISO).
 */
private fun parseTaskDate(text: String): LocalDate? {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    
    return when (text.uppercase()) {
        "TODAY", "OGGI" -> today
        "YESTERDAY", "IERI" -> today.minus(DatePeriod(days = 1))
        else -> try {
            LocalDate.parse(text)
        } catch (e: Exception) {
            // Prova altri formati comuni
            null
        }
    }
}
```

---

### Fix 2: Rimuovere Markdown dal TTS

**File**: `VoiceService.kt` (o dove viene chiamato `ttsService.speak()`)

Creare un helper per pulire il markdown:

```kotlin
/**
 * Rimuove la formattazione markdown dal testo per il TTS.
 */
object TtsTextCleaner {
    
    fun clean(text: String): String {
        return text
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
            
            // Code
            .replace(Regex("""`{1,3}[^`]*`{1,3}"""), "")
            
            // Caratteri residui
            .replace("*", "")
            .replace("_", " ")
            .replace("#", "")
            
            // Cleanup spazi multipli
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
```

**File**: `VoiceAssistant.kt` → `speakResponse()`

Applicare il cleaner prima di passare al TTS:

```kotlin
private fun speakResponse(text: String) {
    if (_isTtsEnabled.value && ttsService.isAvailable.value) {
        val cleanText = TtsTextCleaner.clean(text)  // ← AGGIUNTO
        ttsService.speak(cleanText)
    } else {
        resetToIdleAfterDelay()
    }
}
```

---

## File da Modificare

| File | Modifica |
|------|----------|
| `AppModules.kt` | Aggiungere istruzioni TASK_UPDATE al system prompt |
| `GeminiService.kt` | Aggiungere parsing TASK_UPDATE in `parseResponse()` |
| `VoiceService.kt` | Nuovo object `TtsTextCleaner` |
| `VoiceAssistant.kt` | Applicare `TtsTextCleaner.clean()` in `speakResponse()` |

---

## Test di Validazione

### Test Loop Conferma
1. "Registra manutenzione sull'UPS"
2. (Gemini chiede tipo) "Riparazione"
3. (Gemini chiede descrizione) "Sostituita la batteria"
4. (Gemini chiede conferma) "Sì"
5. **Atteso**: Manutenzione salvata, task completato
6. **Non deve**: Richiedere di nuovo tipo e descrizione

### Test TTS Pulito
1. Attivare una risposta che contenga markdown
2. **Atteso**: TTS pronuncia il testo senza dire "asterisco"
3. **Non deve**: Dire "asterisco asterisco tipo asterisco asterisco"

### Test Date Speciali
1. "La manutenzione l'ho fatta ieri"
2. **Atteso**: `date` nel task = data di ieri
3. "Oggi ho verificato il concentratore"
4. **Atteso**: `date` = data odierna

---

## Note Implementative

### Import Necessari
```kotlin
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
```

### Logging Consigliato
Aggiungere log per debug del parsing:
```kotlin
Log.d(TAG, "TASK_UPDATE parsed: $updates")
Log.d(TAG, "Task after update: ${conversationContext.activeTask}")
```

### Fallback
Se Gemini non include il tag TASK_UPDATE (es. per risposte semplici), il comportamento attuale rimane invariato. Il fix è additivo, non rompe nulla di esistente.
