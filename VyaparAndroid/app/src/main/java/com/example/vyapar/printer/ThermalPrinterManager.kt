package com.example.vyapar.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.vyapar.data.BusinessProfile
import com.example.vyapar.data.TransactionWithItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

data class BleScanResultItem(
    val name: String,
    val address: String,
    val device: BluetoothDevice
)

class ThermalPrinterManager(private val context: Context) {

    companion object {
        private const val TAG = "BLEPrinter"
        private const val SCAN_TIMEOUT_MS = 10_000L // 10 seconds
        private const val CHUNK_SIZE = 64 // Smaller chunks to avoid BLE buffer overflow (red light / stutter)
        private const val CHUNK_DELAY_MS = 150L // Generous delay between chunks to prevent printer buffer overrun

        // Common BLE thermal printer service/characteristic UUIDs
        private val KNOWN_PRINTER_SERVICE_UUIDS = listOf(
            UUID.fromString("0000ae30-0000-1000-8000-00805f9b34fb"), // F2C / common Chinese printers
            UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"), // Common Chinese printers (FFE0)
            UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455"), // ISSC Printer
            UUID.fromString("000018f0-0000-1000-8000-00805f9b34fb"), // Generic thermal
            UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb"), // Alt generic
            UUID.fromString("e7810a71-73ae-499d-8c15-faa9aef0c3f2")  // Nordic UART
        )
        private val KNOWN_WRITE_CHAR_UUIDS = listOf(
            UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb"), // F2C write char
            UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"), // FFE1 write char
            UUID.fromString("49535343-114d-40d6-b403-b830532b2e55"), // ISSC write char
            UUID.fromString("00002af1-0000-1000-8000-00805f9b34fb"), // Generic write
            UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb"), // Alt write
            UUID.fromString("bef8d6c9-9c21-4c9e-b632-bd58c1009f9f")  // Nordic TX
        )
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner

    private var scanCallback: ScanCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private var activeGatt: BluetoothGatt? = null

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun hasBluetoothPermission(): Boolean {
        val locationGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            locationGranted
        } else {
            locationGranted
        }
    }

