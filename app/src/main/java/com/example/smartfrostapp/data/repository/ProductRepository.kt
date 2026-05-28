package com.example.smartfrostapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.smartfrostapp.data.model.Product

object ProductRepository {
    private const val PREFS_NAME = "product_data_prefs"
    private const val KEY_COUNT = "products_count"
    private const val KEY_PREFIX = "product_"
    private const val SEP = "::"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveProducts(products: List<Product>) {
        val editor = prefs.edit()
        editor.putInt(KEY_COUNT, products.size)
        products.forEachIndexed { index, product ->
            val value = listOf(
                product.id,
                product.name,
                product.quantity,
                product.category,
                product.expiryDays.toString(),
                product.icon,
                product.isLocked.toString(),
                product.manufactureDate,
                product.expiryDate,
                product.addedDate,
                product.backendId.toString()
            ).joinToString(SEP)
            editor.putString("$KEY_PREFIX$index", value)
        }
        editor.apply()
    }

    fun loadProducts(): List<Product> {
        val count = prefs.getInt(KEY_COUNT, 0)
        return (0 until count).mapNotNull { index ->
            val value = prefs.getString("$KEY_PREFIX$index", null) ?: return@mapNotNull null
            val parts = value.split(SEP)
            if (parts.size >= 10) {
                Product(
                    id = parts[0],
                    name = parts[1],
                    quantity = parts[2],
                    category = parts[3],
                    expiryDays = parts[4].toIntOrNull() ?: 0,
                    icon = parts[5],
                    isLocked = parts[6].toBoolean(),
                    manufactureDate = parts[7],
                    expiryDate = parts[8],
                    addedDate = parts[9],
                    backendId = parts.getOrNull(10)?.toIntOrNull() ?: 0
                )
            } else null
        }
    }
}
