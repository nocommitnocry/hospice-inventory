package org.incammino.hospiceinventory.service.voice

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.ResponseStoppedException
import com.google.ai.client.generativeai.type.SerializationException
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.incammino.hospiceinventory.data.repository.ProductRepository
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

/**
 * Contesto della conversazione per risposte più accurate.
 */
data class ConversationContext(
    val currentProduct: Product? = null,
    val lastSearchResults: List<Product> = emptyList(),
    val pendingAction: AssistantAction? = null,
    val awaitingConfirmation: Boolean = false
)

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
    private val productRepository: ProductRepository
) {
    private var conversationContext = ConversationContext()
    private val rateLimiter = RateLimiter(maxRequests = 15, windowDuration = 1.minutes)

    companion object {
        private const val TAG = "GeminiService"
    }

    /**
     * Processa un messaggio dell'utente e restituisce una risposta.
     * Include sanitizzazione, rate limiting e gestione conferme.
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

        // 3. Log richiesta
        AuditLogger.logRequest(sanitizedMessage, isSuspicious)

        // 4. Gestione conferma pendente
        if (conversationContext.awaitingConfirmation && conversationContext.pendingAction != null) {
            return handleConfirmationResponse(sanitizedMessage)
        }

        // 5. Processa con Gemini
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

                when {
                    action == null -> GeminiResult.Success(cleanResponse)
                    action.riskLevel == ActionRiskLevel.LOW -> {
                        AuditLogger.logAction(action)
                        GeminiResult.ActionRequired(action, cleanResponse)
                    }
                    else -> {
                        // Azioni a rischio medio/alto richiedono conferma
                        AuditLogger.logAction(action)
                        requestConfirmation(action, cleanResponse)
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
     * Costruisce il prompt di contesto per Gemini.
     */
    private fun buildPrompt(
        contextPrompt: String,
        userMessage: String,
        @Suppress("UNUSED_PARAMETER") isSuspicious: Boolean
    ): String {
        return """
            |$contextPrompt
            |
            |UTENTE: $userMessage
            |
            |Rispondi in modo naturale e conciso. Se serve un'azione, aggiungi il tag:
            |[ACTION:tipo:parametri]
            |
            |Azioni disponibili:
            |- SEARCH:query - Cerca prodotti
            |- SHOW:productId - Mostra dettaglio
            |- CREATE:campo=valore - Nuovo prodotto
            |- MAINTENANCE_LIST:filtro - Lista manutenzioni
            |- EMAIL:productId:descrizione - Email manutentore
            |- SCAN:motivo - Scanner barcode
            |- ALERTS - Scadenze
        """.trimMargin()
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
     * Costruisce il prompt di contesto basato sullo stato attuale.
     */
    private suspend fun buildContextPrompt(): String {
        val parts = mutableListOf<String>()

        conversationContext.currentProduct?.let { product ->
            parts.add("""
                |PRODOTTO ATTUALMENTE VISUALIZZATO:
                |Nome: ${product.name}
                |Categoria: ${product.category}
                |Ubicazione: ${product.location}
                |Stato garanzia: ${product.getWarrantyStatusText()}
                |Stato manutenzione: ${product.getMaintenanceStatusText()}
            """.trimMargin())
        }

        try {
            val overdueCount = productRepository.countOverdueMaintenance()
            if (overdueCount > 0) {
                parts.add("ATTENZIONE: Ci sono $overdueCount manutenzioni scadute.")
            }
        } catch (_: Exception) { }

        return if (parts.isEmpty()) {
            "Nessun contesto specifico."
        } else {
            parts.joinToString("\n\n")
        }
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
