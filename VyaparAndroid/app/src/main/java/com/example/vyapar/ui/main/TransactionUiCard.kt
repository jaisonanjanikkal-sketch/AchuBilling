package com.example.vyapar.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vyapar.data.TransactionWithItems
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionUiCard(
    txn: TransactionWithItems,
    onPrint: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFDBEAFE), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "💵", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Cash Sale", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                    val timeStr = sdf.format(Date(txn.transaction.date))
                    val itemsCount = txn.items.size
                    Text(
                        text = "#${txn.transaction.id} · $timeStr · $itemsCount item${if (itemsCount != 1) "s" else ""}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatCurrency(txn.transaction.grandTotal),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF16A34A)
                    )
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFDCFCE7), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = "Paid", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFAFAFA))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPrint,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🖨️ Print", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("📤 Share", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("✏️ Edit", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🗑️ Del", fontSize = 11.sp)
                }
            }
        }
    }
}
