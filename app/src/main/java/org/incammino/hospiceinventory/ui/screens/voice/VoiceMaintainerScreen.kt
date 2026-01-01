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
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.incammino.hospiceinventory.service.voice.MaintainerConfirmData
import org.incammino.hospiceinventory.service.voice.VoiceMaintainerState
import org.incammino.hospiceinventory.ui.components.MicrophonePermissionState
import org.incammino.hospiceinventory.ui.components.rememberMicrophonePermission

/**
 * Screen per input vocale nuovo manutentore/fornitore.
 * Paradigma "Voice Dump + Visual Confirm" (Fase 3 - 28/12/2025)
 *
 * L'utente:
 * 1. Tocca il pulsante microfono
 * 2. Parla TUTTO: nome azienda, contatti, indirizzo, specializzazione, ecc.
 * 3. Al termine, Gemini estrae i dati
 * 4. Si naviga alla schermata di conferma
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceMaintainerScreen(
    viewModel: VoiceMaintainerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToConfirm: (MaintainerConfirmData) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Gestione permesso microfono
    val micPermission = rememberMicrophonePermission(
        onGranted = {
            viewModel.toggleListening()
        }
    )

    // Mostra snackbar se permesso negato
    LaunchedEffect(micPermission.state) {
        if (micPermission.state == MicrophonePermissionState.Denied) {
            snackbarHostState.showSnackbar("Permesso microfono necessario per la registrazione vocale")
        }
    }

    // Naviga automaticamente quando estrazione completata
    LaunchedEffect(state) {
        if (state is VoiceMaintainerState.Extracted) {
            val data = (state as VoiceMaintainerState.Extracted).data
            // Reset PRIMA di navigare per evitare loop
            viewModel.reset()
            onNavigateToConfirm(data)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuovo Manutentore") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    text = "Mi descriva il manutentore:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "nome azienda, telefono, email, indirizzo e specializzazione",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Pulsante microfono
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MaintainerMicrophoneButton(
                    state = state,
                    isPermissionGranted = micPermission.isGranted,
                    onTap = {
                        if (micPermission.isGranted) {
                            viewModel.toggleListening()
                        } else {
                            micPermission.requestPermission()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Label stato
                Text(
                    text = getMaintainerStateLabel(state),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Box transcript
            MaintainerTranscriptBox(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            )
        }
    }
}

@Composable
private fun MaintainerMicrophoneButton(
    state: VoiceMaintainerState,
    isPermissionGranted: Boolean,
    onTap: () -> Unit
) {
    val isActive = state is VoiceMaintainerState.Listening ||
                   state is VoiceMaintainerState.Transcribing

    val isProcessing = state is VoiceMaintainerState.Processing

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

    // Colori basati su stato e permessi (TAP-TO-STOP pattern: rosso durante ascolto)
    val containerColor = when {
        !isPermissionGranted -> MaterialTheme.colorScheme.surfaceVariant
        isActive -> Color.Red  // ROSSO = STOP
        isProcessing -> MaterialTheme.colorScheme.tertiary
        state is VoiceMaintainerState.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val contentColor = when {
        !isPermissionGranted -> MaterialTheme.colorScheme.onSurfaceVariant
        isActive -> Color.White
        isProcessing -> MaterialTheme.colorScheme.onTertiary
        state is VoiceMaintainerState.Error -> MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.onPrimary
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
            !isPermissionGranted -> {
                Icon(
                    Icons.Default.MicOff,
                    contentDescription = "Permesso richiesto",
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
private fun MaintainerTranscriptBox(
    state: VoiceMaintainerState,
    modifier: Modifier = Modifier
) {
    val text = when (state) {
        is VoiceMaintainerState.Transcribing -> state.partialText
        is VoiceMaintainerState.Error -> state.message
        else -> ""
    }

    val isError = state is VoiceMaintainerState.Error

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

private fun getMaintainerStateLabel(state: VoiceMaintainerState): String {
    return when (state) {
        is VoiceMaintainerState.Idle -> "Tocca e parla"
        is VoiceMaintainerState.Listening -> "Sto ascoltando..."
        is VoiceMaintainerState.Transcribing -> "Sto ascoltando..."
        is VoiceMaintainerState.Processing -> "Elaborazione in corso..."
        is VoiceMaintainerState.Extracted -> "Completato!"
        is VoiceMaintainerState.Error -> "Tocca per riprovare"
    }
}
