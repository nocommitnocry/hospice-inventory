# CLAUDE.md - Hospice Inventory

> Istruzioni per Claude Code. Questo file viene letto automaticamente all'avvio.

## 📋 Progetto

**Nome:** Hospice Inventory
**Tipo:** App Android voice-first
**Cliente:** In Cammino Società Cooperativa Sociale (Hospice Abbiategrasso)
**Lingua codice:** Kotlin
**Lingua UI/commenti:** Italiano

---

## 🛠️ Comandi Essenziali

```bash
# Build debug
./gradlew assembleDebug

# Build release
./gradlew assembleRelease

# Run tests
./gradlew test

# Lint check
./gradlew lint

# Clean build
./gradlew clean assembleDebug

# Install su device connesso
./gradlew installDebug

# Generare Room schemas
./gradlew kspDebugKotlin
```

---

## 📁 Struttura Progetto

```
HospiceInventory/
├── app/
│   ├── build.gradle.kts          # Dipendenze modulo
│   ├── proguard-rules.pro        # Regole ProGuard
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/org/incammino/hospiceinventory/
│       │   ├── MainActivity.kt
│       │   ├── HospiceInventoryApp.kt    # Application + Hilt
│       │   ├── data/
│       │   │   ├── local/
│       │   │   │   ├── dao/              # Room DAOs
│       │   │   │   ├── entity/           # Room Entities
│       │   │   │   └── database/         # HospiceDatabase.kt
│       │   │   ├── remote/               # Firebase, API clients
│       │   │   └── repository/           # Repository pattern
│       │   ├── domain/
│       │   │   ├── model/                # Domain models + Enums
│       │   │   └── usecase/              # Business logic
│       │   ├── ui/
│       │   │   ├── theme/                # Theme.kt, Type.kt
│       │   │   ├── screens/              # Compose screens
│       │   │   │   ├── home/
│       │   │   │   ├── search/
│       │   │   │   ├── product/
│       │   │   │   ├── maintenance/
│       │   │   │   ├── settings/
│       │   │   │   └── voice/            # Voice Dump screens (v2.0)
│       │   │   ├── components/           # Componenti riutilizzabili
│       │   │   │   └── voice/            # VoiceDumpButton, VoiceContinueButton
│       │   │   └── navigation/           # Navigation.kt, NavigationWithCleanup.kt
│       │   ├── service/
│       │   │   ├── voice/                # VoiceService, GeminiService, ExtractionPrompts
│       │   │   ├── notification/         # NotificationWorker
│       │   │   └── sync/                 # SyncWorker, Firebase
│       │   ├── di/                       # Hilt modules
│       │   └── util/                     # Extensions, helpers
│       └── res/
│           ├── values/                   # strings.xml, colors.xml, themes.xml
│           ├── xml/                      # backup_rules, network_security
│           └── drawable/                 # Icons, images
├── gradle/
│   └── libs.versions.toml        # Version catalog
├── build.gradle.kts              # Project-level
├── settings.gradle.kts
├── local.properties              # API keys (NON COMMITTARE)
└── google-services.json          # Firebase config (NON COMMITTARE)
```

---

## 🔧 Stack Tecnologico

| Layer | Tecnologia | Versione |
|-------|------------|----------|
| Linguaggio | Kotlin | 2.0.21 |
| UI | Jetpack Compose | BOM 2024.10.01 |
| UI Components | Material 3 | latest |
| Navigation | Navigation Compose | 2.8.3 |
| DI | Hilt | 2.52 |
| Database | Room | 2.6.1 |
| Async | Coroutines | 1.9.0 |
| Network | Retrofit + OkHttp | 2.11.0 / 4.12.0 |
| Serialization | Kotlinx Serialization | 1.7.3 |
| AI | Google Generative AI | 0.9.0 |
| Barcode | ML Kit | 17.3.0 |
| Camera | CameraX | 1.4.0 |
| Images | Coil | 2.7.0 |
| Background | WorkManager | 2.10.0 |
| Cloud | Firebase (Firestore, Auth, FCM) | BOM 33.5.1 |
| Date/Time | Kotlinx Datetime | 0.6.1 |

