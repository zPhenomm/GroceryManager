package com.example.grocerymanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.grocerymanager.data.local.RecipeEntity
import com.example.grocerymanager.data.local.RecipeIngredientEntity
import com.example.grocerymanager.data.local.RecipeIngredientJoinedRow
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY name COLLATE NOCASE")
    fun observeRecipes(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE nameKey = :nameKey LIMIT 1")
    suspend fun findByNameKey(nameKey: String): RecipeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Query("DELETE FROM recipes WHERE id = :recipeId")
    suspend fun deleteById(recipeId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipeIngredients(ingredients: List<RecipeIngredientEntity>)

    @Query(
        """
        SELECT ri.recipeId, ri.ingredientId, i.displayName, i.nameKey, i.type, ri.requiredAmount
        FROM recipe_ingredients ri
        JOIN ingredients i ON i.id = ri.ingredientId
        ORDER BY ri.recipeId, i.displayName COLLATE NOCASE
        """
    )
    fun observeRecipeIngredientRows(): Flow<List<RecipeIngredientJoinedRow>>

    @Query(
        """
        SELECT ri.recipeId, ri.ingredientId, i.displayName, i.nameKey, i.type, ri.requiredAmount
        FROM recipe_ingredients ri
        JOIN ingredients i ON i.id = ri.ingredientId
        WHERE ri.recipeId = :recipeId
        ORDER BY i.displayName COLLATE NOCASE
        """
    )
    suspend fun getRecipeIngredientRows(recipeId: Long): List<RecipeIngredientJoinedRow>

    @Query("UPDATE recipe_ingredients SET requiredAmount = NULL WHERE ingredientId = :ingredientId")
    suspend fun setRequiredAmountNull(ingredientId: Long)

    @Query(
        """
        UPDATE recipe_ingredients
        SET requiredAmount = COALESCE(requiredAmount, 1.0)
        WHERE ingredientId = :ingredientId
        """
    )
    suspend fun setNullRequiredAmountsToDefault(ingredientId: Long)

    @Query(
        """
        DELETE FROM recipes
        WHERE id IN (
            SELECT recipes.id
            FROM recipes
            LEFT JOIN recipe_ingredients ON recipes.id = recipe_ingredients.recipeId
            WHERE recipe_ingredients.id IS NULL
        )
        """
    )
    suspend fun deleteRecipesWithoutIngredients()
}
