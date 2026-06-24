package com.example.vyapar.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vyapar.data.DataRepository
import com.example.vyapar.data.ItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class ItemsViewModel(private val repository: DataRepository) : ViewModel() {
    val searchQuery = MutableStateFlow("")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val itemsState: StateFlow<List<ItemEntity>> = searchQuery
        .flatMapLatest { query ->
            repository.searchItemsFlow(query)
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveItem(code: String, name: String, salePrice: Double, stock: Double) {
        viewModelScope.launch {
            val existing = repository.getItemByCode(code)
            if (existing != null) {
                repository.updateItem(existing.copy(name = name, salePrice = salePrice, stock = stock))
            } else {
                repository.insertItem(ItemEntity(code = code, name = name, salePrice = salePrice, stock = stock))
            }
        }
    }

    fun deleteItem(item: ItemEntity) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }
}

@Composable
fun ItemsScreen(
    viewModel: ItemsViewModel,
    modifier: Modifier = Modifier
) {
    val itemsList by viewModel.itemsState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ItemEntity?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
        ) {
            // Search Bar Area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = { Text("Search by name or code...", color = Color(0xFF94A3B8), fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF64748B)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )
            }

            // Items List
            if (itemsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📦", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No products matched your search" else "No products in inventory yet",
                            color = Color(0xFF64748B),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Tap the '+' button below to add one.",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(itemsList, key = { it.code }) { item ->
                        ItemCard(
                            item = item,
                            onEditClick = {
                                editingItem = item
                                showAddEditDialog = true
                            },
                            onDeleteClick = { viewModel.deleteItem(item) }
                        )
                    }
                }
            }
        }

        // FAB to add item
        FloatingActionButton(
            onClick = {
                editingItem = null
                showAddEditDialog = true
            },
            containerColor = Color(0xFF2563EB),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (showAddEditDialog) {
        AddEditItemDialog(
            item = editingItem,
            onDismiss = { showAddEditDialog = false },
            onSave = { code, name, price, stock ->
                viewModel.saveItem(code, name, price, stock)
                showAddEditDialog = false
            }
        )
    }
}

@Composable
fun ItemCard(
    item: ItemEntity,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditClick() },
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
            // Avatar with initials
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFFDBEAFE), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val initials = if (item.name.length >= 2) item.name.substring(0, 2).uppercase() else item.name.uppercase()
                Text(
                    text = initials,
                    color = Color(0xFF2563EB),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text(text = "Code: ${item.code}", fontSize = 11.sp, color = Color(0xFF94A3B8))
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(text = formatCurrency(item.salePrice), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                
                val stockColor = when {
                    item.stock > 5.0 -> Color(0xFF16A34A)
                    item.stock > 0.0 -> Color(0xFFD97706)
                    else -> Color(0xFFDC2626)
                }
                Text(
                    text = "${formatQuantity(item.stock)} in stock",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = stockColor
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444).copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
fun AddEditItemDialog(
    item: ItemEntity?,
    onDismiss: () -> Unit,
    onSave: (code: String, name: String, price: Double, stock: Double) -> Unit
) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var code by remember { mutableStateOf(item?.code ?: "") }
    var salePrice by remember { mutableStateOf(item?.salePrice?.toString() ?: "") }
    var stock by remember { mutableStateOf(item?.stock?.toString() ?: "") }

    var isNameError by remember { mutableStateOf(false) }
    var isPriceError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (item == null) "Add New Item" else "Edit Item", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        isNameError = it.isBlank()
                    },
                    label = { Text("Item Name") },
                    isError = isNameError,
                    supportingText = { if (isNameError) Text("Name cannot be empty") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Item Code (Optional)") },
                    placeholder = { Text("Will auto-generate if empty") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = item == null, // Code cannot be edited for existing items
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = salePrice,
                    onValueChange = {
                        salePrice = it
                        isPriceError = it.toDoubleOrNull() == null || it.toDouble() <= 0.0
                    },
                    label = { Text("Sale Price (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = isPriceError,
                    supportingText = { if (isPriceError) Text("Enter a valid price > 0") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = stock,
                    onValueChange = { stock = it },
                    label = { Text("Opening Stock") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = name.trim()
                    val finalPrice = salePrice.toDoubleOrNull() ?: 0.0
                    val finalStock = stock.toDoubleOrNull() ?: 0.0
                    val finalCode = code.trim().ifBlank { "ITEM_" + System.currentTimeMillis() }

                    isNameError = finalName.isBlank()
                    isPriceError = finalPrice <= 0.0

                    if (!isNameError && !isPriceError) {
                        onSave(finalCode, finalName, finalPrice, finalStock)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF64748B))
            }
        }
    )
}
