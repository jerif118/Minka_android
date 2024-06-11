package com.minka.app


import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.prueba1.ui.theme.Prueba1Theme
import com.google.android.gms.ads.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView


private const val NOTIFICATIONS_KEY = "notifications"
val Context.dataStore by preferencesDataStore(name = "settings")
//MINKA

class MyNotificationListenerService : NotificationListenerService() {

    companion object {
        var notificationListener: ((NotificationData) -> Unit)? = null
        var allowedPackages = mutableStateListOf<String>()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("NotificationListener", "Notification Listener Connected")
        initializeAllowedPackages()
    }

    private fun initializeAllowedPackages() {
        val context = applicationContext
        val dataStore = context.dataStore
        val coroutineScope = CoroutineScope(Dispatchers.Main)

        coroutineScope.launch {
            val savedPackages = dataStore.data.map { preferences ->
                preferences[stringSetPreferencesKey("selected_apps")] ?: emptySet()
            }.first()
            allowedPackages.clear()
            allowedPackages.addAll(savedPackages)
            Log.d("NotificationListener", "Allowed Packages Initialized: $allowedPackages")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        if (allowedPackages.contains(sbn.packageName)) {
            val notification = sbn.notification
            val extras = notification.extras
            val title = extras.getString("android.title")
            val text = extras.getCharSequence("android.text")?.toString() // Convert CharSequence to String
            val appName = getApplicationName(packageManager, sbn.packageName)
            val amount = extractAmount(title, text)
            val name = extractName(appName, text)

            if (amount > 0) {
                val notificationData = NotificationData(
                    sbn.key,
                    title,
                    text,
                    appName,
                    sbn.packageName,
                    amount = amount,
                    date = sbn.postTime,
                    senderName = name
                )
                notificationListener?.invoke(notificationData)
                Log.d("NotificationListener", "Notification Posted: $title - $text from $appName")
                saveNotification(applicationContext, notificationData)
            }
        }
    }


    private val palabrasClaveTitulo = listOf("Confirmación de pago", "pago recibido", "te ha plineado","Interbank","oh!pay","Recibiste un pago","Scotiabank","BanBif","Caja Ica","Caja Sullana","Alfin","CAJA HUANCAYO","Caja Huancayo","Ligo")
    private val palabrasClaveTexto = listOf(" te ha plineado ", " te envió un pago por ","te pagó")

    private fun extractAmount(title: String?, text: String?): Double {
        if (title == null || text == null) return 0.0

        // Verificar si el título contiene alguna de las palabras clave
        val contienePalabraClaveTitulo = palabrasClaveTitulo.any { clave -> title.contains(clave, ignoreCase = true) }
        if (!contienePalabraClaveTitulo) return 0.0

        // Verificar si el texto sigue la estructura específica
        val estructuraValida = palabrasClaveTexto.any { clave -> text.contains(clave, ignoreCase = true) }
        if (!estructuraValida) return 0.0

        // Extraer la cantidad del texto
        val regex = Regex("""S\/ (\d+(\.\d{1,2})?)""")
        val matchResult = regex.find(text)
        matchResult?.let {
            return it.groupValues[1].toDouble()
        }
        return 0.0
    }

    private fun extractName(appName: String, text: String?): String {
        text?.let {
            return when (appName.lowercase()) {
                "interbank" -> {
                    val regex = Regex("""([A-Za-z\s]+) te ha plineado""")
                    val matchResult = regex.find(text)
                    matchResult?.groupValues?.get(1)?.trim() ?: "Desconocido"
                }
                "yape" -> {
                    val regex = Regex("""Yape! ([A-Za-z\s]+) te envió un pago por""")
                    val matchResult = regex.find(text)
                    matchResult?.groupValues?.get(1)?.trim() ?: "Desconocido"
                }
                else -> "Desconocido"
            }
        }
        return "Desconocido"
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        if (allowedPackages.contains(sbn.packageName)) {
            val notification = sbn.notification
            val extras = notification.extras
            val title = extras.getString("android.title")
            val text = extras.getCharSequence("android.text")?.toString() // Convert CharSequence to String
            val appName = getApplicationName(packageManager, sbn.packageName)
            Log.d("NotificationListener", "Notification Removed: $title - $text from $appName")
        }
    }

    private fun getApplicationName(packageManager: PackageManager, packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun saveNotification(context: Context, notification: NotificationData) {
        val dataStore = context.dataStore
        val coroutineScope = CoroutineScope(Dispatchers.IO)
        coroutineScope.launch {
            val notifications = loadNotifications(context).toMutableList()
            notifications.add(notification)
            val json = Gson().toJson(notifications)
            dataStore.edit { preferences ->
                preferences[stringPreferencesKey(NOTIFICATIONS_KEY)] = json
            }
        }
    }

    suspend fun loadNotifications(context: Context): List<NotificationData> {
        val dataStore = context.dataStore
        val json = dataStore.data.map { preferences ->
            preferences[stringPreferencesKey(NOTIFICATIONS_KEY)] ?: "[]"
        }.first()
        val type = object : TypeToken<List<NotificationData>>() {}.type
        return Gson().fromJson(json, type)
    }
}


data class NotificationData(
    val id: String,
    val title: String?,
    val text: String?,
    val appName: String,
    val packageName: String,
    val date: Long = System.currentTimeMillis(),
    val amount: Double = 0.0,
    val senderName: String = "Desconocido"

)


class NotificationViewModel : ViewModel() {
    val notifications = mutableStateListOf<NotificationData>()
    private val processedNotificationIds = mutableSetOf<String>()

    fun addNotification(notification: NotificationData) {
        if (notification.amount > 0 && !processedNotificationIds.contains(notification.id)) {
            notifications.add(notification)
            processedNotificationIds.add(notification.id)
        }
    }

    fun loadNotifications(context: Context) {
        val coroutineScope = CoroutineScope(Dispatchers.IO)
        coroutineScope.launch {
            val notificationsFromStore = MyNotificationListenerService().loadNotifications(context)
            withContext(Dispatchers.Main) {
                notifications.clear()
                processedNotificationIds.clear()
                notificationsFromStore.filter { it.amount > 0 }.forEach {
                    notifications.add(it)
                    processedNotificationIds.add(it.id)
                }
            }
        }
    }
}



class MainActivity : ComponentActivity() {
    private lateinit var tts: TextToSpeech
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //anuncios
        // Initialize the Google Mobile Ads SDK on a background thread.
        MobileAds.initialize(this@MainActivity) {}
        //MobileAds.initialize(this) {}
        //anuncios
        /*tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                tts.language = Locale.getDefault()
            }
        }
        */
        enableEdgeToEdge()
        setContent {
            Prueba1Theme {
                val navController = rememberNavController()
                val viewModel: NotificationViewModel = viewModel()
                viewModel.loadNotifications(applicationContext)

                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MyNotificationListenerService.notificationListener = {
                            viewModel.addNotification(it)
                        }

                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            MainScreen(
                                modifier = Modifier.padding(innerPadding),
                                viewModel = viewModel,
                                onManageAppsClicked = { navController.navigate("settings") },
                                onFabClicked = { navController.navigate("herramientas") }
                            )
                        }
                    }
                    composable("settings") {
                        AppSettingsScreen(onBack = { navController.navigateUp() })
                    }
                    composable("herramientas") {
                        Herramientas(navController = navController)
                    }
                    composable("resumen_de_ingresos") {
                        ResumenDeIngresos(viewModel = viewModel)
                    }
                    /*composable("lectura_notificaciones") {
                        LecturaNotificacionesScreen(navController = navController, viewModel = viewModel)
                    }
                       */
                }
            }
        }
    }
    /*override fun onDestroy() {
        super.onDestroy()
        tts.stop()
        tts.shutdown()
    }

     */
}


