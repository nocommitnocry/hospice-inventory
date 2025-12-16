package org.incammino.hospiceinventory.service.voice

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.ResponseStoppedException
import com.google.ai.client.generativeai.type.SerializationException
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.incammino.hospiceinventory.data.repository.MaintainerRepository
import org.incammino.hospiceinventory.data.repository.ProductRepository
import org.incammino.hospiceinventory.domain.model.MaintenanceType
import org.incammino.hospiceinventory.domain.model.Product
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

// ═══════════════════════════════════════════════════════════════════════════════
// RESULT TYPES
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Risultato di una richiesta a Gemini.
 */
sealed class GeminiResult {
    data class Success(val response: String) : GeminiResult()
    data class Error(val message: String, val errorType: ErrorType = ErrorType.GENERIC) : GeminiResult()
    data class ActionRequired(
        val action: AssistantAction,
        val response: String
    ) : GeminiResult()

    /**
     * Azione che richiede conferma esplicita dell'utente.
     */
    data class ConfirmationNeeded(
        val action: AssistantAction,
        val response: String,
        val confirmationMessage: String
    ) : GeminiResult()
}

/**
 * Tipi di errore per gestione differenziata.
 */
enum class ErrorType {
    GENERIC,
    RATE_LIMITED,
    INVALID_INPUT,
    SUSPICIOUS_INPUT,
    NETWORK_ERROR
}

// ═══════════════════════════════════════════════════════════════════════════════
// ACTIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Livello di rischio delle azioni.
 */
enum class ActionRiskLevel {
    LOW,      // Ricerca, visualizzazione - esecuzione diretta
    MEDIUM,   // Creazione, modifica - conferma semplice
    HIGH      // Eliminazione, invio email - conferma esplicita con dettagli
}

/**
 * Azioni che l'assistente può richiedere.
 */
sealed class AssistantAction {
    abstract val riskLevel: ActionRiskLevel

    data class SearchProducts(val query: String) : AssistantAction() {
        override val riskLevel = ActionRiskLevel.LOW
    }

    data class ShowProduct(val productId: String) : AssistantAction() {
        override val riskLevel = ActionRiskLevel.LOW
    }

    data class CreateProduct(val prefillData: Map<String, String>?) : AssistantAction() {
        override val riskLevel = ActionRiskLevel.MEDIUM
    }

    data class ShowMaintenanceList(val filter: String?) : AssistantAction() {
        override val riskLevel = ActionRiskLevel.LOW
    }

    data class PrepareEmail(val productId: String, val description: String) : AssistantAction() {
        override val riskLevel = ActionRiskLevel.HIGH
    }

    data class ScanBarcode(val reason: String) : AssistantAction() {
        override val riskLevel = ActionRiskLevel.LOW
    }

    data object ShowOverdueAlerts : AssistantAction() {
        override val riskLevel = ActionRiskLevel.LOW
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CONTEXT
// ═══════════════════════════════════════════════════════════════════════════════
// ConversationContext, ActiveTask, ChatExchange, SpeakerHint, SpeakerInference,
// UserIntentDetector ed EnumMatcher sono definiti in ConversationContext.kt

// ═══════════════════════════════════════════════════════════════════════════════
// INPUT SANITIZER
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Sanitizza e valida l'input utente per prevenire prompt injection.
 */
object InputSanitizer {

    private const val TAG = "InputSanitizer"
    private const val MAX_INPUT_LENGTH = 500

    // Pattern sospetti - solo i più critici
    private val SUSPICIOUS_PATTERNS = listOf(
        // Tentativi di override istruzioni di sistema
        Regex("""(?i)(ignora|ignore|dimentica).*(system|istruzioni di sistema)"""),
        // Tentativi di esecuzione codice
        Regex("""\$\{.*\}"""),
        // Path traversal
        Regex("""\.\./\.\./""")
    )

    // Caratteri da rimuovere o sostituire
    private val CHARS_TO_REMOVE = listOf(
        '\u0000', '\u0001', '\u0002', '\u0003', // Null e control chars
        '\u200B', '\u200C', '\u200D', '\uFEFF'  // Zero-width chars (possono nascondere testo)
    )

    /**
     * Risultato della sanitizzazione.
     */
    sealed class SanitizeResult {
        data class Clean(val sanitizedInput: String) : SanitizeResult()
        data class Suspicious(val reason: String, val sanitizedInput: String) : SanitizeResult()
        data class Rejected(val reason: String) : SanitizeResult()
    }

    /**
     * Sanitizza l'input utente.
     */
    fun sanitize(input: String): SanitizeResult {
        // 1. Controlla lunghezza
        if (input.length > MAX_INPUT_LENGTH) {
            Log.w(TAG, "Input troppo lungo: ${input.length} caratteri")
            return SanitizeResult.Rejected("Input troppo lungo (max $MAX_INPUT_LENGTH caratteri)")
        }

        // 2. Rimuovi caratteri invisibili/pericolosi
        var cleaned = input
        CHARS_TO_REMOVE.forEach { char ->
            cleaned = cleaned.replace(char.toString(), "")
        }

        // 3. Normalizza spazi multipli
        cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()

        // 4. Controlla se vuoto dopo pulizia
        if (cleaned.isBlank()) {
            return SanitizeResult.Rejected("Input vuoto")
        }

        // 5. Controlla pattern sospetti
        for (pattern in SUSPICIOUS_PATTERNS) {
            if (pattern.containsMatchIn(cleaned)) {
                Log.w(TAG, "Pattern sospetto rilevato: ${pattern.pattern}")
                // Non rifiutiamo, ma segnaliamo e limitiamo
                return SanitizeResult.Suspicious(
                    reason = "Pattern sospetto rilevato",
                    sanitizedInput = cleaned.take(100) // Limita ulteriormente
                )
            }
        }

        return SanitizeResult.Clean(cleaned)
    }

