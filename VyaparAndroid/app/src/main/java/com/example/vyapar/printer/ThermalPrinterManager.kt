package com.example.vyapar.printer

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.vyapar.data.BusinessProfile
import com.example.vyapar.data.TransactionWithItems
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume

data class BleScanResultItem(
    val name: String,
    val address: String,
    val device: BluetoothDevice
)

class ThermalPrinterManager(private val context: Context) {

    companion object {
        private const val TAG = "ThermalPrinter"
        private const val SCAN_TIMEOUT_MS = 10_000L
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner

    private var scanCallback: ScanCallback? = null
    private val handler = Handler(Looper.getMainLooper())

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true
    fun isLocationServiceEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        return lm?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                lm?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
    }

    fun hasBluetoothPermission(): Boolean {
        val locationGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            locationGranted
        } else locationGranted
    }

    // ─── Bluetooth Classic (SPP) Printing ────────────────────────────────

    @SuppressLint("MissingPermission")
    suspend fun printInvoice(
        deviceAddress: String,
        profile: BusinessProfile,
        invoice: TransactionWithItems
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return@withContext Result.failure(Exception("Adapter null"))
        
        var socket: BluetoothSocket? = null
        try {
            Log.d(TAG, "Connecting to SPP...")
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            
            val out = socket.outputStream
            val data = compileReceiptBytes(profile, invoice)
            
            Log.d(TAG, "Streaming data to printer...")
            out.write(data)
            out.flush()
            
            // Allow time for motor to finish
            delay(1500)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Print failed: ${e.message}")
            Result.failure(e)
        } finally {
            try { socket?.close() } catch (e: Exception) {}
        }
    }

    // ─── ESC/POS Receipt Formatting (Power Optimized) ───────────────────

    private fun compileReceiptBytes(profile: BusinessProfile, invoice: TransactionWithItems): ByteArray {
        val b = mutableListOf<Byte>()
        val W = 32 // 58mm thermal = 32 chars per line

        // Commands
        val init = byteArrayOf(0x1B, 0x40) // Initialize
        val alignCenter = byteArrayOf(0x1B, 0x61, 0x01)
        val alignLeft = byteArrayOf(0x1B, 0x61, 0x00)
        val boldOn = byteArrayOf(0x1B, 0x45, 0x01)
        val boldOff = byteArrayOf(0x1B, 0x45, 0x00)
        val sizeDouble = byteArrayOf(0x1D, 0x21, 0x11)
        val sizeNormal = byteArrayOf(0x1D, 0x21, 0x00)

        fun esc(arr: ByteArray) { b.addAll(arr.toList()) }
        
        fun line(text: String) { 
            b.addAll(text.toByteArray(Charsets.UTF_8).toList()) 
            b.add(0x0A)
        }

        fun blankLine() {
            b.add(0x0A)
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

        // 1. Initialize
        esc(init)
        
        // 2. Power Management - CRITICAL for 5V 1A
        // ESC 7 n1 n2 n3 (n1=Max Dots, n2=Heat Time, n3=Heat Interval)
        // Values optimized to keep peak current low (Prevents Red Light)
        esc(byteArrayOf(0x1B, 0x37, 0x07, 0x40, 0x02)) 
        
        // Stabilize after power config
        repeat(10) { b.add(0x00) } 

        // 3. Business Name & details (optional, but standard for POS bills)
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
        // HEADER — centered
        // ════════════════════════════════════════
        esc(alignCenter)
        esc(boldOn)
        line("Invoice")
        esc(boldOff)
        blankLine()

        // ════════════════════════════════════════
        // Invoice No / Date / Time — left+right
        // ════════════════════════════════════════
        esc(alignLeft)
        line(leftRight("Invoice No:$invoiceId", "Date:$dateStr"))
        line(leftRight("70", "Time:$timeStr"))
        blankLine()
        dashes()

        // ════════════════════════════════════════
        // Cash Sale — centered, bold
        // ════════════════════════════════════════
        esc(alignCenter)
        esc(boldOn)
        line(if (invoice.transaction.isPaid) "Cash Sale" else "Credit Sale")
        esc(boldOff)
        dashes()

        // ════════════════════════════════════════
        // COLUMN HEADER — left-aligned
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
        // ITEM ROWS — two lines per item
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
        // ITEMS COUNT + SUBTOTAL
        // ════════════════════════════════════════
        dashes()
        blankLine()
        val itemsLeft = "Items: ${invoice.items.size}"
        val subtotalRight = n(subtotal)
        line(leftRight(itemsLeft, subtotalRight))
        blankLine()
        dashes()

        // ════════════════════════════════════════
        // TOTALS — label left, value right
        // ════════════════════════════════════════
        blankLine()
        blankLine()

        val grandTotal = invoice.transaction.grandTotal
        fun totalLine(label: String, value: String) {
            val labelPart = label.padEnd(10) + ":"
            val valuePart = value.padStart(W - labelPart.length)
            line(labelPart + valuePart)
        }

        esc(boldOn)
        totalLine("Total", n(grandTotal))
        esc(boldOff)

        blankLine()
        dashes()

        // ════════════════════════════════════════
        // FOOTER — centered
        // ════════════════════════════════════════
        esc(alignCenter)
        esc(boldOn)
        line("Terms & Conditions")
        esc(boldOff)
        blankLine()
        line("Thank you for doing business")
        line("with us!")

        // 6. Paper Feed
        repeat(5) { b.add(0x0A) }
        
        return b.toByteArray()
    }

    // ─── Scanning (Restored Logic) ───────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun scanBleDevices(onResult: (List<BleScanResultItem>) -> Unit) {
        val scanner = bleScanner ?: return onResult(emptyList())
        val devices = mutableMapOf<String, BleScanResultItem>()
        
        stopScan()
        
        scanCallback = object : ScanCallback() {
            override fun onScanResult(ct: Int, r: ScanResult) {
                val d = r.device
                val name = d.name ?: r.scanRecord?.deviceName ?: "Unknown Device"
                val addr = d.address ?: return
                if (!devices.containsKey(addr)) {
                    devices[addr] = BleScanResultItem(name, addr, d)
                }
            }
            override fun onScanFailed(e: Int) { onResult(emptyList()) }
        }
        
        scanner.startScan(scanCallback)
        
        handler.postDelayed({
            stopScan()
            onResult(devices.values.toList())
        }, SCAN_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanCallback?.let { bleScanner?.stopScan(it) }
        scanCallback = null
    }

    fun disconnect() { /* Managed by SPP stream now */ }
}
