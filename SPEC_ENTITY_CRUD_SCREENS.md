# Specifiche: Screen CRUD Entità + Pulizia Dati Test

**Data**: 21 Dicembre 2025  
**Progetto**: Hospice Inventory  
**Priorità**: Alta - Prerequisito per import Excel e testing completo

---

## Obiettivo

Completare le funzionalità di inserimento/modifica manuale per tutte le entità principali, garantendo un fallback UI quando l'assistente vocale non è disponibile o preferito. Aggiungere anche la funzionalità di pulizia dati di test prima dell'import Excel di produzione.

---

## 1. LocationRepository

**Path**: `app/src/main/java/org/incammino/hospiceinventory/data/repository/LocationRepository.kt`

Il DAO `LocationDao` esiste già in `SupportDaos.kt`. Serve creare il Repository seguendo il pattern di `MaintainerRepository`.

### Struttura

```kotlin
package org.incammino.hospiceinventory.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import org.incammino.hospiceinventory.data.local.dao.LocationDao
import org.incammino.hospiceinventory.data.local.entity.LocationEntity
import org.incammino.hospiceinventory.domain.model.Location
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    private val locationDao: LocationDao
) {
    // ═══════════════════════════════════════════════════════════════════════════
    // QUERY
    // ═══════════════════════════════════════════════════════════════════════════
    
    fun getAllActive(): Flow<List<Location>>
    fun getById(id: String): Location?
    fun getByIdFlow(id: String): Flow<Location?>
    fun getRootLocations(): Flow<List<Location>>  // Sedi principali (parentId = null)
    fun getChildren(parentId: String): Flow<List<Location>>  // Sotto-ubicazioni
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CRUD
    // ═══════════════════════════════════════════════════════════════════════════
    
    suspend fun insert(location: Location): String
    suspend fun update(location: Location)
    suspend fun softDelete(id: String)  // Aggiungere query al DAO
    suspend fun delete(id: String)
    
    // ═══════════════════════════════════════════════════════════════════════════
    // BULK
    // ═══════════════════════════════════════════════════════════════════════════
    
    suspend fun insertAll(locations: List<Location>)
    suspend fun deleteAll()  // Per pulizia dati test
}
```

### Domain Model da creare

**Path**: `app/src/main/java/org/incammino/hospiceinventory/domain/model/Location.kt`

```kotlin
data class Location(
    val id: String = "",
    val name: String,
    val parentId: String? = null,
    val address: String? = null,
    val coordinates: String? = null,
    val notes: String? = null,
    val isActive: Boolean = true
)
```

### Mapping Extensions

Aggiungere nel file repository:

```kotlin
fun LocationEntity.toDomain(): Location = Location(...)
fun Location.toEntity(): LocationEntity = LocationEntity(...)
```

### DAO: Query mancante da aggiungere

In `SupportDaos.kt`, aggiungere a `LocationDao`:

```kotlin
@Query("UPDATE locations SET isActive = 0, updatedAt = :updatedAt WHERE id = :id")
suspend fun softDelete(id: String, updatedAt: kotlinx.datetime.Instant)

@Query("DELETE FROM locations")
suspend fun deleteAll()

@Query("SELECT * FROM locations WHERE id = :id")
fun getByIdFlow(id: String): Flow<LocationEntity?>
```

---

## 2. LocationEditScreen

**Path**: `app/src/main/java/org/incammino/hospiceinventory/ui/screens/location/`

### File da creare

1. `LocationEditScreen.kt`
2. `LocationEditViewModel.kt`
3. `LocationListScreen.kt` (opzionale ma consigliato)

### LocationEditViewModel

```kotlin
@HiltViewModel
class LocationEditViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val locationId: String? = savedStateHandle["locationId"]?.takeIf { it != "new" }
    
    data class UiState(
        val isNew: Boolean = true,
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        val name: String = "",
        val parentId: String? = null,
        val parentName: String? = null,  // Per visualizzazione
        val address: String? = null,
        val notes: String? = null,
        val availableParents: List<Location> = emptyList(),  // Per dropdown
        val error: String? = null,
        val savedLocationId: String? = null
    )
    
    // Validazione: name obbligatorio
    // parentId opzionale (se null = sede root)
}
```

### LocationEditScreen UI

