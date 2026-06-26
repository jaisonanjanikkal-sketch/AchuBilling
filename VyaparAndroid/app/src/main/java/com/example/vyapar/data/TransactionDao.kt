package com.example.vyapar.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class TransactionWithItems(
    @Embedded val transaction: TransactionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "transactionId"
    )
    val items: List<TransactionItemEntity>
)

data class TopSellingQueryResult(
    val itemCode: String,
    val itemName: String,
    val totalQuantity: Double
)

@Dao
interface TransactionDao {
    @Query("""
        SELECT ti.itemCode AS itemCode, ti.itemName AS itemName, SUM(ti.quantity) AS totalQuantity
        FROM transaction_items ti
        INNER JOIN transactions t ON ti.transactionId = t.id
        WHERE t.date BETWEEN :startDate AND :endDate
        GROUP BY ti.itemCode
        ORDER BY totalQuantity DESC
    """)
    suspend fun getTopSellingItemsInRange(startDate: Long, endDate: Long): List<TopSellingQueryResult>

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactionsFlow(): Flow<List<TransactionWithItems>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): TransactionWithItems?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionItems(items: List<TransactionItemEntity>)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
}
