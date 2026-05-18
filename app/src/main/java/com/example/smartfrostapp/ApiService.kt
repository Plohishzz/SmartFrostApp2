package com.example.smartfrostapp

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

data class QrRequest(
    val qrraw: String
)

interface ApiService {

    @POST("items")
    fun sendQr(
        @Body request: QrRequest
    ): Call<ResponseBody>
}