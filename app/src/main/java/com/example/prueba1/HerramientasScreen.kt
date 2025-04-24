    package com.minka.app

    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.items
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.filled.ArrowBack
    import androidx.compose.material.icons.filled.ArrowForward
    import androidx.compose.material.icons.filled.Edit
    import androidx.compose.material3.*
    import androidx.compose.runtime.Composable
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.graphics.vector.ImageVector
    import androidx.compose.ui.unit.dp
    import androidx.navigation.NavController

    data class OpcionData(val nombre: String, val icono: ImageVector)

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun HerramientasScreen(navController: NavController) {
        val opciones = listOf(OpcionData("Resumen de ingresos", Icons.Default.Edit))
        Scaffold(
            topBar={
                TopAppBar(
                    title={ Text("Herramientas") },
                    navigationIcon={
                        IconButton(onClick={ navController.navigateUp() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription="Back")
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(Modifier.padding(padding).padding(16.dp)) {
                items(opciones) { opcion ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .clickable {
                                if(opcion.nombre=="Resumen de ingresos")
                                    navController.navigate("resumen_de_ingresos")
                            },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(opcion.nombre)
                        Icon(Icons.Default.ArrowForward, contentDescription=null)
                    }
                }
            }
        }
    }
