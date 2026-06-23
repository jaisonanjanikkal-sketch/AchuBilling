package com.example.vyapar.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY name ASC")
    fun getAllItemsFlow(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE name LIKE :query OR code LIKE :query ORDER BY name ASC")
    fun searchItemsFlow(query: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE stock <= 5 ORDER BY stock ASC")
    fun getLowStockItemsFlow(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE code = :code LIMIT 1")
    suspend fun getItemByCode(code: String): ItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity)

    @Update
    suspend fun updateItem(item: ItemEntity)

    @Delete
    suspend fun deleteItem(item: ItemEntity)

    @Query("DELETE FROM items")
    suspend fun deleteAllItems()
}
