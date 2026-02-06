package com.example.grocerymanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.grocerymanager.data.local.StorageEntryEntity
import com.example.grocerymanager.data.local.StorageJoinedRow
import kotlinx.coroutines.flow.Flow

@Dao
interface StorageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: StorageEntryEntity)

    @Query("SELECT * FROM storage_entries WHERE ingredientId = :ingredientId LIMIT 1")
    suspend fun findByIngredientId(ingredientId: Long): StorageEntryEntity?

    @Query("DELETE FROM storage_entries WHERE ingredientId = :ingredientId")
    suspend fun deleteByIngredientId(ingredientId: Long)

    @Query(
        """
        SELECT i.id AS ingredientId, i.displayName, i.nameKey, i.type, i.category, s.amount
        FROM storage_entries s
        JOIN ingredients i ON i.id = s.ingredientId
        ORDER BY i.displayName COLLATE NOCASE
        """
    )
    fun observeStorage(): Flow<List<StorageJoinedRow>>

    @Query(
        """
        SELECT i.id AS ingredientId, i.displayName, i.nameKey, i.type, i.category, s.amount
        FROM storage_entries s
        JOIN ingredients i ON i.id = s.ingredientId
        """
    )
    suspend fun getStorageRows(): List<StorageJoinedRow>

    @Query("UPDATE storage_entries SET amount = NULL WHERE ingredientId = :ingredientId")
    suspend fun setAmountNull(ingredientId: Long)

    @Query("UPDATE storage_entries SET amount = COALESCE(amount, 1.0) WHERE ingredientId = :ingredientId")
    suspend fun setNullAmountsToDefault(ingredientId: Long)
}
