# 🔧 Brief Fix #1 - Navigazione e UI Mancante

**Priorità**: CRITICA  
**Effort stimato**: 4-5 ore  
**Focus**: Collegamenti mancanti e Dashboard incompleta

---

## Contesto

L'app Hospice Inventory ha superato 65% dei test, ma diverse schermate **esistono nel codice** ma **non sono raggiungibili dalla UI**. Questo brief risolve i problemi di navigazione.

---

## BUG-05: Liste Manutentori e Ubicazioni non raggiungibili

### Problema
Le schermate esistono ma non c'è modo di accedervi:
- `MaintainerListScreen.kt` ✅ esiste
- `MaintainerEditScreen.kt` ✅ esiste  
- `LocationListScreen.kt` ✅ esiste
- `LocationEditScreen.kt` ✅ esiste

Ma in `SettingsScreen.kt` mancano i link per raggiungerle.

### Soluzione richiesta
In `SettingsScreen.kt` aggiungere due voci:
1. **"Manutentori"** → naviga a `MaintainerListScreen`
2. **"Ubicazioni"** → naviga a `LocationListScreen`

### File da modificare
- `SettingsScreen.kt` - aggiungere voci menu
- Verificare che `Navigation.kt` abbia già le route (dovrebbe averle)

### Risultato atteso
Settings → Manutentori → Lista → Tap su item → MaintainerEditScreen
Settings → Ubicazioni → Lista → Tap su item → LocationEditScreen

---

## BUG-06: Dashboard Home incompleta

### Problema
`HomeViewModel.kt` calcola già:
- `overdueCount` ✅ visualizzato
- `upcomingCount` ❌ NON visualizzato
- `totalProducts` ❌ NON visualizzato

Inoltre manca accesso a `MaintenanceListScreen` con i filtri.

### Soluzione richiesta
In `HomeScreen.kt`:
1. Mostrare 3 card: Scadute / In scadenza (7gg) / Totale prodotti
2. Tap su "Scadute" → `MaintenanceListScreen` con filtro preimpostato
3. Tap su "In scadenza" → `MaintenanceListScreen` con filtro settimana

### File da modificare
- `HomeScreen.kt` - aggiungere card mancanti e navigazione
- Verificare che `MaintenanceListScreen` accetti filtro iniziale come parametro

---

## BUG-03: Back senza conferma annullamento

### Problema
Nelle ConfirmScreen (dopo voice dump), premere Back esce senza chiedere conferma. L'utente perde i dati e potrebbe esserci contaminazione nella sessione successiva.

### Soluzione richiesta
In tutte le ConfirmScreen aggiungere `BackHandler`:
```kotlin
BackHandler {
    showDiscardDialog = true
}

if (showDiscardDialog) {
    AlertDialog(
        title = { Text("Annullare?") },
        text = { Text("I dati inseriti andranno persi.") },
        onDismissRequest = { showDiscardDialog = false },
        confirmButton = {
            TextButton(onClick = { 
                onNavigateBack()  // o chiamare cleanup
            }) {
                Text("Annulla")
            }
        },
        dismissButton = {
            TextButton(onClick = { showDiscardDialog = false }) {
                Text("Continua")
            }
        }
    )
}
```

### File da modificare
- `MaintenanceConfirmScreen.kt`
- `ProductConfirmScreen.kt`
- `MaintainerConfirmScreen.kt`
- `LocationConfirmScreen.kt`

### Nota
Verificare che `onVoiceSessionComplete` callback in `Navigation.kt` venga chiamato per pulire il contesto Gemini.

---

## BUG-07: Pull-to-refresh non funziona

### Problema
Trascinando verso il basso nella Home non accade nulla.

### Soluzione richiesta
Aggiungere `pullRefresh` modifier in `HomeScreen.kt`:

```kotlin
val pullRefreshState = rememberPullRefreshState(
    refreshing = uiState.isRefreshing,
    onRefresh = { viewModel.refresh() }
)

Box(Modifier.pullRefresh(pullRefreshState)) {
    // contenuto esistente
    
    PullRefreshIndicator(
        refreshing = uiState.isRefreshing,
        state = pullRefreshState,
        modifier = Modifier.align(Alignment.TopCenter)
    )
}
```

### File da modificare
- `HomeScreen.kt`
- `HomeViewModel.kt` - aggiungere stato `isRefreshing` se mancante

---

## BUG-11: Campo durata assente in MaintenanceEditScreen

### Problema
`MaintenanceEditScreen.kt` non ha il campo per modificare la durata dell'intervento, mentre `MaintenanceConfirmScreen.kt` ce l'ha.

### Soluzione richiesta
Copiare il componente durata da `MaintenanceConfirmScreen` a `MaintenanceEditScreen`.

### File da modificare
- `MaintenanceEditScreen.kt`
- `MaintenanceEditViewModel.kt` - aggiungere stato e update per durata se mancante

---

## Checklist completamento

- [ ] BUG-05: Settings mostra "Manutentori" e "Ubicazioni"
- [ ] BUG-05: Navigazione a liste funziona
- [ ] BUG-05: Tap su item apre EditScreen
- [ ] BUG-06: Home mostra 3 conteggi
- [ ] BUG-06: Tap su card naviga a MaintenanceList filtrata
- [ ] BUG-03: Back in ConfirmScreen chiede conferma
- [ ] BUG-07: Pull-to-refresh aggiorna dashboard
- [ ] BUG-11: MaintenanceEditScreen ha campo durata

---

## Test di verifica

Dopo i fix, questi test devono passare:
- D6, E6 (liste raggiungibili)
- F3.1-F3.6 (modifica manutentore)
- F4.1-F4.6 (modifica ubicazione)
- I2-I6 (dashboard e filtri)
- I7 (pull-to-refresh)
- B5.2, B5.3 (conferma annullamento)
- F2.4 (durata in edit)
