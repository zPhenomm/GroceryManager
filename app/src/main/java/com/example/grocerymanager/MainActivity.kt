package com.example.grocerymanager

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GroceryManagerApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroceryManagerApp() {
    val context = LocalContext.current
    val activity = context as? Activity
    val showExitDialogState = rememberSaveable { mutableStateOf(false) }
    val isCreatingRecipeState = rememberSaveable { mutableStateOf(false) }
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
    if (showExitDialogState.value) {
        ConfirmDeleteDialog(
            title = "Exit app",
            message = "Do you want to exit?",
            confirmLabel = "Exit",
            onConfirm = { activity?.finish() },
            onDismiss = { showExitDialogState.value = false }
        )
    }

    BackHandler(enabled = !showExitDialogState.value) {
        showExitDialogState.value = true
    }

    GroceryManagerTheme {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(selectedTab.title) },
                    actions = {
                        if (selectedTab == MainTab.Recipes && !isCreatingRecipeState.value) {
                            TextButton(onClick = { isCreatingRecipeState.value = true }) {
                                Text(
                                    text = "Create",
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp)
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                MainBottomBar(
                    selectedTab = selectedTab,
                    onSelectTab = viewModel::selectTab
                )
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
                    isCreatingRecipe = isCreatingRecipeState.value,
                    onDoneCreatingRecipe = { isCreatingRecipeState.value = false },
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
}

@Composable
private fun MainBottomBar(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit
) {
    Surface(
        shadowElevation = 3.dp,
        tonalElevation = 1.dp,
        modifier = Modifier.navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                TextButton(
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = tab.title,
                        style= MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.5.sp
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
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
    val showSuggestions = remember(newItem) { NameNormalizer.nameKey(newItem).length >= 2 }
    val visibleSuggestions = remember(filteredSuggestions, showSuggestions) {
        if (showSuggestions) filteredSuggestions else emptyList()
    }
    val showAmount = newItem.isNotBlank() && (matched == null || matched.type == IngredientType.QUANTITY_TRACKED)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = newItem,
            onValueChange = { newItem = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ingredient") },
            singleLine = true
        )
        IngredientSuggestionList(
            suggestions = visibleSuggestions,
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
    isCreatingRecipe: Boolean,
    onDoneCreatingRecipe: () -> Unit,
    suggestionProvider: (String) -> kotlinx.coroutines.flow.Flow<List<IngredientSuggestion>>,
    modifier: Modifier = Modifier
) {
    val recipeNameState = rememberSaveable { mutableStateOf("") }
    val nextRowIdState = rememberSaveable { mutableIntStateOf(2) }
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

    if (isCreatingRecipe) {
        RecipeCreationScreen(
            recipeName = recipeNameState.value,
            onRecipeNameChange = { recipeNameState.value = it },
            draftIngredients = draftIngredients,
            onIngredientChange = { index, updated -> draftIngredients[index] = updated },
            onRemoveIngredient = { index -> draftIngredients.removeAt(index) },
            onAddIngredientRow = {
                draftIngredients.add(
                    RecipeDraftIngredient(
                        id = nextRowIdState.intValue,
                        name = "",
                        amountText = ""
                    )
                )
                nextRowIdState.intValue += 1
            },
            onSubmit = {
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
                onAddRecipe(recipeNameState.value, inputs)
                recipeNameState.value = ""
                draftIngredients.clear()
                draftIngredients.add(RecipeDraftIngredient(id = 1, name = "", amountText = ""))
                nextRowIdState.intValue = 2
                onDoneCreatingRecipe()
            },
            onCancel = onDoneCreatingRecipe,
            canSubmit = recipeNameState.value.isNotBlank() &&
                draftIngredients.any { NameNormalizer.normalizeName(it.name).isNotBlank() },
            suggestionProvider = suggestionProvider,
            modifier = modifier
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
}

@Composable
private fun RecipeCreationScreen(
    recipeName: String,
    onRecipeNameChange: (String) -> Unit,
    draftIngredients: List<RecipeDraftIngredient>,
    onIngredientChange: (Int, RecipeDraftIngredient) -> Unit,
    onRemoveIngredient: (Int) -> Unit,
    onAddIngredientRow: () -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    canSubmit: Boolean,
    suggestionProvider: (String) -> kotlinx.coroutines.flow.Flow<List<IngredientSuggestion>>,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onCancel)
    val ingredientListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Create recipe", fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onCancel) { Text("Back") }
        }

        OutlinedTextField(
            value = recipeName,
            onValueChange = onRecipeNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Recipe name") },
            singleLine = true
        )

        LazyColumn(
            state = ingredientListState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(draftIngredients, key = { _, row -> row.id }) { index, row ->
                RecipeIngredientRow(
                    row = row,
                    canRemove = draftIngredients.size > 1,
                    onRowChange = { updated -> onIngredientChange(index, updated) },
                    onRemove = { onRemoveIngredient(index) },
                    onAnyFieldFocused = {
                        coroutineScope.launch {
                            ingredientListState.animateScrollToItem(index)
                        }
                    },
                    suggestionProvider = suggestionProvider
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAddIngredientRow) {
                Text("Add ingredient row")
            }
            Button(onClick = onSubmit, enabled = canSubmit) {
                Text("Add recipe")
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
    onAnyFieldFocused: () -> Unit,
    suggestionProvider: (String) -> kotlinx.coroutines.flow.Flow<List<IngredientSuggestion>>
) {
    val suggestionsFlow = remember(row.name) { suggestionProvider(row.name) }
    val suggestions by suggestionsFlow.collectAsState(initial = emptyList())
    val matched = suggestions.firstOrNull { NameNormalizer.nameKey(it.name) == NameNormalizer.nameKey(row.name) }
    val showSuggestions = remember(row.name) { NameNormalizer.nameKey(row.name).length >= 2 }
    val visibleSuggestions = remember(suggestions, showSuggestions) {
        if (showSuggestions) suggestions else emptyList()
    }
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
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged {
                            if (it.isFocused) onAnyFieldFocused()
                        },
                    label = { Text("Ingredient") },
                    singleLine = true
                )
                TextButton(onClick = onRemove, enabled = canRemove) {
                    Text("Remove")
                }
            }

            IngredientSuggestionList(
                suggestions = visibleSuggestions,
                onSelect = { onRowChange(row.copy(name = it)) }
            )

            if (showAmount) {
                OutlinedTextField(
                    value = row.amountText,
                    onValueChange = { onRowChange(row.copy(amountText = it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) onAnyFieldFocused()
                        },
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
    var isAddingItem by rememberSaveable { mutableStateOf(false) }
    val hasBought = shoppingItems.any { it.isBought }
    val unboughtItems = remember(shoppingItems) { shoppingItems.filterNot { it.isBought } }
    val boughtItems = remember(shoppingItems) { shoppingItems.filter { it.isBought } }
    val unboughtByCategory = remember(unboughtItems) { unboughtItems.groupBy { it.category } }
    val suggestionsFlow = remember(newItem, isAddingItem) {
        if (isAddingItem) {
            suggestionProvider(newItem)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }
    val suggestions by suggestionsFlow.collectAsState(initial = emptyList())
    val matched = suggestions.firstOrNull { NameNormalizer.nameKey(it.name) == NameNormalizer.nameKey(newItem) }
    val showSuggestions = remember(newItem, isAddingItem) {
        isAddingItem && NameNormalizer.nameKey(newItem).length >= 2
    }
    val visibleSuggestions = remember(suggestions, showSuggestions) {
        if (showSuggestions) suggestions else emptyList()
    }
    val showQuantity = isAddingItem && newItem.isNotBlank() &&
        (matched == null || matched.type == IngredientType.QUANTITY_TRACKED)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (isAddingItem) {
                        onAddItem(newItem, parsePositiveDouble(quantityText))
                        newItem = ""
                        quantityText = ""
                        isAddingItem = false
                    } else {
                        isAddingItem = true
                    }
                },
                enabled = !isAddingItem || newItem.isNotBlank()
            ) {
                Text(if (isAddingItem) "Confirm add" else "Add item")
            }

            Button(onClick = onMoveBoughtToStorage, enabled = hasBought) {
                Text("Move bought items to storage")
            }
        }

        if (isAddingItem) {
            OutlinedTextField(
                value = newItem,
                onValueChange = { newItem = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ingredient") },
                singleLine = true
            )
            IngredientSuggestionList(
                suggestions = visibleSuggestions,
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        newItem = ""
                        quantityText = ""
                        isAddingItem = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        }

        if (shoppingItems.isEmpty()) {
            EmptyState("Shopping list is empty.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var categoryIndex = 0
                unboughtByCategory.forEach { (category, itemsInCategory) ->
                    if (categoryIndex > 0) {
                        item(key = "category_spacer_${NameNormalizer.nameKey(category)}") {
                            Spacer(modifier = Modifier.height(3.dp))
                        }
                    }
                    item(key = "category_header_${NameNormalizer.nameKey(category)}") {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(itemsInCategory, key = { it.ingredientId }) { item ->
                        ShoppingItemCard(
                            item = item,
                            onToggleBought = onToggleBought,
                            onRemoveItem = onRemoveItem
                        )
                    }
                    categoryIndex += 1
                }

                if (unboughtItems.isNotEmpty() && boughtItems.isNotEmpty()) {
                    item(key = "bought_section_spacer") {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                items(boughtItems, key = { it.ingredientId }) { item ->
                    ShoppingItemCard(
                        item = item,
                        onToggleBought = onToggleBought,
                        onRemoveItem = onRemoveItem
                    )
                }
            }
        }
    }
}

@Composable
private fun ShoppingItemCard(
    item: ShoppingUiItem,
    onToggleBought: (Long) -> Unit,
    onRemoveItem: (Long) -> Unit
) {
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
    val deleteCategoryNameState = rememberSaveable { mutableStateOf<String?>(null) }
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
    deleteCategoryNameState.value?.let { categoryName ->
        ConfirmDeleteDialog(
            title = "Delete category",
            message = "Delete $categoryName? Ingredients in this category will be moved to $DEFAULT_CATEGORY_NAME.",
            onConfirm = {
                onDeleteCategory(categoryName)
                deleteCategoryNameState.value = null
            },
            onDismiss = { deleteCategoryNameState.value = null }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search ingredients/categories") },
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
                                    Text(item.type.readableLabel())
                                    Text(item.category)
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
                    Spacer(modifier = Modifier.height(4.dp))
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
                                    onClick = { deleteCategoryNameState.value = category.name },
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
    confirmLabel: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(confirmLabel)
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
    BackHandler(onBack = onDismiss)

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
    BackHandler(onBack = onDismiss)

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
