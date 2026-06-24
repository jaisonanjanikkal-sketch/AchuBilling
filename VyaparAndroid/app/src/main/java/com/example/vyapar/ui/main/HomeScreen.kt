package com.example.vyapar.ui.main

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vyapar.data.BusinessProfile
import com.example.vyapar.data.DataRepository
import com.example.vyapar.data.TransactionWithItems
import com.example.vyapar.printer.ThermalPrinterManager
import com.example.vyapar.utils.InvoiceShareUtility
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class HomeStats(
    val todaySales: Double = 0.0,
    val totalItemsSold: Double = 0.0,
    val totalTransactions: Int = 0,
    val lowStockCount: Int = 0
)

data class HomeState(
    val stats: HomeStats = HomeStats(),
    val recentTransactions: List<TransactionWithItems> = emptyList()
)

class HomeViewModel(private val repository: DataRepository) : ViewModel() {
    val state: StateFlow<HomeState> = combine(
        repository.getTransactionsFlow(),
        repository.getItemsFlow(),
        repository.getLowStockItemsFlow()
    ) { txns, items, lowStock ->
        // Today's Sales calculation
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todaySales = txns.filter { it.transaction.date >= todayStart }.sumOf { it.transaction.grandTotal }

        // Total items sold (sum of quantity of all items in all sales)
        val totalItemsSold = txns.sumOf { it.items.sumOf { item -> item.quantity } }

        // Total Invoices
        val totalTransactions = txns.size

        // Low stock count (items with stock <= 5)
        val lowStockCount = items.filter { it.stock <= 5 }.size

        HomeState(
            stats = HomeStats(todaySales, totalItemsSold, totalTransactions, lowStockCount),
            recentTransactions = txns.take(10)
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    fun getBusinessProfile(): BusinessProfile = repository.getBusinessProfile()
    fun getSelectedPrinterAddress(): String? = repository.getSelectedPrinterAddress()

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onTabChange: (String) -> Unit,
    onNavigateToBilling: () -> Unit,
    onEditTransaction: (TransactionWithItems) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.state.collectAsState()
    val context = LocalContext.current
    val printerManager = remember { ThermalPrinterManager(context) }
    var selectedTxnForDelete by remember { mutableStateOf<Long?>(null) }

    if (selectedTxnForDelete != null) {
        AlertDialog(
            onDismissRequest = { selectedTxnForDelete = null },
            title = { Text("Delete Transaction?") },
            text = { Text("Are you sure you want to delete invoice #${selectedTxnForDelete}? This will also revert the item stocks.") },
            confirmButton = {
                Button(
                    onClick = {
                        selectedTxnForDelete?.let { viewModel.deleteTransaction(it) }
                        selectedTxnForDelete = null
                        Toast.makeText(context, "Transaction deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedTxnForDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
    ) {


        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stats Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            title = "Today's Sales",
                            value = formatCurrency(uiState.stats.todaySales),
                            icon = "💰",
                            color = Color(0xFF2563EB), // Blue
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "Items Sold",
                            value = formatQuantity(uiState.stats.totalItemsSold),
                            icon = "📦",
                            color = Color(0xFF16A34A), // Green
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            title = "Total Invoices",
                            value = uiState.stats.totalTransactions.toString(),
                            icon = "📋",
                            color = Color(0xFFD97706), // Amber
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "Low Stock Alerts",
                            value = uiState.stats.lowStockCount.toString(),
                            icon = "⚠️",
                            color = Color(0xFFDC2626), // Red
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Quick Links Section
            item {
                Column {
                    Text(
                        text = "Quick Links",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        QuickLinkCard(
                            label = "Stock Summary",
                            icon = "📦",
                            bg = Color(0xFFDBEAFE),
                            tint = Color(0xFF2563EB),
                            onClick = { onTabChange("items") },
                            modifier = Modifier.weight(1f)
                        )
                        QuickLinkCard(
                            label = "Analytics",
                            icon = "📊",
                            bg = Color(0xFFDCFCE7),
                            tint = Color(0xFF16A34A),
                            onClick = { onTabChange("dashboard") },
                            modifier = Modifier.weight(1f)
                        )
                        QuickLinkCard(
                            label = "New Sale",
                            icon = "🧾",
                            bg = Color(0xFFFEE2E2),
                            tint = Color(0xFFDC2626),
                            onClick = onNavigateToBilling,
                            modifier = Modifier.weight(1f)
                        )
                        QuickLinkCard(
                            label = "Settings",
                            icon = "⚙️",
                            bg = Color(0xFFFEF3C7),
                            tint = Color(0xFFD97706),
                            onClick = { onTabChange("menu") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Recent Transactions Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Transactions",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "View All",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB),
                        modifier = Modifier.clickable { onTabChange("dashboard") }
                    )
                }
            }

            // Recent Transactions List
            if (uiState.recentTransactions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📋", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No transactions yet",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                fontSize = 14.sp
                            )
                            Text(
                                "Tap \"＋ Add New Sale\" to create your first invoice",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(uiState.recentTransactions, key = { it.transaction.id }) { txn ->
                    TransactionUiCard(
                        txn = txn,
                        onPrint = {
                            val printerMac = viewModel.getSelectedPrinterAddress()
                            if (printerMac.isNullOrBlank()) {
                                Toast.makeText(context, "Printer not connected. Go to Settings to link printer.", Toast.LENGTH_SHORT).show()
                            } else if (!printerManager.hasBluetoothPermission()) {
                                Toast.makeText(context, "Bluetooth permissions are required for printing.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Connecting to printer...", Toast.LENGTH_SHORT).show()
                                viewModel.viewModelScope.launch {
                                    val printResult = printerManager.printInvoice(printerMac, viewModel.getBusinessProfile(), txn)
                                    if (printResult.isSuccess) {
                                        Toast.makeText(context, "Printed successfully!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Print failed: ${printResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        onShare = {
                            val profile = viewModel.getBusinessProfile()
                            val dialogOptions = arrayOf("Share as PNG Image", "Share as PDF Document")
                            android.app.AlertDialog.Builder(context)
                                .setTitle("Share Invoice #${txn.transaction.id}")
                                .setItems(dialogOptions) { _, which ->
                                    if (which == 0) {
                                        InvoiceShareUtility.shareInvoiceAsImage(context, profile, txn)
                                    } else {
                                        InvoiceShareUtility.shareInvoiceAsPdf(context, profile, txn)
                                    }
                                }
                                .show()
                        },
                        onEdit = { onEditTransaction(txn) },
                        onDelete = { selectedTxnForDelete = txn.transaction.id }
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = color)
            Text(text = title, fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun QuickLinkCard(
    label: String,
    icon: String,
    bg: Color,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(bg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )
        }
    }
}


