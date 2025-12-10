package org.incammino.hospiceinventory.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.incammino.hospiceinventory.R
import org.incammino.hospiceinventory.domain.model.AlertType
import org.incammino.hospiceinventory.ui.theme.*

/**
 * Banner di alert per notifiche importanti (manutenzioni scadute, etc.).
 */
@Composable
fun AlertBanner(
    alertType: AlertType,
    message: String,
    onClick: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, contentColor, icon) = getAlertColors(alertType)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )

            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Banner compatto per conteggio alert nella home.
 */
@Composable
fun AlertCountBanner(
    overdueCount: Int,
    upcomingCount: Int,
    onClickOverdue: () -> Unit,
    onClickUpcoming: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Alert scadute (rosse)
        AnimatedVisibility(
            visible = overdueCount > 0,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            AlertBanner(
                alertType = AlertType.OVERDUE,
                message = stringResource(R.string.alert_count_overdue, overdueCount),
                onClick = onClickOverdue,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Alert in scadenza (arancioni)
        AnimatedVisibility(
            visible = upcomingCount > 0,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            AlertBanner(
                alertType = AlertType.ADVANCE_7,
                message = stringResource(R.string.alert_count_upcoming, upcomingCount),
                onClick = onClickUpcoming
            )
        }
    }
}

/**
 * Banner singolo per dettaglio prodotto.
 */
@Composable
fun MaintenanceAlertBanner(
    daysRemaining: Int?,
    onRequestMaintenance: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (daysRemaining == null) return

    val alertType = AlertType.fromDaysRemaining(daysRemaining.toLong())

    val message = when {
        daysRemaining < 0 -> stringResource(R.string.alert_overdue, -daysRemaining)
        daysRemaining == 0 -> stringResource(R.string.alert_due_today)
        else -> stringResource(R.string.alert_due_soon, daysRemaining)
    }

    AlertBanner(
        alertType = alertType,
        message = message,
        onClick = onRequestMaintenance,
        modifier = modifier
    )
}

/**
 * Ritorna colori e icona in base al tipo di alert.
 */
@Composable
private fun getAlertColors(alertType: AlertType): Triple<Color, Color, ImageVector> {
    return when (alertType) {
        AlertType.OVERDUE -> Triple(
            AlertOverdue,
            Color.White,
            Icons.Default.Warning
        )
        AlertType.DUE_TODAY -> Triple(
            AlertWarning,
            Color.White,
            Icons.Default.Warning
        )
        AlertType.ADVANCE_7 -> Triple(
            AlertWarning.copy(alpha = 0.8f),
            Color.White,
            Icons.Default.Warning
        )
        AlertType.ADVANCE_30 -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Default.Warning
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AlertBannerOverduePreview() {
    HospiceInventoryTheme {
        AlertBanner(
            alertType = AlertType.OVERDUE,
            message = "3 manutenzioni SCADUTE",
            onClick = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AlertBannerWarningPreview() {
    HospiceInventoryTheme {
        AlertBanner(
            alertType = AlertType.ADVANCE_7,
            message = "5 in scadenza questa settimana",
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AlertCountBannerPreview() {
    HospiceInventoryTheme {
        AlertCountBanner(
            overdueCount = 3,
            upcomingCount = 5,
            onClickOverdue = {},
            onClickUpcoming = {}
        )
    }
}
