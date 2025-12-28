# BRIEF FASE 3: Entità di Supporto Voice Dump

**Data**: 26 Dicembre 2025  
**Durata stimata**: 1 settimana  
**Priorità**: MEDIA  
**Prerequisiti**: Fase 1 e Fase 2 completate

---

## Obiettivo

Implementare i flussi Voice Dump per:
1. **Nuovo Manutentore/Fornitore** - anagrafica aziende esterne
2. **Nuova Ubicazione** - locali con gerarchia Edificio > Piano > Stanza

Questi flussi sono usati sia standalone che inline (durante creazione prodotto/manutenzione).

---

## Deliverable

### File da CREARE

```
app/src/main/java/org/incammino/hospiceinventory/ui/screens/voice/
├── VoiceMaintainerScreen.kt
├── VoiceMaintainerViewModel.kt
├── MaintainerConfirmScreen.kt
├── MaintainerConfirmViewModel.kt
├── VoiceLocationScreen.kt
├── VoiceLocationViewModel.kt
├── LocationConfirmScreen.kt
└── LocationConfirmViewModel.kt
```

### File da MODIFICARE

```
├── ui/navigation/Navigation.kt
├── ui/screens/home/HomeScreen.kt
├── service/voice/ExtractionPrompts.kt
├── service/voice/ExtractionModels.kt
├── service/voice/GeminiService.kt
├── data/local/entity/LocationEntity.kt    # Aggiungere campi gerarchia
├── data/local/dao/LocationDao.kt
└── domain/model/Location.kt
```

---

## PARTE A: Flusso Nuovo Manutentore

### A.1 Modelli Dati

```kotlin
// Aggiungere a ExtractionModels.kt

@Serializable
data class MaintainerExtraction(
    val maintainer: MaintainerCompanyInfo,
    val contact: ContactInfo?,
    val address: AddressInfo?,
    val business: BusinessInfo?,
    val confidence: ConfidenceInfo
)

@Serializable
data class MaintainerCompanyInfo(
    val name: String,
    val vatNumber: String?,
    val fiscalCode: String?
)

@Serializable
data class ContactInfo(
    val phone: String?,
    val email: String?,
    val contactPerson: String?
)

@Serializable
data class AddressInfo(
    val street: String?,
    val city: String?,
    val postalCode: String?,
    val province: String?
)

@Serializable
data class BusinessInfo(
    val specializations: List<String>?,
    val isSupplier: Boolean?,
    val isMaintainer: Boolean?
)
```

### A.2 Prompt Gemini Manutentore

```kotlin
// Aggiungere a ExtractionPrompts.kt

fun maintainerExtractionPrompt(transcript: String): String {
    return """
Sei un assistente per la registrazione di manutentori/fornitori.
L'utente ha dettato i dati di una nuova azienda o professionista.

ESTRAI i dati dal testo e rispondi SOLO con JSON valido.

FORMATO RISPOSTA:
{
  "maintainer": {
    "name": "ragione sociale (obbligatorio)",
    "vatNumber": "partita IVA o null",
    "fiscalCode": "codice fiscale o null"
  },
  "contact": {
    "phone": "telefono o null",
    "email": "email o null",
    "contactPerson": "nome referente o null"
  },
  "address": {
    "street": "via/indirizzo o null",
    "city": "città o null",
    "postalCode": "CAP o null",
    "province": "sigla provincia o null"
  },
  "business": {
    "specializations": ["specializzazione1", "specializzazione2"],
    "isSupplier": true_o_false_o_null,
    "isMaintainer": true_o_false_o_null
  },
  "confidence": {
    "overall": 0.0-1.0,
    "missingRequired": ["campo"]
  }
}

CAMPO OBBLIGATORIO: maintainer.name

REGOLE:

1. RAGIONE SOCIALE:
   - "TechMed Srl" → name="TechMed Srl"
   - "ditta Rossi" → name="Rossi"
   - Includi forma giuridica se detta (Srl, Spa, Snc, ditta individuale)

2. PARTITA IVA / CODICE FISCALE:
   - P.IVA: 11 cifre
   - C.F. azienda: 11 cifre
   - C.F. persona: 16 caratteri alfanumerici
   - Normalizza rimuovendo spazi e trattini

3. CONTATTI:
   - Telefono: normalizza (rimuovi spazi, mantieni prefisso +39)
   - Email: verifica formato base (contiene @)
   - "Il riferimento è Mario" → contactPerson="Mario"

4. INDIRIZZO:
   - "Via Roma 123, Milano" → street="Via Roma 123", city="Milano"
   - Province: usa sigle (MI, RM, TO, ...)
   - CAP: 5 cifre

5. SPECIALIZZAZIONI (normalizza a):
   - "clima" / "condizionamento" / "HVAC" / "climatizzazione"
   - "elettrico" / "impianti elettrici" / "elettricista"
   - "idraulico" / "termoidraulico" / "idraulica"
   - "medicale" / "elettromedicale" / "biomedicale"
   - "informatico" / "IT" / "computer"
   - "ascensori" / "elevatori" / "montacarichi"
   - "antincendio" / "estintori" / "sicurezza"
   - "generale" / "multiservizi" / "manutenzione generale"
   - "frigorista" / "refrigerazione"
   - "carpenteria" / "fabbro"

6. TIPOLOGIA:
   - Default: isMaintainer=true (se non specificato)
   - "anche fornitore" / "ci vendono anche" → isSupplier=true
   - "solo fornitore" → isMaintainer=false, isSupplier=true

TESTO UTENTE:
\"\"\"
$transcript
\"\"\"

JSON:
""".trimIndent()
}
```

