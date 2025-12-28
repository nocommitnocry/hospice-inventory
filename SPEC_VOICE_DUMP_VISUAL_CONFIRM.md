# SPEC: Voice Dump + Visual Confirm

**Data**: 26 Dicembre 2025  
**Versione**: 1.0  
**Stato**: Da Implementare  
**Priorità**: ALTA - Revisione completa UX vocale

---

## Executive Summary

### Problema Attuale

Il flusso conversazionale multi-step causa:
- 6+ tocchi per una singola registrazione
- 6+ chiamate API Gemini (latenza, costi)
- ~2.5 minuti per operazione
- Frustrazione utente (interrogatorio vs conversazione)
- Bug complessi (loop ActiveTask, context loss)

### Soluzione Proposta

**Paradigma "Voice Dump + Visual Confirm":**
1. L'utente sceglie COSA registrare (1 tocco)
2. L'utente parla TUTTO in un'unica sessione vocale
3. Gemini estrae i dati (1 chiamata API)
4. Si apre scheda precompilata per verifica/correzione
5. L'utente salva (1 tocco)

**Risultato:** 2 tocchi, 1 chiamata API, ~45 secondi

---

## 1. Architettura Entry Point

### 1.1 Home Screen - Selezione Tipo Registrazione

```
┌─────────────────────────────────────────────────────────┐
│                    HOSPICE INVENTORY                    │
│                         [logo]                          │
├─────────────────────────────────────────────────────────┤
│                                                         │
│     ┌─────────────────────────────────────────────┐     │
│     │  🔧  REGISTRA MANUTENZIONE                  │     │
│     └─────────────────────────────────────────────┘     │
│                                                         │
│     ┌─────────────────────────────────────────────┐     │
│     │  📦  NUOVO PRODOTTO / IMPIANTO              │     │
│     └─────────────────────────────────────────────┘     │
│                                                         │
│     ┌─────────────────────────────────────────────┐     │
│     │  👷  NUOVO MANUTENTORE / FORNITORE          │     │
│     └─────────────────────────────────────────────┘     │
│                                                         │
│     ┌─────────────────────────────────────────────┐     │
│     │  📍  NUOVA UBICAZIONE                       │     │
│     └─────────────────────────────────────────────┘     │
│                                                         │
├─────────────────────────────────────────────────────────┤
│  [🔍 Cerca]                    [📋 Inventario]          │
└─────────────────────────────────────────────────────────┘
```

### 1.2 Rationale Pulsanti Separati

| Aspetto | Pulsanti | Gemini Decide |
|---------|----------|---------------|
| Ambiguità | 0% | 5-10% errori |
| Prompt Gemini | Specifico, ottimale | Generico |
| Scheda risultante | Corretta al 100% | Rischio tipo sbagliato |
| Complessità codice | Bassa | Alta (intent detection) |

---

## 2. Flusso Registrazione Manutenzione

### 2.1 Screen: VoiceMaintenanceScreen

