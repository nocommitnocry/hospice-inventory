package org.incammino.hospiceinventory.service.voice

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Prompt per l'estrazione dati da voice dump.
 * Paradigma Voice Dump + Visual Confirm (v2.0 - 26/12/2025)
 *
 * Ogni prompt richiede a Gemini di rispondere SOLO con JSON valido,
 * senza markdown o commenti.
 */
object ExtractionPrompts {

    /**
     * Prompt per estrazione dati manutenzione.
     * L'utente ha dettato tutto in una volta: chi ha fatto cosa, su quale apparecchio, ecc.
     */
    fun maintenanceExtractionPrompt(transcript: String): String {
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date

        return """
Sei un assistente per la registrazione di interventi di manutenzione in un hospice.
L'utente ha dettato una descrizione libera del lavoro svolto.

ESTRAI i dati dal testo e rispondi SOLO con JSON valido, senza markdown o commenti.

FORMATO RISPOSTA (JSON puro):
{
  "maintainer": {
    "name": "nome persona o null",
    "company": "nome ditta/azienda o null"
  },
  "product": {
    "searchTerms": ["termine1", "termine2"],
    "locationHint": "indicazione ubicazione o null"
  },
  "intervention": {
    "type": "ORDINARY|EXTRAORDINARY|VERIFICATION|INSTALLATION|DISPOSAL|null",
    "description": "descrizione lavoro svolto",
    "durationMinutes": numero_o_null,
    "isWarranty": true_false_o_null,
    "date": "YYYY-MM-DD o null per oggi"
  },
  "confidence": {
    "overall": 0.0-1.0,
    "missingFields": ["campo1", "campo2"]
  }
}

REGOLE ESTRAZIONE:

1. MANUTENTORE:
   - Se parla in prima persona ("ho riparato", "sono intervenuto") → è lui il manutentore
   - "Sono Mario di TechMed" → name="Mario", company="TechMed"
   - "Sono della TechMed" → name=null, company="TechMed"
   - "La TechMed ha riparato" → name=null, company="TechMed"
   - Se non menziona chi ha fatto → maintainer=null

2. PRODOTTO (searchTerms):
   - Estrai parole chiave per cercare: tipo apparecchio, marca, modello, ubicazione
   - "frigo in camera 12" → ["frigorifero", "frigo", "camera 12"]
   - "condizionatore reparto degenza" → ["condizionatore", "clima", "degenza"]
   - "letto della stanza 5" → ["letto", "stanza 5"]
   - Includi sempre sinonimi comuni (frigo/frigorifero, clima/condizionatore, etc.)

3. TIPO INTERVENTO:
   - ORDINARY: verifica periodica, controllo, ispezione, manutenzione programmata, tagliando
   - EXTRAORDINARY: riparazione, sostituzione, guasto, rottura, emergenza, aggiustato
   - VERIFICATION: collaudo, test, certificazione, verifica sicurezza
   - INSTALLATION: installazione, messa in opera, prima accensione, montaggio
   - DISPOSAL: dismissione, smaltimento, rimozione, buttato via

4. DURATA (converti in minuti):
   - "mezz'ora" → 30
   - "un'ora" → 60
   - "un'ora e mezza" → 90
   - "due ore" → 120
   - "venti minuti" → 20
   - "un paio d'ore" → 120

5. DATA:
   - "oggi", "stamattina", "adesso", "poco fa" → null (= data odierna)
   - "ieri" → calcola data -1 giorno
   - "l'altro ieri" → calcola data -2 giorni
   - "lunedì scorso", "la settimana scorsa" → stima approssimativa
   - Se non specificata → null

6. GARANZIA:
   - "in garanzia", "coperto da garanzia", "intervento in garanzia" → true
   - "fuori garanzia", "a pagamento", "non in garanzia" → false
   - Non menzionata → null

7. CONFIDENCE:
   - 0.9-1.0: tutti i campi principali estratti chiaramente
   - 0.7-0.9: la maggior parte dei campi estratti
   - 0.5-0.7: solo alcuni campi, richiede verifica
   - <0.5: testo confuso o incompleto
   - missingFields: elenca i campi non trovati nel testo

DATA ODIERNA: $today

TESTO UTENTE:
\"\"\"
$transcript
\"\"\"

JSON:
""".trimIndent()
    }

