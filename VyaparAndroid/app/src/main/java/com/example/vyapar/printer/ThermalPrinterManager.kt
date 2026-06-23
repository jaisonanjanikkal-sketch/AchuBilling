package com.example.vyapar.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.vyapar.data.BusinessProfile
import com.example.vyapar.data.TransactionWithItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ThermalPrinterManager(private val context: Context) {

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasBluetoothPermission()) return emptyList()
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    suspend fun printInvoice(
        deviceAddress: String,
        profile: BusinessProfile,
        invoice: TransactionWithItems
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (bluetoothAdapter == null) return@withContext Result.failure(Exception("Bluetooth not supported"))
        val device = try {
            bluetoothAdapter.getRemoteDevice(deviceAddress)
        } catch (e: IllegalArgumentException) {
            return@withContext Result.failure(Exception("Invalid MAC Address"))
        }

        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null

        try {
            socket = device.createRfcommSocketToServiceRecord(sppUuid)
            socket.connect()
            outputStream = socket.outputStream

            val printBytes = compileReceiptBytes(profile, invoice)
            outputStream.write(printBytes)
            outputStream.flush()

            // Feed paper
            outputStream.write(byteArrayOf(0x1B, 0x64, 0x05)) // Feed 5 lines
            
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        } finally {
            try {
                outputStream?.close()
                socket?.close()
            } catch (e: Exception) {
                // Ignore close exceptions
            }
        }
    }

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
        writeText("Payment Mode: Cash\n")
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
