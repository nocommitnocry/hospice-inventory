package org.incammino.hospiceinventory.ui.screens.location

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.incammino.hospiceinventory.R
import org.incammino.hospiceinventory.domain.model.Location

/**
 * Schermata creazione/modifica ubicazione.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationEditScreen(
    locationId: String?,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: LocationEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Naviga dopo il salvataggio
    LaunchedEffect(uiState.savedLocationId) {
        if (uiState.savedLocationId != null) {
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
                        if (uiState.isNew) stringResource(R.string.location_new)
                        else stringResource(R.string.location_edit)
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
            LocationEditForm(
                uiState = uiState,
                onNameChange = viewModel::updateName,
                onParentChange = viewModel::updateParent,
                onAddressChange = viewModel::updateAddress,
                onNotesChange = viewModel::updateNotes,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

/**
 * Form di modifica ubicazione.
 */
@Composable
private fun LocationEditForm(
    uiState: LocationEditUiState,
    onNameChange: (String) -> Unit,
    onParentChange: (String?, String?) -> Unit,
    onAddressChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sezione Dati Ubicazione
        item {
            SectionCard(title = stringResource(R.string.section_main_data)) {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.location_name) + " *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = uiState.name.isBlank() && uiState.error != null
                )

                Spacer(modifier = Modifier.height(12.dp))

                ParentLocationDropdown(
                    selected = uiState.parentId,
                    selectedName = uiState.parentName,
                    locations = uiState.availableParents,
                    onSelect = onParentChange
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.address,
                    onValueChange = onAddressChange,
                    label = { Text(stringResource(R.string.location_address)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

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
 * Dropdown per selezione sede padre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentLocationDropdown(
    selected: String?,
    selectedName: String?,
    locations: List<Location>,
    onSelect: (String?, String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedName ?: stringResource(R.string.location_no_parent),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.location_parent)) },
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
            // Opzione "Nessuna"
            DropdownMenuItem(
                text = { Text(stringResource(R.string.location_no_parent)) },
                onClick = {
                    onSelect(null, null)
                    expanded = false
                }
            )

            // Tutte le ubicazioni disponibili
            locations.forEach { location ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(location.name)
                            location.address?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelect(location.id, location.name)
                        expanded = false
                    }
                )
            }
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