### A.3 Screen VoiceMaintainerScreen

```kotlin
// Prompt mostrato all'utente
Text(
    text = "Mi dica: ragione sociale, specializzazione, e i contatti (telefono, email, indirizzo).",
    ...
)
```

### A.4 MaintainerConfirmScreen - Layout

```
┌─────────────────────────────────────────────────────────┐
│  ←  CONFERMA NUOVO MANUTENTORE                          │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  DATI AZIENDA                                           │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Ragione sociale * │ [TechMed Srl           ]    │    │
│  │ Partita IVA       │ [12345678901           ]    │    │
│  │ Codice Fiscale    │ [_______________       ]    │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  CONTATTI                                               │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Telefono    │ [02 1234567              ]        │    │
│  │ Email       │ [info@techmed.it         ]        │    │
│  │ Referente   │ [Mario Rossi             ]        │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  INDIRIZZO                                              │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Via         │ [Via Roma 123            ]        │    │
│  │ Città       │ [Milano    ] CAP [20100] PR [MI] │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  TIPOLOGIA                                              │
│  ┌─────────────────────────────────────────────────┐    │
│  │ ☑ Manutentore    ☑ Fornitore                   │    │
│  │                                                 │    │
│  │ Specializzazioni:                               │    │
│  │ [Elettromedicale ✕] [Clima ✕] [+ Aggiungi]     │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
├─────────────────────────────────────────────────────────┤
│       [ANNULLA]                      [💾 SALVA]         │
└─────────────────────────────────────────────────────────┘
```

---

## PARTE B: Flusso Nuova Ubicazione

### B.1 Modello Gerarchico

```kotlin
// Aggiornare LocationEntity.kt

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey
    val id: String,
    
    // Identificazione
    val name: String,                    // "Camera 15"
    val type: String,                    // LocationType come stringa
    
    // Gerarchia
    val parentId: String?,               // ID ubicazione padre
    val floor: String?,                  // "PT", "P1", "P-1"
    val floorName: String?,              // "Piano Terra", "Primo Piano"
    val department: String?,             // "Degenza" (tag, non FK)
    val building: String?,               // "Hospice Principale"
    
    // Dettagli
    val hasOxygenOutlet: Boolean = false,
    val bedCount: Int? = null,
    val notes: String? = null,
    
    // Metadata
    val isActive: Boolean = true,
    val needsCompletion: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: String = "PENDING"
)
```

