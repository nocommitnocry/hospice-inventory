# Specifica: Miglioramento LocationEditScreen

**Data**: 30 Dicembre 2025  
**Priorità**: Alta  
**Stima**: 2-3 ore  

## Problema

LocationEditScreen è incompleto rispetto a LocationConfirmScreen (flusso vocale) e usa TextField liberi che causeranno inconsistenze nei dati ("Ala Vecchia" vs "ala vecchia" vs "AlaVecchia").

## Obiettivi

1. **Allineare** LocationEditScreen con tutti i campi di LocationConfirmScreen
2. **Autocomplete con suggerimenti** per building, department, floor basati sui valori esistenti nel DB
3. **Normalizzazione** automatica dei valori inseriti (capitalizzazione)
4. **Dropdown** per type (già esistente in LocationConfirmScreen)

## Campi da aggiungere a LocationEditScreen

| Campo | Tipo UI | Sorgente suggerimenti |
|-------|---------|----------------------|
| type | Dropdown (enum) | LocationType.entries |
| building | AutocompleteTextField | Query DISTINCT building FROM locations |
| floor | AutocompleteTextField | Query DISTINCT floor FROM locations |
| floorName | AutocompleteTextField | Query DISTINCT floorName FROM locations |
| department | AutocompleteTextField | Query DISTINCT department FROM locations |
| hasOxygenOutlet | Switch | - |
| bedCount | TextField numerico | - |

## Architettura

### 1. Nuovo componente: AutocompleteTextField

Composable riutilizzabile che mostra suggerimenti filtrati mentre l'utente digita.

```kotlin
// File: app/src/main/java/org/incammino/hospiceinventory/ui/components/AutocompleteTextField.kt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutocompleteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
    singleLine: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var textFieldValue by remember(value) { mutableStateOf(value) }
    
    // Filtra suggerimenti in base al testo corrente
    val filteredSuggestions = remember(textFieldValue, suggestions) {
        if (textFieldValue.isBlank()) {
            suggestions.take(10) // Mostra i primi 10 se campo vuoto
        } else {
            suggestions.filter { 
                it.contains(textFieldValue, ignoreCase = true) 
            }.take(10)
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filteredSuggestions.isNotEmpty(),
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                onValueChange(newValue)
                expanded = true
            },
            label = { Text(label) },
            modifier = modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable),
            singleLine = singleLine,
            placeholder = placeholder?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                capitalization = capitalization
            ),
            trailingIcon = {
                if (filteredSuggestions.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            }
        )

        if (filteredSuggestions.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                filteredSuggestions.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion) },
                        onClick = {
                            textFieldValue = suggestion
                            onValueChange(suggestion)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
```

### 2. Estensioni DAO per suggerimenti

```kotlin
// File: app/src/main/java/org/incammino/hospiceinventory/data/local/dao/LocationDao.kt
// AGGIUNGERE queste query:

@Query("SELECT DISTINCT building FROM locations WHERE building IS NOT NULL AND building != '' AND isActive = 1 ORDER BY building")
suspend fun getDistinctBuildings(): List<String>

@Query("SELECT DISTINCT floor FROM locations WHERE floor IS NOT NULL AND floor != '' AND isActive = 1 ORDER BY floor")
suspend fun getDistinctFloors(): List<String>

@Query("SELECT DISTINCT floorName FROM locations WHERE floorName IS NOT NULL AND floorName != '' AND isActive = 1 ORDER BY floorName")
suspend fun getDistinctFloorNames(): List<String>

@Query("SELECT DISTINCT department FROM locations WHERE department IS NOT NULL AND department != '' AND isActive = 1 ORDER BY department")
suspend fun getDistinctDepartments(): List<String>
```

### 3. Estensioni Repository

```kotlin
// File: app/src/main/java/org/incammino/hospiceinventory/data/repository/LocationRepository.kt
// AGGIUNGERE:

/**
 * Suggerimenti per autocomplete.
 */
suspend fun getDistinctBuildings(): List<String> = locationDao.getDistinctBuildings()
suspend fun getDistinctFloors(): List<String> = locationDao.getDistinctFloors()
suspend fun getDistinctFloorNames(): List<String> = locationDao.getDistinctFloorNames()
suspend fun getDistinctDepartments(): List<String> = locationDao.getDistinctDepartments()

/**
 * Tutti i suggerimenti in un colpo solo (per UI).
 */
data class LocationSuggestions(
    val buildings: List<String>,
    val floors: List<String>,
    val floorNames: List<String>,
    val departments: List<String>
)

suspend fun getAllSuggestions(): LocationSuggestions = LocationSuggestions(
    buildings = getDistinctBuildings(),
    floors = getDistinctFloors(),
    floorNames = getDistinctFloorNames(),
    departments = getDistinctDepartments()
)
```

