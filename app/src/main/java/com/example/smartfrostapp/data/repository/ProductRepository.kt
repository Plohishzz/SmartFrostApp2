package com.example.smartfrostapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.smartfrostapp.data.model.Product
import com.example.smartfrostapp.utils.calculateDaysRemaining

object ProductRepository {
    private const val PREFS_PREFIX = "product_data_prefs_user_"
    private const val KEY_COUNT = "products_count"
    private const val KEY_PREFIX = "product_"
    private const val SEP = "::"

    private var prefs: SharedPreferences? = null

    fun init(context: Context, userId: Int) {
        prefs = context.getSharedPreferences("$PREFS_PREFIX$userId", Context.MODE_PRIVATE)
    }

    fun saveProducts(products: List<Product>) {
        val editor = prefs?.edit() ?: return
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
        val p = prefs ?: return emptyList()
        val count = p.getInt(KEY_COUNT, 0)
        return (0 until count).mapNotNull { index ->
            val value = p.getString("$KEY_PREFIX$index", null) ?: return@mapNotNull null
            val parts = value.split(SEP)
            if (parts.size >= 10) {
                val expiryDate = parts[8]
                val recalculatedExpiryDays = if (expiryDate.isNotEmpty()) {
                    calculateDaysRemaining(expiryDate)
                } else {
                    parts[4].toIntOrNull() ?: 0
                }
                Product(
                    id = parts[0],
                    name = parts[1],
                    quantity = parts[2],
                    category = parts[3],
                    expiryDays = recalculatedExpiryDays,
                    icon = parts[5],
                    isLocked = parts[6].toBoolean(),
                    manufactureDate = parts[7],
                    expiryDate = expiryDate,
                    addedDate = parts[9],
                    backendId = parts.getOrNull(10)?.toIntOrNull() ?: 0
                )
            } else null
        }
    }
}
