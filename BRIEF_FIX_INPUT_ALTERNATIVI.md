# BRIEF: Fix e Input Alternativi nelle Schermate

**Data**: 30 Dicembre 2024  
**Priorità**: ALTA  
**Stima**: 2-3 sessioni Claude Code

---

## Obiettivo

Risolvere un bug critico e migliorare l'UX permettendo input alternativi (voce e barcode scanner) nelle schermate di conferma, eliminando la necessità di tornare indietro per aggiungere informazioni.

---

## Riepilogo Interventi

| # | Tipo | Descrizione | Priorità |
|---|------|-------------|----------|
| 1 | 🐛 Bug | SearchScreen TextField non accetta input | CRITICA |
| 2 | ✨ Feature | VoiceContinueButton in tutte le ConfirmScreen | ALTA |
| 3 | ✨ Feature | Integrazione Barcode Scanner | ALTA |

---

## 1. 🐛 BUG FIX: SearchScreen TextField

### Problema

Il TextField nella SearchScreen non accetta input. L'utente può vedere i prodotti ma non può digitare per filtrare.

### Causa Root

In `SearchViewModel.kt` c'è un disallineamento tra `_query` (MutableStateFlow interno) e `uiState.query` (quello mostrato nel TextField).

```kotlin
// PROBLEMA: updateQuery aggiorna _query ma NON uiState.query
fun updateQuery(query: String) {
    _query.value = query  // ✅ aggiorna flow interno
    // ❌ uiState.query resta vecchio fino al debounce!
}
```

Il TextField legge `uiState.query`, che viene aggiornato solo dopo 300ms di debounce in `setupSearch()`. Nel frattempo il TextField mostra il valore vecchio.

### Fix

**File**: `app/src/main/java/org/incammino/hospiceinventory/ui/screens/search/SearchViewModel.kt`

```kotlin
/**
 * Aggiorna la query di ricerca.
 */
fun updateQuery(query: String) {
    _query.value = query
    _uiState.update { it.copy(query = query) }  // ← AGGIUNGERE
}

/**
 * Seleziona/deseleziona una categoria.
 */
fun toggleCategory(category: String) {
    val newCategory = if (_selectedCategory.value == category) null else category
    _selectedCategory.value = newCategory
    _uiState.update { it.copy(selectedCategory = newCategory) }  // ← AGGIUNGERE
}

/**
 * Pulisce la ricerca.
 */
fun clearSearch() {
    _query.value = ""
    _selectedCategory.value = null
    _uiState.update { it.copy(query = "", selectedCategory = null) }  // ← AGGIUNGERE
}
```

### Test

1. Aprire SearchScreen
2. Toccare il campo di ricerca
3. Digitare "Aspir" → Deve filtrare mostrando "Aspiratore chirurgico"
4. Toccare X per cancellare → Campo deve svuotarsi
5. Selezionare filtro categoria → Deve filtrare correttamente

---

## 2. ✨ FEATURE: VoiceContinueButton

### Problema UX

Quando l'utente è in una ConfirmScreen (es. ProductConfirmScreen) e vuole aggiungere informazioni a voce, deve tornare alla schermata precedente. Questo è friction inutile.

### Soluzione

Aggiungere un bottone "Aggiungi a voce" in tutte le ConfirmScreen che permette input vocale incrementale.

### 2.1 Creare Componente Riutilizzabile

**File DA CREARE**: `app/src/main/java/org/incammino/hospiceinventory/ui/components/voice/VoiceContinueButton.kt`

```kotlin
package org.incammino.hospiceinventory.ui.components.voice

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Stato del bottone vocale.
 */
enum class VoiceContinueState {
    Idle,
    Listening,
    Processing
}

/**
 * Bottone per continuare l'input vocale nelle schermate di conferma.
 * Permette all'utente di aggiungere dettagli senza tornare indietro.
 *
 * @param state Stato attuale del bottone
 * @param onTap Callback quando l'utente tocca il bottone
 * @param partialTranscript Testo parziale durante l'ascolto (opzionale)
 * @param modifier Modifier per il componente
 */
@Composable
fun VoiceContinueButton(
    state: VoiceContinueState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    partialTranscript: String? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedButton(
            onClick = onTap,
            enabled = state != VoiceContinueState.Processing,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = when (state) {
                    VoiceContinueState.Listening -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            when (state) {
                VoiceContinueState.Processing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Elaborazione...")
                }
                VoiceContinueState.Listening -> {
                    Icon(
                        Icons.Default.MicOff,
                        contentDescription = "Stop",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Tocca per terminare")
                }
                VoiceContinueState.Idle -> {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Parla",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Aggiungi dettagli a voce")
                }
            }
        }

        // Mostra trascrizione parziale durante l'ascolto
        if (state == VoiceContinueState.Listening && !partialTranscript.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = partialTranscript,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
```

