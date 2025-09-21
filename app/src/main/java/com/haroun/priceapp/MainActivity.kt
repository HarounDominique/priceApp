package com.haroun.priceapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.haroun.priceapp.database.AppDatabase
import com.haroun.priceapp.entity.Product
import com.haroun.priceapp.entity.ProductDao
import com.haroun.priceapp.ui.theme.PriceAppTheme
import com.haroun.priceapp.worker.PriceUpdateWorker
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.truncate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PriceAppTheme {
                MainScreen()
            }
        }

        val workRequest = PeriodicWorkRequestBuilder<PriceUpdateWorker>(
            12, java.util.concurrent.TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PriceUpdateWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val productDao = db.productDao()

    var url by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val workManager = WorkManager.getInstance(context)

    Scaffold(
        topBar = { MyTopAppBar("Price Checker") },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Pega la URL del producto") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    scope.launch {
                        try {
                            loading = true
                            val response = RetrofitClient.api.getPrice(UrlRequest(url))
                            result = "${response.name} → ${response.price} ${response.currency}"

                            // Guardar en DB (sobrescribe si ya existe la misma URL)
                            val product = Product(
                                url = url,
                                name = response.name,
                                price = response.price,
                                timestamp = System.currentTimeMillis()
                            )
                            productDao.insert(product)

                        } catch (e: Exception) {
                            result = "Error: ${e.message}"
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading
            ) {
                Text(if (loading) "Buscando..." else "Buscar")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --------------------
            // 🔧 Botón para simular cambio de precio (TEST)
            // --------------------
            /*
            Button(
                onClick = {
                    scope.launch {
                        val products = productDao.getAllProducts().firstOrNull()
                        products?.forEach { product ->
                            val updatedProduct = product.copy(
                                price = (product.price.toDouble() + 1).toString(), // subir 1€
                                timestamp = System.currentTimeMillis() - (11 * 60 + 59) * 60 * 1000 // 11h59m atrás
                            )
                            productDao.insert(updatedProduct)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Simular cambio de precio")
            }

            Spacer(modifier = Modifier.height(16.dp))

            */

            // --------------------
            // 🔧 Botón para forzar consulta automática (TEST)
            // --------------------
            /*
            // Función para truncar texto con "..."
            fun truncate(text: String, maxLength: Int = 40): String {
                return if (text.length > maxLength) {
                    text.take(maxLength).substringBeforeLast(" ") + "..."
                } else {
                    text
                }
            }

            // Reintento en caso de fallo DNS (igual que en PriceUpdateWorker)
            suspend fun <T> retryOnHostResolution(
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
                            kotlinx.coroutines.delay(delayMs)
                        }
                    }
                }
                throw lastError ?: Exception("Error desconocido")
            }

            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        try {
                            // Esperar 3 segundos antes de hacer la consulta
                            kotlinx.coroutines.delay(3000)

                            val products = productDao.getAllProductsOnce()
                            products.forEach { product ->
                                try {
                                    val response = retryOnHostResolution {
                                        RetrofitClient.api.getPrice(UrlRequest(product.url))
                                    }
                                    val newPrice = response.price.toDoubleOrNull() ?: return@forEach
                                    val oldPrice = product.price.toDoubleOrNull() ?: return@forEach

                                    Log.d(
                                        "ForceCheck",
                                        "Consulta completada para ${product.name}, precio actual = $newPrice, precio base = $oldPrice"
                                    )

                                    if (newPrice < oldPrice) {
                                        val notifManager =
                                            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                                        val channelId = "price_updates"
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            val channel = NotificationChannel(
                                                channelId,
                                                "Actualizaciones de precios",
                                                NotificationManager.IMPORTANCE_HIGH
                                            )
                                            notifManager.createNotificationChannel(channel)
                                        }

                                        val shortName = truncate(product.name, 40)
                                        val percentageDiscount = ((oldPrice - newPrice) * 100 / oldPrice).toInt()

                                        val notification = NotificationCompat.Builder(context, channelId)
                                            .setContentTitle("¡Hey, $shortName ha bajado de precio un $percentageDiscount%!")
                                            .setContentText("Antes costaba $oldPrice€, ahora cuesta $newPrice€")
                                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                                            .build()

                                        notifManager.notify(product.name.hashCode(), notification)

                                        Log.d("ForceCheck", "📢 Notificación enviada para ${product.name}")
                                    }

                                } catch (e: Exception) {
                                    Log.e("ForceCheck", "Error consultando ${product.url}: ${e.message}")
                                }
                            }

                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Forzar consulta automática")
            }
            */
            Spacer(modifier = Modifier.height(16.dp))

            // Lista de productos guardados
            ProductList(productDao = productDao)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTopAppBar(title: String) {
    TopAppBar(
        title = { Text(title) },
        colors = TopAppBarDefaults.topAppBarColors()
    )
}

@Composable
fun ProductList(productDao: ProductDao) {
    val products by productDao.getAllProducts().collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        items(products) { product ->
            ProductCard(product)
            Spacer(modifier = Modifier.height(8.dp)) // separación entre tarjetas
        }
    }
}

@Composable
fun ProductCard(product: Product) {
    val shortName = truncate(product.name)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Aquí puedes abrir detalle o modal */ },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = shortName,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${product.price}€",
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFF388E3C) // verde para precio
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatDate(product.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    PriceAppTheme {
        MainScreen()
    }
}

private fun truncate(text: String, maxLength: Int = 40): String {
    return if (text.length > maxLength) {
        text.take(maxLength).substringBeforeLast(" ") + "..."
    } else {
        text
    }
}