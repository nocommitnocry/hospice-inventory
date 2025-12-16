package org.incammino.hospiceinventory.service.voice

import android.util.Log

/**
 * P4/P5 FIX: Post-processore per il riconoscimento vocale.
 *
 * Gestisce:
 * - P4: Correzione sigle distorte dal riconoscimento (ABC -> APC, UBS -> UPS)
 * - P5: Interpretazione spelling fonetico italiano (A come Ancona, P come Padova -> AP)
 *
 * @see VoiceService dove questo processore viene applicato
 */
object SttPostProcessor {

    private const val TAG = "SttPostProcessor"

    // ═══════════════════════════════════════════════════════════════════════════════
    // P4: CORREZIONE SIGLE NOTE
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Mappature per sigle comunemente mal riconosciute nel dominio hospice/inventario.
     * Chiave = errore comune, Valore = correzione
     */
    private val KNOWN_CORRECTIONS = mapOf(
        // Sigle produttori
        "ABC" to "APC",      // APC è produttore di UPS
        "UBS" to "UPS",      // UPS spesso mal riconosciuto
        "EPS" to "UPS",      // Altro errore comune
        "IPS" to "UPS",

        // Sigle mediche
        "ECG" to "ECG",      // Già corretto ma per conferma
        "CPAP" to "CPAP",
        "BIPAP" to "BiPAP",

        // Marche comuni
        "Philips" to "Philips",
        "Phillips" to "Philips",
        "Fillips" to "Philips",
        "Siemens" to "Siemens",
        "Simmons" to "Siemens",

        // Nomi comuni mal riconosciuti
        "ossigeno" to "ossigeno",
        "O2" to "O2",
        "o 2" to "O2",
        "oh 2" to "O2"
    )

