# SPEC: Entity Resolution & Get-or-Create Pattern

**Data**: 24 Dicembre 2025  
**Priorità**: CRITICA - Blocca il flusso vocale di creazione entità  
**Prerequisito per**: Creazione prodotti, registrazione manutenzioni, gestione fornitori

---

## Executive Summary

### Il Problema Fondamentale

Quando l'utente dice vocalmente:

> "Aggiungi concentratore OxyGen, fornitore Medika Srl, ubicazione Magazzino"

Il sistema attualmente:
1. ❌ Raccoglie "Medika Srl" come **stringa**
2. ❌ Non verifica se esiste nel DB
3. ❌ Non può salvare perché `warrantyMaintainerId` richiede un **UUID**
4. ❌ Va in loop o perde i dati

### La Soluzione

Implementare un pattern **Get-or-Create** che:
1. ✅ Cerca l'entità per nome (fuzzy match)
2. ✅ Se trovata univocamente → usa l'ID esistente
3. ✅ Se ambigua → chiede chiarimento
4. ✅ Se non trovata → offre di crearla al volo

---

## 1. Architettura della Soluzione

### 1.1 Nuovo Componente: EntityResolver

```kotlin
// File: app/src/main/java/org/incammino/hospiceinventory/service/voice/EntityResolver.kt

package org.incammino.hospiceinventory.service.voice

import org.incammino.hospiceinventory.data.repository.*
import org.incammino.hospiceinventory.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Risolve riferimenti testuali a entità del database.
 * Usato durante i task vocali per convertire nomi in ID.
 */
@Singleton
class EntityResolver @Inject constructor(
    private val maintainerRepository: MaintainerRepository,
    private val locationRepository: LocationRepository,
    private val assigneeRepository: AssigneeRepository
) {
    
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
        data class NeedsConfirmation<T>(val candidate: T, val similarity: Float, val query: String) : Resolution<T>()
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // MAINTAINER RESOLUTION
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Risolve un nome manutentore/fornitore in un ID.
     * 
     * @param nameQuery Il nome pronunciato dall'utente (es. "Medika", "Siemens Healthcare")
     * @return Resolution con l'entità trovata o le alternative
     */
    suspend fun resolveMaintainer(nameQuery: String): Resolution<Maintainer> {
        val normalized = nameQuery.lowercase().trim()
        val allMaintainers = maintainerRepository.getAllActiveSync()
        
        // 1. Match esatto (case-insensitive)
        allMaintainers.find { it.name.equals(normalized, ignoreCase = true) }
            ?.let { return Resolution.Found(it) }
        
        // 2. Match per contenimento
        val containsMatches = allMaintainers.filter { maintainer ->
            maintainer.name.lowercase().contains(normalized) ||
            normalized.contains(maintainer.name.lowercase())
        }
        
        when (containsMatches.size) {
            1 -> return Resolution.Found(containsMatches[0])
            in 2..5 -> return Resolution.Ambiguous(containsMatches, nameQuery)
        }
        
        // 3. Fuzzy match con Levenshtein distance
        val fuzzyMatches = allMaintainers.mapNotNull { maintainer ->
            val similarity = calculateSimilarity(normalized, maintainer.name.lowercase())
            if (similarity >= 0.6f) maintainer to similarity else null
        }.sortedByDescending { it.second }
        
        return when {
            fuzzyMatches.isEmpty() -> Resolution.NotFound(nameQuery)
            fuzzyMatches.size == 1 && fuzzyMatches[0].second >= 0.8f -> 
                Resolution.Found(fuzzyMatches[0].first)
            fuzzyMatches.size == 1 -> 
                Resolution.NeedsConfirmation(fuzzyMatches[0].first, fuzzyMatches[0].second, nameQuery)
            fuzzyMatches[0].second - fuzzyMatches[1].second > 0.2f ->
                Resolution.NeedsConfirmation(fuzzyMatches[0].first, fuzzyMatches[0].second, nameQuery)
            else -> 
                Resolution.Ambiguous(fuzzyMatches.take(3).map { it.first }, nameQuery)
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // LOCATION RESOLUTION
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Risolve un nome ubicazione in un ID.
     */
    suspend fun resolveLocation(nameQuery: String): Resolution<Location> {
        val normalized = nameQuery.lowercase().trim()
        val allLocations = locationRepository.getAllActiveSync()
        
        // Match esatto
        allLocations.find { it.name.equals(normalized, ignoreCase = true) }
            ?.let { return Resolution.Found(it) }
        
        // Match parziale
        val partialMatches = allLocations.filter { location ->
            location.name.lowercase().contains(normalized) ||
            normalized.contains(location.name.lowercase())
        }
        
        return when (partialMatches.size) {
            0 -> Resolution.NotFound(nameQuery)
            1 -> Resolution.Found(partialMatches[0])
            else -> Resolution.Ambiguous(partialMatches, nameQuery)
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // ASSIGNEE RESOLUTION
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Risolve un nome assegnatario in un ID.
     */
    suspend fun resolveAssignee(nameQuery: String): Resolution<Assignee> {
        val normalized = nameQuery.lowercase().trim()
        val allAssignees = assigneeRepository.getAllActiveSync()
        
        // Match esatto
        allAssignees.find { it.name.equals(normalized, ignoreCase = true) }
            ?.let { return Resolution.Found(it) }
        
        // Match parziale (nome o dipartimento)
        val partialMatches = allAssignees.filter { assignee ->
            assignee.name.lowercase().contains(normalized) ||
            normalized.contains(assignee.name.lowercase()) ||
            assignee.department?.lowercase()?.contains(normalized) == true
        }
        
        return when (partialMatches.size) {
            0 -> Resolution.NotFound(nameQuery)
            1 -> Resolution.Found(partialMatches[0])
            else -> Resolution.Ambiguous(partialMatches, nameQuery)
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════════
    
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
```

