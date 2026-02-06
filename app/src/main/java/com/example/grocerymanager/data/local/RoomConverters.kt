package com.example.grocerymanager.data.local

import androidx.room.TypeConverter

//TODO: these functions are never used?
class RoomConverters {
    @TypeConverter
    fun ingredientTypeToString(type: IngredientType): String = type.name

    @TypeConverter
    fun stringToIngredientType(value: String): IngredientType = IngredientType.valueOf(value)
}
