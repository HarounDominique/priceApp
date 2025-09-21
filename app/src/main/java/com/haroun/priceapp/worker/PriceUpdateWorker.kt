package com.haroun.priceapp.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.haroun.priceapp.database.AppDatabase
import com.haroun.priceapp.entity.Product
import com.haroun.priceapp.RetrofitClient
import com.haroun.priceapp.UrlRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class PriceUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val CHANNEL_ID = "price_updates"
    private val NOTIF_ID = 1

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("PriceUpdateWorker", "Worker disparado: iniciando consulta automática")
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val productDao = db.productDao()

            val products: List<Product> = productDao.getAllProductsOnce()

            products.forEach { product ->
                try {
                    val response = retryOnHostResolution {
                        RetrofitClient.api.getPrice(UrlRequest(product.url))
                    }
                    val newPrice = response.price.toDoubleOrNull() ?: return@forEach
                    val oldPrice = product.price.toDoubleOrNull() ?: return@forEach

                    Log.d(
                        "PriceUpdateWorker",
                        "Consulta completada para ${product.name}: precio actual = $newPrice, precio base = $oldPrice"
                    )

                    // Versión final: solo disparar notificación si el precio bajó
                    if (newPrice < oldPrice) {
                        sendNotification(product.name, oldPrice, newPrice)
                    }

                } catch (e: Exception) {
                    Log.e("PriceUpdateWorker", "Error consultando ${product.url}: ${e.message}")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("PriceUpdateWorker", "Worker fallo: ${e.message}")
            Result.failure()
        }
    }

    /**
     * Reintenta una operación en caso de fallo de DNS (UnknownHostException).
     */
    private suspend fun <T> retryOnHostResolution(
        retries: Int = 3,
        delayMs: Long = 5000,
        block: suspend () -> T
    ): T {
        var lastError: Exception? = null
        repeat(retries) { attempt ->
            try {
                return block()
            } catch (e: java.net.UnknownHostException) {
                lastError = e
                if (attempt < retries - 1) {
                    Log.w("RetryHelper", "DNS falló (${e.message}), reintentando en ${delayMs}ms...")
                    delay(delayMs)
                }
            }
        }
        throw lastError ?: Exception("Error desconocido")
    }

    private fun truncate(text: String, maxLength: Int = 40): String {
        return if (text.length > maxLength) {
            text.take(maxLength).substringBeforeLast(" ") + "..."
        } else {
            text
        }
    }

    private fun sendNotification(productName: String, oldPrice: Double, newPrice: Double) {
        val notifManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Actualizaciones de precios",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notifManager.createNotificationChannel(channel)
        }

        val shortName = truncate(productName, 40)

        val percentageDiscount = ((oldPrice - newPrice) * 100 / oldPrice).toInt()

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("¡Hey, $shortName ha bajado de precio un $percentageDiscount%!")
            .setContentText("Antes costaba $oldPrice€, ahora cuesta $newPrice€")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notifManager.notify(NOTIF_ID + productName.hashCode(), notification)
    }
}