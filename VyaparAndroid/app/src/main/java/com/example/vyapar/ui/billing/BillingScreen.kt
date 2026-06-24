package com.example.vyapar.ui.billing

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vyapar.data.*
import com.example.vyapar.printer.ThermalPrinterManager
import com.example.vyapar.ui.main.formatCurrency
import com.example.vyapar.ui.main.formatQuantity
import com.example.vyapar.utils.InvoiceShareUtility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BillLineItem(
    val code: String,
    val name: String,
    val quantity: Double,
    val rate: Double
) {
    val amount: Double get() = quantity * rate
}

class BillingViewModel(private val repository: DataRepository) : ViewModel() {
    val billItems = mutableStateListOf<BillLineItem>()
    val autocompleteQuery = MutableStateFlow("")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val autocompleteSuggestions: StateFlow<List<ItemEntity>> = autocompleteQuery
        .flatMapLatest { query ->
            repository.searchItemsFlow(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val grandTotal: Double
        get() = billItems.sumOf { it.amount }

    fun addLineItem(name: String, rate: Double, qty: Double) {
        viewModelScope.launch {
            // Find existing product code by name (case-insensitive)
            val allItems = repository.searchItemsFlow("").stateIn(viewModelScope).value
            val existing = allItems.find { it.name.lowercase() == name.lowercase() }
            val code = existing?.code ?: ("__new__" + System.currentTimeMillis())

            // Check if item already in bill list
            val existingIdx = billItems.indexOfFirst { it.name.lowercase() == name.lowercase() }
            if (existingIdx != -1) {
                val oldLine = billItems[existingIdx]
                billItems[existingIdx] = oldLine.copy(quantity = oldLine.quantity + qty, rate = rate)
            } else {
                billItems.add(BillLineItem(code, name, qty, rate))
            }
        }
    }

    fun removeLineItem(index: Int) {
        billItems.removeAt(index)
    }

    var editingTransactionId by mutableStateOf<Long?>(null)

    fun startEditing(txn: TransactionWithItems) {
        editingTransactionId = txn.transaction.id
        billItems.clear()
        txn.items.forEach { item ->
            billItems.add(
                BillLineItem(
                    code = item.itemCode,
                    name = item.itemName,
                    quantity = item.quantity,
                    rate = item.rate
                )
            )
        }
    }

    fun clearBill() {
        billItems.clear()
        editingTransactionId = null
    }

    suspend fun commitSaleAndGetInvoice(): TransactionWithItems? {
        if (billItems.isEmpty()) return null
        
        val date = System.currentTimeMillis()
        val total = grandTotal
        val transaction = TransactionEntity(
            date = date,
            total = total,
            grandTotal = total,
            type = "SALE"
        )

        val items = billItems.map { lineItem ->
            TransactionItemEntity(
                transactionId = editingTransactionId ?: 0L,
                itemCode = lineItem.code,
                itemName = lineItem.name,
                quantity = lineItem.quantity,
                rate = lineItem.rate,
                amount = lineItem.amount
            )
        }

        val txnId = editingTransactionId
        if (txnId != null) {
            repository.updateSale(txnId, transaction.copy(id = txnId), items)
        } else {
            repository.insertSale(transaction, items)
        }

        // Retrieve the saved transaction with generated ID
        val txns = repository.getTransactionsFlow().stateIn(viewModelScope).value
        val savedInvoice = txns.firstOrNull { 
            if (txnId != null) it.transaction.id == txnId
            else it.transaction.date == date && it.transaction.total == total 
        }
        
        // Clear bill list after saving
        clearBill()
        
        return savedInvoice
    }

    fun getBusinessProfile(): BusinessProfile = repository.getBusinessProfile()

    fun getSelectedPrinterAddress(): String? = repository.getSelectedPrinterAddress()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(
    viewModel: BillingViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val printerManager = remember { ThermalPrinterManager(context) }

    val suggestions by viewModel.autocompleteSuggestions.collectAsState()
    val grandTotal = viewModel.grandTotal

    var itemNameInput by remember { mutableStateOf("") }
    var rateInput by remember { mutableStateOf("") }
    var qtyInput by remember { mutableStateOf("1") }
    
    var showSuggestions by remember { mutableStateOf(false) }

    fun addCurrentItem() {
        val name = itemNameInput.trim()
        val rate = rateInput.toDoubleOrNull() ?: 0.0
        val qty = qtyInput.toDoubleOrNull() ?: 0.0

        if (name.isBlank()) {
            Toast.makeText(context, "Please enter item name", Toast.LENGTH_SHORT).show()
            return
        }
        if (rate <= 0.0) {
            Toast.makeText(context, "Please enter a valid price", Toast.LENGTH_SHORT).show()
            return
        }
        if (qty <= 0.0) {
            Toast.makeText(context, "Please enter a valid quantity", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.addLineItem(name, rate, qty)

        // Reset inputs
        itemNameInput = ""
        rateInput = ""
        qtyInput = "1"
        showSuggestions = false
    }

    var savedInvoiceForOptions by remember { mutableStateOf<TransactionWithItems?>(null) }

    if (savedInvoiceForOptions != null) {
        val invoice = savedInvoiceForOptions!!
        val profile = viewModel.getBusinessProfile()
        val printerAddress = viewModel.getSelectedPrinterAddress()
        
        AlertDialog(
            onDismissRequest = {
                savedInvoiceForOptions = null
                onBackClick()
            },
            title = { Text("Invoice Saved!", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Invoice #${invoice.transaction.id} for ${formatCurrency(invoice.transaction.grandTotal)} has been successfully saved.")
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            if (printerAddress != null) {
                                viewModel.viewModelScope.launch {
                                    Toast.makeText(context, "Connecting to printer...", Toast.LENGTH_SHORT).show()
                                    val printResult = printerManager.printInvoice(printerAddress, profile, invoice)
                                    if (printResult.isSuccess) {
                                        Toast.makeText(context, "Printed successfully!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Print failed: ${printResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Printer not paired. Configure printer in Settings.", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🖨️ Print Receipt")
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { InvoiceShareUtility.shareInvoiceAsImage(context, profile, invoice) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🖼️ Share PNG", fontSize = 12.sp)
                        }
                        
                        OutlinedButton(
                            onClick = { InvoiceShareUtility.shareInvoiceAsPdf(context, profile, invoice) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("📄 Share PDF", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        savedInvoiceForOptions = null
                        onBackClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Done")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.editingTransactionId != null) "Edit Tax Invoice #${viewModel.editingTransactionId}" else "New Tax Invoice", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (viewModel.billItems.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearBill() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear Bill", tint = Color(0xFFEF4444))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8FAFC))
        ) {
            // Sale Entry Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Item Name Input
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = itemNameInput,
                            onValueChange = {
                                itemNameInput = it
                                viewModel.autocompleteQuery.value = it
                                showSuggestions = it.isNotBlank()
                            },
                            label = { Text("Search or Enter Product Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Suggestions list overlays on top of content
                        if (showSuggestions && suggestions.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 66.dp)
                                    .heightIn(max = 200.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                LazyColumn {
                                    itemsIndexed(suggestions) { _, item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    itemNameInput = item.name
                                                    rateInput = item.salePrice.toString()
                                                    qtyInput = "1"
                                                    showSuggestions = false
                                                }
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text("Code: ${item.code}", fontSize = 11.sp, color = Color.Gray)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(formatCurrency(item.salePrice), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text("${formatQuantity(item.stock)} in stock", fontSize = 11.sp, color = Color(0xFF2563EB))
                                            }
                                        }
                                        HorizontalDivider(color = Color(0xFFF1F5F9))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = rateInput,
                            onValueChange = { rateInput = it },
                            label = { Text("Rate (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1.2f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        OutlinedTextField(
                            value = qtyInput,
                            onValueChange = { qtyInput = it },
                            label = { Text("Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.8f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { addCurrentItem() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add Item to Bill", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Items Table Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Items in Bill: ${viewModel.billItems.size}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Text(
                    text = "Total: " + formatCurrency(grandTotal),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF16A34A)
                )
            }

            // Line Items List
            if (viewModel.billItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No items added yet", color = Color(0xFF94A3B8), fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    itemsIndexed(viewModel.billItems) { index, item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray,
                                    modifier = Modifier.width(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text = "${formatQuantity(item.quantity)} x ${formatCurrency(item.rate)}",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                                Text(
                                    text = formatCurrency(item.amount),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF0F172A),
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                IconButton(onClick = { viewModel.removeLineItem(index) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove",
                                        tint = Color(0xFFEF4444).copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Action Bar
            if (viewModel.billItems.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = Color.White
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                viewModel.viewModelScope.launch {
                                    val invoice = viewModel.commitSaleAndGetInvoice()
                                    if (invoice != null) {
                                        savedInvoiceForOptions = invoice
                                    } else {
                                        Toast.makeText(context, "Error saving invoice", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save Invoice", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}
