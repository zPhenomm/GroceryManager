package com.example.grocerymanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.grocerymanager.data.NameNormalizer
import com.example.grocerymanager.data.local.IngredientType
import com.example.grocerymanager.data.repo.CategoryUiItem
import com.example.grocerymanager.data.repo.IngredientSuggestion
import com.example.grocerymanager.data.repo.IngredientUiItem
import com.example.grocerymanager.data.repo.NewIngredientMetaInput
import com.example.grocerymanager.data.repo.RecipeIngredientInput
import com.example.grocerymanager.data.repo.RecipeUiModel
import com.example.grocerymanager.data.repo.ShoppingUiItem
import com.example.grocerymanager.data.repo.StorageUiItem
import com.example.grocerymanager.ui.GroceryViewModel
import com.example.grocerymanager.ui.MainTab
import com.example.grocerymanager.ui.theme.GroceryManagerTheme
import kotlin.math.abs
import java.util.Locale


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GroceryManagerTheme {
                GroceryManagerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroceryManagerApp() {
    val context = LocalContext.current
    val viewModel: GroceryViewModel = viewModel(factory = GroceryViewModel.factory(context = context))
    val selectedTab by viewModel.selectedTab.collectAsState()
    val storageItems by viewModel.storageItems.collectAsState()
    val recipes by viewModel.recipes.collectAsState()
    val shoppingItems by viewModel.shoppingItems.collectAsState()
    val ingredients by viewModel.ingredients.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val pendingPrompt by viewModel.pendingPrompt.collectAsState()
    val knownCategories = remember(categories) {
        categories.map { it.name }
    }

    pendingPrompt?.let { prompt ->
        NewIngredientDialog(
            ingredientName = prompt.ingredientName,
            categorySuggestions = knownCategories,
            onConfirm = { type, category ->
                viewModel.confirmPendingIngredient(
                    NewIngredientMetaInput(
                        type = type,
                        category = category
                    )
                )
            },
            onDismiss = viewModel::dismissPendingIngredient
        )
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(selectedTab.title) }) },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = { Text(tab.shortLabel) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            MainTab.Storage -> StorageScreen(
                storageItems = storageItems,
                onAddItem = viewModel::requestAddStorage,
                onDeleteItem = { viewModel.removeStorageItem(it) },
                suggestionProvider = viewModel::searchIngredientSuggestions,
                modifier = Modifier.padding(padding)
            )

            MainTab.Recipes -> RecipesScreen(
                recipes = recipes,
                onAddRecipe = viewModel::requestAddRecipe,
                onDeleteRecipe = viewModel::deleteRecipe,
                onAddMissingToShopping = viewModel::addMissingIngredientsToShopping,
                onCookRecipe = viewModel::cookRecipe,
                suggestionProvider = viewModel::searchIngredientSuggestions,
                modifier = Modifier.padding(padding)
            )

            MainTab.Shopping -> ShoppingScreen(
                shoppingItems = shoppingItems,
                onToggleBought = { viewModel.toggleShoppingItem(it) },
                onRemoveItem = { viewModel.removeShoppingItem(it) },
                onMoveBoughtToStorage = viewModel::moveBoughtToStorage,
                onAddItem = viewModel::requestAddShopping,
                suggestionProvider = viewModel::searchIngredientSuggestions,
                modifier = Modifier.padding(padding)
            )

            MainTab.Ingredients -> IngredientsScreen(
                ingredients = ingredients,
                categories = categories,
                onUpdateIngredient = viewModel::updateIngredientMetadata,
                onDeleteIngredient = viewModel::deleteIngredient,
                onDeleteCategory = viewModel::deleteCategory,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun StorageScreen(
    storageItems: List<StorageUiItem>,
    onAddItem: (String, Double?) -> Unit,
    onDeleteItem: (Long) -> Unit,
    suggestionProvider: (String) -> kotlinx.coroutines.flow.Flow<List<IngredientSuggestion>>,
    modifier: Modifier = Modifier
) {
    var newItem by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    val presenceOnlyKeysInStorage = remember(storageItems) {
        storageItems
            .filter { it.type == IngredientType.PRESENCE_ONLY }
            .map { NameNormalizer.nameKey(it.name) }
            .toSet()
    }
    val suggestionsFlow = remember(newItem) { suggestionProvider(newItem) }
    val suggestions by suggestionsFlow.collectAsState(initial = emptyList())
    val matched = suggestions.firstOrNull { NameNormalizer.nameKey(it.name) == NameNormalizer.nameKey(newItem) }
    val filteredSuggestions = remember(suggestions, presenceOnlyKeysInStorage) {
        suggestions.filter { NameNormalizer.nameKey(it.name) !in presenceOnlyKeysInStorage }
    }
    val showAmount = newItem.isNotBlank() && (matched == null || matched.type == IngredientType.QUANTITY_TRACKED)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("What is currently in your kitchen?", fontWeight = FontWeight.SemiBold)

        OutlinedTextField(
            value = newItem,
            onValueChange = { newItem = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ingredient") },
            singleLine = true
        )
        IngredientSuggestionList(
            suggestions = filteredSuggestions,
            onSelect = { newItem = it }
        )

        if (showAmount) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Amount (optional, default 1)") },
                singleLine = true
            )
        }

        Button(
            onClick = {
                onAddItem(newItem, parsePositiveDouble(amountText))
                newItem = ""
                amountText = ""
            },
            enabled = newItem.isNotBlank()
        ) {
            Text("Add to storage")
        }

        if (storageItems.isEmpty()) {
            EmptyState("No storage items yet.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(storageItems, key = { it.ingredientId }) { item ->
                    OutlinedCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name)
                                when (item.type) {
                                    IngredientType.PRESENCE_ONLY -> Text("Available")
                                    IngredientType.QUANTITY_TRACKED -> Text("Amount: ${formatAmount(item.amount)}")
                                }
                            }
                            TextButton(onClick = { onDeleteItem(item.ingredientId) }) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class RecipeDraftIngredient(
    val id: Int,
    val name: String,
    val amountText: String
)

@Composable
private fun RecipesScreen(
    recipes: List<RecipeUiModel>,
    onAddRecipe: (String, List<RecipeIngredientInput>) -> Unit,
    onDeleteRecipe: (Long) -> Unit,
    onAddMissingToShopping: (Long) -> Unit,
    onCookRecipe: (Long) -> Unit,
    suggestionProvider: (String) -> kotlinx.coroutines.flow.Flow<List<IngredientSuggestion>>,
    modifier: Modifier = Modifier
) {
    var recipeName by rememberSaveable { mutableStateOf("") }
    var nextRowId by rememberSaveable { mutableIntStateOf(2) }
    var deleteRecipeId by rememberSaveable { mutableStateOf<Long?>(null) }
    val draftIngredients = remember {
        mutableStateListOf(RecipeDraftIngredient(id = 1, name = "", amountText = ""))
    }
    val deleteRecipeItem = remember(recipes, deleteRecipeId) {
        deleteRecipeId?.let { id -> recipes.firstOrNull { it.recipeId == id } }
    }

    deleteRecipeItem?.let { recipe ->
        ConfirmDeleteDialog(
            title = "Delete recipe",
            message = "Delete ${recipe.name}?",
            onConfirm = {
                onDeleteRecipe(recipe.recipeId)
                deleteRecipeId = null
            },
            onDismiss = { deleteRecipeId = null }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Create recipes and check if they are cookable.", fontWeight = FontWeight.SemiBold)

        OutlinedCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = recipeName,
                    onValueChange = { recipeName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Recipe name") },
                    singleLine = true
                )

                draftIngredients.forEachIndexed { index, row ->
                    RecipeIngredientRow(
                        row = row,
                        canRemove = draftIngredients.size > 1,
                        onRowChange = { updated -> draftIngredients[index] = updated },
                        onRemove = { draftIngredients.removeAt(index) },
                        suggestionProvider = suggestionProvider
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            draftIngredients.add(
                                RecipeDraftIngredient(
                                    id = nextRowId,
                                    name = "",
                                    amountText = ""
                                )
                            )
                            nextRowId += 1
                        }
                    ) {
                        Text("Add ingredient row")
                    }

                    Button(
                        onClick = {
                            val inputs = draftIngredients.mapNotNull {
                                val normalized = NameNormalizer.normalizeName(it.name)
                                if (normalized.isBlank()) {
                                    null
                                } else {
                                    RecipeIngredientInput(
                                        name = normalized,
                                        requiredAmount = parsePositiveDouble(it.amountText)
                                    )
                                }
                            }
                            onAddRecipe(recipeName, inputs)
                            recipeName = ""
                            draftIngredients.clear()
                            draftIngredients.add(RecipeDraftIngredient(id = 1, name = "", amountText = ""))
                            nextRowId = 2
                        },
                        enabled = recipeName.isNotBlank() &&
                            draftIngredients.any { NameNormalizer.normalizeName(it.name).isNotBlank() }
                    ) {
                        Text("Add recipe")
                    }
                }
            }
        }

        if (recipes.isEmpty()) {
            EmptyState("No recipes yet.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recipes, key = { it.recipeId }) { recipe ->
                    RecipeCard(
                        model = recipe,
                        onDeleteRecipe = { deleteRecipeId = recipe.recipeId },
                        onAddMissingToShopping = { onAddMissingToShopping(recipe.recipeId) },
                        onCookRecipe = { onCookRecipe(recipe.recipeId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipeIngredientRow(
    row: RecipeDraftIngredient,
    canRemove: Boolean,
    onRowChange: (RecipeDraftIngredient) -> Unit,
    onRemove: () -> Unit,
    suggestionProvider: (String) -> kotlinx.coroutines.flow.Flow<List<IngredientSuggestion>>
) {
    val suggestionsFlow = remember(row.name) { suggestionProvider(row.name) }
    val suggestions by suggestionsFlow.collectAsState(initial = emptyList())
    val matched = suggestions.firstOrNull { NameNormalizer.nameKey(it.name) == NameNormalizer.nameKey(row.name) }
    val showAmount = row.name.isNotBlank() && (matched == null || matched.type == IngredientType.QUANTITY_TRACKED)

    OutlinedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = row.name,
                    onValueChange = { onRowChange(row.copy(name = it)) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Ingredient") },
                    singleLine = true
                )
                TextButton(onClick = onRemove, enabled = canRemove) {
                    Text("Remove")
                }
            }

            IngredientSuggestionList(
                suggestions = suggestions,
                onSelect = { onRowChange(row.copy(name = it)) }
            )

            if (showAmount) {
                OutlinedTextField(
                    value = row.amountText,
                    onValueChange = { onRowChange(row.copy(amountText = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Required amount (optional, default 1)") },
                    singleLine = true
                )
            }
        }
    }
}

@Composable
private fun RecipeCard(
    model: RecipeUiModel,
    onDeleteRecipe: () -> Unit,
    onAddMissingToShopping: () -> Unit,
    onCookRecipe: () -> Unit
) {
    val isCookable = model.missing.isEmpty()

    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(model.name, fontWeight = FontWeight.SemiBold)
                if (isCookable) {
                    Text(
                        text = "\u2713",
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                "Ingredients: ${
                    model.ingredients.joinToString(", ") { ingredient ->
                        when (ingredient.type) {
                            IngredientType.PRESENCE_ONLY -> ingredient.name
                            IngredientType.QUANTITY_TRACKED ->
                                "${ingredient.name} (${formatAmount(ingredient.requiredAmount)})"
                        }
                    }
                }"
            )

            if (isCookable) {
                Text("Cookable with current storage.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCookRecipe) {
                        Text("Cooked")
                    }
                    TextButton(onClick = onDeleteRecipe) {
                        Text("Delete")
                    }
                }
            } else {
                Text(
                    "Missing (${model.missing.size}): ${
                        model.missing.joinToString(", ") {
                            if (it.missingAmount == null) it.name else "${it.name} (${formatAmount(it.missingAmount)})"
                        }
                    }"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAddMissingToShopping) {
                        Text("Add missing to shopping list")
                    }
                    TextButton(onClick = onDeleteRecipe) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun ShoppingScreen(
    shoppingItems: List<ShoppingUiItem>,
    onToggleBought: (Long) -> Unit,
    onRemoveItem: (Long) -> Unit,
    onMoveBoughtToStorage: () -> Unit,
    onAddItem: (String, Double?) -> Unit,
    suggestionProvider: (String) -> kotlinx.coroutines.flow.Flow<List<IngredientSuggestion>>,
    modifier: Modifier = Modifier
) {
    var newItem by rememberSaveable { mutableStateOf("") }
    var quantityText by rememberSaveable { mutableStateOf("") }
    val hasBought = shoppingItems.any { it.isBought }
    val firstBoughtIndex = shoppingItems.indexOfFirst { it.isBought }
    val suggestionsFlow = remember(newItem) { suggestionProvider(newItem) }
    val suggestions by suggestionsFlow.collectAsState(initial = emptyList())
    val matched = suggestions.firstOrNull { NameNormalizer.nameKey(it.name) == NameNormalizer.nameKey(newItem) }
    val showQuantity = newItem.isNotBlank() && (matched == null || matched.type == IngredientType.QUANTITY_TRACKED)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Pick up items and move them to storage.", fontWeight = FontWeight.SemiBold)

        OutlinedTextField(
            value = newItem,
            onValueChange = { newItem = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ingredient") },
            singleLine = true
        )
        IngredientSuggestionList(
            suggestions = suggestions,
            onSelect = { newItem = it }
        )

        if (showQuantity) {
            OutlinedTextField(
                value = quantityText,
                onValueChange = { quantityText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Quantity (optional, default 1)") },
                singleLine = true
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onAddItem(newItem, parsePositiveDouble(quantityText))
                    newItem = ""
                    quantityText = ""
                },
                enabled = newItem.isNotBlank()
            ) {
                Text("Add")
            }

            Button(onClick = onMoveBoughtToStorage, enabled = hasBought) {
                Text("Move bought items to storage")
            }
        }

        if (shoppingItems.isEmpty()) {
            EmptyState("Shopping list is empty.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = shoppingItems,
                    key = { _, item -> item.ingredientId }
                ) { index, item ->
                    if (index == firstBoughtIndex && index > 0) {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                    OutlinedCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = item.isBought,
                                onCheckedChange = { onToggleBought(item.ingredientId) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when (item.type) {
                                        IngredientType.PRESENCE_ONLY -> item.name
                                        IngredientType.QUANTITY_TRACKED ->
                                            "${item.name} (${formatAmount(item.quantity)})"
                                    },
                                    textDecoration = if (item.isBought) TextDecoration.LineThrough else null
                                )
                                if (item.isBought) {
                                    Text("Ready to move to storage")
                                }
                            }
                            TextButton(onClick = { onRemoveItem(item.ingredientId) }) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IngredientsScreen(
    ingredients: List<IngredientUiItem>,
    categories: List<CategoryUiItem>,
    onUpdateIngredient: (Long, IngredientType, String) -> Unit,
    onDeleteIngredient: (Long) -> Unit,
    onDeleteCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var search by rememberSaveable { mutableStateOf("") }
    var editingIngredientId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteIngredientId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val editingItem = remember(ingredients, editingIngredientId) {
        editingIngredientId?.let { id -> ingredients.firstOrNull { it.ingredientId == id } }
    }
    val deleteIngredientItem = remember(ingredients, deleteIngredientId) {
        deleteIngredientId?.let { id -> ingredients.firstOrNull { it.ingredientId == id } }
    }
    val searchKey = remember(search) { NameNormalizer.nameKey(search) }

    val filteredIngredients = remember(ingredients, searchKey) {
        if (searchKey.isBlank()) {
            ingredients
        } else {
            ingredients.filter {
                NameNormalizer.nameKey(it.name).contains(searchKey) ||
                    NameNormalizer.nameKey(it.category).contains(searchKey)
            }
        }
    }
    val filteredCategories = remember(categories, searchKey) {
        if (searchKey.isBlank()) {
            categories
        } else {
            categories.filter { NameNormalizer.nameKey(it.name).contains(searchKey) }
        }
    }

    editingItem?.let { item ->
        EditIngredientDialog(
            ingredient = item,
            categorySuggestions = categories.map { it.name },
            onConfirm = { type, category ->
                onUpdateIngredient(item.ingredientId, type, category)
                editingIngredientId = null
            },
            onDismiss = { editingIngredientId = null }
        )
    }
    deleteIngredientItem?.let { item ->
        ConfirmDeleteDialog(
            title = "Delete ingredient",
            message = "Delete ${item.name}? This removes it from storage, recipes and shopping list.",
            onConfirm = {
                onDeleteIngredient(item.ingredientId)
                deleteIngredientId = null
            },
            onDismiss = { deleteIngredientId = null }
        )
    }
    deleteCategoryName?.let { categoryName ->
        ConfirmDeleteDialog(
            title = "Delete category",
            message = "Delete $categoryName? Ingredients in this category will be moved to $DEFAULT_CATEGORY_NAME.",
            onConfirm = {
                onDeleteCategory(categoryName)
                deleteCategoryName = null
            },
            onDismiss = { deleteCategoryName = null }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Manage ingredient types and categories.", fontWeight = FontWeight.SemiBold)

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search ingredients") },
            singleLine = true
        )

        if (filteredIngredients.isEmpty() && filteredCategories.isEmpty()) {
            EmptyState("No ingredients or categories found.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "ingredients_header") {
                    Text("Ingredients", fontWeight = FontWeight.SemiBold)
                }

                if (filteredIngredients.isEmpty()) {
                    item(key = "ingredients_empty") { Text("No ingredients found.") }
                } else {
                    items(filteredIngredients, key = { it.ingredientId }) { item ->
                        OutlinedCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.SemiBold)
                                    Text("Type: ${item.type.readableLabel()}")
                                    Text("Category: ${item.category}")
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { editingIngredientId = item.ingredientId }) {
                                        Text("Edit")
                                    }
                                    TextButton(onClick = { deleteIngredientId = item.ingredientId }) {
                                        Text("Delete")
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "categories_spacer") {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                item(key = "categories_header") {
                    Text("Categories", fontWeight = FontWeight.SemiBold)
                }

                if (filteredCategories.isEmpty()) {
                    item(key = "categories_empty") { Text("No categories found.") }
                } else {
                    items(filteredCategories, key = { NameNormalizer.nameKey(it.name) }) { category ->
                        val isDefaultCategory =
                            NameNormalizer.nameKey(category.name) == NameNormalizer.nameKey(DEFAULT_CATEGORY_NAME)
                        OutlinedCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(category.name, fontWeight = FontWeight.SemiBold)
                                    Text("${category.ingredientCount} ingredient(s)")
                                }
                                TextButton(
                                    onClick = { deleteCategoryName = category.name },
                                    enabled = !isDefaultCategory
                                ) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditIngredientDialog(
    ingredient: IngredientUiItem,
    categorySuggestions: List<String>,
    onConfirm: (IngredientType, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember(ingredient.ingredientId) { mutableStateOf(ingredient.type) }
    var category by remember(ingredient.ingredientId) { mutableStateOf(ingredient.category) }
    val filteredCategorySuggestions = remember(category, categorySuggestions) {
        val categoryKey = NameNormalizer.nameKey(category)
        if (categoryKey.length < 2) {
            return@remember emptyList()
        }
        categorySuggestions
            .filter { NameNormalizer.nameKey(it).startsWith(categoryKey) }
            .filter { NameNormalizer.nameKey(it) != categoryKey }
            .take(6)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${ingredient.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Category") },
                    singleLine = true
                )
                CategorySuggestionList(
                    suggestions = filteredCategorySuggestions,
                    onSelect = { category = it }
                )
                IngredientTypeSelector(
                    selectedType = selectedType,
                    onTypeSelected = { selectedType = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedType, category) },
                enabled = NameNormalizer.normalizeName(category).isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun NewIngredientDialog(
    ingredientName: String,
    categorySuggestions: List<String>,
    onConfirm: (IngredientType, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember(ingredientName) { mutableStateOf(IngredientType.QUANTITY_TRACKED) }
    var category by remember(ingredientName) { mutableStateOf("") }
    val filteredCategorySuggestions = remember(category, categorySuggestions) {
        val categoryKey = NameNormalizer.nameKey(category)
        if (categoryKey.length < 2) {
            return@remember emptyList()
        }
        categorySuggestions
            .filter { NameNormalizer.nameKey(it).startsWith(categoryKey) }
            .filter { NameNormalizer.nameKey(it) != categoryKey }
            .take(6)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create ingredient") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ingredientName)
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Category") },
                    singleLine = true
                )
                CategorySuggestionList(
                    suggestions = filteredCategorySuggestions,
                    onSelect = { category = it }
                )
                IngredientTypeSelector(
                    selectedType = selectedType,
                    onTypeSelected = { selectedType = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedType, category) },
                enabled = NameNormalizer.normalizeName(category).isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun IngredientTypeSelector(
    selectedType: IngredientType,
    onTypeSelected: (IngredientType) -> Unit
) {
    val selectedBorder = BorderStroke(width = 2.dp, color = MaterialTheme.colorScheme.primary)
    val defaultBorder = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outline)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { onTypeSelected(IngredientType.QUANTITY_TRACKED) },
            border = if (selectedType == IngredientType.QUANTITY_TRACKED) selectedBorder else defaultBorder
        ) {
            Text("Quantity")
        }
        OutlinedButton(
            onClick = { onTypeSelected(IngredientType.PRESENCE_ONLY) },
            border = if (selectedType == IngredientType.PRESENCE_ONLY) selectedBorder else defaultBorder
        ) {
            Text("Presence")
        }
    }
}

@Composable
private fun IngredientSuggestionList(
    suggestions: List<IngredientSuggestion>,
    onSelect: (String) -> Unit
) {
    if (suggestions.isEmpty()) return

    OutlinedCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            suggestions.forEach { suggestion ->
                TextButton(
                    onClick = { onSelect(suggestion.name) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${suggestion.name} (${suggestion.category})")
                }
            }
        }
    }
}

@Composable
private fun CategorySuggestionList(
    suggestions: List<String>,
    onSelect: (String) -> Unit
) {
    if (suggestions.isEmpty()) return

    OutlinedCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            suggestions.forEach { suggestion ->
                TextButton(
                    onClick = { onSelect(suggestion) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(suggestion)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message)
    }
}

private fun parsePositiveDouble(input: String): Double? {
    val value = input.trim().replace(",", ".").toDoubleOrNull() ?: return null
    return if (value > 0.0) value else null
}

private fun formatAmount(amount: Double?): String {
    if (amount == null) return "-"
    val asLong = amount.toLong().toDouble()
    return if (abs(amount - asLong) < 1e-9) {
        asLong.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", amount).trimEnd('0').trimEnd('.')
    }
}

private fun IngredientType.readableLabel(): String {
    return when (this) {
        IngredientType.QUANTITY_TRACKED -> "quantity"
        IngredientType.PRESENCE_ONLY -> "presence"
    }
}

private const val DEFAULT_CATEGORY_NAME = "Uncategorized"
