package org.incammino.hospiceinventory.ui.screens.maintenance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.datetime.LocalDate
import org.incammino.hospiceinventory.R
import org.incammino.hospiceinventory.domain.model.Maintainer
import org.incammino.hospiceinventory.domain.model.MaintenanceOutcome
import org.incammino.hospiceinventory.domain.model.MaintenanceType
import org.incammino.hospiceinventory.domain.model.Product

/**
 * Schermata creazione/modifica manutenzione.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceEditScreen(
    maintenanceId: String?,
    productId: String?,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: MaintenanceEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Naviga dopo il salvataggio
    LaunchedEffect(uiState.savedMaintenanceId) {
        if (uiState.savedMaintenanceId != null) {
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
                        if (uiState.isNew) stringResource(R.string.maintenance_add)
                        else stringResource(R.string.maintenance_edit)
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
            MaintenanceEditForm(
                uiState = uiState,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onProductSelect = viewModel::selectProduct,
                onProductClear = viewModel::clearProduct,
                onDateChange = viewModel::updateDate,
                onTypeChange = viewModel::updateType,
                onOutcomeChange = viewModel::updateOutcome,
                onNotesChange = viewModel::updateNotes,
                onCostChange = viewModel::updateCost,
                onInvoiceNumberChange = viewModel::updateInvoiceNumber,
                onIsWarrantyWorkChange = viewModel::updateIsWarrantyWork,
                onMaintainerChange = viewModel::updateMaintainer,
                onToggleDatePicker = viewModel::toggleDatePicker,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

/**
 * Form di modifica manutenzione.
 */
@Composable
private fun MaintenanceEditForm(
    uiState: MaintenanceEditUiState,
    onSearchQueryChange: (String) -> Unit,
    onProductSelect: (Product) -> Unit,
    onProductClear: () -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTypeChange: (MaintenanceType?) -> Unit,
    onOutcomeChange: (MaintenanceOutcome?) -> Unit,
    onNotesChange: (String) -> Unit,
    onCostChange: (String) -> Unit,
    onInvoiceNumberChange: (String) -> Unit,
    onIsWarrantyWorkChange: (Boolean) -> Unit,
    onMaintainerChange: (String?, String?) -> Unit,
    onToggleDatePicker: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sezione Prodotto
        item {
            SectionCard(title = stringResource(R.string.maintenance_select_product)) {
                if (uiState.productId != null) {
                    // Prodotto selezionato
                    ProductSelectedCard(
                        productName = uiState.productName,
                        productCategory = uiState.productCategory,
                        onClear = onProductClear
                    )
                } else {
                    // Ricerca prodotto
                    ProductSearchField(
                        query = uiState.searchQuery,
                        results = uiState.searchResults,
                        onQueryChange = onSearchQueryChange,
                        onProductSelect = onProductSelect
                    )
                }
            }
        }

        // Sezione Intervento
        item {
            SectionCard(title = stringResource(R.string.section_intervention)) {
                // Data
                DatePickerField(
                    value = uiState.date,
                    onValueChange = onDateChange,
                    label = stringResource(R.string.maintenance_date),
                    showDialog = uiState.showDatePicker,
                    onToggleDialog = onToggleDatePicker
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Tipo intervento
                MaintenanceTypeDropdown(
                    selected = uiState.type,
                    types = uiState.maintenanceTypes,
                    onSelect = onTypeChange
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Esito
                MaintenanceOutcomeDropdown(
                    selected = uiState.outcome,
                    outcomes = uiState.outcomeTypes,
                    onSelect = onOutcomeChange
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Note/Descrizione
                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = onNotesChange,
                    label = { Text(stringResource(R.string.maintenance_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
            }
        }

        // Sezione Esecutore
        item {
            SectionCard(title = stringResource(R.string.section_executor)) {
                MaintainerDropdown(
                    selected = uiState.maintainerId,
                    selectedName = uiState.maintainerName,
                    maintainers = uiState.availableMaintainers,
                    onSelect = onMaintainerChange
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Switch garanzia
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.maintenance_warranty_work),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = uiState.isWarrantyWork,
                        onCheckedChange = onIsWarrantyWorkChange
                    )
                }
            }
        }

        // Sezione Costi (solo se non è lavoro in garanzia)
        if (!uiState.isWarrantyWork) {
            item {
                SectionCard(title = stringResource(R.string.section_costs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.cost,
                            onValueChange = onCostChange,
                            label = { Text(stringResource(R.string.maintenance_cost)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            prefix = { Text("\u20AC ") }
                        )

                        OutlinedTextField(
                            value = uiState.invoiceNumber,
                            onValueChange = onInvoiceNumberChange,
                            label = { Text(stringResource(R.string.maintenance_invoice)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }
        }

        // Spazio in fondo
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Card prodotto selezionato.
 */
@Composable
private fun ProductSelectedCard(
    productName: String,
    productCategory: String,
    onClear: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = productName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = productCategory,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Rimuovi",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * Campo ricerca prodotto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductSearchField(
    query: String,
    results: List<Product>,
    onQueryChange: (String) -> Unit,
    onProductSelect: (Product) -> Unit
) {
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Cerca prodotto...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Pulisci")
                    }
                }
            }
        )

        // Risultati ricerca
        if (results.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))

            Card {
                Column {
                    results.take(5).forEach { product ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = product.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = product.category,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            leadingContent = {
                                Icon(Icons.Default.Inventory2, contentDescription = null)
                            },
                            modifier = Modifier.clickable { onProductSelect(product) }
                        )
                        if (product != results.take(5).last()) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dropdown tipo manutenzione.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceTypeDropdown(
    selected: MaintenanceType?,
    types: List<MaintenanceType>,
    onSelect: (MaintenanceType?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected?.displayName ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.maintenance_type) + " *") },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
            isError = selected == null
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            types.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.displayName) },
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
 * Dropdown esito manutenzione.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceOutcomeDropdown(
    selected: MaintenanceOutcome?,
    outcomes: List<MaintenanceOutcome>,
    onSelect: (MaintenanceOutcome?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected?.label ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.maintenance_outcome)) },
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
            outcomes.forEach { outcome ->
                DropdownMenuItem(
                    text = { Text(outcome.label) },
                    onClick = {
                        onSelect(outcome)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Dropdown manutentore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintainerDropdown(
    selected: String?,
    selectedName: String?,
    maintainers: List<Maintainer>,
    onSelect: (String?, String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedName ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.maintenance_select_maintainer)) },
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
                    onSelect(null, null)
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
                        onSelect(maintainer.id, maintainer.name)
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
    value: LocalDate,
    onValueChange: (LocalDate) -> Unit,
    label: String,
    showDialog: Boolean,
    onToggleDialog: (Boolean) -> Unit
) {
    OutlinedTextField(
        value = "${value.dayOfMonth}/${value.monthNumber}/${value.year}",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleDialog(true) },
        trailingIcon = {
            IconButton(onClick = { onToggleDialog(true) }) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "Seleziona data"
                )
            }
        },
        singleLine = true
    )

    if (showDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = value.toEpochDays() * 24 * 60 * 60 * 1000L
        )

        DatePickerDialog(
            onDismissRequest = { onToggleDialog(false) },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val epochDays = (millis / (24 * 60 * 60 * 1000)).toInt()
                            onValueChange(LocalDate.fromEpochDays(epochDays))
                        }
                        onToggleDialog(false)
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { onToggleDialog(false) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
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
