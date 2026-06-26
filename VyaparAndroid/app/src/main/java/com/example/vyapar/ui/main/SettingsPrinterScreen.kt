package com.example.vyapar.ui.main

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vyapar.printer.BleScanResultItem
import com.example.vyapar.printer.ThermalPrinterManager

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPrinterScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val printerManager = remember { ThermalPrinterManager(context) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    var selectedPrinterMac by remember { mutableStateOf(viewModel.getSelectedPrinterAddress() ?: "") }
    var bleDevices by remember { mutableStateOf(emptyList<BleScanResultItem>()) }
    var isScanning by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bluetooth / Printer Settings", fontWeight = FontWeight.Bold, color = Color.White) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF1F5F9)),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Selected Printer", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                                        Text(selectedPrinterMac, fontSize = 11.sp, color = Color(0xFF64748B))
                                        Text("✅ Connected", color = Color(0xFF16A34A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = {
                                            printerManager.disconnect()
                                            selectedPrinterMac = ""
                                            viewModel.saveSelectedPrinterAddress(null)
                                            Toast.makeText(context, "Printer Disconnected", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Disconnect", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
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
                                        @SuppressLint("MissingPermission")
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
        }
    }
}
