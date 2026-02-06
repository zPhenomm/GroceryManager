package com.example.grocerymanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.grocerymanager.data.local.CategoryCountRow
import com.example.grocerymanager.data.local.IngredientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {
    @Query("SELECT * FROM ingredients ORDER BY displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredients WHERE nameKey = :nameKey LIMIT 1")
    suspend fun findByNameKey(nameKey: String): IngredientEntity?

    @Query("SELECT * FROM ingredients WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): IngredientEntity?

    @Query(
        """
        SELECT category AS name, COUNT(*) AS ingredientCount
        FROM ingredients
        GROUP BY category
        ORDER BY category COLLATE NOCASE
        """
    )
    fun observeCategories(): Flow<List<CategoryCountRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: IngredientEntity): Long

    @Update
    suspend fun update(entity: IngredientEntity)

    @Query("DELETE FROM ingredients WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("UPDATE ingredients SET category = :newCategory WHERE category = :oldCategory")
    suspend fun replaceCategory(oldCategory: String, newCategory: String): Int

    @Query(
        """
        SELECT * FROM ingredients
        WHERE nameKey LIKE :prefixKey || '%'
        ORDER BY displayName COLLATE NOCASE
        LIMIT 8
        """
    )
    fun searchByPrefix(prefixKey: String): Flow<List<IngredientEntity>>
}
