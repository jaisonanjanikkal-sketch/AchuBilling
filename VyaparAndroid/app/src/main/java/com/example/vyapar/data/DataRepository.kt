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
    suspend fun updateSale(transactionId: Long, transaction: TransactionEntity, items: List<TransactionItemEntity>)
    suspend fun deleteTransaction(id: Long)
    suspend fun clearAllData()

    // Business Profile & Settings
    fun getBusinessProfile(): BusinessProfile
    fun saveBusinessProfile(profile: BusinessProfile)
    fun getSelectedPrinterAddress(): String?
    fun saveSelectedPrinterAddress(address: String?)

    suspend fun restoreBackup(
        profile: BusinessProfile,
        items: List<ItemEntity>,
        transactions: List<TransactionEntity>,
        transactionItems: List<TransactionItemEntity>
    )
}

class DefaultDataRepository(private val context: Context) : DataRepository {
    private val db = AppDatabase.getDatabase(context)
    private val itemDao = db.itemDao()
    private val transactionDao = db.transactionDao()

    private val prefs: SharedPreferences = context.getSharedPreferences("vyapar_prefs", Context.MODE_PRIVATE)

    @Volatile
    private var cachedProfile: BusinessProfile? = null
    @Volatile
    private var cachedPrinterAddress: String? = null
    @Volatile
    private var cachedPrinterAddressLoaded = false
    private val cacheLock = Any()

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
                } else {
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

    override suspend fun updateSale(transactionId: Long, transaction: TransactionEntity, items: List<TransactionItemEntity>) {
        db.withTransaction {
            val oldTransactionWithItems = transactionDao.getTransactionById(transactionId)
            if (oldTransactionWithItems != null) {
                for (oldLineItem in oldTransactionWithItems.items) {
                    val existingItem = itemDao.getItemByCode(oldLineItem.itemCode)
                    if (existingItem != null) {
                        itemDao.updateItem(existingItem.copy(stock = existingItem.stock + oldLineItem.quantity))
                    }
                }
                transactionDao.deleteTransactionById(transactionId)
            }

            val updatedTxn = transaction.copy(id = transactionId)
            transactionDao.insertTransaction(updatedTxn)

            val mappedItems = items.map { it.copy(transactionId = transactionId) }
            transactionDao.insertTransactionItems(mappedItems)

            for (lineItem in mappedItems) {
                val existingItem = itemDao.getItemByCode(lineItem.itemCode)
                if (existingItem != null) {
                    itemDao.updateItem(existingItem.copy(stock = existingItem.stock - lineItem.quantity))
                } else {
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
        return cachedProfile ?: synchronized(cacheLock) {
            cachedProfile ?: run {
                val name = prefs.getString("biz_name", "My Business") ?: "My Business"
                val phone = prefs.getString("biz_phone", "") ?: ""
                val address = prefs.getString("biz_address", "") ?: ""
                val profile = BusinessProfile(name, phone, address)
                cachedProfile = profile
                profile
            }
        }
    }

    override fun saveBusinessProfile(profile: BusinessProfile) {
        synchronized(cacheLock) {
            cachedProfile = profile
        }
        prefs.edit()
            .putString("biz_name", profile.name)
            .putString("biz_phone", profile.phone)
            .putString("biz_address", profile.address)
            .apply()
    }

    // Printer Settings
    override fun getSelectedPrinterAddress(): String? {
        return if (cachedPrinterAddressLoaded) {
            cachedPrinterAddress
        } else {
            synchronized(cacheLock) {
                if (cachedPrinterAddressLoaded) {
                    cachedPrinterAddress
                } else {
                    val address = prefs.getString("selected_printer_mac", null)
                    cachedPrinterAddress = address
                    cachedPrinterAddressLoaded = true
                    address
                }
            }
        }
    }

    override fun saveSelectedPrinterAddress(address: String?) {
        synchronized(cacheLock) {
            cachedPrinterAddress = address
            cachedPrinterAddressLoaded = true
        }
        prefs.edit().putString("selected_printer_mac", address).apply()
    }

    override suspend fun restoreBackup(
        profile: BusinessProfile,
        items: List<ItemEntity>,
        transactions: List<TransactionEntity>,
        transactionItems: List<TransactionItemEntity>
    ) {
        db.withTransaction {
            transactionDao.deleteAllTransactions()
            itemDao.deleteAllItems()
            
            saveBusinessProfile(profile)
            
            for (item in items) {
                itemDao.insertItem(item)
            }
            
            for (txn in transactions) {
                transactionDao.insertTransaction(txn)
            }
            
            transactionDao.insertTransactionItems(transactionItems)
        }
    }
}
