package org.incammino.hospiceinventory.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.incammino.hospiceinventory.ui.screens.home.HomeScreen
import org.incammino.hospiceinventory.ui.screens.search.SearchScreen
import org.incammino.hospiceinventory.ui.screens.product.ProductDetailScreen
import org.incammino.hospiceinventory.ui.screens.product.ProductEditScreen
import org.incammino.hospiceinventory.ui.screens.maintenance.MaintenanceListScreen
import org.incammino.hospiceinventory.ui.screens.maintenance.MaintenanceEditScreen
import org.incammino.hospiceinventory.ui.screens.settings.SettingsScreen
import org.incammino.hospiceinventory.ui.screens.scanner.ScannerScreen

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
    data object ProductEdit : Screen("product/edit/{productId}") {
        fun createRoute(productId: String?) = "product/edit/${productId ?: "new"}"
    }
    data object MaintenanceList : Screen("maintenances")
    data object MaintenanceEdit : Screen("maintenance/edit/{maintenanceId}?productId={productId}") {
        fun createRoute(maintenanceId: String? = null, productId: String? = null) = 
            "maintenance/edit/${maintenanceId ?: "new"}?productId=${productId ?: ""}"
    }
    data object Settings : Screen("settings")
    data object Scanner : Screen("scanner?reason={reason}") {
        fun createRoute(reason: String? = null) = "scanner?reason=${reason ?: ""}"
    }
}

/**
 * NavHost principale dell'app.
 */
@Composable
fun HospiceNavHost(
    navController: NavHostController = rememberNavController()
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
                onNavigateToNewProduct = {
                    navController.navigate(Screen.ProductEdit.createRoute(null))
                },
                onNavigateToMaintenances = {
                    navController.navigate(Screen.MaintenanceList.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToScanner = {
                    navController.navigate(Screen.Scanner.route)
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
                navArgument("productId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId")
            val isNew = productId == "new"
            ProductEditScreen(
                productId = if (isNew) null else productId,
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
        
        // Settings
        composable(Screen.Settings.route) {
            SettingsScreen(
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
                    // Torna indietro e naviga alla ricerca con il barcode
                    navController.popBackStack()
                    navController.navigate(Screen.Search.createRoute(barcode))
                }
            )
        }
    }
}