### 4. Aggiornamento LocationEditUiState

```kotlin
// File: app/src/main/java/org/incammino/hospiceinventory/ui/screens/location/LocationEditViewModel.kt

data class LocationEditUiState(
    // Dati form esistenti
    val name: String = "",
    val parentId: String? = null,
    val parentName: String? = null,
    val address: String = "",
    val notes: String = "",

    // NUOVI campi
    val type: LocationType? = null,
    val building: String = "",
    val floor: String = "",
    val floorName: String = "",
    val department: String = "",
    val hasOxygenOutlet: Boolean = false,
    val bedCount: Int? = null,

    // Liste per dropdown/autocomplete
    val availableParents: List<Location> = emptyList(),
    val buildingSuggestions: List<String> = emptyList(),
    val floorSuggestions: List<String> = emptyList(),
    val floorNameSuggestions: List<String> = emptyList(),
    val departmentSuggestions: List<String> = emptyList(),

    // Metadati
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedLocationId: String? = null
)
```

### 5. Aggiornamento ViewModel

```kotlin
// LocationEditViewModel - aggiungere:

init {
    loadParentLocations()
    loadSuggestions()  // NUOVO
    if (locationId != null) {
        loadLocation(locationId)
    }
}

private fun loadSuggestions() {
    viewModelScope.launch {
        try {
            val suggestions = locationRepository.getAllSuggestions()
            _uiState.update { state ->
                state.copy(
                    buildingSuggestions = suggestions.buildings,
                    floorSuggestions = suggestions.floors,
                    floorNameSuggestions = suggestions.floorNames,
                    departmentSuggestions = suggestions.departments
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load suggestions", e)
        }
    }
}

// Nuovi update methods:
fun updateType(value: LocationType?) {
    _uiState.update { it.copy(type = value) }
}

fun updateBuilding(value: String) {
    _uiState.update { it.copy(building = value) }
}

fun updateFloor(value: String) {
    _uiState.update { it.copy(floor = value.uppercase()) }
}

fun updateFloorName(value: String) {
    _uiState.update { it.copy(floorName = value) }
}

fun updateDepartment(value: String) {
    _uiState.update { it.copy(department = value) }
}

fun updateHasOxygenOutlet(value: Boolean) {
    _uiState.update { it.copy(hasOxygenOutlet = value) }
}

fun updateBedCount(value: Int?) {
    _uiState.update { it.copy(bedCount = value) }
}

// Aggiornare save() per includere i nuovi campi:
fun save() {
    if (!validate()) return
    
    val state = _uiState.value
    _uiState.update { it.copy(isSaving = true) }

    viewModelScope.launch {
        try {
            val location = Location(
                id = locationId ?: "",
                name = state.name.trim(),
                type = state.type,
                parentId = state.parentId,
                floor = state.floor.takeIf { it.isNotBlank() },
                floorName = state.floorName.takeIf { it.isNotBlank() },
                department = state.department.takeIf { it.isNotBlank() },
                building = state.building.takeIf { it.isNotBlank() },
                hasOxygenOutlet = state.hasOxygenOutlet,
                bedCount = state.bedCount,
                address = state.address.takeIf { it.isNotBlank() },
                notes = state.notes.takeIf { it.isNotBlank() },
                isActive = true,
                needsCompletion = false
            )

            val savedId = if (state.isNew) {
                locationRepository.insert(location)
            } else {
                locationRepository.update(location)
                locationId!!
            }

            _uiState.update { it.copy(isSaving = false, savedLocationId = savedId) }
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(isSaving = false, error = e.message ?: "Errore salvataggio") 
            }
        }
    }
}

// Aggiornare loadLocation() per caricare tutti i campi:
private fun loadLocation(id: String) {
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }

        locationRepository.getByIdFlow(id).collect { location ->
            if (location != null) {
                val parentName = location.parentId?.let { parentId ->
                    locationRepository.getById(parentId)?.name
                }

                _uiState.update {
                    it.copy(
                        name = location.name,
                        type = location.type,
                        parentId = location.parentId,
                        parentName = parentName,
                        building = location.building ?: "",
                        floor = location.floor ?: "",
                        floorName = location.floorName ?: "",
                        department = location.department ?: "",
                        hasOxygenOutlet = location.hasOxygenOutlet,
                        bedCount = location.bedCount,
                        address = location.address ?: "",
                        notes = location.notes ?: "",
                        isNew = false,
                        isLoading = false,
                        error = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, error = "Ubicazione non trovata")
                }
            }
        }
    }
}
```