### 2.2 Aggiungere Logica ai ViewModel

Ogni ConfirmViewModel deve avere la capacità di:
1. Avviare/fermare l'ascolto vocale
2. Processare l'input con Gemini
3. Aggiornare i campi della form

**Pattern da applicare a tutti i ConfirmViewModel:**

```kotlin
// Aggiungere a ProductConfirmViewModel, MaintenanceConfirmViewModel, 
// MaintainerConfirmViewModel, LocationConfirmViewModel

// === IMPORTS DA AGGIUNGERE ===
import org.incammino.hospiceinventory.service.voice.VoiceService
import org.incammino.hospiceinventory.service.voice.VoiceState
import org.incammino.hospiceinventory.ui.components.voice.VoiceContinueState

// === INJECTION: Aggiungere VoiceService e GeminiService al constructor ===
@HiltViewModel
class ProductConfirmViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val locationRepository: LocationRepository,
    private val maintainerRepository: MaintainerRepository,
    private val voiceService: VoiceService,      // ← AGGIUNGERE
    private val geminiService: GeminiService     // ← AGGIUNGERE (se non presente)
) : ViewModel() {

    // === STATI VOCALI DA AGGIUNGERE ===
    
    private val _voiceContinueState = MutableStateFlow(VoiceContinueState.Idle)
    val voiceContinueState: StateFlow<VoiceContinueState> = _voiceContinueState.asStateFlow()
    
    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    // === INIT: Osservare VoiceService ===
    
    init {
        observeVoiceState()
    }
    
    private fun observeVoiceState() {
        viewModelScope.launch {
            voiceService.state.collect { state ->
                when (state) {
                    is VoiceState.Idle -> {
                        _voiceContinueState.value = VoiceContinueState.Idle
                        _partialTranscript.value = ""
                    }
                    is VoiceState.Listening -> {
                        _voiceContinueState.value = VoiceContinueState.Listening
                    }
                    is VoiceState.Processing -> {
                        _voiceContinueState.value = VoiceContinueState.Processing
                    }
                    is VoiceState.PartialResult -> {
                        _partialTranscript.value = state.text
                    }
                    is VoiceState.Result -> {
                        processAdditionalVoiceInput(state.text)
                    }
                    is VoiceState.Error -> {
                        _voiceContinueState.value = VoiceContinueState.Idle
                        // Opzionale: mostrare errore
                    }
                }
            }
        }
    }

    // === FUNZIONI VOCALI DA AGGIUNGERE ===
    
    /**
     * Toggle ascolto vocale.
     */
    fun toggleVoiceInput() {
        when (_voiceContinueState.value) {
            VoiceContinueState.Idle -> voiceService.startListening()
            VoiceContinueState.Listening -> voiceService.stopListening()
            VoiceContinueState.Processing -> { /* Ignora */ }
        }
    }
    
    /**
     * Processa input vocale aggiuntivo e aggiorna i campi.
     */
    private fun processAdditionalVoiceInput(transcript: String) {
        viewModelScope.launch {
            _voiceContinueState.value = VoiceContinueState.Processing
            
            try {
                // Costruisci contesto con dati attuali
                val currentData = buildCurrentDataContext()
                
                // Chiedi a Gemini di aggiornare
                val updatedData = geminiService.updateProductFromVoice(
                    currentData = currentData,
                    newInput = transcript
                )
                
                // Applica aggiornamenti ai campi
                applyVoiceUpdates(updatedData)
                
            } catch (e: Exception) {
                Log.e(TAG, "Errore processing voice input", e)
            } finally {
                _voiceContinueState.value = VoiceContinueState.Idle
            }
        }
    }
    
    /**
     * Costruisce contesto dei dati attuali per Gemini.
     */
    private fun buildCurrentDataContext(): String {
        // Implementare per ogni ViewModel in base ai campi specifici
        return """
            Nome: ${currentName.value}
            Categoria: ${currentCategory.value}
            Ubicazione: ${currentLocation.value}
            Barcode: ${currentBarcode.value}
            ... altri campi ...
        """.trimIndent()
    }
    
    /**
     * Applica gli aggiornamenti ricevuti da Gemini.
     */
    private fun applyVoiceUpdates(updates: Map<String, String>) {
        // Implementare per ogni ViewModel in base ai campi specifici
        updates["name"]?.let { updateName(it) }
        updates["category"]?.let { updateCategory(it) }
        updates["location"]?.let { updateLocation(it) }
        // ... altri campi ...
    }
}
```

