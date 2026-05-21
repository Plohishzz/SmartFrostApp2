package com.example.smartfrostapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.smartfrostapp.data.model.ActionHistoryEntry
import com.example.smartfrostapp.data.model.ActionType

object ActionHistoryRepository {
    private const val PREFS_NAME = "action_history_prefs"
    private const val KEY_COUNT = "history_count"
    private const val KEY_PREFIX = "action_"
    private const val SEP = "|||"
    private const val FIELD_SEP = "::"
    private const val MAX_ENTRIES = 100

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun addEntry(entry: ActionHistoryEntry) {
        val editor = prefs.edit()
        val count = prefs.getInt(KEY_COUNT, 0)
        val newCount = (count + 1).coerceAtMost(MAX_ENTRIES)

        if (newCount == MAX_ENTRIES && count >= MAX_ENTRIES) {
            editor.remove("$KEY_PREFIX${count - MAX_ENTRIES}")
        }

        val value = listOf(
            entry.id,
            entry.type.name,
            entry.productName,
            entry.productIcon,
            entry.productJson,
            entry.timestamp.toString()
        ).joinToString(SEP)
        editor.putString("$KEY_PREFIX$newCount", value)
        editor.putInt(KEY_COUNT, newCount)
        editor.commit()
    }

    fun loadHistory(): List<ActionHistoryEntry> {
        val count = prefs.getInt(KEY_COUNT, 0)
        val entries = mutableListOf<ActionHistoryEntry>()
        val start = if (count >= MAX_ENTRIES) count - MAX_ENTRIES + 1 else 1
        for (i in start..count) {
            val value = prefs.getString("$KEY_PREFIX$i", null) ?: continue
            val parts = value.split(SEP)
            if (parts.size >= 6) {
                entries.add(ActionHistoryEntry(
                    id = parts[0],
                    type = ActionType.valueOf(parts[1]),
                    productName = parts[2],
                    productIcon = parts[3],
                    productJson = parts[4],
                    timestamp = parts[5].toLongOrNull() ?: 0L
                ))
            } else {
                // Backward compatibility with old "::" separator
                val legacyParts = value.split("::")
                if (legacyParts.size >= 15) {
                    val productJson = legacyParts.subList(4, 14).joinToString("::")
                    entries.add(ActionHistoryEntry(
                        id = legacyParts[0],
                        type = ActionType.valueOf(legacyParts[1]),
                        productName = legacyParts[2],
                        productIcon = legacyParts[3],
                        productJson = productJson,
                        timestamp = legacyParts[14].toLongOrNull() ?: 0L
                    ))
                }
            }
        }
        return entries.reversed()
    }

    fun removeEntry(entryId: String) {
        val editor = prefs.edit()
        val count = prefs.getInt(KEY_COUNT, 0)
        val entries = mutableListOf<Pair<String, String>>()
        
        for (i in 1..count) {
            val value = prefs.getString("$KEY_PREFIX$i", null)
            if (value != null) {
                entries.add("$KEY_PREFIX$i" to value)
            }
        }
        
        val filtered = entries.filter { (_, value) ->
            val parts = value.split(SEP)
            val id = if (parts.size >= 1) parts[0] else ""
            id != entryId
        }
        
        for ((key, _) in entries) {
            editor.remove(key)
        }
        
        for (i in filtered.indices) {
            editor.putString("$KEY_PREFIX${i + 1}", filtered[i].second)
        }
        editor.putInt(KEY_COUNT, filtered.size)
        editor.apply()
    }

    fun clearHistory() {
        val editor = prefs.edit()
        val count = prefs.getInt(KEY_COUNT, 0)
        for (i in 1..count) {
            editor.remove("$KEY_PREFIX$i")
        }
        editor.putInt(KEY_COUNT, 0)
        editor.apply()
    }
}