### 6. Aggiornamento LocationEditScreen

```kotlin
// File: app/src/main/java/org/incammino/hospiceinventory/ui/screens/location/LocationEditScreen.kt

@Composable
private fun LocationEditForm(
    uiState: LocationEditUiState,
    onNameChange: (String) -> Unit,
    onTypeChange: (LocationType?) -> Unit,
    onParentChange: (String?, String?) -> Unit,
    onBuildingChange: (String) -> Unit,
    onFloorChange: (String) -> Unit,
    onFloorNameChange: (String) -> Unit,
    onDepartmentChange: (String) -> Unit,
    onHasOxygenOutletChange: (Boolean) -> Unit,
    onBedCountChange: (Int?) -> Unit,
    onAddressChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ═══════════════════════════════════════════════════════════════
        // SEZIONE: IDENTIFICAZIONE
        // ═══════════════════════════════════════════════════════════════
        item {
            SectionCard(title = "Identificazione") {
                // Nome (obbligatorio)
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = onNameChange,
                    label = { Text("Nome ubicazione *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    ),
                    isError = uiState.name.isBlank() && uiState.error != null
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Tipo (dropdown)
                LocationTypeDropdown(
                    selected = uiState.type,
                    onSelect = onTypeChange
                )
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // SEZIONE: GERARCHIA
        // ═══════════════════════════════════════════════════════════════
        item {
            SectionCard(title = "Gerarchia") {
                // Sede padre (dropdown)
                ParentLocationDropdown(
                    selected = uiState.parentId,
                    selectedName = uiState.parentName,
                    locations = uiState.availableParents,
                    onSelect = onParentChange
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Edificio (autocomplete)
                AutocompleteTextField(
                    value = uiState.building,
                    onValueChange = onBuildingChange,
                    suggestions = uiState.buildingSuggestions,
                    label = "Edificio",
                    placeholder = "Ala Vecchia, Ala Nuova..."
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Piano - codice e nome affiancati
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AutocompleteTextField(
                        value = uiState.floor,
                        onValueChange = onFloorChange,
                        suggestions = uiState.floorSuggestions,
                        label = "Codice Piano",
                        placeholder = "PT, P1, P-1",
                        modifier = Modifier.weight(0.4f),
                        capitalization = KeyboardCapitalization.Characters
                    )

                    AutocompleteTextField(
                        value = uiState.floorName,
                        onValueChange = onFloorNameChange,
                        suggestions = uiState.floorNameSuggestions,
                        label = "Nome Piano",
                        placeholder = "Piano Terra",
                        modifier = Modifier.weight(0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Reparto (autocomplete)
                AutocompleteTextField(
                    value = uiState.department,
                    onValueChange = onDepartmentChange,
                    suggestions = uiState.departmentSuggestions,
                    label = "Reparto/Area",
                    placeholder = "Degenza, Direzione, Cucina..."
                )
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // SEZIONE: CARATTERISTICHE
        // ═══════════════════════════════════════════════════════════════
        item {
            SectionCard(title = "Caratteristiche") {
                // Attacco ossigeno
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Attacco ossigeno",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uiState.hasOxygenOutlet,
                        onCheckedChange = onHasOxygenOutletChange
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Posti letto
                OutlinedTextField(
                    value = uiState.bedCount?.toString() ?: "",
                    onValueChange = { newValue ->
                        onBedCountChange(newValue.toIntOrNull())
                    },
                    label = { Text("Posti letto") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // SEZIONE: ALTRI DATI
        // ═══════════════════════════════════════════════════════════════
        item {
            SectionCard(title = "Altri dati") {
                // Indirizzo (per sedi esterne)
                OutlinedTextField(
                    value = uiState.address,
                    onValueChange = onAddressChange,
                    label = { Text("Indirizzo (sedi esterne)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Note
                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = onNotesChange,
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
            }
        }

        // Spazio in fondo
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Dropdown per selezione tipo ubicazione.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationTypeDropdown(
    selected: LocationType?,
    onSelect: (LocationType?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected?.label ?: "Seleziona tipo",
            onValueChange = {},
            readOnly = true,
            label = { Text("Tipo ubicazione") },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Opzione "Nessuno" per resettare
            DropdownMenuItem(
                text = { Text("Non specificato") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )

            LocationType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    }
                )
            }
        }
    }
}
```

