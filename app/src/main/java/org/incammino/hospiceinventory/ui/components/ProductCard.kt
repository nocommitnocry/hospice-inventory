package org.incammino.hospiceinventory.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import org.incammino.hospiceinventory.R
import org.incammino.hospiceinventory.domain.model.Product
import org.incammino.hospiceinventory.ui.theme.HospiceInventoryTheme

/**
 * Card per visualizzare un prodotto nella lista.
 * Mostra: nome, categoria, ubicazione, stato garanzia/manutenzione.
 */
@Composable
fun ProductCard(
    product: Product,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Nome + Badge stato
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Badge stato manutenzione
                MaintenanceStatusBadge(product = product)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Categoria
            Text(
                text = product.category,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Info row: ubicazione + garanzia
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Ubicazione
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = product.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Stato garanzia
                WarrantyStatusChip(product = product)
            }

            // Barcode (se presente)
            product.barcode?.let { barcode ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "BC: $barcode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/**
 * Badge che mostra lo stato della manutenzione (scaduta, in scadenza, ok).
 */
@Composable
private fun MaintenanceStatusBadge(product: Product) {
    val daysRemaining = product.maintenanceDaysRemaining()

    when {
        daysRemaining == null -> {
            // Nessuna manutenzione programmata
        }
        daysRemaining < 0L -> {
            // Scaduta
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.alert_overdue, (-daysRemaining).toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        daysRemaining <= 7L -> {
            // In scadenza urgente
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (daysRemaining == 0L)
                            stringResource(R.string.alert_due_today)
                        else
                            stringResource(R.string.alert_due_soon, daysRemaining.toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
        daysRemaining <= 30L -> {
            // In scadenza (non urgente)
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = stringResource(R.string.alert_due_soon, daysRemaining.toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Chip che mostra lo stato della garanzia.
 */
@Composable
private fun WarrantyStatusChip(product: Product) {
    if (product.isUnderWarranty()) {
        val daysRemaining = product.warrantyDaysRemaining() ?: return

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = stringResource(R.string.warranty_days_remaining, daysRemaining),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProductCardPreview() {
    HospiceInventoryTheme {
        ProductCard(
            product = Product(
                id = "1",
                barcode = "123456789",
                name = "Letto elettrico XYZ-2000",
                description = "Letto ospedaliero elettrico con 3 motori",
                category = "Letti",
                location = "Piano 1 - Stanza 101",
                assigneeId = null,
                warrantyMaintainerId = null,
                warrantyStartDate = null,
                warrantyEndDate = null,
                serviceMaintainerId = null,
                maintenanceFrequency = null,
                maintenanceStartDate = null,
                maintenanceIntervalDays = null,
                lastMaintenanceDate = null,
                nextMaintenanceDue = LocalDate(2024, 12, 15),
                purchaseDate = null,
                price = null,
                accountType = null,
                supplier = null,
                invoiceNumber = null,
                imageUri = null,
                notes = null,
                isActive = true
            ),
            onClick = {}
        )
    }
}
