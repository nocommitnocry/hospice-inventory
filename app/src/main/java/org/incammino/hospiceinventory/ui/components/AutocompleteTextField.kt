package org.incammino.hospiceinventory.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization

/**
 * Campo testo con suggerimenti autocomplete.
 *
 * Comportamento:
 * - Digitazione -> mostra suggerimenti filtrati
 * - Selezione da lista -> imposta il valore
 * - Se lista vuota o nessun match, permette digitazione libera
 *
 * @param value Valore corrente
 * @param onValueChange Callback quando cambia il valore
 * @param suggestions Lista di suggerimenti
 * @param label Etichetta del campo
 * @param placeholder Placeholder opzionale
 * @param capitalization Tipo di capitalizzazione (default: Words)
 * @param singleLine Campo su singola riga (default: true)
 * @param maxSuggestions Numero massimo suggerimenti (default: 10)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutocompleteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
    singleLine: Boolean = true,
    maxSuggestions: Int = 10
) {
    var expanded by remember { mutableStateOf(false) }
    var textFieldValue by remember(value) { mutableStateOf(value) }

    // Filtra suggerimenti in base al testo corrente
    val filteredSuggestions = remember(textFieldValue, suggestions) {
        if (textFieldValue.isBlank()) {
            suggestions.take(maxSuggestions)
        } else {
            suggestions.filter {
                it.contains(textFieldValue, ignoreCase = true)
            }.take(maxSuggestions)
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filteredSuggestions.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                onValueChange(newValue)
                expanded = true
            },
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable),
            singleLine = singleLine,
            placeholder = placeholder?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                capitalization = capitalization
            ),
            trailingIcon = {
                if (filteredSuggestions.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            }
        )

        if (filteredSuggestions.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
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
            }
        }
    }
}