### 7. Valori predefiniti per primo avvio

Per evitare suggerimenti vuoti al primo utilizzo, aggiungere costanti con valori comuni:

```kotlin
// File: app/src/main/java/org/incammino/hospiceinventory/domain/model/LocationDefaults.kt

object LocationDefaults {
    val COMMON_BUILDINGS = listOf(
        "Hospice Abbiategrasso",
        "Ala Vecchia",
        "Ala Nuova"
    )

    val COMMON_FLOORS = listOf(
        "P-1",  // Seminterrato
        "PT",   // Piano Terra
        "P1",   // Primo Piano
        "P2"    // Secondo Piano
    )

    val COMMON_FLOOR_NAMES = listOf(
        "Seminterrato",
        "Piano Terra",
        "Primo Piano",
        "Secondo Piano"
    )

    val COMMON_DEPARTMENTS = listOf(
        "Degenza",
        "Direzione",
        "Ambulatorio",
        "Day Hospital",
        "Cucina",
        "Magazzino"
    )
}
```

Poi nel ViewModel, unire i default con i valori dal DB:

```kotlin
private fun loadSuggestions() {
    viewModelScope.launch {
        try {
            val suggestions = locationRepository.getAllSuggestions()
            _uiState.update { state ->
                state.copy(
                    buildingSuggestions = (LocationDefaults.COMMON_BUILDINGS + suggestions.buildings).distinct(),
                    floorSuggestions = (LocationDefaults.COMMON_FLOORS + suggestions.floors).distinct(),
                    floorNameSuggestions = (LocationDefaults.COMMON_FLOOR_NAMES + suggestions.floorNames).distinct(),
                    departmentSuggestions = (LocationDefaults.COMMON_DEPARTMENTS + suggestions.departments).distinct()
                )
            }
        } catch (e: Exception) {
            // Fallback ai soli default
            _uiState.update { state ->
                state.copy(
                    buildingSuggestions = LocationDefaults.COMMON_BUILDINGS,
                    floorSuggestions = LocationDefaults.COMMON_FLOORS,
                    floorNameSuggestions = LocationDefaults.COMMON_FLOOR_NAMES,
                    departmentSuggestions = LocationDefaults.COMMON_DEPARTMENTS
                )
            }
        }
    }
}
```

## File da creare/modificare

| File | Azione |
|------|--------|
| `ui/components/AutocompleteTextField.kt` | CREARE |
| `domain/model/LocationDefaults.kt` | CREARE |
| `data/local/dao/LocationDao.kt` | MODIFICARE (aggiungere query DISTINCT) |
| `data/repository/LocationRepository.kt` | MODIFICARE (aggiungere metodi suggerimenti) |
| `ui/screens/location/LocationEditViewModel.kt` | MODIFICARE (nuovi campi, loadSuggestions) |
| `ui/screens/location/LocationEditScreen.kt` | MODIFICARE (nuovo form completo) |

## Test di validazione

1. **Autocomplete funzionante**: digitare "Ala" deve mostrare "Ala Vecchia", "Ala Nuova"
2. **Suggerimenti dal DB**: dopo aver salvato "Reparto Fisioterapia", deve apparire nei suggerimenti
3. **Selezione da dropdown**: click su suggerimento deve compilare il campo
4. **Case-insensitive**: "ala" deve matchare "Ala Vecchia"
5. **Modifica esistente**: aprendo una Location esistente, tutti i campi devono essere precompilati
6. **Salvataggio completo**: tutti i nuovi campi devono essere salvati nel DB

## Note implementative

- `MenuAnchorType.PrimaryEditable` per autocomplete (campo editabile)
- `MenuAnchorType.PrimaryNotEditable` per dropdown puri (campo readonly)
- Il filtro suggerimenti è case-insensitive
- Massimo 10 suggerimenti visibili per non sovraccaricare UI
- I default dell'Hospice sono hardcoded ma estendibili dai valori reali nel DB
