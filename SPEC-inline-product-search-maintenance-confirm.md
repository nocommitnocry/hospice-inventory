# Specifica: Ricerca Inline Prodotto in MaintenanceConfirmScreen

**Data:** 30/12/2025  
**Priorità:** Alta (workflow bloccante)  
**Complessità:** Media  

---

## 1. Problema

Quando l'utente registra una manutenzione via Voice Dump e Gemini non riconosce correttamente il prodotto, può cliccare l'icona 🔍 per cercarlo manualmente. Attualmente questa azione naviga a `SearchScreen`, che poi porta a `ProductDetail`, **perdendo tutti i dati già compilati** (fornitore, descrizione, durata, data, note).

### Flusso attuale (problematico)

```
MaintenanceConfirmScreen → [click 🔍] → SearchScreen → ProductDetail
                                        ↑
                                    DATI PERSI 💥
```

### Flusso desiderato

```
MaintenanceConfirmScreen
    └── [click 🔍] → espande campo ricerca inline
    └── [digita] → mostra risultati sotto
    └── [click risultato] → seleziona prodotto
    └── DATI PRESERVATI ✅
```

---

## 2. Soluzione

Replicare il pattern già esistente in `MaintenanceEditScreen` con `ProductSearchField`:
1. Aggiungere logica di ricerca in `MaintenanceConfirmViewModel`
2. Modificare `ProductSelectionCard` per includere ricerca inline
3. Rimuovere navigazione esterna a `SearchScreen`

---

## 3. Modifiche Richieste

### 3.1 MaintenanceConfirmViewModel.kt

**Aggiungere:** Stati e funzioni per la ricerca prodotto.

```kotlin
// ═══════════════════════════════════════════════════════════════════════════
// RICERCA PRODOTTO INLINE
// ═══════════════════════════════════════════════════════════════════════════

private val _productSearchQuery = MutableStateFlow("")
val productSearchQuery: StateFlow<String> = _productSearchQuery.asStateFlow()

private val _productSearchResults = MutableStateFlow<List<Product>>(emptyList())
val productSearchResults: StateFlow<List<Product>> = _productSearchResults.asStateFlow()

/**
 * Aggiorna la query di ricerca prodotto.
 * Avvia la ricerca automaticamente se query >= 2 caratteri.
 */
fun updateProductSearchQuery(query: String) {
    _productSearchQuery.value = query
    if (query.length >= 2) {
        searchProducts(query)
    } else {
        _productSearchResults.value = emptyList()
    }
}

/**
 * Esegue la ricerca prodotti nel repository.
 */
private fun searchProducts(query: String) {
    viewModelScope.launch {
        try {
            val results = productRepository.searchSync(query)
            _productSearchResults.value = results
        } catch (e: Exception) {
            Log.e(TAG, "Product search failed", e)
            _productSearchResults.value = emptyList()
        }
    }
}

/**
 * Pulisce la ricerca prodotto (query e risultati).
 */
fun clearProductSearch() {
    _productSearchQuery.value = ""
    _productSearchResults.value = emptyList()
}
```

**Aggiungere:** Iniezione `ProductRepository` nel costruttore:

```kotlin
@HiltViewModel
class MaintenanceConfirmViewModel @Inject constructor(
    private val maintenanceRepository: MaintenanceRepository,
    private val maintainerRepository: MaintainerRepository,
    private val productRepository: ProductRepository,  // ← AGGIUNGERE
    private val geminiService: GeminiService,
    private val voiceService: VoiceService
) : ViewModel() {
```

**Import necessario:**
```kotlin
import org.incammino.hospiceinventory.data.repository.ProductRepository
```

---

### 3.2 MaintenanceConfirmScreen.kt

#### 3.2.1 Raccogliere i nuovi stati dal ViewModel

```kotlin
// Dentro MaintenanceConfirmScreen, dopo gli altri collectAsState
val productSearchQuery by viewModel.productSearchQuery.collectAsState()
val productSearchResults by viewModel.productSearchResults.collectAsState()
```

#### 3.2.2 Modificare ProductSelectionCard

**Prima (con navigazione esterna):**
```kotlin
ProductSelectionCard(
    match = selectedProduct,
    onSearchClick = onNavigateToProductSearch,  // ← NAVIGA VIA
    onSelect = { selectedProduct = ProductMatch.Found(it) }
)
```

