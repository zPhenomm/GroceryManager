package com.example.grocerymanager.data

object NameNormalizer {
    fun normalizeName(input: String): String {
        return input.trim().replace(Regex("\\s+"), " ")
    }

    fun nameKey(input: String): String {
        return normalizeName(input).lowercase()
    }
}
