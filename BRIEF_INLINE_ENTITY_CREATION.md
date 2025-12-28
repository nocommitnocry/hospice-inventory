# BRIEF: Inline Entity Creation

> Sintesi per implementare la creazione inline di entità mancanti durante il flusso Voice Dump.
> Riferimento: SPEC_VOICE_DUMP_VISUAL_CONFIRM.md sezioni 7.1-7.2

---

## Problema

Quando l'utente dice "fornitore Medika" e Medika non esiste nel database, il sistema deve:
1. Mostrare che l'entità non è stata trovata
2. Offrire la possibilità di crearla al volo con dati minimi
3. Collegare la nuova entità al record in fase di creazione

Attualmente: EntityResolver restituisce `Resolution.NotFound` ma le ConfirmScreen non gestiscono questo caso.

---

## Soluzione da Implementare

### 1. Pattern UI

```
Campo: Fornitore
[x] Non trovato: "Medika"
    [➕ Crea "Medika"]  ← Bottone inline
```

Quando l'utente clicca:
1. Crea entità minima (solo nome, `needsCompletion = true`)
2. Aggiorna il campo con l'ID della nuova entità
3. Mostra conferma: "✓ Medika creato"

### 2. File da Modificare

#### ConfirmScreens (UI)

**MaintenanceConfirmScreen.kt** (linea ~300-400)
- Aggiungere gestione `maintainerMatch: EntityMatch.NotFound`
- Bottone "Crea Manutentore" che chiama ViewModel

**ProductConfirmScreen.kt** (linea ~400-500)
- Aggiungere gestione per:
  - `locationMatch: EntityMatch.NotFound`
  - `supplierMatch: EntityMatch.NotFound` (se fornitore è entità separata)
  - `serviceMaintainerMatch: EntityMatch.NotFound`
  - `warrantyMaintainerMatch: EntityMatch.NotFound`

**MaintainerConfirmScreen.kt** - Non necessario (manutentore è l'entità principale)

**LocationConfirmScreen.kt**
- Aggiungere gestione per `parentMatch: EntityMatch.NotFound` (edificio/piano parent)

#### ConfirmViewModels (Logica)

**MaintenanceConfirmViewModel.kt**
```kotlin
fun createMaintainerInline(name: String) {
    viewModelScope.launch {
        val newMaintainer = Maintainer(
            id = "",  // Repository genera UUID
            name = name,
            email = null,
            phone = null,
            // ... altri null ...
            isActive = true,
            needsCompletion = true  // FLAG IMPORTANTE!
        )
        val id = maintainerRepository.insert(newMaintainer)
        // Aggiorna stato UI con nuovo maintainerId
        _uiState.update { it.copy(
            maintainerId = id,
            maintainerName = name,
            maintainerMatch = EntityMatch.Found(newMaintainer.copy(id = id))
        )}
    }
}
```

**ProductConfirmViewModel.kt**
```kotlin
fun createLocationInline(name: String) { ... }
fun createMaintainerInline(name: String, isSupplier: Boolean) { ... }
```

**LocationConfirmViewModel.kt**
```kotlin
fun createParentLocationInline(name: String, type: LocationType) { ... }
```

#### Repositories (già pronti)

I repository hanno già i metodi `insert()` che:
- Generano UUID se id è vuoto
- Settano `createdAt` e `updatedAt`
- Restituiscono l'ID generato

### 3. UI Component Riutilizzabile

Creare componente in `ui/components/`:

```kotlin
// InlineEntityCreator.kt
@Composable
fun InlineEntityCreator(
    entityName: String,          // "Medika"
    entityType: String,          // "Manutentore"
    onCreateClick: () -> Unit,
    isCreating: Boolean = false,
    wasCreated: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Warning, "Non trovato", tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Non trovato: \"$entityName\"",
                style = MaterialTheme.typography.bodyMedium
            )
            if (wasCreated) {
                Text(
                    "✓ $entityName creato",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (!wasCreated) {
            TextButton(
                onClick = onCreateClick,
                enabled = !isCreating
            ) {
                if (isCreating) {
                    CircularProgressIndicator(Modifier.size(16.dp))
                } else {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Crea")
                }
            }
        }
    }
}
```

### 4. Stato UI da Aggiungere

In ogni ConfirmData/UiState:

```kotlin
data class MaintenanceConfirmUiState(
    // ... campi esistenti ...

    // Per inline creation
    val isCreatingMaintainer: Boolean = false,
    val maintainerWasCreatedInline: Boolean = false
)
```

### 5. Gestione Post-Creazione

Dopo la creazione inline, l'utente può:
1. **Completare subito** → Il record viene salvato con `needsCompletion = true`
2. **Completare dopo** → Lista "Da completare" in Settings o Home mostra entità incomplete

Query per entità incomplete (già possibile con campo esistente):
```kotlin
// Nel DAO
@Query("SELECT * FROM maintainers WHERE needsCompletion = 1")
fun getIncomplete(): Flow<List<MaintainerEntity>>
```

---

## Sequenza di Implementazione

1. **Creare InlineEntityCreator.kt** (componente riutilizzabile)

2. **Modificare MaintenanceConfirmViewModel.kt**
   - Aggiungere `createMaintainerInline(name: String)`
   - Aggiungere stati `isCreatingMaintainer`, `maintainerWasCreatedInline`

3. **Modificare MaintenanceConfirmScreen.kt**
   - Usare InlineEntityCreator quando `maintainerMatch is NotFound`

4. **Test su device** - Flusso manutenzione con manutentore inesistente

5. **Ripetere per ProductConfirmScreen** (location, maintainers)

6. **Ripetere per LocationConfirmScreen** (parent location)

---

## Test Case

1. Avviare "Registra Manutenzione"
2. Dire: "Manutenzione programmata della lavatrice, fatta da TecnoService"
3. Se "TecnoService" non esiste → mostrare "Non trovato" + bottone "Crea"
4. Click su "Crea" → TecnoService viene creato con needsCompletion=true
5. Il campo manutentore si aggiorna con il nuovo ID
6. Salvare la manutenzione → tutto collegato correttamente

---

## Note

- **needsCompletion flag** è già implementato su MaintainerEntity e Maintainer domain model
- **EntityResolver** è già implementato e restituisce `Resolution.NotFound`
- **EntityMatch** sealed class è già in ExtractionModels.kt
- I **Repository.insert()** già generano UUID e restituiscono l'ID

Stima complessità: Media. Richiede modifiche UI ma la logica backend è già pronta.