---

## 2. Query Sincrone nei Repository

### 2.1 Aggiungere metodi `*Sync()` ai Repository

I repository attuali usano `Flow` per le query, ma `EntityResolver` ha bisogno di risultati sincroni.

**MaintainerRepository.kt** - Aggiungere:

```kotlin
/**
 * Tutti i manutentori attivi (sincrono, per EntityResolver).
 */
suspend fun getAllActiveSync(): List<Maintainer> =
    maintainerDao.getAllActiveSync().map { it.toDomain() }
```

**MaintainerDao.kt** - Aggiungere:

```kotlin
@Query("SELECT * FROM maintainers WHERE is_active = 1 ORDER BY name ASC")
suspend fun getAllActiveSync(): List<MaintainerEntity>
```

**Stesso pattern per LocationRepository e AssigneeRepository.**

---

## 3. Integrazione in GeminiService

### 3.1 Inject EntityResolver

```kotlin
@Singleton
class GeminiService @Inject constructor(
    private val generativeModel: GenerativeModel,
    private val productRepository: ProductRepository,
    private val maintainerRepository: MaintainerRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val locationRepository: LocationRepository,
    private val assigneeRepository: AssigneeRepository,
    private val entityResolver: EntityResolver  // ← NUOVO
) {
```

### 3.2 Nuovo Metodo: resolveEntityReferences()

Chiamato **prima** di completare un task, per risolvere tutti i riferimenti testuali.

