package com.example.smartfrostapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.smartfrostapp.data.model.ActionHistoryEntry
import com.example.smartfrostapp.data.model.ActionType

object ActionHistoryRepository {
    private const val PREFS_PREFIX = "action_history_prefs_user_"
    private const val KEY_COUNT = "history_count"
    private const val KEY_PREFIX = "action_"
    private const val SEP = "|||"
    private const val FIELD_SEP = "::"
    private const val MAX_ENTRIES = 100

    private var prefs: SharedPreferences? = null

    fun init(context: Context, userId: Int) {
        prefs = context.getSharedPreferences("$PREFS_PREFIX$userId", Context.MODE_PRIVATE)
    }

    fun addEntry(entry: ActionHistoryEntry) {
        val editor = prefs?.edit() ?: return
        val count = prefs?.getInt(KEY_COUNT, 0) ?: 0

        if (count >= MAX_ENTRIES) {
            val oldestKey = "$KEY_PREFIX${(count % MAX_ENTRIES) + 1}"
            editor.remove(oldestKey)
        }

        val newIndex = (count % MAX_ENTRIES) + 1
        val value = listOf(
            entry.id,
            entry.type.name,
            entry.productName,
            entry.productIcon,
            entry.productJson,
            entry.timestamp.toString()
        ).joinToString(SEP)
        editor.putString("$KEY_PREFIX$newIndex", value)
        editor.putInt(KEY_COUNT, count + 1)
        editor.commit()
    }

    fun loadHistory(): List<ActionHistoryEntry> {
        val p = prefs ?: return emptyList()
        val count = p.getInt(KEY_COUNT, 0)
        val entries = mutableListOf<ActionHistoryEntry>()
        val total = count.coerceAtMost(MAX_ENTRIES)
        val start = if (count > MAX_ENTRIES) (count % MAX_ENTRIES) + 1 else 1
        for (i in 0 until total) {
            val idx = ((start - 1 + i) % MAX_ENTRIES) + 1
            val value = p.getString("$KEY_PREFIX$idx", null) ?: continue
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
        val editor = prefs?.edit() ?: return
        val count = prefs?.getInt(KEY_COUNT, 0) ?: 0
        val entries = mutableListOf<Pair<String, String>>()
        
        for (i in 1..count) {
            val value = prefs?.getString("$KEY_PREFIX$i", null)
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
        val editor = prefs?.edit() ?: return
        val count = prefs?.getInt(KEY_COUNT, 0) ?: 0
        for (i in 1..count) {
            editor.remove("$KEY_PREFIX$i")
        }
        editor.putInt(KEY_COUNT, 0)
        editor.apply()
    }
}
