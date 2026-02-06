package com.example.grocerymanager.data.repo

import androidx.room.withTransaction
import com.example.grocerymanager.data.NameNormalizer
import com.example.grocerymanager.data.local.AppDatabase
import com.example.grocerymanager.data.local.IngredientEntity
import com.example.grocerymanager.data.local.IngredientType
import com.example.grocerymanager.data.local.RecipeEntity
import com.example.grocerymanager.data.local.RecipeIngredientEntity
import com.example.grocerymanager.data.local.ShoppingItemEntity
import com.example.grocerymanager.data.local.StorageEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface GroceryRepository {
    fun observeStorage(): Flow<List<StorageUiItem>>
    fun observeRecipes(): Flow<List<RecipeUiModel>>
    fun observeShopping(): Flow<List<ShoppingUiItem>>
    fun observeIngredients(): Flow<List<IngredientUiItem>>
    fun searchIngredientSuggestions(prefix: String): Flow<List<IngredientSuggestion>>

    suspend fun findIngredientByName(name: String): IngredientUiItem?
    suspend fun createIngredient(name: String, newMeta: NewIngredientMetaInput): IngredientUiItem?

    suspend fun addStorageItem(name: String, amount: Double?, newMeta: NewIngredientMetaInput? = null): Boolean
    suspend fun removeStorageItem(ingredientId: Long)
    suspend fun addRecipe(name: String, ingredients: List<RecipeIngredientInput>): Boolean
    suspend fun addMissingIngredientsToShopping(recipeId: Long)
    suspend fun addShoppingItem(
        name: String,
        quantity: Double?,
        newMeta: NewIngredientMetaInput? = null
    ): Boolean

    suspend fun toggleShoppingItem(ingredientId: Long)
    suspend fun removeShoppingItem(ingredientId: Long)
    suspend fun moveBoughtToStorage()
    suspend fun updateIngredientMetadata(
        ingredientId: Long,
        newType: IngredientType,
        category: String
    )
}

