package org.incammino.hospiceinventory.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.incammino.hospiceinventory.ui.theme.*

/**
 * Schermata principale dell'app.
 * Presenta il pulsante vocale grande e le azioni rapide.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToSearch: (String) -> Unit,
    onNavigateToProduct: (String) -> Unit,
    onNavigateToNewProduct: () -> Unit,
    onNavigateToMaintenances: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToScanner: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Logo placeholder (sostituire con Image quando disponibile)
                        Text(
                            text = "🦋",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hospice Inventory",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Impostazioni"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Alert Banner (se ci sono scadenze)
            if (uiState.overdueCount > 0 || uiState.upcomingCount > 0) {
                AlertBanner(
                    overdueCount = uiState.overdueCount,
                    upcomingCount = uiState.upcomingCount,
                    onClick = onNavigateToMaintenances
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Pulsante Vocale Grande
            Spacer(modifier = Modifier.weight(0.3f))
            
            VoiceButton(
                isListening = uiState.isListening,
                onClick = { viewModel.toggleVoice() }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Testo trascrizione
            Text(
                text = if (uiState.isListening) {
                    uiState.transcription.ifEmpty { "Sto ascoltando..." }
                } else {
                    "Tocca per parlare"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            
            Spacer(modifier = Modifier.weight(0.3f))
            
            // Azioni Rapide
            QuickActionsGrid(
                onScanClick = onNavigateToScanner,
                onSearchClick = { onNavigateToSearch("") },
                onNewClick = onNavigateToNewProduct,
                onMaintenancesClick = onNavigateToMaintenances
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Status Bar
            StatusBar(
                isOnline = uiState.isOnline,
                pendingSync = uiState.pendingSyncCount
            )
        }
    }
}

/**
 * Banner per alert manutenzioni.
 */
@Composable
private fun AlertBanner(
    overdueCount: Int,
    upcomingCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (overdueCount > 0) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                Color(0xFFFFF3E0) // Arancione chiaro
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
                imageVector = if (overdueCount > 0) Icons.Filled.Warning else Icons.Filled.Schedule,
                contentDescription = null,
                tint = if (overdueCount > 0) AlertOverdue else WarningOrange
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (overdueCount > 0) {
                    Text(
                        text = "$overdueCount manutenzioni SCADUTE",
                        style = MaterialTheme.typography.titleSmall,
                        color = AlertOverdue,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (upcomingCount > 0) {
                    Text(
                        text = "$upcomingCount in scadenza questa settimana",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningOrange
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Vai alle scadenze"
            )
        }
    }
}

/**
 * Pulsante vocale animato.
 */
@Composable
private fun VoiceButton(
    isListening: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isListening) 1.1f else 1f,
        animationSpec = if (isListening) {
            infiniteRepeatable(
                animation = tween(600, easing = EaseInOutQuad),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(300)
        },
        label = "scale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isListening) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(300),
        label = "backgroundColor"
    )
    
    val iconColor by animateColorAsState(
        targetValue = if (isListening) {
            Color.White
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(300),
        label = "iconColor"
    )
    
    Box(
        modifier = Modifier
            .size(160.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isListening) Icons.Filled.Mic else Icons.Outlined.Mic,
            contentDescription = if (isListening) "Fermati" else "Inizia ascolto",
            modifier = Modifier.size(72.dp),
            tint = iconColor
        )
    }
}

/**
 * Griglia azioni rapide.
 */
@Composable
private fun QuickActionsGrid(
    onScanClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNewClick: () -> Unit,
    onMaintenancesClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionButton(
            icon = Icons.Outlined.QrCodeScanner,
            label = "Scansiona",
            onClick = onScanClick,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Outlined.Search,
            label = "Cerca",
            onClick = onSearchClick,
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionButton(
            icon = Icons.Outlined.Add,
            label = "Nuovo",
            onClick = onNewClick,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Outlined.Checklist,
            label = "Scadenze",
            onClick = onMaintenancesClick,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Singolo pulsante azione rapida.
 */
@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(80.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Barra di stato (online/offline, sync).
 */
@Composable
private fun StatusBar(
    isOnline: Boolean,
    pendingSync: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) SuccessGreen else ErrorRed)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isOnline) "Online" else "Offline",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (pendingSync > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$pendingSync da sincronizzare",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (isOnline) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CloudDone,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = SuccessGreen
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Sincronizzato",
                    style = MaterialTheme.typography.labelSmall,
                    color = SuccessGreen
                )
            }
        }
    }
}
