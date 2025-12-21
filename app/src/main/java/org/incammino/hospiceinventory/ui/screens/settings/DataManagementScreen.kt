package org.incammino.hospiceinventory.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.incammino.hospiceinventory.R

/**
 * Schermata gestione dati.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: DataManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Snackbar per messaggi
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(uiState.lastOperationResult) {
        uiState.lastOperationResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearResult()
        }
    }

    // Dialog di conferma
    if (uiState.showConfirmDialog && uiState.pendingOperation != null) {
        ConfirmDeleteDialog(
            operation = uiState.pendingOperation!!,
            onConfirm = viewModel::confirmOperation,
            onDismiss = viewModel::cancelOperation
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.data_management)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading || uiState.operationInProgress) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    if (uiState.operationInProgress) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Operazione in corso...")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sezione Statistiche
                item {
                    StatsSection(
                        productCount = uiState.productCount,
                        maintainerCount = uiState.maintainerCount,
                        maintenanceCount = uiState.maintenanceCount,
                        locationCount = uiState.locationCount
                    )
                }

                // Sezione Pulizia Dati Esempio
                item {
                    SampleDataSection(
                        onCleanup = { viewModel.requestConfirmation(CleanupOperation.SAMPLE_DATA) }
                    )
                }

                // Sezione Reset Completo
                item {
                    ResetSection(
                        onDeleteProducts = { viewModel.requestConfirmation(CleanupOperation.ALL_PRODUCTS) },
                        onDeleteMaintainers = { viewModel.requestConfirmation(CleanupOperation.ALL_MAINTAINERS) },
                        onDeleteMaintenances = { viewModel.requestConfirmation(CleanupOperation.ALL_MAINTENANCES) },
                        onDeleteLocations = { viewModel.requestConfirmation(CleanupOperation.ALL_LOCATIONS) },
                        onFullReset = { viewModel.requestConfirmation(CleanupOperation.FULL_RESET) }
                    )
                }

                // Spazio in fondo
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * Sezione statistiche database.
 */
@Composable
private fun StatsSection(
    productCount: Int,
    maintainerCount: Int,
    maintenanceCount: Int,
    locationCount: Int
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.data_stats),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            StatRow(label = "Prodotti", count = productCount, icon = Icons.Default.Inventory2)
            StatRow(label = "Manutentori", count = maintainerCount, icon = Icons.Default.Engineering)
            StatRow(label = "Manutenzioni", count = maintenanceCount, icon = Icons.Default.Build)
            StatRow(label = "Ubicazioni", count = locationCount, icon = Icons.Default.Place)
        }
    }
}

/**
 * Riga statistica.
 */
@Composable
private fun StatRow(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Sezione pulizia dati di esempio.
 */
@Composable
private fun SampleDataSection(onCleanup: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.data_cleanup),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onCleanup,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.data_cleanup_sample))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.data_cleanup_sample_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Raccomandato prima dell'import Excel di produzione",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

/**
 * Sezione reset completo.
 */
@Composable
private fun ResetSection(
    onDeleteProducts: () -> Unit,
    onDeleteMaintainers: () -> Unit,
    onDeleteMaintenances: () -> Unit,
    onDeleteLocations: () -> Unit,
    onFullReset: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Reset Completo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottoni singoli
            OutlinedButton(
                onClick = onDeleteProducts,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Inventory2, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.data_cleanup_products))
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onDeleteMaintainers,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Engineering, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.data_cleanup_maintainers))
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onDeleteMaintenances,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Build, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Elimina tutte le manutenzioni")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onDeleteLocations,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Place, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Elimina tutte le ubicazioni")
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(16.dp))

            // Reset completo
            Button(
                onClick = onFullReset,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.data_cleanup_all))
            }
        }
    }
}

/**
 * Dialog di conferma eliminazione.
 */
@Composable
private fun ConfirmDeleteDialog(
    operation: CleanupOperation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val (title, message) = when (operation) {
        CleanupOperation.SAMPLE_DATA -> Pair(
            "Elimina dati di esempio",
            stringResource(R.string.data_cleanup_confirm_sample)
        )
        CleanupOperation.ALL_PRODUCTS -> Pair(
            "Elimina tutti i prodotti",
            "Verranno eliminate anche tutte le manutenzioni associate. Continuare?"
        )
        CleanupOperation.ALL_MAINTAINERS -> Pair(
            "Elimina tutti i manutentori",
            "Questa azione non influisce sui prodotti. Continuare?"
        )
        CleanupOperation.ALL_MAINTENANCES -> Pair(
            "Elimina tutte le manutenzioni",
            "Verranno eliminate tutte le registrazioni di interventi. Continuare?"
        )
        CleanupOperation.ALL_LOCATIONS -> Pair(
            "Elimina tutte le ubicazioni",
            "Questa azione non influisce sui prodotti. Continuare?"
        )
        CleanupOperation.FULL_RESET -> Pair(
            "Reset database completo",
            stringResource(R.string.data_cleanup_confirm_all)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Elimina")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
