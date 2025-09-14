package com.haroun.priceapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.haroun.priceapp.ui.theme.PriceAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PriceAppTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var url by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            MyTopAppBar("Price Checker")
        },
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
                    // Aquí iría la lógica de búsqueda de precio
                    result = "Buscando precio para: $url"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Buscar")
            }

            // Mostrar resultado
            result?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
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



@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    PriceAppTheme {
        MainScreen()
    }
}
