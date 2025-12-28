package org.incammino.hospiceinventory.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Componente riutilizzabile per creare inline entità mancanti.
 *
 * Mostra:
 * - Stato "Non trovato" con nome cercato
 * - Pulsante "Crea" per creare l'entità al volo
 * - Stato "Creato" dopo la creazione
 *
 * @param entityName Il nome dell'entità da creare (es. "Medika")
 * @param entityType Il tipo di entità (es. "Manutentore", "Ubicazione")
 * @param onCreateClick Callback per la creazione
 * @param isCreating True durante la creazione
 * @param wasCreated True dopo la creazione
 * @param modifier Modifier opzionale
 */
@Composable
fun InlineEntityCreator(
    entityName: String,
    entityType: String,
    onCreateClick: () -> Unit,
    isCreating: Boolean = false,
    wasCreated: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (wasCreated) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                }
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (wasCreated) Icons.Default.Check else Icons.Default.Warning,
            contentDescription = null,
            tint = if (wasCreated) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.size(20.dp)
        )

        Spacer(Modifier.width(8.dp))

        Column(Modifier.weight(1f)) {
            if (wasCreated) {
                Text(
                    text = "$entityName creato",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Da completare successivamente",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Non trovato: \"$entityName\"",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "$entityType non presente nel sistema",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!wasCreated) {
            TextButton(
                onClick = onCreateClick,
                enabled = !isCreating
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Crea")
                }
            }
        }
    }
}
