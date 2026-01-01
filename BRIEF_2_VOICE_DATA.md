# 🔧 Brief Fix #2 - Voice Flow e Data Consistency

**Priorità**: ALTA  
**Effort stimato**: 4-5 ore  
**Focus**: Permessi, VoiceContinue, barcode, e sincronizzazione dati

**Prerequisito**: Completare Brief #1 prima di questo

---

## Contesto

Dopo aver sistemato la navigazione (Brief #1), questo brief risolve i problemi del flusso vocale e della consistenza dati nelle ConfirmScreen.

---

## BUG-01: Permessi microfono non richiesti

### Problema
Al primo tap sul pulsante voce, appare "Errore interno. Riavvia l'app" invece della richiesta permessi Android.

### Causa probabile
`SpeechRecognizer` viene inizializzato senza verificare prima i permessi `RECORD_AUDIO`.

### Soluzione richiesta
Prima di inizializzare il riconoscimento vocale, verificare e richiedere permessi:

```kotlin
val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { isGranted ->
    if (isGranted) {
        // avvia riconoscimento
    } else {
        // mostra messaggio "Permesso microfono necessario"
    }
}

// Prima di startListening:
when {
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
        == PackageManager.PERMISSION_GRANTED -> {
        // procedi
    }
    else -> {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
}
```

### File da modificare
- `VoiceDumpButton.kt` o dove viene chiamato `voiceAssistant.startListening()`
- Possibilmente `VoiceMaintenanceScreen.kt`, `VoiceProductScreen.kt`, etc.

---

## BUG-02: VoiceContinue assente nelle ConfirmScreen

### Problema
Non esiste il pulsante microfono piccolo per aggiungere info vocali dopo il dump iniziale.

### Contesto
Il componente `VoiceContinueButton.kt` esiste nel codice ma non è inserito nelle ConfirmScreen.

### Soluzione richiesta
In ogni ConfirmScreen, aggiungere `VoiceContinueButton` (tipicamente vicino al titolo o in un FAB):

```kotlin
VoiceContinueButton(
    onProcessVoiceWithContext = { transcript ->
        viewModel.processAdditionalVoiceInput(transcript, currentFormData)
    }
)
```

Il ViewModel deve avere un metodo che:
1. Prende il form data attuale
2. Manda a Gemini il transcript + contesto esistente
3. Aggiorna solo i campi menzionati, preservando il resto

### File da modificare
- `MaintenanceConfirmScreen.kt`
- `ProductConfirmScreen.kt`
- `MaintainerConfirmScreen.kt`
- `LocationConfirmScreen.kt`
- Rispettivi ViewModel se manca `processAdditionalVoiceInput`

---

## BUG-04: Icona barcode assente in ProductConfirmScreen

### Problema
Il campo barcode è un semplice TextField senza icona scanner.

### Soluzione richiesta
Trasformare il TextField barcode in:

```kotlin
OutlinedTextField(
    value = barcode,
    onValueChange = { barcode = it },
    label = { Text("Barcode") },
    trailingIcon = {
        IconButton(onClick = { onNavigateToScanner() }) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = "Scansiona barcode"
            )
        }
    },
    modifier = Modifier.fillMaxWidth()
)
```

### Navigazione scanner
Dopo la scansione, il barcode deve tornare a `ProductConfirmScreen` popolando il campo. Verificare che la navigazione preservi tutti gli altri campi già compilati.

### File da modificare
- `ProductConfirmScreen.kt`
- `Navigation.kt` - verificare route scanner con callback

---

## BUG-08: Nome prodotto non visualizzato dopo selezione

### Problema
In `MaintenanceConfirmScreen`, dopo tap su prodotto dalla ricerca inline:
- Il campo visivamente resta vuoto
- Ma il salvataggio associa correttamente il prodotto

### Causa probabile
Lo state `productName` non viene aggiornato quando si seleziona dalla ricerca.

### Soluzione richiesta
In `MaintenanceConfirmViewModel.kt`, quando si seleziona un prodotto:

```kotlin
fun selectProduct(product: Product) {
    _uiState.update { 
        it.copy(
            selectedProductId = product.id,
            selectedProductName = product.name  // <-- questo manca?
        )
    }
    clearProductSearch()
}
```

E in `MaintenanceConfirmScreen.kt` il TextField deve leggere da `uiState.selectedProductName`.

### File da modificare
- `MaintenanceConfirmViewModel.kt`
- `MaintenanceConfirmScreen.kt`

---

## BUG-09: Campi intermittenti in ProductConfirmScreen

### Problema
A volte i campi estratti non appaiono anche se il modello li pronuncia correttamente.

### Causa probabile
Race condition in `DataHolder.consume()` - i dati vengono consumati prima che la UI li legga, oppure la navigazione avviene prima che i dati siano pronti.

### Soluzione richiesta
Verificare il pattern di consumo dati:

```kotlin
// In ProductConfirmScreen
val initialData = remember { 
    DataHolder.consume<ProductExtractionResult>() 
        ?: ProductExtractionResult.empty()
}
```

Il `remember {}` dovrebbe preservare i dati, ma se la composizione avviene due volte (es. configuration change), potrebbe fallire.

**Alternativa più robusta**: passare i dati via navigation arguments (serializzati) invece che via singleton `DataHolder`.

### File da verificare
- `DataHolder.kt`
- `ProductConfirmScreen.kt`
- `Navigation.kt` - timing della navigazione

---

## BUG-10: Navigazione errata dopo modifica ubicazione inline

### Problema
Da `ProductConfirmScreen`:
1. Crea ubicazione al volo
2. Tap per modificarla → `LocationEditScreen`
3. Salva ubicazione
4. ❌ Torna a Home invece che a `ProductConfirmScreen`

### Causa probabile
`LocationEditScreen.onSaved` fa `popBackStack()` che non tiene conto dello stack quando si arriva da `ProductConfirmScreen`.

### Soluzione richiesta
Opzione A: Navigazione con `popUpTo` specifico
Opzione B: Flag per distinguere "edit standalone" vs "edit inline"

```kotlin
// In Navigation.kt per LocationEdit
onSaved = { 
    // Se arriviamo da ProductConfirm, torna lì
    // Altrimenti torna alla lista
    navController.popBackStack()
}
```

Potrebbe servire passare un parametro `returnRoute` a `LocationEditScreen`.

### File da modificare
- `Navigation.kt`
- `LocationEditScreen.kt`

---

## BUG-17: Feedback vocale confuso per ubicazioni

### Problema
Quando si crea un'ubicazione, il modello a volte dice:
- "quale prodotto stai cercando?"
- "vuoi creare un nuovo prodotto?"

Ma l'estrazione funziona correttamente.

### Causa probabile
Il prompt per il feedback vocale non sa che siamo nel flusso ubicazione.

### Soluzione richiesta
Verificare in `ExtractionPrompts.kt` o `GeminiService.kt` che il prompt per `LocationExtraction` specifichi chiaramente:

```
Stai aiutando a registrare una NUOVA UBICAZIONE (non un prodotto).
Conferma i dati estratti: edificio, ala, piano, stanza.
NON chiedere informazioni sui prodotti.
```

### File da modificare
- `ExtractionPrompts.kt`
- `GeminiService.kt` se il prompt è costruito lì

---

## Checklist completamento

- [ ] BUG-01: Primo tap voce chiede permessi (non errore)
- [ ] BUG-02: VoiceContinue presente in tutte le ConfirmScreen
- [ ] BUG-02: VoiceContinue preserva modifiche manuali
- [ ] BUG-04: Campo barcode ha icona scanner
- [ ] BUG-04: Scanner ritorna barcode a ProductConfirmScreen
- [ ] BUG-08: Nome prodotto visibile dopo selezione inline
- [ ] BUG-09: Campi sempre popolati in ProductConfirmScreen
- [ ] BUG-10: Modifica ubicazione inline ritorna a ProductConfirm
- [ ] BUG-17: Feedback vocale corretto per ubicazioni

---

## Test di verifica

Dopo i fix, questi test devono passare:
- A3 (permessi microfono)
- B4.3, B4.4 (VoiceContinue)
- C2.1-C2.5 (barcode scanner)
- B1.7, B2.2 (nome prodotto visibile)
- C1.3 (campi popolati)
- C1.6 (navigazione ubicazione)
- E2 (feedback vocale ubicazione)
