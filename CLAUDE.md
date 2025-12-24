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
│       │   │   │   └── settings/
│       │   │   ├── components/           # Componenti riutilizzabili
│       │   │   └── navigation/           # Navigation.kt
│       │   ├── service/
│       │   │   ├── voice/                # VoiceService, GeminiService
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
    val email: String?,          // Obbligatoria per invio email
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
    val productId: String,       // FK -> products
    val maintainerId: String?,   // FK -> maintainers
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

### Enums

```kotlin
enum class MaintenanceFrequency(val days: Int, val label: String) {
    TRIMESTRALE(90, "Trimestrale (3 mesi)"),
    SEMESTRALE(180, "Semestrale (6 mesi)"),
    ANNUALE(365, "Annuale"),
    BIENNALE(730, "Biennale (2 anni)"),
    TRIENNALE(1095, "Triennale (3 anni)"),
    QUADRIENNALE(1460, "Quadriennale (4 anni)"),
    QUINQUENNALE(1825, "Quinquennale (5 anni)"),
    CUSTOM(0, "Personalizzata")  // Usa maintenanceIntervalDays
}

enum class AccountType(val label: String) {
    PROPRIETA("Proprietà"), NOLEGGIO("Noleggio"),
    COMODATO("Comodato d'uso"), LEASING("Leasing")
}

// MaintenanceType con supporto matching vocale
enum class MaintenanceType(
    val label: String,
    val displayName: String,
    val metaCategory: MetaCategory,
    val synonyms: List<String>
) {
    PROGRAMMATA("Programmata", "Manutenzione programmata", ORDINARIA,
        listOf("programmata", "periodica", "schedulata")),
    VERIFICA("Verifica/Controllo", "Verifica periodica", ORDINARIA,
        listOf("verifica", "controllo", "check", "ispezione")),
    RIPARAZIONE("Riparazione", "Riparazione", STRAORDINARIA,
        listOf("riparazione", "riparato", "aggiustato", "sistemato")),
    SOSTITUZIONE("Sostituzione componente", "Sostituzione", STRAORDINARIA,
        listOf("sostituzione", "sostituito", "cambiato")),
    INSTALLAZIONE("Installazione", "Installazione", LIFECYCLE,
        listOf("installazione", "installato", "montato")),
    COLLAUDO("Collaudo", "Collaudo", LIFECYCLE,
        listOf("collaudo", "collaudato", "test iniziale")),
    DISMISSIONE("Dismissione", "Dismissione", LIFECYCLE,
        listOf("dismissione", "dismesso", "smontato", "buttato")),
    STRAORDINARIA("Straordinaria", "Intervento straordinario", STRAORDINARIA,
        listOf("straordinaria", "urgente", "emergenza"));

    enum class MetaCategory { ORDINARIA, STRAORDINARIA, LIFECYCLE }
}

enum class MaintenanceOutcome(val label: String) {
    RIPRISTINATO("Ripristinato/Funzionante"), PARZIALE("Parzialmente risolto"),
    GUASTO("Ancora guasto"), IN_ATTESA_RICAMBI("In attesa ricambi"),
    IN_ATTESA_TECNICO("In attesa tecnico"), DISMESSO("Dismesso"),
    SOSTITUITO("Sostituito"), NON_NECESSARIO("Intervento non necessario")
}

enum class SyncStatus { SYNCED, PENDING, CONFLICT }
enum class EmailStatus { PENDING, SENT, FAILED }
enum class AlertType { ADVANCE_30, ADVANCE_7, DUE_TODAY, OVERDUE }
```

---

## 🔑 Business Logic Critica

### Selezione Manutentore (Garanzia vs Service)

```kotlin
// In Product domain model
fun getCurrentMaintainerId(): String? {
    return if (isUnderWarranty()) {
        warrantyMaintainerId ?: serviceMaintainerId
    } else {
        serviceMaintainerId ?: warrantyMaintainerId
    }
}

fun isUnderWarranty(): Boolean {
    val today = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    return warrantyEndDate != null && today <= warrantyEndDate
}
```

### Calcolo Prossima Manutenzione