Campi form:
- **Nome** (obbligatorio) - OutlinedTextField
- **Sede padre** (opzionale) - ExposedDropdownMenu con LocationRepository.getRootLocations()
- **Indirizzo** (opzionale) - OutlinedTextField
- **Note** (opzionale) - OutlinedTextField multiline

Pattern da seguire: `ProductEditScreen.kt`

---

## 3. MaintainerEditScreen

**Path**: `app/src/main/java/org/incammino/hospiceinventory/ui/screens/maintainer/`

Il `MaintainerRepository` esiste già. Serve solo la UI.

### File da creare

1. `MaintainerEditScreen.kt`
2. `MaintainerEditViewModel.kt`
3. `MaintainerListScreen.kt` (per gestione anagrafica)

### MaintainerEditViewModel

```kotlin
@HiltViewModel
class MaintainerEditViewModel @Inject constructor(
    private val maintainerRepository: MaintainerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val maintainerId: String? = savedStateHandle["maintainerId"]?.takeIf { it != "new" }
    
    data class UiState(
        val isNew: Boolean = true,
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        
        // Dati principali
        val name: String = "",
        val email: String = "",
        val phone: String = "",
        
        // Indirizzo
        val address: String = "",
        val city: String = "",
        val postalCode: String = "",
        val province: String = "",
        
        // Dati aziendali
        val vatNumber: String = "",
        val contactPerson: String = "",
        val contactPhone: String = "",
        val contactEmail: String = "",
        val specialization: String = "",
        
        // Flag
        val isSupplier: Boolean = false,  // Importante per garanzie
        
        // Note
        val notes: String = "",
        
        // UI state
        val error: String? = null,
        val savedMaintainerId: String? = null
    )
}
```

### MaintainerEditScreen UI

Organizzare in sezioni con `SectionHeader` (componente riutilizzabile):

**Sezione "Dati Principali"**
- Nome/Ragione Sociale (obbligatorio)
- Email
- Telefono
- Specializzazione

**Sezione "Indirizzo"**
- Indirizzo
- Città
- CAP
- Provincia (dropdown o autocomplete)

**Sezione "Dati Fiscali"**
- Partita IVA

**Sezione "Referente"**
- Nome referente
- Telefono referente
- Email referente

**Sezione "Opzioni"**
- Switch "È anche fornitore" (per garanzie)
- Note (multiline)

### Validazione

- `name` obbligatorio
- Se `email` presente, validare formato
- Se `vatNumber` presente, validare formato IT (11 cifre)

---

## 4. MaintenanceEditScreen (Completamento)

**Path**: `app/src/main/java/org/incammino/hospiceinventory/ui/screens/maintenance/`

Lo scheletro esiste. Serve completare con form funzionante.

### MaintenanceEditViewModel

```kotlin
@HiltViewModel
class MaintenanceEditViewModel @Inject constructor(
    private val maintenanceRepository: MaintenanceRepository,
    private val productRepository: ProductRepository,
    private val maintainerRepository: MaintainerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val maintenanceId: String? = savedStateHandle["maintenanceId"]?.takeIf { it != "new" }
    private val productId: String? = savedStateHandle["productId"]?.takeIf { it.isNotEmpty() }
    
    data class UiState(
        val isNew: Boolean = true,
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        
        // Prodotto (precaricato se passato via nav)
        val productId: String? = null,
        val productName: String = "",
        val productCategory: String = "",
        
        // Dati manutenzione
        val date: LocalDate = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).date,
        val type: MaintenanceType? = null,
        val outcome: MaintenanceOutcome? = null,
        val notes: String = "",
        
        // Costi
        val cost: String = "",  // String per input, parse a Double
        val invoiceNumber: String = "",
        val isWarrantyWork: Boolean = false,
        
        // Manutentore
        val maintainerId: String? = null,
        val maintainerName: String? = null,
        
        // Liste per dropdown
        val availableMaintainers: List<Maintainer> = emptyList(),
        val maintenanceTypes: List<MaintenanceType> = MaintenanceType.entries,
        val outcomeTypes: List<MaintenanceOutcome> = MaintenanceOutcome.entries,
        
        // UI state
        val showDatePicker: Boolean = false,
        val error: String? = null,
        val savedMaintenanceId: String? = null
    )
}
```

### MaintenanceEditScreen UI

**Sezione "Prodotto"** (se non preselezionato)
- Ricerca/selezione prodotto con SearchBar + risultati

**Sezione "Intervento"**
- Data (DatePicker)
- Tipo intervento (ExposedDropdownMenu)
- Esito (ExposedDropdownMenu)
- Descrizione/Note (multiline)

