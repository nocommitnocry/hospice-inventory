# BRIEF FASE 4: Polish e Raffinamenti

**Data**: 26 Dicembre 2025  
**Durata stimata**: 1 settimana  
**Priorità**: MEDIA  
**Prerequisiti**: Fase 1, 2, 3 completate e testate

---

## Obiettivo

Raffinare l'esperienza utente, gestire edge case, aggiungere feedback visivi e preparare per rilascio.

**Focus:**
1. Gestione errori robusta
2. Feedback visivi e animazioni
3. Componenti UI riutilizzabili
4. Pulizia codice e deprecazione vecchio flusso
5. Test manuali sistematici

---

## Deliverable

### File da CREARE

```
app/src/main/java/org/incammino/hospiceinventory/ui/components/voice/
├── MicrophoneButton.kt           # Componente riutilizzabile
├── TranscriptBox.kt              # Componente riutilizzabile
├── EntitySelectionCard.kt        # Card generica per selezione entità
├── ConfirmationDialog.kt         # Dialog conferma azioni
└── ErrorSnackbar.kt              # Snackbar errori con retry
```

### File da MODIFICARE

```
├── Tutti i *ConfirmScreen        # Aggiungere gestione errori
├── Tutti i *ViewModel            # Stati errore consistenti
├── service/voice/VoiceService.kt # Timeout e retry
├── ui/theme/Theme.kt             # Colori stati (success, warning)
└── CLAUDE.md                     # Documentare nuovo paradigma
```

### File da RIMUOVERE/DEPRECARE

```
├── service/voice/ConversationContext.kt  # Vecchio ActiveTask (valutare)
├── Codice multi-step in GeminiService    # Deprecare
└── HomeScreen vecchi callback            # Rimuovere se non usati
```

---

## 1. Componenti UI Riutilizzabili

### 1.1 MicrophoneButton.kt

```kotlin
package org.incammino.hospiceinventory.ui.components.voice

/**
 * Pulsante microfono animato, riutilizzabile in tutti i flussi voice.
 *
 * Stati visuali:
 * - Idle: colore primaryContainer, scala 1.0
 * - Listening: colore primary, pulse animation
 * - Processing: colore tertiary, spinner
 * - Error: colore error, shake animation
 * - Success: colore success, checkmark temporaneo
 */
@Composable
fun MicrophoneButton(
    state: MicButtonState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    // Animazioni
    val scale by animateFloatAsState(...)
    val containerColor by animateColorAsState(...)
    
    // Shake animation per errore
    val shakeOffset by animateFloatAsState(...)
    
    FilledIconButton(
        onClick = onTap,
        modifier = modifier
            .size(size)
            .scale(scale)
            .offset(x = shakeOffset.dp),
        enabled = state != MicButtonState.Processing
    ) {
        when (state) {
            MicButtonState.Processing -> CircularProgressIndicator(...)
            MicButtonState.Success -> Icon(Icons.Default.Check, ...)
            else -> Icon(Icons.Default.Mic, ...)
        }
    }
}

enum class MicButtonState {
    Idle,
    Listening,
    Processing,
    Success,
    Error
}
```

### 1.2 TranscriptBox.kt

```kotlin
/**
 * Box per mostrare il testo riconosciuto in tempo reale.
 * Include indicatore "typing" quando in ascolto.
 */
@Composable
fun TranscriptBox(
    text: String,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = text.isNotEmpty() || isListening,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = text.ifEmpty { "..." },
                    style = MaterialTheme.typography.bodyMedium
                )
                
                if (isListening) {
                    Spacer(Modifier.width(8.dp))
                    TypingIndicator()
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    // Tre pallini che pulsano in sequenza
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            val delay = index * 150
            val alpha by rememberInfiniteTransition().animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = delay),
                    repeatMode = RepeatMode.Reverse
                )
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(alpha)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        CircleShape
                    )
            )
        }
    }
}
```

### 1.3 EntitySelectionCard.kt

```kotlin
/**
 * Card generica per selezione/creazione entità.
 * Usata per Prodotto, Manutentore, Ubicazione.
 *
 * @param T tipo entità (Product, Maintainer, Location)
 */
@Composable
fun <T> EntitySelectionCard(
    title: String,
    match: EntityMatch<T>,
    displayName: (T) -> String,
    onSearch: () -> Unit,
    onCreate: ((String) -> Unit)?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge
            )
            
            Spacer(Modifier.height(8.dp))
            
            when (match) {
                is EntityMatch.Found -> {
                    FoundEntity(
                        name = displayName(match.entity),
                        onSearch = onSearch
                    )
                }
                is EntityMatch.Ambiguous -> {
                    AmbiguousEntity(
                        candidates = match.candidates,
                        displayName = displayName,
                        onSelect = onSelect,
                        onSearch = onSearch
                    )
                }
                is EntityMatch.NotFound -> {
                    NotFoundEntity(
                        searchTerms = match.searchTerms,
                        onSearch = onSearch,
                        onCreate = onCreate
                    )
                }
            }
        }
    }
}

sealed class EntityMatch<T> {
    data class Found<T>(val entity: T) : EntityMatch<T>()
    data class Ambiguous<T>(
        val candidates: List<T>, 
        val searchTerms: String
    ) : EntityMatch<T>()
    data class NotFound<T>(val searchTerms: String) : EntityMatch<T>()
}
```

