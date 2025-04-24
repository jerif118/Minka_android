package com.minka.app

/*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LecturaNotificacionesScreen(
    navController: NavController,
    viewModel: NotificationViewModel
) {
    val context = LocalContext.current
    var isReadingEnabled  by remember { mutableStateOf(false) }
    var isScreenOffEnabled by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(it)
                .padding(16.dp)
        ) {
            Text("Configuraciones de Lectura en Voz Alta", style=MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Leer notificaciones en voz alta")
                Switch(checked=isReadingEnabled, onCheckedChange={ isReadingEnabled=it })
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Leer con pantalla apagada")
                Switch(checked=isScreenOffEnabled, onCheckedChange={ isScreenOffEnabled=it })
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