**Sezione "Esecutore"**
- Manutentore (ExposedDropdownMenu con maintainerRepository)
- Switch "Lavoro in garanzia"

**Sezione "Costi"** (visibile solo se isWarrantyWork = false)
- Costo €
- Numero fattura

### Logica speciale

1. Se `isWarrantyWork = true`, nascondere sezione costi
2. Dopo salvataggio, aggiornare `Product.lastMaintenanceDate` e `nextMaintenanceDue`
3. Se tipo = PROGRAMMATA, calcolare prossima scadenza da frequenza prodotto

---

## 5. Navigazione - Aggiornamenti

**File**: `app/src/main/java/org/incammino/hospiceinventory/ui/navigation/Navigation.kt`

### Nuove Route

```kotlin
sealed class Screen(val route: String) {
    // ... esistenti ...
    
    // Locations
    data object LocationList : Screen("locations")
    data object LocationEdit : Screen("location/edit/{locationId}") {
        fun createRoute(locationId: String?) = "location/edit/${locationId ?: "new"}"
    }
    
    // Maintainers
    data object MaintainerList : Screen("maintainers")
    data object MaintainerEdit : Screen("maintainer/edit/{maintainerId}") {
        fun createRoute(maintainerId: String?) = "maintainer/edit/${maintainerId ?: "new"}"
    }
    
    // Data Management
    data object DataManagement : Screen("data-management")
}
```

### Composable da aggiungere a NavHost

```kotlin
// Location List
composable(Screen.LocationList.route) {
    LocationListScreen(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToEdit = { locationId ->
            navController.navigate(Screen.LocationEdit.createRoute(locationId))
        },
        onNavigateToNew = {
            navController.navigate(Screen.LocationEdit.createRoute(null))
        }
    )
}

// Location Edit
composable(
    route = Screen.LocationEdit.route,
    arguments = listOf(navArgument("locationId") { type = NavType.StringType })
) { backStackEntry ->
    val locationId = backStackEntry.arguments?.getString("locationId")
    LocationEditScreen(
        locationId = if (locationId == "new") null else locationId,
        onNavigateBack = { navController.popBackStack() },
        onSaved = { navController.popBackStack() }
    )
}

// Maintainer List
composable(Screen.MaintainerList.route) {
    MaintainerListScreen(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToEdit = { maintainerId ->
            navController.navigate(Screen.MaintainerEdit.createRoute(maintainerId))
        },
        onNavigateToNew = {
            navController.navigate(Screen.MaintainerEdit.createRoute(null))
        }
    )
}

// Maintainer Edit
composable(
    route = Screen.MaintainerEdit.route,
    arguments = listOf(navArgument("maintainerId") { type = NavType.StringType })
) { backStackEntry ->
    val maintainerId = backStackEntry.arguments?.getString("maintainerId")
    MaintainerEditScreen(
        maintainerId = if (maintainerId == "new") null else maintainerId,
        onNavigateBack = { navController.popBackStack() },
        onSaved = { navController.popBackStack() }
    )
}

// Data Management
composable(Screen.DataManagement.route) {
    DataManagementScreen(
        onNavigateBack = { navController.popBackStack() }
    )
}
```

---

## 6. DataManagementScreen - Pulizia Dati Test

**Path**: `app/src/main/java/org/incammino/hospiceinventory/ui/screens/settings/DataManagementScreen.kt`

### Scopo

Permettere la pulizia dei dati di esempio/test dal database PRIMA dell'import Excel di produzione. Questo evita duplicati e conflitti.

### DataManagementViewModel

```kotlin
@HiltViewModel
class DataManagementViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val maintainerRepository: MaintainerRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val locationRepository: LocationRepository,
    private val database: HospiceDatabase  // Per accesso diretto se serve
) : ViewModel() {
    
    data class UiState(
        val isLoading: Boolean = false,
        val productCount: Int = 0,
        val maintainerCount: Int = 0,
        val maintenanceCount: Int = 0,
        val locationCount: Int = 0,
        val showConfirmDialog: Boolean = false,
        val operationInProgress: String? = null,
        val lastOperationResult: String? = null,
        val error: String? = null
    )
    
    // Azioni
    fun loadCounts()
    fun clearAllData()  // Richiede conferma
    fun clearProducts()
    fun clearMaintainers()
    fun clearMaintenances()
    fun clearLocations()
    fun clearSampleDataOnly()  // Elimina solo record con ID che iniziano con pattern specifici
}
```

