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
        
        fun esc(arr: ByteArray) { b.addAll(arr.toList()) }
        fun line(text: String) { 
            b.addAll(text.toByteArray(Charsets.US_ASCII).toList()) 
            b.add(0x0A)
        }

        // 1. Initialize
        esc(byteArrayOf(0x1B, 0x40))
        
        // 2. Power Management - CRITICAL for 5V 1A
        // ESC 7 n1 n2 n3 (n1=Max Dots, n2=Heat Time, n3=Heat Interval)
        // Values optimized to keep peak current low (Prevents Red Light)
        esc(byteArrayOf(0x1B, 0x37, 0x07, 0x40, 0x02)) 
        
        // Stabilize after power config
        repeat(10) { b.add(0x00) } 

        // 3. Simple Header
        line(profile.name)
        if (profile.phone.isNotBlank()) line("Tel: ${profile.phone}")
        line("--------------------------------")
        
        line("INVOICE #${invoice.transaction.id}")
        val sdf = SimpleDateFormat("dd-MM-yyyy hh:mm", Locale.getDefault())
        line("Date: ${sdf.format(Date(invoice.transaction.date))}")
        line("--------------------------------")

        // 4. Items
        invoice.items.forEach { item ->
            line(item.itemName)
            line("  ${item.quantity} x ${item.rate} = ${item.amount}")
        }
        line("--------------------------------")

        // 5. Totals
        line("TOTAL: ${invoice.transaction.grandTotal}")
        line("PAYMENT: ${if (invoice.transaction.isPaid) "PAID" else "UNPAID"}")
        line("--------------------------------")
        line("Thank you!")
        
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