### 2.3 Aggiungere Funzione a GeminiService

**File**: `app/src/main/java/org/incammino/hospiceinventory/service/voice/GeminiService.kt`

```kotlin
/**
 * Aggiorna dati prodotto con nuovo input vocale.
 * 
 * @param currentData Dati attuali del prodotto in formato stringa
 * @param newInput Nuovo input vocale dell'utente
 * @return Mappa con i campi da aggiornare
 */
suspend fun updateProductFromVoice(
    currentData: String,
    newInput: String
): Map<String, String> {
    val prompt = """
        Sei un assistente per l'inventario di un hospice italiano.
        
        L'utente sta completando la registrazione di un prodotto.
        
        DATI ATTUALI:
        $currentData
        
        L'UTENTE HA AGGIUNTO:
        "$newInput"
        
        Analizza cosa ha detto l'utente e restituisci SOLO i campi da aggiornare in formato JSON.
        
        Campi possibili: name, category, location, barcode, description, supplier, 
        warrantyEndDate (formato YYYY-MM-DD), maintenanceFrequency, notes
        
        Se l'utente menziona una categoria, usa una di queste: 
        Elettromedicali, Arredi sanitari, Climatizzazione, Elettrico, Idraulica
        
        Esempio risposta:
        {"category": "Elettromedicali", "location": "Piano 1 - Stanza 105"}
        
        Rispondi SOLO con il JSON, nient'altro.
    """.trimIndent()
    
    val response = generateContent(prompt)
    return parseJsonToMap(response)
}

/**
 * Aggiorna dati manutenzione con nuovo input vocale.
 */
suspend fun updateMaintenanceFromVoice(
    currentData: String,
    newInput: String
): Map<String, String> {
    val prompt = """
        Sei un assistente per l'inventario di un hospice italiano.
        
        L'utente sta registrando un intervento di manutenzione.
        
        DATI ATTUALI:
        $currentData
        
        L'UTENTE HA AGGIUNTO:
        "$newInput"
        
        Analizza cosa ha detto e restituisci SOLO i campi da aggiornare in formato JSON.
        
        Campi possibili: productName, maintainerName, type, outcome, notes, 
        durationMinutes, date (formato YYYY-MM-DD)
        
        Tipi manutenzione: ORDINARY, CORRECTIVE, VERIFICATION, CALIBRATION, CLEANING, OTHER
        Esiti: COMPLETED, PARTIAL, FAILED, NEEDS_PARTS, NEEDS_FOLLOWUP
        
        Rispondi SOLO con il JSON.
    """.trimIndent()
    
    val response = generateContent(prompt)
    return parseJsonToMap(response)
}

/**
 * Aggiorna dati manutentore con nuovo input vocale.
 */
suspend fun updateMaintainerFromVoice(
    currentData: String,
    newInput: String
): Map<String, String> {
    val prompt = """
        L'utente sta registrando un manutentore/fornitore.
        
        DATI ATTUALI:
        $currentData
        
        L'UTENTE HA AGGIUNTO:
        "$newInput"
        
        Campi possibili: name, email, phone, address, city, postalCode, province,
        vatNumber, contactPerson, specialization, isSupplier (true/false), notes
        
        Per la partita IVA, rimuovi spazi e formatta come 11 cifre.
        Per il telefono, rimuovi spazi extra.
        
        Rispondi SOLO con JSON dei campi da aggiornare.
    """.trimIndent()
    
    val response = generateContent(prompt)
    return parseJsonToMap(response)
}

/**
 * Aggiorna dati ubicazione con nuovo input vocale.
 */
suspend fun updateLocationFromVoice(
    currentData: String,
    newInput: String
): Map<String, String> {
    val prompt = """
        L'utente sta registrando un'ubicazione.
        
        DATI ATTUALI:
        $currentData
        
        L'UTENTE HA AGGIUNTO:
        "$newInput"
        
        Campi possibili: name, type, buildingName, floorCode, floorName, 
        department, hasOxygenOutlet (true/false), bedCount, notes
        
        Tipi ubicazione: BUILDING, FLOOR, ROOM, AREA
        
        Rispondi SOLO con JSON dei campi da aggiornare.
    """.trimIndent()
    
    val response = generateContent(prompt)
    return parseJsonToMap(response)
}

/**
 * Helper per parsing JSON response.
 */
private fun parseJsonToMap(jsonString: String): Map<String, String> {
    return try {
        // Pulisci la risposta (rimuovi markdown se presente)
        val cleaned = jsonString
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        
        // Parse con kotlinx.serialization o manualmente
        Json.decodeFromString<Map<String, String>>(cleaned)
    } catch (e: Exception) {
        Log.e(TAG, "Errore parsing JSON: $jsonString", e)
        emptyMap()
    }
}
```

