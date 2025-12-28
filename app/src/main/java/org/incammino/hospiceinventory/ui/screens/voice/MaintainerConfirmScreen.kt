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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import org.incammino.hospiceinventory.service.voice.MaintainerConfirmData
import org.incammino.hospiceinventory.service.voice.SaveState
import org.incammino.hospiceinventory.ui.theme.AlertWarning

/**
 * Screen di conferma per nuovo manutentore.
 * Mostra i dati estratti da Gemini in forma editabile.
 *
 * Paradigma "Voice Dump + Visual Confirm" (Fase 3 - 28/12/2025)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintainerConfirmScreen(
    initialData: MaintainerConfirmData,
    viewModel: MaintainerConfirmViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit
) {
    val saveState by viewModel.saveState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Stato locale per i campi editabili
    var name by rememberSaveable { mutableStateOf(initialData.name) }
    var vatNumber by rememberSaveable { mutableStateOf(initialData.vatNumber) }
    var specialization by rememberSaveable { mutableStateOf(initialData.specialization) }
    var email by rememberSaveable { mutableStateOf(initialData.email) }
    var phone by rememberSaveable { mutableStateOf(initialData.phone) }
    var contactPerson by rememberSaveable { mutableStateOf(initialData.contactPerson) }
    var street by rememberSaveable { mutableStateOf(initialData.street) }
    var city by rememberSaveable { mutableStateOf(initialData.city) }
    var postalCode by rememberSaveable { mutableStateOf(initialData.postalCode) }
    var province by rememberSaveable { mutableStateOf(initialData.province) }
    var isSupplier by rememberSaveable { mutableStateOf(initialData.isSupplier) }
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
                title = { Text("Conferma Manutentore") },
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
                                vatNumber = vatNumber,
                                specialization = specialization,
                                email = email,
                                phone = phone,
                                contactPerson = contactPerson,
                                street = street,
                                city = city,
                                postalCode = postalCode,
                                province = province,
                                isSupplier = isSupplier,
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
                MaintainerWarningsCard(warnings = initialData.warnings)
            }

            // Sezione Azienda
            MaintainerSectionTitle("Azienda")

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome Azienda *") },
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
                    value = vatNumber,
                    onValueChange = { vatNumber = it },
                    label = { Text("Partita IVA") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = specialization,
                    onValueChange = { specialization = it },
                    label = { Text("Specializzazione") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    )
                )
            }

            // Sezione Contatti
            MaintainerSectionTitle("Contatti")

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Telefono") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                OutlinedTextField(
                    value = contactPerson,
                    onValueChange = { contactPerson = it },
                    label = { Text("Referente") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    )
                )
            }

            // Sezione Indirizzo
            MaintainerSectionTitle("Indirizzo")

            OutlinedTextField(
                value = street,
                onValueChange = { street = it },
                label = { Text("Via/Piazza") },
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
                    value = postalCode,
                    onValueChange = { postalCode = it },
                    label = { Text("CAP") },
                    modifier = Modifier.weight(0.3f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("Città") },
                    modifier = Modifier.weight(0.5f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    )
                )
                OutlinedTextField(
                    value = province,
                    onValueChange = { province = it.uppercase().take(2) },
                    label = { Text("Prov.") },
                    modifier = Modifier.weight(0.2f),
                    singleLine = true
                )
            }

            // Sezione Business
            MaintainerSectionTitle("Ruolo")

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "E' anche fornitore",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isSupplier,
                    onCheckedChange = { isSupplier = it }
                )
            }

            // Note
            MaintainerSectionTitle("Note")

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
                        vatNumber = vatNumber,
                        specialization = specialization,
                        email = email,
                        phone = phone,
                        contactPerson = contactPerson,
                        street = street,
                        city = city,
                        postalCode = postalCode,
                        province = province,
                        isSupplier = isSupplier,
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
                    Text("Salva Manutentore", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun MaintainerSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun MaintainerWarningsCard(warnings: List<String>) {
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

/**
 * Holder condiviso per passare dati tra schermate.
 */
object MaintainerDataHolder {
    private var data: MaintainerConfirmData? = null

    fun set(data: MaintainerConfirmData) {
        this.data = data
    }

    fun consume(): MaintainerConfirmData? {
        val result = data
        data = null
        return result
    }
}
