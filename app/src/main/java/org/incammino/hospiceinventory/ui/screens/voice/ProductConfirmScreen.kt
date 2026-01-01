package org.incammino.hospiceinventory.ui.screens.voice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.incammino.hospiceinventory.domain.model.Location
import org.incammino.hospiceinventory.domain.model.Maintainer
import org.incammino.hospiceinventory.service.voice.LocationMatch
import org.incammino.hospiceinventory.service.voice.MaintainerMatch
import org.incammino.hospiceinventory.service.voice.ProductConfirmData
import org.incammino.hospiceinventory.service.voice.ProductFormData
import org.incammino.hospiceinventory.service.voice.SaveState
import org.incammino.hospiceinventory.ui.components.InlineEntityCreator
import org.incammino.hospiceinventory.ui.components.SelectableDropdownField
import org.incammino.hospiceinventory.ui.components.voice.VoiceContinueButton
import org.incammino.hospiceinventory.ui.theme.AlertWarning

/**
 * Screen di conferma per nuovo prodotto.
 * Mostra i dati estratti da Gemini in forma editabile.
 *
 * Paradigma "Voice Dump + Visual Confirm" (v2.0 - Fase 2)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductConfirmScreen(
    initialData: ProductConfirmData,
    viewModel: ProductConfirmViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    onNavigateToLocationSearch: () -> Unit = {},
    onNavigateToLocationEdit: ((String) -> Unit)? = null,
    onNavigateToBarcodeScanner: ((String) -> Unit)? = null
) {
    // Stato per dialogo conferma annullamento
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Intercetta back gesture per chiedere conferma
    BackHandler {
        showDiscardDialog = true
    }

    // Dialogo conferma annullamento
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Annullare?") },
            text = { Text("I dati inseriti andranno persi.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onNavigateBack()
                }) {
                    Text("Annulla")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Continua")
                }
            }
        )
    }

    val saveState by viewModel.saveState.collectAsState()
    val inlineCreationState by viewModel.inlineCreationState.collectAsState()
    val voiceContinueState by viewModel.voiceContinueState.collectAsState()
    val partialTranscript by viewModel.partialTranscript.collectAsState()
    val categoriesFromDb by viewModel.categories.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Stato locale per i campi editabili
    var name by rememberSaveable { mutableStateOf(initialData.name) }
    var model by rememberSaveable { mutableStateOf(initialData.model) }
    var manufacturer by rememberSaveable { mutableStateOf(initialData.manufacturer) }
    var serialNumber by rememberSaveable { mutableStateOf(initialData.serialNumber) }
    var barcode by rememberSaveable { mutableStateOf(initialData.barcode) }
    var category by rememberSaveable { mutableStateOf(initialData.category) }
    var locationMatch by remember { mutableStateOf(initialData.locationMatch) }
    var supplierMatch by remember { mutableStateOf(initialData.supplierMatch) }
    var warrantyMonths by rememberSaveable { mutableIntStateOf(initialData.warrantyMonths ?: 0) }
    var maintenanceFrequencyMonths by rememberSaveable {
        mutableIntStateOf(initialData.maintenanceFrequencyMonths ?: 0)
    }
    var notes by rememberSaveable { mutableStateOf("") }

    // Configura callback per aggiornamenti da voce
    LaunchedEffect(Unit) {
        // Callback per ricevere il transcript e passare i dati attuali del form
        viewModel.onProcessVoiceWithContext = { transcript ->
            // Costruisci FormData con i valori ATTUALI (incluse modifiche manuali)
            val currentFormData = ProductFormData(
                name = name,
                model = model,
                manufacturer = manufacturer,
                serialNumber = serialNumber,
                barcode = barcode,
                category = category,
                location = when (locationMatch) {
                    is LocationMatch.Found -> (locationMatch as LocationMatch.Found).location.name
                    is LocationMatch.NotFound -> (locationMatch as LocationMatch.NotFound).searchTerms
                    is LocationMatch.Ambiguous -> (locationMatch as LocationMatch.Ambiguous).searchTerms
                },
                supplier = when (supplierMatch) {
                    is MaintainerMatch.Found -> (supplierMatch as MaintainerMatch.Found).maintainer.name
                    is MaintainerMatch.NotFound -> (supplierMatch as MaintainerMatch.NotFound).name
                    else -> ""
                },
                warrantyMonths = warrantyMonths.takeIf { it > 0 },
                maintenanceFrequencyMonths = maintenanceFrequencyMonths.takeIf { it > 0 },
                notes = notes
            )
            // Chiama il ViewModel con transcript E contesto
            viewModel.processVoiceWithContext(transcript, currentFormData)
        }

        // Callback per applicare gli aggiornamenti ai campi
        viewModel.onVoiceUpdate = { updates ->
            updates["name"]?.let { name = it }
            updates["model"]?.let { model = it }
            updates["manufacturer"]?.let { manufacturer = it }
            updates["serialNumber"]?.let { serialNumber = it }
            updates["barcode"]?.let { barcode = it }
            updates["category"]?.let { category = it }
            updates["location"]?.let { locationName ->
                locationMatch = LocationMatch.NotFound(locationName)
            }
            updates["warrantyMonths"]?.toIntOrNull()?.let { warrantyMonths = it }
            updates["maintenanceFrequencyMonths"]?.toIntOrNull()?.let {
                maintenanceFrequencyMonths = it
            }
            updates["notes"]?.let { notes = it }
        }
    }

    // Gestisci esito salvataggio
    LaunchedEffect(saveState) {
        when (saveState) {
            is SaveState.Success -> {
                onSaved()
            }
            is SaveState.Error -> {
                snackbarHostState.showSnackbar((saveState as SaveState.Error).message)
                viewModel.reset()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conferma Prodotto") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.save(
                                name = name,
                                model = model,
                                manufacturer = manufacturer,
                                serialNumber = serialNumber,
                                barcode = barcode,
                                category = category,
                                locationMatch = locationMatch,
                                supplierMatch = supplierMatch,
                                warrantyMonths = warrantyMonths.takeIf { it > 0 },
                                warrantyMaintainerMatch = null, // TODO: gestire separatamente
                                maintenanceFrequencyMonths = maintenanceFrequencyMonths.takeIf { it > 0 },
                                notes = notes.takeIf { it.isNotBlank() }
                            )
                        },
                        enabled = saveState !is SaveState.Saving
                    ) {
                        if (saveState is SaveState.Saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(8.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, "Salva")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Warnings
            if (initialData.warnings.isNotEmpty()) {
                WarningsCard(warnings = initialData.warnings)
            }

            // Sezione Prodotto
            SectionTitle("Prodotto")

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words
                ),
                isError = name.isBlank()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = manufacturer,
                    onValueChange = { manufacturer = it },
                    label = { Text("Produttore") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    )
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Modello") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = serialNumber,
                    onValueChange = { serialNumber = it },
                    label = { Text("N. Serie") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("Barcode") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    trailingIcon = if (onNavigateToBarcodeScanner != null) {
                        {
                            IconButton(onClick = { onNavigateToBarcodeScanner(barcode) }) {
                                Icon(
                                    Icons.Default.QrCode2,
                                    contentDescription = "Scansiona barcode"
                                )
                            }
                        }
                    } else null
                )
            }

            CategorySelector(
                selectedCategory = category,
                categories = categoriesFromDb,
                onCategorySelected = { category = it }
            )

            // Sezione Ubicazione
            SectionTitle("Ubicazione")

            LocationSelector(
                locationMatch = locationMatch,
                onLocationSelected = { locationMatch = it },
                onSearchLocation = onNavigateToLocationSearch,
                onEditLocation = { locationId -> onNavigateToLocationEdit?.invoke(locationId) },
                isCreatingInline = inlineCreationState.isCreatingLocation,
                wasCreatedInline = inlineCreationState.locationWasCreatedInline,
                onCreateInline = { name ->
                    viewModel.createLocationInline(name) { match ->
                        locationMatch = match
                    }
                }
            )

            // Sezione Fornitore
            SectionTitle("Fornitore")

            SupplierSelector(
                supplierMatch = supplierMatch,
                onSupplierUpdated = { supplierMatch = it },
                isCreatingInline = inlineCreationState.isCreatingSupplier,
                wasCreatedInline = inlineCreationState.supplierWasCreatedInline,
                onCreateInline = { name ->
                    viewModel.createSupplierInline(name) { match ->
                        supplierMatch = match
                    }
                }
            )

            // Sezione Garanzia
            SectionTitle("Garanzia")

            WarrantySelector(
                months = warrantyMonths,
                onMonthsChanged = { warrantyMonths = it }
            )

            // Sezione Manutenzione
            SectionTitle("Manutenzione Periodica")

            MaintenanceFrequencySelector(
                months = maintenanceFrequencyMonths,
                onMonthsChanged = { maintenanceFrequencyMonths = it }
            )

            // Note
            SectionTitle("Note")

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Note aggiuntive") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            // Voice Continue Button
            Spacer(modifier = Modifier.height(16.dp))

            VoiceContinueButton(
                state = voiceContinueState,
                onTap = { viewModel.toggleVoiceInput() },
                partialTranscript = partialTranscript
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Pulsante Salva
            Button(
                onClick = {
                    viewModel.save(
                        name = name,
                        model = model,
                        manufacturer = manufacturer,
                        serialNumber = serialNumber,
                        barcode = barcode,
                        category = category,
                        locationMatch = locationMatch,
                        supplierMatch = supplierMatch,
                        warrantyMonths = warrantyMonths.takeIf { it > 0 },
                        warrantyMaintainerMatch = null,
                        maintenanceFrequencyMonths = maintenanceFrequencyMonths.takeIf { it > 0 },
                        notes = notes.takeIf { it.isNotBlank() }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = name.isNotBlank() && saveState !is SaveState.Saving
            ) {
                if (saveState is SaveState.Saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Check, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Salva Prodotto", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun WarningsCard(warnings: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = AlertWarning,
                modifier = Modifier.padding(end = 8.dp)
            )
            Column {
                warnings.forEach { warning ->
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySelector(
    selectedCategory: String,
    categories: List<String>,
    onCategorySelected: (String) -> Unit
) {
    // Lista di default se il DB è vuoto
    val effectiveCategories = categories.ifEmpty {
        listOf(
            "Elettromedicale",
            "Arredo",
            "Informatica",
            "Impianto",
            "Attrezzatura",
            "Ausili",
            "Cucina",
            "Lavanderia",
            "Altro"
        )
    }

    SelectableDropdownField(
        value = selectedCategory,
        onValueChange = onCategorySelected,
        suggestions = effectiveCategories,
        label = "Categoria",
        isRequired = true,
        isError = selectedCategory.isBlank()
    )
}

@Composable
private fun LocationSelector(
    locationMatch: LocationMatch,
    onLocationSelected: (LocationMatch) -> Unit,
    onSearchLocation: () -> Unit,
    onEditLocation: (locationId: String) -> Unit = {},
    isCreatingInline: Boolean = false,
    wasCreatedInline: Boolean = false,
    onCreateInline: (name: String) -> Unit = {}
) {
    var showAmbiguousDialog by remember { mutableStateOf(false) }

    when (locationMatch) {
        is LocationMatch.Found -> {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditLocation(locationMatch.location.id) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = locationMatch.location.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (locationMatch.location.needsCompletion) {
                            Text(
                                text = "Da completare",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        } else {
                            locationMatch.location.notes?.let { note ->
                                Text(
                                    text = note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        is LocationMatch.Ambiguous -> {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAmbiguousDialog = true },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "\"${locationMatch.searchTerms}\"",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "${locationMatch.candidates.size} ubicazioni trovate - tocca per scegliere",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = AlertWarning
                    )
                }
            }

            if (showAmbiguousDialog) {
                LocationAmbiguousDialog(
                    candidates = locationMatch.candidates,
                    onSelect = { location ->
                        onLocationSelected(LocationMatch.Found(location))
                        showAmbiguousDialog = false
                    },
                    onDismiss = { showAmbiguousDialog = false }
                )
            }
        }

        is LocationMatch.NotFound -> {
            if (locationMatch.searchTerms.isNotBlank()) {
                InlineEntityCreator(
                    entityName = locationMatch.searchTerms,
                    entityType = "Ubicazione",
                    onCreateClick = { onCreateInline(locationMatch.searchTerms) },
                    isCreating = isCreatingInline,
                    wasCreated = wasCreatedInline
                )
            } else {
                OutlinedTextField(
                    value = "",
                    onValueChange = { newValue ->
                        onLocationSelected(LocationMatch.NotFound(newValue))
                    },
                    label = { Text("Ubicazione") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        Text("Inserisci un'ubicazione")
                    }
                )
            }
        }
    }
}

@Composable
private fun LocationAmbiguousDialog(
    candidates: List<Location>,
    onSelect: (Location) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleziona ubicazione") },
        text = {
            Column {
                candidates.forEach { location ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelect(location) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = location.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            location.notes?.let { note ->
                                Text(
                                    text = note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        }
    )
}

@Composable
private fun SupplierSelector(
    supplierMatch: MaintainerMatch,
    onSupplierUpdated: (MaintainerMatch) -> Unit,
    isCreatingInline: Boolean = false,
    wasCreatedInline: Boolean = false,
    onCreateInline: (name: String) -> Unit = {}
) {
    var showAmbiguousDialog by remember { mutableStateOf(false) }

    when (supplierMatch) {
        is MaintainerMatch.Found -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = supplierMatch.maintainer.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (supplierMatch.maintainer.needsCompletion) {
                            Text(
                                text = "Da completare",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        } else {
                            supplierMatch.maintainer.email?.let { email ->
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        is MaintainerMatch.Ambiguous -> {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAmbiguousDialog = true },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "\"${supplierMatch.query}\"",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "${supplierMatch.candidates.size} fornitori trovati - tocca per scegliere",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = AlertWarning
                    )
                }
            }

            if (showAmbiguousDialog) {
                SupplierAmbiguousDialog(
                    candidates = supplierMatch.candidates,
                    onSelect = { maintainer ->
                        onSupplierUpdated(MaintainerMatch.Found(maintainer))
                        showAmbiguousDialog = false
                    },
                    onDismiss = { showAmbiguousDialog = false }
                )
            }
        }

        is MaintainerMatch.NotFound -> {
            if (supplierMatch.name.isNotBlank()) {
                InlineEntityCreator(
                    entityName = supplierMatch.name,
                    entityType = "Fornitore",
                    onCreateClick = { onCreateInline(supplierMatch.name) },
                    isCreating = isCreatingInline,
                    wasCreated = wasCreatedInline
                )
            } else {
                OutlinedTextField(
                    value = "",
                    onValueChange = { newValue ->
                        onSupplierUpdated(MaintainerMatch.NotFound(newValue, supplierMatch.company))
                    },
                    label = { Text("Fornitore") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        Text("Inserisci un fornitore")
                    }
                )
            }
        }

        is MaintainerMatch.SelfReported -> {
            Text(
                text = "Fornitore interno",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SupplierAmbiguousDialog(
    candidates: List<Maintainer>,
    onSelect: (Maintainer) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleziona fornitore") },
        text = {
            Column {
                candidates.forEach { maintainer ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelect(maintainer) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = maintainer.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            maintainer.email?.let { email ->
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WarrantySelector(
    months: Int,
    onMonthsChanged: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val options = listOf(
        0 to "Nessuna",
        6 to "6 mesi",
        12 to "1 anno",
        24 to "2 anni",
        36 to "3 anni",
        60 to "5 anni"
    )

    val displayText = options.find { it.first == months }?.second
        ?: if (months > 0) "$months mesi" else "Nessuna"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text("Durata garanzia") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onMonthsChanged(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceFrequencySelector(
    months: Int,
    onMonthsChanged: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val options = listOf(
        0 to "Nessuna",
        3 to "Trimestrale (3 mesi)",
        6 to "Semestrale (6 mesi)",
        12 to "Annuale",
        24 to "Biennale (2 anni)",
        36 to "Triennale (3 anni)",
        48 to "Quadriennale (4 anni)",
        60 to "Quinquennale (5 anni)"
    )

    val displayText = options.find { it.first == months }?.second
        ?: if (months > 0) "Ogni $months mesi" else "Nessuna"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text("Frequenza manutenzione") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onMonthsChanged(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Holder condiviso per passare dati tra schermate (come MaintenanceDataHolder).
 *
 * Pattern robusto per evitare race condition:
 * - get() restituisce i dati senza consumarli
 * - clear() cancella i dati esplicitamente
 * - consume() mantiene compatibilità ma preferire get()+clear()
 */
object ProductDataHolder {
    private var data: ProductConfirmData? = null

    fun set(data: ProductConfirmData) {
        this.data = data
    }

    /**
     * Restituisce i dati senza consumarli.
     * Usare clear() quando i dati sono stati processati.
     */
    fun get(): ProductConfirmData? = data

    /**
     * Cancella i dati esplicitamente.
     * Chiamare dopo aver salvato o annullato.
     */
    fun clear() {
        data = null
    }

    /**
     * Compatibilità: restituisce e cancella i dati.
     * Preferire get()+clear() per evitare race condition.
     */
    fun consume(): ProductConfirmData? {
        val result = data
        data = null
        return result
    }
}
