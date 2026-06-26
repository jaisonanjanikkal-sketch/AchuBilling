package com.example.vyapar.ui.main

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vyapar.data.TransactionWithItems
import com.example.vyapar.printer.ThermalPrinterManager
import com.example.vyapar.utils.InvoiceShareUtility
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: DashboardViewModel,
    onEditTransaction: (TransactionWithItems) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.state.collectAsState()
    val context = LocalContext.current
    val printerManager = remember { ThermalPrinterManager(context) }
    val scope = rememberCoroutineScope()
    var selectedTxnForDelete by remember { mutableStateOf<Long?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    if (selectedTxnForDelete != null) {
        AlertDialog(
            onDismissRequest = { selectedTxnForDelete = null },
            title = { Text("Delete Transaction?", fontWeight = FontWeight.Bold) },
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

    val filteredTransactions = remember(uiState.recentTransactions, searchQuery) {
        uiState.recentTransactions.filter { txn ->
            if (searchQuery.isBlank()) {
                true
            } else {
                val query = searchQuery.lowercase()
                val matchId = txn.transaction.id.toString().contains(query)
                val matchItem = txn.items.any { it.itemName.lowercase().contains(query) }
                matchId || matchItem
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Transactions", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2563EB))
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF1F5F9))
        ) {
            // Search Bar Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by invoice or item...", color = Color(0xFF94A3B8), fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF64748B)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )
            }

            if (filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📋", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No transactions recorded" else "No matching transactions found",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTransactions, key = { it.transaction.id }) { txn ->
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
                                    scope.launch {
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
}
