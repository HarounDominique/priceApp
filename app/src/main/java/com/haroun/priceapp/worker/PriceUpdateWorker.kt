package com.haroun.priceapp.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.haroun.priceapp.RetrofitClient
import com.haroun.priceapp.UrlRequest
import com.haroun.priceapp.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PriceUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val productDao = db.productDao()

            val products = productDao.getAllProductsOnce() // ⚠️ método que devuelve lista, no Flow

            for (product in products) {
                val response = RetrofitClient.api.getPrice(UrlRequest(product.url))
                val updatedProduct = product.copy(
                    price = response.price,
                    timestamp = System.currentTimeMillis()
                )
                productDao.insert(updatedProduct)

                // TODO: Aquí podrías disparar una notificación si el precio cambió
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
