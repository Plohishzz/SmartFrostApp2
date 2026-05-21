package com.example.smartfrostapp.data.model

data class ActionHistoryEntry(
    val id: String,
    val type: ActionType,
    val productName: String,
    val productIcon: String,
    val productJson: String,
    val timestamp: Long
)

enum class ActionType {
    ADDED, EDITED, DELETED
}