    /**
     * Mappa i tipi di intervento dal formato Gemini al MaintenanceType dell'app.
     */
    fun mapInterventionType(geminiType: String?): String? {
        return when (geminiType?.uppercase()) {
            "ORDINARY" -> "PROGRAMMATA"
            "EXTRAORDINARY" -> "RIPARAZIONE"
            "VERIFICATION" -> "VERIFICA"
            "INSTALLATION" -> "INSTALLAZIONE"
            "DISPOSAL" -> "DISMISSIONE"
            else -> geminiType
        }
    }

    /**
     * Prompt per estrazione dati nuovo prodotto/impianto.
     * L'utente ha dettato tutto in una volta: nome, modello, ubicazione, fornitore, ecc.
     */
    fun productExtractionPrompt(transcript: String): String {
        return """
Sei un assistente per la registrazione di nuovi prodotti/impianti in un hospice.
L'utente ha dettato una descrizione del prodotto da registrare.

ESTRAI i dati dal testo e rispondi SOLO con JSON valido, senza markdown o commenti.

FORMATO RISPOSTA (JSON puro):
{
  "product": {
    "name": "nome prodotto (obbligatorio)",
    "model": "modello o null",
    "manufacturer": "produttore/marca o null",
    "serialNumber": "numero serie o null",
    "barcode": "codice a barre o null",
    "category": "categoria o null"
  },
  "location": {
    "searchTerms": ["termine1", "termine2"],
    "floor": "PT|P1|P2|P-1 o null",
    "department": "reparto o null"
  },
  "supplier": {
    "name": "nome fornitore o null",
    "isAlsoMaintainer": true_o_false_o_null
  },
  "warranty": {
    "months": numero_mesi_o_null,
    "maintainerName": "nome assistenza garanzia o null"
  },
  "maintenance": {
    "frequencyMonths": numero_mesi_o_null,
    "notes": "note manutenzione o null"
  },
  "confidence": {
    "overall": 0.0-1.0,
    "missingFields": ["campo1"]
  }
}

CAMPI OBBLIGATORI: product.name, location.searchTerms (almeno 1 termine)

REGOLE ESTRAZIONE:

1. PRODOTTO:
   - Distingui nome generico (concentratore ossigeno) da modello (OxyGen 3000)
   - "Philips" è manufacturer, non name
   - Codici alfanumerici lunghi potrebbero essere serialNumber
   - Categorie comuni: Elettromedicale, Arredo, Informatica, Impianto, Attrezzatura

2. UBICAZIONE:
   - "camera 12" → searchTerms=["camera 12", "camera", "12"]
   - "primo piano" → floor="P1"
   - "reparto degenza" → department="Degenza"
   - "piano terra, stanza medici" → searchTerms=["stanza medici"], floor="PT"
   - Piani: PT (terra), P1 (primo), P2 (secondo), P-1 (seminterrato/interrato)

3. FORNITORE:
   - "comprato da Medika" → supplier.name="Medika"
   - "fornito e assistito da X" → isAlsoMaintainer=true
   - "acquistato presso Y" → supplier.name="Y"

4. GARANZIA:
   - "2 anni di garanzia" → warranty.months=24
   - "garanzia Philips" → warranty.maintainerName="Philips"
   - "1 anno" → 12, "6 mesi" → 6, "3 anni" → 36
   - "assistenza in garanzia da Z" → warranty.maintainerName="Z"

5. MANUTENZIONE:
   - "controllo annuale" → frequencyMonths=12
   - "verifica semestrale" → frequencyMonths=6
   - "manutenzione trimestrale" → frequencyMonths=3
   - "ogni 2 anni" → frequencyMonths=24

6. CONFIDENCE:
   - 0.9-1.0: nome e ubicazione chiari, altri campi presenti
   - 0.7-0.9: dati principali estratti
   - 0.5-0.7: solo alcuni campi, richiede verifica
   - <0.5: testo confuso o incompleto
   - missingFields: elenca "name" o "location" se non trovati

TESTO UTENTE:
\"\"\"
$transcript
\"\"\"

JSON:
""".trimIndent()
    }

