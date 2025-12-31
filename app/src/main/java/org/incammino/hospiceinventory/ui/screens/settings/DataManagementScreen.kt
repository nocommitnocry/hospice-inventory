package org.incammino.hospiceinventory.ui.screens.settings

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import org.incammino.hospiceinventory.R
import org.incammino.hospiceinventory.service.backup.BackupInfo

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
    val backupState by viewModel.backupState.collectAsState()

    // Activity Result per Google Sign-In
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                viewModel.onSignInResult(account)
            } catch (e: ApiException) {
                Log.e("DataManagement", "Sign-in failed: ${e.statusCode}", e)
            }
        }
    }

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
    LaunchedEffect(backupState.lastBackupResult) {
        backupState.lastBackupResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearBackupResult()
        }
    }

    // Dialog di conferma delete
    if (uiState.showConfirmDialog && uiState.pendingOperation != null) {
        ConfirmDeleteDialog(
            operation = uiState.pendingOperation!!,
            onConfirm = viewModel::confirmOperation,
            onDismiss = viewModel::cancelOperation
        )
    }

    // Dialog di conferma restore
    if (backupState.showRestoreConfirmDialog && backupState.pendingRestoreBackup != null) {
        ConfirmRestoreDialog(
            backupName = backupState.pendingRestoreBackup!!.name,
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::cancelRestore
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sezione Google Drive Backup
                item {
                    GoogleDriveSection(
                        backupState = backupState,
                        onSignIn = { launcher.launch(viewModel.getSignInIntent()) },
                        onSignOut = viewModel::signOut,
                        onBackup = viewModel::performBackup,
                        onExport = viewModel::exportToExcel,
                        onRestore = { backup -> viewModel.requestRestoreConfirmation(backup) }
                    )
                }

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

/**
 * Dialog di conferma ripristino backup.
 */
@Composable
private fun ConfirmRestoreDialog(
    backupName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Ripristina backup") },
        text = {
            Text(
                "Ripristinare il backup \"$backupName\"?\n\n" +
                "ATTENZIONE: Tutti i dati attuali verranno sovrascritti. " +
                "L'app dovra' essere riavviata dopo il ripristino."
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Ripristina")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * Sezione Google Drive Backup.
 */
@Composable
private fun GoogleDriveSection(
    backupState: BackupUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onBackup: () -> Unit,
    onExport: () -> Unit,
    onRestore: (BackupInfo) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Google Drive Backup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!backupState.isSignedIn) {
                // Non connesso
                Text(
                    text = "Connetti il tuo account Google per abilitare backup automatici e export Excel su Drive",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Login, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Connetti Google Drive")
                }
            } else {
                // Connesso
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = backupState.accountEmail ?: "Account connesso",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onSignOut) {
                        Text("Disconnetti")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Azioni
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onBackup,
                        enabled = !backupState.isOperationInProgress,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Backup,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Backup")
                    }

                    OutlinedButton(
                        onClick = onExport,
                        enabled = !backupState.isOperationInProgress,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.TableChart,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Excel")
                    }
                }

                // Progress indicator
                if (backupState.isOperationInProgress) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }

                // Lista backup recenti
                if (backupState.backups.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Backup recenti",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    backupState.backups.take(3).forEach { backup ->
                        BackupItem(
                            backup = backup,
                            onRestore = { onRestore(backup) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Elemento backup nella lista.
 */
@Composable
private fun BackupItem(
    backup: BackupInfo,
    onRestore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = backup.name,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = formatFileSize(backup.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRestore) {
            Icon(
                Icons.Default.Restore,
                contentDescription = "Ripristina",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Formatta la dimensione del file.
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    }
}
