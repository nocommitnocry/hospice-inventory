package org.incammino.hospiceinventory.ui.screens.voice

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import org.incammino.hospiceinventory.domain.model.LocationType
import org.incammino.hospiceinventory.service.voice.LocationConfirmData
import org.incammino.hospiceinventory.service.voice.SaveState
import org.incammino.hospiceinventory.ui.theme.AlertWarning

/**
 * Screen di conferma per nuova ubicazione.
 * Mostra i dati estratti da Gemini in forma editabile.
 *
 * Paradigma "Voice Dump + Visual Confirm" (Fase 3 - 28/12/2025)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationConfirmScreen(
    initialData: LocationConfirmData,
    viewModel: LocationConfirmViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit
) {
    val saveState by viewModel.saveState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Stato locale per i campi editabili
    var name by rememberSaveable { mutableStateOf(initialData.name) }
    var type by rememberSaveable { mutableStateOf(initialData.type) }
    var buildingName by rememberSaveable { mutableStateOf(initialData.buildingName) }
    var floorCode by rememberSaveable { mutableStateOf(initialData.floorCode) }
    var floorName by rememberSaveable { mutableStateOf(initialData.floorName) }
    var department by rememberSaveable { mutableStateOf(initialData.department) }
    var hasOxygenOutlet by rememberSaveable { mutableStateOf(initialData.hasOxygenOutlet) }
    var bedCount by rememberSaveable { mutableIntStateOf(initialData.bedCount ?: 0) }
    var notes by rememberSaveable { mutableStateOf(initialData.notes) }

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
                title = { Text("Conferma Ubicazione") },
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
                                type = type,
                                buildingName = buildingName,
                                floorCode = floorCode,
                                floorName = floorName,
                                department = department,
                                hasOxygenOutlet = hasOxygenOutlet,
                                bedCount = bedCount.takeIf { it > 0 },
                                notes = notes
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
                LocationWarningsCard(warnings = initialData.warnings)
            }

            // Sezione Identificazione
            LocationSectionTitle("Identificazione")

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome Ubicazione *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words
                ),
                isError = name.isBlank()
            )

            LocationTypeSelector(
                selectedType = type,
                onTypeSelected = { type = it }
            )

            // Sezione Gerarchia
            LocationSectionTitle("Gerarchia")

            OutlinedTextField(
                value = buildingName,
                onValueChange = { buildingName = it },
                label = { Text("Edificio") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = floorCode,
                    onValueChange = { floorCode = it.uppercase() },
                    label = { Text("Codice Piano") },
                    modifier = Modifier.weight(0.4f),
                    singleLine = true,
                    placeholder = { Text("PT, P1, P-1") }
                )
                OutlinedTextField(
                    value = floorName,
                    onValueChange = { floorName = it },
                    label = { Text("Nome Piano") },
                    modifier = Modifier.weight(0.6f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    ),
                    placeholder = { Text("Piano Terra") }
                )
            }

            OutlinedTextField(
                value = department,
                onValueChange = { department = it },
                label = { Text("Reparto/Area") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words
                ),
                placeholder = { Text("Degenza, Ambulatorio, Direzione...") }
            )

            // Sezione Caratteristiche
            LocationSectionTitle("Caratteristiche")

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Attacco ossigeno",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = hasOxygenOutlet,
                    onCheckedChange = { hasOxygenOutlet = it }
                )
            }

            OutlinedTextField(
                value = if (bedCount > 0) bedCount.toString() else "",
                onValueChange = { newValue ->
                    bedCount = newValue.toIntOrNull() ?: 0
                },
                label = { Text("Posti letto") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // Note
            LocationSectionTitle("Note")

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Note aggiuntive") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Pulsante Salva
            Button(
                onClick = {
                    viewModel.save(
                        name = name,
                        type = type,
                        buildingName = buildingName,
                        floorCode = floorCode,
                        floorName = floorName,
                        department = department,
                        hasOxygenOutlet = hasOxygenOutlet,
                        bedCount = bedCount.takeIf { it > 0 },
                        notes = notes
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
                    Text("Salva Ubicazione", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun LocationSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun LocationWarningsCard(warnings: List<String>) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationTypeSelector(
    selectedType: String,
    onTypeSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val typeOptions = LocationType.entries.map { it.name to it.label }

    val displayText = typeOptions.find { it.first.equals(selectedType, ignoreCase = true) }?.second
        ?: selectedType.ifBlank { "Seleziona tipo" }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text("Tipo") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            typeOptions.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onTypeSelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Holder condiviso per passare dati tra schermate.
 */
object LocationDataHolder {
    private var data: LocationConfirmData? = null

    fun set(data: LocationConfirmData) {
        this.data = data
    }

    fun consume(): LocationConfirmData? {
        val result = data
        data = null
        return result
    }
}
