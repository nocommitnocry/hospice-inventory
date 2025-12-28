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
}
