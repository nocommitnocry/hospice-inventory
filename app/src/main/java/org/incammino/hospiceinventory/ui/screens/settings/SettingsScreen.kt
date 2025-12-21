package org.incammino.hospiceinventory.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.incammino.hospiceinventory.R

/**
 * Schermata impostazioni.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDataManagement: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sezione Dati
            item {
                SettingsSectionHeader(title = "Gestione Dati")
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.data_management),
                    subtitle = "Pulizia database, import/export",
                    onClick = onNavigateToDataManagement
                )
            }

            // Sezione App (placeholder per future implementazioni)
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionHeader(title = "Applicazione")
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "Notifiche",
                    subtitle = "Configura promemoria scadenze",
                    onClick = { /* TODO */ },
                    enabled = false
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Sync,
                    title = "Sincronizzazione",
                    subtitle = "Configura backup cloud",
                    onClick = { /* TODO */ },
                    enabled = false
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "Assistente Vocale",
                    subtitle = "Voce e lingua",
                    onClick = { /* TODO */ },
                    enabled = false
                )
            }

            // Sezione Info
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionHeader(title = "Informazioni")
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Versione",
                    subtitle = "1.0.0 (sviluppo)",
                    onClick = { },
                    showArrow = false
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * Header di sezione.
 */
@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

/**
 * Singola voce impostazioni.
 */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    showArrow: Boolean = true
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
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
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (showArrow && enabled) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