### 2.4 Aggiungere Bottone alle ConfirmScreen

**Pattern da applicare a tutte le ConfirmScreen:**

```kotlin
// In ogni ConfirmScreen (Product, Maintenance, Maintainer, Location)

@Composable
fun ProductConfirmScreen(
    // ... parametri esistenti ...
    viewModel: ProductConfirmViewModel = hiltViewModel()
) {
    val voiceContinueState by viewModel.voiceContinueState.collectAsState()
    val partialTranscript by viewModel.partialTranscript.collectAsState()
    
    // ... UI esistente ...
    
    // AGGIUNGERE prima dei bottoni Annulla/Salva:
    Spacer(Modifier.height(16.dp))
    
    VoiceContinueButton(
        state = voiceContinueState,
        onTap = viewModel::toggleVoiceInput,
        partialTranscript = partialTranscript,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    
    Spacer(Modifier.height(16.dp))
    
    // Bottoni Annulla/Salva esistenti...
}
```

---

## 3. ✨ FEATURE: Integrazione Barcode Scanner

### Contesto

Lo `ScannerScreen` esiste già con ML Kit integrato. Dobbiamo collegarlo ai flussi esistenti.

### 3.1 Caso d'Uso: Ricerca Prodotto da Barcode

**Flusso:**
```
HomeScreen [Scansiona] → ScannerScreen → Barcode rilevato
    ↓
Cerca in DB
    ↓
├── TROVATO → ProductDetailScreen
└── NON TROVATO → Dialog "Vuoi creare questo prodotto?"
        ├── Sì → ProductConfirmScreen (barcode precompilato)
        └── No → Torna a Home
```

**File da modificare**: `app/src/main/java/org/incammino/hospiceinventory/ui/navigation/Navigation.kt`

Aggiungere callback e logica in ScannerScreen per gestire il risultato:

```kotlin
// Nel NavHost, modificare la destinazione scanner:

composable(Screen.Scanner.route) {
    ScannerScreen(
        onNavigateBack = { navController.popBackStack() },
        onBarcodeScanned = { barcode ->
            // Naviga a una schermata di "risoluzione" barcode
            navController.navigate("barcode_result/$barcode")
        }
    )
}

// Aggiungere nuova destinazione per risultato barcode:
composable(
    route = "barcode_result/{barcode}",
    arguments = listOf(navArgument("barcode") { type = NavType.StringType })
) { backStackEntry ->
    val barcode = backStackEntry.arguments?.getString("barcode") ?: ""
    BarcodeResultScreen(
        barcode = barcode,
        onNavigateBack = { navController.popBackStack() },
        onNavigateToProduct = { productId ->
            navController.navigate(Screen.ProductDetail.createRoute(productId))
        },
        onNavigateToCreateProduct = { barcodeValue ->
            // Passa barcode a VoiceProductScreen o direttamente a ProductConfirmScreen
            navController.navigate("voice_product?barcode=$barcodeValue")
        }
    )
}
```

