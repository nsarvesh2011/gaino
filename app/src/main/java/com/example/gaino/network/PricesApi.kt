package com.example.gaino.network

import retrofit2.http.GET
import retrofit2.http.Query

data class PricesPayload(
    val tab: String,
    val asOf: String,
    val prices: Map<String, Double>
)

interface PricesApi {
    @GET("exec")
    suspend fun getStocks(@Query("tab") tab: String = "stocks"): PricesPayload
}
