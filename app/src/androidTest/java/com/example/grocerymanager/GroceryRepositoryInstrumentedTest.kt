package com.example.grocerymanager

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.grocerymanager.data.NameNormalizer
import com.example.grocerymanager.data.local.AppDatabase
import com.example.grocerymanager.data.local.IngredientEntity
import com.example.grocerymanager.data.local.IngredientType
import com.example.grocerymanager.data.local.RecipeEntity
import com.example.grocerymanager.data.local.RecipeIngredientEntity
import com.example.grocerymanager.data.local.ShoppingItemEntity
import com.example.grocerymanager.data.local.StorageEntryEntity
import com.example.grocerymanager.data.repo.GroceryRepository
import com.example.grocerymanager.data.repo.GroceryRepositoryImpl
import com.example.grocerymanager.data.repo.NewIngredientMetaInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroceryRepositoryInstrumentedTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: GroceryRepository

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = GroceryRepositoryImpl(db)
        seedDefaults()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun ingredient_uniqueness_is_case_insensitive() = runBlocking {
        val existing = repository.createIngredient(
            "eggs",
            NewIngredientMetaInput(type = IngredientType.QUANTITY_TRACKED, category = "Dairy")
        )
        assertNotNull(existing)
        assertEquals("Eggs", existing?.name)

        val all = repository.observeIngredients().first()
        assertEquals(8, all.size)
    }

    @Test
    fun autocomplete_returns_prefix_matches_sorted() = runBlocking {
        repository.createIngredient(
            "Tea",
            NewIngredientMetaInput(type = IngredientType.PRESENCE_ONLY, category = "Pantry")
        )
        repository.createIngredient(
            "Tofu",
            NewIngredientMetaInput(type = IngredientType.QUANTITY_TRACKED, category = "Protein")
        )

        val suggestions = repository.searchIngredientSuggestions("t").first()
        val names = suggestions.map { it.name }
        assertTrue(names.contains("Tea"))
        assertTrue(names.contains("Tofu"))
        assertTrue(names.contains("Tomatoes"))
        assertEquals(names.sortedBy { it.lowercase() }, names)
    }

    @Test
    fun missing_logic_uses_presence_and_quantity_rules() = runBlocking {
        val omelet = repository.observeRecipes().first().first { it.name == "Omelet" }
        val missingNames = omelet.missing.map { it.name }
        assertTrue(missingNames.contains("Salt"))

        val eggsMissing = omelet.missing.firstOrNull { it.name == "Eggs" }
        assertNotNull(eggsMissing)
        assertEquals(1.0, eggsMissing?.missingAmount ?: 0.0, 0.0001)
    }

    @Test
    fun add_missing_ingredients_to_shopping_merges_and_sums() = runBlocking {
        val omelet = repository.observeRecipes().first().first { it.name == "Omelet" }
        repository.addMissingIngredientsToShopping(omelet.recipeId)
        repository.addMissingIngredientsToShopping(omelet.recipeId)

        val shopping = repository.observeShopping().first()
        val eggs = shopping.first { it.name == "Eggs" }
        val salt = shopping.first { it.name == "Salt" }

        assertEquals(2.0, eggs.quantity ?: 0.0, 0.0001)
        assertNull(salt.quantity)
    }

    @Test
    fun move_bought_to_storage_increases_quantity_and_clears_bought_items() = runBlocking {
        repository.addShoppingItem("Eggs", 2.0)
        val eggsShopping = repository.observeShopping().first().first { it.name == "Eggs" }
        repository.toggleShoppingItem(eggsShopping.ingredientId)
        repository.moveBoughtToStorage()

        val storageEggs = repository.observeStorage().first().first { it.name == "Eggs" }
        assertEquals(5.0, storageEggs.amount ?: 0.0, 0.0001)
        assertFalse(repository.observeShopping().first().any { it.name == "Eggs" })
    }

    @Test
    fun type_conversion_updates_storage_recipe_and_shopping_amounts() = runBlocking {
        repository.addStorageItem("Salt", null)
        repository.addShoppingItem("Salt", null)

        val saltId = repository.findIngredientByName("Salt")!!.ingredientId
        repository.updateIngredientMetadata(
            ingredientId = saltId,
            newType = IngredientType.QUANTITY_TRACKED,
            category = "Pantry"
        )

        val storageSalt = repository.observeStorage().first().first { it.name == "Salt" }
        val shoppingSalt = repository.observeShopping().first().first { it.name == "Salt" }
        val omelet = repository.observeRecipes().first().first { it.name == "Omelet" }
        val recipeSalt = omelet.ingredients.first { it.name == "Salt" }

        assertEquals(1.0, storageSalt.amount ?: 0.0, 0.0001)
        assertEquals(1.0, shoppingSalt.quantity ?: 0.0, 0.0001)
        assertEquals(1.0, recipeSalt.requiredAmount ?: 0.0, 0.0001)
    }

    private suspend fun seedDefaults() {
        val ingredientDao = db.ingredientDao()
        val storageDao = db.storageDao()
        val recipeDao = db.recipeDao()

        val ingredients = listOf(
            IngredientEntity(displayName = "Eggs", nameKey = "eggs", type = IngredientType.QUANTITY_TRACKED, category = "Dairy"),
            IngredientEntity(displayName = "Milk", nameKey = "milk", type = IngredientType.QUANTITY_TRACKED, category = "Dairy"),
            IngredientEntity(displayName = "Rice", nameKey = "rice", type = IngredientType.QUANTITY_TRACKED, category = "Grains"),
            IngredientEntity(displayName = "Tomatoes", nameKey = "tomatoes", type = IngredientType.QUANTITY_TRACKED, category = "Produce"),
            IngredientEntity(displayName = "Salt", nameKey = "salt", type = IngredientType.PRESENCE_ONLY, category = "Pantry"),
            IngredientEntity(displayName = "Olive Oil", nameKey = "olive oil", type = IngredientType.PRESENCE_ONLY, category = "Pantry"),
            IngredientEntity(displayName = "Flour", nameKey = "flour", type = IngredientType.QUANTITY_TRACKED, category = "Baking"),
            IngredientEntity(displayName = "Sugar", nameKey = "sugar", type = IngredientType.QUANTITY_TRACKED, category = "Baking")
        )

        ingredients.forEach { ingredientDao.insert(it) }

        val eggsId = ingredientDao.findByNameKey(NameNormalizer.nameKey("Eggs"))!!.id
        val milkId = ingredientDao.findByNameKey(NameNormalizer.nameKey("Milk"))!!.id
        val riceId = ingredientDao.findByNameKey(NameNormalizer.nameKey("Rice"))!!.id
        val tomatoesId = ingredientDao.findByNameKey(NameNormalizer.nameKey("Tomatoes"))!!.id
        val saltId = ingredientDao.findByNameKey(NameNormalizer.nameKey("Salt"))!!.id
        val oliveOilId = ingredientDao.findByNameKey(NameNormalizer.nameKey("Olive Oil"))!!.id
        val flourId = ingredientDao.findByNameKey(NameNormalizer.nameKey("Flour"))!!.id
        val sugarId = ingredientDao.findByNameKey(NameNormalizer.nameKey("Sugar"))!!.id

        storageDao.upsert(StorageEntryEntity(ingredientId = eggsId, amount = 3.0))
        storageDao.upsert(StorageEntryEntity(ingredientId = milkId, amount = 1.0))
        storageDao.upsert(StorageEntryEntity(ingredientId = riceId, amount = 1.0))
        storageDao.upsert(StorageEntryEntity(ingredientId = tomatoesId, amount = 4.0))

        val omeletId = recipeDao.insertRecipe(RecipeEntity(name = "Omelet", nameKey = "omelet"))
        val tomatoRiceId = recipeDao.insertRecipe(RecipeEntity(name = "Tomato Rice", nameKey = "tomato rice"))
        val pancakesId = recipeDao.insertRecipe(RecipeEntity(name = "Pancakes", nameKey = "pancakes"))

        recipeDao.insertRecipeIngredients(
            listOf(
                RecipeIngredientEntity(recipeId = omeletId, ingredientId = eggsId, requiredAmount = 4.0),
                RecipeIngredientEntity(recipeId = omeletId, ingredientId = milkId, requiredAmount = 1.0),
                RecipeIngredientEntity(recipeId = omeletId, ingredientId = saltId, requiredAmount = null),
                RecipeIngredientEntity(recipeId = tomatoRiceId, ingredientId = riceId, requiredAmount = 1.0),
                RecipeIngredientEntity(recipeId = tomatoRiceId, ingredientId = tomatoesId, requiredAmount = 2.0),
                RecipeIngredientEntity(recipeId = tomatoRiceId, ingredientId = oliveOilId, requiredAmount = null),
                RecipeIngredientEntity(recipeId = pancakesId, ingredientId = eggsId, requiredAmount = 2.0),
                RecipeIngredientEntity(recipeId = pancakesId, ingredientId = milkId, requiredAmount = 1.0),
                RecipeIngredientEntity(recipeId = pancakesId, ingredientId = flourId, requiredAmount = 1.0),
                RecipeIngredientEntity(recipeId = pancakesId, ingredientId = sugarId, requiredAmount = 0.2)
            )
        )
    }
}