    fun isLocationServiceEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        return locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
    }

    // ─── BLE Scanning ───────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun scanBleDevices(onResult: (List<BleScanResultItem>) -> Unit) {
        val scanner = bleScanner
        if (scanner == null) {
            onResult(emptyList())
            return
        }

        val discoveredDevices = mutableMapOf<String, BleScanResultItem>()

        // Stop any previous scan
        stopScan()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val address = device.address ?: return
                val rawName = device.name ?: result.scanRecord?.deviceName
                val name = rawName ?: "Unknown BLE Device"
                
                val existing = discoveredDevices[address]
                if (existing == null || (existing.name.startsWith("Unknown") && rawName != null)) {
                    discoveredDevices[address] = BleScanResultItem(name, address, device)
                    Log.d(TAG, "BLE Device found: $name ($address)")
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE Scan failed with error code: $errorCode")
                onResult(emptyList())
            }
        }

        scanner.startScan(scanCallback)
        Log.d(TAG, "BLE Scan started")

        // Auto-stop after timeout
        handler.postDelayed({
            stopScan()
            onResult(discoveredDevices.values.toList())
        }, SCAN_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanCallback?.let { callback ->
            try {
                bleScanner?.stopScan(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping scan: ${e.message}")
            }
        }
        scanCallback = null
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            activeGatt?.disconnect()
            activeGatt?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error during manual disconnect: ${e.message}")
        } finally {
            activeGatt = null
        }
    }

    // ─── BLE Printing via GATT ──────────────────────────────────────────

    @SuppressLint("MissingPermission")
    suspend fun printInvoice(
        deviceAddress: String,
        profile: BusinessProfile,
        invoice: TransactionWithItems
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (bluetoothAdapter == null) {
            return@withContext Result.failure(Exception("Bluetooth not supported"))
        }

        val device = try {
            bluetoothAdapter.getRemoteDevice(deviceAddress)
        } catch (e: IllegalArgumentException) {
            return@withContext Result.failure(Exception("Invalid MAC Address: $deviceAddress"))
        }

        val receiptBytes = compileReceiptBytes(profile, invoice)

        try {
            val result = connectAndPrint(device, receiptBytes)
            result
        } catch (e: Exception) {
            Log.e(TAG, "BLE Print failed", e)
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectAndPrint(
        device: BluetoothDevice,
        data: ByteArray
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        var gatt: BluetoothGatt? = null
        var resumed = false

        fun safeResume(result: Result<Unit>) {
            if (!resumed) {
                resumed = true
                try {
                    gatt?.close()
                    if (activeGatt == gatt) {
                        activeGatt = null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing GATT: ${e.message}")
                }
                continuation.resume(result)
            }
        }

        // Timeout: if nothing happens within 30 seconds, fail
        val timeoutRunnable = Runnable {
            safeResume(Result.failure(Exception("BLE connection timed out")))
        }
        handler.postDelayed(timeoutRunnable, 30_000L)

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "GATT connected, requesting MTU...")
                    val mtuSuccess = g.requestMtu(128)
                    if (!mtuSuccess) {
                        Log.w(TAG, "Request MTU failed, starting service discovery directly")
                        g.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "GATT disconnected")
                    if (!resumed) {
                        safeResume(Result.failure(Exception("Printer disconnected unexpectedly")))
                    }
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                Log.d(TAG, "MTU changed to $mtu, status=$status. Discovering services...")
                g.discoverServices()
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    safeResume(Result.failure(Exception("Service discovery failed (status $status)")))
                    return
                }

                Log.d(TAG, "Services discovered: ${g.services.map { it.uuid }}")

                // Find writable characteristic
                val writeChar = findWritableCharacteristic(g)
                if (writeChar == null) {
                    val allServices = g.services.joinToString("\n") { svc ->
                        "Service: ${svc.uuid}\n" + svc.characteristics.joinToString("\n") { ch ->
                            "  Char: ${ch.uuid} props=${ch.properties}"
                        }
                    }
                    Log.e(TAG, "No writable characteristic found. Available:\n$allServices")
                    safeResume(Result.failure(Exception("No writable printer characteristic found. Ensure printer is a BLE thermal printer.")))
                    return
                }

                Log.d(TAG, "Using write characteristic: ${writeChar.uuid}")

                // Write data in chunks on a background thread
                Thread {
                    try {
                        writeDataInChunks(g, writeChar, data)
                        handler.removeCallbacks(timeoutRunnable)
                        // Small delay to let the printer finish
                        Thread.sleep(500)
                        safeResume(Result.success(Unit))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error writing to printer", e)
                        handler.removeCallbacks(timeoutRunnable)
                        safeResume(Result.failure(e))
                    }
                }.start()
            }
        }

        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        activeGatt = gatt

        continuation.invokeOnCancellation {
            handler.removeCallbacks(timeoutRunnable)
            try {
                gatt?.close()
                if (activeGatt == gatt) {
                    activeGatt = null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error closing GATT on cancel: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun findWritableCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
        // 1. Try known printer service/char UUIDs first
        for (serviceUuid in KNOWN_PRINTER_SERVICE_UUIDS) {
            val service = gatt.getService(serviceUuid)
            if (service != null) {
                for (charUuid in KNOWN_WRITE_CHAR_UUIDS) {
                    val char = service.getCharacteristic(charUuid)
                    if (char != null && isWritable(char)) {
                        return char
                    }
                }
                // Fallback: find any writable char in this known service
                for (char in service.characteristics) {
                    if (isWritable(char)) return char
                }
            }
        }

        // 2. Scan all services for any writable characteristic, excluding standard non-printer services
        val ignoredServices = setOf(
            UUID.fromString("00001800-0000-1000-8000-00805f9b34fb"), // Generic Access
            UUID.fromString("00001801-0000-1000-8000-00805f9b34fb"), // Generic Attribute
            UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb"), // Device Information
            UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")  // Battery Service
        )

        for (service in gatt.services) {
            if (ignoredServices.contains(service.uuid)) continue
            for (char in service.characteristics) {
                if (isWritable(char)) return char
            }
        }

        return null
    }

    private fun isWritable(char: BluetoothGattCharacteristic): Boolean {
        val props = char.properties
        return (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
               (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
    }

    @SuppressLint("MissingPermission")
    private fun writeDataInChunks(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray
    ) {
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + CHUNK_SIZE, data.size)
            val chunk = data.copyOfRange(offset, end)

            characteristic.value = chunk
            // Always use WRITE_TYPE_DEFAULT (with response/ACK) for reliable printing
            // WRITE_NO_RESPONSE can cause buffer overflow on thermal printers
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

            val success = gatt.writeCharacteristic(characteristic)
            if (!success) {
                throw Exception("Failed to write chunk at offset $offset")
            }

            offset = end
            // Small delay to avoid overwhelming the BLE buffer
            Thread.sleep(CHUNK_DELAY_MS)
        }
        Log.d(TAG, "Successfully wrote ${data.size} bytes in ${(data.size + CHUNK_SIZE - 1) / CHUNK_SIZE} chunks")
    }

    // ─── ESC/POS Receipt Formatting ─────────────────────────────────────

    private fun compileReceiptBytes(profile: BusinessProfile, invoice: TransactionWithItems): ByteArray {
        val bytes = mutableListOf<Byte>()

        // Commands
        val init = byteArrayOf(0x1B, 0x40) // Initialize
        val alignCenter = byteArrayOf(0x1B, 0x61, 0x01)
        val alignLeft = byteArrayOf(0x1B, 0x61, 0x00)
        val alignRight = byteArrayOf(0x1B, 0x61, 0x02)
        val boldOn = byteArrayOf(0x1B, 0x45, 0x01)
        val boldOff = byteArrayOf(0x1B, 0x45, 0x00)
        val sizeDouble = byteArrayOf(0x1D, 0x21, 0x11)
        val sizeNormal = byteArrayOf(0x1D, 0x21, 0x00)

        fun writeBytes(arr: ByteArray) {
            bytes.addAll(arr.toList())
        }

        fun writeText(text: String) {
            bytes.addAll(text.toByteArray(Charsets.US_ASCII).toList())
        }

        // Initialize
        writeBytes(init)

        // Business Name
        writeBytes(alignCenter)
        writeBytes(sizeDouble)
        writeBytes(boldOn)
        writeText("${profile.name}\n")
        writeBytes(sizeNormal)
        writeBytes(boldOff)

        // Phone & Address
        if (profile.phone.isNotBlank()) {
            writeText("Tel: ${profile.phone}\n")
        }
        if (profile.address.isNotBlank()) {
            writeText("${profile.address}\n")
        }

        // Divider
        writeBytes(alignLeft)
        writeText("--------------------------------\n")

        // Invoice metadata
        val sdf = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
        val dateStr = sdf.format(Date(invoice.transaction.date))
        writeText("Invoice #: ${invoice.transaction.id}\n")
        writeText("Date: $dateStr\n")
        writeText("Payment: ${if (invoice.transaction.isPaid) "Paid" else "Unpaid"}\n")
        writeText("--------------------------------\n")

        // Column Headers
        writeBytes(boldOn)
        writeText(formatRow("Item Description", "Amount"))
        writeText("\n")
        writeBytes(boldOff)
        writeText("--------------------------------\n")

        // Items list
        for (item in invoice.items) {
            // Print item name
            writeText("${item.itemName}\n")
            // Print rate x qty and subtotal
            val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else String.format("%.2f", item.quantity)
            val rateStr = String.format("%.2f", item.rate)
            val amtStr = String.format("%.2f", item.amount)
            val detailRow = formatRow("  $qtyStr x $rateStr", amtStr)
            writeText("$detailRow\n")
        }

        writeText("--------------------------------\n")

        // Total Summary
        val totalLabel = "TOTAL"
        val totalVal = String.format("%.2f", invoice.transaction.total)
        writeBytes(boldOn)
        writeText(formatRow(totalLabel, totalVal))
        writeText("\n")
        writeBytes(boldOff)

        if (invoice.transaction.discount > 0.0) {
            val discLabel = "Discount"
            val discVal = String.format("-%.2f", invoice.transaction.discount)
            writeText(formatRow(discLabel, discVal))
            writeText("\n")
            val grandLabel = "GRAND TOTAL"
            val grandVal = String.format("%.2f", invoice.transaction.grandTotal)
            writeBytes(boldOn)
            writeText(formatRow(grandLabel, grandVal))
            writeText("\n")
            writeBytes(boldOff)
        }

        writeText("--------------------------------\n")

        // Footer
        writeBytes(alignCenter)
        writeText("Thank you for your purchase!\n")
        writeText("Please visit again.\n")

        // Feed paper
        writeBytes(byteArrayOf(0x0A, 0x0A, 0x0A, 0x0A)) // 4 Line Feeds (0x0A)
        // Cut paper
        writeBytes(byteArrayOf(0x1D, 0x56, 0x42, 0x00)) // Cut paper (using 0x1D, 0x56, 0x42, 0x00)

        return bytes.toByteArray()
    }

    private fun formatRow(left: String, right: String, limit: Int = 32): String {
        val totalLen = left.length + right.length
        if (totalLen >= limit) {
            val leftLimit = limit - right.length - 1
            if (leftLimit > 0) {
                return left.substring(0, leftLimit) + " " + right
            }
            return left.substring(0, limit)
        }
        val spaces = limit - totalLen
        return left + "".padEnd(spaces) + right
    }
}