    /**
     * Prompt per estrazione dati nuovo manutentore/fornitore.
     * L'utente ha dettato i dati dell'azienda: nome, contatti, indirizzo, ecc.
     * Fase 3 (28/12/2025)
     */
    fun maintainerExtractionPrompt(transcript: String): String {
        return """
Sei un assistente per la registrazione di manutentori/fornitori in un hospice.
L'utente ha dettato i dati di un'azienda o tecnico da aggiungere all'anagrafica.

ESTRAI i dati dal testo e rispondi SOLO con JSON valido, senza markdown o commenti.

FORMATO RISPOSTA (JSON puro):
{
  "company": {
    "name": "nome azienda/tecnico (obbligatorio)",
    "vatNumber": "partita IVA o null",
    "specialization": "specializzazione o null"
  },
  "contact": {
    "email": "email o null",
    "phone": "telefono o null",
    "contactPerson": "persona di riferimento o null"
  },
  "address": {
    "street": "via/indirizzo o null",
    "city": "città o null",
    "postalCode": "CAP o null",
    "province": "provincia (sigla) o null"
  },
  "business": {
    "isSupplier": true_o_false_o_null,
    "notes": "note o null"
  },
  "confidence": {
    "overall": 0.0-1.0,
    "missingFields": ["campo1"]
  }
}

CAMPO OBBLIGATORIO: company.name

REGOLE ESTRAZIONE:

1. AZIENDA:
   - "TechMed SRL" → name="TechMed SRL"
   - "ditta Rossi" → name="Rossi"
   - "Mario Bianchi elettricista" → name="Mario Bianchi", specialization="Elettricista"
   - Specializzazioni comuni: Elettricista, Idraulico, Frigorista, Elettromedicali, Informatica, Multiservizi

2. PARTITA IVA:
   - Formato italiano: 11 cifre (es. 12345678901)
   - Potrebbe essere dettata come "partita IVA uno due tre quattro..."
   - "P.IVA", "partita IVA", "codice fiscale azienda" → vatNumber

3. CONTATTI:
   - Formattazione telefono: mantieni numeri, rimuovi spazi extra
   - "zero due" → "02", "tre tre nove" → "339"
   - Email: cerca pattern "chiocciola" o "at" per @
   - "info chiocciola techmed punto it" → "info@techmed.it"
   - Referente: "chiedere di Mario", "contatto Marco Rossi" → contactPerson

4. INDIRIZZO:
   - Via/piazza + numero civico → street
   - "via Roma 15" → street="Via Roma 15"
   - Province: MI, MB, VA, CO, PV, LO, CR, MN, BG, BS...
   - "Milano provincia" → city="Milano", province="MI"

5. BUSINESS:
   - "è anche fornitore", "ci vende anche" → isSupplier=true
   - "solo assistenza", "solo manutenzione" → isSupplier=false
   - Default: null (non specificato)

6. CONFIDENCE:
   - 0.9-1.0: nome chiaro, contatti presenti
   - 0.7-0.9: nome chiaro, alcuni contatti
   - 0.5-0.7: solo nome estratto
   - <0.5: testo confuso
   - missingFields: elenca "name" se mancante, poi "email", "phone" se non trovati

TESTO UTENTE:
\"\"\"
$transcript
\"\"\"

JSON:
""".trimIndent()
    }

