package org.incammino.hospiceinventory.service.voice

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.incammino.hospiceinventory.domain.model.MaintenanceType
import org.incammino.hospiceinventory.domain.model.Product

// ═══════════════════════════════════════════════════════════════════════════════
// CONVERSATION CONTEXT - Gestione stato conversazionale per flussi multi-step
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Ruolo del partecipante alla conversazione.
 */
enum class Role {
    USER,
    ASSISTANT
}

/**
 * Singolo scambio nella conversazione (turno utente o assistente).
 */
data class ChatExchange(
    val role: Role,
    val content: String,
    val timestamp: Instant = Clock.System.now()
)

/**
 * Hint su chi sta parlando (inferito dal pattern linguistico).
 * L'app non ha login, quindi si deduce dal contesto.
 */
enum class SpeakerHint {
    /** Non è possibile determinare chi parla */
    UNKNOWN,
    /** Probabilmente il manutentore stesso (parla in prima persona: "ho riparato") */
    LIKELY_MAINTAINER,
    /** Probabilmente un operatore dell'hospice (parla in terza persona: "il tecnico ha riparato") */
    LIKELY_OPERATOR
}

/**
 * Contesto della conversazione per risposte più accurate e flussi multi-step.
 *
 * NOTA: Questo contesto vive in memoria e viene perso quando:
 * - L'utente naviga via dalla HomeScreen
 * - L'Activity viene distrutta
 * - L'app viene terminata dal sistema
 *
 * @see HomeViewModel.onCleared per dettagli sul ciclo di vita
 */
data class ConversationContext(
    // === CONTESTO PRODOTTO ===
    /** Prodotto attualmente visualizzato/selezionato */
    val currentProduct: Product? = null,
    /** Ultimi risultati di ricerca */
    val lastSearchResults: List<Product> = emptyList(),

    // === STATO AZIONI ===
    /** Azione in attesa di conferma utente */
    val pendingAction: AssistantAction? = null,
    /** True se stiamo aspettando conferma sì/no */
    val awaitingConfirmation: Boolean = false,

    // === CONTESTO CONVERSAZIONALE (NUOVO) ===
    /** Task multi-step attualmente in corso */
    val activeTask: ActiveTask? = null,
    /** Ultimi scambi della conversazione (max 6 turni per limitare token) */
    val recentExchanges: List<ChatExchange> = emptyList(),
    /** Hint su chi sta parlando (manutentore vs operatore) */
    val speakerHint: SpeakerHint = SpeakerHint.UNKNOWN
) {
    companion object {
        /** Numero massimo di scambi da mantenere in memoria */
        const val MAX_EXCHANGES = 6
    }

    /**
     * Aggiunge uno scambio alla cronologia, mantenendo il limite.
     */
    fun addExchange(exchange: ChatExchange): ConversationContext {
        val updated = (recentExchanges + exchange).takeLast(MAX_EXCHANGES)
        return copy(recentExchanges = updated)
    }

    /**
     * Verifica se c'è un task attivo in corso.
     */
    fun hasActiveTask(): Boolean = activeTask != null

    /**
     * Verifica se il task attivo è completo (tutti i campi obbligatori presenti).
     */
    fun isActiveTaskComplete(): Boolean = activeTask?.isMinimallyComplete == true
}

// ═══════════════════════════════════════════════════════════════════════════════
// ACTIVE TASK - Task multi-step in corso
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Rappresenta un task multi-step in corso (creazione prodotto, registrazione manutenzione, etc.).
 * Accumula i dati raccolti turno dopo turno fino al completamento.
 */
sealed class ActiveTask {
    /** Timestamp di inizio del task */
    abstract val startedAt: Instant

    /** Lista dei campi obbligatori ancora mancanti */
    abstract val requiredMissing: List<String>

    /** True se tutti i campi obbligatori sono stati raccolti */
    val isMinimallyComplete: Boolean
        get() = requiredMissing.isEmpty()

    /**
     * Creazione di un nuovo prodotto nell'inventario.
     */
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

