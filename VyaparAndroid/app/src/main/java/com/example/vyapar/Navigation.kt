package com.example.vyapar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.vyapar.data.DefaultDataRepository
import com.example.vyapar.ui.billing.BillingScreen
import com.example.vyapar.ui.billing.BillingViewModel
import com.example.vyapar.ui.main.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val repository = remember { DefaultDataRepository(context.applicationContext) }

    // Initialize ViewModels manually passing repository for simplicity and reliability
    val dashboardViewModel: DashboardViewModel = viewModel { DashboardViewModel(repository) }
    val itemsViewModel: ItemsViewModel = viewModel { ItemsViewModel(repository) }
    val billingViewModel: BillingViewModel = viewModel { BillingViewModel(repository) }
    val historyViewModel: HistoryViewModel = viewModel { HistoryViewModel(repository) }
    val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel(repository) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "dashboard"

    // Hide bottom navigation on billing screen to optimize screen estate
    val showBottomNav = currentRoute != "billing"

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                        label = { Text("Dashboard") },
                        selected = currentRoute == "dashboard",
                        onClick = {
                            if (currentRoute != "dashboard") {
                                navController.navigate("dashboard") {
                                    popUpTo("dashboard") { inclusive = true }
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.List, contentDescription = "Inventory") },
                        label = { Text("Items") },
                        selected = currentRoute == "items",
                        onClick = {
                            if (currentRoute != "items") {
                                navController.navigate("items") {
                                    popUpTo("dashboard")
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Menu, contentDescription = "History") },
                        label = { Text("History") },
                        selected = currentRoute == "history",
                        onClick = {
                            if (currentRoute != "history") {
                                navController.navigate("history") {
                                    popUpTo("dashboard")
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = currentRoute == "settings",
                        onClick = {
                            if (currentRoute != "settings") {
                                navController.navigate("settings") {
                                    popUpTo("dashboard")
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable("dashboard") {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToBilling = { navController.navigate("billing") },
                    onViewTransaction = { _ ->
                        navController.navigate("history")
                    }
                )
            }
            composable("items") {
                ItemsScreen(
                    viewModel = itemsViewModel
                )
            }
            composable("history") {
                HistoryScreen(
                    viewModel = historyViewModel
                )
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = settingsViewModel
                )
            }
            composable("billing") {
                BillingScreen(
                    viewModel = billingViewModel,
                    onBackClick = { navController.navigateUp() }
                )
            }
        }
    }
}
