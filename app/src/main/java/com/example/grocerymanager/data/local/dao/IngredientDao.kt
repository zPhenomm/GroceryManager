package com.example.grocerymanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: IngredientEntity): Long

    @Update
    suspend fun update(entity: IngredientEntity)

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