```kotlin
fun calculateNextMaintenanceDue(
    lastDate: LocalDate,
    frequency: MaintenanceFrequency,
    customDays: Int? = null
): LocalDate {
    val days = if (frequency == MaintenanceFrequency.CUSTOM) {
        customDays ?: 365
    } else {
        frequency.days
    }
    return lastDate.plus(DatePeriod(days = days))
}
```

### Notifiche Scadenze

| Condizione | Alert |
|------------|-------|
| daysRemaining > 30 | Nessuno |
| daysRemaining in 8..30 | ADVANCE_30 |
| daysRemaining in 1..7 | ADVANCE_7 |
| daysRemaining == 0 | DUE_TODAY |
| daysRemaining < 0 | OVERDUE (daily reminder) |

---

## 🎤 Voice Interface

### Architettura Servizi Vocali

```
VoiceAssistantManager (orchestratore)
├── VoiceService (STT - Speech-to-Text)
│   └── SttPostProcessor (correzione sigle + spelling fonetico)
├── TtsService / GeminiTtsService (Text-to-Speech)
└── GeminiService (AI + Action Parsing)
    └── ConversationContext (stato conversazionale)
        ├── recentExchanges (ultimi 6 turni)
        ├── activeTask (task multi-step in corso)
        └── speakerHint (manutentore vs operatore)
```

### Action Tags (implementato)

Gemini risponde con tag `[ACTION:tipo:parametri]` che vengono parsati:

```kotlin
// CREAZIONE ENTITÀ (task guidati multi-step)
- START_PRODUCT_CREATION          // Avvia creazione prodotto
- START_MAINTENANCE:id:nome       // Avvia registrazione manutenzione
- START_MAINTAINER_CREATION       // Avvia creazione manutentore/fornitore
- START_LOCATION_CREATION         // Avvia creazione ubicazione
- START_ASSIGNEE_CREATION         // Avvia creazione assegnatario

// ALTRE AZIONI
- SEARCH:query                    // Cerca prodotti
- SHOW:productId                  // Mostra dettaglio
- MAINTENANCE_LIST:filtro         // Lista manutenzioni
- EMAIL:productId:descrizione     // Email manutentore
- SCAN:motivo                     // Scanner barcode
- ALERTS                          // Mostra scadenze

// DEPRECATO: CREATE:campo=valore → ora unificato con START_PRODUCT_CREATION
```

### Contesto Conversazionale Multi-Turno

```kotlin
data class ConversationContext(
    val currentProduct: Product? = null,
    val lastSearchResults: List<Product> = emptyList(),
    val pendingAction: AssistantAction? = null,
    val awaitingConfirmation: Boolean = false,

    // Contesto conversazionale
    val activeTask: ActiveTask? = null,     // Task multi-step
    val recentExchanges: List<ChatExchange>, // Max 6 turni
    val speakerHint: SpeakerHint            // Manutentore/Operatore
)
```

### Task Multi-Step (ActiveTask)

Per flussi guidati come creazione prodotto o registrazione manutenzione:

```kotlin
sealed class ActiveTask {
    data class ProductCreation(
        val name: String?, val category: String?,
        val brand: String?, val location: String?, ...
    ) : ActiveTask()

    data class MaintenanceRegistration(
        val productId: String, val productName: String,
        val type: MaintenanceType?, val description: String?, ...
    ) : ActiveTask()

    data class MaintainerCreation(
        val name: String?, val company: String?,
        val email: String?, val phone: String?, ...
    ) : ActiveTask()

    data class LocationCreation(
        val name: String?, val parentId: String?,
        val address: String?, val notes: String?
    ) : ActiveTask()

    data class AssigneeCreation(
        val name: String?, val department: String?,
        val phone: String?, val email: String?
    ) : ActiveTask()
}
```

### Entity Resolution ✅ (Implementato 24/12/2025)

Quando l'utente riferisce entità per nome (es. "fornitore Medika"), il sistema risolve il nome in ID.

**Stato implementazione:**
- ✅ `EntityResolver` class con fuzzy match Levenshtein: `service/voice/EntityResolver.kt`
- ✅ Query sincrone `getAllActiveSync()` in tutti i DAO e Repository
- ✅ Campi `*Name` e `*Id` in `ActiveTask.ProductCreation`
- ✅ Integrazione in `GeminiService.completeActiveTask()` con `resolveEntityReferences()`

