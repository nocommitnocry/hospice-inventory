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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.datetime.LocalDate
import org.incammino.hospiceinventory.domain.model.Maintainer
import org.incammino.hospiceinventory.domain.model.MaintenanceType
import org.incammino.hospiceinventory.domain.model.Product
import org.incammino.hospiceinventory.service.voice.MaintenanceConfirmData
import org.incammino.hospiceinventory.service.voice.MaintenanceFormData
import org.incammino.hospiceinventory.service.voice.MaintainerMatch
import org.incammino.hospiceinventory.service.voice.ProductMatch
import org.incammino.hospiceinventory.service.voice.SaveState
import org.incammino.hospiceinventory.ui.components.InlineEntityCreator
import org.incammino.hospiceinventory.ui.components.voice.VoiceContinueButton

/**
 * Screen di conferma manutenzione.
 * Mostra i dati estratti da Gemini e permette di modificarli prima del salvataggio.
 *
 * Paradigma "Voice Dump + Visual Confirm" (v2.0 - 26/12/2025)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceConfirmScreen(
    initialData: MaintenanceConfirmData,
    viewModel: MaintenanceConfirmViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    onNavigateToProductSearch: () -> Unit
) {
    // Intercetta back gesture per garantire cleanup del contesto Gemini
    BackHandler {
        onNavigateBack()  // Delega al callback che fa cleanup
    }

    val saveState by viewModel.saveState.collectAsState()
    val inlineCreationState by viewModel.inlineCreationState.collectAsState()
    val voiceContinueState by viewModel.voiceContinueState.collectAsState()
    val partialTranscript by viewModel.partialTranscript.collectAsState()

    // Stati editabili locali
    var selectedProduct by remember { mutableStateOf(initialData.productMatch) }
    var selectedMaintainer by remember { mutableStateOf(initialData.maintainerMatch) }
    var selectedType by remember { mutableStateOf(initialData.type) }
    var description by remember { mutableStateOf(initialData.description) }
    var durationMinutes by remember {
        mutableStateOf(initialData.durationMinutes?.toString() ?: "")
    }
    var isWarranty by remember { mutableStateOf(initialData.isWarranty) }
    var date by remember { mutableStateOf(initialData.date) }
    var notes by remember { mutableStateOf("") }

    // Configura callback per aggiornamenti da voce
    LaunchedEffect(Unit) {
        // Callback per ricevere il transcript e passare i dati attuali del form
        viewModel.onProcessVoiceWithContext = { transcript ->
            // Costruisci FormData con i valori ATTUALI (incluse modifiche manuali)
            val currentFormData = MaintenanceFormData(
                productName = when (selectedProduct) {
                    is ProductMatch.Found -> (selectedProduct as ProductMatch.Found).product.name
                    is ProductMatch.NotFound -> (selectedProduct as ProductMatch.NotFound).searchTerms
                    is ProductMatch.Ambiguous -> (selectedProduct as ProductMatch.Ambiguous).searchTerms
                },
                maintainerName = when (selectedMaintainer) {
                    is MaintainerMatch.Found -> (selectedMaintainer as MaintainerMatch.Found).maintainer.name
                    is MaintainerMatch.NotFound -> (selectedMaintainer as MaintainerMatch.NotFound).name
                    is MaintainerMatch.Ambiguous -> (selectedMaintainer as MaintainerMatch.Ambiguous).query
                    is MaintainerMatch.SelfReported -> "Operatore"
                },
                type = selectedType?.label ?: "",
                description = description,
                durationMinutes = durationMinutes.toIntOrNull(),
                isWarranty = isWarranty,
                date = date.toString(),
                notes = notes
            )
            // Chiama il ViewModel con transcript E contesto
            viewModel.processVoiceWithContext(transcript, currentFormData)
        }

        // Callback per applicare gli aggiornamenti ai campi
        viewModel.onVoiceUpdate = { updates ->
            updates["maintainerName"]?.let { name ->
                selectedMaintainer = MaintainerMatch.NotFound(name, null)
            }
            updates["type"]?.let { typeStr ->
                MaintenanceType.entries.find { it.name.equals(typeStr, ignoreCase = true) }
                    ?.let { selectedType = it }
            }
            updates["description"]?.let { description = it }
            updates["durationMinutes"]?.toIntOrNull()?.let { durationMinutes = it.toString() }
            updates["notes"]?.let { notes = it }
        }
    }

    // Navigazione dopo salvataggio
    LaunchedEffect(saveState) {
        if (saveState is SaveState.Success) {
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conferma Manutenzione") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(onClick = onNavigateBack) {
                        Text("Annulla")
                    }

                    Button(
                        onClick = {
                            viewModel.save(
                                productMatch = selectedProduct,
                                maintainerMatch = selectedMaintainer,
                                type = selectedType,
                                description = description,
                                durationMinutes = durationMinutes.toIntOrNull(),
                                isWarranty = isWarranty,
                                date = date,
                                notes = notes.takeIf { it.isNotBlank() }
                            )
                        },
                        enabled = selectedProduct is ProductMatch.Found &&
                                  selectedType != null &&
                                  description.isNotBlank() &&
                                  saveState !is SaveState.Saving
                    ) {
                        if (saveState is SaveState.Saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Save, null, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Salva")
                    }
                }
            }
        }
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

            // Prodotto
            ProductSelectionCard(
                match = selectedProduct,
                onSearchClick = onNavigateToProductSearch,
                onSelect = { selectedProduct = ProductMatch.Found(it) }
            )

            // Tipo intervento
            MaintenanceTypeSelector(
                selected = selectedType,
                onSelect = { selectedType = it }
            )

            // Descrizione
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descrizione intervento *") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                isError = description.isBlank()
            )

            // Manutentore
            MaintainerSelectionCard(
                match = selectedMaintainer,
                onSelect = { selectedMaintainer = MaintainerMatch.Found(it) },
                isCreatingInline = inlineCreationState.isCreatingMaintainer,
                wasCreatedInline = inlineCreationState.maintainerWasCreatedInline,
                onCreateInline = { name, company ->
                    viewModel.createMaintainerInline(name, company) { match ->
                        selectedMaintainer = match
                    }
                }
            )

            // Data e Durata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = date.toString(),
                    onValueChange = { },
                    label = { Text("Data") },
                    modifier = Modifier.weight(1f),
                    readOnly = true
                )

                OutlinedTextField(
                    value = durationMinutes,
                    onValueChange = { durationMinutes = it.filter { c -> c.isDigit() } },
                    label = { Text("Durata (min)") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Garanzia
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isWarranty,
                    onCheckedChange = { isWarranty = it }
                )
                Text("Intervento in garanzia")
            }

            // Note aggiuntive
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Note aggiuntive (opzionale)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            // Voice Continue Button
            Spacer(modifier = Modifier.height(16.dp))

            VoiceContinueButton(
                state = voiceContinueState,
                onTap = { viewModel.toggleVoiceInput() },
                partialTranscript = partialTranscript
            )

            // Errore salvataggio
            if (saveState is SaveState.Error) {
                Text(
                    text = (saveState as SaveState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(80.dp)) // Spazio per BottomAppBar
        }
    }
}

@Composable
private fun WarningsCard(warnings: List<String>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            warnings.forEach { warning ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(warning, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ProductSelectionCard(
    match: ProductMatch,
    onSearchClick: () -> Unit,
    onSelect: (Product) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Prodotto *",
                    style = MaterialTheme.typography.labelLarge
                )
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, "Cerca")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (match) {
                is ProductMatch.Found -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                match.product.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            match.product.location?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                is ProductMatch.Ambiguous -> {
                    Text(
                        "Trovati ${match.candidates.size} risultati per \"${match.searchTerms}\":",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    match.candidates.take(3).forEach { product ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(product) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = false,
                                onClick = { onSelect(product) }
                            )
                            Column {
                                Text(product.name)
                                product.location?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                is ProductMatch.NotFound -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        if (match.searchTerms.isNotBlank()) {
                            Text(
                                "\"${match.searchTerms}\" non trovato",
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                "Nessun prodotto specificato",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceTypeSelector(
    selected: MaintenanceType?,
    onSelect: (MaintenanceType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected?.label ?: "",
            onValueChange = { },
            readOnly = true,
            label = { Text("Tipo intervento *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            isError = selected == null
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            MaintenanceType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MaintainerSelectionCard(
    match: MaintainerMatch,
    onSelect: (Maintainer) -> Unit,
    isCreatingInline: Boolean = false,
    wasCreatedInline: Boolean = false,
    onCreateInline: (name: String, company: String?) -> Unit = { _, _ -> }
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Eseguito da",
                style = MaterialTheme.typography.labelLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (match) {
                is MaintainerMatch.Found -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(match.maintainer.name)
                            if (match.maintainer.needsCompletion) {
                                Text(
                                    "Da completare",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }

                is MaintainerMatch.Ambiguous -> {
                    Text(
                        "Trovati ${match.candidates.size} risultati per \"${match.query}\":",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    match.candidates.forEach { maintainer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(maintainer) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = false,
                                onClick = { onSelect(maintainer) }
                            )
                            Text(maintainer.name)
                        }
                    }
                }

                is MaintainerMatch.NotFound -> {
                    val displayName = buildString {
                        if (match.name.isNotBlank()) append(match.name)
                        if (match.company != null && match.company != match.name) {
                            if (isNotEmpty()) append(" - ")
                            append(match.company)
                        }
                    }

                    if (displayName.isNotBlank()) {
                        InlineEntityCreator(
                            entityName = displayName,
                            entityType = "Manutentore",
                            onCreateClick = { onCreateInline(match.name, match.company) },
                            isCreating = isCreatingInline,
                            wasCreated = wasCreatedInline
                        )
                    } else {
                        Text(
                            "Manutentore non specificato",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is MaintainerMatch.SelfReported -> {
                    Text(
                        "Manutentore che sta parlando",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
