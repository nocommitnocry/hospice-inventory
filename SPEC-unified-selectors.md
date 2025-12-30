# Specifica Tecnica: Selettori Unificati per Categoria, Ubicazione e Fornitore

## 📋 Panoramica

### Problema
L'app presenta inconsistenze significative nei selettori per Categoria, Ubicazione e Fornitore:

| Campo | ProductEditScreen | ProductConfirmScreen |
|-------|-------------------|---------------------|
| **Categoria** | AutocompleteTextField (solo digitando) | Lista HARDCODED nel codice! |
| **Ubicazione** | AutocompleteTextField (solo digitando) | LocationMatch pattern (OK) |
| **Fornitore** | OutlinedTextField semplice (NIENTE!) | MaintainerMatch pattern (OK) |

### Obiettivo
1. Creare componente `SelectableDropdownField` riutilizzabile
2. Aggiungere query `getAllSuppliers()` al database
3. Unificare l'esperienza in tutte le schermate

### Filosofia
- L'utente deve poter **cliccare la freccia** per vedere TUTTI i valori disponibili
- L'utente deve poter **digitare** per filtrare o inserire valore nuovo
- I valori vengono dal **database**, mai hardcoded

---

## 🔧 Fase 1: Backend - Query Fornitori

### 1.1 ProductDao.kt
**Path:** `app/src/main/java/org/incammino/hospiceinventory/data/local/dao/ProductDao.kt`

**Aggiungere** nella sezione STATISTICHE (dopo `getAllLocations()`):

```kotlin
@Query("""
    SELECT DISTINCT supplier FROM products 
    WHERE isActive = 1 
    AND supplier IS NOT NULL 
    AND supplier != ''
    ORDER BY supplier
""")
fun getAllSuppliers(): Flow<List<String>>
```

### 1.2 ProductRepository.kt
**Path:** `app/src/main/java/org/incammino/hospiceinventory/data/repository/ProductRepository.kt`

**Aggiungere** nella sezione QUERY (dopo `getAllLocations()`):

```kotlin
/**
 * Tutti i fornitori distinti dai prodotti attivi.
 */
fun getAllSuppliers(): Flow<List<String>> = productDao.getAllSuppliers()
```

---

## 🎨 Fase 2: Componente UI Unificato

### 2.1 Nuovo file: SelectableDropdownField.kt
**Path:** `app/src/main/java/org/incammino/hospiceinventory/ui/components/SelectableDropdownField.kt`

```kotlin
package org.incammino.hospiceinventory.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/**
 * Campo con dropdown selezionabile E digitazione libera.
 * 
 * Comportamento:
 * - Click sulla freccia → mostra TUTTI i valori
 * - Digitazione → filtra i valori + permette valore nuovo
 * - Selezione da lista → imposta il valore
 * 
 * @param value Valore corrente
 * @param onValueChange Callback quando cambia il valore
 * @param suggestions Lista di suggerimenti dal database
 * @param label Etichetta del campo
 * @param isError Mostra stato errore
 * @param isRequired Aggiunge asterisco alla label
 * @param allowNewValues Se true, permette valori non in lista (default true)
 * @param maxSuggestions Numero massimo di suggerimenti visibili (default 10)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectableDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    isRequired: Boolean = false,
    allowNewValues: Boolean = true,
    maxSuggestions: Int = 10
) {
    var expanded by remember { mutableStateOf(false) }
    var textFieldValue by remember(value) { mutableStateOf(value) }
    
    // Filtra i suggerimenti in base al testo digitato
    val filteredSuggestions = remember(textFieldValue, suggestions) {
        if (textFieldValue.isBlank()) {
            suggestions.take(maxSuggestions)
        } else {
            suggestions.filter { 
                it.contains(textFieldValue, ignoreCase = true) 
            }.take(maxSuggestions)
        }
    }
    
    // Mostra dropdown se:
    // - expanded è true E
    // - ci sono suggerimenti da mostrare (o è lista completa)
    val shouldShowDropdown = expanded && (
        filteredSuggestions.isNotEmpty() || 
        (textFieldValue.isBlank() && suggestions.isNotEmpty())
    )

    ExposedDropdownMenuBox(
        expanded = shouldShowDropdown,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                onValueChange(newValue)
                expanded = true // Apri dropdown mentre digiti
            },
            label = { 
                Text(if (isRequired) "$label *" else label) 
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable),
            singleLine = true,
            isError = isError,
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Default.ArrowDropUp
                        } else {
                            Icons.Default.ArrowDropDown
                        },
                        contentDescription = if (expanded) {
                            "Chiudi suggerimenti"
                        } else {
                            "Mostra suggerimenti"
                        }
                    )
                }
            }
        )

        ExposedDropdownMenu(
            expanded = shouldShowDropdown,
            onDismissRequest = { expanded = false }
        ) {
            filteredSuggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        textFieldValue = suggestion
                        onValueChange(suggestion)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
            
            // Se il valore digitato non è nella lista e allowNewValues è true
            if (allowNewValues && 
                textFieldValue.isNotBlank() && 
                !suggestions.any { it.equals(textFieldValue, ignoreCase = true) }
            ) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { 
                        Text(
                            "Usa \"$textFieldValue\"",
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = {
                        onValueChange(textFieldValue)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
```