**Architettura implementata:**

```kotlin
// EntityResolver.kt - service/voice/
sealed class Resolution<T> {
    data class Found<T>(val entity: T) : Resolution<T>()
    data class Ambiguous<T>(val candidates: List<T>, val query: String) : Resolution<T>()
    data class NotFound<T>(val query: String) : Resolution<T>()
    data class NeedsConfirmation<T>(val candidate: T, val similarity: Float, val query: String) : Resolution<T>()
}

class EntityResolver @Inject constructor(...) {
    suspend fun resolveMaintainer(name: String): Resolution<Maintainer>
    suspend fun resolveLocation(name: String): Resolution<Location>
    suspend fun resolveAssignee(name: String): Resolution<Assignee>
}
```

**Flusso risoluzione:**
```
Utente: "fornitore Medika"
    ↓
EntityResolver.resolveMaintainer("Medika")
    ↓
Found(id="maint-123") → usa ID nel prodotto
Ambiguous([...]) → "Quale intendi: Medika Srl, Medika Service?"
NeedsConfirmation → "Intendi Medika Healthcare?"
NotFound → "Medika non esiste. Vuoi crearlo?" (TODO: creazione inline)
```

**Campi in ActiveTask.ProductCreation:**
```kotlin
// Campi *Id per ID risolti (pronti per salvataggio)
// Campi *Name per nomi non ancora risolti
val warrantyMaintainerId: String?, val warrantyMaintainerName: String?,
val serviceMaintainerId: String?, val serviceMaintainerName: String?,
val locationId: String?, val locationName: String?,
val assigneeId: String?, val assigneeName: String?
```

**TODO:** Creazione inline entità mancanti quando NotFound.

### Inferenza Speaker

L'app inferisce chi sta parlando dai pattern linguistici:
- **LIKELY_MAINTAINER**: "Ho riparato...", "Sono di Tecnomed"
- **LIKELY_OPERATOR**: "Il tecnico ha fatto...", "È venuto..."

Se manutentore → non chiede "chi ha fatto l'intervento"

### STT Timeout e Gestione Intelligente

**Timeout Android (intent extras):**
```kotlin
EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS = 5000L      // 5s
EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS = 3000L  // 3s
EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS = 2000L
```

**Silence Delay (implementato in VoiceService):**
- Dopo `onEndOfSpeech()`, attende 2.5s prima di processare
- Se l'utente riprende a parlare, il timer viene cancellato
- Permette pause naturali durante dettatura di informazioni lunghe

