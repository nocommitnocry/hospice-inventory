package org.incammino.hospiceinventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import org.incammino.hospiceinventory.ui.navigation.HospiceNavHost
import org.incammino.hospiceinventory.ui.theme.HospiceInventoryTheme

/**
 * Activity principale di Hospice Inventory.
 * Punto di ingresso dell'app, configura Compose e la navigazione.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            HospiceInventoryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HospiceNavHost()
                }
            }
        }
    }
}