### Logica clearSampleDataOnly()

I dati di esempio in `SampleDataPopulator` hanno ID con pattern riconoscibili:
- Prodotti: `prod-001`, `prod-002`, etc.
- Manutentori: `maint-001`, `maint-002`, etc.
- Locations: `loc-001`, `loc-002`, etc.

```kotlin
suspend fun clearSampleDataOnly() {
    // Eliminare record con ID che matchano pattern "prod-", "maint-", "loc-"
    // Questo preserva eventuali dati reali inseriti manualmente
    
    database.withTransaction {
        // Prima le manutenzioni (dipendenze)
        maintenanceRepository.deleteByProductIdPattern("prod-%")
        
        // Poi prodotti
        productRepository.deleteByIdPattern("prod-%")
        
        // Poi manutentori
        maintainerRepository.deleteByIdPattern("maint-%")
        
        // Poi locations
        locationRepository.deleteByIdPattern("loc-%")
    }
}
```

### Query da aggiungere ai DAO

```kotlin
// ProductDao
@Query("DELETE FROM products WHERE id LIKE :pattern")
suspend fun deleteByIdPattern(pattern: String)

// MaintainerDao
@Query("DELETE FROM maintainers WHERE id LIKE :pattern")
suspend fun deleteByIdPattern(pattern: String)

// LocationDao
@Query("DELETE FROM locations WHERE id LIKE :pattern")
suspend fun deleteByIdPattern(pattern: String)

// MaintenanceDao
@Query("DELETE FROM maintenances WHERE productId LIKE :pattern")
suspend fun deleteByProductIdPattern(pattern: String)
```

### DataManagementScreen UI

```
┌─────────────────────────────────────────┐
│ ← Gestione Dati                         │
├─────────────────────────────────────────┤
│                                         │
│  📊 STATISTICHE DATABASE                │
│  ─────────────────────────              │
│  Prodotti:      245                     │
│  Manutentori:    32                     │
│  Manutenzioni:  128                     │
│  Ubicazioni:     18                     │
│                                         │
├─────────────────────────────────────────┤
│                                         │
│  🧹 PULIZIA DATI                        │
│  ─────────────────────────              │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │  Elimina solo dati di esempio   │    │
│  │  (prod-*, maint-*, loc-*)       │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ⚠️ Raccomandato prima dell'import      │
│     Excel di produzione                 │
│                                         │
├─────────────────────────────────────────┤
│                                         │
│  🗑️ RESET COMPLETO                      │
│  ─────────────────────────              │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │  Elimina TUTTI i prodotti       │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │  Elimina TUTTI i manutentori    │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │  ⚠️ RESET DATABASE COMPLETO     │    │
│  └─────────────────────────────────┘    │
│                                         │
└─────────────────────────────────────────┘
```

### Conferma per operazioni distruttive

Usare `AlertDialog` con doppia conferma per "Reset completo":

```kotlin
@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    confirmText: String = "Elimina",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        }
    )
}
```

---

## 7. Accesso da Settings/Home

### SettingsScreen

Aggiungere voce menu per accedere a DataManagementScreen:

```kotlin
// In SettingsScreen.kt
SettingsItem(
    icon = Icons.Default.Storage,
    title = "Gestione Dati",
    subtitle = "Pulizia database, import/export",
    onClick = { onNavigateToDataManagement() }
)
```

### HomeScreen (opzionale)

Aggiungere accesso rapido ad anagrafica manutentori/ubicazioni nel menu o nei quick actions:

```kotlin
// Quick action per Manutentori
QuickActionButton(
    icon = Icons.Default.Engineering,
    label = "Manutentori",
    onClick = onNavigateToMaintainers
)

// Quick action per Ubicazioni
QuickActionButton(
    icon = Icons.Default.Place,
    label = "Ubicazioni", 
    onClick = onNavigateToLocations
)
```

---

## 8. Strings da aggiungere

**File**: `app/src/main/res/values/strings.xml`