**Trigger Words** (terminano immediatamente l'ascolto):
- "fatto", "invia", "ok", "procedi", "basta", "stop", "fine"

### SttPostProcessor (correzione STT)

Post-processore per migliorare il riconoscimento vocale:

**Correzione sigle distorte:**
- ABC → APC (produttore UPS)
- UBS/EPS/IPS → UPS
- Phillips/Fillips → Philips

**Spelling fonetico italiano:**
- "A come Ancona, P come Padova, C come Como" → "APC"
- Supporta alfabeto fonetico completo (Ancona, Bari, Como, ...)

---

## 📧 Email Templates

### Richiesta Intervento Standard

```
Oggetto: [Hospice Inventory] Richiesta intervento: {product.name}

Gentili Signori,

richiediamo un intervento di manutenzione per:

PRODOTTO: {product.name}
ID: {product.id}
Barcode: {product.barcode}
Ubicazione: {product.location}
Categoria: {product.category}

PROBLEMA SEGNALATO:
{description}

Restiamo in attesa di Vostro riscontro per concordare l'intervento.

Cordiali saluti,

Hospice In Cammino
Via dei Mille 8/10 - 20081 Abbiategrasso (MI)
Tel: 02 9466160

---
Messaggio generato automaticamente da Hospice Inventory
```

### Richiesta Intervento in GARANZIA

```
Oggetto: [GARANZIA] Richiesta assistenza: {product.name}

Gentili Signori,

richiediamo INTERVENTO IN GARANZIA per:

PRODOTTO: {product.name}
ID: {product.id}

DATI ACQUISTO:
- Data acquisto: {product.purchaseDate}
- N° Fattura: {product.invoiceNumber}
- Scadenza garanzia: {product.warrantyEndDate}

PROBLEMA SEGNALATO:
{description}

[resto come standard]
```

---

## 🔄 Sync Strategy

### Offline-First Pattern

1. **Tutte le operazioni** scrivono prima su Room (locale)
2. **SyncStatus** traccia lo stato: SYNCED, PENDING, CONFLICT
3. **SyncWorker** (WorkManager) sincronizza con Firebase quando online
4. **Conflict resolution**: Last-write-wins con timestamp

```kotlin
// Flusso scrittura
suspend fun saveProduct(product: Product) {
    // 1. Salva locale
    productDao.insert(product.copy(syncStatus = PENDING))
    
    // 2. Trigger sync se online
    if (networkMonitor.isOnline()) {
        syncWorker.enqueue(SyncRequest.PRODUCTS)
    }
}
```

---

## ⚙️ Configurazione

### local.properties (da creare, NON committare)

```properties
sdk.dir=/path/to/Android/Sdk
GEMINI_API_KEY=your_api_key_here
```

### google-services.json

Scaricare dalla Firebase Console e posizionare in `app/`

### Permessi Richiesti (AndroidManifest.xml)

- `INTERNET` - API calls
- `CAMERA` - Barcode scanner, foto
- `RECORD_AUDIO` - Voice input
- `POST_NOTIFICATIONS` - Alert scadenze
- `RECEIVE_BOOT_COMPLETED` - Reschedule alarms

---

## 🎨 UI Guidelines

### Colori (dal logo farfalla)

```kotlin
val HospiceBlue = Color(0xFF1E88E5)      // Primario
val HospiceLightBlue = Color(0xFF90CAF9) // Secondario
val HospiceDarkBlue = Color(0xFF1565C0)  // Accent

val AlertOverdue = Color(0xFFD32F2F)     // Rosso - scaduto
val AlertWarning = Color(0xFFF57C00)     // Arancione - in scadenza
val AlertOk = Color(0xFF388E3C)          // Verde - OK
```

### Componenti Chiave

- **VoiceButton**: Pulsante circolare grande, animazione pulse quando attivo
- **AlertBanner**: Card colorata per scadenze (rosso/arancione)
- **ProductCard**: Card con info essenziali + stato garanzia/manutenzione
- **StatusBar**: Indicatore online/offline + sync pending

---

## 📱 Target Device

**Nokia T21**
- Android 12+ (API 31)
- 4GB RAM
- Camera 13MP
- Display 10.4"

**Ottimizzazioni:**
- Compose con `remember` e `derivedStateOf`
- Lazy loading per liste
- Image caching con Coil
- Room queries con Flow (non LiveData)

---

## 🧪 Testing

```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest

# Specific test class
./gradlew test --tests "*.ProductRepositoryTest"
```

### Test Prioritari

1. `ProductDao` - CRUD e query scadenze
2. `MaintenanceCalculator` - Logica date
3. `WarrantyChecker` - Selezione manutentore
4. `GeminiService` - Function calling

---

## 📋 TODO / Checklist

### ✅ Completato

#### Infrastruttura
- [x] Gradle setup con version catalog
- [x] Room entities (Product, Maintainer, Maintenance, etc.)
- [x] Room DAOs con tutte le query
- [x] Database + TypeConverters
- [x] Domain models + Enums (con synonyms e metaCategory per MaintenanceType)
- [x] Hilt DI modules
- [x] Theme (colori, typography)
- [x] Navigation setup
- [x] Resources (strings, colors)

#### Schermate UI
- [x] **HomeScreen** + HomeViewModel
- [x] **SearchScreen** + SearchViewModel - Ricerca prodotti con filtri
- [x] **ProductDetailScreen** + ProductDetailViewModel - Dettaglio con storico manutenzioni
- [x] **ProductEditScreen** + ProductEditViewModel - Form creazione/modifica
- [x] **MaintenanceListScreen** + MaintenanceListViewModel - Lista scadenze
- [x] **MaintenanceEditScreen** - Registra intervento
- [x] **SettingsScreen** - Configurazioni

#### Componenti UI
- [x] VoiceButton - Pulsante vocale con animazioni
- [x] StatusBar - Indicatore online/offline + sync
- [x] SearchBar - Barra ricerca
- [x] ProductCard - Card prodotto
- [x] AlertBanner - Banner alert scadenze
- [x] MaintenanceCard - Card manutenzione

#### Repository Layer
- [x] **ProductRepository** - CRUD prodotti + query scadenze
- [x] **MaintainerRepository** - CRUD manutentori + `getAllActiveSync()` (24/12/2025)
- [x] **MaintenanceRepository** - CRUD manutenzioni
- [x] **LocationRepository** - CRUD ubicazioni + `getAllActiveSync()` (24/12/2025)
- [x] **AssigneeRepository** - CRUD assegnatari + `getAllActiveSync()` (24/12/2025)
- [x] **EntityResolver** - Risoluzione nomi → ID con fuzzy match Levenshtein (24/12/2025)
- [x] Query sincrone `*Sync()` nei repository e DAO (24/12/2025)

#### Servizi Vocali
- [x] **VoiceService** - Speech-to-Text (Google Speech Recognition)
  - [x] Silence delay (2.5s) per frasi lunghe
  - [x] Trigger words per terminazione immediata
  - [x] Accumulo risultati parziali
- [x] **SttPostProcessor** - Correzione sigle distorte + spelling fonetico italiano
- [x] **TtsService** - Text-to-Speech Android nativo
- [x] **GeminiTtsService** - Gemini 2.5 Flash TTS via REST API (voce Kore, PCM 24kHz) + fallback Android TTS
- [x] **GeminiService** - AI con function calling, rate limiting, audit logging
  - [x] Contesto arricchito (data corrente, lista manutentori)
  - [x] Ricerca interna durante task MaintenanceRegistration
  - [x] Temperature ridotta a 0.4 per maggiore precisione
- [x] **ConversationContext** - Contesto conversazionale multi-turno
- [x] **ActiveTask** - Task multi-step (ProductCreation, MaintenanceRegistration, MaintainerCreation, LocationCreation, AssigneeCreation)
- [x] **SpeakerInference** - Inferenza manutentore vs operatore
- [x] **UserIntentDetector** - Rilevamento "basta così", "annulla"
- [x] **EnumMatcher** - Matching fuzzy per categorie/tipi

#### Hardware Integration
- [x] **BarcodeAnalyzer** - Analyzer CameraX con ML Kit per scansione codici
- [x] **ScannerScreen** - Schermata scanner con preview camera e overlay mirino

#### Dati Demo
- [x] **SampleDataPopulator** - Dati di test per sviluppo

### 🔲 Da Fare

#### Entity Resolution ✅ (Implementato 24/12/2025)
- [x] **EntityResolver** class - Risoluzione nomi → ID con fuzzy match Levenshtein
- [x] Query sincrone `getAllActiveSync()` in MaintainerRepository
- [x] Query sincrone `getAllActiveSync()` in LocationRepository
- [x] Query sincrone `getAllActiveSync()` in AssigneeRepository
- [x] Campi `*Name` in ActiveTask.ProductCreation per riferimenti non risolti
- [x] Integrazione EntityResolver in `GeminiService.completeActiveTask()`
- [x] Risoluzione automatica con disambiguazione utente (Ambiguous, NeedsConfirmation)
- [ ] Creazione inline entità mancanti (NotFound → offri creazione) - TODO

#### Import/Export
- [ ] **Excel Import** - Parser per dati iniziali (Inventario.xlsx)

#### Hardware Integration
- [x] **BarcodeScanner** - ML Kit Barcode Scanning (17/12/2024)
- [ ] **CameraCapture** - Foto prodotti con CameraX

#### Comunicazione
- [ ] **EmailService** - Invio email manutentori (Gmail API o Intent)

#### Background Tasks
- [ ] **NotificationWorker** - Alert scadenze manutenzioni
- [ ] **SyncWorker** - Sincronizzazione Firebase
- [ ] **Offline queue** - Coda email offline

---

## 🚨 Note Importanti

1. **Mai committare** `local.properties` e `google-services.json`
2. **Gemini API**: Usare `gemini-2.5-flash`, NON gemini-pro (costi 16x)
   - Temperature: 0.4 (ridotta per maggiore precisione nell'estrazione dati)
3. **Room migrations**: Per ora `fallbackToDestructiveMigration()` ok, rimuovere in produzione
4. **Kotlin**: Target JVM 17
5. **Min SDK**: 26 (Android 8.0) per kotlinx-datetime
6. **Nessun dato sanitario**: L'app gestisce solo inventario, NO dati pazienti

---

## 🐛 Bugfix Recenti (Dicembre 2024)

### Sessione 16/12/2024 - Test su Tablet

**P1 - Flusso MaintenanceRegistration** (CRITICO - RISOLTO)
- Problema: Navigazione a SearchScreen interrompeva il task multi-step
- Fix: Ricerca interna in `GeminiService.handleInternalSearchForMaintenance()`
- File: `GeminiService.kt`, `ProductDao.kt` (aggiunto `searchSync`)

**P2 - Timeout STT** (CRITICO - RISOLTO)
- Problema: Device ignorava timeout estesi, troncava frasi lunghe
- Fix: Silence delay 2.5s + trigger words in `VoiceService`
- File: `VoiceService.kt`

**P3 - Contesto Gemini incompleto** (ALTO - RISOLTO)
- Problema: Gemini non conosceva data corrente e manutentori
- Fix: `buildContextPrompt()` arricchito con data, manutentori, task attivo
- File: `GeminiService.kt`

**P4/P5 - Sigle STT distorte** (ALTO - RISOLTO)
- Problema: "APC" riconosciuto come "ABC", spelling fonetico non interpretato
- Fix: Nuovo `SttPostProcessor` con correzioni e alfabeto fonetico
- File: `SttPostProcessor.kt` (nuovo)

**P6 - TTS qualità** (MEDIO - RISOLTO 21/12/2024)
- Fix: Implementato Gemini 2.5 Flash TTS via REST API con voce "Kore" (italiana)
- Formato: PCM 24kHz mono 16-bit, riprodotto con AudioTrack
- Fallback: Android TTS nativo se offline o errori API
- File: `GeminiTtsService.kt`

### Sessione 17/12/2024 - Bugfix Loop ActiveTask

**P7 - Loop conferma ActiveTask** (CRITICO - RISOLTO)
- Problema: Dopo conferma utente ("sì"), il task ricominciava chiedendo tipo/descrizione
- Causa: Gemini estraeva i dati ma non li salvava nel task (nessun formato strutturato)
- Fix:
  - Aggiunto tag `[TASK_UPDATE:campo=valore]` al system prompt
  - Parsing del tag in `parseResponse()` con `applyTaskUpdates()`
  - Helper: `parseTaskUpdateParams()`, `parseMaintenanceTypeFromUpdate()`, `parseTaskDate()`
- File: `AppModules.kt`, `GeminiService.kt`

**P8 - TTS legge markdown e tag interni** (ALTO - RISOLTO)
- Problema: TTS pronunciava "asterisco asterisco" e "TASK UPDATE type RIPARAZIONE"
- Fix: Creato `TtsTextCleaner` object che rimuove:
  - Tag interni: `[TASK_UPDATE:...]`, `[ACTION:...]`
  - Markdown: bold, italic, headers, liste, links, code blocks
- Applicato in `VoiceAssistant.speakResponse()` prima di passare al TTS
- File: `GeminiTtsService.kt` (TtsTextCleaner object)

### Sessione 23/12/2025 - Voice Flow Entity Creation

**P9 - ProductEditViewModel isNew errato** (CRITICO - PARZIALE)
- Problema: Route `product/edit/new` → `savedStateHandle["productId"]` restituisce `"new"` (stringa), non `null`
- Effetto: `isNew = productId == null` → `false` ❌ (modalità MODIFICA invece di CREAZIONE)
- Fix parziale: Logica corretta per gestire `"new"` come nuovo prodotto
- ✅ Riconoscimento route "new" → modalità creazione
- ❌ Prefill data da voice flow non implementato (i dati raccolti vocalmente NON vengono passati alla UI)
- ❌ Salvataggio diretto da voice non implementato
- File: `ProductEditViewModel.kt` (linee 71-74)

**P10 - Azioni creazione entità mancanti** (ALTO - RISOLTO 24/12/2025)
- Problema: Mancavano action tags per avviare creazione manutentori, ubicazioni, assegnatari
- ✅ Action tags aggiunti al system prompt:
  - `START_MAINTAINER_CREATION`, `START_LOCATION_CREATION`, `START_ASSIGNEE_CREATION`
- ✅ Parsing ACTION in `parseResponse()`
- ✅ Metodi: `startMaintainerCreationTask()`, `startLocationCreationTask()`, `startAssigneeCreationTask()`
- ✅ `completeActiveTask()` genera `AssistantAction.Save*` per salvataggio
- ✅ EntityResolver implementato per risolvere nomi → ID
- File: `GeminiService.kt`, `EntityResolver.kt`

**P11 - CREATE generico confondeva Gemini** (MEDIO - RISOLTO)
- Problema: `CREATE:campo=valore` veniva usato per qualsiasi entità, non solo prodotti
- Fix:
  - Prompt riorganizzato con sezione "CREAZIONE ENTITÀ" dedicata
  - `CREATE` unificato con `START_PRODUCT_CREATION` (entrambi avviano task guidato)
  - Aggiunto `startProductCreationTaskWithPrefill()` per CREATE con parametri
  - Nota esplicita: "Non usare CREATE generico"
- File: `GeminiService.kt`

**P12 - Voice-first flow + Entity Resolution** (ENHANCEMENT - IMPLEMENTATO 24/12/2025)
- Scelta architetturale: Salvataggio diretto da voice, UI opzionale per dettagli
- ✅ EntityResolver implementato con fuzzy match Levenshtein
- ✅ Campi `*Name` e `*Id` in ActiveTask.ProductCreation
- ✅ `resolveEntityReferences()` in GeminiService
- ✅ Disambiguazione automatica (Ambiguous, NeedsConfirmation)
- ⏳ TODO: Creazione inline entità mancanti (NotFound → offri creazione)
- File: `GeminiService.kt`, `EntityResolver.kt`, `ConversationContext.kt`

### Sessione 24/12/2025 - Entity Resolution Implementation

**P13 - Entity Resolution** (CRITICO - IMPLEMENTATO)
- Problema: Utente dice "fornitore Medika" ma il DB richiede UUID, non stringa
- Soluzione: EntityResolver con fuzzy match Levenshtein

**File creati/modificati:**
```
NUOVO: service/voice/EntityResolver.kt
  - Resolution<T>: Found, Ambiguous, NotFound, NeedsConfirmation
  - resolveMaintainer(), resolveLocation(), resolveAssignee()
  - Levenshtein distance per fuzzy match (similarity >= 0.6)

MODIFICATI:
  - SupportDaos.kt: +getAllActiveSync() per Maintainer, Location, Assignee
  - MaintainerRepository.kt: +getAllActiveSync()
  - LocationRepository.kt: +getAllActiveSync()
  - AssigneeRepository.kt: +getAllActiveSync()
  - ConversationContext.kt: +campi *Id/*Name in ProductCreation
  - GeminiService.kt: +entityResolver injection, +resolveEntityReferences()
```

**Flusso implementato:**
```
completeActiveTask()
    ↓
resolveEntityReferences(task)
    ↓
  Found → usa ID, procedi
  Ambiguous → "Quale intendi: X, Y, Z?"
  NeedsConfirmation → "Intendi X?" (similarity 60-80%)
  NotFound → "X non esiste. Vuoi crearlo?" (TODO: inline creation)
```

**Test:** Build passata, da testare su device.

---

## 🔗 Risorse

- **Google Cloud Project**: `inventario-462506`
- **Firebase Project**: Collegato allo stesso progetto
- **Excel dati**: `Inventario.xlsx` (394 prodotti, 65 manutentori con email)
- **Logo**: Farfalla bicolore blu/azzurro
