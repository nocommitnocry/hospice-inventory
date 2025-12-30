package org.incammino.hospiceinventory.ui.screens.product

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.datetime.LocalDate
import org.incammino.hospiceinventory.R
import org.incammino.hospiceinventory.ui.components.SelectableDropdownField
import org.incammino.hospiceinventory.domain.model.AccountType
import org.incammino.hospiceinventory.domain.model.Maintainer
import org.incammino.hospiceinventory.domain.model.MaintenanceFrequency

/**
 * Schermata creazione/modifica prodotto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditScreen(
    productId: String?,
    prefillData: Map<String, String>? = null,
    onNavigateBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: ProductEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Applica prefill se presente (solo una volta)
    LaunchedEffect(prefillData) {
        prefillData?.let { viewModel.applyPrefill(it) }
    }

    // Naviga dopo il salvataggio
    LaunchedEffect(uiState.savedProductId) {
        uiState.savedProductId?.let { onSaved(it) }
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
                        if (uiState.isNew) stringResource(R.string.product_new)
                        else stringResource(R.string.product_edit)
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
            ProductEditForm(
                uiState = uiState,
                onNameChange = viewModel::updateName,
                onBarcodeChange = viewModel::updateBarcode,
                onCategoryChange = viewModel::updateCategory,
                onLocationChange = viewModel::updateLocation,
                onDescriptionChange = viewModel::updateDescription,
                onSupplierChange = viewModel::updateSupplier,
                onPriceChange = viewModel::updatePrice,
                onAccountTypeChange = viewModel::updateAccountType,
                onNotesChange = viewModel::updateNotes,
                onWarrantyEndDateChange = viewModel::updateWarrantyEndDate,
                onWarrantyMaintainerChange = viewModel::updateWarrantyMaintainer,
                onMaintenanceFrequencyChange = viewModel::updateMaintenanceFrequency,
                onCustomIntervalDaysChange = viewModel::updateCustomIntervalDays,
                onServiceMaintainerChange = viewModel::updateServiceMaintainer,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

/**
 * Form di modifica prodotto.
 */
@Composable
private fun ProductEditForm(
    uiState: ProductEditUiState,
    onNameChange: (String) -> Unit,
    onBarcodeChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSupplierChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onAccountTypeChange: (AccountType?) -> Unit,
    onNotesChange: (String) -> Unit,
    onWarrantyEndDateChange: (LocalDate?) -> Unit,
    onWarrantyMaintainerChange: (String?) -> Unit,
    onMaintenanceFrequencyChange: (MaintenanceFrequency?) -> Unit,
    onCustomIntervalDaysChange: (String) -> Unit,
    onServiceMaintainerChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sezione Informazioni Base
        item {
            SectionCard(title = "Informazioni base") {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.product_name) + " *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = uiState.name.isBlank() && uiState.error != null
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.barcode,
                    onValueChange = onBarcodeChange,
                    label = { Text(stringResource(R.string.product_barcode)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { /* TODO: Scansione barcode */ }) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = stringResource(R.string.action_scan)
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Categoria con dropdown selezionabile
                SelectableDropdownField(
                    value = uiState.category,
                    onValueChange = onCategoryChange,
                    suggestions = uiState.categories,
                    label = stringResource(R.string.product_category),
                    isRequired = true,
                    isError = uiState.category.isBlank() && uiState.error != null
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Ubicazione con dropdown selezionabile
                SelectableDropdownField(
                    value = uiState.location,
                    onValueChange = onLocationChange,
                    suggestions = uiState.locations,
                    label = stringResource(R.string.product_location),
                    isRequired = true,
                    isError = uiState.location.isBlank() && uiState.error != null
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = onDescriptionChange,
                    label = { Text(stringResource(R.string.product_description)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        }

        // Sezione Acquisto
        item {
            SectionCard(title = "Dati acquisto") {
                SelectableDropdownField(
                    value = uiState.supplier,
                    onValueChange = onSupplierChange,
                    suggestions = uiState.suppliers,
                    label = "Fornitore"
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.price,
                        onValueChange = onPriceChange,
                        label = { Text(stringResource(R.string.product_price)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        prefix = { Text("€ ") }
                    )

                    AccountTypeDropdown(
                        selected = uiState.accountType,
                        onSelect = onAccountTypeChange,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Sezione Garanzia
        item {
            SectionCard(title = "Garanzia") {
                DatePickerField(
                    value = uiState.warrantyEndDate,
                    onValueChange = onWarrantyEndDateChange,
                    label = stringResource(R.string.warranty_end_date)
                )

                Spacer(modifier = Modifier.height(12.dp))

                MaintainerDropdown(
                    selected = uiState.warrantyMaintainerId,
                    maintainers = uiState.maintainers,
                    onSelect = onWarrantyMaintainerChange,
                    label = stringResource(R.string.warranty_maintainer)
                )
            }
        }

        // Sezione Manutenzione
        item {
            SectionCard(title = "Manutenzione programmata") {
                FrequencyDropdown(
                    selected = uiState.maintenanceFrequency,
                    onSelect = onMaintenanceFrequencyChange
                )

                if (uiState.maintenanceFrequency == MaintenanceFrequency.CUSTOM) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = uiState.customIntervalDays,
                        onValueChange = onCustomIntervalDaysChange,
                        label = { Text("Intervallo (giorni)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                MaintainerDropdown(
                    selected = uiState.serviceMaintainerId,
                    maintainers = uiState.maintainers,
                    onSelect = onServiceMaintainerChange,
                    label = stringResource(R.string.maintenance_service_maintainer)
                )
            }
        }

        // Sezione Note
        item {
            SectionCard(title = "Note") {
                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = onNotesChange,
                    label = { Text("Note aggiuntive") },
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

/**
 * Dropdown per tipo proprietà.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountTypeDropdown(
    selected: AccountType?,
    onSelect: (AccountType?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected?.label ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Proprietà") },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Nessuno") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            AccountType.entries.forEach { type ->
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

/**
 * Dropdown per frequenza manutenzione.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrequencyDropdown(
    selected: MaintenanceFrequency?,
    onSelect: (MaintenanceFrequency?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected?.label ?: "Nessuna",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.maintenance_frequency)) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Nessuna") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            MaintenanceFrequency.entries.forEach { freq ->
                DropdownMenuItem(
                    text = { Text(freq.label) },
                    onClick = {
                        onSelect(freq)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Dropdown per selezione manutentore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintainerDropdown(
    selected: String?,
    maintainers: List<Maintainer>,
    onSelect: (String?) -> Unit,
    label: String
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedMaintainer = maintainers.find { it.id == selected }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedMaintainer?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Nessuno") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            maintainers.forEach { maintainer ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(maintainer.name)
                            maintainer.specialization?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelect(maintainer.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Campo data con date picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    label: String
) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value?.let { "${it.dayOfMonth}/${it.monthNumber}/${it.year}" } ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            Row {
                if (value != null) {
                    IconButton(onClick = { onValueChange(null) }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Rimuovi data"
                        )
                    }
                }
                IconButton(onClick = { showDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Seleziona data"
                    )
                }
            }
        },
        singleLine = true
    )

    if (showDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = value?.let {
                it.toEpochDays() * 24 * 60 * 60 * 1000L
            }
        )

        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val epochDays = (millis / (24 * 60 * 60 * 1000)).toInt()
                            onValueChange(LocalDate.fromEpochDays(epochDays))
                        }
                        showDialog = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
