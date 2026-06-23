package com.example.vyapar.ui.main

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
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
            val list = repository.getTransactionsFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList()).value
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

    val businessProfile = remember { viewModel.getBusinessProfile() }
    var bizName by remember { mutableStateOf(businessProfile.name) }
    var bizPhone by remember { mutableStateOf(businessProfile.phone) }
    var bizAddress by remember { mutableStateOf(businessProfile.address) }

    var selectedPrinterMac by remember { mutableStateOf(viewModel.getSelectedPrinterAddress() ?: "") }
    var pairedDevices by remember { mutableStateOf(emptyList<BluetoothDevice>()) }

    var showResetConfirm by remember { mutableStateOf(false) }

    // Load paired devices if permission exists
    LaunchedEffect(key1 = true) {
        if (printerManager.hasBluetoothPermission()) {
            pairedDevices = printerManager.getPairedDevices()
        }
    }

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

        // Section: Bluetooth Printer Setup
        item {
            Text("Bluetooth Thermal Printer", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
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
                        Text("Bluetooth permission is not granted. Please allow permissions in settings.", color = Color(0xFFEF4444), fontSize = 13.sp)
                    } else {
                        Text(
                            text = "Select Paired Printer Device (F2C / POS / MTP):",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (pairedDevices.isEmpty()) {
                            Text("No paired bluetooth devices found. Pair the thermal printer in your phone Bluetooth settings first.", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        } else {
                            pairedDevices.forEach { device ->
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
                                        Text(device.name ?: "Unknown Device", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(device.address, fontSize = 11.sp, color = Color.Gray)
                                    }
                                    if (isSelected) {
                                        Text("Selected", color = Color(0xFF2563EB), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