---

## 2. Gestione Errori

### 2.1 Errori STT (Speech-to-Text)

```kotlin
// In VoiceService.kt - migliorare gestione errori

sealed class VoiceError(val message: String, val isRetryable: Boolean) {
    object NetworkError : VoiceError(
        "Errore di rete. Verifica la connessione.",
        isRetryable = true
    )
    object NoMatch : VoiceError(
        "Non ho capito. Prova a parlare più chiaramente.",
        isRetryable = true
    )
    object Timeout : VoiceError(
        "Tempo scaduto. Tocca il microfono per riprovare.",
        isRetryable = true
    )
    object Busy : VoiceError(
        "Riconoscimento occupato. Attendi un momento.",
        isRetryable = true
    )
    object Unavailable : VoiceError(
        "Riconoscimento vocale non disponibile su questo dispositivo.",
        isRetryable = false
    )
    data class Unknown(val code: Int) : VoiceError(
        "Errore sconosciuto ($code)",
        isRetryable = true
    )
}
```

### 2.2 Errori Gemini

```kotlin
// In GeminiService.kt

sealed class GeminiError(val message: String, val isRetryable: Boolean) {
    object NetworkError : GeminiError(
        "Connessione al server AI non disponibile.",
        isRetryable = true
    )
    object RateLimited : GeminiError(
        "Troppe richieste. Attendi qualche secondo.",
        isRetryable = true
    )
    object ParseError : GeminiError(
        "Risposta AI non interpretabile. Riprova.",
        isRetryable = true
    )
    object ContentFiltered : GeminiError(
        "Contenuto filtrato. Riformula la richiesta.",
        isRetryable = false
    )
    data class Unknown(val cause: String) : GeminiError(
        "Errore AI: $cause",
        isRetryable = true
    )
}
```

### 2.3 ErrorSnackbar con Retry

```kotlin
@Composable
fun ErrorSnackbar(
    error: AppError?,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)?
) {
    error?.let {
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                if (it.isRetryable && onRetry != null) {
                    TextButton(onClick = onRetry) {
                        Text("Riprova")
                    }
                }
            },
            dismissAction = {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Chiudi")
                }
            }
        ) {
            Text(it.message)
        }
    }
}
```

---

## 3. Feedback Visivi

### 3.1 Stati Salvataggio

```kotlin
// Pattern consistente per tutti i ConfirmViewModel

sealed class SaveState {
    object Idle : SaveState()
    object Saving : SaveState()
    object Success : SaveState()
    data class Error(val message: String) : SaveState()
}

// Uso nel Composable
when (saveState) {
    SaveState.Saving -> {
        // Disabilita pulsante, mostra spinner
        Button(enabled = false) {
            CircularProgressIndicator(Modifier.size(16.dp))
            Text("Salvataggio...")
        }
    }
    SaveState.Success -> {
        // Feedback temporaneo, poi naviga
        LaunchedEffect(Unit) {
            delay(500)
            onSaved()
        }
    }
    is SaveState.Error -> {
        // Mostra errore, mantieni dati
        ErrorSnackbar(error = saveState.message, onRetry = { viewModel.retry() })
    }
}
```

### 3.2 Animazioni Transizione

```kotlin
// Transizione tra VoiceScreen → ConfirmScreen

NavHost(...) {
    composable(
        route = Screen.VoiceMaintenance.route,
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left)
        }
    ) { ... }
    
    composable(
        route = Screen.MaintenanceConfirm.route,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left)
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right)
        }
    ) { ... }
}
```

### 3.3 Colori Stati nel Theme

```kotlin
// Aggiungere a Theme.kt

val ColorScheme.success: Color
    @Composable
    get() = Color(0xFF4CAF50)

val ColorScheme.successContainer: Color
    @Composable
    get() = Color(0xFFE8F5E9)

val ColorScheme.warning: Color
    @Composable
    get() = Color(0xFFFF9800)

val ColorScheme.warningContainer: Color
    @Composable
    get() = Color(0xFFFFF3E0)
```

---

## 4. Pulizia Codice

### 4.1 Deprecare Vecchio Flusso Conversazionale

```kotlin
// In GeminiService.kt

/**
 * @deprecated Usare extractMaintenanceData() per il nuovo paradigma Voice Dump.
 * Mantenuto temporaneamente per backward compatibility.
 */
@Deprecated(
    message = "Use extractMaintenanceData() instead",
    replaceWith = ReplaceWith("extractMaintenanceData(transcript)")
)
suspend fun processVoiceInput(message: String): GeminiResult { ... }
```

### 4.2 Semplificare ConversationContext

