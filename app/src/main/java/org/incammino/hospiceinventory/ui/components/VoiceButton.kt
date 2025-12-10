package org.incammino.hospiceinventory.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.incammino.hospiceinventory.R
import org.incammino.hospiceinventory.ui.theme.HospiceInventoryTheme

/**
 * Stati del pulsante vocale.
 */
enum class VoiceButtonState {
    IDLE,       // In attesa
    LISTENING,  // Sta ascoltando
    PROCESSING  // Sta elaborando
}

/**
 * Pulsante vocale grande con animazione pulse quando attivo.
 * Pensato per essere il focus principale della HomeScreen.
 */
@Composable
fun VoiceButton(
    state: VoiceButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Animazione pulse quando sta ascoltando
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Colori in base allo stato
    val backgroundColor = when (state) {
        VoiceButtonState.IDLE -> MaterialTheme.colorScheme.primary
        VoiceButtonState.LISTENING -> MaterialTheme.colorScheme.error
        VoiceButtonState.PROCESSING -> MaterialTheme.colorScheme.tertiary
    }

    val contentColor = when (state) {
        VoiceButtonState.IDLE -> MaterialTheme.colorScheme.onPrimary
        VoiceButtonState.LISTENING -> MaterialTheme.colorScheme.onError
        VoiceButtonState.PROCESSING -> MaterialTheme.colorScheme.onTertiary
    }

    val icon = when (state) {
        VoiceButtonState.IDLE -> Icons.Default.Mic
        VoiceButtonState.LISTENING -> Icons.Default.Stop
        VoiceButtonState.PROCESSING -> Icons.Default.Mic
    }

    val contentDescription = when (state) {
        VoiceButtonState.IDLE -> stringResource(R.string.home_tap_to_speak)
        VoiceButtonState.LISTENING -> stringResource(R.string.home_listening)
        VoiceButtonState.PROCESSING -> stringResource(R.string.home_listening)
    }

    Box(
        modifier = modifier.size(size + 24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Cerchio pulse esterno (solo quando listening)
        if (state == VoiceButtonState.LISTENING) {
            Box(
                modifier = Modifier
                    .size(size + 16.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(backgroundColor.copy(alpha = pulseAlpha))
            )
        }

        // Cerchio principale
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(backgroundColor)
                .border(
                    width = 3.dp,
                    color = backgroundColor.copy(alpha = 0.5f),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, radius = size / 2),
                    onClick = onClick,
                    enabled = state != VoiceButtonState.PROCESSING
                )
                .semantics { role = Role.Button },
            contentAlignment = Alignment.Center
        ) {
            if (state == VoiceButtonState.PROCESSING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(size * 0.4f),
                    color = contentColor,
                    strokeWidth = 3.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(size * 0.45f),
                    tint = contentColor
                )
            }
        }
    }
}

/**
 * Versione compatta del pulsante vocale per uso in altre schermate.
 */
@Composable
fun VoiceButtonCompact(
    state: VoiceButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    VoiceButton(
        state = state,
        onClick = onClick,
        modifier = modifier,
        size = 56.dp
    )
}

/**
 * Testo sotto il pulsante vocale che indica lo stato.
 */
@Composable
fun VoiceButtonLabel(
    state: VoiceButtonState,
    transcription: String = "",
    modifier: Modifier = Modifier
) {
    val text = when {
        state == VoiceButtonState.LISTENING && transcription.isNotEmpty() -> transcription
        state == VoiceButtonState.LISTENING -> stringResource(R.string.home_listening)
        state == VoiceButtonState.PROCESSING -> "..."
        else -> stringResource(R.string.home_tap_to_speak)
    }

    val color = when (state) {
        VoiceButtonState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
        VoiceButtonState.LISTENING -> MaterialTheme.colorScheme.primary
        VoiceButtonState.PROCESSING -> MaterialTheme.colorScheme.tertiary
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun VoiceButtonIdlePreview() {
    HospiceInventoryTheme {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            VoiceButton(
                state = VoiceButtonState.IDLE,
                onClick = {}
            )
            Spacer(modifier = Modifier.height(16.dp))
            VoiceButtonLabel(state = VoiceButtonState.IDLE)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VoiceButtonListeningPreview() {
    HospiceInventoryTheme {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            VoiceButton(
                state = VoiceButtonState.LISTENING,
                onClick = {}
            )
            Spacer(modifier = Modifier.height(16.dp))
            VoiceButtonLabel(
                state = VoiceButtonState.LISTENING,
                transcription = "Cerca letto elettrico..."
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VoiceButtonProcessingPreview() {
    HospiceInventoryTheme {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            VoiceButton(
                state = VoiceButtonState.PROCESSING,
                onClick = {}
            )
            Spacer(modifier = Modifier.height(16.dp))
            VoiceButtonLabel(state = VoiceButtonState.PROCESSING)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VoiceButtonCompactPreview() {
    HospiceInventoryTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            VoiceButtonCompact(state = VoiceButtonState.IDLE, onClick = {})
            VoiceButtonCompact(state = VoiceButtonState.LISTENING, onClick = {})
            VoiceButtonCompact(state = VoiceButtonState.PROCESSING, onClick = {})
        }
    }
}