```
┌─────────────────────────────────────────────────────────┐
│  ←  REGISTRA MANUTENZIONE                               │
├─────────────────────────────────────────────────────────┤
│                                                         │
│                                                         │
│     Mi dica: nome e ditta, su quale apparecchio         │
│     o impianto, cosa ha fatto, e quanto tempo           │
│     ha impiegato.                                       │
│                                                         │
│                                                         │
│                       ┌─────┐                           │
│                       │ 🎤  │                           │
│                       │     │                           │
│                       └─────┘                           │
│                    Tocca e parla                        │
│                                                         │
│                                                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │                                                 │    │
│  │  (Testo riconosciuto apparirà qui in tempo     │    │
│  │   reale per feedback visivo)                   │    │
│  │                                                 │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 2.2 Prompt Gemini - Estrazione Manutenzione

```kotlin
val MAINTENANCE_EXTRACTION_PROMPT = """
Sei un assistente per la registrazione di interventi di manutenzione.
L'utente ha dettato una descrizione libera del lavoro svolto.

ESTRAI i seguenti dati dal testo (JSON):

{
  "maintainer": {
    "name": "nome persona",
    "company": "nome ditta/azienda"
  },
  "product": {
    "searchTerms": ["termini", "per", "cercare"],
    "locationHint": "indicazione ubicazione se presente"
  },
  "intervention": {
    "type": "ORDINARY|EXTRAORDINARY|VERIFICATION|INSTALLATION|DISPOSAL",
    "description": "descrizione lavoro svolto",
    "durationMinutes": numero_o_null,
    "isWarranty": true|false|null,
    "date": "YYYY-MM-DD o null se oggi"
  },
  "confidence": {
    "overall": 0.0-1.0,
    "missingFields": ["campo1", "campo2"]
  }
}

REGOLE:
- Se l'utente parla in prima persona ("ho riparato"), è lui il manutentore
- "Mezz'ora" = 30, "un'ora" = 60, "due ore" = 120
- "Stamattina/oggi" = data odierna, "ieri" = data -1
- Se dice "in garanzia" → isWarranty = true
- Se non specificato un campo, usa null
- searchTerms: estrai parole chiave per cercare il prodotto (modello, tipo, ubicazione)

TIPI INTERVENTO:
- ORDINARY: verifica periodica, controllo, ispezione programmata
- EXTRAORDINARY: riparazione, sostituzione componente, guasto
- VERIFICATION: collaudo, test, certificazione
- INSTALLATION: prima installazione, messa in opera
- DISPOSAL: dismissione, smaltimento

DATA ODIERNA: ${LocalDate.now()}

TESTO UTENTE:
"""
```

### 2.3 Screen: MaintenanceConfirmScreen

Dopo l'estrazione Gemini, si apre la scheda di conferma:

```
┌─────────────────────────────────────────────────────────┐
│  ←  CONFERMA MANUTENZIONE                               │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  PRODOTTO                                               │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Frigorifero Electrolux - Camera 12          ✓  │    │
│  │ [Trovato: 1 risultato]                   [🔍]  │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  TIPO INTERVENTO                                        │
│  ┌─────────────────────────────────────────────────┐    │
│  │ ● Straordinario (riparazione)                  │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  DESCRIZIONE                                            │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Sostituzione compressore                       │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  ESEGUITO DA                                            │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Mario Rossi - TechMed Srl                   ✓  │    │
│  │ [Trovato]                                [🔍]  │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  DATA                           DURATA                  │
│  ┌──────────────────┐          ┌──────────────────┐    │
│  │ 26/12/2025       │          │ 2 ore            │    │
│  └──────────────────┘          └──────────────────┘    │
│                                                         │
│  ☑ Intervento in garanzia                               │
│                                                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Note (opzionale)                               │    │
│  │                                                 │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
├─────────────────────────────────────────────────────────┤
│       [ANNULLA]                      [💾 SALVA]         │
└─────────────────────────────────────────────────────────┘
```

### 2.4 Gestione Entità Non Trovate

**Caso: Prodotto non identificato**

```
│  PRODOTTO                                               │
│  ┌─────────────────────────────────────────────────┐    │
│  │ ⚠️ "frigo camera 12" - Non trovato             │    │
│  │                                                 │    │
│  │ Forse intendevi:                               │    │
│  │  ○ Frigorifero Electrolux (Camera 12)          │    │
│  │  ○ Frigorifero Smeg (Camera 14)                │    │
│  │  ○ [🔍 Cerca manualmente]                      │    │
│  └─────────────────────────────────────────────────┘    │
```

**Caso: Manutentore non trovato**

```
│  ESEGUITO DA                                            │
│  ┌─────────────────────────────────────────────────┐    │
│  │ ⚠️ "TechMed" - Non trovato                     │    │
│  │                                                 │    │
│  │  ○ Tecnomed Srl (simile)                       │    │
│  │  ○ [➕ Crea "TechMed" come nuovo manutentore]   │    │
│  └─────────────────────────────────────────────────┘    │
```

---

## 3. Flusso Nuovo Prodotto/Impianto

### 3.1 Screen: VoiceProductScreen

```
┌─────────────────────────────────────────────────────────┐
│  ←  NUOVO PRODOTTO                                      │
├─────────────────────────────────────────────────────────┤
│                                                         │
│     Mi descriva il prodotto: nome, modello,             │
│     produttore, dove si trova, e il fornitore           │
│     da cui è stato acquistato.                          │
│                                                         │
│                       ┌─────┐                           │
│                       │ 🎤  │                           │
│                       └─────┘                           │
│                    Tocca e parla                        │
│                                                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │  (Testo riconosciuto)                          │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 3.2 Prompt Gemini - Estrazione Prodotto

