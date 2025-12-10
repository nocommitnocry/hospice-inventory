package org.incammino.hospiceinventory.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.incammino.hospiceinventory.R
import org.incammino.hospiceinventory.ui.theme.HospiceInventoryTheme

/**
 * Barra di stato che mostra connettività e stato sync.
 * Da posizionare in alto o in basso nella schermata.
 */
@Composable
fun StatusBar(
    isOnline: Boolean,
    pendingSyncCount: Int,
    onSyncClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val showBar = !isOnline || pendingSyncCount > 0

    AnimatedVisibility(
        visible = showBar,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = if (isOnline)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.errorContainer,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Stato connettività
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isOnline) Icons.Default.Cloud else Icons.Default.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isOnline)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isOnline)
                            stringResource(R.string.sync_online)
                        else
                            stringResource(R.string.sync_offline),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOnline)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                // Stato sync
                if (pendingSyncCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.sync_pending, pendingSyncCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOnline)
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )

                        if (isOnline && onSyncClick != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = onSyncClick,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Sincronizza",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Indicatore di stato compatto per TopAppBar.
 */
@Composable
fun StatusIndicator(
    isOnline: Boolean,
    pendingSyncCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Pallino connettività
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (isOnline)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error,
                    shape = MaterialTheme.shapes.small
                )
        )

        // Badge sync pending
        if (pendingSyncCount > 0) {
            Icon(
                imageVector = if (isOnline) Icons.Default.Sync else Icons.Default.SyncProblem,
                contentDescription = stringResource(R.string.sync_pending, pendingSyncCount),
                modifier = Modifier.size(16.dp),
                tint = if (isOnline)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Badge per numero di elementi in attesa di sync.
 */
@Composable
fun SyncBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    if (count <= 0) return

    Badge(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusBarOnlineWithPendingPreview() {
    HospiceInventoryTheme {
        StatusBar(
            isOnline = true,
            pendingSyncCount = 5,
            onSyncClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusBarOfflinePreview() {
    HospiceInventoryTheme {
        StatusBar(
            isOnline = false,
            pendingSyncCount = 12
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusBarOnlineSyncedPreview() {
    HospiceInventoryTheme {
        // Questo non mostrerà nulla perché isOnline=true e pendingSyncCount=0
        StatusBar(
            isOnline = true,
            pendingSyncCount = 0
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusIndicatorPreview() {
    HospiceInventoryTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            StatusIndicator(isOnline = true, pendingSyncCount = 0)
            StatusIndicator(isOnline = true, pendingSyncCount = 3)
            StatusIndicator(isOnline = false, pendingSyncCount = 5)
        }
    }
}
