package org.incammino.hospiceinventory.ui.screens.maintainer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.incammino.hospiceinventory.R

/**
 * Schermata creazione/modifica manutentore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintainerEditScreen(
    maintainerId: String?,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: MaintainerEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Naviga dopo il salvataggio
    LaunchedEffect(uiState.savedMaintainerId) {
        if (uiState.savedMaintainerId != null) {
            onSaved()
        }
    }

    // Snackbar per errori
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.isNew) stringResource(R.string.maintainer_new)
                        else stringResource(R.string.maintainer_edit)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 12.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = viewModel::save) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.action_save)
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            MaintainerEditForm(
                uiState = uiState,
                onNameChange = viewModel::updateName,
                onEmailChange = viewModel::updateEmail,
                onPhoneChange = viewModel::updatePhone,
                onSpecializationChange = viewModel::updateSpecialization,
                onAddressChange = viewModel::updateAddress,
                onCityChange = viewModel::updateCity,
                onPostalCodeChange = viewModel::updatePostalCode,
                onProvinceChange = viewModel::updateProvince,
                onVatNumberChange = viewModel::updateVatNumber,
                onContactPersonChange = viewModel::updateContactPerson,
                onContactPhoneChange = viewModel::updateContactPhone,
                onContactEmailChange = viewModel::updateContactEmail,
                onIsSupplierChange = viewModel::updateIsSupplier,
                onNotesChange = viewModel::updateNotes,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

/**
 * Form di modifica manutentore.
 */
@Composable
private fun MaintainerEditForm(
    uiState: MaintainerEditUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSpecializationChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onCityChange: (String) -> Unit,
    onPostalCodeChange: (String) -> Unit,
    onProvinceChange: (String) -> Unit,
    onVatNumberChange: (String) -> Unit,
    onContactPersonChange: (String) -> Unit,
    onContactPhoneChange: (String) -> Unit,
    onContactEmailChange: (String) -> Unit,
    onIsSupplierChange: (Boolean) -> Unit,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sezione Dati Principali
        item {
            SectionCard(title = stringResource(R.string.section_main_data)) {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.maintainer_name) + " *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = uiState.name.isBlank() && uiState.error != null
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = onEmailChange,
                    label = { Text(stringResource(R.string.email)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.phone,
                    onValueChange = onPhoneChange,
                    label = { Text(stringResource(R.string.phone)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.specialization,
                    onValueChange = onSpecializationChange,
                    label = { Text(stringResource(R.string.maintainer_specialization)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        // Sezione Indirizzo
        item {
            SectionCard(title = stringResource(R.string.section_address)) {
                OutlinedTextField(
                    value = uiState.address,
                    onValueChange = onAddressChange,
                    label = { Text(stringResource(R.string.address)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.city,
                        onValueChange = onCityChange,
                        label = { Text(stringResource(R.string.city)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = uiState.postalCode,
                        onValueChange = onPostalCodeChange,
                        label = { Text(stringResource(R.string.postal_code)) },
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.province,
                    onValueChange = onProvinceChange,
                    label = { Text(stringResource(R.string.province)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        // Sezione Dati Fiscali
        item {
            SectionCard(title = stringResource(R.string.section_fiscal)) {
                OutlinedTextField(
                    value = uiState.vatNumber,
                    onValueChange = onVatNumberChange,
                    label = { Text(stringResource(R.string.maintainer_vat)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        // Sezione Referente
        item {
            SectionCard(title = stringResource(R.string.section_contact)) {
                OutlinedTextField(
                    value = uiState.contactPerson,
                    onValueChange = onContactPersonChange,
                    label = { Text(stringResource(R.string.maintainer_contact_person)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.contactPhone,
                    onValueChange = onContactPhoneChange,
                    label = { Text(stringResource(R.string.phone)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.contactEmail,
                    onValueChange = onContactEmailChange,
                    label = { Text(stringResource(R.string.email)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
            }
        }

        // Sezione Opzioni
        item {
            SectionCard(title = stringResource(R.string.section_options)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.maintainer_is_supplier),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.maintainer_is_supplier_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.isSupplier,
                        onCheckedChange = onIsSupplierChange
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = onNotesChange,
                    label = { Text(stringResource(R.string.notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
            }
        }

        // Spazio in fondo
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Card per sezione del form.
 */
@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            content()
        }
    }
}
