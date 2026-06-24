package com.example.vyapar.ui.main

import android.annotation.SuppressLint
import android.os.Build
import com.example.vyapar.printer.BleScanResultItem
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vyapar.data.BusinessProfile
import com.example.vyapar.data.DataRepository
import com.example.vyapar.data.ItemEntity
import com.example.vyapar.data.TransactionEntity
import com.example.vyapar.data.TransactionItemEntity
import com.example.vyapar.printer.ThermalPrinterManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File

class SettingsViewModel(private val repository: DataRepository) : ViewModel() {
    fun getBusinessProfile(): BusinessProfile = repository.getBusinessProfile()

    fun saveBusinessProfile(profile: BusinessProfile) {
        repository.saveBusinessProfile(profile)
    }

    fun getSelectedPrinterAddress(): String? = repository.getSelectedPrinterAddress()

    fun saveSelectedPrinterAddress(address: String?) {
        repository.saveSelectedPrinterAddress(address)
    }

    fun loadDemoData() {
        viewModelScope.launch {
            // Pre-add demo items
            val demoItems = listOf(
                ItemEntity("20,20 BISC", "BISCUITS (20-20)", 10.0, 7.0, 150.0),
                ItemEntity("PARLE-G", "PARLE-G GLUCOSE", 5.0, 3.5, 200.0),
                ItemEntity("MAGGI-2M", "MAGGI 2-MIN NOODLES", 14.0, 11.0, 80.0),
                ItemEntity("THUMPS-500", "THUMS UP 500ML", 40.0, 32.0, 60.0),
                ItemEntity("COCA-CAN", "COCA COLA CAN 300ML", 35.0, 28.0, 45.0),
                ItemEntity("DAIRY-MLK", "DAIRY MILK SILK", 80.0, 65.0, 30.0),
                ItemEntity("LUX-SOAP", "LUX BEAUTY SOAP", 45.0, 35.0, 70.0),
                ItemEntity("SURF-1KG", "SURF EXCEL 1KG", 120.0, 95.0, 25.0)
            )
            for (item in demoItems) {
                if (repository.getItemByCode(item.code) == null) {
                    repository.insertItem(item)
                }
            }

            // Pre-add mock transactions if none exist
            val list = withContext(Dispatchers.IO) {
                repository.getTransactionsFlow().first()
            }
            if (list.isEmpty()) {
                val mockTxn1 = TransactionEntity(
                    date = System.currentTimeMillis() - 86400000L, // 1 day ago
                    total = 140.0,
                    grandTotal = 140.0
                )
                val mockItems1 = listOf(
                    TransactionItemEntity(transactionId = 0L, itemCode = "PARLE-G", itemName = "PARLE-G GLUCOSE", quantity = 10.0, rate = 5.0, amount = 50.0),
                    TransactionItemEntity(transactionId = 0L, itemCode = "MAGGI-2M", itemName = "MAGGI 2-MIN NOODLES", quantity = 5.0, rate = 14.0, amount = 70.0),
                    TransactionItemEntity(transactionId = 0L, itemCode = "20,20 BISC", itemName = "BISCUITS (20-20)", quantity = 2.0, rate = 10.0, amount = 20.0)
                )
                repository.insertSale(mockTxn1, mockItems1)

                val mockTxn2 = TransactionEntity(
                    date = System.currentTimeMillis(),
                    total = 200.0,
                    grandTotal = 200.0
                )
                val mockItems2 = listOf(
                    TransactionItemEntity(transactionId = 0L, itemCode = "DAIRY-MLK", itemName = "DAIRY MILK SILK", quantity = 2.0, rate = 80.0, amount = 160.0),
                    TransactionItemEntity(transactionId = 0L, itemCode = "THUMPS-500", itemName = "THUMS UP 500ML", quantity = 1.0, rate = 40.0, amount = 40.0)
                )
                repository.insertSale(mockTxn2, mockItems2)
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
        }
    }

    fun exportBackup(context: Context, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val items = repository.getItemsFlow().first()
                val transactionsWithItems = repository.getTransactionsFlow().first()
                val profile = repository.getBusinessProfile()

                val root = org.json.JSONObject()

                val bizObj = org.json.JSONObject().apply {
                    put("name", profile.name)
                    put("phone", profile.phone)
                    put("address", profile.address)
                }
                root.put("business", bizObj)

                val itemsArr = org.json.JSONArray()
                for (item in items) {
                    val itemObj = org.json.JSONObject().apply {
                        put("code", item.code)
                        put("name", item.name)
                        put("salePrice", item.salePrice)
                        put("purchasePrice", item.purchasePrice)
                        put("stock", item.stock)
                    }
                    itemsArr.put(itemObj)
                }
                root.put("items", itemsArr)

                val txnsArr = org.json.JSONArray()
                val lineItemsArr = org.json.JSONArray()

                for (txnWithItems in transactionsWithItems) {
                    val txn = txnWithItems.transaction
                    val txnObj = org.json.JSONObject().apply {
                        put("id", txn.id)
                        put("date", txn.date)
                        put("total", txn.total)
                        put("discount", txn.discount)
                        put("grandTotal", txn.grandTotal)
                        put("type", txn.type)
                        put("isPaid", txn.isPaid)
                    }
                    txnsArr.put(txnObj)

                    for (item in txnWithItems.items) {
                        val liObj = org.json.JSONObject().apply {
                            put("id", item.id)
                            put("transactionId", item.transactionId)
                            put("itemCode", item.itemCode)
                            put("itemName", item.itemName)
                            put("quantity", item.quantity)
                            put("rate", item.rate)
                            put("amount", item.amount)
                        }
                        lineItemsArr.put(liObj)
                    }
                }
                root.put("transactions", txnsArr)
                root.put("transactionItems", lineItemsArr)

                val file = File(context.cacheDir, "vyapar_backup_${System.currentTimeMillis()}.json")
                file.writeText(root.toString(2))

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = android.content.ClipData.newRawUri("", uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Vyapar Backup")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(intent, "Share Backup File")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)

                withContext(Dispatchers.Main) {
                    onResult(true, "Backup file generated!")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Export failed: ${e.message}")
                }
            }
        }
    }

