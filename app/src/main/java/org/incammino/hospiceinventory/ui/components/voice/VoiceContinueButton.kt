package org.incammino.hospiceinventory.ui.components.voice

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Stato del bottone vocale.
 */
enum class VoiceContinueState {
    Idle,
    Listening,
    Processing
}

/**
 * Bottone per continuare l'input vocale nelle schermate di conferma.
 * Permette all'utente di aggiungere dettagli senza tornare indietro.
 *
 * @param state Stato attuale del bottone
 * @param onTap Callback quando l'utente tocca il bottone
 * @param partialTranscript Testo parziale durante l'ascolto (opzionale)
 * @param modifier Modifier per il componente
 */
@Composable
fun VoiceContinueButton(
    state: VoiceContinueState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    partialTranscript: String? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedButton(
            onClick = onTap,
            enabled = state != VoiceContinueState.Processing,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = when (state) {
                    VoiceContinueState.Listening -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            when (state) {
                VoiceContinueState.Processing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Elaborazione...")
                }
                VoiceContinueState.Listening -> {
                    Icon(
                        Icons.Default.MicOff,
                        contentDescription = "Stop",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Tocca per terminare")
                }
                VoiceContinueState.Idle -> {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Parla",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Aggiungi dettagli a voce")
                }
            }
        }

        // Mostra trascrizione parziale durante l'ascolto
        if (state == VoiceContinueState.Listening && !partialTranscript.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = partialTranscript,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