@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: NotificationViewModel,
    onManageAppsClicked: () -> Unit,
    onFabClicked: () -> Unit // Lambda para manejar el clic en el botón flotante
) {
    val notifications = viewModel.notifications.reversed()
    val context = LocalContext.current
    //var expanded by remember { mutableStateOf(false) } // Estado para controlar la visibilidad del DropdownMenu

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    context.startActivity(intent)
                }, modifier = Modifier.weight(1f)) {
                    Text("Acceso notif.")
                }

                Button(onClick = onManageAppsClicked, modifier = Modifier.weight(1f)) {
                    Text("Gestionar Apps")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                items(notifications.size) { index ->
                    val notification = notifications[index]
                    NotificationItem(notification)
                }
            }
        }

        // Botón flotante y menú desplegable
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 20.dp, end = 15.dp), // Ajusta estos valores según sea necesario
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButton(
                onClick = { onFabClicked() }, // Llama a la lambda cuando se haga clic en el FAB
                content = {
                    Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                }
            )
        }
    }
}

fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable) {
        return this.bitmap
    }

    val bitmap = Bitmap.createBitmap(this.intrinsicWidth, this.intrinsicHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    this.setBounds(0, 0, canvas.width, canvas.height)
    this.draw(canvas)
    return bitmap
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter", "QueryPermissionsNeeded")
@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val allowedPackages = listOf(
        "pe.com.interbank.mobilebanking",
        "com.bcp.innovacxion.yapeapp",
        "pe.indigital.tunki.user",
        "pe.com.interbank.mpay.customer",
        "com.cmac.cajamovilaqp",
        "com.bbva.nxt_peru",
        "pe.com.scotiabank.blpm.android.client",
        "pe.com.banBifBanking.icBanking.androidUI",
        "com.cmacica.prd",
        "com.pe.cajasullana.cajamovil",
        "com.alfinbanco.appclientes",
        "com.cajahuancayo.cajahuancayo.appcajahuancayo",
        "pe.com.tarjetasperuanasprepago.tppapp"

    )
    val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        .filter { appInfo ->
            val shouldInclude = allowedPackages.contains(appInfo.packageName)
            Log.d("AppSettingsScreen", "App: ${appInfo.packageName}, Should Include: $shouldInclude")
            shouldInclude
        }

    Log.d("AppSettingsScreen", "Installed Apps: ${installedApps.size}")

    val selectedAppsFlow: Flow<Set<String>> = context.dataStore.data.map { preferences: Preferences ->
        preferences[stringSetPreferencesKey("selected_apps")] ?: emptySet()
    }
    val selectedApps by selectedAppsFlow.collectAsState(initial = emptySet())
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestionar Aplicaciones") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .padding(16.dp)
                .padding(top = 80.dp)
        ) {
            items(installedApps.size) { index ->
                val appInfo = installedApps[index]
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                val appIcon = packageManager.getApplicationIcon(appInfo.packageName).toBitmap()
                val isSelected = selectedApps.contains(appInfo.packageName)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Image(
                        bitmap = appIcon.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(text = appName, modifier = Modifier.weight(1f))
                    Switch(
                        checked = isSelected,
                        onCheckedChange = { checked ->
                            val updatedSelectedApps = selectedApps.toMutableSet()
                            if (checked) {
                                updatedSelectedApps.add(appInfo.packageName)
                                MyNotificationListenerService.allowedPackages.add(appInfo.packageName)
                            } else {
                                updatedSelectedApps.remove(appInfo.packageName)
                                MyNotificationListenerService.allowedPackages.remove(appInfo.packageName)
                            }
                            coroutineScope.launch {
                                context.dataStore.edit { settings ->
                                    settings[stringSetPreferencesKey("selected_apps")] = updatedSelectedApps
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun NotificationItem(notification: NotificationData) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val appIcon = remember { getApplicationIcon(packageManager, notification.packageName) }

    // Convertir el tiempo de notificación a una cadena legible
    val time = remember(notification.date) {
        val date = Date(notification.date)
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        format.format(date)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            appIcon?.let {
                Image(
                    bitmap = it.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                //Text(text = "App: ${notification.appName}")
                Text(text = "${notification.title ?: "N/A"}")
                Text(text = "${notification.text ?: "N/A"}")
                Text(text = "$time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
        Divider(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}


private fun getApplicationIcon(packageManager: PackageManager, packageName: String): Drawable? {
    return try {
        packageManager.getApplicationIcon(packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
// Extension function to clear previous content
fun Modifier.clearContentAfter(): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
)

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Prueba1Theme {
        MainScreen(viewModel = NotificationViewModel(), onManageAppsClicked = {}, onFabClicked = {})
    }
}
@Preview(showBackground = true)
@Composable
fun HerramientasPreview() {
    val navController = rememberNavController() // NavController simulado
    Prueba1Theme {
        Herramientas(navController)
    }
}


//nueva pantalla
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Herramientas(navController: NavController) {
    val opciones = listOf(
        OpcionData("Resumen de ingresos", Icons.Default.Edit)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Herramientas") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                items(opciones) { opcion ->
                    OpcionItem(opcion = opcion, navController = navController)
                }
            }
            Banner1()
        }
    }
}

@Composable
fun Banner1() {
    val adRequest = AdManagerAdRequest.Builder().build()
    AndroidView(
        factory = { context ->
            AdManagerAdView(context).apply {
                setAdSize(AdSize.FLUID) // Utiliza un tamaño de anuncio adaptable
                adUnitId = "ca-app-pub-3940256099942544/9214589741"// ID de prueba
                //ca-app-pub-6966530780523209/6434493241
                loadAd(adRequest)
            }
        },
        update = {
            it.loadAd(adRequest)
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp) // Ajusta la altura según sea necesario
    )
}



//@Composable
//fun SeccionTitulo(titulo: String) {
//    Text(
//        text = titulo,
//        style = MaterialTheme.typography.headlineSmall,
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(vertical = 8.dp),
//        color = MaterialTheme.colorScheme.primary
//    )
//}

data class OpcionData(val nombre: String, val icono: ImageVector)

@Composable
fun OpcionItem(opcion: OpcionData, navController: NavController) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable {
                when (opcion.nombre) {
                    "Resumen de ingresos" -> navController.navigate("resumen_de_ingresos")
                    //"Lectura de notificaciones" -> navController.navigate("lectura_notificaciones")
                }
            }, // Navegación al hacer clic
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = opcion.icono,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(opcion.nombre, style = MaterialTheme.typography.bodyMedium)
        }
        Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
    }
}
//resumen de ingresos
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumenDeIngresos(viewModel: NotificationViewModel) {
    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    val notificaciones = viewModel.notifications.filter {
        val date = Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate()
        date == selectedDate
    }.sortedByDescending { it.date }

    val totalIngresos = notificaciones.sumOf { it.amount }
    val formattedTotalIngresos = String.format("%.2f", totalIngresos)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resumen de Ingresos") },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.onBackPressed() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { exportToExcel(context, notificaciones) }) {
                        Icon(imageVector = Icons.Default.List, contentDescription = "Exportar a tablas")
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(it)
                .padding(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                DatePickerDialog(" ", selectedDate) { selectedDate = it }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f), // Ocupa el espacio disponible
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                items(notificaciones) { notificacion ->
                    NotificationItemResumen(notificacion)
                }
            }
                Text(
                    text = "Total Ingresos: S/ $formattedTotalIngresos",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
        }
    }
}

@Composable
fun DatePickerDialog(label: String, date: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    calendar.time = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant())

    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onDateSelected(LocalDate.of(year, month + 1, dayOfMonth))
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = { datePickerDialog.show() }) {
            Text(date.toString())
        }
    }
}
@Composable
fun NotificationItemResumen(notification: NotificationData) {
    val time = remember(notification.date) {
        val date1 = Date(notification.date)
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        format.format(date1)
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "App: ${notification.appName}")
                Text(text = "Nombre: ${notification.senderName}")
                Text(text = "Monto: S/ ${notification.amount}")
                Text(text = "Hora: $time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }

        // Línea divisoria
        Divider(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}


//xlsx
fun exportToExcel(activity: Context, notificaciones: List<NotificationData>) {
    val workbook = XSSFWorkbook()
    val sheet = workbook.createSheet("Resumen de Ingresos")

    // Crear encabezados de columna
    val headerRow = sheet.createRow(0)
    headerRow.createCell(0).setCellValue("app")
    headerRow.createCell(1).setCellValue("Nombre")
    headerRow.createCell(2).setCellValue("Monto")

    // Llenar las filas con los datos
    notificaciones.forEachIndexed { index, notification ->
        if (notification.amount > 0) {
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(notification.appName)
            row.createCell(1).setCellValue(notification.senderName)
            row.createCell(2).setCellValue(notification.amount)
        }
    }
    // Crear un archivo temporal
    val fileName = "ResumenIngresos.xlsx"
    val file = File(activity.getExternalFilesDir(null), fileName)
    FileOutputStream(file).use { fileOut ->
        workbook.write(fileOut)
    }
    workbook.close()

    // Abrir un Intent para compartir el archivo
    val uri: Uri = FileProvider.getUriForFile(activity, activity.applicationContext.packageName + ".provider", file)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        putExtra(Intent.EXTRA_STREAM, uri)
    }

    activity.startActivity(Intent.createChooser(shareIntent, "Guardar archivo en"))
}
//lectura de notificaciones
/*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LecturaNotificacionesScreen(navController: NavController, viewModel: NotificationViewModel) {
    val context = LocalContext.current
    var isReadingEnabled by remember { mutableStateOf(false) }
    var isScreenOffEnabled by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    // Inicializa TextToSpeech
    val ttsInitListener = TextToSpeech.OnInitListener { status ->
        if (status != TextToSpeech.ERROR) {
            tts?.language = Locale.getDefault()
        }
    }

    LaunchedEffect(context) {
        tts = TextToSpeech(context, ttsInitListener)
    }

    DisposableEffect(context) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lectura de Notificaciones") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(it)
                .padding(16.dp)
        ) {
            Text(text = "Configuraciones de Lectura en Voz Alta", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Leer notificaciones en voz alta")
                Switch(checked = isReadingEnabled, onCheckedChange = { isReadingEnabled = it })
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Leer con pantalla apagada")
                Switch(checked = isScreenOffEnabled, onCheckedChange = { isScreenOffEnabled = it })
            }
        }
    }

    if (isReadingEnabled) {
        viewModel.notifications.forEach { notification ->
            tts?.speak(notification.text, TextToSpeech.QUEUE_ADD, null, null)
        }
    }
}
*/