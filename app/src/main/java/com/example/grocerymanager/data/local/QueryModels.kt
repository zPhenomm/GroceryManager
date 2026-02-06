package com.example.grocerymanager.data.local

data class StorageJoinedRow(
    val ingredientId: Long,
    val displayName: String,
    val nameKey: String,
    val type: IngredientType,
    val category: String,
    val amount: Double?
)

data class RecipeIngredientJoinedRow(
    val recipeId: Long,
    val ingredientId: Long,
    val displayName: String,
    val nameKey: String,
    val type: IngredientType,
    val requiredAmount: Double?
)

data class ShoppingJoinedRow(
    val ingredientId: Long,
    val displayName: String,
    val nameKey: String,
    val type: IngredientType,
    val category: String,
    val quantity: Double?,
    val isBought: Boolean
)