```kotlin
/**
 * Risolve i riferimenti testuali in un ActiveTask prima del salvataggio.
 * 
 * @return Pair di:
 *   - Task aggiornato con ID risolti (o null se impossibile)
 *   - Messaggio per l'utente (conferma, disambiguazione, o offerta di creazione)
 */
private suspend fun resolveEntityReferences(task: ActiveTask): Pair<ActiveTask?, String> {
    return when (task) {
        is ActiveTask.ProductCreation -> resolveProductCreationRefs(task)
        is ActiveTask.MaintenanceRegistration -> resolveMaintenanceRefs(task)
        is ActiveTask.MaintainerCreation -> task to "" // Nessun riferimento da risolvere
        is ActiveTask.LocationCreation -> resolveLocationCreationRefs(task)
        is ActiveTask.AssigneeCreation -> task to "" // Nessun riferimento da risolvere
    }
}

private suspend fun resolveProductCreationRefs(
    task: ActiveTask.ProductCreation
): Pair<ActiveTask.ProductCreation?, String> {
    var updatedTask = task
    val messages = mutableListOf<String>()
    
    // Risolvi warrantyMaintainer se è un nome (non un UUID)
    task.warrantyMaintainerName?.let { name ->
        when (val resolution = entityResolver.resolveMaintainer(name)) {
            is EntityResolver.Resolution.Found -> {
                updatedTask = updatedTask.copy(
                    warrantyMaintainerId = resolution.entity.id,
                    warrantyMaintainerName = null // Risolto
                )
            }
            is EntityResolver.Resolution.Ambiguous -> {
                val options = resolution.candidates.joinToString(", ") { it.name }
                return null to "Ho trovato più manutentori simili a \"$name\": $options. Quale intendi?"
            }
            is EntityResolver.Resolution.NotFound -> {
                return null to "Non ho trovato \"$name\" tra i manutentori. Vuoi che lo aggiunga come nuovo fornitore?"
            }
            is EntityResolver.Resolution.NeedsConfirmation -> {
                return null to "Intendi \"${resolution.candidate.name}\"?"
            }
        }
    }
    
    // Risolvi serviceMaintainer
    task.serviceMaintainerName?.let { name ->
        when (val resolution = entityResolver.resolveMaintainer(name)) {
            is EntityResolver.Resolution.Found -> {
                updatedTask = updatedTask.copy(
                    serviceMaintainerId = resolution.entity.id,
                    serviceMaintainerName = null
                )
            }
            is EntityResolver.Resolution.Ambiguous -> {
                val options = resolution.candidates.joinToString(", ") { it.name }
                return null to "Quale manutentore service intendi tra: $options?"
            }
            is EntityResolver.Resolution.NotFound -> {
                return null to "\"$name\" non è tra i manutentori registrati. Lo creo?"
            }
            is EntityResolver.Resolution.NeedsConfirmation -> {
                return null to "Per il service intendi \"${resolution.candidate.name}\"?"
            }
        }
    }
    
    // Risolvi location
    task.locationName?.let { name ->
        when (val resolution = entityResolver.resolveLocation(name)) {
            is EntityResolver.Resolution.Found -> {
                updatedTask = updatedTask.copy(
                    location = resolution.entity.name, // Per ora usiamo il nome, TODO: locationId
                    locationName = null
                )
            }
            is EntityResolver.Resolution.Ambiguous -> {
                val options = resolution.candidates.joinToString(", ") { it.name }
                return null to "Quale ubicazione: $options?"
            }
            is EntityResolver.Resolution.NotFound -> {
                return null to "L'ubicazione \"$name\" non esiste. La creo?"
            }
            is EntityResolver.Resolution.NeedsConfirmation -> {
                return null to "Intendi l'ubicazione \"${resolution.candidate.name}\"?"
            }
        }
    }
    
    return updatedTask to ""
}
```

### 3.3 Modificare completeActiveTask()

