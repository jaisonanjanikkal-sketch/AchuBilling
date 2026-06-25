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
        private const val CHUNK_SIZE = 20 // BLE MTU default write chunk
        private const val CHUNK_DELAY_MS = 50L // delay between chunks

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
                    Log.d(TAG, "GATT connected, discovering services...")
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "GATT disconnected")
                    if (!resumed) {
                        safeResume(Result.failure(Exception("Printer disconnected unexpectedly")))
                    }
                }
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
            // Use WRITE_NO_RESPONSE if supported for speed, else standard WRITE
            characteristic.writeType =
                if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                else
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

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
        val W = 32 // 58mm thermal = 32 chars per line

        // Commands
        val init = byteArrayOf(0x1B, 0x40) // Initialize
        val alignCenter = byteArrayOf(0x1B, 0x61, 0x01)
        val alignLeft = byteArrayOf(0x1B, 0x61, 0x00)
        val alignRight = byteArrayOf(0x1B, 0x61, 0x02)
        val boldOn = byteArrayOf(0x1B, 0x45, 0x01)
        val boldOff = byteArrayOf(0x1B, 0x45, 0x00)
        val sizeDouble = byteArrayOf(0x1D, 0x21, 0x11)
        val sizeNormal = byteArrayOf(0x1D, 0x21, 0x00)

        fun esc(arr: ByteArray) {
            bytes.addAll(arr.toList())
        }

        fun line(text: String) {
            bytes.addAll(text.toByteArray(Charsets.UTF_8).toList())
            bytes.add(0x0A)
        }

        fun blankLine() {
            bytes.add(0x0A)
        }

        fun dashes() {
            line("-".repeat(W))
        }

        // Format number: no trailing zeros, but keep meaningful decimals
        fun n(v: Double): String {
            if (v == Math.floor(v) && !v.isInfinite()) return v.toLong().toString()
            val s = String.format(Locale.US, "%.2f", v)
            return if (s.endsWith("0") && !s.endsWith(".0")) s.trimEnd('0') else s
        }

        // Build a 32-char line with left text and right text
        fun leftRight(left: String, right: String): String {
            val gap = W - left.length - right.length
            return if (gap > 0) left + " ".repeat(gap) + right else "$left $right"
        }

        // Initialize printer
        esc(init)

        // Business Name & details (optional, but standard for POS bills)
        if (profile.name.isNotBlank()) {
            esc(alignCenter)
            esc(sizeDouble)
            esc(boldOn)
            line(profile.name)
            esc(sizeNormal)
            esc(boldOff)

            if (profile.phone.isNotBlank()) {
                line("Tel: ${profile.phone}")
            }
            if (profile.address.isNotBlank()) {
                line(profile.address)
            }
            blankLine()
        }

        // ── Date / Time ──
        val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateObj = Date(invoice.transaction.date)
        val dateStr = dateFmt.format(dateObj)
        val timeStr = timeFmt.format(dateObj)
        val invoiceId = invoice.transaction.id.toString()

        // ════════════════════════════════════════
        // 1. HEADER — centered
        // ════════════════════════════════════════
        esc(alignCenter)
        esc(boldOn)
        line("Invoice")
        esc(boldOff)
        blankLine()

        // ════════════════════════════════════════
        // 2. Invoice No / Date / Time — left+right
        // ════════════════════════════════════════
        esc(alignLeft)
        line(leftRight("Invoice No:$invoiceId", "Date:$dateStr"))
        line(leftRight("70", "Time:$timeStr"))
        blankLine()
        dashes()

        // ════════════════════════════════════════
        // 3. Cash Sale — centered, bold
        // ════════════════════════════════════════
        esc(alignCenter)
        esc(boldOn)
        line(if (invoice.transaction.isPaid) "Cash Sale" else "Credit Sale")
        esc(boldOff)
        dashes()

        // ════════════════════════════════════════
        // 4. COLUMN HEADER — left-aligned
        // ════════════════════════════════════════
        esc(alignLeft)
        // Column layout (32 chars total):
        //   Col A (serial#) : 3 chars  (positions 1-3)
        //   Col B (qty)     : 14 chars (positions 4-17)
        //   Col C (price)   : 5 chars  (positions 18-22)
        //   Col D (amount)  : 10 chars (positions 23-32)
        //
        // Header line 1: "#  Item Name"
        // Header line 2: "   Quantity      Price    Amount"
        esc(boldOn)
        line("#  Item Name")
        line("   Quantity      Price    Amount")
        esc(boldOff)
        dashes()

        // ════════════════════════════════════════
        // 5. ITEM ROWS — two lines per item
        // ════════════════════════════════════════
        var subtotal = 0.0
        invoice.items.forEachIndexed { index, item ->
            val serial = (index + 1).toString()
            val name   = item.itemName.take(29)
            val qty    = n(item.quantity)
            val price  = n(item.rate)
            val amount = n(item.amount)
            subtotal += item.amount

            // Line 1: serial# left-padded to 3 chars + name
            line(serial.padEnd(3) + name)

            // Line 2: 3 spaces + qty (14) + price (5) + amount (10)
            line("   " + qty.padEnd(14) + price.padStart(5) + amount.padStart(10))
        }

        // ════════════════════════════════════════
        // 6. ITEMS COUNT + SUBTOTAL
        // ════════════════════════════════════════
        dashes()
        val itemsLeft = "Items: ${invoice.items.size}"
        val subtotalRight = n(subtotal)
        line(leftRight(itemsLeft, subtotalRight))

        // ════════════════════════════════════════
        // 7. TOTALS — label left, value right
        // ════════════════════════════════════════
        dashes()
        blankLine()

        val grandTotal = invoice.transaction.grandTotal
        val received = if (invoice.transaction.isPaid) grandTotal else 0.0
        val balance = grandTotal - received

        // Format: "Total     :           283"
        fun totalLine(label: String, value: String) {
            val labelPart = label.padEnd(10) + ":"
            val valuePart = value.padStart(W - labelPart.length)
            line(labelPart + valuePart)
        }

        esc(boldOn)
        totalLine("Total", n(grandTotal))
        blankLine()
        totalLine("Received", n(received))
        blankLine()
        totalLine("Balance", n(balance))
        esc(boldOff)

        blankLine()
        dashes()

        // ════════════════════════════════════════
        // 8. FOOTER — centered
        // ════════════════════════════════════════
        esc(alignCenter)
        esc(boldOn)
        line("Terms & Conditions")
        esc(boldOff)
        blankLine()
        line("Thank you for doing business")
        line("with us!")

        // Feed paper
        esc(byteArrayOf(0x1B, 0x64, 0x05)) // Feed 5 lines

        return bytes.toByteArray()
    }
}
