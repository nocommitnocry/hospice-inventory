package org.incammino.hospiceinventory.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Colori basati sul logo della farfalla dell'Hospice.
 * Blu principale: #1E88E5
 * Azzurro chiaro: #90CAF9
 */

// ═══════════════════════════════════════════════════════════════════════════
// COLORI PRIMARI (dal logo)
// ═══════════════════════════════════════════════════════════════════════════

val HospiceBlue = Color(0xFF1E88E5)           // Blu principale
val HospiceLightBlue = Color(0xFF90CAF9)      // Azzurro chiaro
val HospiceDarkBlue = Color(0xFF1565C0)       // Blu scuro per contrasto

// ═══════════════════════════════════════════════════════════════════════════
// COLORI SEMANTICI
// ═══════════════════════════════════════════════════════════════════════════

val SuccessGreen = Color(0xFF4CAF50)
val WarningOrange = Color(0xFFFF9800)
val ErrorRed = Color(0xFFF44336)
val InfoBlue = Color(0xFF2196F3)

// Alert colors
val AlertOverdue = Color(0xFFD32F2F)          // Rosso per scaduto
val AlertWarning = Color(0xFFF57C00)          // Arancione per in scadenza
val AlertOk = Color(0xFF388E3C)               // Verde per OK

// ═══════════════════════════════════════════════════════════════════════════
// LIGHT THEME
// ═══════════════════════════════════════════════════════════════════════════

private val LightColorScheme = lightColorScheme(
    primary = HospiceBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF0D47A1),
    
    secondary = HospiceLightBlue,
    onSecondary = Color(0xFF0D47A1),
    secondaryContainer = Color(0xFFE3F2FD),
    onSecondaryContainer = Color(0xFF1565C0),
    
    tertiary = Color(0xFF00897B),              // Teal per accent
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB2DFDB),
    onTertiaryContainer = Color(0xFF004D40),
    
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFFB71C1C),
    
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF212121),
    
    surface = Color.White,
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFE3F2FD),
    onSurfaceVariant = Color(0xFF424242),
    
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFE0E0E0)
)

// ═══════════════════════════════════════════════════════════════════════════
// DARK THEME
// ═══════════════════════════════════════════════════════════════════════════

private val DarkColorScheme = darkColorScheme(
    primary = HospiceLightBlue,
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = HospiceDarkBlue,
    onPrimaryContainer = Color(0xFFBBDEFB),
    
    secondary = Color(0xFF64B5F6),
    onSecondary = Color(0xFF0D47A1),
    secondaryContainer = Color(0xFF1565C0),
    onSecondaryContainer = Color(0xFFE3F2FD),
    
    tertiary = Color(0xFF4DB6AC),
    onTertiary = Color(0xFF003D33),
    tertiaryContainer = Color(0xFF00695C),
    onTertiaryContainer = Color(0xFFB2DFDB),
    
    error = Color(0xFFEF5350),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF263238),
    onSurfaceVariant = Color(0xFFB0BEC5),
    
    outline = Color(0xFF616161),
    outlineVariant = Color(0xFF424242)
)

// ═══════════════════════════════════════════════════════════════════════════
// THEME
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun HospiceInventoryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