    /**
     * Sanitizza specificamente input da barcode/QR.
     * Più restrittivo perché i QR possono contenere payload malevoli.
     */
    fun sanitizeBarcode(input: String): SanitizeResult {
        // Barcode validi contengono solo alfanumerici e alcuni simboli
        val barcodePattern = Regex("""^[A-Za-z0-9\-_.]+$""")

        val cleaned = input.trim()

        if (cleaned.length > 100) {
            Log.w(TAG, "Barcode troppo lungo: ${cleaned.length}")
            return SanitizeResult.Rejected("Codice troppo lungo")
        }

        if (!barcodePattern.matches(cleaned)) {
            Log.w(TAG, "Barcode con caratteri non validi: $cleaned")
            // Estrai solo caratteri validi
            val sanitized = cleaned.filter { it.isLetterOrDigit() || it in "-_." }
            if (sanitized.isEmpty()) {
                return SanitizeResult.Rejected("Codice non valido")
            }
            return SanitizeResult.Suspicious("Caratteri rimossi dal codice", sanitized)
        }

        return SanitizeResult.Clean(cleaned)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// RATE LIMITER
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Rate limiter per prevenire abusi.
 */
class RateLimiter(
    private val maxRequests: Int = 10,
    private val windowDuration: kotlin.time.Duration = 1.minutes
) {
    private val requestTimestamps = mutableListOf<Instant>()
    private val mutex = Mutex()

    /**
     * Verifica se una nuova richiesta è permessa.
     * @return true se permessa, false se rate limited
     */
    suspend fun tryAcquire(): Boolean = mutex.withLock {
        val now = Clock.System.now()
        val windowStart = now - windowDuration

        // Rimuovi timestamp vecchi
        requestTimestamps.removeAll { it < windowStart }

        // Controlla se sotto il limite
        if (requestTimestamps.size >= maxRequests) {
            Log.w("RateLimiter", "Rate limit raggiunto: ${requestTimestamps.size}/$maxRequests")
            return false
        }

        // Registra nuova richiesta
        requestTimestamps.add(now)
        return true
    }

    /**
     * Richieste rimanenti nella finestra corrente.
     */
    suspend fun remainingRequests(): Int = mutex.withLock {
        val now = Clock.System.now()
        val windowStart = now - windowDuration
        requestTimestamps.removeAll { it < windowStart }
        return maxRequests - requestTimestamps.size
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AUDIT LOGGER
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Logger per audit delle operazioni AI.
 */
object AuditLogger {
    private const val TAG = "GeminiAudit"

    /**
     * Tipo di evento da loggare.
     */
    enum class EventType {
        REQUEST,
        RESPONSE,
        ACTION_REQUESTED,
        ACTION_CONFIRMED,
        ACTION_REJECTED,
        SUSPICIOUS_INPUT,
        RATE_LIMITED,
        ERROR
    }

    /**
     * Logga un evento.
     */
    fun log(
        eventType: EventType,
        message: String,
        details: Map<String, Any?> = emptyMap()
    ) {
        val timestamp = Clock.System.now()
        val detailsStr = if (details.isNotEmpty()) {
            details.entries.joinToString(", ") { "${it.key}=${it.value}" }
        } else ""

        val logMessage = "[$eventType] $message ${if (detailsStr.isNotEmpty()) "| $detailsStr" else ""}"

        when (eventType) {
            EventType.SUSPICIOUS_INPUT, EventType.RATE_LIMITED -> Log.w(TAG, logMessage)
            EventType.ERROR -> Log.e(TAG, logMessage)
            else -> Log.i(TAG, logMessage)
        }

        // In futuro: salvare su file/database per audit trail persistente
    }

    /**
     * Logga una richiesta utente (sanitizzata).
     */
    fun logRequest(sanitizedInput: String, isSuspicious: Boolean = false) {
        log(
            eventType = if (isSuspicious) EventType.SUSPICIOUS_INPUT else EventType.REQUEST,
            message = "User input",
            details = mapOf(
                "input" to sanitizedInput.take(50), // Log solo primi 50 char
                "length" to sanitizedInput.length,
                "suspicious" to isSuspicious
            )
        )
    }

    /**
     * Logga un'azione richiesta.
     */
    fun logAction(action: AssistantAction, confirmed: Boolean? = null) {
        val eventType = when (confirmed) {
            true -> EventType.ACTION_CONFIRMED
            false -> EventType.ACTION_REJECTED
            null -> EventType.ACTION_REQUESTED
        }

        log(
            eventType = eventType,
            message = "Action: ${action::class.simpleName}",
            details = mapOf(
                "riskLevel" to action.riskLevel,
                "confirmed" to confirmed
            )
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GEMINI SERVICE
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Servizio per interagire con Gemini AI.
 * Include protezioni contro prompt injection, rate limiting e audit logging.
 */
@Singleton
class GeminiService @Inject constructor(
    private val generativeModel: GenerativeModel,
    private val productRepository: ProductRepository,
    private val maintainerRepository: MaintainerRepository  // P3 FIX: Per contesto manutentori
) {
    private var conversationContext = ConversationContext()
    private val rateLimiter = RateLimiter(maxRequests = 15, windowDuration = 1.minutes)

    companion object {
        private const val TAG = "GeminiService"
    }

    /**
     * Processa un messaggio dell'utente e restituisce una risposta.
     * Include sanitizzazione, rate limiting, gestione conferme e contesto conversazionale.
     */
    suspend fun processMessage(userMessage: String): GeminiResult {
        // 1. Rate limiting
        if (!rateLimiter.tryAcquire()) {
            AuditLogger.log(AuditLogger.EventType.RATE_LIMITED, "Request blocked")
            return GeminiResult.Error(
                "Troppe richieste. Attendi un momento prima di riprovare.",
                ErrorType.RATE_LIMITED
            )
        }

        // 2. Sanitizzazione input
        val sanitizeResult = InputSanitizer.sanitize(userMessage)
        val sanitizedMessage: String
        var isSuspicious = false

        when (sanitizeResult) {
            is InputSanitizer.SanitizeResult.Clean -> {
                sanitizedMessage = sanitizeResult.sanitizedInput
            }
            is InputSanitizer.SanitizeResult.Suspicious -> {
                sanitizedMessage = sanitizeResult.sanitizedInput
                isSuspicious = true
                AuditLogger.logRequest(sanitizedMessage, isSuspicious = true)
            }
            is InputSanitizer.SanitizeResult.Rejected -> {
                AuditLogger.log(
                    AuditLogger.EventType.SUSPICIOUS_INPUT,
                    "Input rejected: ${sanitizeResult.reason}"
                )
                return GeminiResult.Error(sanitizeResult.reason, ErrorType.INVALID_INPUT)
            }
        }

        // 3. Log richiesta e registra scambio
        AuditLogger.logRequest(sanitizedMessage, isSuspicious)
        conversationContext = conversationContext.addExchange(
            ChatExchange(Role.USER, sanitizedMessage)
        )

        // 4. Inferenza speaker (manutentore vs operatore)
        val inferredSpeaker = SpeakerInference.infer(sanitizedMessage)
        if (inferredSpeaker != SpeakerHint.UNKNOWN) {
            conversationContext = conversationContext.copy(
                speakerHint = SpeakerInference.updateHint(
                    conversationContext.speakerHint,
                    inferredSpeaker
                )
            )
            Log.d(TAG, "Speaker hint updated: ${conversationContext.speakerHint}")
        }

        // 5. Gestione conferma pendente
        if (conversationContext.awaitingConfirmation && conversationContext.pendingAction != null) {
            return handleConfirmationResponse(sanitizedMessage)
        }

        // 6. Rileva intent utente (basta così, annulla, continua)
        val userIntent = UserIntentDetector.detect(sanitizedMessage)

        // 7. Gestione task attivo
        if (conversationContext.hasActiveTask()) {
            when (userIntent) {
                UserIntentDetector.UserIntent.CANCEL -> {
                    val taskType = conversationContext.activeTask?.let { it::class.simpleName } ?: "task"
                    conversationContext = conversationContext.copy(activeTask = null)
                    return addAssistantExchangeAndReturn(
                        GeminiResult.Success("Ok, ho annullato il $taskType. Come posso aiutarti?")
                    )
                }
                UserIntentDetector.UserIntent.PROCEED -> {
                    if (conversationContext.isActiveTaskComplete()) {
                        return completeActiveTask()
                    } else {
                        val missing = conversationContext.activeTask?.requiredMissing?.joinToString(", ")
                        return addAssistantExchangeAndReturn(
                            GeminiResult.Success("Mi mancano ancora: $missing. Vuoi fornirli o procedere comunque?")
                        )
                    }
                }
                UserIntentDetector.UserIntent.CONTINUE -> {
                    // Continua normalmente con Gemini per estrarre dati
                }
            }
        }

        // 8. Processa con Gemini
        return withContext(Dispatchers.IO) {
            try {
                val contextPrompt = buildContextPrompt()
                val fullPrompt = buildPrompt(contextPrompt, sanitizedMessage, isSuspicious)

                val response = generativeModel.generateContent(
                    content { text(fullPrompt) }
                )

                val responseText = response.text ?: "Mi dispiace, non ho capito."

                AuditLogger.log(AuditLogger.EventType.RESPONSE, "AI response received")

                // Estrai eventuali azioni dalla risposta
                val (cleanResponse, action) = parseResponse(responseText)

                // Registra risposta assistente
                conversationContext = conversationContext.addExchange(
                    ChatExchange(Role.ASSISTANT, cleanResponse)
                )

                // P1 FIX: Se c'è un task attivo di MaintenanceRegistration e viene richiesta
                // una ricerca, eseguila internamente invece di navigare
                val finalAction = if (action is AssistantAction.SearchProducts &&
                    conversationContext.activeTask is ActiveTask.MaintenanceRegistration
                ) {
                    handleInternalSearchForMaintenance(action.query)
                } else {
                    action
                }

                when {
                    finalAction == null -> GeminiResult.Success(cleanResponse)
                    finalAction.riskLevel == ActionRiskLevel.LOW -> {
                        AuditLogger.logAction(finalAction)
                        GeminiResult.ActionRequired(finalAction, cleanResponse)
                    }
                    else -> {
                        // Azioni a rischio medio/alto richiedono conferma
                        AuditLogger.logAction(finalAction)
                        requestConfirmation(finalAction, cleanResponse)
                    }
                }
            } catch (e: ResponseStoppedException) {
                // Risposta troncata per limite token o content filter
                Log.w(TAG, "Response stopped: ${e.message}")
                AuditLogger.log(AuditLogger.EventType.ERROR, "Response stopped: ${e.message}")
                GeminiResult.Success("Mi dispiace, puoi ripetere in modo più specifico?")
            } catch (e: SerializationException) {
                // Risposta vuota o malformata dal server
                Log.w(TAG, "Serialization error: ${e.message}")
                AuditLogger.log(AuditLogger.EventType.ERROR, "Serialization error: ${e.message}")
                GeminiResult.Success("Non ho capito, puoi riformulare?")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message", e)
                AuditLogger.log(AuditLogger.EventType.ERROR, "Processing error: ${e.message}")
                GeminiResult.Error("Errore di comunicazione: ${e.message}", ErrorType.NETWORK_ERROR)
            }
        }
    }

    /**
     * Aggiunge uno scambio assistente e restituisce il risultato.
     */
    private fun addAssistantExchangeAndReturn(result: GeminiResult): GeminiResult {
        val message = when (result) {
            is GeminiResult.Success -> result.response
            is GeminiResult.ActionRequired -> result.response
            is GeminiResult.ConfirmationNeeded -> result.response
            is GeminiResult.Error -> result.message
        }
        conversationContext = conversationContext.addExchange(
            ChatExchange(Role.ASSISTANT, message)
        )
        return result
    }

    /**
     * Completa il task attivo e restituisce l'azione appropriata.
     */
    private fun completeActiveTask(): GeminiResult {
        val task = conversationContext.activeTask ?: return GeminiResult.Success("Nessun task attivo.")

        return when (task) {
            is ActiveTask.ProductCreation -> {
                val prefillData = buildMap {
                    task.name?.let { put("name", it) }
                    task.category?.let { put("category", it) }
                    task.brand?.let { put("brand", it) }
                    task.model?.let { put("model", it) }
                    task.location?.let { put("location", it) }
                    task.purchaseDate?.let { put("purchaseDate", it.toString()) }
                    task.warrantyMonths?.let { put("warrantyMonths", it.toString()) }
                    task.barcode?.let { put("barcode", it) }
                    task.notes?.let { put("notes", it) }
                }
                conversationContext = conversationContext.copy(activeTask = null)
                addAssistantExchangeAndReturn(
                    GeminiResult.ActionRequired(
                        AssistantAction.CreateProduct(prefillData),
                        "Perfetto! Apro la schermata di creazione prodotto con i dati raccolti."
                    )
                )
            }
            is ActiveTask.MaintenanceRegistration -> {
                // Per ora restituisce un messaggio, in futuro implementeremo l'azione
                conversationContext = conversationContext.copy(activeTask = null)
                addAssistantExchangeAndReturn(
                    GeminiResult.Success(
                        "Manutenzione pronta per la registrazione:\n${task.toCollectedDataString()}"
                    )
                )
            }
            is ActiveTask.MaintainerCreation -> {
                conversationContext = conversationContext.copy(activeTask = null)
                addAssistantExchangeAndReturn(
                    GeminiResult.Success(
                        "Manutentore pronto per la registrazione:\n${task.toCollectedDataString()}"
                    )
                )
            }
        }
    }

    /**
     * Costruisce il prompt di contesto per Gemini.
     * Include cronologia conversazione, task attivo e contesto prodotto.
     */
    private fun buildPrompt(
        contextPrompt: String,
        userMessage: String,
        @Suppress("UNUSED_PARAMETER") isSuspicious: Boolean
    ): String {
        val conversationHistory = buildConversationHistoryPrompt()
        val activeTaskPrompt = buildActiveTaskPrompt()
        val speakerHintPrompt = buildSpeakerHintPrompt()

        return """
            |SISTEMA: Sei l'assistente vocale di Hospice Inventory per l'Hospice di Abbiategrasso.
            |Rispondi sempre in italiano, in modo conciso e naturale.
            |
            |$contextPrompt
            |$conversationHistory
            |$activeTaskPrompt
            |$speakerHintPrompt
            |
            |MESSAGGIO UTENTE: $userMessage
            |
            |ISTRUZIONI:
            |1. Se c'è un TASK IN CORSO, estrai TUTTI i dati possibili dal messaggio
            |2. Per campi enum (categoria, tipo manutenzione): se ambiguo, chiedi scelta
            |3. Se l'utente corregge un dato precedente, aggiorna
            |4. Rispondi in modo naturale e conciso
            |
            |Se serve un'azione, aggiungi il tag: [ACTION:tipo:parametri]
            |
            |Azioni disponibili:
            |- SEARCH:query - Cerca prodotti
            |- SHOW:productId - Mostra dettaglio prodotto
            |- CREATE:campo=valore,campo2=valore2 - Nuovo prodotto
            |- START_PRODUCT_CREATION - Avvia creazione prodotto guidata
            |- START_MAINTENANCE:productId:productName - Avvia registrazione manutenzione
            |- MAINTENANCE_LIST:filtro - Lista manutenzioni
            |- EMAIL:productId:descrizione - Email manutentore
            |- SCAN:motivo - Scanner barcode
            |- ALERTS - Mostra scadenze
        """.trimMargin()
    }

    /**
     * Costruisce la sezione del prompt con la cronologia conversazione.
     */
    private fun buildConversationHistoryPrompt(): String {
        if (conversationContext.recentExchanges.isEmpty()) {
            return ""
        }

        val history = conversationContext.recentExchanges
            .dropLast(1) // L'ultimo messaggio utente è già nel prompt principale
            .takeLast(5) // Max 5 scambi precedenti
            .joinToString("\n") { exchange ->
                val role = if (exchange.role == Role.USER) "UTENTE" else "ASSISTENTE"
                "$role: ${exchange.content}"
            }

        return if (history.isNotBlank()) {
            """
            |
            |CONVERSAZIONE RECENTE:
            |$history
            """.trimMargin()
        } else ""
    }

    /**
     * Costruisce la sezione del prompt per il task attivo.
     */
    private fun buildActiveTaskPrompt(): String {
        val task = conversationContext.activeTask ?: return ""

        return when (task) {
            is ActiveTask.ProductCreation -> """
                |
                |TASK IN CORSO: Creazione nuovo prodotto
                |DATI GIÀ RACCOLTI:
                |${task.toCollectedDataString()}
                |CAMPI OBBLIGATORI MANCANTI: ${task.requiredMissing.joinToString(", ").ifEmpty { "nessuno" }}
                |CAMPI OPZIONALI MANCANTI: ${task.optionalMissing.joinToString(", ")}
                |
                |ISTRUZIONI TASK:
                |- Estrai TUTTI i campi identificabili dal messaggio utente
                |- Se l'utente dice "basta/così/procedi", non estrarre altri dati
                |- Categorie valide: Apparecchiatura elettromedicale, Attrezzatura sanitaria, Arredo, Informatica, Altro
                |- Ubicazioni: usa il formato fornito dall'utente (es. "stanza 12", "magazzino")
            """.trimMargin()

            is ActiveTask.MaintenanceRegistration -> """
                |
                |TASK IN CORSO: Registrazione manutenzione per ${task.productName}
                |DATI GIÀ RACCOLTI:
                |${task.toCollectedDataString()}
                |CAMPI OBBLIGATORI MANCANTI: ${task.requiredMissing.joinToString(", ").ifEmpty { "nessuno" }}
                |
                |ISTRUZIONI TASK:
                |- Estrai tipo intervento, descrizione, chi l'ha fatto, costo se menzionato
                |- Tipi validi: Programmata, Verifica, Riparazione, Sostituzione, Installazione, Collaudo, Dismissione, Straordinaria
                |- Se utente dice "ordinaria" chiedi: "Verifica periodica o manutenzione programmata?"
                |- Se utente dice "straordinaria" chiedi il tipo specifico
            """.trimMargin()

            is ActiveTask.MaintainerCreation -> """
                |
                |TASK IN CORSO: Creazione nuovo manutentore/fornitore
                |DATI GIÀ RACCOLTI:
                |${task.toCollectedDataString()}
                |CAMPI OBBLIGATORI MANCANTI: ${task.requiredMissing.joinToString(", ").ifEmpty { "nessuno" }}
                |
                |ISTRUZIONI TASK:
                |- Estrai nome/azienda, contatti (email, telefono), indirizzo se fornito
                |- Chiedi se è anche fornitore oltre che manutentore
            """.trimMargin()
        }
    }

    /**
     * Costruisce la sezione del prompt per lo speaker hint.
     */
    private fun buildSpeakerHintPrompt(): String {
        return when (conversationContext.speakerHint) {
            SpeakerHint.LIKELY_MAINTAINER -> """
                |
                |NOTA: L'utente sembra essere il MANUTENTORE stesso (parla in prima persona).
                |Non chiedere "chi ha fatto l'intervento" - è implicito che sia lui.
            """.trimMargin()

            SpeakerHint.LIKELY_OPERATOR -> """
                |
                |NOTA: L'utente sembra essere un OPERATORE dell'hospice (parla in terza persona).
                |Chiedi chi ha eseguito l'intervento se non specificato.
            """.trimMargin()

            SpeakerHint.UNKNOWN -> ""
        }
    }

    /**
     * Richiede conferma per azioni a rischio medio/alto.
     */
    private fun requestConfirmation(
        action: AssistantAction,
        aiResponse: String
    ): GeminiResult {
        conversationContext = conversationContext.copy(
            pendingAction = action,
            awaitingConfirmation = true
        )

        val confirmMessage = when (action) {
            is AssistantAction.PrepareEmail ->
                "Vuoi davvero inviare un'email al manutentore? Rispondi 'sì' o 'no'."
            is AssistantAction.CreateProduct ->
                "Confermi la creazione del nuovo prodotto? Rispondi 'sì' o 'no'."
            else ->
                "Confermi questa azione? Rispondi 'sì' o 'no'."
        }

        return GeminiResult.ConfirmationNeeded(
            action = action,
            response = aiResponse,
            confirmationMessage = confirmMessage
        )
    }

    /**
     * Gestisce la risposta di conferma dell'utente.
     */
    private fun handleConfirmationResponse(response: String): GeminiResult {
        val pendingAction = conversationContext.pendingAction

        // Reset stato conferma
        conversationContext = conversationContext.copy(
            pendingAction = null,
            awaitingConfirmation = false
        )

        if (pendingAction == null) {
            return GeminiResult.Success("Non c'è nessuna azione in attesa.")
        }

        val normalizedResponse = response.lowercase().trim()
        val isConfirmed = normalizedResponse in listOf(
            "sì", "si", "yes", "ok", "confermo", "conferma", "procedi", "vai"
        )
        val isDenied = normalizedResponse in listOf(
            "no", "annulla", "cancel", "stop", "ferma"
        )

        return when {
            isConfirmed -> {
                AuditLogger.logAction(pendingAction, confirmed = true)
                GeminiResult.ActionRequired(pendingAction, "Perfetto, procedo!")
            }
            isDenied -> {
                AuditLogger.logAction(pendingAction, confirmed = false)
                GeminiResult.Success("Ok, operazione annullata. Come posso aiutarti?")
            }
            else -> {
                // Risposta non chiara, richiedi di nuovo
                conversationContext = conversationContext.copy(
                    pendingAction = pendingAction,
                    awaitingConfirmation = true
                )
                GeminiResult.ConfirmationNeeded(
                    action = pendingAction,
                    response = "Non ho capito la risposta.",
                    confirmationMessage = "Per favore rispondi 'sì' per confermare o 'no' per annullare."
                )
            }
        }
    }

    /**
     * Processa un messaggio con contesto aggiuntivo sui risultati di ricerca.
     */
    suspend fun processWithSearchResults(
        userMessage: String,
        searchResults: List<Product>
    ): GeminiResult {
        conversationContext = conversationContext.copy(lastSearchResults = searchResults)

        return withContext(Dispatchers.IO) {
            try {
                val resultsDescription = if (searchResults.isEmpty()) {
                    "Nessun prodotto trovato."
                } else {
                    searchResults.take(5).mapIndexed { index, product ->
                        "${index + 1}. ${product.name} - ${product.location}" +
                            product.maintenanceDaysRemaining()?.let { days ->
                                if (days < 0) " (SCADUTA da ${-days} giorni)"
                                else if (days <= 7) " (scade tra $days giorni)"
                                else ""
                            }.orEmpty()
                    }.joinToString("\n")
                }

                val prompt = """
                    |L'utente ha cercato: "$userMessage"
                    |
                    |RISULTATI RICERCA:
                    |$resultsDescription
                    |
                    |Presenta i risultati in modo naturale e chiedi se vuole vedere i dettagli
                    |di uno specifico prodotto. Se ci sono manutenzioni scadute, segnalalo.
                """.trimMargin()

                val response = generativeModel.generateContent(
                    content { text(prompt) }
                )

                GeminiResult.Success(response.text ?: "Ecco i risultati della ricerca.")
            } catch (e: Exception) {
                GeminiResult.Error("Errore: ${e.message}")
            }
        }
    }

    /**
     * Processa input da barcode con sanitizzazione specifica.
     */
    suspend fun processBarcodeInput(barcode: String): GeminiResult {
        val sanitizeResult = InputSanitizer.sanitizeBarcode(barcode)

        return when (sanitizeResult) {
            is InputSanitizer.SanitizeResult.Clean -> {
                // Cerca prodotto per barcode
                val product = productRepository.getByBarcode(sanitizeResult.sanitizedInput)
                if (product != null) {
                    setCurrentProduct(product)
                    GeminiResult.ActionRequired(
                        AssistantAction.ShowProduct(product.id),
                        "Ho trovato: ${product.name}"
                    )
                } else {
                    GeminiResult.Success(
                        "Nessun prodotto trovato con codice ${sanitizeResult.sanitizedInput}. " +
                        "Vuoi aggiungerlo all'inventario?"
                    )
                }
            }
            is InputSanitizer.SanitizeResult.Suspicious -> {
                AuditLogger.log(
                    AuditLogger.EventType.SUSPICIOUS_INPUT,
                    "Suspicious barcode: ${sanitizeResult.reason}"
                )
                GeminiResult.Error("Codice non valido", ErrorType.SUSPICIOUS_INPUT)
            }
            is InputSanitizer.SanitizeResult.Rejected -> {
                GeminiResult.Error(sanitizeResult.reason, ErrorType.INVALID_INPUT)
            }
        }
    }

    /**
     * Aggiorna il contesto con il prodotto attualmente visualizzato.
     */
    fun setCurrentProduct(product: Product?) {
        conversationContext = conversationContext.copy(currentProduct = product)
    }

    /**
     * Resetta il contesto della conversazione.
     */
    fun resetContext() {
        conversationContext = ConversationContext()
    }

    /**
     * Annulla eventuale azione in attesa di conferma.
     */
    fun cancelPendingAction() {
        if (conversationContext.pendingAction != null) {
            AuditLogger.logAction(conversationContext.pendingAction!!, confirmed = false)
        }
        conversationContext = conversationContext.copy(
            pendingAction = null,
            awaitingConfirmation = false
        )
    }

    /**
     * Verifica se c'è un'azione in attesa di conferma.
     */
    fun hasPendingAction(): Boolean = conversationContext.awaitingConfirmation

    /**
     * Richieste rimanenti prima del rate limit.
     */
    suspend fun remainingRequests(): Int = rateLimiter.remainingRequests()

    /**
     * P3 FIX: Costruisce il prompt di contesto basato sullo stato attuale.
     * Include data corrente, prodotto visualizzato, manutentori e alert scadenze.
     */
    private suspend fun buildContextPrompt(): String {
        val parts = mutableListOf<String>()

        // 1. DATA CORRENTE (Cruciale per evitare allucinazioni temporali)
        val now = Clock.System.now()
        val today = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val dayOfWeekItalian = when (today.dayOfWeek.name) {
            "MONDAY" -> "Lunedì"
            "TUESDAY" -> "Martedì"
            "WEDNESDAY" -> "Mercoledì"
            "THURSDAY" -> "Giovedì"
            "FRIDAY" -> "Venerdì"
            "SATURDAY" -> "Sabato"
            "SUNDAY" -> "Domenica"
            else -> today.dayOfWeek.name
        }
        parts.add("DATA ODIERNA: ${today.date} ($dayOfWeekItalian)")

        // 2. PRODOTTO ATTUALMENTE VISUALIZZATO
        conversationContext.currentProduct?.let { product ->
            parts.add("""
                |PRODOTTO ATTUALMENTE VISUALIZZATO:
                |Nome: ${product.name}
                |ID: ${product.id}
                |Categoria: ${product.category}
                |Ubicazione: ${product.location}
                |Stato garanzia: ${product.getWarrantyStatusText()}
                |Manutentore garanzia: ${product.warrantyMaintainerId ?: "N/A"}
                |Manutentore service: ${product.serviceMaintainerId ?: "N/A"}
            """.trimMargin())
        }

        // 3. MANUTENTORI REGISTRATI (Per inferenza speaker e assegnazione)
        try {
            val maintainers = maintainerRepository.getAllActive().first()
            if (maintainers.isNotEmpty()) {
                val maintainerList = maintainers.take(20).joinToString("\n") { m ->
                    "- ${m.name}${m.specialization?.let { " ($it)" } ?: ""}: ${m.email ?: ""} ${m.phone ?: ""}"
                }
                val totalCount = if (maintainers.size > 20) " (mostrando 20 di ${maintainers.size})" else ""
                parts.add("""
                    |MANUTENTORI REGISTRATI NEL SISTEMA$totalCount:
                    |$maintainerList
                    |
                    |ISTRUZIONE: Se l'utente si presenta come tecnico o dipendente di una di queste aziende
                    |(es. "Sono Mario di TechMed", "Sono il tecnico di Elettro Impianti"),
                    |consideralo come l'esecutore dell'intervento (LIKELY_MAINTAINER).
                    |Non chiedere "chi ha eseguito l'intervento" se l'utente si è già identificato.
                """.trimMargin())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Impossibile recuperare lista manutentori per prompt", e)
        }

        // 4. ALERT SCADENZE
        try {
            val overdueCount = productRepository.countOverdueMaintenance()
            if (overdueCount > 0) {
                parts.add("⚠️ ATTENZIONE: Ci sono $overdueCount manutenzioni scadute che richiedono intervento.")
            }
        } catch (_: Exception) { }

        // 5. TASK ATTIVO (Se presente)
        conversationContext.activeTask?.let { task ->
            parts.add(buildActiveTaskContext(task))
        }

        return if (parts.isEmpty()) {
            "Nessun contesto specifico."
        } else {
            parts.joinToString("\n\n")
        }
    }

    /**
     * P3 FIX: Helper per formattare il contesto del task attivo.
     */
    private fun buildActiveTaskContext(task: ActiveTask): String = when (task) {
        is ActiveTask.MaintenanceRegistration -> """
            |TASK IN CORSO: Registrazione Manutenzione
            |Prodotto target: ${task.productName} (ID: ${task.productId})
            |Dati già raccolti:
            |${task.toCollectedDataString()}
            |Campi mancanti obbligatori: ${task.requiredMissing.joinToString(", ").ifEmpty { "nessuno" }}
        """.trimMargin()
        is ActiveTask.ProductCreation -> """
            |TASK IN CORSO: Creazione Nuovo Prodotto
            |Dati già raccolti:
            |${task.toCollectedDataString()}
            |Campi mancanti obbligatori: ${task.requiredMissing.joinToString(", ").ifEmpty { "nessuno" }}
        """.trimMargin()
        is ActiveTask.MaintainerCreation -> """
            |TASK IN CORSO: Registrazione Manutentore
            |Dati già raccolti:
            |${task.toCollectedDataString()}
            |Campi mancanti: ${task.requiredMissing.joinToString(", ").ifEmpty { "nessuno" }}
        """.trimMargin()
    }

    /**
     * Estrae azioni dalla risposta di Gemini.
     */
    private fun parseResponse(response: String): Pair<String, AssistantAction?> {
        val actionRegex = """\[ACTION:([A-Z_]+):?([^\]]*)\]""".toRegex()
        val match = actionRegex.find(response)

        if (match == null) {
            return response to null
        }

        val cleanResponse = response.replace(actionRegex, "").trim()
        val actionType = match.groupValues[1]
        val actionParams = match.groupValues[2]

        val action = when (actionType) {
            "SEARCH" -> AssistantAction.SearchProducts(actionParams)
            "SHOW" -> AssistantAction.ShowProduct(actionParams)
            "CREATE" -> {
                val prefill = if (actionParams.isNotBlank()) {
                    actionParams.split(",").associate {
                        val parts = it.split("=")
                        parts[0].trim() to parts.getOrElse(1) { "" }.trim()
                    }
                } else null
                AssistantAction.CreateProduct(prefill)
            }
            "START_PRODUCT_CREATION" -> {
                // Avvia un task di creazione prodotto guidata
                startProductCreationTask()
                null // Nessuna azione esterna, gestito internamente
            }
            "START_MAINTENANCE" -> {
                // Avvia un task di registrazione manutenzione
                val parts = actionParams.split(":", limit = 2)
                if (parts.size >= 2) {
                    startMaintenanceTask(parts[0], parts[1])
                }
                null // Nessuna azione esterna, gestito internamente
            }
            "MAINTENANCE_LIST" -> AssistantAction.ShowMaintenanceList(
                actionParams.takeIf { it.isNotBlank() }
            )
            "EMAIL" -> {
                val parts = actionParams.split(":", limit = 2)
                AssistantAction.PrepareEmail(
                    productId = parts[0],
                    description = parts.getOrElse(1) { "" }
                )
            }
            "SCAN" -> AssistantAction.ScanBarcode(actionParams)
            "ALERTS" -> AssistantAction.ShowOverdueAlerts
            else -> null
        }

        return cleanResponse to action
    }

    /**
     * P1 FIX: Gestisce la ricerca internamente quando c'è un task di MaintenanceRegistration attivo.
     * Invece di navigare alla schermata di ricerca, cerca nel repository e aggiorna il task.
     *
     * @param query La query di ricerca
     * @return AssistantAction? - null se gestito internamente, ShowProduct se trovato singolo prodotto
     */
    private suspend fun handleInternalSearchForMaintenance(query: String): AssistantAction? {
        val task = conversationContext.activeTask as? ActiveTask.MaintenanceRegistration
            ?: return AssistantAction.SearchProducts(query) // Fallback se non è MaintenanceRegistration

        Log.d(TAG, "P1: Internal search for maintenance task, query: $query")

        try {
            val results = productRepository.searchSync(query)

            return when {
                results.isEmpty() -> {
                    Log.d(TAG, "P1: No products found for query: $query")
                    // Nessun prodotto trovato - la risposta di Gemini gestirà questo caso
                    // Aggiorniamo il contesto per indicare che la ricerca non ha trovato nulla
                    null
                }

                results.size == 1 -> {
                    val product = results.first()
                    Log.d(TAG, "P1: Single product found: ${product.name} (${product.id})")

                    // Aggiorna il task con il prodotto trovato
                    conversationContext = conversationContext.copy(
                        activeTask = task.copy(
                            productId = product.id,
                            productName = product.name
                        ),
                        currentProduct = product
                    )

                    // Aggiorna la risposta per confermare il prodotto trovato
                    conversationContext = conversationContext.addExchange(
                        ChatExchange(
                            Role.ASSISTANT,
                            "Ho trovato ${product.name}. Procedo con la registrazione della manutenzione."
                        )
                    )

                    // Restituisci ShowProduct per mostrare i dettagli (opzionale)
                    AssistantAction.ShowProduct(product.id)
                }

                else -> {
                    Log.d(TAG, "P1: Multiple products found (${results.size})")
                    // Multipli prodotti - aggiorniamo i risultati nel contesto
                    conversationContext = conversationContext.copy(
                        lastSearchResults = results
                    )
                    // La risposta di Gemini dovrebbe già chiedere disambiguazione
                    // Non navighiamo - l'utente può specificare meglio vocalmente
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "P1: Error during internal search", e)
            // In caso di errore, fallback alla ricerca normale
            return AssistantAction.SearchProducts(query)
        }
    }

    /**
     * Avvia un task di creazione prodotto guidata.
     */
    private fun startProductCreationTask() {
        conversationContext = conversationContext.copy(
            activeTask = ActiveTask.ProductCreation()
        )
        Log.d(TAG, "Started ProductCreation task")
    }

    /**
     * Avvia un task di registrazione manutenzione.
     */
    private fun startMaintenanceTask(productId: String, productName: String) {
        conversationContext = conversationContext.copy(
            activeTask = ActiveTask.MaintenanceRegistration(
                productId = productId,
                productName = productName
            )
        )
        Log.d(TAG, "Started MaintenanceRegistration task for product: $productName")
    }

    /**
     * Aggiorna il task attivo con nuovi dati estratti.
     * Chiamato quando Gemini estrae campi dal messaggio utente.
     */
    fun updateActiveTask(updates: Map<String, Any?>) {
        val currentTask = conversationContext.activeTask ?: return

        val updatedTask = when (currentTask) {
            is ActiveTask.ProductCreation -> currentTask.copy(
                name = updates["name"] as? String ?: currentTask.name,
                category = updates["category"] as? String ?: currentTask.category,
                brand = updates["brand"] as? String ?: currentTask.brand,
                model = updates["model"] as? String ?: currentTask.model,
                location = updates["location"] as? String ?: currentTask.location,
                barcode = updates["barcode"] as? String ?: currentTask.barcode,
                notes = updates["notes"] as? String ?: currentTask.notes
            )
            is ActiveTask.MaintenanceRegistration -> currentTask.copy(
                type = updates["type"] as? MaintenanceType ?: currentTask.type,
                description = updates["description"] as? String ?: currentTask.description,
                performedBy = updates["performedBy"] as? String ?: currentTask.performedBy,
                cost = updates["cost"] as? Double ?: currentTask.cost
            )
            is ActiveTask.MaintainerCreation -> currentTask.copy(
                name = updates["name"] as? String ?: currentTask.name,
                company = updates["company"] as? String ?: currentTask.company,
                email = updates["email"] as? String ?: currentTask.email,
                phone = updates["phone"] as? String ?: currentTask.phone
            )
        }

        conversationContext = conversationContext.copy(activeTask = updatedTask)
        Log.d(TAG, "Updated active task: $updatedTask")
    }

    /**
     * Verifica se c'è un task attivo in corso.
     */
    fun hasActiveTask(): Boolean = conversationContext.hasActiveTask()

    /**
     * Ottiene il task attivo corrente.
     */
    fun getActiveTask(): ActiveTask? = conversationContext.activeTask

    /**
     * Genera suggerimento basato sullo stato attuale.
     */
    suspend fun getSuggestion(): GeminiResult {
        return withContext(Dispatchers.IO) {
            try {
                val contextPrompt = buildContextPrompt()
                val prompt = """
                    |$contextPrompt
                    |
                    |Genera un breve suggerimento (max 2 frasi) su cosa l'utente potrebbe
                    |voler fare in base al contesto. Sii proattivo riguardo alle scadenze.
                """.trimMargin()

                val response = generativeModel.generateContent(
                    content { text(prompt) }
                )

                GeminiResult.Success(response.text ?: "Come posso aiutarti?")
            } catch (e: Exception) {
                GeminiResult.Success("Come posso aiutarti?")
            }
        }
    }
}