---

## 📊 Data Model

### Entità Principali (Room)

**ProductEntity** - Prodotto/Asset dell'inventario
```kotlin
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val barcode: String?,
    val name: String,
    val description: String?,
    val category: String,
    val location: String,
    val assigneeId: String?,

    // Garanzia
    val warrantyMaintainerId: String?,   // Fornitore (durante garanzia)
    val warrantyStartDate: LocalDate?,
    val warrantyEndDate: LocalDate?,
    val serviceMaintainerId: String?,    // Riparatore (post-garanzia)

    // Manutenzione periodica
    val maintenanceFrequency: MaintenanceFrequency?,
    val maintenanceIntervalDays: Int?,   // Per CUSTOM
    val lastMaintenanceDate: LocalDate?,
    val nextMaintenanceDue: LocalDate?,

    // Acquisto
    val purchaseDate: LocalDate?,
    val price: Double?,
    val accountType: AccountType?,
    val supplier: String?,
    val invoiceNumber: String?,

    // Metadata
    val imageUri: String?,
    val notes: String?,
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)
```

**MaintainerEntity** - Manutentore/Fornitore
```kotlin
@Entity(tableName = "maintainers")
data class MaintainerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String?,
    val phone: String?,
    val address: String?,
    val city: String?,
    val postalCode: String?,
    val province: String?,
    val vatNumber: String?,
    val contactPerson: String?,
    val specialization: String?,
    val isSupplier: Boolean = false,
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

**MaintenanceEntity** - Intervento di manutenzione
```kotlin
@Entity(tableName = "maintenances")
data class MaintenanceEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val maintainerId: String?,
    val date: Instant,
    val type: MaintenanceType,
    val outcome: MaintenanceOutcome?,
    val notes: String?,
    val cost: Double?,
    val invoiceNumber: String?,
    val isWarrantyWork: Boolean = false,
    val requestEmailSent: Boolean = false,
    val requestEmailDate: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)
```

### Enums Principali

```kotlin
enum class MaintenanceFrequency(val days: Int, val label: String) {
    TRIMESTRALE(90), SEMESTRALE(180), ANNUALE(365),
    BIENNALE(730), TRIENNALE(1095), QUADRIENNALE(1460),
    QUINQUENNALE(1825), CUSTOM(0)
}

enum class AccountType { PROPRIETA, NOLEGGIO, COMODATO, LEASING }

enum class MaintenanceType(val metaCategory: MetaCategory, val synonyms: List<String>) {
    PROGRAMMATA, VERIFICA, RIPARAZIONE, SOSTITUZIONE,
    INSTALLAZIONE, COLLAUDO, DISMISSIONE, STRAORDINARIA
}

enum class MaintenanceOutcome {
    RIPRISTINATO, PARZIALE, GUASTO, IN_ATTESA_RICAMBI,
    IN_ATTESA_TECNICO, DISMESSO, SOSTITUITO, NON_NECESSARIO
}

enum class SyncStatus { SYNCED, PENDING, CONFLICT }
```

---

## 🔑 Business Logic Critica

### Selezione Manutentore (Garanzia vs Service)

```kotlin
fun getCurrentMaintainerId(): String? {
    return if (isUnderWarranty()) {
        warrantyMaintainerId ?: serviceMaintainerId
    } else {
        serviceMaintainerId ?: warrantyMaintainerId
    }
}
```

### Notifiche Scadenze

| Condizione | Alert |
|------------|-------|
| daysRemaining > 30 | Nessuno |
| daysRemaining in 8..30 | ADVANCE_30 |
| daysRemaining in 1..7 | ADVANCE_7 |
| daysRemaining == 0 | DUE_TODAY |
| daysRemaining < 0 | OVERDUE |

---

## 🎤 Voice Interface (v2.0 - Voice Dump + Visual Confirm)

### Paradigma Attuale

| Aspetto | Voice Dump (v2.0) |
|---------|-------------------|
| Tocchi | 2 |
| Chiamate API Gemini | 1 |
| Tempo completamento | ~45 secondi |
| Pattern | Tap-to-start, Tap-to-stop |

**Flusso:**
```
1. Utente tocca pulsante (es. "Registra Manutenzione")
2. Pulsante diventa ROSSO - utente parla liberamente
3. Tocca di nuovo per FERMARE → Gemini estrae dati (JSON)
4. Si apre scheda precompilata per verifica/correzione
5. Utente salva (1 tocco)
```

### Architettura Voice Dump

```
HomeScreen
├── [🔧 Registra Manutenzione] → VoiceMaintenanceScreen → MaintenanceConfirmScreen
├── [📦 Nuovo Prodotto]        → VoiceProductScreen     → ProductConfirmScreen
├── [👷 Nuovo Manutentore]     → VoiceMaintainerScreen  → MaintainerConfirmScreen
└── [📍 Nuova Ubicazione]      → VoiceLocationScreen    → LocationConfirmScreen

