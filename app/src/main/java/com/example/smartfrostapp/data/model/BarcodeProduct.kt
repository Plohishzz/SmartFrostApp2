package com.example.smartfrostapp.data.model

data class BarcodeProduct(
    val barcode: String,
    val productName: String,
    val quantity: String,
    val unit: String,
    val category: String,
    val icon: String,
    val expiryDays: Int
)
