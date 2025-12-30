# SPEC: Pulizia Memoria Gemini su Salva/Annulla

**Data:** 30 Dicembre 2025  
**Priorità:** CRITICA  
**Impatto:** Risolve contaminazione dati tra sessioni vocali  

---

## 1. Problema Identificato

### 1.1 Sintomo (dal log TEST1AGGIUNTIVO)

```
11:55:54 - Estrazione Location: "ufficio assistente sociale"
11:59:53 - Estrazione Product: contiene frammenti della location precedente!
```

Il `ConversationContext` di Gemini accumula dati tra sessioni vocali diverse, causando:
- Dati estratti "contaminati" da sessioni precedenti
- Confusione nell'entity resolution
- Confidence scores inaffidabili

### 1.2 Causa Root

Il metodo `geminiService.resetContext()` viene chiamato **solo** quando `HomeViewModel.onCleared()` viene invocato (distruzione Activity). Ma nei flussi Voice Dump:

1. Utente fa voice dump → VoiceScreen
2. Naviga a ConfirmScreen → HomeViewModel **NON** viene distrutto
3. Salva o Annulla → torna a Home
4. Il contesto Gemini è ancora "sporco"

### 1.3 Soluzione

Chiamare `resetContext()` su **ogni completamento di flusso**:
- ✅ **Salva** → missione completata, pulisci
- ❌ **Annulla/Back/Swipe** → missione abbandonata, pulisci

---

## 2. Architettura Soluzione

### 2.1 Approccio: Callback Centralizzato in Navigation.kt

Invece di modificare ogni ConfirmScreen, gestiamo tutto in `Navigation.kt` dove già definiamo i callback `onSaved` e `onNavigateBack`.

**Vantaggi:**
- Punto unico di modifica
- Nessuna modifica alle schermate Confirm
- Facile da testare e debuggare

### 2.2 Dipendenza Necessaria

`Navigation.kt` deve poter chiamare `geminiService.resetContext()`. 

**Opzione scelta:** Passare un callback `onSessionComplete` dal livello superiore (MainActivity o AppNavigation wrapper).

---

## 3. Implementazione

### 3.1 Nuovo Composable Wrapper

Creare un wrapper che fornisce il callback di cleanup:

```kotlin
// File: app/src/main/java/org/incammino/hospiceinventory/ui/navigation/NavigationWithCleanup.kt

package org.incammino.hospiceinventory.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import org.incammino.hospiceinventory.ui.screens.home.HomeViewModel

/**
 * Wrapper che fornisce il callback per pulizia memoria Gemini.
 * 
 * Il cleanup viene triggerato quando un flusso Voice Dump termina,
 * sia con Salva che con Annulla/Back.
 */
@Composable
fun AppNavigationWithCleanup(
    navController: NavHostController
) {
    // Ottieni HomeViewModel per accedere a GeminiService
    val homeViewModel: HomeViewModel = hiltViewModel()
    
    AppNavigation(
        navController = navController,
        onVoiceSessionComplete = {
            // Pulisce il ConversationContext di Gemini
            homeViewModel.clearGeminiContext()
        }
    )
}
```

### 3.2 Nuovo Metodo in HomeViewModel

```kotlin
// File: app/src/main/java/org/incammino/hospiceinventory/ui/screens/home/HomeViewModel.kt
// Aggiungere questo metodo:

/**
 * Pulisce il contesto conversazionale di Gemini.
 * 
 * Chiamato quando un flusso Voice Dump termina (Salva o Annulla)
 * per evitare contaminazione tra sessioni diverse.
 * 
 * @see GeminiService.resetContext
 */
fun clearGeminiContext() {
    voiceAssistant.clearContext()
    Log.d(TAG, "Gemini context cleared after voice session")
}
```

### 3.3 Nuovo Metodo in VoiceAssistant

```kotlin
// File: app/src/main/java/org/incammino/hospiceinventory/service/voice/VoiceAssistant.kt
// Aggiungere questo metodo:

/**
 * Pulisce il contesto conversazionale senza rilasciare le risorse.
 * Usare quando si termina una sessione vocale ma si rimane nell'app.
 */
fun clearContext() {
    geminiService.resetContext()
}
```

### 3.4 Modifica Signature di AppNavigation

