package com.example.vyapar.data

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

interface DataRepository {
    // Items
    fun getItemsFlow(): Flow<List<ItemEntity>>
    fun searchItemsFlow(query: String): Flow<List<ItemEntity>>
    fun getLowStockItemsFlow(): Flow<List<ItemEntity>>
    suspend fun insertItem(item: ItemEntity)
    suspend fun updateItem(item: ItemEntity)
    suspend fun deleteItem(item: ItemEntity)
    suspend fun getItemByCode(code: String): ItemEntity?

    // Transactions
    fun getTransactionsFlow(): Flow<List<TransactionWithItems>>
    suspend fun getTransactionById(id: Long): TransactionWithItems?
    suspend fun insertSale(transaction: TransactionEntity, items: List<TransactionItemEntity>)
    suspend fun deleteTransaction(id: Long)
    suspend fun clearAllData()

    // Business Profile & Settings
    fun getBusinessProfile(): BusinessProfile
    fun saveBusinessProfile(profile: BusinessProfile)
    fun getSelectedPrinterAddress(): String?
    fun saveSelectedPrinterAddress(address: String?)
}

class DefaultDataRepository(private val context: Context) : DataRepository {
    private val db = AppDatabase.getDatabase(context)
    private val itemDao = db.itemDao()
    private val transactionDao = db.transactionDao()

    private val prefs: SharedPreferences = context.getSharedPreferences("vyapar_prefs", Context.MODE_PRIVATE)

    // Items
    override fun getItemsFlow(): Flow<List<ItemEntity>> = itemDao.getAllItemsFlow()

    override fun searchItemsFlow(query: String): Flow<List<ItemEntity>> {
        return if (query.isBlank()) {
            itemDao.getAllItemsFlow()
        } else {
            itemDao.searchItemsFlow("%$query%")
        }
    }

    override fun getLowStockItemsFlow(): Flow<List<ItemEntity>> = itemDao.getLowStockItemsFlow()

    override suspend fun insertItem(item: ItemEntity) = itemDao.insertItem(item)

    override suspend fun updateItem(item: ItemEntity) = itemDao.updateItem(item)

    override suspend fun deleteItem(item: ItemEntity) = itemDao.deleteItem(item)

    override suspend fun getItemByCode(code: String): ItemEntity? = itemDao.getItemByCode(code)

    // Transactions
    override fun getTransactionsFlow(): Flow<List<TransactionWithItems>> = transactionDao.getAllTransactionsFlow()

    override suspend fun getTransactionById(id: Long): TransactionWithItems? = transactionDao.getTransactionById(id)

    override suspend fun insertSale(transaction: TransactionEntity, items: List<TransactionItemEntity>) {
        db.withTransaction {
            // 1. Insert TransactionEntity and get its generated ID
            val generatedId = transactionDao.insertTransaction(transaction)

            // 2. Map transaction items with the correct foreign key ID
            val mappedItems = items.map { it.copy(transactionId = generatedId) }
            transactionDao.insertTransactionItems(mappedItems)

            // 3. Update stock levels for each item
            for (lineItem in mappedItems) {
                val existingItem = itemDao.getItemByCode(lineItem.itemCode)
                if (existingItem != null) {
                    val updatedStock = existingItem.stock - lineItem.quantity
                    itemDao.updateItem(existingItem.copy(stock = updatedStock))
                } else if (!lineItem.itemCode.startsWith("__new__")) {
                    // Create dynamic product with negative stock if it didn't exist
                    val newItem = ItemEntity(
                        code = lineItem.itemCode,
                        name = lineItem.itemName,
                        salePrice = lineItem.rate,
                        stock = -lineItem.quantity
                    )
                    itemDao.insertItem(newItem)
                }
            }
        }
    }

    override suspend fun deleteTransaction(id: Long) {
        db.withTransaction {
            // Restore stock before deleting
            val transactionWithItems = transactionDao.getTransactionById(id)
            if (transactionWithItems != null) {
                for (lineItem in transactionWithItems.items) {
                    val existingItem = itemDao.getItemByCode(lineItem.itemCode)
                    if (existingItem != null) {
                        val restoredStock = existingItem.stock + lineItem.quantity
                        itemDao.updateItem(existingItem.copy(stock = restoredStock))
                    }
                }
                transactionDao.deleteTransactionById(id)
            }
        }
    }

    override suspend fun clearAllData() {
        db.withTransaction {
            transactionDao.deleteAllTransactions()
            itemDao.deleteAllItems()
        }
    }

    // Business Profile
    override fun getBusinessProfile(): BusinessProfile {
        val name = prefs.getString("biz_name", "My Business") ?: "My Business"
        val phone = prefs.getString("biz_phone", "") ?: ""
        val address = prefs.getString("biz_address", "") ?: ""
        return BusinessProfile(name, phone, address)
    }

    override fun saveBusinessProfile(profile: BusinessProfile) {
        prefs.edit()
            .putString("biz_name", profile.name)
            .putString("biz_phone", profile.phone)
            .putString("biz_address", profile.address)
            .apply()
    }

    // Printer Settings
    override fun getSelectedPrinterAddress(): String? {
        return prefs.getString("selected_printer_mac", null)
    }

    override fun saveSelectedPrinterAddress(address: String?) {
        prefs.edit().putString("selected_printer_mac", address).apply()
    }
}
