package com.example.grocerymanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.grocerymanager.ui.theme.GroceryManagerTheme

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

private enum class MainTab(val title: String, val shortLabel: String) {
    Storage("Storage", "S"),
    Recipes("Recipes", "R"),
    Shopping("Shopping", "L")
}

private data class StorageItem(val name: String)

private data class Recipe(val name: String, val ingredients: List<String>)

private data class ShoppingItem(val name: String, val isBought: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroceryManagerApp() {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Storage) }

    val storageItems = remember {
        mutableStateListOf(
            StorageItem("Eggs"),
            StorageItem("Milk"),
            StorageItem("Rice"),
            StorageItem("Tomatoes")
        )
    }

    val recipes = remember {
        mutableStateListOf(
            Recipe("Omelet", listOf("Eggs", "Milk", "Salt")),
            Recipe("Tomato Rice", listOf("Rice", "Tomatoes", "Olive Oil")),
            Recipe("Pancakes", listOf("Eggs", "Milk", "Flour", "Sugar"))
        )
    }

    val shoppingItems = remember { mutableStateListOf<ShoppingItem>() }

    fun addStorageItem(name: String) {
        val normalized = normalizeName(name)
        if (normalized.isBlank()) return
        if (storageItems.any { nameKey(it.name) == nameKey(normalized) }) return
        storageItems.add(StorageItem(normalized))
    }

    fun removeStorageItem(item: StorageItem) {
        storageItems.remove(item)
    }

    fun addRecipe(name: String, ingredients: List<String>) {
        val normalizedName = normalizeName(name)
        if (normalizedName.isBlank()) return
        if (ingredients.isEmpty()) return
        recipes.add(Recipe(normalizedName, ingredients))
    }

    fun addMissingIngredientsToShopping(recipe: Recipe) {
        val missing = missingIngredients(recipe, storageItems)
        if (missing.isEmpty()) return
        val existingKeys = shoppingItems.map { nameKey(it.name) }.toSet()
        val newItems = missing.filter { nameKey(it) !in existingKeys }
        newItems.forEach { shoppingItems.add(ShoppingItem(it)) }
    }

    fun addShoppingItem(name: String) {
        val normalized = normalizeName(name)
        if (normalized.isBlank()) return
        if (shoppingItems.any { nameKey(it.name) == nameKey(normalized) }) return
        shoppingItems.add(ShoppingItem(normalized))
    }

    fun toggleShoppingItem(index: Int) {
        val item = shoppingItems[index]
        shoppingItems[index] = item.copy(isBought = !item.isBought)
    }

    fun removeShoppingItem(item: ShoppingItem) {
        shoppingItems.remove(item)
    }

    fun moveBoughtToStorage() {
        val bought = shoppingItems.filter { it.isBought }
        if (bought.isEmpty()) return
        bought.forEach { addStorageItem(it.name) }
        shoppingItems.removeAll { it.isBought }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(selectedTab.title) }) },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
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
                onAddItem = ::addStorageItem,
                onDeleteItem = ::removeStorageItem,
                modifier = Modifier.padding(padding)
            )
            MainTab.Recipes -> RecipesScreen(
                recipes = recipes,
                storageItems = storageItems,
                onAddRecipe = ::addRecipe,
                onAddMissingToShopping = ::addMissingIngredientsToShopping,
                modifier = Modifier.padding(padding)
            )
            MainTab.Shopping -> ShoppingScreen(
                shoppingItems = shoppingItems,
                onToggleBought = ::toggleShoppingItem,
                onRemoveItem = ::removeShoppingItem,
                onMoveBoughtToStorage = ::moveBoughtToStorage,
                onAddItem = ::addShoppingItem,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun StorageScreen(
    storageItems: List<StorageItem>,
    onAddItem: (String) -> Unit,
    onDeleteItem: (StorageItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var newItem by rememberSaveable { mutableStateOf("") }
    val canAddItem = newItem.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("What is currently in your kitchen?", fontWeight = FontWeight.SemiBold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = newItem,
                onValueChange = { newItem = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Add item") },
                singleLine = true
            )
            Button(
                onClick = {
                    onAddItem(newItem)
                    newItem = ""
                },
                enabled = canAddItem
            ) {
                Text("Add")
            }
        }

        if (storageItems.isEmpty()) {
            EmptyState("No storage items yet.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(storageItems, key = { nameKey(it.name) }) { item ->
                    OutlinedCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.name)
                            TextButton(onClick = { onDeleteItem(item) }) {
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
private fun RecipesScreen(
    recipes: List<Recipe>,
    storageItems: List<StorageItem>,
    onAddRecipe: (String, List<String>) -> Unit,
    onAddMissingToShopping: (Recipe) -> Unit,
    modifier: Modifier = Modifier
) {
    var recipeName by rememberSaveable { mutableStateOf("") }
    var ingredientInput by rememberSaveable { mutableStateOf("") }
    val parsedIngredients = parseIngredients(ingredientInput)

    val sortedRecipes = recipes.map { recipe ->
        val missing = missingIngredients(recipe, storageItems)
        RecipeUiModel(recipe, missing)
    }.sortedWith(
        compareBy<RecipeUiModel> { it.missing.isNotEmpty() }
            .thenBy { it.missing.size }
            .thenBy { it.recipe.name.lowercase() }
    )

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
                TextField(
                    value = recipeName,
                    onValueChange = { recipeName = it },
                    placeholder = { Text("Recipe name") },
                    singleLine = true
                )
                TextField(
                    value = ingredientInput,
                    onValueChange = { ingredientInput = it },
                    placeholder = { Text("Ingredients (comma separated)") },
                    singleLine = true
                )
                Button(
                    onClick = {
                        onAddRecipe(recipeName, parsedIngredients)
                        recipeName = ""
                        ingredientInput = ""
                    },
                    enabled = recipeName.isNotBlank() && parsedIngredients.isNotEmpty()
                ) {
                    Text("Add recipe")
                }
            }
        }

        if (sortedRecipes.isEmpty()) {
            EmptyState("No recipes yet.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sortedRecipes, key = { nameKey(it.recipe.name) }) { model ->
                    RecipeCard(
                        model = model,
                        onAddMissingToShopping = { onAddMissingToShopping(model.recipe) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipeCard(
    model: RecipeUiModel,
    onAddMissingToShopping: () -> Unit
) {
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(model.recipe.name, fontWeight = FontWeight.SemiBold)
            Text("Ingredients: ${model.recipe.ingredients.joinToString(", ")}")

            if (model.missing.isEmpty()) {
                Text("Cookable with current storage.")
                OutlinedButton(onClick = onAddMissingToShopping, enabled = false) {
                    Text("All ingredients available")
                }
            } else {
                Text("Missing (${model.missing.size}): ${model.missing.joinToString(", ")}")
                Button(onClick = onAddMissingToShopping) {
                    Text("Add missing to shopping list")
                }
            }
        }
    }
}

@Composable
private fun ShoppingScreen(
    shoppingItems: List<ShoppingItem>,
    onToggleBought: (Int) -> Unit,
    onRemoveItem: (ShoppingItem) -> Unit,
    onMoveBoughtToStorage: () -> Unit,
    onAddItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var newItem by rememberSaveable { mutableStateOf("") }
    val hasBought = shoppingItems.any { it.isBought }
    val canAddItem = newItem.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Pick up items and move them to storage.", fontWeight = FontWeight.SemiBold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = newItem,
                onValueChange = { newItem = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Add shopping item") },
                singleLine = true
            )
            Button(
                onClick = {
                    onAddItem(newItem)
                    newItem = ""
                },
                enabled = canAddItem
            ) {
                Text("Add")
            }
        }

        Button(
            onClick = onMoveBoughtToStorage,
            enabled = hasBought
        ) {
            Text("Move bought items to storage")
        }

        if (shoppingItems.isEmpty()) {
            EmptyState("Shopping list is empty.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shoppingItems, key = { nameKey(it.name) }) { item ->
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
                                onCheckedChange = {
                                    val index = shoppingItems.indexOf(item)
                                    if (index >= 0) onToggleBought(index)
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    textDecoration = if (item.isBought) TextDecoration.LineThrough else null
                                )
                                if (item.isBought) {
                                    Text("Ready to move to storage")
                                }
                            }
                            TextButton(onClick = { onRemoveItem(item) }) {
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

private data class RecipeUiModel(val recipe: Recipe, val missing: List<String>)

private fun parseIngredients(input: String): List<String> {
    return input.split(",")
        .map { normalizeName(it) }
        .filter { it.isNotBlank() }
        .distinctBy { nameKey(it) }
}

private fun missingIngredients(recipe: Recipe, storageItems: List<StorageItem>): List<String> {
    val storageKeys = storageItems.map { nameKey(it.name) }.toSet()
    return recipe.ingredients.filter { nameKey(it) !in storageKeys }
}

private fun normalizeName(input: String): String {
    return input.trim().replace(Regex("\\s+"), " ")
}

private fun nameKey(input: String): String {
    return normalizeName(input).lowercase()
}

@Preview(showBackground = true)
@Composable
private fun GroceryManagerPreview() {
    GroceryManagerTheme {
        GroceryManagerApp()
    }
}
