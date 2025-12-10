package org.incammino.hospiceinventory.service.voice

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.incammino.hospiceinventory.data.repository.ProductRepository
import org.incammino.hospiceinventory.domain.model.Product
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Risultato di una richiesta a Gemini.
 */
sealed class GeminiResult {
    data class Success(val response: String) : GeminiResult()
    data class Error(val message: String) : GeminiResult()
    data class ActionRequired(
        val action: AssistantAction,
        val response: String
    ) : GeminiResult()
}

/**
 * Azioni che l'assistente può richiedere.
 */
sealed class AssistantAction {
    data class SearchProducts(val query: String) : AssistantAction()
    data class ShowProduct(val productId: String) : AssistantAction()
    data class CreateProduct(val prefillData: Map<String, String>?) : AssistantAction()
    data class ShowMaintenanceList(val filter: String?) : AssistantAction()
    data class PrepareEmail(val productId: String, val description: String) : AssistantAction()
    data class ScanBarcode(val reason: String) : AssistantAction()
    object ShowOverdueAlerts : AssistantAction()
}

/**
 * Contesto della conversazione per risposte più accurate.
 */
data class ConversationContext(
    val currentProduct: Product? = null,
    val lastSearchResults: List<Product> = emptyList(),
    val pendingAction: AssistantAction? = null
)

/**
 * Servizio per interagire con Gemini AI.
 * Gestisce la conversazione, interpreta i comandi e suggerisce azioni.
 */
@Singleton
class GeminiService @Inject constructor(
    private val generativeModel: GenerativeModel,
    private val productRepository: ProductRepository
) {
    private var conversationContext = ConversationContext()

    /**
     * Processa un messaggio dell'utente e restituisce una risposta.
     */
    suspend fun processMessage(userMessage: String): GeminiResult {
        return withContext(Dispatchers.IO) {
            try {
                // Costruisci il prompt con contesto
                val contextPrompt = buildContextPrompt()
                val fullPrompt = """
                    |$contextPrompt
                    |
                    |MESSAGGIO UTENTE: $userMessage
                    |
                    |Rispondi in modo conciso e naturale. Se l'utente chiede di fare qualcosa,
                    |includi nella risposta un tag ACTION con il formato:
                    |[ACTION:tipo:parametri]
                    |
                    |Tipi di azione disponibili:
                    |- SEARCH:query - Cerca prodotti
                    |- SHOW:productId - Mostra dettaglio prodotto
                    |- CREATE:campo1=valore1,campo2=valore2 - Crea nuovo prodotto
                    |- MAINTENANCE_LIST:filtro - Mostra lista manutenzioni (filtro: all/overdue/week)
                    |- EMAIL:productId:descrizione - Prepara email al manutentore
                    |- SCAN:motivo - Attiva scanner barcode
                    |- ALERTS - Mostra manutenzioni scadute
                    |
                    |Esempio: "Cerco il monitor" -> "Cerco i monitor nell'inventario. [ACTION:SEARCH:monitor]"
                """.trimMargin()

                val response = generativeModel.generateContent(
                    content { text(fullPrompt) }
                )

                val responseText = response.text ?: "Mi dispiace, non ho capito."

                // Estrai eventuali azioni dalla risposta
                val (cleanResponse, action) = parseResponse(responseText)

                if (action != null) {
                    GeminiResult.ActionRequired(action, cleanResponse)
                } else {
                    GeminiResult.Success(cleanResponse)
                }
            } catch (e: Exception) {
                GeminiResult.Error("Errore di comunicazione: ${e.message}")
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
     * Costruisce il prompt di contesto basato sullo stato attuale.
     */
    private suspend fun buildContextPrompt(): String {
        val parts = mutableListOf<String>()

        // Contesto prodotto corrente
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

        // Statistiche generali
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
     * Genera una risposta per confermare un'azione.
     */
    suspend fun confirmAction(action: AssistantAction, confirmed: Boolean): GeminiResult {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = if (confirmed) {
                    "L'utente ha CONFERMATO l'azione. Rispondi brevemente confermando l'esecuzione."
                } else {
                    "L'utente ha ANNULLATO l'azione. Rispondi brevemente e chiedi cosa vuole fare."
                }

                val response = generativeModel.generateContent(
                    content { text(prompt) }
                )

                GeminiResult.Success(response.text ?: if (confirmed) "Fatto!" else "Annullato.")
            } catch (e: Exception) {
                GeminiResult.Error("Errore: ${e.message}")
            }
        }
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