```kotlin
val PRODUCT_EXTRACTION_PROMPT = """
Sei un assistente per la registrazione di nuovi prodotti/impianti.
L'utente ha dettato una descrizione del prodotto da registrare.

ESTRAI i seguenti dati (JSON):

{
  "product": {
    "name": "nome prodotto",
    "model": "modello se specificato",
    "manufacturer": "produttore/marca",
    "serialNumber": "numero serie se detto",
    "barcode": "codice a barre se detto"
  },
  "location": {
    "searchTerms": ["camera", "12"],
    "floor": "piano se specificato",
    "department": "reparto se specificato"
  },
  "supplier": {
    "name": "nome fornitore",
    "isAlsoMaintainer": true|false
  },
  "warranty": {
    "months": numero_o_null,
    "maintainerName": "nome assistenza in garanzia"
  },
  "maintenance": {
    "frequencyMonths": numero_o_null,
    "notes": "note specifiche manutenzione"
  },
  "confidence": {
    "overall": 0.0-1.0,
    "missingRequired": ["campo1"]
  }
}

CAMPI OBBLIGATORI: name, location (almeno un termine)
CAMPI FACOLTATIVI: tutti gli altri

NOTE:
- "Comprato da X" → supplier.name = X
- "Garanzia 2 anni" → warranty.months = 24
- "Manutenzione semestrale" → maintenance.frequencyMonths = 6
- "Assistenza Y" → warranty.maintainerName = Y

TESTO UTENTE:
"""
```

### 3.3 Screen: ProductConfirmScreen

```
┌─────────────────────────────────────────────────────────┐
│  ←  CONFERMA NUOVO PRODOTTO                             │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  DATI PRODOTTO                                          │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Nome *        │ Concentratore ossigeno          │    │
│  │ Modello       │ OxyGen 3000                     │    │
│  │ Produttore    │ Philips                         │    │
│  │ N. Serie      │ _______________                 │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  UBICAZIONE *                                           │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Camera 12 - Piano Terra                      ✓ │    │
│  │ [Trovata]                                [🔍] │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  FORNITORE                                              │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Medika Srl                                   ✓ │    │
│  │ [Trovato]                    [➕ Crea nuovo]   │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  GARANZIA                                               │
│  ┌────────────────┐  ┌────────────────────────────┐    │
│  │ 24 mesi        │  │ Assistenza: Philips Italia │    │
│  └────────────────┘  └────────────────────────────┘    │
│                                                         │
│  MANUTENZIONE PROGRAMMATA                               │
│  ┌────────────────┐                                    │
│  │ Ogni 12 mesi   │  ☐ Richiede verifica periodica    │
│  └────────────────┘                                    │
│                                                         │
├─────────────────────────────────────────────────────────┤
│       [ANNULLA]                      [💾 SALVA]         │
└─────────────────────────────────────────────────────────┘
```

---

## 4. Flusso Nuovo Manutentore/Fornitore

### 4.1 Screen: VoiceMaintainerScreen

