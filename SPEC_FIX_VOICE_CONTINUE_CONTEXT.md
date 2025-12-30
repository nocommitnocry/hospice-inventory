# SPEC: Fix VoiceContinueButton - Preservare Dati Manuali

**Data**: 29 Dicembre 2025  
**Versione**: 1.0  
**Stato**: Da Implementare  
**Priorità**: CRITICA - Bug UX

---

## Executive Summary

### Problema

Quando l'utente usa il `VoiceContinueButton` nella schermata di conferma, i dati inseriti **manualmente** nei campi vengono ignorati. Gemini riceve una stringa vuota come contesto e non può preservare le modifiche manuali.

### Scenario "Pasticcione"

1. Utente registra nuovo prodotto a voce → estrazione parziale (nome, modello, telefono)
2. Scheda conferma si apre precompilata
3. Utente inserisce **manualmente** l'email nel campo
4. Utente tocca "Aggiungi dettagli a voce"
5. Utente dice: "aggiungi la data di acquisto primo settembre 2023"
6. **BUG**: Email inserita manualmente viene persa!

### Causa Root

```kotlin
// ProductConfirmViewModel.kt - PROBLEMA
private fun processAdditionalVoiceInput(transcript: String) {
    val updates = geminiService.updateProductFromVoice(
        currentData = "", // ⚠️ STRINGA VUOTA - non vede i campi!
        newInput = transcript
    )
}
```

Lo stato dei campi è in `rememberSaveable` nella Screen (Compose), ma il ViewModel non li riceve durante l'elaborazione vocale.

---

## Stato Attuale dei ViewModel

| ViewModel | `processVoiceWithContext()` | Collegato a VoiceContinueButton |
|-----------|----------------------------|--------------------------------|
| ProductConfirmViewModel | ✅ Esiste | ❌ NO - usa `processAdditionalVoiceInput()` |
| MaintenanceConfirmViewModel | ❌ Non esiste | ❌ NO |
| MaintainerConfirmViewModel | ❌ Non esiste | ❌ NO |
| LocationConfirmViewModel | ❌ Non esiste | ❌ NO |

---

## Soluzione Proposta

### Architettura

La Screen deve passare i dati attuali al ViewModel quando l'utente ferma l'ascolto vocale.

```
┌─────────────────────────────────────────────────────────────┐
│  XxxConfirmScreen (Compose)                                 │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ rememberSaveable: name, email, phone, notes...      │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                  │
│                          ▼ onStopListening(currentFormData) │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ XxxConfirmViewModel                                 │   │
│  │   processVoiceWithContext(transcript, formData)     │   │
│  │      → GeminiService.updateXxxFromVoice()           │   │
│  │      → onVoiceUpdate callback                       │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                  │
│                          ▼ updates: Map<String, String>     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Screen applica updates ai campi rememberSaveable    │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---
## Prerequisito

Questa specifica assume che VoiceService usi la modalità MANUAL_STOP
(vedi TAP_TO_STOP_SPEC.md). VoiceContinueButton deve usare:
- viewModel.startVoiceDump() invece di toggle
- viewModel.stopVoiceDump() invece di auto-timeout

## Implementazione

### Fase 1: Data Classes per Form State

Creare data class per ogni tipo di form in `service/voice/ExtractionModels.kt`:

```kotlin
/**
 * Stato attuale del form prodotto per context vocale.
 */
data class ProductFormData(
    val name: String = "",
    val model: String = "",
    val manufacturer: String = "",
    val serialNumber: String = "",
    val barcode: String = "",
    val category: String = "",
    val location: String = "",
    val supplier: String = "",
    val warrantyMonths: Int? = null,
    val maintenanceFrequencyMonths: Int? = null,
    val notes: String = ""
)

/**
 * Stato attuale del form manutenzione per context vocale.
 */
data class MaintenanceFormData(
    val productName: String = "",
    val maintainerName: String = "",
    val type: String = "",
    val description: String = "",
    val durationMinutes: Int? = null,
    val isWarranty: Boolean = false,
    val date: String = "",
    val notes: String = ""
)

/**
 * Stato attuale del form manutentore per context vocale.
 */
data class MaintainerFormData(
    val name: String = "",
    val vatNumber: String = "",
    val specialization: String = "",
    val email: String = "",
    val phone: String = "",
    val contactPerson: String = "",
    val street: String = "",
    val city: String = "",
    val postalCode: String = "",
    val province: String = "",
    val isSupplier: Boolean = false,
    val notes: String = ""
)

/**
 * Stato attuale del form ubicazione per context vocale.
 */
