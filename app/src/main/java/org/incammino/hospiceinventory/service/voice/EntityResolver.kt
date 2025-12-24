package org.incammino.hospiceinventory.service.voice

import android.util.Log
import org.incammino.hospiceinventory.data.repository.AssigneeRepository
import org.incammino.hospiceinventory.data.repository.LocationRepository
import org.incammino.hospiceinventory.data.repository.MaintainerRepository
import org.incammino.hospiceinventory.domain.model.Assignee
import org.incammino.hospiceinventory.domain.model.Location
import org.incammino.hospiceinventory.domain.model.Maintainer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Risolve riferimenti testuali a entità del database.
 * Usato durante i task vocali per convertire nomi in ID.
 *
 * Strategia di matching:
 * 1. Match esatto (case-insensitive)
 * 2. Match per contenimento (il nome contiene la query o viceversa)
 * 3. Fuzzy match con Levenshtein distance (similarity >= 0.6)
 *
 * @see Resolution per i possibili risultati
 */
@Singleton
class EntityResolver @Inject constructor(
    private val maintainerRepository: MaintainerRepository,
    private val locationRepository: LocationRepository,
    private val assigneeRepository: AssigneeRepository
) {
    companion object {
        private const val TAG = "EntityResolver"

        /** Soglia minima di similarità per considerare un match fuzzy */
        private const val MIN_SIMILARITY = 0.6f

        /** Soglia per match fuzzy ad alta confidenza (non richiede conferma) */
        private const val HIGH_CONFIDENCE_SIMILARITY = 0.8f

        /** Differenza minima tra primo e secondo match per considerare il primo "vincente" */
        private const val CONFIDENCE_GAP = 0.2f
    }

    /**
     * Risultato della risoluzione di un'entità.
     */
    sealed class Resolution<T> {
        /** Entità trovata univocamente */
        data class Found<T>(val entity: T) : Resolution<T>()

        /** Più entità corrispondono - serve disambiguazione */
        data class Ambiguous<T>(val candidates: List<T>, val query: String) : Resolution<T>()

        /** Nessuna entità trovata - offrire creazione */
        data class NotFound<T>(val query: String) : Resolution<T>()

        /** Match parziale con bassa confidenza - chiedere conferma */
        data class NeedsConfirmation<T>(
            val candidate: T,
            val similarity: Float,
            val query: String
        ) : Resolution<T>()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MAINTAINER RESOLUTION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Risolve un nome manutentore/fornitore in un ID.
     *
     * @param nameQuery Il nome pronunciato dall'utente (es. "Medika", "Siemens Healthcare")
     * @return Resolution con l'entità trovata o le alternative
     */
    suspend fun resolveMaintainer(nameQuery: String): Resolution<Maintainer> {
        Log.d(TAG, "resolveMaintainer: query='$nameQuery'")

        val normalized = nameQuery.lowercase().trim()
        if (normalized.isEmpty()) {
            return Resolution.NotFound(nameQuery)
        }

        val allMaintainers = maintainerRepository.getAllActiveSync()
        Log.d(TAG, "resolveMaintainer: ${allMaintainers.size} manutentori attivi")

        // 1. Match esatto (case-insensitive)
        allMaintainers.find { it.name.equals(normalized, ignoreCase = true) }
            ?.let {
                Log.d(TAG, "resolveMaintainer: match esatto trovato: ${it.name}")
                return Resolution.Found(it)
            }

        // 2. Match per contenimento
        val containsMatches = allMaintainers.filter { maintainer ->
            maintainer.name.lowercase().contains(normalized) ||
                normalized.contains(maintainer.name.lowercase())
        }

        when (containsMatches.size) {
            1 -> {
                Log.d(TAG, "resolveMaintainer: match contenimento univoco: ${containsMatches[0].name}")
                return Resolution.Found(containsMatches[0])
            }
            in 2..5 -> {
                Log.d(TAG, "resolveMaintainer: match contenimento ambiguo: ${containsMatches.map { it.name }}")
                return Resolution.Ambiguous(containsMatches, nameQuery)
            }
        }

        // 3. Fuzzy match con Levenshtein distance
        val fuzzyMatches = allMaintainers.mapNotNull { maintainer ->
            val similarity = calculateSimilarity(normalized, maintainer.name.lowercase())
            if (similarity >= MIN_SIMILARITY) maintainer to similarity else null
        }.sortedByDescending { it.second }

        Log.d(TAG, "resolveMaintainer: fuzzy matches: ${fuzzyMatches.map { "${it.first.name}=${it.second}" }}")

        return when {
            fuzzyMatches.isEmpty() -> {
                Log.d(TAG, "resolveMaintainer: nessun match trovato")
                Resolution.NotFound(nameQuery)
            }
            fuzzyMatches.size == 1 && fuzzyMatches[0].second >= HIGH_CONFIDENCE_SIMILARITY -> {
                Log.d(TAG, "resolveMaintainer: match fuzzy alta confidenza: ${fuzzyMatches[0].first.name}")
                Resolution.Found(fuzzyMatches[0].first)
            }
            fuzzyMatches.size == 1 -> {
                Log.d(TAG, "resolveMaintainer: match fuzzy richiede conferma: ${fuzzyMatches[0].first.name}")
                Resolution.NeedsConfirmation(
                    fuzzyMatches[0].first,
                    fuzzyMatches[0].second,
                    nameQuery
                )
            }
            fuzzyMatches[0].second - fuzzyMatches[1].second > CONFIDENCE_GAP -> {
                Log.d(TAG, "resolveMaintainer: primo match significativamente migliore: ${fuzzyMatches[0].first.name}")
                Resolution.NeedsConfirmation(
                    fuzzyMatches[0].first,
                    fuzzyMatches[0].second,
                    nameQuery
                )
            }
            else -> {
                Log.d(TAG, "resolveMaintainer: match fuzzy ambiguo")
                Resolution.Ambiguous(fuzzyMatches.take(3).map { it.first }, nameQuery)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LOCATION RESOLUTION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Risolve un nome ubicazione in un ID.
     *
     * @param nameQuery Il nome pronunciato dall'utente (es. "Magazzino", "Piano 1")
     * @return Resolution con l'entità trovata o le alternative
     */
    suspend fun resolveLocation(nameQuery: String): Resolution<Location> {
        Log.d(TAG, "resolveLocation: query='$nameQuery'")

        val normalized = nameQuery.lowercase().trim()
        if (normalized.isEmpty()) {
            return Resolution.NotFound(nameQuery)
        }

        val allLocations = locationRepository.getAllActiveSync()
        Log.d(TAG, "resolveLocation: ${allLocations.size} ubicazioni attive")

        // Match esatto
        allLocations.find { it.name.equals(normalized, ignoreCase = true) }
            ?.let {
                Log.d(TAG, "resolveLocation: match esatto trovato: ${it.name}")
                return Resolution.Found(it)
            }

        // Match parziale
        val partialMatches = allLocations.filter { location ->
            location.name.lowercase().contains(normalized) ||
                normalized.contains(location.name.lowercase())
        }

        return when (partialMatches.size) {
            0 -> {
                // Prova fuzzy match
                val fuzzyMatches = allLocations.mapNotNull { location ->
                    val similarity = calculateSimilarity(normalized, location.name.lowercase())
                    if (similarity >= MIN_SIMILARITY) location to similarity else null
                }.sortedByDescending { it.second }

                when {
                    fuzzyMatches.isEmpty() -> {
                        Log.d(TAG, "resolveLocation: nessun match")
                        Resolution.NotFound(nameQuery)
                    }
                    fuzzyMatches[0].second >= HIGH_CONFIDENCE_SIMILARITY -> {
                        Log.d(TAG, "resolveLocation: fuzzy alta confidenza: ${fuzzyMatches[0].first.name}")
                        Resolution.Found(fuzzyMatches[0].first)
                    }
                    else -> {
                        Log.d(TAG, "resolveLocation: fuzzy richiede conferma: ${fuzzyMatches[0].first.name}")
                        Resolution.NeedsConfirmation(
                            fuzzyMatches[0].first,
                            fuzzyMatches[0].second,
                            nameQuery
                        )
                    }
                }
            }
            1 -> {
                Log.d(TAG, "resolveLocation: match parziale univoco: ${partialMatches[0].name}")
                Resolution.Found(partialMatches[0])
            }
            else -> {
                Log.d(TAG, "resolveLocation: match parziale ambiguo: ${partialMatches.map { it.name }}")
                Resolution.Ambiguous(partialMatches, nameQuery)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ASSIGNEE RESOLUTION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Risolve un nome assegnatario in un ID.
     *
     * @param nameQuery Il nome pronunciato dall'utente (es. "Mario Rossi", "Reparto Infermieri")
     * @return Resolution con l'entità trovata o le alternative
     */
    suspend fun resolveAssignee(nameQuery: String): Resolution<Assignee> {
        Log.d(TAG, "resolveAssignee: query='$nameQuery'")

        val normalized = nameQuery.lowercase().trim()
        if (normalized.isEmpty()) {
            return Resolution.NotFound(nameQuery)
        }

        val allAssignees = assigneeRepository.getAllActiveSync()
        Log.d(TAG, "resolveAssignee: ${allAssignees.size} assegnatari attivi")

        // Match esatto su nome
        allAssignees.find { it.name.equals(normalized, ignoreCase = true) }
            ?.let {
                Log.d(TAG, "resolveAssignee: match esatto trovato: ${it.name}")
                return Resolution.Found(it)
            }

        // Match parziale (nome o dipartimento)
        val partialMatches = allAssignees.filter { assignee ->
            assignee.name.lowercase().contains(normalized) ||
                normalized.contains(assignee.name.lowercase()) ||
                assignee.department?.lowercase()?.contains(normalized) == true
        }

        return when (partialMatches.size) {
            0 -> {
                // Prova fuzzy match
                val fuzzyMatches = allAssignees.mapNotNull { assignee ->
                    val nameSimilarity = calculateSimilarity(normalized, assignee.name.lowercase())
                    val deptSimilarity = assignee.department?.let {
                        calculateSimilarity(normalized, it.lowercase())
                    } ?: 0f
                    val bestSimilarity = maxOf(nameSimilarity, deptSimilarity)
                    if (bestSimilarity >= MIN_SIMILARITY) assignee to bestSimilarity else null
                }.sortedByDescending { it.second }

                when {
                    fuzzyMatches.isEmpty() -> {
                        Log.d(TAG, "resolveAssignee: nessun match")
                        Resolution.NotFound(nameQuery)
                    }
                    fuzzyMatches[0].second >= HIGH_CONFIDENCE_SIMILARITY -> {
                        Log.d(TAG, "resolveAssignee: fuzzy alta confidenza: ${fuzzyMatches[0].first.name}")
                        Resolution.Found(fuzzyMatches[0].first)
                    }
                    else -> {
                        Log.d(TAG, "resolveAssignee: fuzzy richiede conferma: ${fuzzyMatches[0].first.name}")
                        Resolution.NeedsConfirmation(
                            fuzzyMatches[0].first,
                            fuzzyMatches[0].second,
                            nameQuery
                        )
                    }
                }
            }
            1 -> {
                Log.d(TAG, "resolveAssignee: match parziale univoco: ${partialMatches[0].name}")
                Resolution.Found(partialMatches[0])
            }
            else -> {
                Log.d(TAG, "resolveAssignee: match parziale ambiguo: ${partialMatches.map { it.name }}")
                Resolution.Ambiguous(partialMatches, nameQuery)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Calcola similarità tra due stringhe (Levenshtein normalizzato).
     * @return Valore tra 0.0 (completamente diversi) e 1.0 (identici)
     */
    private fun calculateSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f

        val maxLen = maxOf(s1.length, s2.length)
        val distance = levenshteinDistance(s1, s2)
        return 1.0f - (distance.toFloat() / maxLen)
    }

    /**
     * Calcola la distanza di Levenshtein tra due stringhe.
     * Rappresenta il numero minimo di operazioni (inserimento, cancellazione, sostituzione)
     * necessarie per trasformare una stringa nell'altra.
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[s1.length][s2.length]
    }
}
