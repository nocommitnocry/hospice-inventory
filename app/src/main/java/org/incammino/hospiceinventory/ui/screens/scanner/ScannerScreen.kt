package org.incammino.hospiceinventory.ui.screens.scanner

import android.Manifest
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import org.incammino.hospiceinventory.service.scanner.BarcodeAnalyzer
import org.incammino.hospiceinventory.service.scanner.BarcodeResult
import java.util.concurrent.Executors

private const val TAG = "ScannerScreen"

/**
 * Schermata per la scansione di barcode/QR code.
 *
 * Usa CameraX per il preview e ML Kit per la decodifica.
 *
 * @param reason Motivo della scansione (opzionale, mostrato come sottotitolo)
 * @param onNavigateBack Callback per tornare indietro
 * @param onBarcodeScanned Callback quando un barcode viene scansionato
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    reason: String? = null,
    onNavigateBack: () -> Unit,
    onBarcodeScanned: (String) -> Unit
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Scanner")
                        reason?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Indietro"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                cameraPermissionState.status.isGranted -> {
                    CameraPreview(
                        onBarcodeScanned = onBarcodeScanned
                    )
                }
                cameraPermissionState.status.shouldShowRationale -> {
                    PermissionRationale(
                        onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                    )
                }
                else -> {
                    PermissionDenied()
                }
            }
        }
    }
}

/**
 * Preview della camera con overlay per la scansione.
 */
@Composable
private fun CameraPreview(
    onBarcodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var scannedBarcode by remember { mutableStateOf<BarcodeResult?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }

    // Inizializza la camera
    LaunchedEffect(lifecycleOwner) {
        camera = initializeCamera(
            context = context,
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            cameraExecutor = cameraExecutor,
            onBarcodeDetected = { barcode ->
                val result = BarcodeResult.fromBarcode(barcode)
                scannedBarcode = result
                Log.i(TAG, "Barcode scanned: ${result.rawValue}")
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay con mirino
        ScannerOverlay()

        // Pulsante flash
        camera?.let { cam ->
            if (cam.cameraInfo.hasFlashUnit()) {
                IconButton(
                    onClick = {
                        isFlashOn = !isFlashOn
                        cam.cameraControl.enableTorch(isFlashOn)
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Icon(
                        if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = if (isFlashOn) "Spegni flash" else "Accendi flash",
                        tint = Color.White
                    )
                }
            }
        }

        // Risultato scansione
        AnimatedVisibility(
            visible = scannedBarcode != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            scannedBarcode?.let { result ->
                ScannedResultCard(
                    result = result,
                    onConfirm = {
                        onBarcodeScanned(result.rawValue)
                    },
                    onDismiss = {
                        scannedBarcode = null
                    }
                )
            }
        }

        // Istruzioni
        if (scannedBarcode == null) {
            Text(
                text = "Inquadra il codice a barre",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * Overlay con mirino per guidare la scansione.
 */
@Composable
private fun ScannerOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Oscuramento aree esterne al mirino
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scanAreaWidth = size.width * 0.7f
            val scanAreaHeight = size.height * 0.3f
            val left = (size.width - scanAreaWidth) / 2
            val top = (size.height - scanAreaHeight) / 2

            // Area oscurata sopra
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = androidx.compose.ui.geometry.Offset.Zero,
                size = androidx.compose.ui.geometry.Size(size.width, top)
            )
            // Area oscurata sotto
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = androidx.compose.ui.geometry.Offset(0f, top + scanAreaHeight),
                size = androidx.compose.ui.geometry.Size(size.width, size.height - top - scanAreaHeight)
            )
            // Area oscurata sinistra
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                size = androidx.compose.ui.geometry.Size(left, scanAreaHeight)
            )
            // Area oscurata destra
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = androidx.compose.ui.geometry.Offset(left + scanAreaWidth, top),
                size = androidx.compose.ui.geometry.Size(left, scanAreaHeight)
            )

            // Bordo del mirino
            val strokeWidth = 4.dp.toPx()
            val cornerLength = 40.dp.toPx()

            // Angoli del mirino (colore primario)
            val cornerColor = Color(0xFF1E88E5) // HospiceBlue

            // Top-left
            drawLine(cornerColor, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(left + cornerLength, top), strokeWidth)
            drawLine(cornerColor, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(left, top + cornerLength), strokeWidth)

            // Top-right
            drawLine(cornerColor, androidx.compose.ui.geometry.Offset(left + scanAreaWidth - cornerLength, top), androidx.compose.ui.geometry.Offset(left + scanAreaWidth, top), strokeWidth)
            drawLine(cornerColor, androidx.compose.ui.geometry.Offset(left + scanAreaWidth, top), androidx.compose.ui.geometry.Offset(left + scanAreaWidth, top + cornerLength), strokeWidth)

            // Bottom-left
            drawLine(cornerColor, androidx.compose.ui.geometry.Offset(left, top + scanAreaHeight - cornerLength), androidx.compose.ui.geometry.Offset(left, top + scanAreaHeight), strokeWidth)
            drawLine(cornerColor, androidx.compose.ui.geometry.Offset(left, top + scanAreaHeight), androidx.compose.ui.geometry.Offset(left + cornerLength, top + scanAreaHeight), strokeWidth)

            // Bottom-right
            drawLine(cornerColor, androidx.compose.ui.geometry.Offset(left + scanAreaWidth, top + scanAreaHeight - cornerLength), androidx.compose.ui.geometry.Offset(left + scanAreaWidth, top + scanAreaHeight), strokeWidth)
            drawLine(cornerColor, androidx.compose.ui.geometry.Offset(left + scanAreaWidth - cornerLength, top + scanAreaHeight), androidx.compose.ui.geometry.Offset(left + scanAreaWidth, top + scanAreaHeight), strokeWidth)
        }
    }
}