VoiceXxxScreen
├── VoiceService (STT con ascolto continuo)
│   ├── isContinuousListening flag
│   ├── SttPostProcessor (correzioni)
│   └── Accumulo risultati tra pause
├── VoiceDumpButton (rosso durante ascolto)
└── GeminiService.extractXxxData() → JSON

XxxConfirmScreen
├── BackHandler (intercetta swipe back)
├── Dati precompilati da Gemini
├── EntityResolver per match entità
├── VoiceContinueButton (input incrementale)
├── FormData per context (preserva modifiche manuali)
└── [Salva] → Repository → onVoiceSessionComplete() → cleanup
```

### VoiceService - Pattern Ascolto Continuo (30/12/2025)

```kotlin
// Flag principale
private var isContinuousListening = false

fun startListening() {
    isContinuousListening = true
    speechRecognizer?.startListening(intent)
}

fun stopListening() {
    isContinuousListening = false
    speechRecognizer?.stopListening()
    finalizeResult()  // UNICO punto di finalizzazione
}

// In RecognitionListener:
override fun onEndOfSpeech() {
    // NON fare nulla - aspettare onResults/onError
}

override fun onResults(results: Bundle?) {
    accumulatedText.append(bestMatch)
    if (isContinuousListening) {
        speechRecognizer?.startListening(intent)  // Loop!
    }
}
```

### VoiceContinueButton - Context Preservation (30/12/2025)

```kotlin
// ConfirmScreen: passa dati attuali al ViewModel
LaunchedEffect(Unit) {
    viewModel.onProcessVoiceWithContext = { transcript ->
        val currentFormData = ProductFormData(
            name = name,  // Valore ATTUALE (incluse modifiche manuali)
            model = model,
            // ...
        )
        viewModel.processVoiceWithContext(transcript, currentFormData)
    }
}

// ViewModel: chiama Gemini con context
fun processVoiceWithContext(transcript: String, currentData: ProductFormData) {
    val context = "Nome: ${currentData.name}\nModello: ${currentData.model}..."
    val updates = geminiService.updateProductFromVoice(context, transcript)
    onVoiceUpdate?.invoke(updates)
}
```

### Memory Cleanup - Gemini Context (30/12/2025)

```kotlin
// Flusso cleanup su Salva/Annulla/Back/Swipe:

ConfirmScreen → onNavigateBack/onSaved
    → Navigation.kt: onVoiceSessionComplete()
        → HomeViewModel.clearGeminiContext()
            → VoiceAssistant.clearContext()
                → GeminiService.resetContext()
                    → Log: "Context RESET - had activeTask: X, exchanges cleared: Y"

// BackHandler in ogni ConfirmScreen:
BackHandler {
    onNavigateBack()  // Delega al callback che fa cleanup
}

// Navigation.kt con cleanup callback:
AppNavigation(
    navController = navController,
    onVoiceSessionComplete = { homeViewModel.clearGeminiContext() }
)
```

### Entity Resolution

```kotlin
sealed class Resolution<T> {
    data class Found<T>(val entity: T) : Resolution<T>()
    data class Ambiguous<T>(val candidates: List<T>, val query: String) : Resolution<T>()
    data class NotFound<T>(val query: String) : Resolution<T>()
    data class NeedsConfirmation<T>(val candidate: T, val similarity: Float) : Resolution<T>()
}

