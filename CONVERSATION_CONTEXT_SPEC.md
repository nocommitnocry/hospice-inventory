# Specifiche: Contesto Conversazionale Gemini

## Problema

L'attuale `GeminiService` è **stateless**: ogni chiamata a Gemini non ha memoria dei turni precedenti. Questo causa perdita del "filo" dopo interruzioni o in flussi multi-step (creazione prodotto, registrazione manutenzione).

**Sintomo tipico**: L'utente dice "OxyGen 3000" dopo che Gemini ha chiesto il nome, ma Gemini non ricorda di aver fatto quella domanda.

---

## Soluzione: Contesto Incrementale + Estrazione Intelligente

### 1. Estendere `ConversationContext`

```kotlin
data class ConversationContext(
    // ESISTENTI
    val currentProduct: Product? = null,
    val lastSearchResults: List<Product> = emptyList(),
    val pendingAction: AssistantAction? = null,
    val awaitingConfirmation: Boolean = false,
    
    // NUOVI
    val activeTask: ActiveTask? = null,
    val recentExchanges: List<ChatExchange> = emptyList(),  // Max 5-6 turni
    val speakerHint: SpeakerHint = SpeakerHint.UNKNOWN
)

data class ChatExchange(
    val role: Role,  // USER, ASSISTANT
    val content: String,
    val timestamp: Instant
)

enum class SpeakerHint { UNKNOWN, LIKELY_MAINTAINER, LIKELY_OPERATOR }
```

### 2. ActiveTask per Flussi Multi-Step

```kotlin
sealed class ActiveTask {
    abstract val startedAt: Instant
    
    data class ProductCreation(
        val name: String? = null,
        val category: String? = null,
        val brand: String? = null,
        val model: String? = null,
        val location: String? = null,
        val purchaseDate: LocalDate? = null,
        val warrantyMonths: Int? = null,
        val barcode: String? = null,
        val notes: String? = null,
        override val startedAt: Instant = Clock.System.now()
    ) : ActiveTask() {
        val requiredMissing: List<String>
            get() = buildList {
                if (name == null) add("nome")
                if (category == null) add("categoria")
                if (location == null) add("ubicazione")
            }
        val isMinimallyComplete: Boolean
            get() = requiredMissing.isEmpty()
    }
    
    data class MaintenanceRegistration(
        val productId: String,
        val productName: String,
        val type: MaintenanceType? = null,
        val description: String? = null,
        val performedBy: String? = null,
        val cost: Double? = null,
        val date: LocalDate? = null,
        override val startedAt: Instant = Clock.System.now()
    ) : ActiveTask() {
        val requiredMissing: List<String>
            get() = buildList {
                if (type == null) add("tipo intervento")
                if (description == null) add("descrizione")
                // performedBy: richiesto solo se speaker=OPERATOR
            }
    }
    
    data class MaintainerCreation(
        val name: String? = null,
        val company: String? = null,
        val email: String? = null,
        val phone: String? = null,
        val specializations: List<String>? = null,
        override val startedAt: Instant = Clock.System.now()
    ) : ActiveTask() {
        val requiredMissing: List<String>
            get() = buildList {
                if (name == null && company == null) add("nome o ragione sociale")
                if (email == null && phone == null) add("contatto (email o telefono)")
            }
    }
}
```

---

## 3. Logica di Estrazione (Prompt Gemini)

**Principio**: Gemini deve **estrarre tutti i campi possibili** da ogni frase, non fare domande una alla volta.

```
ESEMPIO INPUT: "Aggiungi concentratore OxyGen 3000, Philips, stanza 12"

GEMINI DEVE ESTRARRE:
{
  "name": "OxyGen 3000",
  "category": "Apparecchiatura elettromedicale",  // inferito da "concentratore"
  "brand": "Philips",
  "location": "stanza 12"
}

POI RISPONDERE:
"Ho capito: OxyGen 3000, Philips, stanza 12, categoria Apparecchiatura elettromedicale.
Mi manca solo la data di acquisto, oppure procedo così?"
```

