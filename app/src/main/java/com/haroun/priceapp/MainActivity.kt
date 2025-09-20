package com.haroun.priceapp

import android.os.Bundle
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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.haroun.priceapp.database.AppDatabase
import com.haroun.priceapp.entity.Product
import com.haroun.priceapp.entity.ProductDao
import com.haroun.priceapp.ui.theme.PriceAppTheme
import com.haroun.priceapp.worker.PriceUpdateWorker
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

                            // Guardar en DB
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

            result?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Aquí mostramos la lista de productos guardados
            ProductList(productDao = productDao)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTopAppBar(title: String) {
    TopAppBar(
        title = { Text(title) },
        colors = TopAppBarDefaults.topAppBarColors() // opcional, para colores por defecto
    )
}

@Composable
fun ProductList(productDao: ProductDao) {
    val products by productDao.getAllProducts().collectAsState(initial = emptyList())

    LazyColumn {
        items(products) { product ->
            Text("${product.name} → ${product.price} (${formatDate(product.timestamp)}) ${product.url}")
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
