package com.example.grocerymanager.data.repo

import com.example.grocerymanager.data.local.IngredientType

data class StorageUiItem(
    val ingredientId: Long,
    val name: String,
    val type: IngredientType,
    val category: String,
    val amount: Double?
)

data class RecipeIngredientUiItem(
    val ingredientId: Long,
    val name: String,
    val type: IngredientType,
    val requiredAmount: Double?
)

data class MissingIngredientUiItem(
    val ingredientId: Long,
    val name: String,
    val missingAmount: Double?
)

data class RecipeUiModel(
    val recipeId: Long,
    val name: String,
    val ingredients: List<RecipeIngredientUiItem>,
    val missing: List<MissingIngredientUiItem>
)

data class ShoppingUiItem(
    val ingredientId: Long,
    val name: String,
    val type: IngredientType,
    val category: String,
    val quantity: Double?,
    val isBought: Boolean
)

data class IngredientUiItem(
    val ingredientId: Long,
    val name: String,
    val type: IngredientType,
    val category: String
)

data class CategoryUiItem(
    val name: String,
    val ingredientCount: Int
)

data class IngredientSuggestion(
    val ingredientId: Long,
    val name: String,
    val type: IngredientType,
    val category: String
)

data class RecipeIngredientInput(
    val name: String,
    val requiredAmount: Double?
)

data class NewIngredientMetaInput(
    val type: IngredientType,
    val category: String
)