**Template prompt estrazione** (da inserire in `buildPrompt`):

```
TASK IN CORSO: [ProductCreation | MaintenanceRegistration | ...]
DATI GIÀ RACCOLTI: [elenco campi valorizzati]
CAMPI MANCANTI OBBLIGATORI: [elenco]

ISTRUZIONI:
1. Estrai TUTTI i campi identificabili dal messaggio utente
2. Per campi enum (categoria, tipo manutenzione, ubicazione): 
   - Se match esatto/sinonimo → usa valore canonico
   - Se ambiguo → chiedi scelta tra opzioni
   - Se non riconosciuto → elenca opzioni disponibili
3. Se utente dice "basta/così/procedi" → segnala STOP
4. Se utente corregge dato precedente → aggiorna

Output JSON:
{
  "extracted": { campo: valore, ... },
  "userWantsToStop": true/false,
  "needsClarification": { campo: [opzioni] } | null
}
```

---

## 4. Matching Campi Enum

### Categorie, Ubicazioni, Tipi Manutenzione

Strategia **ibrida**: match locale veloce → fallback Gemini semantico.

```kotlin
object EnumMatcher {
    
    fun <T> match(
        userInput: String,
        candidates: List<T>,
        getName: (T) -> String,
        getSynonyms: (T) -> List<String>
    ): MatchResult<T> {
        val normalized = userInput.lowercase().trim()
        
        // 1. Match esatto
        candidates.find { getName(it).equals(normalized, ignoreCase = true) }
            ?.let { return MatchResult.Exact(it) }
        
        // 2. Match sinonimo
        candidates.find { candidate ->
            getSynonyms(candidate).any { it.equals(normalized, ignoreCase = true) }
        }?.let { return MatchResult.Synonym(it) }
        
        // 3. Match parziale (contiene)
        candidates.filter { 
            getName(it).contains(normalized, ignoreCase = true) ||
            normalized.contains(getName(it).lowercase())
        }.let { matches ->
            if (matches.size == 1) return MatchResult.Partial(matches[0])
            if (matches.size > 1) return MatchResult.Ambiguous(matches)
        }
        
        // 4. Nessun match → Gemini deciderà
        return MatchResult.NoMatch
    }
    
    sealed class MatchResult<T> {
        data class Exact<T>(val value: T) : MatchResult<T>()
        data class Synonym<T>(val value: T) : MatchResult<T>()
        data class Partial<T>(val value: T) : MatchResult<T>()
        data class Ambiguous<T>(val options: List<T>) : MatchResult<T>()
        class NoMatch<T> : MatchResult<T>()
    }
}
```

### Tipi Manutenzione (Meta-categorie)

```kotlin
enum class MaintenanceType(
    val displayName: String,
    val metaCategory: MetaCategory,
    val synonyms: List<String>
) {
    VERIFICA_PERIODICA("Verifica periodica", ORDINARIA, listOf("verifica", "controllo", "check")),
    SOPRALLUOGO("Sopralluogo", ORDINARIA, listOf("ispezione", "visita")),
    RIPARAZIONE("Riparazione", STRAORDINARIA, listOf("riparato", "aggiustato", "sistemato")),
    SOSTITUZIONE("Sostituzione", STRAORDINARIA, listOf("sostituito", "cambiato")),
    INSTALLAZIONE("Installazione", STRAORDINARIA, listOf("installato", "montato")),
    COLLAUDO("Collaudo", LIFECYCLE, listOf("collaudato", "test iniziale")),
    DISMISSIONE("Dismissione", LIFECYCLE, listOf("dismesso", "smontato", "buttato"));
    
    enum class MetaCategory { ORDINARIA, STRAORDINARIA, LIFECYCLE }
}
```

**Se utente dice "ordinaria"** → rispondere: "Che tipo: verifica periodica o sopralluogo?"

---

## 5. Inferenza Speaker (Manutentore vs Operatore)

L'app non ha login. Inferire chi parla dai pattern linguistici:

