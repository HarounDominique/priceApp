package com.haroun.priceapp.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.haroun.priceapp.database.AppDatabase
import com.haroun.priceapp.entity.Product
import com.haroun.priceapp.RetrofitClient
import com.haroun.priceapp.UrlRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PriceUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val CHANNEL_ID = "price_updates"
    private val NOTIF_ID = 1

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val productDao = db.productDao()

            val products: List<Product> = productDao.getAllProductsOnce() // función suspend para obtener lista actual

            products.forEach { product ->
                try {
                    val response = RetrofitClient.api.getPrice(UrlRequest(product.url))

                    val newPrice = response.price.toDoubleOrNull() ?: return@forEach
                    val oldPrice = product.price.toDoubleOrNull() ?: return@forEach

                    if (newPrice < oldPrice) {
                        // Precio bajó, enviamos notificación
                        sendNotification(
                            productName = product.name,
                            oldPrice = oldPrice,
                            newPrice = newPrice
                        )
                    }

                    // NO actualizamos la base de datos
                } catch (_: Exception) {
                    // ignoramos errores de conexión
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun sendNotification(productName: String, oldPrice: Double, newPrice: Double) {
        val notifManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Crear canal si es Android >= O
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Actualizaciones de precios",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notifManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Precio bajó: $productName")
            .setContentText("De $oldPrice → $newPrice")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        notifManager.notify(NOTIF_ID + productName.hashCode(), notification)
    }
}