**Dopo (con ricerca inline):**
```kotlin
ProductSelectionCard(
    match = selectedProduct,
    searchQuery = productSearchQuery,
    searchResults = productSearchResults,
    onSearchQueryChange = { viewModel.updateProductSearchQuery(it) },
    onSelect = { product ->
        selectedProduct = ProductMatch.Found(product)
        viewModel.clearProductSearch()
    }
)
```

#### 3.2.3 Riscrivere ProductSelectionCard Composable

Sostituire completamente la funzione `ProductSelectionCard`:

```kotlin
/**
 * Card per la selezione del prodotto con ricerca inline.
 * 
 * Stati supportati:
 * - Found: prodotto identificato, mostra nome con ✓
 * - Ambiguous: più candidati, mostra lista per selezione
 * - NotFound: non trovato, mostra campo di ricerca espanso
 * 
 * La ricerca inline evita di perdere i dati già compilati
 * navigando ad altre schermate.
 */
@Composable
private fun ProductSelectionCard(
    match: ProductMatch,
    searchQuery: String,
    searchResults: List<Product>,
    onSearchQueryChange: (String) -> Unit,
    onSelect: (Product) -> Unit
) {
    var showSearch by remember { mutableStateOf(match is ProductMatch.NotFound) }
    
    // Se il match cambia a Found, nascondi la ricerca
    LaunchedEffect(match) {
        if (match is ProductMatch.Found) {
            showSearch = false
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header con titolo e toggle ricerca
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Prodotto *",
                    style = MaterialTheme.typography.labelLarge
                )
                // Mostra icona ricerca solo se non è già Found
                if (match !is ProductMatch.Found) {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(
                            if (showSearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (showSearch) "Chiudi ricerca" else "Cerca prodotto"
                        )
                    }
                } else {
                    // Se Found, permetti di cambiare prodotto
                    IconButton(onClick = { showSearch = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Cambia prodotto")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Contenuto basato sullo stato
            when {
                // Ricerca attiva
                showSearch -> {
                    ProductSearchField(
                        query = searchQuery,
                        results = searchResults,
                        onQueryChange = onSearchQueryChange,
                        onProductSelect = onSelect
                    )
                }
                
                // Prodotto trovato
                match is ProductMatch.Found -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                match.product.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            match.product.location?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Risultati ambigui - mostra candidati
                match is ProductMatch.Ambiguous -> {
                    Text(
                        "Trovati ${match.candidates.size} risultati per \"${match.searchTerms}\":",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    match.candidates.take(5).forEach { product ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(product) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = false,
                                onClick = { onSelect(product) }
                            )
                            Column {
                                Text(product.name)
                                product.location?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    
                    // Link per cercare manualmente se i candidati non vanno bene
                    TextButton(onClick = { showSearch = true }) {
                        Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cerca altro prodotto")
                    }
                }

                // Non trovato
                match is ProductMatch.NotFound -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        if (match.searchTerms.isNotBlank()) {
                            Text(
                                "\"${match.searchTerms}\" non trovato",
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                "Nessun prodotto specificato",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Campo di ricerca prodotto con risultati dropdown.
 * Pattern identico a MaintenanceEditScreen.
 */
@Composable
private fun ProductSearchField(
    query: String,
    results: List<Product>,
    onQueryChange: (String) -> Unit,
    onProductSelect: (Product) -> Unit
) {
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Cerca prodotto...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Pulisci")
                    }
                }
            }
        )

        // Risultati ricerca
        if (results.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column {
                    results.take(5).forEach { product ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = product.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Row {
                                    Text(
                                        text = product.category,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    product.location?.let { loc ->
                                        Text(
                                            text = " • $loc",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            leadingContent = {
                                Icon(Icons.Default.Inventory2, contentDescription = null)
                            },
                            modifier = Modifier.clickable { onProductSelect(product) }
                        )
                        if (product != results.take(5).last()) {
                            HorizontalDivider()
                        }
                    }
                    
                    // Indicatore se ci sono più risultati
                    if (results.size > 5) {
                        Text(
                            text = "... e altri ${results.size - 5} risultati",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        } else if (query.length >= 2) {
            // Query inserita ma nessun risultato
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Nessun prodotto trovato per \"$query\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

#### 3.2.4 Import necessari

Aggiungere a MaintenanceConfirmScreen.kt:
```kotlin
import androidx.compose.material3.ListItem
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.LaunchedEffect
```

---

### 3.3 Navigation.kt

**Rimuovere** il parametro `onNavigateToProductSearch` dalla chiamata a `MaintenanceConfirmScreen`:

**Prima:**
```kotlin
MaintenanceConfirmScreen(
    initialData = data,
    onNavigateBack = { ... },
    onSaved = { ... },
    onNavigateToProductSearch = {
        navController.navigate(Screen.Search.createRoute(""))
    }
)
```

**Dopo:**
```kotlin
MaintenanceConfirmScreen(
    initialData = data,
    onNavigateBack = { ... },
    onSaved = { ... }
    // onNavigateToProductSearch RIMOSSO
)
```

---

### 3.4 Signature MaintenanceConfirmScreen

Aggiornare la firma della funzione rimuovendo il parametro:

**Prima:**
```kotlin
@Composable
fun MaintenanceConfirmScreen(
    initialData: MaintenanceConfirmData,
    viewModel: MaintenanceConfirmViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    onNavigateToProductSearch: () -> Unit  // ← RIMUOVERE
)
```

**Dopo:**
```kotlin
@Composable
fun MaintenanceConfirmScreen(
    initialData: MaintenanceConfirmData,
    viewModel: MaintenanceConfirmViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit
)
```

---

## 4. Test di Validazione

### 4.1 Scenario Base - Ricerca Manuale

1. Avvia registrazione manutenzione vocale
2. Descrivi: "Ho riparato il... ehm... coso in camera 5"
3. Verifica che in MaintenanceConfirmScreen il prodotto sia "Non trovato"
4. Clicca icona 🔍
5. Digita "letto" nel campo di ricerca
6. Verifica che appaiano risultati
7. Seleziona "Letto elettrico camera 5"
8. Verifica che il prodotto sia ora "Found" con ✓
9. **Verifica che gli altri dati (descrizione, durata) siano preservati**
10. Salva con successo

### 4.2 Scenario - Cambio Prodotto Già Selezionato

1. Registra manutenzione con prodotto riconosciuto correttamente
2. In MaintenanceConfirmScreen, clicca icona ✏️ (edit) sul prodotto
3. Cerca e seleziona un prodotto diverso
4. Verifica che il cambio sia avvenuto
5. Verifica che gli altri dati siano preservati

### 4.3 Scenario - Selezione da Candidati Ambigui + Ricerca

1. Registra manutenzione con nome prodotto ambiguo (es. "condizionatore")
2. Verifica che appaiano i candidati
3. Se nessun candidato è corretto, clicca "Cerca altro prodotto"
4. Cerca e seleziona il prodotto giusto
5. Verifica preservazione dati

### 4.4 Scenario - Nessun Risultato

1. In ricerca inline, digita "xyzabc123"
2. Verifica messaggio "Nessun prodotto trovato per xyzabc123"
3. Pulisci e cerca un termine valido
4. Verifica che funzioni

---

## 5. Note Implementative

### 5.1 Debounce

La ricerca in `MaintenanceEditViewModel` NON usa debounce esplicito. Per consistenza, anche qui non lo usiamo. Se si volesse aggiungere in futuro:

```kotlin
private val _productSearchQuery = MutableStateFlow("")