---

## 📝 Fase 3: Aggiornare ProductEditViewModel

### 3.1 ProductEditUiState
**Path:** `app/src/main/java/org/incammino/hospiceinventory/ui/screens/product/ProductEditViewModel.kt`

**Modificare** `ProductEditUiState` per aggiungere lista fornitori:

```kotlin
data class ProductEditUiState(
    // ... campi esistenti ...
    
    // Dati di supporto
    val categories: List<String> = emptyList(),
    val locations: List<String> = emptyList(),
    val suppliers: List<String> = emptyList(),  // AGGIUNGERE
    val maintainers: List<Maintainer> = emptyList()
)
```

### 3.2 loadSupportData()
**Modificare** la funzione `loadSupportData()` per caricare anche i fornitori:

```kotlin
private fun loadSupportData() {
    viewModelScope.launch {
        productRepository.getAllCategories().collect { categories ->
            _uiState.update { it.copy(categories = categories) }
        }
    }

    viewModelScope.launch {
        productRepository.getAllLocations().collect { locations ->
            _uiState.update { it.copy(locations = locations) }
        }
    }

    // AGGIUNGERE: Carica fornitori
    viewModelScope.launch {
        productRepository.getAllSuppliers().collect { suppliers ->
            _uiState.update { it.copy(suppliers = suppliers) }
        }
    }

    viewModelScope.launch {
        maintainerRepository.getAllActive().collect { maintainers ->
            _uiState.update { it.copy(maintainers = maintainers) }
        }
    }
}
```

---

## 🖼️ Fase 4: Aggiornare ProductEditScreen

### 4.1 Import del componente
**Path:** `app/src/main/java/org/incammino/hospiceinventory/ui/screens/product/ProductEditScreen.kt`

**Aggiungere** import:
```kotlin
import org.incammino.hospiceinventory.ui.components.SelectableDropdownField
```

### 4.2 Sezione "Informazioni base"
**Sostituire** i campi Categoria e Ubicazione con `SelectableDropdownField`:

**PRIMA (da rimuovere):**
```kotlin
// Categoria con autocomplete
AutocompleteTextField(
    value = uiState.category,
    onValueChange = onCategoryChange,
    suggestions = uiState.categories,
    label = stringResource(R.string.product_category) + " *",
    isError = uiState.category.isBlank() && uiState.error != null
)

Spacer(modifier = Modifier.height(12.dp))

// Ubicazione con autocomplete
AutocompleteTextField(
    value = uiState.location,
    onValueChange = onLocationChange,
    suggestions = uiState.locations,
    label = stringResource(R.string.product_location) + " *",
    isError = uiState.location.isBlank() && uiState.error != null
)
```

**DOPO (nuovo codice):**
```kotlin
// Categoria con dropdown selezionabile
SelectableDropdownField(
    value = uiState.category,
    onValueChange = onCategoryChange,
    suggestions = uiState.categories,
    label = stringResource(R.string.product_category),
    isRequired = true,
    isError = uiState.category.isBlank() && uiState.error != null
)

Spacer(modifier = Modifier.height(12.dp))

// Ubicazione con dropdown selezionabile
SelectableDropdownField(
    value = uiState.location,
    onValueChange = onLocationChange,
    suggestions = uiState.locations,
    label = stringResource(R.string.product_location),
    isRequired = true,
    isError = uiState.location.isBlank() && uiState.error != null
)
```

### 4.3 Sezione "Dati acquisto"
**Sostituire** il campo Fornitore:

**PRIMA (da rimuovere):**
```kotlin
OutlinedTextField(
    value = uiState.supplier,
    onValueChange = onSupplierChange,
    label = { Text("Fornitore") },
    modifier = Modifier.fillMaxWidth(),
    singleLine = true
)
```

**DOPO (nuovo codice):**
```kotlin
SelectableDropdownField(
    value = uiState.supplier,
    onValueChange = onSupplierChange,
    suggestions = uiState.suppliers,
    label = "Fornitore"
)
```

### 4.4 Rimuovere AutocompleteTextField (opzionale)
Se `AutocompleteTextField` non è più usato altrove, può essere rimosso dal file.
Verificare prima che non sia usato in altre schermate.

---

## 🎤 Fase 5: Aggiornare ProductConfirmScreen

### 5.1 Modificare ProductConfirmViewModel
**Path:** `app/src/main/java/org/incammino/hospiceinventory/ui/screens/voice/ProductConfirmViewModel.kt`

**Aggiungere** nel UiState (se non esiste già):
```kotlin
data class ProductConfirmUiState(
    // ... campi esistenti ...
    val categories: List<String> = emptyList()
)
```

**Aggiungere** nel ViewModel:
```kotlin
init {
    // ... codice esistente ...
    loadCategories()
}

private fun loadCategories() {
    viewModelScope.launch {
        productRepository.getAllCategories().collect { categories ->
            _uiState.update { it.copy(categories = categories) }
        }
    }
}
```