class GroceryRepositoryImpl(
    private val db: AppDatabase
) : GroceryRepository {
    private val ingredientDao = db.ingredientDao()
    private val storageDao = db.storageDao()
    private val recipeDao = db.recipeDao()
    private val shoppingDao = db.shoppingDao()

    override fun observeStorage(): Flow<List<StorageUiItem>> {
        return storageDao.observeStorage().map { rows ->
            rows.map {
                StorageUiItem(
                    ingredientId = it.ingredientId,
                    name = it.displayName,
                    type = it.type,
                    category = it.category,
                    amount = it.amount
                )
            }
        }
    }

    override fun observeRecipes(): Flow<List<RecipeUiModel>> {
        return combine(
            recipeDao.observeRecipes(),
            recipeDao.observeRecipeIngredientRows(),
            storageDao.observeStorage()
        ) { recipes, ingredientRows, storageRows ->
            val storageByIngredient = storageRows.associateBy { it.ingredientId }
            val recipeIngredientsByRecipe = ingredientRows.groupBy { it.recipeId }

            recipes.map { recipe ->
                val rows = recipeIngredientsByRecipe[recipe.id].orEmpty()
                val missing = mutableListOf<MissingIngredientUiItem>()

                val ingredientModels = rows.map { row ->
                    when (row.type) {
                        IngredientType.PRESENCE_ONLY -> {
                            if (storageByIngredient[row.ingredientId] == null) {
                                missing.add(
                                    MissingIngredientUiItem(
                                        ingredientId = row.ingredientId,
                                        name = row.displayName,
                                        missingAmount = null
                                    )
                                )
                            }
                        }

                        IngredientType.QUANTITY_TRACKED -> {
                            val required = row.requiredAmount ?: 1.0
                            val available = storageByIngredient[row.ingredientId]?.amount ?: 0.0
                            val missingAmount = (required - available).coerceAtLeast(0.0)
                            if (missingAmount > EPSILON) {
                                missing.add(
                                    MissingIngredientUiItem(
                                        ingredientId = row.ingredientId,
                                        name = row.displayName,
                                        missingAmount = missingAmount
                                    )
                                )
                            }
                        }
                    }

                    RecipeIngredientUiItem(
                        ingredientId = row.ingredientId,
                        name = row.displayName,
                        type = row.type,
                        requiredAmount = row.requiredAmount
                    )
                }

                RecipeUiModel(
                    recipeId = recipe.id,
                    name = recipe.name,
                    ingredients = ingredientModels,
                    missing = missing
                )
            }.sortedWith(
                compareBy<RecipeUiModel> { it.missing.isNotEmpty() }
                    .thenBy { it.missing.size }
                    .thenBy { it.name.lowercase() }
            )
        }
    }

    override fun observeShopping(): Flow<List<ShoppingUiItem>> {
        return shoppingDao.observeShopping().map { rows ->
            rows.map {
                ShoppingUiItem(
                    ingredientId = it.ingredientId,
                    name = it.displayName,
                    type = it.type,
                    category = it.category,
                    quantity = it.quantity,
                    isBought = it.isBought
                )
            }
        }
    }

    override fun observeIngredients(): Flow<List<IngredientUiItem>> {
        return ingredientDao.observeAll().map { entities ->
            entities.map {
                IngredientUiItem(
                    ingredientId = it.id,
                    name = it.displayName,
                    type = it.type,
                    category = it.category
                )
            }
        }
    }

    override fun searchIngredientSuggestions(prefix: String): Flow<List<IngredientSuggestion>> {
        val normalizedPrefix = NameNormalizer.nameKey(prefix)
        if (normalizedPrefix.isBlank()) return flowOf(emptyList())
        return ingredientDao.searchByPrefix(normalizedPrefix).map { entities ->
            entities.map {
                IngredientSuggestion(
                    ingredientId = it.id,
                    name = it.displayName,
                    type = it.type,
                    category = it.category
                )
            }
        }
    }

    override suspend fun findIngredientByName(name: String): IngredientUiItem? {
        val normalized = NameNormalizer.normalizeName(name)
        if (normalized.isBlank()) return null
        val entity = ingredientDao.findByNameKey(NameNormalizer.nameKey(normalized)) ?: return null
        return entity.toUiItem()
    }

    override suspend fun createIngredient(
        name: String,
        newMeta: NewIngredientMetaInput
    ): IngredientUiItem? {
        return ensureIngredient(name, newMeta)?.toUiItem()
    }

    override suspend fun addStorageItem(
        name: String,
        amount: Double?,
        newMeta: NewIngredientMetaInput?
    ): Boolean {
        return db.withTransaction {
            val ingredient = ensureIngredient(name, newMeta) ?: return@withTransaction false

            when (ingredient.type) {
                IngredientType.PRESENCE_ONLY -> {
                    storageDao.upsert(StorageEntryEntity(ingredientId = ingredient.id, amount = null))
                }

                IngredientType.QUANTITY_TRACKED -> {
                    val delta = positiveOrDefault(amount, 1.0)
                    val existing = storageDao.findByIngredientId(ingredient.id)?.amount ?: 0.0
                    storageDao.upsert(
                        StorageEntryEntity(
                            ingredientId = ingredient.id,
                            amount = existing + delta
                        )
                    )
                }
            }

            true
        }
    }

    override suspend fun removeStorageItem(ingredientId: Long) {
        storageDao.deleteByIngredientId(ingredientId)
    }

    override suspend fun addRecipe(name: String, ingredients: List<RecipeIngredientInput>): Boolean {
        return db.withTransaction {
            val normalizedName = NameNormalizer.normalizeName(name)
            if (normalizedName.isBlank()) return@withTransaction false

            if (recipeDao.findByNameKey(NameNormalizer.nameKey(normalizedName)) != null) {
                return@withTransaction false
            }

            val resolved = mutableListOf<Pair<IngredientEntity, Double?>>()
            for (input in ingredients) {
                val ingredient = ensureIngredient(input.name, null) ?: return@withTransaction false
                val required = when (ingredient.type) {
                    IngredientType.PRESENCE_ONLY -> null
                    IngredientType.QUANTITY_TRACKED -> positiveOrDefault(input.requiredAmount, 1.0)
                }
                resolved += ingredient to required
            }
            if (resolved.isEmpty()) return@withTransaction false

            val merged = linkedMapOf<Long, Pair<IngredientEntity, Double?>>()
            for ((ingredient, required) in resolved) {
                val previous = merged[ingredient.id]
                merged[ingredient.id] = if (previous == null) {
                    ingredient to required
                } else {
                    val combined = when (ingredient.type) {
                        IngredientType.PRESENCE_ONLY -> null
                        IngredientType.QUANTITY_TRACKED ->
                            (previous.second ?: 0.0) + (required ?: 0.0)
                    }
                    ingredient to combined
                }
            }

            val recipeId = recipeDao.insertRecipe(
                RecipeEntity(
                    name = normalizedName,
                    nameKey = NameNormalizer.nameKey(normalizedName)
                )
            )

            val recipeIngredients = merged.values.map { (ingredient, requiredAmount) ->
                RecipeIngredientEntity(
                    recipeId = recipeId,
                    ingredientId = ingredient.id,
                    requiredAmount = requiredAmount
                )
            }

            recipeDao.insertRecipeIngredients(recipeIngredients)
            true
        }
    }

    override suspend fun addMissingIngredientsToShopping(recipeId: Long) {
        db.withTransaction {
            val recipeRows = recipeDao.getRecipeIngredientRows(recipeId)
            if (recipeRows.isEmpty()) return@withTransaction

            val storageByIngredient = storageDao.getStorageRows().associateBy { it.ingredientId }

            for (row in recipeRows) {
                val missingAmount = when (row.type) {
                    IngredientType.PRESENCE_ONLY -> {
                        if (storageByIngredient[row.ingredientId] == null) 1.0 else 0.0
                    }

                    IngredientType.QUANTITY_TRACKED -> {
                        val required = row.requiredAmount ?: 1.0
                        val available = storageByIngredient[row.ingredientId]?.amount ?: 0.0
                        (required - available).coerceAtLeast(0.0)
                    }
                }

                if (missingAmount <= EPSILON) continue

                val existing = shoppingDao.findByIngredientId(row.ingredientId)
                val mergedQuantity = when (row.type) {
                    IngredientType.PRESENCE_ONLY -> null
                    IngredientType.QUANTITY_TRACKED ->
                        (existing?.quantity ?: 0.0) + missingAmount
                }

                shoppingDao.upsert(
                    ShoppingItemEntity(
                        ingredientId = row.ingredientId,
                        quantity = mergedQuantity,
                        isBought = false
                    )
                )
            }
        }
    }

    override suspend fun addShoppingItem(
        name: String,
        quantity: Double?,
        newMeta: NewIngredientMetaInput?
    ): Boolean {
        return db.withTransaction {
            val ingredient = ensureIngredient(name, newMeta) ?: return@withTransaction false
            val existing = shoppingDao.findByIngredientId(ingredient.id)

            val mergedQuantity = when (ingredient.type) {
                IngredientType.PRESENCE_ONLY -> null
                IngredientType.QUANTITY_TRACKED ->
                    (existing?.quantity ?: 0.0) + positiveOrDefault(quantity, 1.0)
            }

            shoppingDao.upsert(
                ShoppingItemEntity(
                    ingredientId = ingredient.id,
                    quantity = mergedQuantity,
                    isBought = false
                )
            )
            true
        }
    }

    override suspend fun toggleShoppingItem(ingredientId: Long) {
        val existing = shoppingDao.findByIngredientId(ingredientId) ?: return
        shoppingDao.updateBoughtState(ingredientId, !existing.isBought)
    }

    override suspend fun removeShoppingItem(ingredientId: Long) {
        shoppingDao.deleteByIngredientId(ingredientId)
    }

    override suspend fun moveBoughtToStorage() {
        db.withTransaction {
            val boughtRows = shoppingDao.getBoughtRows()
            if (boughtRows.isEmpty()) return@withTransaction

            for (row in boughtRows) {
                when (row.type) {
                    IngredientType.PRESENCE_ONLY -> {
                        storageDao.upsert(StorageEntryEntity(ingredientId = row.ingredientId, amount = null))
                    }

                    IngredientType.QUANTITY_TRACKED -> {
                        val delta = positiveOrDefault(row.quantity, 1.0)
                        val existing = storageDao.findByIngredientId(row.ingredientId)?.amount ?: 0.0
                        storageDao.upsert(
                            StorageEntryEntity(
                                ingredientId = row.ingredientId,
                                amount = existing + delta
                            )
                        )
                    }
                }
            }

            shoppingDao.deleteBought()
        }
    }

    override suspend fun updateIngredientMetadata(
        ingredientId: Long,
        newType: IngredientType,
        category: String
    ) {
        val normalizedCategory = NameNormalizer.normalizeName(category)
        if (normalizedCategory.isBlank()) return

        db.withTransaction {
            val existing = ingredientDao.findById(ingredientId) ?: return@withTransaction
            val oldType = existing.type

            ingredientDao.update(
                existing.copy(
                    type = newType,
                    category = normalizedCategory
                )
            )

            if (oldType == newType) return@withTransaction

            when {
                oldType == IngredientType.QUANTITY_TRACKED && newType == IngredientType.PRESENCE_ONLY -> {
                    storageDao.setAmountNull(ingredientId)
                    recipeDao.setRequiredAmountNull(ingredientId)
                    shoppingDao.setQuantityNull(ingredientId)
                }

                oldType == IngredientType.PRESENCE_ONLY && newType == IngredientType.QUANTITY_TRACKED -> {
                    storageDao.setNullAmountsToDefault(ingredientId)
                    recipeDao.setNullRequiredAmountsToDefault(ingredientId)
                    shoppingDao.setNullQuantitiesToDefault(ingredientId)
                }
            }
        }
    }

    private suspend fun ensureIngredient(
        name: String,
        newMeta: NewIngredientMetaInput?
    ): IngredientEntity? {
        val normalizedName = NameNormalizer.normalizeName(name)
        if (normalizedName.isBlank()) return null
        val key = NameNormalizer.nameKey(normalizedName)

        ingredientDao.findByNameKey(key)?.let { return it }
        if (newMeta == null) return null

        val category = NameNormalizer.normalizeName(newMeta.category)
        if (category.isBlank()) return null

        val newIngredient = IngredientEntity(
            displayName = normalizedName,
            nameKey = key,
            type = newMeta.type,
            category = category
        )

        return try {
            val id = ingredientDao.insert(newIngredient)
            newIngredient.copy(id = id)
        } catch (_: Throwable) {
            ingredientDao.findByNameKey(key)
        }
    }

    //TODO: Value of parameter 'defaultValue' is always '1.0'
    private fun positiveOrDefault(value: Double?, defaultValue: Double): Double {
        val candidate = value ?: defaultValue
        return if (candidate > 0.0) candidate else defaultValue
    }

    private fun IngredientEntity.toUiItem(): IngredientUiItem {
        return IngredientUiItem(
            ingredientId = id,
            name = displayName,
            type = type,
            category = category
        )
    }

    companion object {
        private const val EPSILON = 1e-9
    }
}
