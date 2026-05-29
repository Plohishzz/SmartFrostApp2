package com.example.smartfrostapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object PendingSyncRepository {
    private const val PREFS_NAME = "pending_sync_prefs"
    private const val KEY_QUEUE = "sync_queue"
    private const val TAG = "PendingSync"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun addAddOperation(productJson: String) {
        val queue = loadQueue()
        queue.put(JSONObject().apply {
            put("type", "add")
            put("product", productJson)
        })
        saveQueue(queue)
    }

    fun addUpdateOperation(backendId: Int, changesJson: String) {
        val queue = loadQueue()
        queue.put(JSONObject().apply {
            put("type", "update")
            put("backendId", backendId)
            put("changes", changesJson)
        })
        saveQueue(queue)
    }

    fun addDeleteOperation(backendId: Int) {
        val queue = loadQueue()
        queue.put(JSONObject().apply {
            put("type", "delete")
            put("backendId", backendId)
        })
        saveQueue(queue)
    }

    fun loadQueue(): JSONArray {
        val json = prefs.getString(KEY_QUEUE, "[]") ?: "[]"
        return try {
            JSONArray(json)
        } catch (e: Exception) {
            JSONArray()
        }
    }

    fun saveQueue(queue: JSONArray) {
        prefs.edit().putString(KEY_QUEUE, queue.toString()).apply()
    }

    fun clearQueue() {
        prefs.edit().remove(KEY_QUEUE).apply()
    }

    fun hasPendingOperations(): Boolean {
        return loadQueue().length() > 0
    }

    fun getPendingCount(): Int {
        return loadQueue().length()
    }
}
