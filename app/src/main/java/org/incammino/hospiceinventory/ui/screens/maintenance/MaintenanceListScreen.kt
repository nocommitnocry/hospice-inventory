package org.incammino.hospiceinventory.ui.screens.maintenance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import org.incammino.hospiceinventory.domain.model.Product
import org.incammino.hospiceinventory.ui.theme.AlertOk
import org.incammino.hospiceinventory.ui.theme.AlertOverdue
import org.incammino.hospiceinventory.ui.theme.AlertWarning

/**
 * Schermata lista manutenzioni in scadenza.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProduct: (String) -> Unit,
    onNavigateToMaintenance: (String) -> Unit,
    initialFilter: String? = null,
    viewModel: MaintenanceListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Imposta il filtro iniziale se specificato
    LaunchedEffect(initialFilter) {
        initialFilter?.let { filterName ->
            MaintenanceFilter.entries.find { it.name.equals(filterName, ignoreCase = true) }
                ?.let { viewModel.setFilter(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.maintenance_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Summary cards
            MaintenanceSummary(
                overdueCount = uiState.overdueCount,
                weekCount = uiState.weekCount,
                monthCount = uiState.monthCount,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Filtri
            FilterChips(
                selectedFilter = uiState.filter,
                onFilterSelect = viewModel::setFilter,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Lista
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.items.isEmpty() -> {
                    EmptyState(
                        filter = uiState.filter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    MaintenanceList(
                        items = uiState.items,
                        onProductClick = onNavigateToProduct,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Riepilogo manutenzioni.
 */
@Composable
private fun MaintenanceSummary(
    overdueCount: Int,
    weekCount: Int,
    monthCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryCard(
            count = overdueCount,
            label = "Scadute",
            color = AlertOverdue,
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            count = weekCount,
            label = "Settimana",
            color = AlertWarning,
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            count = monthCount,
            label = "Mese",
            color = AlertOk,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Card riepilogo singola.
 */
@Composable
private fun SummaryCard(
    count: Int,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Chip per i filtri.
 */
@Composable
private fun FilterChips(
    selectedFilter: MaintenanceFilter,
    onFilterSelect: (MaintenanceFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(MaintenanceFilter.entries) { filter ->
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onFilterSelect(filter) },
                label = { Text(filter.label) },
                leadingIcon = if (filter == selectedFilter) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null
            )
        }
    }
}

/**
 * Lista delle manutenzioni.
 */
@Composable
private fun MaintenanceList(
    items: List<MaintenanceAlertItem>,
    onProductClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = items,
            key = { it.product.id }
        ) { item ->
            MaintenanceAlertCard(
                item = item,
                onClick = { onProductClick(item.product.id) }
            )
        }
    }
}

/**
 * Card per singola manutenzione in scadenza.
 */
@Composable
private fun MaintenanceAlertCard(
    item: MaintenanceAlertItem,
    onClick: () -> Unit
) {
    val statusColor = when {
        item.isOverdue -> AlertOverdue
        item.isUrgent -> AlertWarning
        else -> AlertOk
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicatore stato
            Surface(
                shape = MaterialTheme.shapes.small,
                color = statusColor.copy(alpha = 0.2f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            item.isOverdue -> Icons.Default.Warning
                            item.isUrgent -> Icons.Default.Schedule
                            else -> Icons.Default.Build
                        },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Info prodotto
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = item.product.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item.product.maintenanceFrequency?.let { freq ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = freq.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Badge giorni
            DaysBadge(
                daysRemaining = item.daysRemaining,
                isOverdue = item.isOverdue,
                color = statusColor
            )
        }
    }
}

/**
 * Badge con i giorni rimanenti/scaduti.
 */
@Composable
private fun DaysBadge(
    daysRemaining: Int,
    isOverdue: Boolean,
    color: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = color
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isOverdue) "${-daysRemaining}" else "$daysRemaining",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = if (isOverdue) "scaduta" else "giorni",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Stato vuoto.
 */
@Composable
private fun EmptyState(
    filter: MaintenanceFilter,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = AlertOk
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = when (filter) {
                MaintenanceFilter.ALL -> "Nessuna manutenzione in programma"
                MaintenanceFilter.OVERDUE -> "Nessuna manutenzione scaduta"
                MaintenanceFilter.THIS_WEEK -> "Nessuna manutenzione questa settimana"
                MaintenanceFilter.THIS_MONTH -> "Nessuna manutenzione questo mese"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tutto sotto controllo!",
            style = MaterialTheme.typography.bodyMedium,
            color = AlertOk
        )
    }
}