    fun importBackup(context: Context, uri: android.net.Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                }
                if (jsonString == null) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Failed to read file contents")
                    }
                    return@launch
                }

                val root = org.json.JSONObject(jsonString)
                if (!root.has("items") && !root.has("transactions") && !root.has("business")) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Invalid backup file structure")
                    }
                    return@launch
                }

                val bizProfile = if (root.has("business")) {
                    val bizObj = root.getJSONObject("business")
                    BusinessProfile(
                        name = bizObj.optString("name", "My Business"),
                        phone = bizObj.optString("phone", ""),
                        address = bizObj.optString("address", "")
                    )
                } else {
                    BusinessProfile("My Business", "", "")
                }

                val itemsList = mutableListOf<ItemEntity>()
                if (root.has("items")) {
                    val arr = root.getJSONArray("items")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val code = obj.optString("code")
                        if (code.isNullOrBlank()) continue
                        val name = obj.optString("name", "")
                        val salePrice = obj.optDouble("salePrice", obj.optDouble("sale_price", 0.0))
                        val purchasePrice = obj.optDouble("purchasePrice", obj.optDouble("purchase_price", 0.0))
                        val stock = obj.optDouble("stock", 0.0)
                        itemsList.add(ItemEntity(code, name, salePrice, purchasePrice, stock))
                    }
                }

                val txnsList = mutableListOf<TransactionEntity>()
                if (root.has("transactions")) {
                    val arr = root.getJSONArray("transactions")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optLong("id", 0L)
                        val date = obj.optLong("date", System.currentTimeMillis())
                        val total = obj.optDouble("total", 0.0)
                        val grandTotal = obj.optDouble("grandTotal", obj.optDouble("grand_total", total))
                        val discount = obj.optDouble("discount", 0.0)
                        val type = obj.optString("type", "SALE")
                        val isPaid = obj.optBoolean("isPaid", true)
                        txnsList.add(TransactionEntity(id = id, date = date, total = total, grandTotal = grandTotal, discount = discount, type = type, isPaid = isPaid))
                    }
                }

                val txnItemsList = mutableListOf<TransactionItemEntity>()
                if (root.has("transactionItems")) {
                    val arr = root.getJSONArray("transactionItems")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optLong("id", 0L)
                        val txnId = obj.optLong("transactionId", obj.optLong("transaction_id", 0L))
                        val itemCode = obj.optString("itemCode", obj.optString("item_code", ""))
                        val itemName = obj.optString("itemName", obj.optString("item_name", ""))
                        val quantity = obj.optDouble("quantity", 0.0)
                        val rate = obj.optDouble("rate", 0.0)
                        val amount = obj.optDouble("amount", 0.0)
                        txnItemsList.add(TransactionItemEntity(id = id, transactionId = txnId, itemCode = itemCode, itemName = itemName, quantity = quantity, rate = rate, amount = amount))
                    }
                }

                repository.restoreBackup(bizProfile, itemsList, txnsList, txnItemsList)
                withContext(Dispatchers.Main) {
                    onResult(true, "Backup restored successfully!")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult(false, "Import failed: ${e.message}")
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val printerManager = remember { ThermalPrinterManager(context) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackup(context, uri) { success, message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    val businessProfile = remember { viewModel.getBusinessProfile() }
    var bizName by remember { mutableStateOf(businessProfile.name) }
    var bizPhone by remember { mutableStateOf(businessProfile.phone) }
    var bizAddress by remember { mutableStateOf(businessProfile.address) }

    var selectedPrinterMac by remember { mutableStateOf(viewModel.getSelectedPrinterAddress() ?: "") }
    var bleDevices by remember { mutableStateOf(emptyList<BleScanResultItem>()) }
    var isScanning by remember { mutableStateOf(false) }

    var showResetConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)),
        contentPadding = PaddingValues(16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Business Profile
        item {
            Text("Business Profile", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = bizName,
                        onValueChange = { bizName = it },
                        label = { Text("Business Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = bizPhone,
                        onValueChange = { bizPhone = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = bizAddress,
                        onValueChange = { bizAddress = it },
                        label = { Text("Business Address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            viewModel.saveBusinessProfile(BusinessProfile(bizName.trim(), bizPhone.trim(), bizAddress.trim()))
                            Toast.makeText(context, "Business Profile Saved!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Profile", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section: BLE Thermal Printer Setup
        item {
            Text("BLE Thermal Printer", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (!printerManager.isBluetoothSupported()) {
                        Text("Bluetooth is not supported on this device.", color = Color(0xFFEF4444), fontSize = 13.sp)
                    } else if (!printerManager.isBluetoothEnabled()) {
                        Text("Please enable Bluetooth on your phone to connect the printer.", color = Color(0xFFD97706), fontSize = 13.sp)
                    } else if (!printerManager.hasBluetoothPermission()) {
                        Column {
                            Text("Bluetooth & Location permissions are required for scanning.", color = Color(0xFFEF4444), fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        arrayOf(
                                            android.Manifest.permission.BLUETOOTH_SCAN,
                                            android.Manifest.permission.BLUETOOTH_CONNECT,
                                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    } else {
                                        arrayOf(
                                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    }
                                    permissionsLauncher.launch(permissions)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Grant Permissions", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (!printerManager.isLocationServiceEnabled()) {
                        Column {
                            Text("Location services (GPS) must be turned ON to scan for BLE printers.", color = Color(0xFFD97706), fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Turn On Location", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Current selection display
                        if (selectedPrinterMac.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFDBEAFE), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Selected Printer", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                                    Text(selectedPrinterMac, fontSize = 11.sp, color = Color(0xFF64748B))
                                }
                                Text("✅ Connected", color = Color(0xFF16A34A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Scan button
                        Button(
                            onClick = {
                                if (!isScanning) {
                                    isScanning = true
                                    bleDevices = emptyList()
                                    printerManager.scanBleDevices { results ->
                                        bleDevices = results
                                        isScanning = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isScanning) Color(0xFF94A3B8) else Color(0xFF2563EB)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isScanning
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scanning for BLE printers...", fontWeight = FontWeight.Bold)
                            } else {
                                Text("🔍 Scan for BLE Printers", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Scanned device list
                        if (bleDevices.isEmpty() && !isScanning) {
                            Text(
                                "Turn on your BLE thermal printer (F2C / POS) and tap 'Scan' above.",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        } else {
                            bleDevices.forEach { device ->
                                val isSelected = device.address == selectedPrinterMac
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelected) Color(0xFFDBEAFE) else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            selectedPrinterMac = device.address
                                            viewModel.saveSelectedPrinterAddress(device.address)
                                            Toast.makeText(context, "Printer Selected: ${device.name}", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(device.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                                        Text(device.address, fontSize = 11.sp, color = Color.Gray)
                                    }
                                    if (isSelected) {
                                        Text("✅ Selected", color = Color(0xFF2563EB), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Data & Utilities
        item {
            Text("Data Management", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Load demo data
                    Button(
                        onClick = {
                            viewModel.loadDemoData()
                            Toast.makeText(context, "Demo Data Loaded successfully!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("📥 Load Demo Data", fontWeight = FontWeight.Bold)
                    }

                    // Export Backup
                    Button(
                        onClick = {
                            viewModel.exportBackup(context) { success, message ->
                                if (!success) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("📤 Export Backup JSON", fontWeight = FontWeight.Bold)
                    }

                    // Import Backup
                    Button(
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("📥 Import Backup JSON", fontWeight = FontWeight.Bold)
                    }

                    // Reset Data
                    Button(
                        onClick = { showResetConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🗑️ Reset All App Data", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset All Data?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete all inventory items and transaction billing history. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showResetConfirm = false
                        Toast.makeText(context, "All data has been reset", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            }
        )
    }
}
