package com.haroun.priceapp.entity

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: Product)

    @Query("SELECT * FROM products ORDER BY timestamp DESC")
    fun getAllProducts(): kotlinx.coroutines.flow.Flow<List<Product>>

    @Query("DELETE FROM products")
    suspend fun deleteAll()

    @Query("SELECT * FROM products ORDER BY timestamp DESC")
    suspend fun getAllProductsOnce(): List<Product>

}