```
┌─────────────────────────────────────────────────────────┐
│  ←  NUOVO MANUTENTORE                                   │
├─────────────────────────────────────────────────────────┤
│                                                         │
│     Mi dica: ragione sociale, specializzazione,         │
│     e i contatti (telefono, email, indirizzo).          │
│                                                         │
│                       ┌─────┐                           │
│                       │ 🎤  │                           │
│                       └─────┘                           │
│                    Tocca e parla                        │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 4.2 Prompt Gemini - Estrazione Manutentore

```kotlin
val MAINTAINER_EXTRACTION_PROMPT = """
Sei un assistente per la registrazione di manutentori/fornitori.
L'utente ha dettato i dati di una nuova azienda o tecnico.

ESTRAI i seguenti dati (JSON):

{
  "maintainer": {
    "name": "ragione sociale o nome",
    "vatNumber": "partita IVA se detta",
    "fiscalCode": "codice fiscale se detto"
  },
  "contact": {
    "phone": "telefono",
    "email": "email",
    "contactPerson": "nome referente"
  },
  "address": {
    "street": "via/indirizzo",
    "city": "città",
    "postalCode": "CAP",
    "province": "provincia (sigla)"
  },
  "business": {
    "specializations": ["clima", "elettrico", "idraulico", ...],
    "isSupplier": true|false,
    "isMaintainer": true|false
  },
  "confidence": {
    "overall": 0.0-1.0,
    "missingRequired": ["campo1"]
  }
}

CAMPI OBBLIGATORI: name
TUTTO IL RESTO: opzionale

SPECIALIZZAZIONI COMUNI:
- "clima" / "condizionamento" / "HVAC"
- "elettrico" / "impianti elettrici"  
- "idraulico" / "termoidraulico"
- "medicale" / "elettromedicale"
- "informatico" / "IT"
- "ascensori" / "montacarichi"
- "antincendio"
- "generale" / "multiservizi"

NOTE:
- "Anche fornitore" → isSupplier = true
- Numeri telefono: normalizza rimuovendo spazi
- Email: valida formato base

TESTO UTENTE:
"""
```

### 4.3 Screen: MaintainerConfirmScreen

```
┌─────────────────────────────────────────────────────────┐
│  ←  CONFERMA NUOVO MANUTENTORE                          │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  DATI AZIENDA                                           │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Ragione sociale * │ TechMed Srl                 │    │
│  │ Partita IVA       │ _______________             │    │
│  │ Codice Fiscale    │ _______________             │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  CONTATTI                                               │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Telefono    │ 02 1234567                        │    │
│  │ Email       │ info@techmed.it                   │    │
│  │ Referente   │ Mario Rossi                       │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  INDIRIZZO                                              │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Via         │ Roma 123                          │    │
│  │ Città       │ Milano         │ CAP │ 20100     │    │
│  │ Provincia   │ MI                                │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  TIPOLOGIA                                              │
│  ┌─────────────────────────────────────────────────┐    │
│  │ ☑ Manutentore    ☐ Fornitore                   │    │
│  │                                                 │    │
│  │ Specializzazioni:                               │    │
│  │ [Elettromedicale ✓] [Clima ✓] [+ Aggiungi]     │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
├─────────────────────────────────────────────────────────┤
│       [ANNULLA]                      [💾 SALVA]         │
└─────────────────────────────────────────────────────────┘
```

---

## 5. Flusso Nuova Ubicazione

### 5.1 Modello Gerarchico Ubicazioni

```
LIVELLO 1: Edificio (se multipli)
    └── LIVELLO 2: Piano / Ala
            └── LIVELLO 3: Locale / Stanza

Esempi:
├── Hospice (edificio principale)
│   ├── Piano Terra
│   │   ├── Camera 1
│   │   ├── Camera 2
│   │   ├── Sala Medici
│   │   └── Reception
│   ├── Piano 1
│   │   ├── Camera 10
│   │   ├── Camera 11
│   │   └── Magazzino Farmaci
│   └── Seminterrato
│       ├── Locale Tecnico
│       ├── Centrale Termica
│       └── Magazzino Generale
```

### 5.2 Screen: VoiceLocationScreen

```
┌─────────────────────────────────────────────────────────┐
│  ←  NUOVA UBICAZIONE                                    │
├─────────────────────────────────────────────────────────┤
│                                                         │
│     Mi dica il nome del locale, a che piano si trova,   │
│     e se appartiene a un reparto specifico.             │
│                                                         │
│                       ┌─────┐                           │
│                       │ 🎤  │                           │
│                       └─────┘                           │
│                    Tocca e parla                        │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 5.3 Prompt Gemini - Estrazione Ubicazione

