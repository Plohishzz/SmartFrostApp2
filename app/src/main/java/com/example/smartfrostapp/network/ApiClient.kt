package com.example.smartfrostapp.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

import com.example.smartfrostapp.utils.NetworkConstants

object ApiClient {

    private const val TAG = "ApiClient"

    // ==================== AUTH ====================

    suspend fun sendVerificationCode(email: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${NetworkConstants.BASE_URL}verification-code?email=${URLEncoder.encode(email, "UTF-8")}")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()
            connection.disconnect()

            val json = JSONObject(response)
            val message = json.optString("message", "")
            if (message == "sent") {
                Result.success("Код отправлен на $email")
            } else {
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendVerificationCode error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun verifyCode(email: String, code: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${NetworkConstants.BASE_URL}verification-code")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val body = JSONObject().apply {
                put("email", email)
                put("code", code)
            }

            OutputStreamWriter(connection.outputStream).use {
                it.write(body.toString())
                it.flush()
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()
            connection.disconnect()

            val json = JSONObject(response)
            val message = json.optString("message", "")
            if (message == "success") {
                val token = json.optString("token", "")
                Result.success(token)
            } else {
                Result.failure(Exception("Неверный код"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "verifyCode error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun register(name: String, email: String, password: String, verificationToken: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${NetworkConstants.BASE_URL}users")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val body = JSONObject().apply {
                put("name", name)
                put("email", email)
                put("password", password)
                put("token", verificationToken)
            }

            OutputStreamWriter(connection.outputStream).use {
                it.write(body.toString())
                it.flush()
            }

            val responseCode = connection.responseCode
            val inputStream = if (responseCode == 200) connection.inputStream else connection.errorStream
            val reader = BufferedReader(InputStreamReader(inputStream))
            val response = reader.readText()
            reader.close()
            connection.disconnect()

            val json = JSONObject(response)
            val message = json.optString("message", "")

            when (message) {
                "success" -> {
                    val userId = json.optInt("user_id", 0)
                    val token = json.optString("token", "")
                    Result.success(AuthResponse(userId, token, "success"))
                }
                "email exists" -> Result.failure(Exception("Этот Email уже зарегистрирован"))
                "bad token" -> Result.failure(Exception("Токен верификации недействителен"))
                else -> Result.failure(Exception("Ошибка регистрации: $message"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "register error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${NetworkConstants.BASE_URL}login")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
            }

            OutputStreamWriter(connection.outputStream).use {
                it.write(body.toString())
                it.flush()
            }

            val responseCode = connection.responseCode
            val inputStream = if (responseCode == 200) connection.inputStream else connection.errorStream
            val reader = BufferedReader(InputStreamReader(inputStream))
            val response = reader.readText()
            reader.close()
            connection.disconnect()

            val json = JSONObject(response)
            val message = json.optString("message", "")

            if (message == "success") {
                val userId = json.optInt("user_id", 0)
                val token = json.optString("token", "")
                Result.success(AuthResponse(userId, token, "success"))
            } else {
                Result.failure(Exception("Неправильная почта или пароль"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "login error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getUser(token: String): Result<UserInfo> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${NetworkConstants.BASE_URL}users?token=${URLEncoder.encode(token, "UTF-8")}")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()
            connection.disconnect()

            val json = JSONObject(response)
            val message = json.optString("message", "")

            if (message == "success") {
                val userObj = json.getJSONObject("user")
                Result.success(
                    UserInfo(
                        name = userObj.optString("name", ""),
                        email = userObj.optString("email", "")
                    )
                )
            } else {
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getUser error: ${e.message}")
            Result.failure(e)
        }
    }

    // ==================== PRODUCTS ====================

    suspend fun getItems(token: String): Result<List<BackendProduct>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${NetworkConstants.BASE_URL}items?token=${URLEncoder.encode(token, "UTF-8")}")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()
            connection.disconnect()

            val json = JSONObject(response)
            val message = json.optString("message", "")

            if (message == "success") {
                val itemsArray = json.getJSONArray("items")
                val products = mutableListOf<BackendProduct>()
                for (i in 0 until itemsArray.length()) {
                    val item = itemsArray.getJSONObject(i)
                    products.add(
                        BackendProduct(
                            id = item.optInt("id", 0),
                            userId = item.optInt("user_id", 0),
                            name = item.optString("name", ""),
                            category = item.optString("category", null),
                            expiration = item.optString("expiration", null),
                            quantity = item.optDouble("quantity", 1.0),
                            unit = item.optString("unit", "шт"),
                            deleted = item.optBoolean("deleted", false)
                        )
                    )
                }
                Result.success(products)
            } else {
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getItems error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun addItems(token: String, items: List<BackendProductItem>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${NetworkConstants.BASE_URL}items")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val itemsArray = JSONArray()
            for (item in items) {
                itemsArray.put(item.toJson())
            }

            val body = JSONObject().apply {
                put("token", token)
                put("items", itemsArray)
            }

            OutputStreamWriter(connection.outputStream).use {
                it.write(body.toString())
                it.flush()
            }

            val responseCode = connection.responseCode
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()
            connection.disconnect()

            val json = JSONObject(response)
            val message = json.optString("message", "")
            if (message == "success") {
                Result.success("success")
            } else {
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "addItems error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updateItems(token: String, updates: List<Pair<Int, Map<String, Any>>>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${NetworkConstants.BASE_URL}items")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val itemsUpdArray = JSONArray()
            for ((id, changes) in updates) {
                val pairArray = JSONArray().apply {
                    put(id)
                    val changesObj = JSONObject()
                    for ((key, value) in changes) {
                        when (value) {
                            is String -> changesObj.put(key, value)
                            is Int -> changesObj.put(key, value)
                            is Double -> changesObj.put(key, value)
                            is Boolean -> changesObj.put(key, value)
                            is Map<*, *> -> {
                                val nested = JSONObject()
                                @Suppress("UNCHECKED_CAST")
                                for ((k, v) in value as Map<String, Any>) {
                                    when (v) {
                                        is String -> nested.put(k, v)
                                        is Number -> nested.put(k, v)
                                        is Boolean -> nested.put(k, v)
                                        null -> nested.put(k, JSONObject.NULL)
                                        else -> nested.put(k, v.toString())
                                    }
                                }
                                changesObj.put(key, nested)
                            }
                            null -> changesObj.put(key, JSONObject.NULL)
                            else -> changesObj.put(key, value.toString())
                        }
                    }
                    put(changesObj)
                }
                itemsUpdArray.put(pairArray)
            }

            val body = JSONObject().apply {
                put("token", token)
                put("items_upd", itemsUpdArray)
            }

            OutputStreamWriter(connection.outputStream).use {
                it.write(body.toString())
                it.flush()
            }

            val responseCode = connection.responseCode
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()
            connection.disconnect()

            val json = JSONObject(response)
            Result.success(json.optString("message", ""))
        } catch (e: Exception) {
            Log.e(TAG, "updateItems error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteItems(token: String, itemIds: List<Int>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${NetworkConstants.BASE_URL}items/delete")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val idsArray = JSONArray()
            for (id in itemIds) {
                idsArray.put(id)
            }

            val body = JSONObject().apply {
                put("token", token)
                put("item_ids", idsArray)
            }

            OutputStreamWriter(connection.outputStream).use {
                it.write(body.toString())
                it.flush()
            }

            val responseCode = connection.responseCode
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()
            connection.disconnect()

            val json = JSONObject(response)
            Result.success(json.optString("message", ""))
        } catch (e: Exception) {
            Log.e(TAG, "deleteItems error: ${e.message}")
            Result.failure(e)
        }
    }

    // ==================== QR / RECEIPT ====================

    suspend fun scanReceiptQr(token: String, userId: Int, qrRaw: String): Result<List<BackendProductItem>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${NetworkConstants.BASE_URL}qr-text")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val body = JSONObject().apply {
                put("user_id", userId)
                put("qrraw", qrRaw)
                put("token", token)
            }

            OutputStreamWriter(connection.outputStream).use {
                it.write(body.toString())
                it.flush()
            }

            val responseCode = connection.responseCode
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()
            connection.disconnect()

            val json = JSONObject(response)
            val message = json.optString("message", "")

            if (message == "success") {
                val itemsArray = json.getJSONArray("items")
                val items = mutableListOf<BackendProductItem>()
                for (i in 0 until itemsArray.length()) {
                    val obj = itemsArray.getJSONObject(i)
                    items.add(
                        BackendProductItem(
                            name = obj.optString("name", "Неизвестный товар"),
                            quantity = obj.optString("quantity", "1"),
                            unit = obj.optString("unit", "шт"),
                            category = obj.optString("category", "Прочее"),
                            expiration = obj.optString("expiration", null)
                        )
                    )
                }
                Result.success(items)
            } else {
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "scanReceiptQr error: ${e.message}")
            Result.failure(e)
        }
    }

    // ==================== DATA CLASSES ====================

    data class AuthResponse(
        val userId: Int,
        val token: String,
        val message: String
    )

    data class UserInfo(
        val name: String,
        val email: String
    )

    data class BackendProduct(
        val id: Int,
        val userId: Int,
        val name: String,
        val category: String?,
        val expiration: String?,
        val quantity: Double,
        val unit: String,
        val deleted: Boolean
    )

    data class BackendProductItem(
        val name: String,
        val quantity: String,
        val unit: String = "шт",
        val category: String? = null,
        val expiration: String? = null
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("name", name)
            put("quantity", quantity)
            put("unit", unit)
            if (category != null) put("category", category)
            if (expiration != null) put("expiration", expiration)
        }
    }
}
