package com.example.vyapar.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vyapar.data.DataRepository
import com.example.vyapar.data.ItemEntity
import com.example.vyapar.data.TransactionWithItems
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import com.example.vyapar.utils.InvoiceShareUtility
import com.example.vyapar.data.BusinessProfile

// ViewModel and State definitions
data class DashboardStats(
    val totalRevenue: Double = 0.0,
    val avgInvoice: Double = 0.0,
    val itemsSold: Double = 0.0,
    val inventoryValue: Double = 0.0
)

data class TopSellingItem(
    val itemCode: String,
    val name: String,
    val quantitySold: Double,
    val percent: Int
)

data class DashboardState(
    val stats: DashboardStats = DashboardStats(),
    val lowStockItems: List<ItemEntity> = emptyList(),
    val topSellingItems: List<TopSellingItem> = emptyList(),
    val recentTransactions: List<TransactionWithItems> = emptyList()
)

class DashboardViewModel(private val repository: DataRepository) : ViewModel() {
    val state: StateFlow<DashboardState> = combine(
        repository.getTransactionsFlow(),
        repository.getItemsFlow(),
        repository.getLowStockItemsFlow()
    ) { txns, items, lowStock ->
        val totalRevenue = txns.sumOf { it.transaction.grandTotal }
        val avgInvoice = if (txns.isNotEmpty()) totalRevenue / txns.size else 0.0
        val itemsSold = txns.sumOf { it.items.sumOf { item -> item.quantity } }
        val inventoryValue = items.sumOf { it.salePrice * kotlin.math.max(0.0, it.stock) }

        // Top selling items
        val salesMap = mutableMapOf<String, Double>()
        for (txn in txns) {
            for (lineItem in txn.items) {
                salesMap[lineItem.itemCode] = (salesMap[lineItem.itemCode] ?: 0.0) + lineItem.quantity
            }
        }
        val topItemsSorted = salesMap.entries
            .sortedByDescending { it.value }
            .take(5)
        val maxQty = topItemsSorted.firstOrNull()?.value ?: 1.0
        val topSelling = topItemsSorted.map { entry ->
            val item = items.find { it.code == entry.key }
            val name = item?.name ?: entry.key
            val pct = ((entry.value / maxQty) * 100).toInt()
            TopSellingItem(entry.key, name, entry.value, pct)
        }

        DashboardState(
            stats = DashboardStats(totalRevenue, avgInvoice, itemsSold, inventoryValue),
            lowStockItems = lowStock.take(5),
            topSellingItems = topSelling,
            recentTransactions = txns.take(5)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())

    fun getBusinessProfile(): BusinessProfile = repository.getBusinessProfile()
}

// UI Composable Screen
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToBilling: () -> Unit,
    onViewTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.state.collectAsState()
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC)),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Header Space / Title
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Dashboard",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Overview of your business",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    Button(
                        onClick = onNavigateToBilling,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("+ New Invoice", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Stat Grid
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            label = "TOTAL SALES",
                            value = formatCurrency(uiState.stats.totalRevenue),
                            gradient = Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF60A5FA))),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        StatCard(
                            label = "AVG INVOICE",
                            value = formatCurrency(uiState.stats.avgInvoice),
                            gradient = Brush.linearGradient(listOf(Color(0xFF16A34A), Color(0xFF4ADE80))),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            label = "ITEMS SOLD",
                            value = formatQuantity(uiState.stats.itemsSold),
                            gradient = Brush.linearGradient(listOf(Color(0xFFD97706), Color(0xFFFBBF24))),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        StatCard(
                            label = "INVENTORY VAL",
                            value = formatCurrency(uiState.stats.inventoryValue),
                            gradient = Brush.linearGradient(listOf(Color(0xFFDC2626), Color(0xFFF87171))),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Low Stock Section
            if (uiState.lowStockItems.isNotEmpty()) {
                item {
                    SectionHeader(title = "Low Stock Alerts")
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        uiState.lowStockItems.forEach { item ->
                            LowStockCard(item)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            // Top Selling Items Section
            if (uiState.topSellingItems.isNotEmpty()) {
                item {
                    SectionHeader(title = "Top Selling Products")
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        uiState.topSellingItems.forEach { topItem ->
                            TopItemCard(topItem)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            // Recent Transactions Section
            item {
                SectionHeader(title = "Recent Sales")
            }
            if (uiState.recentTransactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No sales recorded yet.",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(uiState.recentTransactions) { txn ->
                    RecentSaleCard(
                        invoice = txn,
                        onViewTransaction = onViewTransaction,
                        onShareClick = { invoice ->
                            val profile = viewModel.getBusinessProfile()
                            InvoiceShareUtility.shareInvoiceAsImage(context, profile, invoice)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    gradient: Brush,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF1E293B),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
    )
}

@Composable
fun LowStockCard(item: ItemEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dotColor = if (item.stock < 0.0) Color(0xFFDC2626) else Color(0xFFD97706)
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(dotColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text(text = "Code: ${item.code}", fontSize = 11.sp, color = Color(0xFF94A3B8))
            }
            Text(
                text = "${formatQuantity(item.stock)} left",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (item.stock < 0.0) Color(0xFFDC2626) else Color(0xFFD97706)
            )
        }
    }
}

@Composable
fun TopItemCard(item: TopSellingItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = item.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text(text = "${formatQuantity(item.quantitySold)} sold", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(Color(0xFFE2E8F0), RoundedCornerShape(3.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(item.percent / 100f)
                        .background(Color(0xFF2563EB), RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

@Composable
fun RecentSaleCard(
    invoice: TransactionWithItems,
    onViewTransaction: (Long) -> Unit,
    onShareClick: (TransactionWithItems) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onViewTransaction(invoice.transaction.id) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFDCFCE7), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "💵", fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Cash Sale", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                val timeStr = sdf.format(Date(invoice.transaction.date))
                val itemsCount = invoice.items.size
                Text(
                    text = "#${invoice.transaction.id} · $timeStr · $itemsCount item${if (itemsCount != 1) "s" else ""}",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatCurrency(invoice.transaction.grandTotal),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF16A34A)
                )
                Box(
                    modifier = Modifier
                        .background(Color(0xFFDCFCE7), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = "PAID", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { onShareClick(invoice) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Helpers
fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return format.format(amount).replace("Rs.", "₹").replace("INR", "₹").trim()
}

fun formatQuantity(qty: Double): String {
    return if (qty % 1.0 == 0.0) qty.toInt().toString() else String.format("%.2f", qty)
}
