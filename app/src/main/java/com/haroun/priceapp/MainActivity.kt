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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
                        products?.firstOrNull()?.let { firstProduct ->
                            val updatedProduct = firstProduct.copy(
                                price = (firstProduct.price.toDouble() + 1).toString(), // subir 1€
                                timestamp = System.currentTimeMillis() - (11*60 + 59) * 60 * 1000 // 11h59m atrás
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
                                    val response = RetrofitClient.api.getPrice(UrlRequest(product.url))
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

                                        val notification = NotificationCompat.Builder(context, channelId)
                                            .setContentTitle("Precio bajó: ${product.name}")
                                            .setContentText("De $oldPrice → $newPrice")
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

            Spacer(modifier = Modifier.height(16.dp))
            */

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

    LazyColumn {
        items(products) { product ->
            Text("${product.name} → ${product.price} (${formatDate(product.timestamp)})")
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