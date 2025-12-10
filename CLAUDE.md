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
enum class MaintenanceFrequency(val days: Int) {
    TRIMESTRALE(90),
    SEMESTRALE(180),
    ANNUALE(365),
    BIENNALE(730),
    TRIENNALE(1095),
    QUADRIENNALE(1460),
    QUINQUENNALE(1825),
    CUSTOM(0)  // Usa maintenanceIntervalDays
}

enum class AccountType { PROPRIETA, NOLEGGIO, COMODATO, LEASING }

enum class MaintenanceType {
    PROGRAMMATA, STRAORDINARIA, VERIFICA, INSTALLAZIONE,
    DISMISSIONE, RIPARAZIONE, SOSTITUZIONE
}

enum class MaintenanceOutcome {
    RIPRISTINATO, PARZIALE, GUASTO, IN_ATTESA_RICAMBI,
    IN_ATTESA_TECNICO, DISMESSO, SOSTITUITO, NON_NECESSARIO
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

### Gemini System Prompt

```kotlin
const val SYSTEM_INSTRUCTION = """
Sei l'assistente vocale di Hospice Inventory, un'app per la gestione 
dell'inventario e delle manutenzioni dell'Hospice di Abbiategrasso.

Il tuo ruolo è:
1. Aiutare a cercare prodotti nell'inventario
2. Registrare manutenzioni
3. Preparare richieste di intervento via email
4. Informare sulle scadenze di manutenzioni e garanzie
5. Guidare nell'inserimento di nuovi prodotti

Rispondi sempre in italiano, in modo conciso e naturale.
Se devi confermare un'azione importante (invio email, eliminazione), 
chiedi sempre conferma.

Quando un prodotto è in garanzia, ricorda che il manutentore è il fornitore.
Quando la garanzia è scaduta, suggerisci il manutentore di servizio.
"""
```

### Function Calling Tools (da implementare)

```kotlin
// Tools per Gemini
val tools = listOf(
    Tool("search_product", "Cerca prodotti per nome, categoria, ubicazione o barcode"),
    Tool("get_product", "Recupera dettagli di un prodotto specifico"),
    Tool("add_maintenance", "Registra un nuovo intervento di manutenzione"),
    Tool("send_email", "Prepara e invia email al manutentore"),
    Tool("scan_barcode", "Attiva lo scanner barcode"),
    Tool("take_photo", "Scatta foto del prodotto"),
    Tool("get_alerts", "Recupera manutenzioni in scadenza"),
    Tool("create_product", "Crea un nuovo prodotto")
)
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
- [x] Gradle setup con version catalog
- [x] Room entities (Product, Maintainer, Maintenance, etc.)
- [x] Room DAOs con tutte le query
- [x] Database + TypeConverters
- [x] Domain models + Enums
- [x] Hilt DI modules
- [x] Theme (colori, typography)
- [x] Navigation setup
- [x] HomeScreen + ViewModel
- [x] Resources (strings, colors)

### 🔲 Da Fare
- [ ] **SearchScreen** - Ricerca prodotti con filtri
- [ ] **ProductDetailScreen** - Dettaglio con storico manutenzioni
- [ ] **ProductEditScreen** - Form creazione/modifica
- [ ] **MaintenanceListScreen** - Lista scadenze
- [ ] **MaintenanceEditScreen** - Registra intervento
- [ ] **SettingsScreen** - Configurazioni
- [ ] **Repository layer** - ProductRepository, etc.
- [ ] **Excel Import** - Parser per dati iniziali
- [ ] **VoiceService** - Speech-to-Text integration
- [ ] **GeminiService** - AI con function calling
- [ ] **BarcodeScanner** - ML Kit integration
- [ ] **CameraCapture** - Foto prodotti
- [ ] **EmailService** - Gmail API
- [ ] **NotificationWorker** - Alert scadenze
- [ ] **SyncWorker** - Firebase sync
- [ ] **Offline queue** - Email queue

---

## 🚨 Note Importanti

1. **Mai committare** `local.properties` e `google-services.json`
2. **Gemini API**: Usare `gemini-2.5-flash`, NON gemini-pro (costi 16x)
3. **Room migrations**: Per ora `fallbackToDestructiveMigration()` ok, rimuovere in produzione
4. **Kotlin**: Target JVM 17
5. **Min SDK**: 26 (Android 8.0) per kotlinx-datetime
6. **Nessun dato sanitario**: L'app gestisce solo inventario, NO dati pazienti

---

## 🔗 Risorse

- **Google Cloud Project**: `inventario-462506`
- **Firebase Project**: Collegato allo stesso progetto
- **Excel dati**: `Inventario.xlsx` (394 prodotti, 65 manutentori con email)
- **Logo**: Farfalla bicolore blu/azzurro
