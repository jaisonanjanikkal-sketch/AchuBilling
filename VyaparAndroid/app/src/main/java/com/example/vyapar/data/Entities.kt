package com.example.vyapar.data

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val code: String,
    val name: String,
    val salePrice: Double,
    val purchasePrice: Double = 0.0,
    val stock: Double
)

@Immutable
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val date: Long, // timestamp in millis
    val total: Double,
    val discount: Double = 0.0,
    val grandTotal: Double,
    val type: String = "SALE", // SALE or PURCHASE
    val isPaid: Boolean = true
)

@Immutable
@Entity(
    tableName = "transaction_items",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["transactionId"])]
)
data class TransactionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val transactionId: Long,
    val itemCode: String,
    val itemName: String,
    val quantity: Double,
    val rate: Double,
    val amount: Double
)

@Immutable
data class BusinessProfile(
    val name: String,
    val phone: String,
    val address: String
)

@Immutable
data class TopSellingItem(
    val itemCode: String,
    val name: String,
    val quantitySold: Double,
    val percent: Int
)
