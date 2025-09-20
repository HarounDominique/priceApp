package com.haroun.priceapp

data class PriceResponse(
    val source: String,
    val name: String,
    val price: String,
    val currency: String
)
