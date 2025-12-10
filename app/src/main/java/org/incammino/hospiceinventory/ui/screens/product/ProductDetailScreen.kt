package org.incammino.hospiceinventory.ui.screens.product

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import org.incammino.hospiceinventory.domain.model.Maintainer
import org.incammino.hospiceinventory.domain.model.Maintenance
import org.incammino.hospiceinventory.domain.model.Product
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.incammino.hospiceinventory.ui.components.MaintenanceAlertBanner
import org.incammino.hospiceinventory.ui.theme.AlertOk
import org.incammino.hospiceinventory.ui.theme.AlertOverdue
import org.incammino.hospiceinventory.ui.theme.AlertWarning

/**
 * Schermata dettaglio prodotto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    productId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: () -> Unit,
    onNavigateToMaintenance: (String) -> Unit,
    onNavigateToNewMaintenance: () -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.product?.name ?: stringResource(R.string.product_detail)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.action_edit)
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToNewMaintenance,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = stringResource(R.string.maintenance_add)
                )
            }
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error ?: stringResource(R.string.error_generic),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            uiState.product != null -> {
                ProductDetailContent(
                    product = uiState.product!!,
                    warrantyMaintainer = uiState.warrantyMaintainer,
                    serviceMaintainer = uiState.serviceMaintainer,
                    maintenanceHistory = uiState.maintenanceHistory,
                    onMaintenanceClick = onNavigateToMaintenance,
                    onRequestMaintenance = onNavigateToNewMaintenance,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }

    // Dialog conferma eliminazione
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteProduct(onNavigateBack)
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * Contenuto del dettaglio prodotto.
 */
@Composable
private fun ProductDetailContent(
    product: Product,
    warrantyMaintainer: Maintainer?,
    serviceMaintainer: Maintainer?,
    maintenanceHistory: List<Maintenance>,
    onMaintenanceClick: (String) -> Unit,
    onRequestMaintenance: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Alert manutenzione (se in scadenza/scaduta)
        item {
            val daysRemaining = product.maintenanceDaysRemaining()?.toInt()
            if (daysRemaining != null && daysRemaining <= 30) {
                MaintenanceAlertBanner(
                    daysRemaining = daysRemaining,
                    onRequestMaintenance = onRequestMaintenance
                )
            }
        }

        // Info base
        item {
            ProductInfoCard(product = product)
        }

        // Garanzia
        item {
            WarrantyCard(
                product = product,
                warrantyMaintainer = warrantyMaintainer
            )
        }

        // Manutenzione
        item {
            MaintenanceInfoCard(
                product = product,
                serviceMaintainer = serviceMaintainer
            )
        }

        // Storico manutenzioni
        if (maintenanceHistory.isNotEmpty()) {
            item {
                Text(
                    text = "Storico manutenzioni (${maintenanceHistory.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            items(maintenanceHistory.take(5)) { maintenance ->
                MaintenanceHistoryItem(
                    maintenance = maintenance,
                    onClick = { onMaintenanceClick(maintenance.id) }
                )
            }
        }
    }
}

/**
 * Card info base prodotto.
 */
@Composable
private fun ProductInfoCard(product: Product) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Informazioni",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            HorizontalDivider()

            InfoRow(label = "Categoria", value = product.category)
            InfoRow(label = "Ubicazione", value = product.location)
            product.barcode?.let { InfoRow(label = "Barcode", value = it) }
            product.description?.let { InfoRow(label = "Descrizione", value = it) }
            product.supplier?.let { InfoRow(label = "Fornitore", value = it) }
            product.accountType?.let { InfoRow(label = "Tipo proprietà", value = it.label) }
            product.price?.let { InfoRow(label = "Prezzo", value = "€ ${"%.2f".format(it)}") }
        }
    }
}

/**
 * Card garanzia.
 */
@Composable
private fun WarrantyCard(
    product: Product,
    warrantyMaintainer: Maintainer?
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Garanzia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (product.isUnderWarranty()) {
                    Surface(
                        color = AlertOk,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = stringResource(R.string.warranty_status_active),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else if (product.warrantyEndDate != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = stringResource(R.string.warranty_status_expired),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            HorizontalDivider()

            product.warrantyEndDate?.let {
                InfoRow(label = "Scadenza", value = "${it.dayOfMonth}/${it.monthNumber}/${it.year}")
            }

            product.warrantyDaysRemaining()?.let { days ->
                val color = when {
                    days < 0 -> AlertOverdue
                    days <= 30 -> AlertWarning
                    else -> AlertOk
                }
                InfoRow(
                    label = "Giorni rimanenti",
                    value = if (days < 0) "Scaduta da ${-days} giorni" else "$days giorni",
                    valueColor = color
                )
            }

            warrantyMaintainer?.let { maintainer ->
                InfoRow(label = "Manutentore garanzia", value = maintainer.name)
                maintainer.email?.let { InfoRow(label = "Email", value = it) }
                maintainer.phone?.let { InfoRow(label = "Telefono", value = it) }
            }
        }
    }
}

/**
 * Card manutenzione programmata.
 */
@Composable
private fun MaintenanceInfoCard(
    product: Product,
    serviceMaintainer: Maintainer?
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Manutenzione programmata",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            HorizontalDivider()

            product.maintenanceFrequency?.let {
                InfoRow(label = "Frequenza", value = it.label)
            }

            product.lastMaintenanceDate?.let {
                InfoRow(label = "Ultima manutenzione", value = "${it.dayOfMonth}/${it.monthNumber}/${it.year}")
            }

            product.nextMaintenanceDue?.let {
                InfoRow(label = "Prossima scadenza", value = "${it.dayOfMonth}/${it.monthNumber}/${it.year}")
            }

            product.maintenanceDaysRemaining()?.let { days ->
                val color = when {
                    days < 0 -> AlertOverdue
                    days <= 7 -> AlertWarning
                    days <= 30 -> AlertWarning
                    else -> AlertOk
                }
                InfoRow(
                    label = "Giorni alla scadenza",
                    value = if (days < 0) "Scaduta da ${-days} giorni" else "$days giorni",
                    valueColor = color
                )
            }

            serviceMaintainer?.let { maintainer ->
                InfoRow(label = "Manutentore service", value = maintainer.name)
                maintainer.email?.let { InfoRow(label = "Email", value = it) }
                maintainer.phone?.let { InfoRow(label = "Telefono", value = it) }
            }
        }
    }
}

/**
 * Item storico manutenzione.
 */
@Composable
private fun MaintenanceHistoryItem(
    maintenance: Maintenance,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = maintenance.type.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatDate(maintenance.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            maintenance.outcome?.let { outcome ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = outcome.label,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Riga info label-value.
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

/**
 * Formatta data Instant.
 */
private fun formatDate(instant: kotlinx.datetime.Instant): String {
    val tz = TimeZone.currentSystemDefault()
    val localDateTime = instant.toLocalDateTime(tz)
    return "${localDateTime.dayOfMonth}/${localDateTime.monthNumber}/${localDateTime.year}"
}