```kotlin
private suspend fun completeActiveTask(): Pair<String, AssistantAction?> {
    val task = conversationContext.activeTask ?: return "Nessun task attivo." to null
    
    // NUOVO: Risolvi riferimenti prima del salvataggio
    val (resolvedTask, resolutionMessage) = resolveEntityReferences(task)
    
    if (resolvedTask == null) {
        // Serve input utente per disambiguare
        return resolutionMessage to null
    }
    
    // Procedi con il salvataggio usando resolvedTask
    return when (resolvedTask) {
        is ActiveTask.ProductCreation -> {
            // Salva nel DB
            val product = Product(
                name = resolvedTask.name ?: "Nuovo prodotto",
                category = resolvedTask.category,
                location = resolvedTask.location,
                warrantyMaintainerId = resolvedTask.warrantyMaintainerId,
                serviceMaintainerId = resolvedTask.serviceMaintainerId,
                // ... altri campi
            )
            val id = productRepository.insert(product)
            conversationContext.clearActiveTask()
            
            "Prodotto \"${product.name}\" salvato con ID $id." to null
        }
        
        is ActiveTask.MaintenanceRegistration -> {
            val maintenance = Maintenance(
                productId = resolvedTask.productId,
                type = resolvedTask.type ?: MaintenanceType.RIPARAZIONE,
                description = resolvedTask.description ?: "",
                date = resolvedTask.date ?: Clock.System.now(),
                maintainerId = resolvedTask.maintainerId,
                // ... altri campi
            )
            maintenanceRepository.insert(maintenance)
            conversationContext.clearActiveTask()
            
            "Manutenzione registrata su ${resolvedTask.productName}." to null
        }
        
        is ActiveTask.MaintainerCreation -> {
            val maintainer = Maintainer(
                name = resolvedTask.name ?: resolvedTask.company ?: "Nuovo manutentore",
                email = resolvedTask.email,
                phone = resolvedTask.phone,
                specialization = resolvedTask.specializations?.joinToString(", "),
                isSupplier = true
            )
            val id = maintainerRepository.insert(maintainer)
            conversationContext.clearActiveTask()
            
            "Fornitore \"${maintainer.name}\" registrato." to null
        }
        
        is ActiveTask.LocationCreation -> {
            val location = Location(
                name = resolvedTask.name ?: "Nuova ubicazione",
                parentId = resolvedTask.parentId,
                address = resolvedTask.address,
                notes = resolvedTask.notes
            )
            val id = locationRepository.insert(location)
            conversationContext.clearActiveTask()
            
            "Ubicazione \"${location.name}\" creata." to null
        }
        
        is ActiveTask.AssigneeCreation -> {
            val assignee = Assignee(
                name = resolvedTask.name ?: "Nuovo assegnatario",
                department = resolvedTask.department,
                phone = resolvedTask.phone,
                email = resolvedTask.email
            )
            val id = assigneeRepository.insert(assignee)
            conversationContext.clearActiveTask()
            
            "Assegnatario \"${assignee.name}\" registrato." to null
        }
    }
}
```

---

## 4. Estensione ActiveTask per Riferimenti Non Risolti

Aggiungere campi `*Name` per i riferimenti testuali non ancora risolti in ID.

**ConversationContext.kt** - Modificare `ActiveTask.ProductCreation`:

```kotlin
data class ProductCreation(
    // Campi esistenti
    val name: String? = null,
    val category: String? = null,
    val location: String? = null,
    
    // ID risolti
    val warrantyMaintainerId: String? = null,
    val serviceMaintainerId: String? = null,
    val assigneeId: String? = null,
    val locationId: String? = null,
    
    // NUOVI: Riferimenti testuali non ancora risolti
    val warrantyMaintainerName: String? = null,
    val serviceMaintainerName: String? = null,
    val assigneeName: String? = null,
    val locationName: String? = null,
    
    // ... altri campi esistenti
    override val startedAt: Instant = Clock.System.now()
) : ActiveTask()
```

---

## 5. Gestione Creazione Inline

Quando `EntityResolver` restituisce `NotFound`, l'utente può scegliere di creare l'entità al volo.

### 5.1 Nuovo Stato: PendingEntityCreation

```kotlin
// In ConversationContext
data class PendingEntityCreation(
    val entityType: EntityType,
    val suggestedName: String,
    val returnToTask: ActiveTask
)

enum class EntityType {
    MAINTAINER, LOCATION, ASSIGNEE
}
```

### 5.2 Flusso

```
1. Utente: "fornitore Medika Srl"
2. EntityResolver: NotFound("Medika Srl")
3. Sistema: "Non ho trovato Medika Srl. Vuoi che lo aggiunga?"
4. Utente: "Sì"
5. Sistema: 
   - Salva pendingEntityCreation = PendingEntityCreation(MAINTAINER, "Medika Srl", currentTask)
   - Avvia ActiveTask.MaintainerCreation(name = "Medika Srl")
6. [Flusso creazione manutentore]
7. Al completamento:
   - Recupera pendingEntityCreation.returnToTask
   - Aggiorna il task originale con l'ID appena creato
   - Riprende da dove si era interrotto
```

---

## 6. Modifiche al System Prompt

Aggiungere istruzioni per Gemini su come gestire riferimenti a entità.

