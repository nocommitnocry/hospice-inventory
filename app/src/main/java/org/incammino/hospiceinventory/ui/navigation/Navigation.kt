package org.incammino.hospiceinventory.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.incammino.hospiceinventory.ui.screens.home.HomeScreen
import org.incammino.hospiceinventory.ui.screens.search.SearchScreen
import org.incammino.hospiceinventory.ui.screens.product.ProductDetailScreen
import org.incammino.hospiceinventory.ui.screens.product.ProductEditScreen
import org.incammino.hospiceinventory.ui.screens.maintenance.MaintenanceListScreen
import org.incammino.hospiceinventory.ui.screens.maintenance.MaintenanceEditScreen
import org.incammino.hospiceinventory.ui.screens.maintainer.MaintainerListScreen
import org.incammino.hospiceinventory.ui.screens.maintainer.MaintainerEditScreen
import org.incammino.hospiceinventory.ui.screens.location.LocationListScreen
import org.incammino.hospiceinventory.ui.screens.location.LocationEditScreen
import org.incammino.hospiceinventory.ui.screens.settings.SettingsScreen
import org.incammino.hospiceinventory.ui.screens.settings.DataManagementScreen
import org.incammino.hospiceinventory.ui.screens.scanner.ScannerScreen
import org.incammino.hospiceinventory.ui.screens.scanner.BarcodeResultScreen
import org.incammino.hospiceinventory.ui.screens.voice.VoiceMaintenanceScreen
import org.incammino.hospiceinventory.ui.screens.voice.MaintenanceConfirmScreen
import org.incammino.hospiceinventory.ui.screens.voice.MaintenanceDataHolder
import org.incammino.hospiceinventory.ui.screens.voice.VoiceProductScreen
import org.incammino.hospiceinventory.ui.screens.voice.ProductConfirmScreen
import org.incammino.hospiceinventory.ui.screens.voice.ProductDataHolder
import org.incammino.hospiceinventory.ui.screens.voice.VoiceMaintainerScreen
import org.incammino.hospiceinventory.ui.screens.voice.MaintainerConfirmScreen
import org.incammino.hospiceinventory.ui.screens.voice.MaintainerDataHolder
import org.incammino.hospiceinventory.ui.screens.voice.VoiceLocationScreen
import org.incammino.hospiceinventory.ui.screens.voice.LocationConfirmScreen
import org.incammino.hospiceinventory.ui.screens.voice.LocationDataHolder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Route di navigazione dell'app.
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Search : Screen("search?query={query}") {
        fun createRoute(query: String = "") = "search?query=$query"
    }
    data object ProductDetail : Screen("product/{productId}") {
        fun createRoute(productId: String) = "product/$productId"
    }
    data object ProductEdit : Screen("product/edit/{productId}?prefill={prefill}") {
        fun createRoute(productId: String?, prefill: Map<String, String>? = null): String {
            val prefillJson = prefill?.let {
                URLEncoder.encode(Json.encodeToString(it), StandardCharsets.UTF_8.toString())
            } ?: ""
            return "product/edit/${productId ?: "new"}?prefill=$prefillJson"
        }
    }
    data object MaintenanceList : Screen("maintenances")
    data object MaintenanceEdit : Screen("maintenance/edit/{maintenanceId}?productId={productId}") {
        fun createRoute(maintenanceId: String? = null, productId: String? = null) =
            "maintenance/edit/${maintenanceId ?: "new"}?productId=${productId ?: ""}"
    }

    // Manutentori
    data object MaintainerList : Screen("maintainers")
    data object MaintainerEdit : Screen("maintainer/edit/{maintainerId}") {
        fun createRoute(maintainerId: String?) = "maintainer/edit/${maintainerId ?: "new"}"
    }

    // Ubicazioni
    data object LocationList : Screen("locations")
    data object LocationEdit : Screen("location/edit/{locationId}") {
        fun createRoute(locationId: String?) = "location/edit/${locationId ?: "new"}"
    }

    // Settings
    data object Settings : Screen("settings")
    data object DataManagement : Screen("data-management")

    // Scanner
    data object Scanner : Screen("scanner?reason={reason}") {
        fun createRoute(reason: String? = null) = "scanner?reason=${reason ?: ""}"
    }
    data object BarcodeResult : Screen("barcode_result/{barcode}") {
        fun createRoute(barcode: String) = "barcode_result/$barcode"
    }

    // Voice Dump Flow (v2.0 - 26/12/2025)
    data object VoiceMaintenance : Screen("voice/maintenance")
    data object MaintenanceConfirm : Screen("maintenance/confirm")
    data object VoiceProduct : Screen("voice/product")
    data object ProductConfirm : Screen("product/confirm")

    // Voice Dump Flow Fase 3 (28/12/2025)
    data object VoiceMaintainer : Screen("voice/maintainer")
    data object MaintainerConfirm : Screen("maintainer/confirm")
    data object VoiceLocation : Screen("voice/location")
    data object LocationConfirm : Screen("location/confirm")
}

