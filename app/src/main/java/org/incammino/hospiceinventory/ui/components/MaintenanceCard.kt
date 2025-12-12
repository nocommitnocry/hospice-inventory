package org.incammino.hospiceinventory.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.incammino.hospiceinventory.R
import org.incammino.hospiceinventory.domain.model.*
import org.incammino.hospiceinventory.ui.theme.AlertOk
import org.incammino.hospiceinventory.ui.theme.AlertOverdue
import org.incammino.hospiceinventory.ui.theme.AlertWarning
import org.incammino.hospiceinventory.ui.theme.HospiceInventoryTheme

/**
 * Card per visualizzare una manutenzione nella lista.
 */
@Composable
fun MaintenanceCard(
    maintenance: Maintenance,
    productName: String,
    maintainerName: String?,
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
            // Header: Prodotto + Tipo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = productName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                MaintenanceTypeBadge(type = maintenance.type)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Data
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatInstant(maintenance.date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Manutentore (se presente)
            maintainerName?.let { name ->
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Esito (se presente)
            maintenance.outcome?.let { outcome ->
                Spacer(modifier = Modifier.height(8.dp))
                MaintenanceOutcomeBadge(outcome = outcome)
            }

            // Note (se presenti)
            maintenance.notes?.let { notes ->
                if (notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Garanzia badge
            if (maintenance.isWarrantyWork) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = stringResource(R.string.warranty_status_active),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Card per manutenzione in scadenza (lista scadenze).
 */
@Composable
fun MaintenanceDueCard(
    productName: String,
    productCategory: String,
    productLocation: String,
    dueDate: LocalDate,
    daysRemaining: Int,
    onProductClick: () -> Unit,
    onRequestMaintenance: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, contentColor) = when {
        daysRemaining < 0 -> AlertOverdue to MaterialTheme.colorScheme.onError
        daysRemaining <= 7 -> AlertWarning to MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onProductClick),
        colors = CardDefaults.cardColors(
            containerColor = if (daysRemaining <= 7) backgroundColor.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header con urgenza
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = productName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Badge giorni rimanenti
                Surface(
                    color = backgroundColor,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = when {
                            daysRemaining < 0 -> stringResource(R.string.alert_overdue, -daysRemaining)
                            daysRemaining == 0 -> stringResource(R.string.alert_due_today)
                            else -> stringResource(R.string.alert_due_soon, daysRemaining)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (daysRemaining <= 7) MaterialTheme.colorScheme.onError
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Info prodotto
            Text(
                text = "$productCategory - $productLocation",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Data scadenza
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Event,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Scadenza: ${formatLocalDate(dueDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pulsante richiedi intervento
            Button(
                onClick = onRequestMaintenance,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (daysRemaining < 0)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.email_send_request))
            }
        }
    }
}

/**
 * Badge per tipo di manutenzione.
 */
@Composable
private fun MaintenanceTypeBadge(type: MaintenanceType) {
    val icon = when (type) {
        MaintenanceType.PROGRAMMATA -> Icons.Default.Schedule
        MaintenanceType.STRAORDINARIA -> Icons.Default.Warning
        MaintenanceType.VERIFICA -> Icons.Default.Checklist
        MaintenanceType.INSTALLAZIONE -> Icons.Default.Add
        MaintenanceType.DISMISSIONE -> Icons.Default.Delete
        MaintenanceType.RIPARAZIONE -> Icons.Default.Build
        MaintenanceType.SOSTITUZIONE -> Icons.Default.SwapHoriz
        MaintenanceType.COLLAUDO -> Icons.AutoMirrored.Filled.FactCheck
    }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = type.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * Badge per esito manutenzione.
 */
@Composable
private fun MaintenanceOutcomeBadge(outcome: MaintenanceOutcome) {
    val (color, textColor) = when (outcome) {
        MaintenanceOutcome.RIPRISTINATO -> AlertOk to MaterialTheme.colorScheme.onPrimary
        MaintenanceOutcome.PARZIALE -> AlertWarning to MaterialTheme.colorScheme.onError
        MaintenanceOutcome.GUASTO, MaintenanceOutcome.DISMESSO ->
            AlertOverdue to MaterialTheme.colorScheme.onError
        MaintenanceOutcome.IN_ATTESA_RICAMBI, MaintenanceOutcome.IN_ATTESA_TECNICO ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        MaintenanceOutcome.SOSTITUITO, MaintenanceOutcome.NON_NECESSARIO ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = color,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = outcome.label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Formatta un Instant in stringa data leggibile.
 */
private fun formatInstant(instant: Instant): String {
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localDateTime.dayOfMonth}/${localDateTime.monthNumber}/${localDateTime.year}"
}

/**
 * Formatta una LocalDate in stringa leggibile.
 */
private fun formatLocalDate(date: LocalDate): String {
    return "${date.dayOfMonth}/${date.monthNumber}/${date.year}"
}

@Preview(showBackground = true)
@Composable
private fun MaintenanceCardPreview() {
    HospiceInventoryTheme {
        MaintenanceCard(
            maintenance = Maintenance(
                id = "1",
                productId = "prod1",
                maintainerId = "maint1",
                date = Instant.parse("2024-11-15T10:00:00Z"),
                type = MaintenanceType.PROGRAMMATA,
                outcome = MaintenanceOutcome.RIPRISTINATO,
                notes = "Sostituzione filtro e controllo generale",
                cost = null,
                invoiceNumber = null,
                isWarrantyWork = false,
                requestEmailSent = false,
                reportEmailSent = false
            ),
            productName = "Letto elettrico XYZ-2000",
            maintainerName = "Tecnoservice Srl",
            onClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MaintenanceDueCardOverduePreview() {
    HospiceInventoryTheme {
        MaintenanceDueCard(
            productName = "Monitor multiparametrico",
            productCategory = "Elettromedicali",
            productLocation = "Piano 2 - Stanza 205",
            dueDate = LocalDate(2024, 11, 1),
            daysRemaining = -15,
            onProductClick = {},
            onRequestMaintenance = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MaintenanceDueCardSoonPreview() {
    HospiceInventoryTheme {
        MaintenanceDueCard(
            productName = "Carrozzina pieghevole",
            productCategory = "Mobilità",
            productLocation = "Magazzino",
            dueDate = LocalDate(2024, 12, 20),
            daysRemaining = 5,
            onProductClick = {},
            onRequestMaintenance = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