```kotlin
// File: app/src/main/java/org/incammino/hospiceinventory/ui/navigation/Navigation.kt

@Composable
fun AppNavigation(
    navController: NavHostController,
    onVoiceSessionComplete: () -> Unit = {}  // NUOVO PARAMETRO
) {
    // ... resto del codice
}
```

### 3.5 Aggiornare Callback nelle Route Voice Dump

In `Navigation.kt`, modificare TUTTE le route dei flussi Voice Dump:

#### MaintenanceConfirm
```kotlin
composable(Screen.MaintenanceConfirm.route) {
    val data = remember { MaintenanceDataHolder.consume() }

    if (data != null) {
        MaintenanceConfirmScreen(
            initialData = data,
            onNavigateBack = { 
                onVoiceSessionComplete()  // <-- CLEANUP
                navController.popBackStack() 
            },
            onSaved = {
                onVoiceSessionComplete()  // <-- CLEANUP
                navController.popBackStack(Screen.Home.route, inclusive = false)
            },
            onNavigateToProductSearch = {
                navController.navigate(Screen.Search.createRoute(""))
            }
        )
    } else {
        LaunchedEffect(Unit) {
            onVoiceSessionComplete()  // <-- CLEANUP anche qui
            navController.popBackStack()
        }
    }
}
```

#### ProductConfirm
```kotlin
composable(Screen.ProductConfirm.route) {
    val data = remember { ProductDataHolder.consume() }

    if (data != null) {
        ProductConfirmScreen(
            initialData = data,
            onNavigateBack = { 
                onVoiceSessionComplete()  // <-- CLEANUP
                navController.popBackStack() 
            },
            onSaved = {
                onVoiceSessionComplete()  // <-- CLEANUP
                navController.popBackStack(Screen.Home.route, inclusive = false)
            },
            onNavigateToLocationSearch = {
                navController.navigate(Screen.LocationList.route)
            }
        )
    } else {
        LaunchedEffect(Unit) {
            onVoiceSessionComplete()
            navController.popBackStack()
        }
    }
}
```

#### LocationConfirm
```kotlin
composable(Screen.LocationConfirm.route) {
    val data = remember { LocationDataHolder.consume() }

    if (data != null) {
        LocationConfirmScreen(
            initialData = data,
            onNavigateBack = { 
                onVoiceSessionComplete()  // <-- CLEANUP
                navController.popBackStack() 
            },
            onSaved = {
                onVoiceSessionComplete()  // <-- CLEANUP
                navController.popBackStack(Screen.Home.route, inclusive = false)
            }
        )
    } else {
        LaunchedEffect(Unit) {
            onVoiceSessionComplete()
            navController.popBackStack()
        }
    }
}
```

#### MaintainerConfirm
```kotlin
composable(Screen.MaintainerConfirm.route) {
    val data = remember { MaintainerDataHolder.consume() }

    if (data != null) {
        MaintainerConfirmScreen(
            initialData = data,
            onNavigateBack = { 
                onVoiceSessionComplete()  // <-- CLEANUP
                navController.popBackStack() 
            },
            onSaved = {
                onVoiceSessionComplete()  // <-- CLEANUP
                navController.popBackStack(Screen.Home.route, inclusive = false)
            }
        )
    } else {
        LaunchedEffect(Unit) {
            onVoiceSessionComplete()
            navController.popBackStack()
        }
    }
}
```

### 3.6 Aggiornare MainActivity

```kotlin
// File: app/src/main/java/org/incammino/hospiceinventory/MainActivity.kt
// Nel setContent, sostituire AppNavigation con:

setContent {
    HospiceInventoryTheme {
        val navController = rememberNavController()
        
        // Usa il wrapper con cleanup
        AppNavigationWithCleanup(
            navController = navController
        )
    }
}
```

---

## 4. Gestione Back Gesture (Swipe)

### 4.1 Problema

Lo swipe-to-back di Android non passa per `onNavigateBack` ma bypassa direttamente.

### 4.2 Soluzione: BackHandler

Aggiungere `BackHandler` in ogni ConfirmScreen per intercettare il back gesture:

```kotlin
// Aggiungere in OGNI *ConfirmScreen.kt, subito dopo la dichiarazione del Composable:

import androidx.activity.compose.BackHandler

@Composable
fun MaintenanceConfirmScreen(
    initialData: MaintenanceConfirmData,
    viewModel: MaintenanceConfirmViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    onNavigateToProductSearch: () -> Unit
) {
    // Intercetta back gesture per garantire cleanup
    BackHandler {
        onNavigateBack()  // Delega al callback che fa cleanup
    }
    
    // ... resto del codice
}
```

