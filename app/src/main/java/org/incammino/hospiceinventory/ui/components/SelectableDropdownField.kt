package org.incammino.hospiceinventory.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/**
 * Campo con dropdown selezionabile E digitazione libera.
 *
 * Comportamento:
 * - Click sulla freccia -> mostra TUTTI i valori
 * - Digitazione -> filtra i valori + permette valore nuovo
 * - Selezione da lista -> imposta il valore
 *
 * @param value Valore corrente
 * @param onValueChange Callback quando cambia il valore
 * @param suggestions Lista di suggerimenti dal database
 * @param label Etichetta del campo
 * @param isError Mostra stato errore
 * @param isRequired Aggiunge asterisco alla label
 * @param allowNewValues Se true, permette valori non in lista (default true)
 * @param maxSuggestions Numero massimo di suggerimenti visibili (default 10)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectableDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    isRequired: Boolean = false,
    allowNewValues: Boolean = true,
    maxSuggestions: Int = 10
) {
    var expanded by remember { mutableStateOf(false) }
    var textFieldValue by remember(value) { mutableStateOf(value) }

    // Filtra i suggerimenti in base al testo digitato
    val filteredSuggestions = remember(textFieldValue, suggestions) {
        if (textFieldValue.isBlank()) {
            suggestions.take(maxSuggestions)
        } else {
            suggestions.filter {
                it.contains(textFieldValue, ignoreCase = true)
            }.take(maxSuggestions)
        }
    }

    // Mostra dropdown se:
    // - expanded è true E
    // - ci sono suggerimenti da mostrare (o è lista completa)
    val shouldShowDropdown = expanded && (
            filteredSuggestions.isNotEmpty() ||
                    (textFieldValue.isBlank() && suggestions.isNotEmpty())
            )

    ExposedDropdownMenuBox(
        expanded = shouldShowDropdown,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                onValueChange(newValue)
                expanded = true // Apri dropdown mentre digiti
            },
            label = {
                Text(if (isRequired) "$label *" else label)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable),
            singleLine = true,
            isError = isError,
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Default.ArrowDropUp
                        } else {
                            Icons.Default.ArrowDropDown
                        },
                        contentDescription = if (expanded) {
                            "Chiudi suggerimenti"
                        } else {
                            "Mostra suggerimenti"
                        }
                    )
                }
            }
        )

        ExposedDropdownMenu(
            expanded = shouldShowDropdown,
            onDismissRequest = { expanded = false }
        ) {
            filteredSuggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        textFieldValue = suggestion
                        onValueChange(suggestion)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }

            // Se il valore digitato non è nella lista e allowNewValues è true
            if (allowNewValues &&
                textFieldValue.isNotBlank() &&
                !suggestions.any { it.equals(textFieldValue, ignoreCase = true) }
            ) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(
                            "Usa \"$textFieldValue\"",
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = {
                        onValueChange(textFieldValue)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
