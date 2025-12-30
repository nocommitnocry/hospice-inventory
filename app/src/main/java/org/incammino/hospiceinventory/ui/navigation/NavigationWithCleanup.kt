package org.incammino.hospiceinventory.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import org.incammino.hospiceinventory.ui.screens.home.HomeViewModel

/**
 * Wrapper che fornisce il callback per pulizia memoria Gemini.
 *
 * Il cleanup viene triggerato quando un flusso Voice Dump termina,
 * sia con Salva che con Annulla/Back.
 *
 * Questo risolve il problema di contaminazione dati tra sessioni vocali,
 * dove il ConversationContext accumulava dati dalle sessioni precedenti.
 */
@Composable
fun AppNavigationWithCleanup(
    navController: NavHostController
) {
    // Ottieni HomeViewModel per accedere a GeminiService via VoiceAssistant
    val homeViewModel: HomeViewModel = hiltViewModel()

    AppNavigation(
        navController = navController,
        onVoiceSessionComplete = {
            // Pulisce il ConversationContext di Gemini
            homeViewModel.clearGeminiContext()
        }
    )
}