**Applicare a:**
- `MaintenanceConfirmScreen.kt`
- `ProductConfirmScreen.kt`
- `LocationConfirmScreen.kt`
- `MaintainerConfirmScreen.kt`

---

## 5. Logging per Debug

### 5.1 Aggiungere Log in GeminiService.resetContext()

```kotlin
// In GeminiService.kt, modificare:

fun resetContext() {
    val hadActiveTask = conversationContext.activeTask != null
    val exchangeCount = conversationContext.recentExchanges.size
    
    conversationContext = ConversationContext()
    
    Log.i(TAG, "Context RESET - had activeTask: $hadActiveTask, exchanges cleared: $exchangeCount")
}
```

### 5.2 Tag Logcat per Monitoraggio

```
adb logcat -s GeminiService:I HomeViewModel:D
```

Output atteso dopo fix:
```
D/HomeViewModel: Gemini context cleared after voice session
I/GeminiService: Context RESET - had activeTask: false, exchanges cleared: 3
```

---

## 6. Test di Verifica

### 6.1 Test Manuale Base

1. Home → Nuovo Prodotto (voice)
2. Parla: "Laptop Dell in ufficio"
3. Vai a Conferma
4. **Annulla** (back o freccia)
5. Home → Nuova Manutenzione (voice)
6. Parla: "Riparato il frigo"
7. Verifica: la scheda Conferma **NON** deve contenere "Dell" o "ufficio"

### 6.2 Test Swipe Gesture

1. Home → Nuova Location (voice)
2. Parla: "Camera 12 primo piano"
3. Vai a Conferma
4. **Swipe da sinistra** (back gesture)
5. Verifica log: deve apparire "Context RESET"

### 6.3 Test Salvataggio

1. Home → Nuovo Manutentore (voice)
2. Parla: "TechService di Milano"
3. Conferma → **Salva**
4. Verifica log: "Context RESET" dopo salvataggio
5. Home → Nuova Manutenzione
6. Parla: "Sostituito filtro"
7. Verifica: nessun riferimento a "TechService" nei dati estratti

---

## 7. Checklist Implementazione

```markdown
### File da Modificare

- [ ] Creare `NavigationWithCleanup.kt`
- [ ] `HomeViewModel.kt` - aggiungere `clearGeminiContext()`
- [ ] `VoiceAssistant.kt` - aggiungere `clearContext()`
- [ ] `GeminiService.kt` - migliorare logging in `resetContext()`
- [ ] `Navigation.kt` - aggiungere parametro e callback
- [ ] `MainActivity.kt` - usare nuovo wrapper
- [ ] `MaintenanceConfirmScreen.kt` - aggiungere BackHandler
- [ ] `ProductConfirmScreen.kt` - aggiungere BackHandler
- [ ] `LocationConfirmScreen.kt` - aggiungere BackHandler
- [ ] `MaintainerConfirmScreen.kt` - aggiungere BackHandler

### Test
- [ ] Test annulla con freccia back
- [ ] Test annulla con swipe gesture
- [ ] Test salvataggio
- [ ] Test sequenza: Location → annulla → Product (no contaminazione)
- [ ] Test sequenza: Product → salva → Maintenance (no contaminazione)
```

---

## 8. Note Aggiuntive

### 8.1 Perché Non Usare DisposableEffect

Si potrebbe pensare di usare `DisposableEffect` nelle ConfirmScreen per fare cleanup automatico. Ma questo non funzionerebbe bene perché:
- Il cleanup deve avvenire **prima** della navigazione
- `DisposableEffect.onDispose` viene chiamato **dopo** che il Composable è già stato rimosso

### 8.2 Persistenza Futura (Out of Scope)

Se in futuro servisse persistere il contesto tra sessioni (es. per continuare un flusso interrotto), si dovrebbe:
1. Serializzare `ConversationContext` con kotlinx.serialization
2. Salvare in `SavedStateHandle` del ViewModel
3. Ripristinare in `init` block

Ma per ora il comportamento corretto è: **ogni flusso Voice Dump è indipendente**.
