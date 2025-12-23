BUGFIX: Entity Creation & Voice Flow

Hospice Inventory - Specifiche Tecniche

**Data: 23 Dicembre 2025 | Priorità: CRITICA**

Executive Summary

Sono stati identificati bug critici nel flusso di creazione entità che
impediscono il corretto salvataggio dei dati nel database. I problemi
coinvolgono sia il flusso vocale che quello manuale UI.

  -----------------------------------------------------------------------
  **Problema**               **Sintomo**                **Severità**
  -------------------------- -------------------------- -----------------
  CreateProduct prefill      Scheda prodotto vuota dopo **CRITICA**
  ignorato                   conferma vocale            

  Location: conferma senza   TTS conferma, DB vuoto     **CRITICA**
  save                                                  

  Maintenance: "prodotto    Errore dopo conferma       **CRITICA**
  non trovato"              manuale                    

  Supplier: STT non          "Dell SaS" → "delle     **ALTA**
  riconosce nomi             sass"                     
  -----------------------------------------------------------------------

1. Diagnosi Tecnica

1.1 Problema #1: prefillData Ignorato

**File:** HomeViewModel.kt

**Linea critica:**

is AssistantAction.CreateProduct -> NavigationAction.ToNewProduct

**Problema:** ToNewProduct è un *data object* senza parametri. I dati
raccolti vocalmente (prefillData) vengono completamente ignorati.

1.2 Problema #2: Mancano AssistantAction per Save

**File:** GeminiService.kt → completeActiveTask()

Il metodo completeActiveTask() per Location e Maintenance restituisce
solo un messaggio TTS, ma NON esegue alcun salvataggio nel database:

-   **ProductCreation:** restituisce CreateProduct(prefillData) → apre
    UI, ma prefill ignorato

-   **MaintenanceRegistration:** restituisce solo messaggio, NESSUN
    salvataggio

-   **MaintainerCreation:** restituisce solo messaggio, NESSUN
    salvataggio

-   **LocationCreation:** non esiste nemmeno come ActiveTask!

1.3 Problema #3: MaintenanceEditScreen - "Prodotto non trovato"

Quando si crea una manutenzione manualmente:

1.  L'utente compila il form e seleziona il prodotto

2.  Tap sull'icona di conferma

3.  Errore: "Prodotto non trovato"

**Causa probabile:** Il productId non viene passato correttamente al
save() o la validazione fallisce prima del salvataggio.

1.4 Problema #4: Nomi Fornitori con STT

Il riconoscimento vocale italiano non gestisce nomi stranieri e sigle:

-   "Dell" → "delle", "del", "della"

-   "SaS" → "sass", "sas"

-   Spelling fonetico complesso da interpretare correttamente

2. Soluzioni Proposte

2.1 FIX #1: NavigationAction con Prefill

**File da modificare:** HomeViewModel.kt

**Modifica 1 - NavigationAction:**

// PRIMA:

data object ToNewProduct : NavigationAction()

// DOPO:

data class ToNewProduct(val prefill: Map<String, String>? = null) :
NavigationAction()

**Modifica 2 - handleAssistantAction:**

is AssistantAction.CreateProduct ->
NavigationAction.ToNewProduct(action.prefillData)

**Modifica 3 - Navigation.kt:** Passare prefill come JSON string nella
route e deserializzare in ProductEditViewModel.

2.2 FIX #2: Nuove AssistantAction per Save Diretto

**File:** GeminiService.kt

Aggiungere nuove azioni in sealed class AssistantAction:

data class SaveProduct(val product: Product) : AssistantAction() {

override val riskLevel = ActionRiskLevel.MEDIUM

}

data class SaveMaintenance(val maintenance: Maintenance) :
AssistantAction()

data class SaveMaintainer(val maintainer: Maintainer) :
AssistantAction()

data class SaveLocation(val location: Location) : AssistantAction()

**Nota:** Aggiungere anche LocationCreation come nuovo ActiveTask in
ConversationContext.kt

2.3 FIX #3: completeActiveTask() con Salvataggio

**File:** GeminiService.kt

Modificare completeActiveTask() per restituire azioni di salvataggio:

is ActiveTask.MaintenanceRegistration -> {

val maintenance = Maintenance(

productId = task.productId,

type = task.type ?: MaintenanceType.VERIFICATION,

description = task.description,

// ... altri campi

)

conversationContext = conversationContext.copy(activeTask = null)

GeminiResult.ActionRequired(

AssistantAction.SaveMaintenance(maintenance),

"Manutenzione registrata con successo!"

)

2.4 FIX #4: Handler per Save Actions

**File:** HomeViewModel.kt

Aggiungere handler in handleAssistantAction():

is AssistantAction.SaveMaintenance -> {

viewModelScope.launch {

try {

maintenanceRepository.insert(action.maintenance)

// Opzionale: navigare al dettaglio

} catch (e: Exception) {

// Gestire errore

}

}

null // Nessuna navigazione

}

**Nota:** HomeViewModel richiede iniezione di MaintenanceRepository,
MaintainerRepository, LocationRepository via Hilt.

2.5 FIX #5: MaintenanceEditScreen Debug

**File:** MaintenanceEditViewModel.kt

Verificare il metodo save():

1.  Controllare che selectedProductId non sia null/vuoto al momento del
    save

2.  Aggiungere logging per tracciare il flusso

3.  Verificare che la validazione non fallisca silenziosamente

4.  Controllare che il prodotto selezionato esista effettivamente nel DB

Log.d(TAG, "Saving maintenance - productId: $selectedProductId,
exists: ${productExists}")

2.6 FIX #6: Spelling Guidato per Fornitori

Per la registrazione di nuovi fornitori, implementare un flusso di
spelling guidato:

**Flusso proposto:**

1.  Utente: "Voglio aggiungere un nuovo fornitore"

2.  Sistema: "Dimmi il nome lettera per lettera, tipo D come
    Domodossola..."

3.  Utente: "D come Domodossola, E come Empoli, L come Livorno, L come
    Livorno"

4.  Sistema: "Ho capito: DELL. Qual è la forma societaria? SRL, SAS,
    SPA..."

5.  Utente: "SAS"

6.  Sistema: "Registro DELL SAS. Confermi?"

**Implementazione:**

-   Usare SttPostProcessor.normalizeSpelling() già esistente

-   Aggiungere SupplierCreation come nuovo ActiveTask

-   Lista forme societarie: SRL, SRLS, SAS, SPA, SNPC, Ditta
    individuale, Cooperativa

-   Fuzzy matching per forma societaria (es. "esse a esse" → "SAS")

3. Dipendenze e Campi Obbligatori

Basato sullo schema Room Database:

  -------------------------------------------------------------------------
  **Entità**        **Campi NOT NULL**          **Dipendenze (FK)**
  ----------------- --------------------------- ---------------------------
  **Product**       id, name, category,         assigneeId?, maintainerIds?
                    location, isActive          

  **Maintenance**   id, productId, date, type   **productId (REQUIRED)**

  **Maintainer**    id, name, isSupplier,       Nessuna
                    isActive                    

  **Location**      id, name, isActive          parentId? (self-reference)
  -------------------------------------------------------------------------

4. Ordine di Implementazione

Implementare nell'ordine seguente per minimizzare dipendenze:

1.  **FIX #5:** Debug MaintenanceEditScreen (issue più critico per UI
    manuale)

2.  **FIX #2:** Nuove AssistantAction (fondamentale per voice flow)

3.  **FIX #3:** completeActiveTask() con salvataggio

4.  **FIX #4:** Handler Save in HomeViewModel

5.  **FIX #1:** NavigationAction.ToNewProduct con prefill

6.  **FIX #6:** Spelling guidato fornitori (lower priority)

5. Test Cases

5.1 Test Vocali

-   "Registra manutenzione per concentratore ossigeno" → verifica DB
    contiene nuova manutenzione

-   "Aggiungi nuovo prodotto letto elettrico in camera 5" → verifica
    navigazione con prefill

-   "Nuova ubicazione Magazzino Piano Terra" → verifica inserimento DB

-   "Nuovo fornitore" → flusso spelling → verifica nome corretto

5.2 Test Manuali UI

-   Crea manutenzione via form → verifica salvataggio senza errori

-   Crea prodotto via form → verifica tutti i campi salvati

-   Crea ubicazione via form → verifica gerarchia parent/child

-   Crea manutentore via form → verifica flag isSupplier

Appendice: File Coinvolti

1.  HomeViewModel.kt - NavigationAction, handleAssistantAction

2.  GeminiService.kt - AssistantAction, completeActiveTask

3.  ConversationContext.kt - ActiveTask classes

4.  Navigation.kt - Route definitions

5.  MaintenanceEditViewModel.kt - save() method

6.  ProductEditViewModel.kt - prefill handling

7.  SttPostProcessor.kt - spelling normalizer