init {
    viewModelScope.launch {
        _productSearchQuery
            .debounce(300)
            .filter { it.length >= 2 }
            .collectLatest { query ->
                searchProducts(query)
            }
    }
}
```

### 5.2 Gestione Focus

Il campo di ricerca dovrebbe ricevere focus automaticamente quando `showSearch` diventa true. Opzionale:

```kotlin
val focusRequester = remember { FocusRequester() }

LaunchedEffect(showSearch) {
    if (showSearch) {
        focusRequester.requestFocus()
    }
}

OutlinedTextField(
    modifier = Modifier
        .fillMaxWidth()
        .focusRequester(focusRequester),
    // ...
)
```

### 5.3 Scroll dei Risultati

Se i risultati fossero molti, potrebbe essere utile limitare l'altezza del dropdown. Attualmente limitiamo a 5 risultati, che è sufficiente.

---

## 6. Checklist Implementazione

- [ ] Aggiungere `ProductRepository` a `MaintenanceConfirmViewModel`
- [ ] Aggiungere stati `productSearchQuery` e `productSearchResults`
- [ ] Aggiungere funzioni `updateProductSearchQuery`, `searchProducts`, `clearProductSearch`
- [ ] Modificare signature `MaintenanceConfirmScreen` (rimuovere `onNavigateToProductSearch`)
- [ ] Aggiornare chiamata in `Navigation.kt`
- [ ] Riscrivere `ProductSelectionCard` con ricerca inline
- [ ] Aggiungere `ProductSearchField` composable
- [ ] Aggiungere import necessari
- [ ] Build e test su device


