package org.incammino.hospiceinventory.ui.components.voice

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.incammino.hospiceinventory.service.voice.VoiceState

/**
 * Pulsante Tap-to-Start / Tap-to-Stop per voice dump.
 *
 * Uso:
 *   VoiceDumpButton(
 *       voiceState = viewModel.voiceState,
 *       partialText = viewModel.partialText,
 *       onStartListening = { viewModel.startVoiceDump() },
 *       onStopListening = { viewModel.stopVoiceDump() }
 *   )
 */
@Composable
fun VoiceDumpButton(
    voiceState: VoiceState,
    partialText: String,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = voiceState is VoiceState.Listening ||
            voiceState is VoiceState.PartialResult

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Testo parziale durante ascolto
        if (isListening && partialText.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = partialText,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Pulsante principale
        Button(
            onClick = {
                if (isListening) {
                    onStopListening()
                } else {
                    onStartListening()
                }
            },
            modifier = Modifier
                .size(if (isListening) 120.dp else 80.dp)
                .animateContentSize(),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isListening)
                    Color.Red
                else
                    MaterialTheme.colorScheme.primary
            )
        ) {
            if (isListening) {
                // Stato RECORDING - mostra STOP
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                    Text(
                        "STOP",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Stato IDLE - mostra MIC
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Parla",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            }
        }

        // Label sotto il pulsante
        Text(
            text = if (isListening)
                "Tap quando hai finito"
            else
                "Tap per parlare",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )

        // Indicatore recording animato
        if (isListening) {
            RecordingIndicator()
        }
    }
}

/**
 * Indicatore animato "REC" durante la registrazione.
 */
@Composable
private fun RecordingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "recording")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Row(
        modifier = Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    color = Color.Red.copy(alpha = alpha),
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "REC",
            color = Color.Red.copy(alpha = alpha),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

/**
 * Versione compatta del VoiceDumpButton per spazi ristretti.
 * Non mostra il testo parziale inline (va gestito separatamente).
 */
@Composable
fun VoiceDumpButtonCompact(
    isListening: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                if (isListening) {
                    onStopListening()
                } else {
                    onStartListening()
                }
            },
            modifier = Modifier
                .size(if (isListening) 100.dp else 72.dp)
                .animateContentSize(),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isListening) Color.Red else MaterialTheme.colorScheme.primary
            )
        ) {
            if (isListening) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    modifier = Modifier.size(28.dp),
                    tint = Color.White
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Parla",
                    modifier = Modifier.size(28.dp),
                    tint = Color.White
                )
            }
        }

        Text(
            text = if (isListening) "STOP" else "PARLA",
            style = MaterialTheme.typography.labelMedium,
            color = if (isListening) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )

        if (isListening) {
            RecordingIndicator()
        }
    }
}