```kotlin
val LOCATION_EXTRACTION_PROMPT = """
Sei un assistente per la registrazione di ubicazioni/locali.
L'utente ha dettato i dati di una nuova ubicazione.

ESTRAI i seguenti dati (JSON):

{
  "location": {
    "name": "nome locale (es. Camera 15, Magazzino, Sala Medici)",
    "type": "ROOM|CORRIDOR|STORAGE|TECHNICAL|OFFICE|COMMON_AREA|EXTERNAL",
    "floor": "PT|P1|P2|P-1|...",
    "floorName": "nome piano (es. Piano Terra, Primo Piano, Seminterrato)"
  },
  "hierarchy": {
    "parentSearchTerms": ["termini", "per", "cercare", "padre"],
    "department": "reparto se specificato (Degenza, Amministrazione, ...)",
    "building": "edificio se specificato"
  },
  "details": {
    "capacity": numero_posti_letto_o_null,
    "hasOxygen": true|false|null,
    "notes": "note aggiuntive"
  },
  "confidence": {
    "overall": 0.0-1.0,
    "missingRequired": ["campo1"]
  }
}

CAMPI OBBLIGATORI: name
FACOLTATIVI: tutto il resto

TIPI LOCALE:
- ROOM: camera degenza, stanza
- CORRIDOR: corridoio, disimpegno
- STORAGE: magazzino, deposito, ripostiglio
- TECHNICAL: locale tecnico, centrale termica, quadro elettrico
- OFFICE: ufficio, studio medico
- COMMON_AREA: sala comune, reception, sala attesa
- EXTERNAL: esterno, giardino, parcheggio

PIANI (normalizza):
- "piano terra" / "pianoterra" / "PT" → "PT"
- "primo piano" / "piano 1" → "P1"
- "seminterrato" / "interrato" / "-1" → "P-1"

NOTE:
- "Camera 15 al primo piano" → name="Camera 15", floor="P1"
- "Sotto il reparto degenza" → hierarchy.parentSearchTerms=["degenza"]
- "Con attacco ossigeno" → hasOxygen=true

TESTO UTENTE:
"""
```

### 5.4 Screen: LocationConfirmScreen

```
┌─────────────────────────────────────────────────────────┐
│  ←  CONFERMA NUOVA UBICAZIONE                           │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  DATI LOCALE                                            │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Nome *        │ Camera 15                       │    │
│  │ Tipo          │ [Camera degenza        ▼]      │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  POSIZIONE                                              │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Piano         │ [Primo Piano           ▼]      │    │
│  │ Reparto       │ [Degenza               ▼]      │    │
│  │ Edificio      │ [Hospice Principale    ▼]      │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  GERARCHIA                                              │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Ubicazione padre (opzionale):                   │    │
│  │ [Seleziona...]                             [🔍]│    │
│  │                                                 │    │
│  │ Percorso: Hospice > Piano 1 > Camera 15        │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  DETTAGLI (opzionale)                                   │
│  ┌─────────────────────────────────────────────────┐    │
│  │ ☐ Attacco ossigeno    Posti letto: [2]         │    │
│  │ Note: _______________________________          │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
├─────────────────────────────────────────────────────────┤
│       [ANNULLA]                      [💾 SALVA]         │
└─────────────────────────────────────────────────────────┘
```

---

## 6. Modifiche Architetturali

### 6.1 Nuovi Screen da Creare

| Screen | Descrizione | Priorità |
|--------|-------------|----------|
| `VoiceMaintenanceScreen` | Input vocale manutenzione | ALTA |
| `MaintenanceConfirmScreen` | Conferma dati manutenzione | ALTA |
| `VoiceProductScreen` | Input vocale prodotto | ALTA |
| `ProductConfirmScreen` | Conferma dati prodotto | ALTA |
| `VoiceMaintainerScreen` | Input vocale manutentore | MEDIA |
| `MaintainerConfirmScreen` | Conferma dati manutentore | MEDIA |
| `VoiceLocationScreen` | Input vocale ubicazione | MEDIA |
| `LocationConfirmScreen` | Conferma dati ubicazione | MEDIA |

### 6.2 Nuovi ViewModel