        override val requiredMissing: List<String>
            get() = buildList {
                if (name == null) add("nome")
                if (category == null) add("categoria")
                if (location == null) add("ubicazione")
            }

        /** Campi opzionali ancora non compilati */
        val optionalMissing: List<String>
            get() = buildList {
                if (brand == null) add("marca")
                if (model == null) add("modello")
                if (purchaseDate == null) add("data acquisto")
                if (warrantyMonths == null) add("durata garanzia")
                if (barcode == null) add("codice a barre")
            }

        /** Descrizione dei dati già raccolti per il prompt */
        fun toCollectedDataString(): String = buildString {
            name?.let { appendLine("- Nome: $it") }
            category?.let { appendLine("- Categoria: $it") }
            brand?.let { appendLine("- Marca: $it") }
            model?.let { appendLine("- Modello: $it") }
            location?.let { appendLine("- Ubicazione: $it") }
            purchaseDate?.let { appendLine("- Data acquisto: $it") }
            warrantyMonths?.let { appendLine("- Garanzia: $it mesi") }
            barcode?.let { appendLine("- Barcode: $it") }
            notes?.let { appendLine("- Note: $it") }
        }.ifEmpty { "(nessun dato ancora raccolto)" }
    }

    /**
     * Registrazione di un intervento di manutenzione su un prodotto esistente.
     */
    data class MaintenanceRegistration(
        val productId: String,
        val productName: String,
        val type: MaintenanceType? = null,
        val description: String? = null,
        val performedBy: String? = null,
        val cost: Double? = null,
        val date: LocalDate? = null,
        val isWarrantyWork: Boolean? = null,
        override val startedAt: Instant = Clock.System.now()
    ) : ActiveTask() {

        override val requiredMissing: List<String>
            get() = buildList {
                if (type == null) add("tipo intervento")
                if (description == null) add("descrizione")
                // performedBy è richiesto solo se speaker = OPERATOR (verificato esternamente)
            }

        /** Descrizione dei dati già raccolti per il prompt */
        fun toCollectedDataString(): String = buildString {
            appendLine("- Prodotto: $productName (ID: $productId)")
            type?.let { appendLine("- Tipo: ${it.displayName}") }
            description?.let { appendLine("- Descrizione: $it") }
            performedBy?.let { appendLine("- Eseguito da: $it") }
            cost?.let { appendLine("- Costo: €$it") }
            date?.let { appendLine("- Data: $it") }
            isWarrantyWork?.let { appendLine("- In garanzia: ${if (it) "Sì" else "No"}") }
        }
    }

    /**
     * Creazione di un nuovo manutentore/fornitore.
     */
    data class MaintainerCreation(
        val name: String? = null,
        val company: String? = null,
        val email: String? = null,
        val phone: String? = null,
        val address: String? = null,
        val city: String? = null,
        val specializations: List<String>? = null,
        val isSupplier: Boolean? = null,
        override val startedAt: Instant = Clock.System.now()
    ) : ActiveTask() {

        override val requiredMissing: List<String>
            get() = buildList {
                if (name == null && company == null) add("nome o ragione sociale")
                if (email == null && phone == null) add("contatto (email o telefono)")
            }

        /** Descrizione dei dati già raccolti per il prompt */
        fun toCollectedDataString(): String = buildString {
            name?.let { appendLine("- Nome: $it") }
            company?.let { appendLine("- Azienda: $it") }
            email?.let { appendLine("- Email: $it") }
            phone?.let { appendLine("- Telefono: $it") }
            address?.let { appendLine("- Indirizzo: $it") }
            city?.let { appendLine("- Città: $it") }
            specializations?.let { appendLine("- Specializzazioni: ${it.joinToString(", ")}") }
            isSupplier?.let { appendLine("- È fornitore: ${if (it) "Sì" else "No"}") }
        }.ifEmpty { "(nessun dato ancora raccolto)" }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SPEAKER INFERENCE - Inferenza di chi sta parlando
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Inferisce chi sta parlando basandosi sui pattern linguistici.
 * L'app non ha sistema di login, quindi si deduce dal modo di parlare.
 */
object SpeakerInference {

    // Pattern che indicano prima persona (manutentore che parla di sé)
    private val FIRST_PERSON_PATTERNS = listOf(
        Regex("""(?i)\b(ho|abbiamo)\s+(riparato|fatto|verificato|installato|sostituito|sistemato|controllato)"""),
        Regex("""(?i)\bfinito\s+(l'intervento|il lavoro|la riparazione|la manutenzione)"""),
        Regex("""(?i)\bsono\s+(di|della|del)\s+\w+"""),  // "Sono di Tecnomed"
        Regex("""(?i)\bsiamo\s+(di|della|del|venuti)\s*"""),
        Regex("""(?i)\b(sono|siamo)\s+intervenut[oiae]"""),
        Regex("""(?i)\bho\s+(appena|già)\s+""")
    )

    // Pattern che indicano terza persona (operatore che riferisce)
    private val THIRD_PERSON_PATTERNS = listOf(
        Regex("""(?i)\b(è|sono)\s+venut[oiae]"""),
        Regex("""(?i)\b(ha|hanno)\s+(riparato|fatto|verificato|installato|sostituito|sistemato)"""),
        Regex("""(?i)\b(il|la|i|le)\s+(tecnico|tecnici|manutentore|manutentori|ditta)\s+ha"""),
        Regex("""(?i)\b(il|la)\s+\w+\s+ha\s+(fatto|riparato|sistemato)"""),  // "La Tecnomed ha riparato"
        Regex("""(?i)\bhanno\s+detto\s+che"""),
        Regex("""(?i)\b(mi|ci)\s+hanno\s+""")
    )

    /**
     * Inferisce chi sta parlando basandosi sul testo.
     *
     * @param input Il messaggio dell'utente
     * @return SpeakerHint che indica la probabilità di chi sta parlando
     */
    fun infer(input: String): SpeakerHint {
        val firstPersonScore = FIRST_PERSON_PATTERNS.count { it.containsMatchIn(input) }
        val thirdPersonScore = THIRD_PERSON_PATTERNS.count { it.containsMatchIn(input) }

        return when {
            firstPersonScore > thirdPersonScore -> SpeakerHint.LIKELY_MAINTAINER
            thirdPersonScore > firstPersonScore -> SpeakerHint.LIKELY_OPERATOR
            else -> SpeakerHint.UNKNOWN
        }
    }

    /**
     * Aggiorna lo speaker hint combinando il nuovo con quello esistente.
     * Se il nuovo hint è più forte, lo usa; altrimenti mantiene l'esistente.
     */
    fun updateHint(current: SpeakerHint, new: SpeakerHint): SpeakerHint {
        return when {
            current == SpeakerHint.UNKNOWN -> new
            new == SpeakerHint.UNKNOWN -> current
            else -> new  // Il più recente vince se entrambi hanno un valore
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// USER INTENT DETECTOR - Rilevamento intenzioni "basta così", "annulla"
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Rileva l'intenzione dell'utente riguardo al proseguimento del task.
 */
object UserIntentDetector {

    // Frasi che indicano "procedi con quello che hai"
    private val PROCEED_PHRASES = listOf(
        "basta", "così", "procedi", "ok così", "va bene", "conferma",
        "fatto", "ok", "bene così", "va bene così", "salva", "registra",
        "sì", "si", "esatto", "perfetto", "confermo"
    )

    // Frasi che indicano "annulla tutto"
    private val CANCEL_PHRASES = listOf(
        "annulla", "lascia stare", "cancella", "lascia perdere",
        "non importa", "stop", "ferma", "no", "niente", "abbandona"
    )

    /**
     * Intenzione rilevata dall'utente.
     */
    enum class UserIntent {
        /** L'utente vuole continuare a fornire dati */
        CONTINUE,
        /** L'utente vuole procedere/salvare con i dati attuali */
        PROCEED,
        /** L'utente vuole annullare il task */
        CANCEL
    }

    /**
     * Rileva l'intenzione dell'utente dal suo messaggio.
     *
     * @param input Il messaggio dell'utente
     * @return L'intenzione rilevata
     */
    fun detect(input: String): UserIntent {
        val normalized = input.lowercase().trim()

        // Controlla prima le frasi di cancellazione (hanno priorità)
        if (CANCEL_PHRASES.any { normalized.contains(it) }) {
            return UserIntent.CANCEL
        }

        // Poi controlla le frasi di conferma/procedere
        if (PROCEED_PHRASES.any { normalized == it || normalized.startsWith("$it ") || normalized.endsWith(" $it") }) {
            return UserIntent.PROCEED
        }

        // Default: l'utente sta fornendo altri dati
        return UserIntent.CONTINUE
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ENUM MATCHER - Matching fuzzy per categorie, ubicazioni, tipi manutenzione
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Matcher generico per campi enum con supporto sinonimi e match parziale.
 * Strategia: match locale veloce → fallback Gemini per casi ambigui.
 */
object EnumMatcher {

    /**
     * Risultato del matching.
     */
    sealed class MatchResult<T> {
        /** Match esatto trovato */
        data class Exact<T>(val value: T) : MatchResult<T>()
        /** Match tramite sinonimo */
        data class Synonym<T>(val value: T) : MatchResult<T>()
        /** Match parziale (una sola corrispondenza) */
        data class Partial<T>(val value: T) : MatchResult<T>()
        /** Match ambiguo (più corrispondenze possibili) */
        data class Ambiguous<T>(val options: List<T>) : MatchResult<T>()
        /** Nessun match trovato */
        class NoMatch<T> : MatchResult<T>()
    }

    /**
     * Cerca un match per l'input utente tra i candidati.
     *
     * @param userInput L'input dell'utente da matchare
     * @param candidates Lista di candidati possibili
     * @param getName Funzione per ottenere il nome display del candidato
     * @param getSynonyms Funzione per ottenere i sinonimi del candidato
     * @return Risultato del matching
     */
    fun <T> match(
        userInput: String,
        candidates: List<T>,
        getName: (T) -> String,
        getSynonyms: (T) -> List<String> = { emptyList() }
    ): MatchResult<T> {
        val normalized = userInput.lowercase().trim()

        // 1. Match esatto sul nome
        candidates.find { getName(it).equals(normalized, ignoreCase = true) }
            ?.let { return MatchResult.Exact(it) }

        // 2. Match esatto su sinonimo
        candidates.find { candidate ->
            getSynonyms(candidate).any { it.equals(normalized, ignoreCase = true) }
        }?.let { return MatchResult.Synonym(it) }

        // 3. Match parziale (contiene)
        val partialMatches = candidates.filter { candidate ->
            val name = getName(candidate).lowercase()
            name.contains(normalized) || normalized.contains(name) ||
                    getSynonyms(candidate).any { syn ->
                        syn.lowercase().contains(normalized) || normalized.contains(syn.lowercase())
                    }
        }

        return when (partialMatches.size) {
            0 -> MatchResult.NoMatch()
            1 -> MatchResult.Partial(partialMatches[0])
            else -> MatchResult.Ambiguous(partialMatches)
        }
    }

    /**
     * Match specifico per tipi di manutenzione con meta-categorie.
     */
    fun matchMaintenanceType(userInput: String): MatchResult<MaintenanceType> {
        val normalized = userInput.lowercase().trim()

        // Gestione speciale per meta-categorie
        if (normalized in listOf("ordinaria", "manutenzione ordinaria")) {
            val ordinaryTypes = MaintenanceType.entries.filter {
                it.metaCategory == MaintenanceType.MetaCategory.ORDINARIA
            }
            return MatchResult.Ambiguous(ordinaryTypes)
        }

        if (normalized in listOf("straordinaria", "manutenzione straordinaria")) {
            val extraordinaryTypes = MaintenanceType.entries.filter {
                it.metaCategory == MaintenanceType.MetaCategory.STRAORDINARIA
            }
            return MatchResult.Ambiguous(extraordinaryTypes)
        }

        // Match standard
        return match(
            userInput = userInput,
            candidates = MaintenanceType.entries,
            getName = { it.displayName },
            getSynonyms = { it.synonyms }
        )
    }
}