**File DA CREARE**: `app/src/main/java/org/incammino/hospiceinventory/ui/screens/scanner/BarcodeResultScreen.kt`

```kotlin
package org.incammino.hospiceinventory.ui.screens.scanner

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Schermata che mostra il risultato della scansione barcode.
 * Cerca il prodotto nel DB e offre azioni appropriate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeResultScreen(
    barcode: String,
    onNavigateBack: () -> Unit,
    onNavigateToProduct: (String) -> Unit,
    onNavigateToCreateProduct: (String) -> Unit,
    viewModel: BarcodeResultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(barcode) {
        viewModel.searchByBarcode(barcode)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Risultato Scansione") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {
                is BarcodeResultState.Loading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Ricerca in corso...")
                    }
                }
                
                is BarcodeResultState.Found -> {
                    // Prodotto trovato - naviga automaticamente o mostra preview
                    LaunchedEffect(state.product.id) {
                        onNavigateToProduct(state.product.id)
                    }
                }
                
                is BarcodeResultState.NotFound -> {
                    NotFoundContent(
                        barcode = barcode,
                        onCreateProduct = { onNavigateToCreateProduct(barcode) },
                        onCancel = onNavigateBack
                    )
                }
                
                is BarcodeResultState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = { viewModel.searchByBarcode(barcode) },
                        onCancel = onNavigateBack
                    )
                }
            }
        }
    }
}

@Composable
private fun NotFoundContent(
    barcode: String,
    onCreateProduct: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                "Prodotto non trovato",
                style = MaterialTheme.typography.headlineSmall
            )
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                "Nessun prodotto con barcode\n$barcode",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(24.dp))
            
            Button(
                onClick = onCreateProduct,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Crea nuovo prodotto")
            }
            
            Spacer(Modifier.height(8.dp))
            
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Annulla")
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(Modifier.height(16.dp))
        
        Text(message, textAlign = TextAlign.Center)
        
        Spacer(Modifier.height(24.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel) {
                Text("Annulla")
            }
            Button(onClick = onRetry) {
                Text("Riprova")
            }
        }
    }
}
```

**File DA CREARE**: `app/src/main/java/org/incammino/hospiceinventory/ui/screens/scanner/BarcodeResultViewModel.kt`

```kotlin
package org.incammino.hospiceinventory.ui.screens.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.incammino.hospiceinventory.data.repository.ProductRepository
import org.incammino.hospiceinventory.domain.model.Product
import javax.inject.Inject

sealed class BarcodeResultState {
    data object Loading : BarcodeResultState()
    data class Found(val product: Product) : BarcodeResultState()
    data class NotFound(val barcode: String) : BarcodeResultState()
    data class Error(val message: String) : BarcodeResultState()
}

@HiltViewModel
class BarcodeResultViewModel @Inject constructor(
    private val productRepository: ProductRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<BarcodeResultState>(BarcodeResultState.Loading)
    val uiState: StateFlow<BarcodeResultState> = _uiState.asStateFlow()
    
    fun searchByBarcode(barcode: String) {
        viewModelScope.launch {
            _uiState.value = BarcodeResultState.Loading
            
            try {
                val product = productRepository.getByBarcode(barcode)
                
                _uiState.value = if (product != null) {
                    BarcodeResultState.Found(product)
                } else {
                    BarcodeResultState.NotFound(barcode)
                }
            } catch (e: Exception) {
                _uiState.value = BarcodeResultState.Error(
                    "Errore nella ricerca: ${e.message}"
                )
            }
        }
    }
}
```

### 3.2 Aggiungere Query al ProductRepository

**File**: `app/src/main/java/org/incammino/hospiceinventory/data/repository/ProductRepository.kt`

```kotlin
/**
 * Cerca prodotto per barcode (esatto).
 */
suspend fun getByBarcode(barcode: String): Product? {
    return productDao.getByBarcode(barcode)?.toDomain()
}
```

**File**: `app/src/main/java/org/incammino/hospiceinventory/data/local/dao/ProductDao.kt`