data class LocationFormData(
    val name: String = "",
    val type: String = "",
    val buildingName: String = "",
    val floorCode: String = "",
    val floorName: String = "",
    val department: String = "",
    val hasOxygenOutlet: Boolean = false,
    val bedCount: Int? = null,
    val notes: String = ""
)
```

### Fase 2: Modificare ViewModel - Pattern Comune

Per ogni ConfirmViewModel, implementare:

```kotlin
// 1. Callback per ricevere transcript + context
var onProcessVoiceWithContext: ((String) -> Unit)? = null

// 2. Metodo pubblico che la Screen chiamerà
fun processVoiceWithContext(transcript: String, currentData: XxxFormData) {
    viewModelScope.launch {
        _voiceContinueState.value = VoiceContinueState.Processing
        
        try {
            val context = buildContextString(currentData)
            
            val updates = geminiService.updateXxxFromVoice(
                currentData = context,
                newInput = transcript
            )
            
            if (updates.isNotEmpty()) {
                Log.d(TAG, "Voice updates with context: $updates")
                onVoiceUpdate?.invoke(updates)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing voice with context", e)
        } finally {
            _voiceContinueState.value = VoiceContinueState.Idle
        }
    }
}

// 3. Helper per costruire stringa contesto
private fun buildContextString(data: XxxFormData): String {
    return """
        Nome: ${data.name}
        Email: ${data.email}
        Telefono: ${data.phone}
        ... altri campi ...
    """.trimIndent()
}

// 4. MODIFICARE processAdditionalVoiceInput per salvare transcript
private var pendingTranscript: String? = null

private fun processAdditionalVoiceInput(transcript: String) {
    // Salva il transcript, la Screen chiamerà processVoiceWithContext
    pendingTranscript = transcript
    onProcessVoiceWithContext?.invoke(transcript)
}
```

### Fase 3: Modificare Screen - Collegare Callback

Per ogni ConfirmScreen:

```kotlin
@Composable
fun XxxConfirmScreen(
    initialData: XxxConfirmData,
    viewModel: XxxConfirmViewModel = hiltViewModel(),
    // ...
) {
    // Stati locali
    var name by rememberSaveable { mutableStateOf(initialData.name) }
    var email by rememberSaveable { mutableStateOf(initialData.email) }
    // ... altri campi ...
    
    // Collegare callback per voice context
    LaunchedEffect(Unit) {
        viewModel.onProcessVoiceWithContext = { transcript ->
            // Costruisci FormData con valori ATTUALI
            val currentFormData = XxxFormData(
                name = name,
                email = email,
                // ... tutti i campi attuali ...
            )
            viewModel.processVoiceWithContext(transcript, currentFormData)
        }
        
        viewModel.onVoiceUpdate = { updates ->
            // Applica aggiornamenti
            updates["name"]?.let { name = it }
            updates["email"]?.let { email = it }
            // ... altri campi ...
        }
    }
    
    // ... rest of UI ...
}
```

---

## Checklist Implementazione per Claude Code

### ⚠️ MEMO IMPORTANTE

Prima di implementare, verificare che TUTTE e 4 le schermate abbiano:
1. `processVoiceWithContext()` nel ViewModel
2. La relativa `XxxFormData` data class
3. Il collegamento `onProcessVoiceWithContext` nella Screen
4. L'applicazione degli updates ai campi `rememberSaveable`

### File da Modificare

#### 1. ExtractionModels.kt
- [ ] Aggiungere `ProductFormData`
- [ ] Aggiungere `MaintenanceFormData`
- [ ] Aggiungere `MaintainerFormData`
- [ ] Aggiungere `LocationFormData`

#### 2. ProductConfirmViewModel.kt
- [ ] Aggiungere `onProcessVoiceWithContext` callback
- [ ] Verificare che `processVoiceWithContext()` usi il context
- [ ] Modificare `processAdditionalVoiceInput()` per invocare callback

#### 3. ProductConfirmScreen.kt
- [ ] Collegare `onProcessVoiceWithContext` in `LaunchedEffect`
- [ ] Passare tutti i campi attuali a `ProductFormData`

#### 4. MaintenanceConfirmViewModel.kt
- [ ] Aggiungere `onProcessVoiceWithContext` callback
- [ ] Implementare `processVoiceWithContext(transcript, MaintenanceFormData)`
- [ ] Implementare `buildContextString(MaintenanceFormData)`
- [ ] Modificare `processAdditionalVoiceInput()` per invocare callback

#### 5. MaintenanceConfirmScreen.kt
- [ ] Collegare `onProcessVoiceWithContext` in `LaunchedEffect`
- [ ] Passare tutti i campi attuali a `MaintenanceFormData`

#### 6. MaintainerConfirmViewModel.kt
- [ ] Aggiungere `onProcessVoiceWithContext` callback
- [ ] Implementare `processVoiceWithContext(transcript, MaintainerFormData)`
- [ ] Implementare `buildContextString(MaintainerFormData)`
- [ ] Modificare `processAdditionalVoiceInput()` per invocare callback

#### 7. MaintainerConfirmScreen.kt
- [ ] Collegare `onProcessVoiceWithContext` in `LaunchedEffect`
- [ ] Passare tutti i campi attuali a `MaintainerFormData`

#### 8. LocationConfirmViewModel.kt
- [ ] Aggiungere `onProcessVoiceWithContext` callback
- [ ] Implementare `processVoiceWithContext(transcript, LocationFormData)`
- [ ] Implementare `buildContextString(LocationFormData)`
- [ ] Modificare `processAdditionalVoiceInput()` per invocare callback

#### 9. LocationConfirmScreen.kt
- [ ] Collegare `onProcessVoiceWithContext` in `LaunchedEffect`
- [ ] Passare tutti i campi attuali a `LocationFormData`

---

## Test Cases

### Test 1: Prodotto - Email manuale preservata
1. Avvia "Nuovo Prodotto"
2. Parla: "Frigorifero Electrolux modello ABC123"
3. Nella scheda conferma, inserisci manualmente: email = "test@example.com"
4. Tocca "Aggiungi dettagli a voce"
5. Parla: "garanzia 24 mesi"
6. **Verifica**: Email "test@example.com" è ancora presente

### Test 2: Manutenzione - Note manuali preservate
1. Avvia "Registra Manutenzione"
2. Parla: "Mario Rossi di TechMed ha riparato il frigorifero"
3. Inserisci manualmente nelle note: "Controllare tra 6 mesi"
4. Tocca "Aggiungi dettagli a voce"
5. Parla: "durata due ore"
6. **Verifica**: Note contengono ancora "Controllare tra 6 mesi"

### Test 3: Manutentore - Telefono manuale preservato
1. Avvia "Nuovo Manutentore"
2. Parla: "Elettrica Bianchi di Milano"
3. Inserisci manualmente: telefono = "02 12345678"
4. Tocca "Aggiungi dettagli a voce"
5. Parla: "email info@elettricabianchi.it"
6. **Verifica**: Telefono "02 12345678" è ancora presente

### Test 4: Ubicazione - Note manuali preservate
1. Avvia "Nuova Ubicazione"
2. Parla: "Camera 15 primo piano"
3. Inserisci manualmente nelle note: "Vicino all'ascensore"
4. Tocca "Aggiungi dettagli a voce"
5. Parla: "ha presa ossigeno"
6. **Verifica**: Note contengono ancora "Vicino all'ascensore"

---

## Aggiornare CLAUDE.md

Dopo l'implementazione, aggiungere in CLAUDE.md:

```markdown
### Sessione XX/XX/2025 - Fix VoiceContinueButton Context

**P18 - VoiceContinueButton ignorava dati manuali** (CRITICO - RISOLTO)
- Problema: Quando l'utente usava VoiceContinueButton dopo aver modificato campi manualmente,
  i dati manuali venivano persi perché Gemini riceveva `currentData = ""`
- Causa: `processAdditionalVoiceInput()` non aveva accesso allo stato Compose (rememberSaveable)
- Soluzione: Pattern callback dove la Screen passa i dati attuali al ViewModel
- File modificati:
  - `ExtractionModels.kt` - +ProductFormData, MaintenanceFormData, MaintainerFormData, LocationFormData
  - `ProductConfirmViewModel.kt` - +onProcessVoiceWithContext, fix processVoiceWithContext
  - `ProductConfirmScreen.kt` - collegamento callback con dati attuali
  - `MaintenanceConfirmViewModel.kt` - +processVoiceWithContext con context
  - `MaintenanceConfirmScreen.kt` - collegamento callback
  - `MaintainerConfirmViewModel.kt` - +processVoiceWithContext con context
  - `MaintainerConfirmScreen.kt` - collegamento callback
  - `LocationConfirmViewModel.kt` - +processVoiceWithContext con context
  - `LocationConfirmScreen.kt` - collegamento callback
```

---

## Note Tecniche

### Perché non elevare lo stato al ViewModel?

Sarebbe più pulito architetturalmente, ma richiederebbe:
- Refactoring significativo di tutte le ConfirmScreen
- Gestione dello stato in StateFlow invece di rememberSaveable
- Possibili problemi con la sopravvivenza ai configuration changes

Il pattern callback è meno invasivo e risolve il problema specifico.

### Alternative Considerate

1. **Stato nel ViewModel** - Troppo invasivo
2. **SharedFlow bidirezionale** - Overengineering
3. **Callback semplice** ✅ - Scelto: minimale, funziona

---

## Priorità

Questo fix è **CRITICO** perché:
1. Scenario "pasticcione" è molto realistico
2. Perdita dati causa frustrazione utente
3. Contraddice la filosofia "Voice Dump + Visual Confirm"
4. L'utente potrebbe non accorgersi della perdita fino al salvataggio