class EntityResolver @Inject constructor(...) {
    suspend fun resolveMaintainer(name: String): Resolution<Maintainer>
    suspend fun resolveLocation(name: String): Resolution<Location>
    suspend fun resolveProduct(searchTerms: List<String>): Resolution<Product>
}
```

---

## 📧 Email Templates

### Richiesta Intervento Standard

```
Oggetto: [Hospice Inventory] Richiesta intervento: {product.name}

Gentili Signori,
richiediamo un intervento di manutenzione per:

PRODOTTO: {product.name}
ID: {product.id}
Ubicazione: {product.location}

PROBLEMA SEGNALATO:
{description}

Cordiali saluti,
Hospice In Cammino - Via dei Mille 8/10 - 20081 Abbiategrasso (MI)
```

---

## ⚙️ Configurazione

### local.properties (NON committare)

```properties
sdk.dir=/path/to/Android/Sdk
GEMINI_API_KEY=your_api_key_here
```

### Permessi Richiesti

- `INTERNET` - API calls
- `CAMERA` - Barcode scanner
- `RECORD_AUDIO` - Voice input
- `POST_NOTIFICATIONS` - Alert scadenze

---

## 🎨 UI Guidelines

### Colori

```kotlin
val HospiceBlue = Color(0xFF1E88E5)      // Primario
val HospiceLightBlue = Color(0xFF90CAF9) // Secondario
val AlertOverdue = Color(0xFFD32F2F)     // Rosso - scaduto
val AlertWarning = Color(0xFFF57C00)     // Arancione - in scadenza
val AlertOk = Color(0xFF388E3C)          // Verde - OK
```

### Componenti Chiave

- **VoiceDumpButton**: Pulsante con stati idle (blu) / listening (rosso) / processing
- **VoiceContinueButton**: Input vocale incrementale nelle ConfirmScreen
- **InlineEntityCreator**: Creazione rapida entità mancanti
- **AlertBanner**: Card colorata per scadenze
- **SelectableDropdownField**: Campo con dropdown + digitazione libera (Categoria, Ubicazione, Fornitore)
- **AutocompleteTextField**: Campo testo con suggerimenti filtrati (Edificio, Piano, Reparto)

---

## 📱 Target Device

**Nokia T21** - Android 12+ (API 31), 4GB RAM, Display 10.4"

---

## 📋 TODO / Checklist

### ✅ Completato

#### Voice Dump + Visual Confirm (v2.0)
- [x] **Fase 1: Manutenzione** ✅ 26/12/2025
- [x] **Fase 2: Prodotto** ✅ 28/12/2025
- [x] **Fase 3: Entità Supporto** ✅ 28/12/2025
  - [x] VoiceMaintainerScreen + MaintainerConfirmScreen
  - [x] VoiceLocationScreen + LocationConfirmScreen
- [x] **Fase 4: Polish** ✅ 30/12/2025
  - [x] VoiceContinueButton per input incrementale
  - [x] Tap-to-start/Tap-to-stop pattern (isContinuousListening)
  - [x] Context preservation per VoiceContinue (FormData)
  - [x] Memory cleanup su Salva/Annulla/Back/Swipe
  - [x] BackHandler in tutte le ConfirmScreen
- [x] **Fase 5: Unified Selectors** ✅ 30/12/2025
  - [x] SelectableDropdownField componente riutilizzabile
  - [x] ProductEditScreen: Categoria, Ubicazione, Fornitore con suggerimenti DB
  - [x] ProductConfirmScreen: CategorySelector con categorie dal DB
  - [x] getAllSuppliers() in ProductDao/Repository
- [x] **Fase 6: LocationEditScreen Enhancement** ✅ 30/12/2025
  - [x] AutocompleteTextField componente riutilizzabile
  - [x] LocationDefaults per valori predefiniti Hospice
  - [x] Query DISTINCT per building/floor/floorName/department
  - [x] Form completo: Identificazione, Gerarchia, Caratteristiche, Altri dati
  - [x] LocationTypeDropdown per tipo ubicazione

#### Infrastruttura & UI
- [x] Room entities, DAOs, Database
- [x] Hilt DI modules
- [x] Navigation con cleanup callback
- [x] Tutte le schermate principali (Home, Search, Product, Maintenance, Settings)
- [x] EntityResolver con fuzzy match Levenshtein
- [x] BarcodeScanner + BarcodeResultScreen

#### Servizi Vocali
- [x] VoiceService con ascolto continuo
- [x] SttPostProcessor (correzione sigle + spelling fonetico)
- [x] GeminiService (estrazione, update incrementali)
- [x] GeminiTtsService (voce Kore italiana)
- [x] TtsTextCleaner (rimuove markdown per TTS)

### 🔲 Da Fare

#### Priorità Alta
- [ ] **Test con utenti reali** su tablet Nokia T21
- [ ] **Excel Import** - Parser per Inventario.xlsx

#### Priorità Media
- [ ] **CameraCapture** - Foto prodotti
- [ ] **EmailService** - Invio email manutentori
- [ ] **NotificationWorker** - Alert scadenze

#### Priorità Bassa
- [ ] **SyncWorker** - Sincronizzazione Firebase
- [ ] **Offline queue** - Coda email offline

---

## 🚨 Note Importanti

1. **Mai committare** `local.properties` e `google-services.json`
2. **Gemini API**: Usare `gemini-2.5-flash` (Temperature: 0.4)
3. **Room migrations**: `fallbackToDestructiveMigration()` - rimuovere in produzione
4. **Min SDK**: 26 (Android 8.0)
5. **Nessun dato sanitario**: Solo inventario, NO dati pazienti

---

## 🐛 Bugfix Recenti

### Sessione 30/12/2025 (pomeriggio) - Unified Selectors & LocationEditScreen

**F4 - Unified Selectors** (FEATURE)
- Problema: Inconsistenze dati per Categoria/Ubicazione/Fornitore (es. "Ala Vecchia" vs "ala vecchia")
- Soluzione: `SelectableDropdownField` - dropdown con digitazione libera e suggerimenti dal DB
- File nuovi: `SelectableDropdownField.kt`
- File modificati: `ProductDao.kt` (+getAllSuppliers), `ProductRepository.kt`, `ProductEditViewModel.kt`, `ProductEditScreen.kt`, `ProductConfirmViewModel.kt`, `ProductConfirmScreen.kt`

**F5 - LocationEditScreen Enhancement** (FEATURE)
- Problema: LocationEditScreen incompleto rispetto a LocationConfirmScreen
- Soluzione: Form completo con 4 sezioni + autocomplete per campi gerarchia
- File nuovi: `AutocompleteTextField.kt`, `LocationDefaults.kt`
- File modificati: `SupportDaos.kt` (+query DISTINCT), `LocationRepository.kt` (+LocationSuggestions), `LocationEditViewModel.kt` (nuovi campi + loadSuggestions), `LocationEditScreen.kt` (form completo)

**F6 - Ricerca Prodotto Inline in MaintenanceConfirmScreen** (FIX)
- Problema: Clic su 🔍 per cercare prodotto navigava a SearchScreen, perdendo tutti i dati già compilati (descrizione, manutentore, durata, data, note)
- Soluzione: Ricerca inline con `ProductSearchField` dentro `ProductSelectionCard`
- File modificati: `MaintenanceConfirmViewModel.kt` (+ProductRepository, +productSearchQuery/Results, +updateProductSearchQuery/clearProductSearch), `MaintenanceConfirmScreen.kt` (riscritto ProductSelectionCard, +ProductSearchField), `Navigation.kt` (-onNavigateToProductSearch)

### Sessione 30/12/2025 (mattina) - Voice Recognition & Memory Cleanup

**F1 - Tap-to-Stop Voice Recognition** (FEATURE)
- Pattern semplificato: `isContinuousListening` flag
- `onEndOfSpeech()` non fa nulla - aspetta onResults/onError
- `onResults()` accumula e riavvia se flag attivo
- Solo `stopListening()` finalizza il risultato
- File: `VoiceService.kt`, `VoiceDumpButton.kt`

**F2 - VoiceContinue Context Preservation** (FIX)
- Problema: Modifiche manuali perse quando si usa VoiceContinue
- Soluzione: FormData classes + callback `onProcessVoiceWithContext`
- File: `ExtractionModels.kt` (+FormData classes), tutti i ConfirmViewModel/Screen

**F3 - Memory Cleanup Gemini** (FIX)
- Problema: Contaminazione dati tra sessioni vocali
- Soluzione: `resetContext()` su ogni completamento flusso
- File: `NavigationWithCleanup.kt`, `Navigation.kt`, `HomeViewModel.kt`, `VoiceAssistant`, `GeminiService.kt`, tutte le ConfirmScreen (+BackHandler)

### Sessione 29/12/2025

- **SearchScreen TextField** - Sincronizzato `_uiState` in `updateQuery()`
- **VoiceContinueButton** - Input vocale incrementale
- **BarcodeResultScreen** - Flusso scansione → ricerca DB

### Sessione 28/12/2025

- **DataHolder.consume()** - Fix con `remember {}` per preservare dati
- **InlineEntityCreator** - Creazione rapida entità mancanti
- **Fase 2 & 3 Voice Dump** - Completati tutti i flussi

---

## 🔗 Risorse

- **Google Cloud Project**: `inventario-462506`
- **Firebase Project**: Collegato allo stesso progetto
- **Excel dati**: `Inventario.xlsx` (394 prodotti, 65 manutentori)
- **Logo**: Farfalla bicolore blu/azzurro

---

## 🗑️ Legacy da pulire prima della conclusione definitiva

> Codice deprecato che rimane per compatibilità ma **NON va esteso**.
> Da rimuovere quando il sistema Voice Dump è stabile in produzione.

### Flusso Conversazionale Multi-Step (DEPRECATO)

Il vecchio paradigma richiedeva 6+ tocchi e chiamate API per completare un'operazione.
Sostituito completamente da "Voice Dump + Visual Confirm".

**File/Codice da rimuovere:**
- `ConversationContext.activeTask` - Task multi-step (ProductCreation, MaintenanceRegistration, etc.)
- `GeminiService` metodi legacy: `startProductCreationTask()`, `startMaintenanceTask()`, etc.
- Action tags nel system prompt: `START_PRODUCT_CREATION`, `START_MAINTENANCE`, `TASK_UPDATE`, etc.
- `UserIntentDetector` - Rilevamento "basta così", "annulla" (non più necessario)
- `SpeakerInference` - Inferenza manutentore vs operatore
- `EnumMatcher` - Matching fuzzy per categorie (ora gestito da ExtractionPrompts)

**Bugfix storici ora irrilevanti:**
- P1-P8 (Dicembre 2024): Relativi al flusso multi-step
- P9-P14 (Dicembre 2025): Relativi a ActiveTask e entity resolution nel flusso legacy

### Codice da valutare per rimozione

```kotlin
// ConversationContext.kt - Rimuovere:
sealed class ActiveTask { ... }
val activeTask: ActiveTask?
fun hasActiveTask()
fun isActiveTaskComplete()

// GeminiService.kt - Rimuovere sezioni:
// - "TASK MULTI-STEP" nel system prompt
// - Metodi start*Task(), complete*Task()
// - applyTaskUpdates(), parseTaskUpdateParams()
// - resolveEntityReferences() per ActiveTask

// VoiceAssistant.kt - Semplificare:
// - Rimuovere gestione WaitingConfirmation state
// - Rimuovere sendConfirmation()
```

### File di Spec implementati (da archiviare)

- `TAP_TO_STOP_SPEC.md` - Implementato 30/12/2025
- `SPEC_FIX_VOICE_CONTINUE_CONTEXT.md` - Implementato 30/12/2025
- `SPEC_MEMORY_CLEANUP.md` - Implementato 30/12/2025
- `SPEC-unified-selectors.md` - Implementato 30/12/2025
- `SPEC-LocationEditScreen-Enhancement.md` - Implementato 30/12/2025