```kotlin
@Query("SELECT * FROM products WHERE barcode = :barcode AND isActive = 1 LIMIT 1")
suspend fun getByBarcode(barcode: String): ProductEntity?
```

### 3.3 Caso d'Uso: Scansione Barcode in ProductConfirmScreen

Aggiungere un bottone per scansionare il barcode accanto al campo barcode.

**Modificare**: `app/src/main/java/org/incammino/hospiceinventory/ui/screens/voice/ProductConfirmScreen.kt`

```kotlin
// Nel campo barcode, aggiungere bottone scanner:

Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
) {
    OutlinedTextField(
        value = barcode,
        onValueChange = onBarcodeChange,
        label = { Text("Barcode") },
        modifier = Modifier.weight(1f),
        singleLine = true
    )
    
    Spacer(Modifier.width(8.dp))
    
    IconButton(
        onClick = onScanBarcode,  // ← Nuovo callback
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Icon(
            Icons.Default.QrCodeScanner,
            contentDescription = "Scansiona barcode",
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
```

Per gestire il ritorno dalla scansione, usare `rememberLauncherForActivityResult` o navigation result.

---

## Riepilogo File

### Da CREARE

```
app/src/main/java/org/incammino/hospiceinventory/
├── ui/
│   ├── components/
│   │   └── voice/
│   │       └── VoiceContinueButton.kt
│   └── screens/
│       └── scanner/
│           ├── BarcodeResultScreen.kt
│           └── BarcodeResultViewModel.kt
```

### Da MODIFICARE

```
app/src/main/java/org/incammino/hospiceinventory/
├── ui/
│   ├── screens/
│   │   ├── search/
│   │   │   └── SearchViewModel.kt          # Bug fix query
│   │   └── voice/
│   │       ├── ProductConfirmScreen.kt     # + VoiceContinueButton + ScanBarcode
│   │       ├── ProductConfirmViewModel.kt  # + voice continue logic
│   │       ├── MaintenanceConfirmScreen.kt # + VoiceContinueButton
│   │       ├── MaintenanceConfirmViewModel.kt
│   │       ├── MaintainerConfirmScreen.kt  # + VoiceContinueButton
│   │       ├── MaintainerConfirmViewModel.kt
│   │       ├── LocationConfirmScreen.kt    # + VoiceContinueButton
│   │       └── LocationConfirmViewModel.kt
│   └── navigation/
│       └── Navigation.kt                   # + barcode result route
├── data/
│   ├── repository/
│   │   └── ProductRepository.kt            # + getByBarcode()
│   └── local/
│       └── dao/
│           └── ProductDao.kt               # + getByBarcode query
└── service/
    └── voice/
        └── GeminiService.kt                # + updateXxxFromVoice()
```

---

## Criteri di Test

### Bug Fix SearchScreen
- [ ] Digitare testo nel campo → caratteri appaiono immediatamente
- [ ] Cancellare con X → campo si svuota
- [ ] Filtri categoria funzionano

### VoiceContinueButton
- [ ] Bottone visibile in tutte le ConfirmScreen
- [ ] Tap → inizia ascolto (icona cambia)
- [ ] Parlare → testo parziale appare
- [ ] Tap per fermare → elaborazione → campi aggiornati
- [ ] Campi vuoti vengono popolati
- [ ] Campi esistenti vengono preservati se non menzionati

### Barcode Scanner
- [ ] HomeScreen → Scansiona → Camera si apre
- [ ] Scansione barcode esistente → Naviga a ProductDetail
- [ ] Scansione barcode nuovo → Dialog "Vuoi creare?"
- [ ] "Crea" → ProductConfirmScreen con barcode precompilato
- [ ] In ProductConfirmScreen, bottone scanner accanto a campo barcode funziona

---

## Note per Claude Code

1. **Ordine di implementazione suggerito:**
   - Prima il bug fix (5 min)
   - Poi VoiceContinueButton (componente + 1 ViewModel come test)
   - Poi estendere a tutti i ViewModel
   - Infine barcode scanner

2. **Se la sessione diventa troppo lunga**, può fermarsi dopo il bug fix + VoiceContinueButton e continuare in sessione successiva con il barcode.

3. **Test su device** consigliato dopo ogni sezione completata.