```xml
<!-- Locations -->
<string name="location_list">Ubicazioni</string>
<string name="location_new">Nuova ubicazione</string>
<string name="location_edit">Modifica ubicazione</string>
<string name="location_name">Nome ubicazione</string>
<string name="location_parent">Sede padre</string>
<string name="location_address">Indirizzo</string>
<string name="location_no_parent">Nessuna (sede principale)</string>

<!-- Maintainers -->
<string name="maintainer_list">Manutentori</string>
<string name="maintainer_new">Nuovo manutentore</string>
<string name="maintainer_edit">Modifica manutentore</string>
<string name="maintainer_name">Nome/Ragione sociale</string>
<string name="maintainer_vat">Partita IVA</string>
<string name="maintainer_contact_person">Referente</string>
<string name="maintainer_specialization">Specializzazione</string>
<string name="maintainer_is_supplier">È anche fornitore</string>
<string name="maintainer_is_supplier_hint">Abilitare se fornisce anche prodotti in garanzia</string>

<!-- Maintenance Edit -->
<string name="maintenance_date">Data intervento</string>
<string name="maintenance_cost">Costo (€)</string>
<string name="maintenance_invoice">N. Fattura</string>
<string name="maintenance_warranty_work">Lavoro in garanzia</string>
<string name="maintenance_select_product">Seleziona prodotto</string>
<string name="maintenance_select_maintainer">Seleziona manutentore</string>

<!-- Data Management -->
<string name="data_management">Gestione Dati</string>
<string name="data_stats">Statistiche Database</string>
<string name="data_cleanup">Pulizia Dati</string>
<string name="data_cleanup_sample">Elimina solo dati di esempio</string>
<string name="data_cleanup_sample_hint">Rimuove record con ID prod-*, maint-*, loc-*</string>
<string name="data_cleanup_all">Reset Database Completo</string>
<string name="data_cleanup_confirm_title">Conferma eliminazione</string>
<string name="data_cleanup_confirm_sample">Eliminare tutti i dati di esempio? I dati inseriti manualmente verranno preservati.</string>
<string name="data_cleanup_confirm_all">ATTENZIONE: Questa azione eliminerà TUTTI i dati in modo irreversibile. Continuare?</string>
<string name="data_cleanup_success">Operazione completata</string>
<string name="data_cleanup_products">Elimina tutti i prodotti</string>
<string name="data_cleanup_maintainers">Elimina tutti i manutentori</string>

<!-- Sections -->
<string name="section_main_data">Dati Principali</string>
<string name="section_address">Indirizzo</string>
<string name="section_fiscal">Dati Fiscali</string>
<string name="section_contact">Referente</string>
<string name="section_options">Opzioni</string>
<string name="section_intervention">Intervento</string>
<string name="section_executor">Esecutore</string>
<string name="section_costs">Costi</string>
```

---

## 9. Ordine di Implementazione

1. **Domain Model Location** + **LocationRepository** + query DAO mancanti
2. **MaintainerEditScreen** + ViewModel (repository già esiste)
3. **LocationEditScreen** + ViewModel
4. **MaintenanceEditScreen** completamento
5. **DataManagementScreen** + ViewModel
6. **Navigazione** aggiornamenti
7. **Strings** e test

---

## 10. Checklist Finale

- [ ] LocationRepository creato con tutte le query
- [ ] Location domain model creato
- [ ] LocationDao query mancanti aggiunte
- [ ] LocationEditScreen + ViewModel funzionanti
- [ ] MaintainerEditScreen + ViewModel funzionanti
- [ ] MaintenanceEditScreen completata con tutti i campi
- [ ] DataManagementScreen con pulizia dati test
- [ ] Pattern deleteByIdPattern aggiunto a tutti i DAO
- [ ] Navigazione aggiornata con nuove route
- [ ] Accesso da SettingsScreen configurato
- [ ] Strings italiane aggiunte
- [ ] Build senza errori
- [ ] Test manuale: creazione location
- [ ] Test manuale: creazione manutentore
- [ ] Test manuale: registrazione manutenzione completa
- [ ] Test manuale: pulizia dati esempio

---

## Note per Claude Code

- Seguire i pattern esistenti in `ProductEditScreen` e `ProductEditViewModel`
- Usare `@HiltViewModel` e `@Inject constructor` per tutti i ViewModel
- Usare `StateFlow` con `_uiState` privato e `uiState` pubblico
- Form validation nel ViewModel, non nella UI
- Tutti i repository già usano `Clock.System.now()` per timestamp
- Material3 con TopAppBar, Scaffold, OutlinedTextField
- ExposedDropdownMenuBox per i select/dropdown
- DatePicker con `rememberDatePickerState()` da Material3
