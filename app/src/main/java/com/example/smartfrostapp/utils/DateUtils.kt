package com.example.smartfrostapp.utils

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

fun calculateDaysRemaining(expiryDateStr: String): Int {
    if (expiryDateStr.isEmpty()) return 0
    return try {
        val format = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
        val expiryDate = format.parse(expiryDateStr) ?: return 0
        
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = calendar.timeInMillis
        
        calendar.time = expiryDate
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val expiry = calendar.timeInMillis
        
        val diff = expiry - today
        TimeUnit.MILLISECONDS.toDays(diff).toInt()
    } catch (e: Exception) {
        0
    }
}