```kotlin
// Aggiornare domain/model/Location.kt

data class Location(
    val id: String,
    val name: String,
    val type: LocationType,
    val parentId: String?,
    val floor: String?,
    val floorName: String?,
    val department: String?,
    val building: String?,
    val hasOxygenOutlet: Boolean,
    val bedCount: Int?,
    val notes: String?,
    val isActive: Boolean,
    val needsCompletion: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    /** Percorso completo es. "Hospice > Piano 1 > Camera 15" */
    val fullPath: String
        get() = listOfNotNull(building, floorName, name)
            .joinToString(" > ")
}

enum class LocationType {
    BUILDING,       // Edificio (livello 1)
    FLOOR,          // Piano (livello 2)
    ROOM,           // Camera degenza
    CORRIDOR,       // Corridoio
    STORAGE,        // Magazzino
    TECHNICAL,      // Locale tecnico
    OFFICE,         // Ufficio
    COMMON_AREA,    // Area comune
    EXTERNAL        // Esterno
}
```

### B.2 Modelli Estrazione

```kotlin
// Aggiungere a ExtractionModels.kt

@Serializable
data class LocationExtraction(
    val location: LocationInfo,
    val hierarchy: HierarchyInfo?,
    val details: LocationDetails?,
    val confidence: ConfidenceInfo
)

@Serializable
data class LocationInfo(
    val name: String,
    val type: String?,
    val floor: String?,
    val floorName: String?
)

@Serializable
data class HierarchyInfo(
    val parentSearchTerms: List<String>?,
    val department: String?,
    val building: String?
)

@Serializable
data class LocationDetails(
    val capacity: Int?,
    val hasOxygen: Boolean?,
    val notes: String?
)
```

### B.3 Prompt Gemini Ubicazione

```kotlin
// Aggiungere a ExtractionPrompts.kt

fun locationExtractionPrompt(transcript: String): String {
    return """
Sei un assistente per la registrazione di ubicazioni/locali in un hospice.
L'utente ha dettato i dati di una nuova ubicazione.

ESTRAI i dati e rispondi SOLO con JSON valido.

FORMATO RISPOSTA:
{
  "location": {
    "name": "nome locale (obbligatorio)",
    "type": "ROOM|CORRIDOR|STORAGE|TECHNICAL|OFFICE|COMMON_AREA|EXTERNAL|FLOOR|BUILDING",
    "floor": "PT|P1|P2|P-1|null",
    "floorName": "nome piano esteso o null"
  },
  "hierarchy": {
    "parentSearchTerms": ["termini", "ricerca", "padre"],
    "department": "reparto o null",
    "building": "edificio o null"
  },
  "details": {
    "capacity": numero_posti_letto_o_null,
    "hasOxygen": true_o_false_o_null,
    "notes": "note o null"
  },
  "confidence": {
    "overall": 0.0-1.0,
    "missingRequired": ["campo"]
  }
}

CAMPO OBBLIGATORIO: location.name

REGOLE:

1. TIPO LOCALE:
   - BUILDING: edificio, palazzina, struttura
   - FLOOR: piano (usare quando si registra un piano intero)
   - ROOM: camera, stanza (degenza)
   - CORRIDOR: corridoio, disimpegno, andito
   - STORAGE: magazzino, deposito, ripostiglio
   - TECHNICAL: locale tecnico, centrale termica, quadro elettrico, CED
   - OFFICE: ufficio, studio medico, ambulatorio
   - COMMON_AREA: sala comune, reception, sala attesa, refettorio
   - EXTERNAL: esterno, giardino, parcheggio, cortile

2. PIANO (normalizza floor):
   - "piano terra" / "pianoterra" / "PT" / "ground" → "PT"
   - "primo piano" / "piano 1" / "1° piano" → "P1"
   - "secondo piano" / "piano 2" → "P2"
   - "seminterrato" / "interrato" / "piano -1" → "P-1"
   - "sottotetto" / "mansarda" → "PM"

3. GERARCHIA:
   - "Camera 15 al primo piano" → floor="P1", floorName="Primo Piano"
   - "sotto il reparto degenza" → department="Degenza"
   - "nell'ala ovest" → parentSearchTerms=["ala ovest"]
   - Se non specificato, parentSearchTerms vuoto

4. DETTAGLI:
   - "con attacco ossigeno" / "presa O2" → hasOxygen=true
   - "camera doppia" / "2 letti" → capacity=2
   - "camera singola" / "1 letto" → capacity=1

TESTO UTENTE:
\"\"\"
$transcript
\"\"\"

JSON:
""".trimIndent()
}
```

### B.4 Screen VoiceLocationScreen

```kotlin
// Prompt mostrato all'utente
Text(
    text = "Mi dica il nome del locale, a che piano si trova, e se appartiene a un reparto specifico.",
    ...
)
```

