package com.example.grocerymanager.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.grocerymanager.data.NameNormalizer
import com.example.grocerymanager.data.local.AppDatabase
import com.example.grocerymanager.data.local.IngredientType
import com.example.grocerymanager.data.repo.GroceryRepository
import com.example.grocerymanager.data.repo.GroceryRepositoryImpl
import com.example.grocerymanager.data.repo.IngredientSuggestion
import com.example.grocerymanager.data.repo.IngredientUiItem
import com.example.grocerymanager.data.repo.NewIngredientMetaInput
import com.example.grocerymanager.data.repo.RecipeIngredientInput
import com.example.grocerymanager.data.repo.RecipeUiModel
import com.example.grocerymanager.data.repo.ShoppingUiItem
import com.example.grocerymanager.data.repo.StorageUiItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MainTab(val title: String, val shortLabel: String) {
    Storage("Storage", "S"),
    Recipes("Recipes", "R"),
    Shopping("Shopping", "L"),
    Ingredients("Ingredients", "I")
}

data class PendingIngredientPrompt(
    val ingredientName: String
)

private sealed class PendingAction {
    data class AddStorage(val name: String, val amount: Double?) : PendingAction()
    data class AddShopping(val name: String, val quantity: Double?) : PendingAction()
    data class AddRecipe(
        val recipeName: String,
        val ingredients: List<RecipeIngredientInput>
    ) : PendingAction()
}

class GroceryViewModel(
    private val repository: GroceryRepository
) : ViewModel() {
    private val _selectedTab = MutableStateFlow(MainTab.Storage)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    val storageItems: StateFlow<List<StorageUiItem>> =
        repository.observeStorage().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val recipes: StateFlow<List<RecipeUiModel>> =
        repository.observeRecipes().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val shoppingItems: StateFlow<List<ShoppingUiItem>> =
        repository.observeShopping().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val ingredients: StateFlow<List<IngredientUiItem>> =
        repository.observeIngredients().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _pendingPrompt = MutableStateFlow<PendingIngredientPrompt?>(null)
    val pendingPrompt: StateFlow<PendingIngredientPrompt?> = _pendingPrompt.asStateFlow()

    private var pendingAction: PendingAction? = null

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun searchIngredientSuggestions(prefix: String): Flow<List<IngredientSuggestion>> {
        return repository.searchIngredientSuggestions(prefix)
    }

    fun requestAddStorage(name: String, amount: Double?) {
        val normalized = NameNormalizer.normalizeName(name)
        if (normalized.isBlank()) return

        viewModelScope.launch {
            val existing = repository.findIngredientByName(normalized)
            if (existing == null) {
                pendingAction = PendingAction.AddStorage(normalized, amount)
                _pendingPrompt.value = PendingIngredientPrompt(normalized)
                return@launch
            }

            repository.addStorageItem(normalized, amount)
        }
    }

    fun removeStorageItem(ingredientId: Long) {
        viewModelScope.launch {
            repository.removeStorageItem(ingredientId)
        }
    }

    fun requestAddShopping(name: String, quantity: Double?) {
        val normalized = NameNormalizer.normalizeName(name)
        if (normalized.isBlank()) return

        viewModelScope.launch {
            val existing = repository.findIngredientByName(normalized)
            if (existing == null) {
                pendingAction = PendingAction.AddShopping(normalized, quantity)
                _pendingPrompt.value = PendingIngredientPrompt(normalized)
                return@launch
            }

            repository.addShoppingItem(normalized, quantity)
        }
    }

    fun toggleShoppingItem(ingredientId: Long) {
        viewModelScope.launch {
            repository.toggleShoppingItem(ingredientId)
        }
    }

    fun removeShoppingItem(ingredientId: Long) {
        viewModelScope.launch {
            repository.removeShoppingItem(ingredientId)
        }
    }

    fun moveBoughtToStorage() {
        viewModelScope.launch {
            repository.moveBoughtToStorage()
        }
    }

    fun requestAddRecipe(name: String, inputs: List<RecipeIngredientInput>) {
        viewModelScope.launch {
            addRecipeInternal(name, inputs)
        }
    }

    fun addMissingIngredientsToShopping(recipeId: Long) {
        viewModelScope.launch {
            repository.addMissingIngredientsToShopping(recipeId)
        }
    }

    fun cookRecipe(recipeId: Long) {
        viewModelScope.launch {
            repository.cookRecipe(recipeId)
        }
    }

    fun updateIngredientMetadata(
        ingredientId: Long,
        newType: IngredientType,
        category: String
    ) {
        viewModelScope.launch {
            repository.updateIngredientMetadata(ingredientId, newType, category)
        }
    }

    fun confirmPendingIngredient(meta: NewIngredientMetaInput) {
        val prompt = _pendingPrompt.value ?: return
        val action = pendingAction ?: return

        viewModelScope.launch {
            val created = repository.createIngredient(prompt.ingredientName, meta)
            if (created == null) return@launch

            pendingAction = null
            _pendingPrompt.value = null

            when (action) {
                is PendingAction.AddStorage -> repository.addStorageItem(action.name, action.amount)
                is PendingAction.AddShopping -> repository.addShoppingItem(action.name, action.quantity)
                is PendingAction.AddRecipe -> addRecipeInternal(action.recipeName, action.ingredients)
            }
        }
    }

    fun dismissPendingIngredient() {
        pendingAction = null
        _pendingPrompt.value = null
    }

    private suspend fun addRecipeInternal(name: String, inputs: List<RecipeIngredientInput>) {
        val normalizedRecipeName = NameNormalizer.normalizeName(name)
        if (normalizedRecipeName.isBlank()) return

        val cleanedInputs = inputs.mapNotNull { input ->
            val normalizedName = NameNormalizer.normalizeName(input.name)
            if (normalizedName.isBlank()) {
                null
            } else {
                RecipeIngredientInput(name = normalizedName, requiredAmount = input.requiredAmount)
            }
        }
        if (cleanedInputs.isEmpty()) return

        var unknown: RecipeIngredientInput? = null
        for (input in cleanedInputs) {
            if (repository.findIngredientByName(input.name) == null) {
                unknown = input
                break
            }
        }
        if (unknown != null) {
            pendingAction = PendingAction.AddRecipe(normalizedRecipeName, cleanedInputs)
            _pendingPrompt.value = PendingIngredientPrompt(unknown.name)
            return
        }

        repository.addRecipe(normalizedRecipeName, cleanedInputs)
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = AppDatabase.getInstance(appContext)
                    val repository = GroceryRepositoryImpl(db)
                    return GroceryViewModel(repository) as T
                }
            }
        }
    }
}
