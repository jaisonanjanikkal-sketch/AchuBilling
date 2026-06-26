package com.example.vyapar.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vyapar.data.TopSellingItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopSellingScreen(
    viewModel: TopSellingViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topSellingItems by viewModel.topSellingItems.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val customRange by viewModel.customDateRange.collectAsState()

    var showDateRangePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Top Selling Products", fontWeight = FontWeight.Bold, color = Color.White) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF1F5F9))
        ) {
            // Horizontal Filter Chips Row
            val scrollState = rememberScrollState()
            val filterOptions = listOf("Today", "Yesterday", "This Week", "This Month", "Custom")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                filterOptions.forEach { filter ->
                    val isSelected = filter == selectedFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (filter == "Custom") {
                                showDateRangePicker = true
                            } else {
                                viewModel.applyFilter(filter)
                            }
                        },
                        label = {
                            Text(
                                text = if (filter == "Custom" && customRange != null) {
                                    val (start, end) = customRange!!
                                    val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
                                    "${sdf.format(Date(start))} - ${sdf.format(Date(end))}"
                                } else {
                                    filter
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2563EB),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFFF1F5F9),
                            labelColor = Color(0xFF64748B)
                        ),
                        border = null
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (topSellingItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📈", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No sales found for this period.",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Add some invoices in the selected range to see analytics",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(topSellingItems, key = { it.itemCode }) { topItem ->
                            TopItemCard(topItem)
                        }
                    }
                }
            }
        }
    }

    if (showDateRangePicker) {
        DateRangePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            onDateRangeSelected = { start, end ->
                viewModel.applyCustomRange(start, end)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    onDismissRequest: () -> Unit,
    onDateRangeSelected: (Long, Long) -> Unit
) {
    val dateRangePickerState = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = dateRangePickerState.selectedStartDateMillis
                    val end = dateRangePickerState.selectedEndDateMillis
                    if (start != null && end != null) {
                        onDateRangeSelected(start, end)
                    }
                },
                enabled = dateRangePickerState.selectedStartDateMillis != null && dateRangePickerState.selectedEndDateMillis != null
            ) {
                Text("Select", color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel", color = Color(0xFF64748B))
            }
        }
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            title = {
                Text(
                    text = "Select Date Range",
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            headline = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    val startText = dateRangePickerState.selectedStartDateMillis?.let { sdf.format(Date(it)) } ?: "Start Date"
                    val endText = dateRangePickerState.selectedEndDateMillis?.let { sdf.format(Date(it)) } ?: "End Date"
                    Text(text = "$startText - $endText", fontSize = 14.sp)
                }
            },
            showModeToggle = false
        )
    }
}
