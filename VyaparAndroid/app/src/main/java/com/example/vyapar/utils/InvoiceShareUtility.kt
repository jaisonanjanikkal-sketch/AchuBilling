package com.example.vyapar.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.example.vyapar.data.BusinessProfile
import com.example.vyapar.data.TransactionWithItems
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InvoiceShareUtility {

    fun shareInvoiceAsImage(
        context: Context,
        profile: BusinessProfile,
        invoice: TransactionWithItems
    ) {
        val bitmap = renderInvoiceToBitmap(profile, invoice)
        val file = saveBitmapToCache(context, bitmap, "Invoice_${invoice.transaction.id}.png") ?: return
        
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri("", uri)
            putExtra(Intent.EXTRA_SUBJECT, "Invoice #${invoice.transaction.id} from ${profile.name}")
            putExtra(Intent.EXTRA_TEXT, "Here is your invoice #${invoice.transaction.id} from ${profile.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Share Invoice")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun renderInvoiceToBitmap(profile: BusinessProfile, invoice: TransactionWithItems): Bitmap {
        val width = 800
        val rowHeight = 50
        val itemsCount = invoice.items.size
        val height = 480 + (itemsCount * rowHeight)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Draw background
        canvas.drawColor(Color.WHITE)

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = Color.parseColor("#1d4ed8") // primary blue
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subHeaderPaint = Paint().apply {
            color = Color.parseColor("#475569") // slate gray
            textSize = 20f
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.parseColor("#cbd5e1") // light gray
            strokeWidth = 2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val boldPaint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        var y = 60f

        // Draw header
        canvas.drawText(profile.name, 40f, y, headerPaint)
        y += 35f
        
        if (profile.phone.isNotBlank()) {
            canvas.drawText("Tel: ${profile.phone}", 40f, y, subHeaderPaint)
            y += 30f
        }
        if (profile.address.isNotBlank()) {
            canvas.drawText(profile.address, 40f, y, subHeaderPaint)
            y += 30f
        }

        y += 20f
        canvas.drawLine(40f, y, width - 40f, y, linePaint)
        y += 40f

        // Invoice metadata
        canvas.drawText("INVOICE", 40f, y, Paint(boldPaint).apply { textSize = 28f; color = Color.parseColor("#1d4ed8") })
        
        val sdf = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
        val dateStr = sdf.format(Date(invoice.transaction.date))
        
        canvas.drawText("Invoice #: ${invoice.transaction.id}", (width / 2).toFloat(), y, boldPaint)
        y += 35f
        canvas.drawText("Date: $dateStr", (width / 2).toFloat(), y, textPaint)
        canvas.drawText("Payment Mode: Cash", 40f, y, textPaint)

        y += 40f
        canvas.drawLine(40f, y, width - 40f, y, linePaint)
        y += 35f

        // Table Header
        canvas.drawText("Item Name", 40f, y, boldPaint)
        canvas.drawText("Qty", 450f, y, boldPaint)
        canvas.drawText("Rate", 570f, y, boldPaint)
        canvas.drawText("Amount", 680f, y, boldPaint)
        y += 15f
        canvas.drawLine(40f, y, width - 40f, y, linePaint)
        y += 35f

        // Table Rows
        for (item in invoice.items) {
            canvas.drawText(item.itemName, 40f, y, textPaint)
            
            val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else String.format("%.2f", item.quantity)
            canvas.drawText(qtyStr, 450f, y, textPaint)
            canvas.drawText(String.format("%.2f", item.rate), 570f, y, textPaint)
            canvas.drawText(String.format("%.2f", item.amount), 680f, y, textPaint)
            y += rowHeight
        }

        y -= (rowHeight - 15f)
        canvas.drawLine(40f, y, width - 40f, y, linePaint)
        y += 40f

        // Totals summary
        val summaryX = 450f
        canvas.drawText("Sub Total:", summaryX, y, textPaint)
        canvas.drawText(String.format("%.2f", invoice.transaction.total), 680f, y, textPaint)
        
        if (invoice.transaction.discount > 0.0) {
            y += 35f
            canvas.drawText("Discount:", summaryX, y, textPaint)
            canvas.drawText(String.format("-%.2f", invoice.transaction.discount), 680f, y, textPaint)
            
            y += 35f
            canvas.drawText("Grand Total:", summaryX, y, boldPaint)
            canvas.drawText(String.format("%.2f", invoice.transaction.grandTotal), 680f, y, boldPaint)
        } else {
            y += 35f
            canvas.drawText("Grand Total:", summaryX, y, boldPaint)
            canvas.drawText(String.format("%.2f", invoice.transaction.total), 680f, y, boldPaint)
        }

        y += 50f
        canvas.drawLine(40f, y, width - 40f, y, linePaint)
        y += 40f

        // Footer message
        val footerPaint = Paint().apply {
            color = Color.parseColor("#64748b")
            textSize = 20f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("Thank you for your business!", (width / 2).toFloat(), y, footerPaint)

        return bitmap
    }

    private fun saveBitmapToCache(context: Context, bitmap: Bitmap, fileName: String): File? {
        val cachePath = File(context.cacheDir, "shared_invoices")
        if (!cachePath.exists()) {
            cachePath.mkdirs()
        }
        val file = File(cachePath, fileName)
        return try {
            val fileOut = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOut)
            fileOut.flush()
            fileOut.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