```kotlin
// In buildSystemPrompt()

"""
## RISOLUZIONE ENTITÀ

Quando l'utente menziona un manutentore, ubicazione o assegnatario per NOME:

1. Se riconosci il nome come entità esistente (dalla lista fornita), usa quello
2. Se il nome è simile ma non identico, chiedi conferma: "Intendi [nome corretto]?"
3. Se il nome non corrisponde a nessuna entità nota, chiedi: "[Nome] non è registrato. Lo creo come nuovo [tipo]?"

IMPORTANTE: Non inventare ID. Usa SOLO gli ID dalla lista manutentori/ubicazioni fornita nel contesto.

Quando estrai dati per un task, usa questi campi:
- Per riferimenti RISOLTI (ID noto): warrantyMaintainerId, serviceMaintainerId, locationId
- Per riferimenti TESTUALI (da risolvere): warrantyMaintainerName, serviceMaintainerName, locationName

Esempio:
Utente: "fornitore Siemens"
Se "Siemens Healthcare" è nella lista → [TASK_UPDATE:warrantyMaintainerId=maint-xyz]
Se non c'è → [TASK_UPDATE:warrantyMaintainerName=Siemens] + "Siemens non è registrato. Lo aggiungo?"
"""
```

---

## 7. Checklist Implementazione

### Layer DAO
- [ ] `MaintainerDao.getAllActiveSync()` 
- [ ] `LocationDao.getAllActiveSync()`
- [ ] `AssigneeDao.getAllActiveSync()`

### Layer Repository  
- [ ] `MaintainerRepository.getAllActiveSync()`
- [ ] `LocationRepository.getAllActiveSync()`
- [ ] `AssigneeRepository.getAllActiveSync()`

### EntityResolver
- [ ] Creare classe `EntityResolver`
- [ ] Implementare `resolveMaintainer()`
- [ ] Implementare `resolveLocation()`
- [ ] Implementare `resolveAssignee()`
- [ ] Implementare `calculateSimilarity()` (Levenshtein)

### GeminiService
- [ ] Inject `EntityResolver`
- [ ] Implementare `resolveEntityReferences()`
- [ ] Modificare `completeActiveTask()` per usare risoluzione
- [ ] Aggiornare system prompt

### ConversationContext
- [ ] Aggiungere campi `*Name` a `ProductCreation`
- [ ] Aggiungere `PendingEntityCreation` per creazione inline

### Hilt Module
- [ ] Registrare `EntityResolver` come `@Singleton`

---

## 8. Test Cases

### Test Risoluzione Esatta
```
Input: "fornitore Elettro Impianti Srl"
DB contiene: "Elettro Impianti Srl"
Atteso: Resolution.Found con ID corretto
```

### Test Risoluzione Parziale
```
Input: "fornitore Elettro Impianti"
DB contiene: "Elettro Impianti Srl"
Atteso: Resolution.Found (match parziale univoco)
```

### Test Ambiguità
```
Input: "fornitore Medika"
DB contiene: "Medika Srl", "Medika Service"
Atteso: Resolution.Ambiguous con entrambe le opzioni
```

### Test Non Trovato
```
Input: "fornitore Acme Corp"
DB non contiene nulla di simile
Atteso: Resolution.NotFound
```

### Test Conferma Fuzzy
```
Input: "fornitore Siemenz"
DB contiene: "Siemens Healthcare"
Atteso: Resolution.NeedsConfirmation (similarity ~0.75)
```

---

## 9. Note per Claude Code

1. **NON modificare i file CLAUDE.md** - sono documentazione, non codice
2. **Seguire i pattern esistenti** per DAO e Repository
3. **Testare su device reale** - il fuzzy matching deve gestire errori STT
4. **Log dettagliati** - loggare ogni step della risoluzione per debug
5. **Fallback sicuro** - se EntityResolver fallisce, NON bloccare il flusso

---

## 10. Relazione con Bug Esistenti

Questa specifica risolve alla radice:

| Bug | Come viene risolto |
|-----|-------------------|
| Loop dopo conferma | `completeActiveTask()` ora salva effettivamente |
| Dati persi in navigazione | Risoluzione avviene PRIMA di qualsiasi navigazione |
| Fornitore non memorizzato | `EntityResolver` + creazione inline |
| Location conferma senza save | `completeActiveTask()` chiama `locationRepository.insert()` |

---

**Fine Specifica**
