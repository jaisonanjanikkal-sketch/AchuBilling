package com.example.vyapar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
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
    val homeViewModel: HomeViewModel = viewModel { HomeViewModel(repository) }
    val dashboardViewModel: DashboardViewModel = viewModel { DashboardViewModel(repository) }
    val itemsViewModel: ItemsViewModel = viewModel { ItemsViewModel(repository) }
    val billingViewModel: BillingViewModel = viewModel { BillingViewModel(repository) }
    val historyViewModel: HistoryViewModel = viewModel { HistoryViewModel(repository) }
    val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel(repository) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    // Hide bottom navigation on billing screen to optimize screen estate
    val showBottomNav = currentRoute != "billing"

    // Hide main top bar on detailed analytics sub-screens to let them display their own back-navigation headers
    val showMainTopBar = showBottomNav && currentRoute != "low_stock" && currentRoute != "top_selling" && currentRoute != "all_transactions"

    // Only show main FAB on Home, Dashboard, and detailed screens to avoid double-FAB stacking on items/settings screen
    val showMainFab = currentRoute == "home" || currentRoute == "dashboard" || currentRoute == "low_stock" || currentRoute == "top_selling" || currentRoute == "all_transactions"

    Scaffold(
        topBar = {
            if (showMainTopBar) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = buildAnnotatedString {
                                    append("Anjani")
                                    withStyle(style = SpanStyle(color = Color(0xFF93C5FD))) {
                                        append("kkal")
                                    }
                                },
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF1D4ED8), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Smart Billing", fontSize = 10.sp, color = Color(0xFF93C5FD), fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2563EB))
                )
            }
        },
        floatingActionButton = {
            if (showMainFab) {
                ExtendedFloatingActionButton(
                    text = { Text("＋ Add New Sale", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "New Sale") },
                    onClick = {
                        billingViewModel.clearBill()
                        navController.navigate("billing")
                    },
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(24.dp)
                )
            }
        },
        bottomBar = {
            if (showBottomNav) {
                NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("HOME") },
                        selected = currentRoute == "home",
                        onClick = {
                            if (currentRoute != "home") {
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.List, contentDescription = "Dashboard") },
                        label = { Text("DASHBOARD") },
                        selected = currentRoute == "dashboard",
                        onClick = {
                            if (currentRoute != "dashboard") {
                                navController.navigate("dashboard") {
                                    popUpTo("home")
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "Items") },
                        label = { Text("ITEMS") },
                        selected = currentRoute == "items",
                        onClick = {
                            if (currentRoute != "items") {
                                navController.navigate("items") {
                                    popUpTo("home")
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Menu, contentDescription = "Menu") },
                        label = { Text("MENU") },
                        selected = currentRoute == "menu",
                        onClick = {
                            if (currentRoute != "menu") {
                                navController.navigate("menu") {
                                    popUpTo("home")
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
            startDestination = "home",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    viewModel = homeViewModel,
                    onTabChange = { tab ->
                        navController.navigate(tab) {
                            popUpTo("home")
                        }
                    },
                    onNavigateToBilling = {
                        billingViewModel.clearBill()
                        navController.navigate("billing")
                    },
                    onEditTransaction = { txn ->
                        billingViewModel.startEditing(txn)
                        navController.navigate("billing")
                    }
                )
            }
            composable("dashboard") {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToBilling = {
                        billingViewModel.clearBill()
                        navController.navigate("billing")
                    },
                    onNavigateToLowStock = {
                        navController.navigate("low_stock")
                    },
                    onNavigateToTopSelling = {
                        navController.navigate("top_selling")
                    },
                    onNavigateToAllTransactions = {
                        navController.navigate("all_transactions")
                    }
                )
            }
            composable("low_stock") {
                LowStockScreen(
                    viewModel = dashboardViewModel,
                    onBackClick = { navController.navigateUp() }
                )
            }
            composable("top_selling") {
                TopSellingScreen(
                    viewModel = dashboardViewModel,
                    onBackClick = { navController.navigateUp() }
                )
            }
            composable("all_transactions") {
                TransactionsScreen(
                    viewModel = dashboardViewModel,
                    onEditTransaction = { txn ->
                        billingViewModel.startEditing(txn)
                        navController.navigate("billing")
                    },
                    onBackClick = { navController.navigateUp() }
                )
            }
            composable("items") {
                ItemsScreen(
                    viewModel = itemsViewModel
                )
            }
            composable("menu") {
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
