package org.incammino.hospiceinventory.ui.screens.voice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.incammino.hospiceinventory.service.voice.MaintenanceConfirmData
import org.incammino.hospiceinventory.service.voice.VoiceMaintenanceState

/**
 * Screen per input vocale manutenzione.
 * Paradigma "Voice Dump + Visual Confirm" (v2.0 - 26/12/2025)
 *
 * L'utente:
 * 1. Tocca il pulsante microfono
 * 2. Parla TUTTO: chi ha fatto cosa, su quale apparecchio, quanto tempo, ecc.
 * 3. Al termine, Gemini estrae i dati
 * 4. Si naviga alla schermata di conferma
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceMaintenanceScreen(
    viewModel: VoiceMaintenanceViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToConfirm: (MaintenanceConfirmData) -> Unit
) {
    val state by viewModel.state.collectAsState()

    // Naviga automaticamente quando estrazione completata
    LaunchedEffect(state) {
        if (state is VoiceMaintenanceState.Extracted) {
            val data = (state as VoiceMaintenanceState.Extracted).data
            // Reset PRIMA di navigare per evitare loop
            viewModel.reset()
            onNavigateToConfirm(data)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registra Manutenzione") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Prompt guida
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 32.dp)
            ) {
                Text(
                    text = "Mi dica tutto in una volta:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "nome e ditta, su quale apparecchio o impianto, cosa ha fatto, e quanto tempo ha impiegato",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Pulsante microfono
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MicrophoneButton(
                    state = state,
                    onTap = { viewModel.toggleListening() }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Label stato
                Text(
                    text = getStateLabel(state),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Box transcript
            TranscriptBox(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            )
        }
    }
}

@Composable
private fun MicrophoneButton(
    state: VoiceMaintenanceState,
    onTap: () -> Unit
) {
    val isActive = state is VoiceMaintenanceState.Listening ||
                   state is VoiceMaintenanceState.Transcribing

    val isProcessing = state is VoiceMaintenanceState.Processing

    // Animazione pulse quando attivo
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val containerColor = when {
        isActive -> MaterialTheme.colorScheme.primary
        isProcessing -> MaterialTheme.colorScheme.tertiary
        state is VoiceMaintenanceState.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    val contentColor = when {
        isActive -> MaterialTheme.colorScheme.onPrimary
        isProcessing -> MaterialTheme.colorScheme.onTertiary
        state is VoiceMaintenanceState.Error -> MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    FilledIconButton(
        onClick = onTap,
        modifier = Modifier
            .size(120.dp)
            .scale(if (isActive) scale else 1f),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        enabled = !isProcessing
    ) {
        when {
            isProcessing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = contentColor,
                    strokeWidth = 4.dp
                )
            }
            isActive -> {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = "Ferma ascolto",
                    modifier = Modifier.size(48.dp)
                )
            }
            else -> {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Avvia ascolto",
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

@Composable
private fun TranscriptBox(
    state: VoiceMaintenanceState,
    modifier: Modifier = Modifier
) {
    val text = when (state) {
        is VoiceMaintenanceState.Transcribing -> state.partialText
        is VoiceMaintenanceState.Error -> state.message
        else -> ""
    }

    val isError = state is VoiceMaintenanceState.Error

    if (text.isNotEmpty()) {
        Surface(
            modifier = modifier.padding(top = 24.dp),
            shape = RoundedCornerShape(12.dp),
            color = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

private fun getStateLabel(state: VoiceMaintenanceState): String {
    return when (state) {
        is VoiceMaintenanceState.Idle -> "Tocca e parla"
        is VoiceMaintenanceState.Listening -> "Sto ascoltando..."
        is VoiceMaintenanceState.Transcribing -> "Sto ascoltando..."
        is VoiceMaintenanceState.Processing -> "Elaborazione in corso..."
        is VoiceMaintenanceState.Extracted -> "Completato!"
        is VoiceMaintenanceState.Error -> "Tocca per riprovare"
    }
}
