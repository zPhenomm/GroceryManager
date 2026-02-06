package com.example.grocerymanager.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ingredients",
    indices = [Index(value = ["nameKey"], unique = true)]
)
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val displayName: String,
    val nameKey: String,
    val type: IngredientType,
    val category: String
)

@Entity(
    tableName = "storage_entries",
    foreignKeys = [
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["ingredientId"], unique = true)]
)
data class StorageEntryEntity(
    @PrimaryKey val ingredientId: Long,
    val amount: Double?
)

@Entity(
    tableName = "recipes",
    indices = [Index(value = ["nameKey"], unique = true)]
)
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val nameKey: String
)

@Entity(
    tableName = "recipe_ingredients",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["recipeId", "ingredientId"], unique = true)]
)
// TODO: [ksp] C:/Users/maxma/AndroidStudioProjects/GroceryManager/app/src/main/java/com/example/grocerymanager/data/local/Entities.kt:65: ingredientId column references a foreign key but it is not part of an index. This may trigger full table scans whenever parent table is modified so you are highly advised to create an index that covers this column.
data class RecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val recipeId: Long,
    val ingredientId: Long,
    val requiredAmount: Double?
)

@Entity(
    tableName = "shopping_items",
    foreignKeys = [
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["ingredientId"], unique = true)]
)
data class ShoppingItemEntity(
    @PrimaryKey val ingredientId: Long,
    val quantity: Double?,
    val isBought: Boolean = false
)
