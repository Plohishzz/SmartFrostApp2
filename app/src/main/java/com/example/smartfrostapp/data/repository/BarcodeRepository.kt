package com.example.smartfrostapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.smartfrostapp.data.model.BarcodeProduct

object BarcodeRepository {
    private const val PREFS_NAME = "barcode_product_prefs"
    private const val KEY_COUNT = "barcode_count"
    private const val KEY_PREFIX = "barcode_"
    private const val SEP = "|||"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveBarcodeProduct(barcodeProduct: BarcodeProduct) {
        val existing = getBarcodeProduct(barcodeProduct.barcode)
        if (existing != null) {
            updateBarcodeProduct(barcodeProduct.barcode, barcodeProduct)
        } else {
            val editor = prefs.edit()
            val count = prefs.getInt(KEY_COUNT, 0)
            val newCount = count + 1

            val value = listOf(
                barcodeProduct.barcode,
                barcodeProduct.productName,
                barcodeProduct.quantity,
                barcodeProduct.unit,
                barcodeProduct.category,
                barcodeProduct.icon,
                barcodeProduct.expiryDays.toString()
            ).joinToString(SEP)
            editor.putString("$KEY_PREFIX$newCount", value)
            editor.putInt(KEY_COUNT, newCount)
            editor.apply()
        }
    }

    fun getBarcodeProduct(barcode: String): BarcodeProduct? {
        val count = prefs.getInt(KEY_COUNT, 0)
        for (i in count downTo 1) {
            val value = prefs.getString("$KEY_PREFIX$i", null) ?: continue
            val parts = value.split(SEP)
            if (parts.size >= 7 && parts[0] == barcode) {
                return BarcodeProduct(
                    barcode = parts[0],
                    productName = parts[1],
                    quantity = parts[2],
                    unit = parts[3],
                    category = parts[4],
                    icon = parts[5],
                    expiryDays = parts[6].toIntOrNull() ?: 7
                )
            }
        }
        return null
    }

    fun getAllBarcodes(): List<BarcodeProduct> {
        val count = prefs.getInt(KEY_COUNT, 0)
        val barcodes = mutableListOf<BarcodeProduct>()
        for (i in 1..count) {
            val value = prefs.getString("$KEY_PREFIX$i", null) ?: continue
            val parts = value.split(SEP)
            if (parts.size >= 7) {
                barcodes.add(BarcodeProduct(
                    barcode = parts[0],
                    productName = parts[1],
                    quantity = parts[2],
                    unit = parts[3],
                    category = parts[4],
                    icon = parts[5],
                    expiryDays = parts[6].toIntOrNull() ?: 7
                ))
            }
        }
        return barcodes
    }

    fun updateBarcodeProduct(barcode: String, updatedProduct: BarcodeProduct) {
        val count = prefs.getInt(KEY_COUNT, 0)
        val editor = prefs.edit()
        for (i in 1..count) {
            val value = prefs.getString("$KEY_PREFIX$i", null) ?: continue
            val parts = value.split(SEP)
            if (parts.size >= 7 && parts[0] == barcode) {
                val newValue = listOf(
                    updatedProduct.barcode,
                    updatedProduct.productName,
                    updatedProduct.quantity,
                    updatedProduct.unit,
                    updatedProduct.category,
                    updatedProduct.icon,
                    updatedProduct.expiryDays.toString()
                ).joinToString(SEP)
                editor.putString("$KEY_PREFIX$i", newValue)
                editor.apply()
                return
            }
        }
    }

    fun deleteBarcodeProduct(barcode: String) {
        val count = prefs.getInt(KEY_COUNT, 0)
        val editor = prefs.edit()
        for (i in 1..count) {
            val value = prefs.getString("$KEY_PREFIX$i", null) ?: continue
            val parts = value.split(SEP)
            if (parts.size >= 7 && parts[0] == barcode) {
                editor.remove("$KEY_PREFIX$i")
                editor.apply()
                return
            }
        }
    }
}