Valutare se mantenere `ConversationContext` per altri usi o deprecare completamente.

```kotlin
// Se mantenuto, semplificare rimuovendo ActiveTask multi-step

data class ConversationContext(
    val currentProduct: Product? = null,
    val lastSearchResults: List<Product> = emptyList(),
    // RIMOSSO: activeTask, recentExchanges, speakerHint
)
```

### 4.3 Rimuovere Callback Inutilizzati

In `HomeScreen.kt`, rimuovere callback non più necessari dopo migrazione al nuovo paradigma.

---

## 5. Test Manuali Sistematici

### 5.1 Checklist Test Manutenzione

```markdown
## Test VoiceMaintenanceScreen

### Input vocale
- [ ] Tocco microfono → inizia ascolto
- [ ] Animazione pulse attiva
- [ ] Parlare → testo appare in real-time
- [ ] Silenzio 2.5s → termina ascolto automatico
- [ ] Tocco durante ascolto → ferma ascolto

### Estrazione
- [ ] "Ho riparato il frigo in camera 12" → estrae prodotto + tipo
- [ ] "Sono Mario di TechMed" → estrae manutentore
- [ ] "Ci ho messo due ore" → estrae durata = 120
- [ ] "Lavoro in garanzia" → isWarranty = true
- [ ] Input vuoto → errore gestito

### Conferma
- [ ] Scheda mostra dati estratti
- [ ] Prodotto trovato → mostra ✓
- [ ] Prodotto ambiguo → mostra radio button
- [ ] Prodotto non trovato → mostra ricerca
- [ ] Tutti i campi editabili
- [ ] Salva → crea record in DB
- [ ] Annulla → torna indietro senza salvare
```

### 5.2 Checklist Test Prodotto

```markdown
## Test VoiceProductScreen

### Estrazione
- [ ] "Concentratore ossigeno Philips" → nome + produttore
- [ ] "In camera 8" → location hint
- [ ] "Comprato da Medika" → fornitore
- [ ] "Garanzia 2 anni" → warrantyMonths = 24

### Conferma
- [ ] Ubicazione trovata → mostra ✓
- [ ] Ubicazione non trovata → opzione crea
- [ ] Creazione inline → torna con entity linkata
- [ ] Fornitore trovato/non trovato → gestito
```

### 5.3 Checklist Test Errori

```markdown
## Test Gestione Errori

### STT
- [ ] Nessuna rete → errore con retry
- [ ] Silenzio totale → "Non ho capito"
- [ ] Microfono negato → errore permission

### Gemini
- [ ] Nessuna rete → errore con retry
- [ ] Risposta malformata → errore parse, retry
- [ ] Timeout → errore con retry

### Salvataggio
- [ ] DB error → errore, mantieni dati
- [ ] Entity reference invalida → errore specifico
```

---

## 6. Documentazione

### 6.1 Aggiornare CLAUDE.md

Aggiungere sezione:

```markdown
## 🎤 Paradigma Voice Dump (v2.0)

### Flusso
1. Utente tocca pulsante specifico (Manutenzione/Prodotto/...)
2. Screen vocale con prompt guida
3. Utente parla liberamente (tutto in una volta)
4. Gemini estrae dati strutturati (1 chiamata)
5. Scheda conferma precompilata
6. Utente verifica/modifica → Salva

### Entry Points
- `VoiceMaintenanceScreen` → `MaintenanceConfirmScreen`
- `VoiceProductScreen` → `ProductConfirmScreen`
- `VoiceMaintainerScreen` → `MaintainerConfirmScreen`
- `VoiceLocationScreen` → `LocationConfirmScreen`

### File Chiave
- `ExtractionPrompts.kt` - Prompt Gemini per estrazione
- `ExtractionModels.kt` - Modelli dati JSON
- `GeminiService.extractXxxData()` - Metodi estrazione
```

---

## 7. Criteri di Completamento Fase 4

### Componenti
- [ ] MicrophoneButton riutilizzabile in tutti i flussi
- [ ] TranscriptBox con typing indicator
- [ ] EntitySelectionCard generico
- [ ] ErrorSnackbar con retry

### Errori
- [ ] Tutti gli errori STT gestiti con messaggi chiari
- [ ] Tutti gli errori Gemini gestiti
- [ ] Errori salvataggio non perdono dati

### UI/UX
- [ ] Animazioni transizione fluide
- [ ] Feedback visivo per tutti gli stati
- [ ] Colori consistenti per success/warning/error

### Codice
- [ ] Vecchio flusso deprecato con annotation
- [ ] Nessun warning critico
- [ ] CLAUDE.md aggiornato

### Test
- [ ] Tutte le checklist completate
- [ ] Test su device fisico
- [ ] Test con utente reale (Mario!)

---

## 8. NON Fare in Questa Fase

❌ Unit test automatizzati (fuori scope)
❌ UI test con Espresso
❌ Refactoring architetturale maggiore
❌ Nuove funzionalità
❌ Ottimizzazione performance prematura
