package com.example.smartfrostapp.data.model

data class Product(
    val id: String,
    val name: String,
    val quantity: String,
    val category: String,
    val expiryDays: Int,
    val icon: String,
    val isLocked: Boolean = false,
    val manufactureDate: String = "",
    val expiryDate: String = "",
    val addedDate: String = "",
    val backendId: Int = 0
)

data class ProductTemplate(
    val name: String,
    val icon: String,
    val defaultShelfLifeDays: Int,
    val category: String,
    val defaultQuantity: String = "1",
    val defaultUnit: String = "шт"
)