```kotlin
// Pattern comune per tutti i flussi Voice Dump

@HiltViewModel
class VoiceMaintenanceViewModel @Inject constructor(
    private val voiceService: VoiceService,
    private val geminiService: GeminiService,
    private val entityResolver: EntityResolver
) : ViewModel() {

    // Stati
    sealed class State {
        object Idle : State()
        object Listening : State()
        data class Transcribing(val partialText: String) : State()
        object Processing : State()  // Gemini sta estraendo
        data class Extracted(val data: MaintenanceExtraction) : State()
        data class Error(val message: String) : State()
    }

    // Dati estratti da Gemini
    data class MaintenanceExtraction(
        val productMatch: EntityMatch<Product>,
        val maintainerMatch: EntityMatch<Maintainer>,
        val type: MaintenanceType?,
        val description: String?,
        val durationMinutes: Int?,
        val isWarranty: Boolean?,
        val date: LocalDate,
        val confidence: Float,
        val missingFields: List<String>
    )

    // Risultato entity resolution
    sealed class EntityMatch<T> {
        data class Found<T>(val entity: T) : EntityMatch<T>()
        data class Ambiguous<T>(val candidates: List<T>) : EntityMatch<T>()
        data class NotFound<T>(val searchTerms: String) : EntityMatch<T>()
    }
}
```

### 6.3 Modifiche a GeminiService

```kotlin
// Aggiungere metodi specifici per estrazione

suspend fun extractMaintenanceData(transcript: String): MaintenanceExtractionResult
suspend fun extractProductData(transcript: String): ProductExtractionResult
suspend fun extractMaintainerData(transcript: String): MaintainerExtractionResult
suspend fun extractLocationData(transcript: String): LocationExtractionResult

// Ciascuno usa il prompt specifico e parsa il JSON risultante
```

### 6.4 Modifiche a Navigation

```kotlin
sealed class Screen(val route: String) {
    // ... esistenti ...
    
    // Nuovi flussi Voice Dump
    object VoiceMaintenance : Screen("voice_maintenance")
    object MaintenanceConfirm : Screen("maintenance_confirm/{extractionJson}")
    object VoiceProduct : Screen("voice_product")
    object ProductConfirm : Screen("product_confirm/{extractionJson}")
    object VoiceMaintainer : Screen("voice_maintainer")
    object MaintainerConfirm : Screen("maintainer_confirm/{extractionJson}")
    object VoiceLocation : Screen("voice_location")
    object LocationConfirm : Screen("location_confirm/{extractionJson}")
}
```

### 6.5 Modifica Location Entity

```kotlin
@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey
    val id: String,
    
    val name: String,                    // "Camera 15"
    val type: LocationType,              // ROOM, STORAGE, TECHNICAL, ...
    
    // Gerarchia
    val parentId: String?,               // UUID padre (Piano, Ala, ...)
    val floor: String?,                  // "PT", "P1", "P-1"
    val floorName: String?,              // "Piano Terra"
    val department: String?,             // "Degenza" (tag, non gerarchia)
    val building: String?,               // "Hospice Principale"
    
    // Dettagli
    val hasOxygenOutlet: Boolean = false,
    val bedCount: Int? = null,
    val notes: String? = null,
    
    // Metadata
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)

enum class LocationType {
    ROOM,           // Camera degenza
    CORRIDOR,       // Corridoio
    STORAGE,        // Magazzino
    TECHNICAL,      // Locale tecnico
    OFFICE,         // Ufficio
    COMMON_AREA,    // Area comune
    EXTERNAL,       // Esterno
    FLOOR,          // Piano (per gerarchia)
    BUILDING        // Edificio (per gerarchia)
}
```

---

## 7. Gestione Inline Entity Creation

### 7.1 Flusso Quando Entità Non Trovata

```
[MaintenanceConfirmScreen]
        │
        ▼
Utente tocca "➕ Crea TechMed"
        │
        ▼
[Dialog: Conferma creazione veloce]
"Vuoi creare TechMed come nuovo manutentore?
 Potrai completare i dati dopo."
        │
    [Sì]  [No, cerca]
        │
        ▼
Crea Maintainer minimale (solo nome)
        │
        ▼
Torna a MaintenanceConfirmScreen con entity linkata
```

### 7.2 Creazione Minimale