    /**
     * Prompt per estrazione dati nuova ubicazione.
     * L'utente ha dettato i dati del luogo: nome, tipo, gerarchia, caratteristiche.
     * Fase 3 (28/12/2025)
     */
    fun locationExtractionPrompt(transcript: String): String {
        return """
Sei un assistente per la registrazione di ubicazioni in un hospice.
L'utente ha dettato i dati di un luogo da aggiungere all'anagrafica.

ESTRAI i dati dal testo e rispondi SOLO con JSON valido, senza markdown o commenti.

FORMATO RISPOSTA (JSON puro):
{
  "location": {
    "name": "nome ubicazione (obbligatorio)",
    "type": "BUILDING|FLOOR|ROOM|CORRIDOR|STORAGE|TECHNICAL|OFFICE|COMMON_AREA|EXTERNAL|null"
  },
  "hierarchy": {
    "buildingName": "nome edificio o null",
    "floorCode": "PT|P1|P2|P-1 o null",
    "floorName": "Piano Terra|Primo Piano o null",
    "department": "reparto o null"
  },
  "details": {
    "hasOxygenOutlet": true_o_false_o_null,
    "bedCount": numero_posti_letto_o_null,
    "notes": "note o null"
  },
  "confidence": {
    "overall": 0.0-1.0,
    "missingFields": ["campo1"]
  }
}

CAMPO OBBLIGATORIO: location.name

REGOLE ESTRAZIONE:

1. TIPO UBICAZIONE:
   - BUILDING: edificio, struttura, palazzina, corpo, blocco
   - FLOOR: piano (usato raramente come ubicazione a sé)
   - ROOM: camera, stanza, locale paziente (con numero)
   - CORRIDOR: corridoio, disimpegno
   - STORAGE: magazzino, deposito, ripostiglio
   - TECHNICAL: locale tecnico, centrale termica, quadro elettrico, locale UPS
   - OFFICE: ufficio, studio, segreteria
   - COMMON_AREA: sala, soggiorno, cucina, bagno comune, ingresso
   - EXTERNAL: esterno, giardino, parcheggio, cortile

2. NOME:
   - "camera 12" → name="Camera 12", type="ROOM"
   - "ufficio direzione" → name="Ufficio Direzione", type="OFFICE"
   - "magazzino piano terra" → name="Magazzino", type="STORAGE"
   - Normalizza maiuscole: prima lettera maiuscola per ogni parola

3. GERARCHIA:
   - "nell'hospice principale" → buildingName="Hospice Principale"
   - "al primo piano" → floorCode="P1", floorName="Primo Piano"
   - "piano terra" → floorCode="PT", floorName="Piano Terra"
   - "seminterrato" / "interrato" → floorCode="P-1"
   - "reparto degenza" → department="Degenza"
   - Reparti comuni: Degenza, Ambulatorio, Day Hospital, Direzione

4. PIANO (mappatura):
   - "piano terra", "pianterreno" → PT / "Piano Terra"
   - "primo piano" → P1 / "Primo Piano"
   - "secondo piano" → P2 / "Secondo Piano"
   - "seminterrato", "piano meno uno" → P-1 / "Seminterrato"
   - "sottotetto", "mansarda" → PS / "Sottotetto"

5. DETTAGLI:
   - "con attacco ossigeno", "presa O2" → hasOxygenOutlet=true
   - "due posti letto", "camera doppia" → bedCount=2
   - "camera singola", "un letto" → bedCount=1
   - "tre letti" → bedCount=3

6. CONFIDENCE:
   - 0.9-1.0: nome e tipo chiari, gerarchia presente
   - 0.7-0.9: nome chiaro, tipo dedotto
   - 0.5-0.7: solo nome estratto
   - <0.5: testo confuso
   - missingFields: elenca "name" se mancante, poi altri campi

TESTO UTENTE:
\"\"\"
$transcript
\"\"\"

JSON:
""".trimIndent()
    }

    /**
     * Mappa i tipi di ubicazione dal formato Gemini al LocationType dell'app.
     */
    fun mapLocationType(geminiType: String?): String? {
        return when (geminiType?.uppercase()) {
            "BUILDING" -> "BUILDING"
            "FLOOR" -> "FLOOR"
            "ROOM" -> "ROOM"
            "CORRIDOR" -> "CORRIDOR"
            "STORAGE" -> "STORAGE"
            "TECHNICAL" -> "TECHNICAL"
            "OFFICE" -> "OFFICE"
            "COMMON_AREA" -> "COMMON_AREA"
            "EXTERNAL" -> "EXTERNAL"
            else -> null
        }
    }
}