@Composable
private fun Canvas(modifier: Modifier, onDraw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    androidx.compose.foundation.Canvas(modifier = modifier, onDraw = onDraw)
}

/**
 * Card con il risultato della scansione.
 */
@Composable
private fun ScannedResultCard(
    result: BarcodeResult,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Codice rilevato",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = result.displayValue,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = result.formatName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Riprova")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Conferma")
                }
            }
        }
    }
}

/**
 * Schermata per richiedere il permesso camera.
 */
@Composable
private fun PermissionRationale(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.QrCodeScanner,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Permesso fotocamera richiesto",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Per scansionare i codici a barre, l'app ha bisogno di accedere alla fotocamera.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("Consenti accesso")
        }
    }
}

/**
 * Schermata quando il permesso è stato negato definitivamente.
 */
@Composable
private fun PermissionDenied() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.QrCodeScanner,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Permesso negato",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Per usare lo scanner, abilita il permesso fotocamera nelle impostazioni dell'app.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CAMERA INITIALIZATION
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Inizializza la camera con CameraX.
 */
private suspend fun initializeCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    cameraExecutor: java.util.concurrent.ExecutorService,
    onBarcodeDetected: (com.google.mlkit.vision.barcode.common.Barcode) -> Unit
): androidx.camera.core.Camera? {
    return withContext(Dispatchers.Main) {
        try {
            val cameraProvider = getCameraProvider(context)

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(
                        cameraExecutor,
                        BarcodeAnalyzer(
                            onBarcodeDetected = onBarcodeDetected,
                            onError = { e ->
                                Log.e(TAG, "Scan error", e)
                            }
                        )
                    )
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            Log.e(TAG, "Camera initialization failed", e)
            null
        }
    }
}

/**
 * Ottiene il ProcessCameraProvider in modo suspend.
 */
private suspend fun getCameraProvider(context: Context): ProcessCameraProvider {
    return ProcessCameraProvider.getInstance(context).await()
}
