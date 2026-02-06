package com.example.grocerymanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.grocerymanager.data.local.ShoppingItemEntity
import com.example.grocerymanager.data.local.ShoppingJoinedRow
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ShoppingItemEntity)

    @Query("SELECT * FROM shopping_items WHERE ingredientId = :ingredientId LIMIT 1")
    suspend fun findByIngredientId(ingredientId: Long): ShoppingItemEntity?

    @Query("DELETE FROM shopping_items WHERE ingredientId = :ingredientId")
    suspend fun deleteByIngredientId(ingredientId: Long)

    @Query("DELETE FROM shopping_items WHERE isBought = 1")
    suspend fun deleteBought()

    @Query(
        """
        SELECT i.id AS ingredientId, i.displayName, i.nameKey, i.type, i.category, s.quantity, s.isBought
        FROM shopping_items s
        JOIN ingredients i ON i.id = s.ingredientId
        ORDER BY s.isBought ASC, i.displayName COLLATE NOCASE
        """
    )
    fun observeShopping(): Flow<List<ShoppingJoinedRow>>

    @Query(
        """
        SELECT i.id AS ingredientId, i.displayName, i.nameKey, i.type, i.category, s.quantity, s.isBought
        FROM shopping_items s
        JOIN ingredients i ON i.id = s.ingredientId
        WHERE s.isBought = 1
        ORDER BY i.displayName COLLATE NOCASE
        """
    )
    suspend fun getBoughtRows(): List<ShoppingJoinedRow>

    @Query("UPDATE shopping_items SET isBought = :isBought WHERE ingredientId = :ingredientId")
    suspend fun updateBoughtState(ingredientId: Long, isBought: Boolean)

    @Query("UPDATE shopping_items SET quantity = NULL WHERE ingredientId = :ingredientId")
    suspend fun setQuantityNull(ingredientId: Long)

    @Query("UPDATE shopping_items SET quantity = COALESCE(quantity, 1.0) WHERE ingredientId = :ingredientId")
    suspend fun setNullQuantitiesToDefault(ingredientId: Long)
}
