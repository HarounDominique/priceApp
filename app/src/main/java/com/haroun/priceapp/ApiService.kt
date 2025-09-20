package com.haroun.priceapp

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface ApiService {
    @Headers("Content-Type: application/json")
    @POST("/get_price")
    suspend fun getPrice(@Body request: UrlRequest): PriceResponse
}

data class UrlRequest(val url: String)