/**
 * NavHost principale dell'app.
 *
 * @param navController Controller di navigazione
 * @param onVoiceSessionComplete Callback chiamato quando un flusso Voice Dump termina
 *        (Salva, Annulla o Back). Usato per pulire il contesto Gemini e evitare
 *        contaminazione dati tra sessioni vocali.
 */
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    onVoiceSessionComplete: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        // Home
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToSearch = { query ->
                    navController.navigate(Screen.Search.createRoute(query))
                },
                onNavigateToProduct = { productId ->
                    navController.navigate(Screen.ProductDetail.createRoute(productId))
                },
                onNavigateToNewProduct = { prefill ->
                    navController.navigate(Screen.ProductEdit.createRoute(null, prefill))
                },
                onNavigateToMaintenances = {
                    navController.navigate(Screen.MaintenanceList.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToScanner = {
                    navController.navigate(Screen.Scanner.route)
                },
                onNavigateToMaintainers = {
                    navController.navigate(Screen.MaintainerList.route)
                },
                onNavigateToLocations = {
                    navController.navigate(Screen.LocationList.route)
                },
                onNavigateToNewMaintenance = { productId, _ ->
                    navController.navigate(Screen.MaintenanceEdit.createRoute(null, productId))
                },
                onNavigateToNewMaintainer = { _ ->
                    navController.navigate(Screen.MaintainerEdit.createRoute(null))
                },
                onNavigateToNewLocation = { _ ->
                    navController.navigate(Screen.LocationEdit.createRoute(null))
                },
                onNavigateToVoiceMaintenance = {
                    navController.navigate(Screen.VoiceMaintenance.route)
                },
                onNavigateToVoiceProduct = {
                    navController.navigate(Screen.VoiceProduct.route)
                },
                onNavigateToVoiceMaintainer = {
                    navController.navigate(Screen.VoiceMaintainer.route)
                },
                onNavigateToVoiceLocation = {
                    navController.navigate(Screen.VoiceLocation.route)
                }
            )
        }

        // Search
        composable(
            route = Screen.Search.route,
            arguments = listOf(
                navArgument("query") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val query = backStackEntry.arguments?.getString("query") ?: ""
            SearchScreen(
                initialQuery = query,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProduct = { productId ->
                    navController.navigate(Screen.ProductDetail.createRoute(productId))
                }
            )
        }

        // Product Detail
        composable(
            route = Screen.ProductDetail.route,
            arguments = listOf(
                navArgument("productId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId") ?: return@composable
            ProductDetailScreen(
                productId = productId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = {
                    navController.navigate(Screen.ProductEdit.createRoute(productId))
                },
                onNavigateToMaintenance = { maintenanceId ->
                    navController.navigate(Screen.MaintenanceEdit.createRoute(maintenanceId, productId))
                },
                onNavigateToNewMaintenance = {
                    navController.navigate(Screen.MaintenanceEdit.createRoute(null, productId))
                }
            )
        }

        // Product Edit
        composable(
            route = Screen.ProductEdit.route,
            arguments = listOf(
                navArgument("productId") { type = NavType.StringType },
                navArgument("prefill") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId")
            val prefillJson = backStackEntry.arguments?.getString("prefill")?.takeIf { it.isNotEmpty() }
            val isNew = productId == "new"
            // Decodifica prefill se presente
            val prefillData = prefillJson?.let {
                try {
                    val decoded = java.net.URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
                    Json.decodeFromString<Map<String, String>>(decoded)
                } catch (e: Exception) {
                    null
                }
            }
            ProductEditScreen(
                productId = if (isNew) null else productId,
                prefillData = prefillData,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { savedProductId ->
                    navController.popBackStack()
                    if (isNew) {
                        navController.navigate(Screen.ProductDetail.createRoute(savedProductId))
                    }
                }
            )
        }

        // Maintenance List
        composable(Screen.MaintenanceList.route) {
            MaintenanceListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProduct = { productId ->
                    navController.navigate(Screen.ProductDetail.createRoute(productId))
                },
                onNavigateToMaintenance = { maintenanceId ->
                    navController.navigate(Screen.MaintenanceEdit.createRoute(maintenanceId))
                }
            )
        }

        // Maintenance Edit
        composable(
            route = Screen.MaintenanceEdit.route,
            arguments = listOf(
                navArgument("maintenanceId") { type = NavType.StringType },
                navArgument("productId") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val maintenanceId = backStackEntry.arguments?.getString("maintenanceId")
            val productId = backStackEntry.arguments?.getString("productId")
            val isNew = maintenanceId == "new"
            MaintenanceEditScreen(
                maintenanceId = if (isNew) null else maintenanceId,
                productId = productId?.takeIf { it.isNotEmpty() },
                onNavigateBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        // Maintainer List
        composable(Screen.MaintainerList.route) {
            MaintainerListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { maintainerId ->
                    navController.navigate(Screen.MaintainerEdit.createRoute(maintainerId))
                },
                onNavigateToNew = {
                    navController.navigate(Screen.MaintainerEdit.createRoute(null))
                }
            )
        }

        // Maintainer Edit
        composable(
            route = Screen.MaintainerEdit.route,
            arguments = listOf(
                navArgument("maintainerId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val maintainerId = backStackEntry.arguments?.getString("maintainerId")
            MaintainerEditScreen(
                maintainerId = if (maintainerId == "new") null else maintainerId,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        // Location List
        composable(Screen.LocationList.route) {
            LocationListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { locationId ->
                    navController.navigate(Screen.LocationEdit.createRoute(locationId))
                },
                onNavigateToNew = {
                    navController.navigate(Screen.LocationEdit.createRoute(null))
                }
            )
        }

        // Location Edit
        composable(
            route = Screen.LocationEdit.route,
            arguments = listOf(
                navArgument("locationId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val locationId = backStackEntry.arguments?.getString("locationId")
            LocationEditScreen(
                locationId = if (locationId == "new") null else locationId,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        // Settings
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDataManagement = {
                    navController.navigate(Screen.DataManagement.route)
                }
            )
        }

        // Data Management
        composable(Screen.DataManagement.route) {
            DataManagementScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Scanner
        composable(
            route = Screen.Scanner.route,
            arguments = listOf(
                navArgument("reason") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val reason = backStackEntry.arguments?.getString("reason")?.takeIf { it.isNotEmpty() }
            ScannerScreen(
                reason = reason,
                onNavigateBack = { navController.popBackStack() },
                onBarcodeScanned = { barcode ->
                    // Naviga al risultato barcode per cercare/creare prodotto
                    navController.navigate(Screen.BarcodeResult.createRoute(barcode)) {
                        popUpTo(Screen.Scanner.route) { inclusive = true }
                    }
                }
            )
        }

        // Barcode Result
        composable(
            route = Screen.BarcodeResult.route,
            arguments = listOf(navArgument("barcode") { type = NavType.StringType })
        ) { backStackEntry ->
            val barcode = backStackEntry.arguments?.getString("barcode") ?: ""
            BarcodeResultScreen(
                barcode = barcode,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProduct = { productId ->
                    navController.navigate(Screen.ProductDetail.createRoute(productId)) {
                        popUpTo(Screen.BarcodeResult.route) { inclusive = true }
                    }
                },
                onNavigateToCreateProduct = { barcodeValue ->
                    // Naviga a VoiceProductScreen con il barcode come parametro
                    // TODO: passare barcode come parametro a VoiceProductScreen
                    navController.navigate(Screen.VoiceProduct.route) {
                        popUpTo(Screen.BarcodeResult.route) { inclusive = true }
                    }
                }
            )
        }

        // ═══════════════════════════════════════════════════════════════════════════════
        // VOICE DUMP FLOW (v2.0 - 26/12/2025)
        // ═══════════════════════════════════════════════════════════════════════════════

        // Voice Maintenance - Input vocale
        composable(Screen.VoiceMaintenance.route) {
            VoiceMaintenanceScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToConfirm = { data ->
                    // Salva data in holder condiviso
                    MaintenanceDataHolder.set(data)
                    navController.navigate(Screen.MaintenanceConfirm.route)
                }
            )
        }

        // Maintenance Confirm - Scheda di conferma
        composable(Screen.MaintenanceConfirm.route) {
            // Usa remember per preservare i dati tra ricomposizioni
            // consume() viene chiamato solo una volta alla prima composizione
            val data = remember { MaintenanceDataHolder.consume() }

            if (data != null) {
                MaintenanceConfirmScreen(
                    initialData = data,
                    onNavigateBack = {
                        onVoiceSessionComplete()  // Cleanup contesto Gemini
                        navController.popBackStack()
                    },
                    onSaved = {
                        onVoiceSessionComplete()  // Cleanup contesto Gemini
                        // Torna alla home dopo il salvataggio
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    },
                    onNavigateToProductSearch = {
                        navController.navigate(Screen.Search.createRoute(""))
                    }
                )
            } else {
                // Se non ci sono dati, torna indietro
                // Usa LaunchedEffect per evitare chiamate durante la composizione
                LaunchedEffect(Unit) {
                    onVoiceSessionComplete()  // Cleanup anche in caso di dati mancanti
                    navController.popBackStack()
                }
            }
        }

        // Voice Product - Input vocale (Fase 2)
        composable(Screen.VoiceProduct.route) {
            VoiceProductScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToConfirm = { data ->
                    // Salva data in holder condiviso
                    ProductDataHolder.set(data)
                    navController.navigate(Screen.ProductConfirm.route)
                }
            )
        }

        // Product Confirm - Scheda di conferma (Fase 2)
        composable(Screen.ProductConfirm.route) {
            // Usa remember per preservare i dati tra ricomposizioni
            val data = remember { ProductDataHolder.consume() }

            if (data != null) {
                ProductConfirmScreen(
                    initialData = data,
                    onNavigateBack = {
                        onVoiceSessionComplete()  // Cleanup contesto Gemini
                        navController.popBackStack()
                    },
                    onSaved = {
                        onVoiceSessionComplete()  // Cleanup contesto Gemini
                        // Torna alla home dopo il salvataggio
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    },
                    onNavigateToLocationSearch = {
                        navController.navigate(Screen.LocationList.route)
                    }
                )
            } else {
                // Se non ci sono dati, torna indietro
                LaunchedEffect(Unit) {
                    onVoiceSessionComplete()  // Cleanup anche in caso di dati mancanti
                    navController.popBackStack()
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════════════════════
        // VOICE DUMP FLOW FASE 3 (28/12/2025)
        // ═══════════════════════════════════════════════════════════════════════════════

        // Voice Maintainer - Input vocale
        composable(Screen.VoiceMaintainer.route) {
            VoiceMaintainerScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToConfirm = { data ->
                    // Salva data in holder condiviso
                    MaintainerDataHolder.set(data)
                    navController.navigate(Screen.MaintainerConfirm.route)
                }
            )
        }

        // Maintainer Confirm - Scheda di conferma
        composable(Screen.MaintainerConfirm.route) {
            // Usa remember per preservare i dati tra ricomposizioni
            val data = remember { MaintainerDataHolder.consume() }

            if (data != null) {
                MaintainerConfirmScreen(
                    initialData = data,
                    onNavigateBack = {
                        onVoiceSessionComplete()  // Cleanup contesto Gemini
                        navController.popBackStack()
                    },
                    onSaved = {
                        onVoiceSessionComplete()  // Cleanup contesto Gemini
                        // Torna alla home dopo il salvataggio
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    }
                )
            } else {
                // Se non ci sono dati, torna indietro
                LaunchedEffect(Unit) {
                    onVoiceSessionComplete()  // Cleanup anche in caso di dati mancanti
                    navController.popBackStack()
                }
            }
        }

        // Voice Location - Input vocale
        composable(Screen.VoiceLocation.route) {
            VoiceLocationScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToConfirm = { data ->
                    // Salva data in holder condiviso
                    LocationDataHolder.set(data)
                    navController.navigate(Screen.LocationConfirm.route)
                }
            )
        }

        // Location Confirm - Scheda di conferma
        composable(Screen.LocationConfirm.route) {
            // Usa remember per preservare i dati tra ricomposizioni
            val data = remember { LocationDataHolder.consume() }

            if (data != null) {
                LocationConfirmScreen(
                    initialData = data,
                    onNavigateBack = {
                        onVoiceSessionComplete()  // Cleanup contesto Gemini
                        navController.popBackStack()
                    },
                    onSaved = {
                        onVoiceSessionComplete()  // Cleanup contesto Gemini
                        // Torna alla home dopo il salvataggio
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    }
                )
            } else {
                // Se non ci sono dati, torna indietro
                LaunchedEffect(Unit) {
                    onVoiceSessionComplete()  // Cleanup anche in caso di dati mancanti
                    navController.popBackStack()
                }
            }
        }
    }
}

/**
 * Alias per compatibilità con codice esistente.
 * @deprecated Usa AppNavigation o AppNavigationWithCleanup
 */
@Composable
fun HospiceNavHost(
    navController: NavHostController = rememberNavController()
) {
    AppNavigation(navController = navController)
}