### 5.2 Modificare CategorySelector
**Path:** `app/src/main/java/org/incammino/hospiceinventory/ui/screens/voice/ProductConfirmScreen.kt`

**Aggiungere** import:
```kotlin
import org.incammino.hospiceinventory.ui.components.SelectableDropdownField
```

**Modificare** la firma di `CategorySelector` per ricevere la lista dal DB:

**PRIMA:**
```kotlin
@Composable
private fun CategorySelector(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val categories = listOf(  // HARDCODED!
        "Elettromedicale",
        "Arredo",
        "Informatica",
        "Impianto",
        "Attrezzatura",
        "Ausili",
        "Cucina",
        "Lavanderia",
        "Altro"
    )
    // ... resto del codice ...
}
```

**DOPO (sostituire l'intera funzione):**
```kotlin
@Composable
private fun CategorySelector(
    selectedCategory: String,
    categories: List<String>,  // Dal database!
    onCategorySelected: (String) -> Unit
) {
    // Lista di default se il DB è vuoto
    val effectiveCategories = categories.ifEmpty {
        listOf(
            "Elettromedicale",
            "Arredo", 
            "Informatica",
            "Impianto",
            "Attrezzatura",
            "Ausili",
            "Cucina",
            "Lavanderia",
            "Altro"
        )
    }
    
    SelectableDropdownField(
        value = selectedCategory,
        onValueChange = onCategorySelected,
        suggestions = effectiveCategories,
        label = "Categoria",
        isRequired = true,
        isError = selectedCategory.isBlank()
    )
}
```

### 5.3 Aggiornare la chiamata a CategorySelector
Nel body di `ProductConfirmScreen`, modificare la chiamata:

**PRIMA:**
```kotlin
CategorySelector(
    selectedCategory = category,
    onCategorySelected = { category = it }
)
```

**DOPO:**
```kotlin
CategorySelector(
    selectedCategory = category,
    categories = uiState.categories,  // Dal ViewModel
    onCategorySelected = { category = it }
)
```

---

## 📁 Fase 6: Riepilogo File da Modificare

| File | Tipo Modifica |
|------|---------------|
| `ProductDao.kt` | Aggiungere query `getAllSuppliers()` |
| `ProductRepository.kt` | Aggiungere metodo `getAllSuppliers()` |
| `SelectableDropdownField.kt` | **NUOVO FILE** - Componente riutilizzabile |
| `ProductEditViewModel.kt` | Aggiungere `suppliers` allo state + caricamento |
| `ProductEditScreen.kt` | Sostituire campi con `SelectableDropdownField` |
| `ProductConfirmViewModel.kt` | Aggiungere `categories` allo state + caricamento |
| `ProductConfirmScreen.kt` | Modificare `CategorySelector` per usare DB |

---

## ✅ Checklist di Test

### Test ProductEditScreen
- [ ] Campo Categoria: click freccia mostra tutte le categorie dal DB
- [ ] Campo Categoria: digitando filtra le categorie
- [ ] Campo Categoria: posso inserire categoria nuova
- [ ] Campo Ubicazione: click freccia mostra tutte le ubicazioni dal DB
- [ ] Campo Ubicazione: digitando filtra le ubicazioni
- [ ] Campo Fornitore: click freccia mostra tutti i fornitori dal DB
- [ ] Campo Fornitore: digitando filtra i fornitori
- [ ] Campo Fornitore: posso inserire fornitore nuovo

### Test ProductConfirmScreen (Registrazione vocale)
- [ ] Campo Categoria: mostra categorie dal DB (non hardcoded)
- [ ] Se DB vuoto, mostra lista di default
- [ ] Nuova categoria creata in ProductEdit appare anche qui

### Test di Regressione
- [ ] Salvataggio prodotto funziona con nuovi campi
- [ ] Modifica prodotto carica correttamente i valori esistenti
- [ ] Ricerca prodotti non è impattata
- [ ] Voice flow completo funziona

---

## 🎯 Risultato Atteso

### Prima
- Utente deve ricordarsi esattamente il nome della categoria
- Nessun modo di vedere cosa c'è nel database
- Fornitore senza suggerimenti
- Lista categorie diversa tra Edit e Confirm

### Dopo
- Click sulla freccia → vedo TUTTO
- Digito → filtro veloce
- Posso comunque inserire valore nuovo
- Esperienza consistente ovunque
- Dati sempre dal database

---

## 📝 Note Implementative

1. **MenuAnchorType**: Usare `PrimaryEditable` (non `PrimaryNotEditable`) perché il campo è editabile

2. **remember(value)**: Il `textFieldValue` interno deve essere sincronizzato con `value` esterno

3. **Fallback categorie**: In ProductConfirmScreen manteniamo lista di default per primo avvio con DB vuoto

4. **Performance**: `filteredSuggestions` è calcolato con `remember` per evitare ricalcoli inutili

5. **UX**: L'opzione "Usa valore nuovo" appare solo se il testo non corrisponde a nessun suggerimento