    /**
     * Applica correzioni per sigle note.
     *
     * @param input Testo dal riconoscimento vocale
     * @return Testo con sigle corrette
     */
    fun correctKnownTerms(input: String): String {
        var result = input

        KNOWN_CORRECTIONS.forEach { (wrong, correct) ->
            // Sostituisci solo parole intere (case-insensitive)
            val regex = Regex("\\b${Regex.escape(wrong)}\\b", RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(result)) {
                Log.d(TAG, "P4: Correcting '$wrong' -> '$correct'")
                result = result.replace(regex, correct)
            }
        }

        return result
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // P5: SPELLING FONETICO ITALIANO
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Alfabeto fonetico italiano standard.
     */
    private val PHONETIC_ALPHABET = mapOf(
        "ancona" to "A",
        "bari" to "B",
        "como" to "C",
        "domodossola" to "D",
        "empoli" to "E",
        "firenze" to "F",
        "genova" to "G",
        "hotel" to "H",
        "imola" to "I",
        "jolly" to "J",
        "kappa" to "K",
        "kilo" to "K",
        "livorno" to "L",
        "milano" to "M",
        "napoli" to "N",
        "otranto" to "O",
        "padova" to "P",
        "quarto" to "Q",
        "quebec" to "Q",
        "roma" to "R",
        "savona" to "S",
        "torino" to "T",
        "udine" to "U",
        "venezia" to "V",
        "washington" to "W",
        "xilofono" to "X",
        "york" to "Y",
        "yacht" to "Y",
        "zara" to "Z",
        "zebra" to "Z"
    )

    /**
     * Pattern per riconoscere spelling fonetico: "X come Città"
     * Gruppi: 1 = lettera (opzionale), 2 = città
     */
    private val SPELLING_PATTERN = Regex(
        """([a-zA-Z])?\s*come\s+(\w+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Pattern alternativo: "Città per X" o solo sequenza di città
     */
    private val CITY_SEQUENCE_PATTERN = Regex(
        """(ancona|bari|como|domodossola|empoli|firenze|genova|hotel|imola|jolly|kappa|livorno|milano|napoli|otranto|padova|quarto|roma|savona|torino|udine|venezia|washington|xilofono|york|zara)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Interpreta e normalizza lo spelling fonetico nel testo.
     *
     * Esempi:
     * - "A come Ancona, P come Padova, C come Como" -> "APC"
     * - "UPS A come Ancona P come Padova C come Como Smart 3000" -> "UPS APC Smart 3000"
     *
     * @param input Testo dal riconoscimento vocale
     * @return Testo con spelling fonetico convertito in sigle
     */
    fun normalizeSpelling(input: String): String {
        // Se non contiene "come", ritorna input originale
        if (!input.contains("come", ignoreCase = true)) {
            return input
        }

        val matches = SPELLING_PATTERN.findAll(input).toList()
        if (matches.isEmpty()) {
            return input
        }

        Log.d(TAG, "P5: Found ${matches.size} spelling patterns in: $input")

        // Estrai le lettere dallo spelling
        val spelledLetters = StringBuilder()
        var lastEnd = 0
        val result = StringBuilder()

        // Trova il range completo dello spelling (da primo a ultimo match)
        val firstMatchStart = matches.first().range.first
        val lastMatchEnd = matches.last().range.last

        // Aggiungi testo prima dello spelling
        if (firstMatchStart > 0) {
            result.append(input.substring(0, firstMatchStart).trim())
            if (result.isNotEmpty()) result.append(" ")
        }

        // Processa ogni match di spelling
        for (match in matches) {
            val letter = match.groupValues[1].uppercase().ifEmpty { "" }
            val city = match.groupValues[2].lowercase()

            // Risolvi la lettera dalla città
            val resolvedLetter = PHONETIC_ALPHABET[city]
                ?: letter.ifEmpty { city.first().uppercaseChar().toString() }

            spelledLetters.append(resolvedLetter)
            Log.d(TAG, "P5: '$city' -> '$resolvedLetter'")
        }

        // Aggiungi la sigla risultante
        if (spelledLetters.isNotEmpty()) {
            result.append(spelledLetters.toString())
        }

        // Aggiungi testo dopo lo spelling
        if (lastMatchEnd < input.length - 1) {
            val afterText = input.substring(lastMatchEnd + 1).trim()
            if (afterText.isNotEmpty()) {
                result.append(" ").append(afterText)
            }
        }

        val finalResult = result.toString().trim()
        Log.d(TAG, "P5: Normalized spelling: '$input' -> '$finalResult'")

        return finalResult
    }

    /**
     * Rileva sequenze di città dell'alfabeto fonetico (senza "come").
     * Utile per "Ancona Padova Como" -> "APC"
     *
     * @param input Testo dal riconoscimento vocale
     * @return Testo con sequenze di città convertite, o input originale
     */
    fun normalizeCitySequence(input: String): String {
        val words = input.split(Regex("\\s+"))
        val consecutiveCities = mutableListOf<String>()
        var inSequence = false
        var sequenceStart = -1

        val result = StringBuilder()
        var i = 0

        while (i < words.size) {
            val word = words[i].lowercase().replace(Regex("[,.]"), "")
            val letter = PHONETIC_ALPHABET[word]

            if (letter != null) {
                if (!inSequence) {
                    sequenceStart = i
                    inSequence = true
                }
                consecutiveCities.add(letter)
            } else {
                if (inSequence && consecutiveCities.size >= 2) {
                    // Abbiamo una sequenza di almeno 2 città -> converti in sigla
                    if (result.isNotEmpty()) result.append(" ")
                    result.append(consecutiveCities.joinToString(""))
                    Log.d(TAG, "P5: City sequence detected: ${words.subList(sequenceStart, i)} -> ${consecutiveCities.joinToString("")}")
                } else if (inSequence && consecutiveCities.size == 1) {
                    // Solo una città, potrebbe essere un nome proprio
                    if (result.isNotEmpty()) result.append(" ")
                    result.append(words[sequenceStart])
                }

                consecutiveCities.clear()
                inSequence = false

                if (result.isNotEmpty()) result.append(" ")
                result.append(words[i])
            }
            i++
        }

        // Gestisci sequenza alla fine
        if (inSequence && consecutiveCities.size >= 2) {
            if (result.isNotEmpty()) result.append(" ")
            result.append(consecutiveCities.joinToString(""))
        } else if (inSequence && consecutiveCities.size == 1) {
            if (result.isNotEmpty()) result.append(" ")
            result.append(words[sequenceStart])
        }

        return result.toString().trim()
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // PROCESSORE COMPLETO
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Applica tutte le correzioni e normalizzazioni al testo.
     *
     * Ordine di applicazione:
     * 1. Normalizza spelling fonetico
     * 2. Normalizza sequenze di città
     * 3. Correggi sigle note
     *
     * @param input Testo grezzo dal riconoscimento vocale
     * @return Testo processato e normalizzato
     */
    fun process(input: String): String {
        if (input.isBlank()) return input

        var result = input

        // 1. Normalizza spelling fonetico (A come Ancona)
        result = normalizeSpelling(result)

        // 2. Normalizza sequenze di città (Ancona Padova Como)
        result = normalizeCitySequence(result)

        // 3. Correggi sigle note (ABC -> APC)
        result = correctKnownTerms(result)

        // 4. Normalizza spazi multipli
        result = result.replace(Regex("\\s+"), " ").trim()

        if (result != input) {
            Log.i(TAG, "Post-processed: '$input' -> '$result'")
        }

        return result
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // VOCABOLARIO DINAMICO (per future estensioni)
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Nomi prodotti dal database (caricati dinamicamente).
     * Usati per fuzzy matching e suggerimenti.
     */
    private var productNames: Set<String> = emptySet()

    /**
     * Aggiorna il vocabolario con i nomi dei prodotti dal database.
     * Chiamare periodicamente o quando il database cambia.
     */
    fun updateProductVocabulary(names: List<String>) {
        productNames = names.map { it.lowercase() }.toSet()
        Log.d(TAG, "Updated product vocabulary with ${productNames.size} names")
    }

    /**
     * Suggerisce correzioni basate sui nomi prodotti noti.
     * Usa distanza di Levenshtein semplificata.
     *
     * @param input Parola da controllare
     * @return Suggerimento se trovato, null altrimenti
     */
    fun suggestProductMatch(input: String): String? {
        if (productNames.isEmpty()) return null

        val normalized = input.lowercase()

        // Match esatto
        if (productNames.contains(normalized)) return null

        // Match parziale (contiene)
        productNames.find { it.contains(normalized) || normalized.contains(it) }?.let {
            return it.replaceFirstChar { c -> c.uppercase() }
        }

        // TODO: Implementare Levenshtein per fuzzy matching più avanzato

        return null
    }
}