```kotlin
object SpeakerInference {
    
    private val FIRST_PERSON = listOf(
        Regex("""(?i)\b(ho|abbiamo)\s+(riparato|fatto|verificato|installato)"""),
        Regex("""(?i)\bfinito\s+(l'intervento|il lavoro)"""),
        Regex("""(?i)\bsono\s+di\s+\w+""")  // "Sono di Tecnomed"
    )
    
    private val THIRD_PERSON = listOf(
        Regex("""(?i)\b(è|sono)\s+venut[oiae]"""),
        Regex("""(?i)\b(ha|hanno)\s+(riparato|fatto)"""),
        Regex("""(?i)\b(la|il)\s+\w+\s+ha\s+""")  // "La Tecnomed ha..."
    )
    
    fun infer(input: String): SpeakerHint {
        val first = FIRST_PERSON.count { it.containsMatchIn(input) }
        val third = THIRD_PERSON.count { it.containsMatchIn(input) }
        
        return when {
            first > third -> SpeakerHint.LIKELY_MAINTAINER
            third > first -> SpeakerHint.LIKELY_OPERATOR
            else -> SpeakerHint.UNKNOWN
        }
    }
}
```

**Conseguenza**: Se `LIKELY_MAINTAINER`, non chiedere "chi ha fatto l'intervento" - è implicito.

---

## 6. Dismissione: Dual-Path

```kotlin
sealed class DismissalFlow {
    /** Serve tecnico per smontaggio (caldaia, montascale) */
    data class WithMaintenance(val productId: String) : DismissalFlow()
    
    /** Dismissione diretta (asta flebo, piccoli oggetti) */
    data class DirectDisposal(val productId: String, val method: DisposalMethod) : DismissalFlow()
}

enum class DisposalMethod {
    RIFIUTI_INGOMBRANTI, RAEE, SMALTIMENTO_SPECIALE, RESO_FORNITORE
}

// Euristica: prodotti "grandi" o elettromedicali → probabilmente serve tecnico
fun Product.needsTechnicianForDismissal(): Boolean {
    val complexKeywords = listOf("caldaia", "montascale", "sollevatore", "letto elettrico", "concentratore")
    return category == "Apparecchiatura elettromedicale" ||
           complexKeywords.any { name.contains(it, ignoreCase = true) }
}
```

---

## 7. Timeout STT

Allungare i tempi di ascolto in `VoiceService`:

```kotlin
private fun createRecognizerIntent(): Intent {
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        // ... esistenti ...
        
        // TIMEOUT ESTESI
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
    }
}
```

---

## 8. Pattern "Basta Così"

```kotlin
object UserIntentDetector {
    private val STOP = listOf("basta", "così", "procedi", "ok così", "va bene", "conferma", "fatto")
    private val CANCEL = listOf("annulla", "lascia stare", "cancella", "lascia perdere")
    
    fun detect(input: String): UserIntent {
        val norm = input.lowercase().trim()
        return when {
            CANCEL.any { norm.contains(it) } -> UserIntent.CANCEL
            STOP.any { norm.contains(it) } -> UserIntent.PROCEED
            else -> UserIntent.CONTINUE
        }
    }
    
    enum class UserIntent { CONTINUE, PROCEED, CANCEL }
}
```

---

## Priorità Implementazione

1. **[ALTA]** Aggiungere `recentExchanges` a `ConversationContext` e includerli nel prompt
2. **[ALTA]** Implementare `ActiveTask` per ProductCreation e MaintenanceRegistration
3. **[MEDIA]** Prompt di estrazione multi-campo per Gemini
4. **[MEDIA]** `EnumMatcher` per categorie/ubicazioni/tipi manutenzione
5. **[MEDIA]** `SpeakerInference` per distinguere manutentore/operatore
6. **[BASSA]** Timeout STT estesi
7. **[BASSA]** Dual-path dismissione

---

## Domanda Aperta

**Verificare**: Quando viene chiamato `resetContext()`? Questo influenza la persistenza del task attivo.
