package org.incammino.hospiceinventory.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Stato del permesso microfono.
 */
enum class MicrophonePermissionState {
    /** Permesso non ancora verificato */
    Unknown,
    /** Permesso concesso */
    Granted,
    /** Permesso negato */
    Denied,
    /** Permesso negato permanentemente (shouldShowRationale = false) */
    PermanentlyDenied
}

/**
 * Stato e funzioni per gestire il permesso microfono.
 */
data class MicrophonePermissionHandler(
    val state: MicrophonePermissionState,
    val requestPermission: () -> Unit,
    val isGranted: Boolean
) {
    val isDenied: Boolean get() = state == MicrophonePermissionState.Denied ||
            state == MicrophonePermissionState.PermanentlyDenied
}

/**
 * Composable che gestisce il permesso microfono.
 *
 * Uso:
 * ```
 * val micPermission = rememberMicrophonePermission()
 *
 * Button(onClick = {
 *     if (micPermission.isGranted) {
 *         startListening()
 *     } else {
 *         micPermission.requestPermission()
 *     }
 * })
 *
 * if (micPermission.isDenied) {
 *     Text("Permesso microfono necessario")
 * }
 * ```
 *
 * @param onGranted Callback opzionale chiamato quando il permesso viene concesso
 * @param onDenied Callback opzionale chiamato quando il permesso viene negato
 */
@Composable
fun rememberMicrophonePermission(
    onGranted: (() -> Unit)? = null,
    onDenied: (() -> Unit)? = null
): MicrophonePermissionHandler {
    val context = LocalContext.current

    var permissionState by remember {
        val currentPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        )
        mutableStateOf(
            if (currentPermission == PackageManager.PERMISSION_GRANTED) {
                MicrophonePermissionState.Granted
            } else {
                MicrophonePermissionState.Unknown
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionState = if (isGranted) {
            MicrophonePermissionState.Granted
        } else {
            MicrophonePermissionState.Denied
        }

        if (isGranted) {
            onGranted?.invoke()
        } else {
            onDenied?.invoke()
        }
    }

    return remember(permissionState) {
        MicrophonePermissionHandler(
            state = permissionState,
            requestPermission = {
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            },
            isGranted = permissionState == MicrophonePermissionState.Granted
        )
    }
}

/**
 * Verifica se il permesso microfono è concesso.
 * Utile per controlli rapidi senza state management.
 */
@Composable
fun isMicrophonePermissionGranted(): Boolean {
    val context = LocalContext.current
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}