```kotlin
// Per non interrompere il flusso, crea entità con dati minimi

suspend fun createMinimalMaintainer(name: String): Maintainer {
    return Maintainer(
        id = UUID.randomUUID().toString(),
        name = name,
        isActive = true,
        needsCompletion = true,  // Flag per reminder
        createdAt = Clock.System.now(),
        // ... altri campi null/default
    )
}
```

---

## 8. Sequenza Implementazione

### Fase 1: Core (Settimana 1)
1. ✅ Definire modelli dati estrazione (data class)
2. Implementare prompt Gemini per manutenzione
3. Creare `VoiceMaintenanceScreen`
4. Creare `MaintenanceConfirmScreen`
5. Testare flusso end-to-end

### Fase 2: Prodotti (Settimana 2)
1. Implementare prompt Gemini per prodotto
2. Creare `VoiceProductScreen`
3. Creare `ProductConfirmScreen`
4. Gestione inline creation per fornitore/manutentore

### Fase 3: Entità di Supporto (Settimana 3)
1. `VoiceMaintainerScreen` + `MaintainerConfirmScreen`
2. `VoiceLocationScreen` + `LocationConfirmScreen`
3. Modifica schema Location per gerarchia
4. Migration database

### Fase 4: Polish (Settimana 4)
1. Gestione errori STT
2. Feedback visivo transcript in tempo reale
3. Animazioni transizione
4. Test con utenti reali

---

## 9. Metriche di Successo

| Metrica | Attuale | Target |
|---------|---------|--------|
| Tocchi per registrazione | 6+ | 2 |
| Chiamate API per registrazione | 6 | 1 |
| Tempo completamento | ~2.5 min | <1 min |
| Tasso errori entity resolution | ~20% | <5% |
| Soddisfazione utente (Mario) | 😤 | 😊 |

---

## Appendice A: Risposte Gemini di Esempio

### A.1 Manutenzione - Input/Output

**Input utente:**
> "Sono Mario della TechMed, ho sostituito il compressore del frigorifero in camera 12, ci ho messo due ore, è in garanzia"

**Output Gemini:**
```json
{
  "maintainer": {
    "name": "Mario",
    "company": "TechMed"
  },
  "product": {
    "searchTerms": ["frigorifero", "camera 12", "frigo"],
    "locationHint": "camera 12"
  },
  "intervention": {
    "type": "EXTRAORDINARY",
    "description": "Sostituzione compressore",
    "durationMinutes": 120,
    "isWarranty": true,
    "date": null
  },
  "confidence": {
    "overall": 0.95,
    "missingFields": []
  }
}
```

### A.2 Prodotto - Input/Output

**Input utente:**
> "Nuovo concentratore di ossigeno Philips OxyGen 3000, lo mettiamo in camera 8, comprato da Medika, ha due anni di garanzia e va controllato ogni anno"

**Output Gemini:**
```json
{
  "product": {
    "name": "Concentratore di ossigeno",
    "model": "OxyGen 3000",
    "manufacturer": "Philips",
    "serialNumber": null,
    "barcode": null
  },
  "location": {
    "searchTerms": ["camera 8", "camera", "8"],
    "floor": null,
    "department": null
  },
  "supplier": {
    "name": "Medika",
    "isAlsoMaintainer": false
  },
  "warranty": {
    "months": 24,
    "maintainerName": null
  },
  "maintenance": {
    "frequencyMonths": 12,
    "notes": null
  },
  "confidence": {
    "overall": 0.92,
    "missingRequired": []
  }
}
```

---

## Appendice B: Edge Cases

### B.1 Transcript Incompleto

Se l'utente dice solo "Ho riparato il frigo":

```json
{
  "confidence": {
    "overall": 0.4,
    "missingFields": ["maintainer", "description", "duration"]
  }
}
```

→ La scheda si apre con campi vuoti evidenziati come obbligatori.

### B.2 Entità Ambigue

"Frigorifero camera 12" trova 2 risultati:
- Frigorifero Farmaci Camera 12
- Frigorifero Cucina Camera 12

→ Mostra radio button per selezione.

### B.3 STT Errato

Transcript: "Tecmed" invece di "TechMed"

→ EntityResolver con fuzzy match (Levenshtein) trova "TechMed" con similarity 0.85
→ Mostra come suggerimento: "Forse intendevi: TechMed?"
