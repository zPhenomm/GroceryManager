package com.example.grocerymanager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.grocerymanager.data.local.dao.IngredientDao
import com.example.grocerymanager.data.local.dao.RecipeDao
import com.example.grocerymanager.data.local.dao.ShoppingDao
import com.example.grocerymanager.data.local.dao.StorageDao

@Database(
    entities = [
        IngredientEntity::class,
        StorageEntryEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        ShoppingItemEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ingredientDao(): IngredientDao
    abstract fun storageDao(): StorageDao
    abstract fun recipeDao(): RecipeDao
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "grocery_manager.db"
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        seedInitialData(db)
                    }
                })
                .build()
        }

        private fun seedInitialData(db: SupportSQLiteDatabase) {
            db.beginTransaction()
            try {
                db.execSQL(
                    """
                    INSERT INTO ingredients (id, displayName, nameKey, type, category) VALUES
                    (1, 'Eggs', 'eggs', 'QUANTITY_TRACKED', 'Dairy'),
                    (2, 'Milk', 'milk', 'QUANTITY_TRACKED', 'Dairy'),
                    (3, 'Rice', 'rice', 'QUANTITY_TRACKED', 'Grains'),
                    (4, 'Tomatoes', 'tomatoes', 'QUANTITY_TRACKED', 'Produce'),
                    (5, 'Salt', 'salt', 'PRESENCE_ONLY', 'Pantry'),
                    (6, 'Olive Oil', 'olive oil', 'PRESENCE_ONLY', 'Pantry'),
                    (7, 'Flour', 'flour', 'QUANTITY_TRACKED', 'Baking'),
                    (8, 'Sugar', 'sugar', 'QUANTITY_TRACKED', 'Baking')
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO storage_entries (ingredientId, amount) VALUES
                    (1, 3.0),
                    (2, 1.0),
                    (3, 1.0),
                    (4, 4.0)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO recipes (id, name, nameKey) VALUES
                    (1, 'Omelet', 'omelet'),
                    (2, 'Tomato Rice', 'tomato rice'),
                    (3, 'Pancakes', 'pancakes')
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO recipe_ingredients (id, recipeId, ingredientId, requiredAmount) VALUES
                    (1, 1, 1, 4.0),
                    (2, 1, 2, 1.0),
                    (3, 1, 5, NULL),
                    (4, 2, 3, 1.0),
                    (5, 2, 4, 2.0),
                    (6, 2, 6, NULL),
                    (7, 3, 1, 2.0),
                    (8, 3, 2, 1.0),
                    (9, 3, 7, 1.0),
                    (10, 3, 8, 0.2)
                    """.trimIndent()
                )

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }
}