### B.5 LocationConfirmScreen - Layout

```
┌─────────────────────────────────────────────────────────┐
│  ←  CONFERMA NUOVA UBICAZIONE                           │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  DATI LOCALE                                            │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Nome *        │ [Camera 15              ]       │    │
│  │ Tipo          │ [Camera degenza         ▼]      │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  POSIZIONE                                              │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Piano         │ [Primo Piano            ▼]      │    │
│  │ Reparto       │ [Degenza                ▼]      │    │
│  │ Edificio      │ [Hospice Principale     ▼]      │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  GERARCHIA                                              │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Ubicazione padre:                               │    │
│  │ [Piano 1 - Ala Est                      🔍]    │    │
│  │                                                 │    │
│  │ Anteprima: Hospice > Piano 1 > Camera 15       │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  DETTAGLI                                               │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Posti letto: [2  ]   ☑ Attacco ossigeno        │    │
│  │ Note: [____________________________]           │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
├─────────────────────────────────────────────────────────┤
│       [ANNULLA]                      [💾 SALVA]         │
└─────────────────────────────────────────────────────────┘
```

---

## 6. Navigation - Nuove Route

```kotlin
// Aggiungere a sealed class Screen

data object VoiceMaintainer : Screen("voice/maintainer")
data object MaintainerConfirm : Screen("maintainer/confirm")
data object VoiceLocation : Screen("voice/location")
data object LocationConfirm : Screen("location/confirm")
```

---

## 7. HomeScreen - 4 Pulsanti

```kotlin
// Layout Home con 4 entry point
Column {
    ActionButton(
        text = "Registra Manutenzione",
        icon = Icons.Default.Build,
        onClick = { onNavigateToVoiceMaintenance() }
    )
    ActionButton(
        text = "Nuovo Prodotto",
        icon = Icons.Default.Inventory,
        onClick = { onNavigateToVoiceProduct() }
    )
    ActionButton(
        text = "Nuovo Manutentore",
        icon = Icons.Default.Engineering,
        onClick = { onNavigateToVoiceMaintainer() }
    )
    ActionButton(
        text = "Nuova Ubicazione",
        icon = Icons.Default.LocationOn,
        onClick = { onNavigateToVoiceLocation() }
    )
}
```

---

## 8. Database Migration

```kotlin
// Room migration per nuovi campi Location

val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Aggiungi nuove colonne
        database.execSQL(
            "ALTER TABLE locations ADD COLUMN parentId TEXT"
        )
        database.execSQL(
            "ALTER TABLE locations ADD COLUMN floorName TEXT"
        )
        database.execSQL(
            "ALTER TABLE locations ADD COLUMN building TEXT"
        )
        database.execSQL(
            "ALTER TABLE locations ADD COLUMN hasOxygenOutlet INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE locations ADD COLUMN bedCount INTEGER"
        )
        database.execSQL(
            "ALTER TABLE locations ADD COLUMN needsCompletion INTEGER NOT NULL DEFAULT 0"
        )
    }
}
```

---

## 9. Criteri di Completamento

### Manutentore
- [ ] VoiceMaintainerScreen funziona
- [ ] Estrazione dati corretta
- [ ] MaintainerConfirmScreen editabile
- [ ] Salvataggio in DB
- [ ] Specializzazioni come chip editabili

### Ubicazione
- [ ] VoiceLocationScreen funziona
- [ ] Estrazione con gerarchia
- [ ] LocationConfirmScreen con selector piano/reparto
- [ ] Salvataggio con parentId corretto
- [ ] fullPath calcolato correttamente

### Integrazione
- [ ] Da ProductConfirmScreen posso creare nuovo fornitore inline
- [ ] Da ProductConfirmScreen posso creare nuova ubicazione inline
- [ ] Nuove entità immediatamente disponibili per selezione

### Home
- [ ] 4 pulsanti visibili e funzionanti
- [ ] Navigazione corretta per tutti i flussi

---

## 10. NON Fare in Questa Fase

❌ Gestione gerarchica completa (albero navigabile)
❌ Import/export ubicazioni da Excel
❌ Validazione P.IVA con servizio esterno
❌ Geocoding indirizzi
❌ QR code per ubicazioni
