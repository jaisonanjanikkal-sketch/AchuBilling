package com.example.vyapar.ui.main

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vyapar.data.DataRepository
import com.example.vyapar.data.TransactionWithItems
import com.example.vyapar.printer.ThermalPrinterManager
import com.example.vyapar.utils.InvoiceShareUtility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryViewModel(private val repository: DataRepository) : ViewModel() {
    val searchQuery = MutableStateFlow("")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val transactionsState: StateFlow<List<TransactionWithItems>> = searchQuery
        .flatMapLatest { query ->
            repository.getTransactionsFlow().map { list ->
                if (query.isBlank()) {
                    list
                } else {
                    list.filter { txn ->
                        txn.transaction.id.toString().contains(query) ||
                        txn.items.any { item -> item.itemName.contains(query, ignoreCase = true) }
                    }
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
        }
    }

    fun getBusinessProfile() = repository.getBusinessProfile()
    fun getSelectedPrinterAddress() = repository.getSelectedPrinterAddress()
}

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier
) {
    val txnsList by viewModel.transactionsState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val context = LocalContext.current

    var selectedTxn by remember { mutableStateOf<TransactionWithItems?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val printerManager = remember { ThermalPrinterManager(context) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
        ) {
            // Search Bar Area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = { Text("Search by invoice ID or item name...", color = Color(0xFF94A3B8), fontSize = 14.sp) },
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

            // Transactions list
            if (txnsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No invoices found for your search" else "No invoices found yet",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(txnsList, key = { it.transaction.id }) { txn ->
                        TransactionListItem(
                            txn = txn,
                            onClick = { selectedTxn = txn },
                            onShareClick = {
                                val profile = viewModel.getBusinessProfile()
                                InvoiceShareUtility.shareInvoiceAsImage(context, profile, txn)
                            }
                        )
                    }
                }
            }
        }
    }

    // Invoice Detail Dialog
    if (selectedTxn != null) {
        val invoice = selectedTxn!!
        AlertDialog(
            onDismissRequest = { selectedTxn = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tax Invoice #${invoice.transaction.id}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Invoice", tint = Color(0xFFEF4444))
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    val sdf = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
                    val dateStr = sdf.format(Date(invoice.transaction.date))
                    
                    Text("Date: $dateStr", fontSize = 13.sp, color = Color.Gray)
                    Text("Payment: Cash (Paid in Full)", fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Header row
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Item Details", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1.5f))
                        Text("Qty", fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.5f))
                        Text("Amount", fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(invoice.items) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1.5f)) {
                                    Text(item.itemName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text("@ " + formatCurrency(item.rate), fontSize = 11.sp, color = Color.Gray)
                                }
                                Text(
                                    text = formatQuantity(item.quantity),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(0.5f)
                                )
                                Text(
                                    text = formatCurrency(item.amount),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("GRAND TOTAL", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color(0xFF16A34A))
                        Text(formatCurrency(invoice.transaction.grandTotal), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color(0xFF16A34A))
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    // Share Image
                    TextButton(
                        onClick = {
                            val profile = viewModel.getBusinessProfile()
                            InvoiceShareUtility.shareInvoiceAsImage(context, profile, invoice)
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    // Print
                    TextButton(
                        onClick = {
                            viewModel.viewModelScope.launch {
                                val printerAddress = viewModel.getSelectedPrinterAddress()
                                val profile = viewModel.getBusinessProfile()

                                if (printerAddress.isNullOrBlank()) {
                                    Toast.makeText(context, "Please set a printer in Settings first", Toast.LENGTH_LONG).show()
                                } else if (!printerManager.hasBluetoothPermission()) {
                                    Toast.makeText(context, "Bluetooth permissions are required for printing.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Connecting to printer...", Toast.LENGTH_SHORT).show()
                                    val printResult = printerManager.printInvoice(printerAddress, profile, invoice)
                                    if (printResult.isSuccess) {
                                        Toast.makeText(context, "Printed successfully!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Print failed: ${printResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Print", fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    TextButton(onClick = { selectedTxn = null }) {
                        Text("Close", color = Color(0xFF64748B))
                    }
                }
            }
        )
    }

    if (showDeleteConfirm && selectedTxn != null) {
        val invoiceId = selectedTxn!!.transaction.id
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Invoice?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete invoice #$invoiceId. Stock levels of the items in this invoice will be restored in the inventory.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTransaction(invoiceId)
                        showDeleteConfirm = false
                        selectedTxn = null
                        Toast.makeText(context, "Invoice #$invoiceId Deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            }
        )
    }
}

@Composable
fun TransactionListItem(
    txn: TransactionWithItems,
    onClick: () -> Unit,
    onShareClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFDBEAFE), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("#${txn.transaction.id}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2563EB))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val sdf = SimpleDateFormat("dd-MMM-yyyy, hh:mm a", Locale.getDefault())
                val timeStr = sdf.format(Date(txn.transaction.date))
                Text(text = "Cash Sale", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text(
                    text = "$timeStr · ${txn.items.size} item${if (txn.items.size != 1) "s" else ""}",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            Text(
                text = formatCurrency(txn.transaction.grandTotal),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF16A34A)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { onShareClick() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
