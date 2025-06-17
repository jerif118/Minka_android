package com.minka.app

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.prueba1.ws.WebSocketManager
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.regex.Pattern

/* ───────────────────────── Constantes DataStore ────────────────────────── */
private const val NOTIFICATIONS_KEY = "notifications"
private const val SELECTED_APPS_KEY = "selected_apps"

class MyNotificationListenerService : NotificationListenerService() {

    companion object {
        /** Recibirás TODO lo que provenga de estos paquetes */
        var allowedPackages = mutableStateListOf(
            "pe.com.interbank.mobilebanking", //interbank
            "com.bcp.innovacxion.yapeapp", //yape
            "com.applemoncash", //lemon
            //"com.bitel.bipay", //bipay
            "pe.indigital.tunki.user", //agora
            "com.pdp.bim"  //bim
        )

        /** Callback que tu ViewModel registra en MainActivity */
        var notificationListener: ((NotificationData) -> Unit)? = null

        // Mapa de filtros de título por paquete para validación adicional
        private val titleFilters = mapOf(
            "com.bcp.innovacxion.yapeapp" to listOf("confirmación de pago", "confirmacion de pago"),
            "com.applemoncash"          to listOf("recibiste s/", "recibiste "),
            "pe.com.interbank.mobilebanking" to listOf("Interbank", "interbank"),
            "pe.indigital.tunki.user" to listOf(
                "oh!pay | recibiste un pago",
                "oh!pay|recibiste un pago"
            )
        )

        // Map of regex patterns by package to validate notification body
        private val contentFilters = mapOf(
            "com.bcp.innovacxion.yapeapp" to Pattern.compile(
                "(?:yape!\\s*.+?te envió un pago por\\s*s/\\s*[0-9]+(?:[.,][0-9]{1,2})?|te envió un pago por\\s*s/\\s*[0-9]+(?:[.,][0-9]{1,2})?)",
                Pattern.CASE_INSENSITIVE
            ),
            "com.applemoncash" to Pattern.compile(
                "(?:recibiste\\s*s/\\s*[0-9]+(?:[.,][0-9]{1,2})?)|(?:te envió dinero)",
                Pattern.CASE_INSENSITIVE
            ),
            "pe.com.interbank.mobilebanking" to Pattern.compile(
                "\\s*.+?te ha plineado\\s*s/\\s*[0-9]+(?:[.,][0-9]{1,2})?",
                Pattern.CASE_INSENSITIVE
            ),
            "pe.indigital.tunki.user" to Pattern.compile(
                "\\s*.+?te pagó\\s*s/\\s*[0-9]+(?:[.,][0-9]{1,2})?",
                Pattern.CASE_INSENSITIVE
            )
        )
    }

    /* ───────────── Al conectar, intenta cargar selección del usuario ────── */
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("NotificationListener", "Notification Listener Connected")

        CoroutineScope(Dispatchers.IO).launch {
            val saved = applicationContext.dataStore.data
                .map { it[stringSetPreferencesKey(SELECTED_APPS_KEY)] ?: emptySet() }
                .first()

            if (saved.isNotEmpty()) {
                allowedPackages.clear()
                allowedPackages.addAll(saved)
            }
            Log.d("NotificationListener", "allowedPackages = $allowedPackages")
        }
    }

    /* ──────────────────  Captura de notificaciones en tiempo real ───────── */
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 1) Ignorar si no es de una app "habilitada"
        if (!allowedPackages.contains(sbn.packageName)) return

        // 2) Extraer título y texto tal cual
        val extras = sbn.notification.extras
        val title  = extras.getString("android.title")
        val text   = extras.getCharSequence("android.text")?.toString()
        val appLbl = getApplicationName(packageManager, sbn.packageName)

        Log.d("NotificationListener", "📨 NOTIFICACIÓN CRUDA RECIBIDA de [$appLbl]")
        Log.d("NotificationListener", "    Título: $title")
        Log.d("NotificationListener", "   Texto: $text")

        // Validación de títulos permitidos por paquete, si existe filtro
        val filters = titleFilters[sbn.packageName]
        if (filters != null) {
            val titleLower = title?.lowercase() ?: ""
            if (filters.none { keyword -> titleLower.contains(keyword) }) return
        }
        // Validar contenido del mensaje si hay un filtro definido
        val contentPattern = contentFilters[sbn.packageName]
        if (contentPattern != null) {
            val textLower = text?.lowercase() ?: ""
            if (!contentPattern.matcher(textLower).find()) return
        }
        //val appLbl = getApplicationName(packageManager, sbn.packageName)

        // 3) Parsear para extraer monto y remitente
        val (amount, sender) = parseNotificationContent(sbn.packageName, title, text)

        val parsedData = parseNotificationContent(sbn.packageName, title, text)
        // 4) Crear un NotificationData con los datos extraídos
        val n = NotificationData(
            id          = sbn.key,
            title       = title,
            text        = text,
            appName     = appLbl,
            packageName = sbn.packageName,
            date        = sbn.postTime,
            amount      = amount,
            senderName  = sender,
            securityCode = parsedData.securityCode
        )

        // 5) Enviar al ViewModel
        notificationListener?.invoke(n)
        Log.d("NotificationListener", "➡️ PAYLOAD A ENVIAR: $n")
        //enviar websocket
        WebSocketManager.sendNotification(n)

        // 6) Guardar en DataStore para histórico
        saveNotification(applicationContext, n)

        Log.d("NotificationListener", "→ [$appLbl] $title - $text")
        Log.d("NotificationListener", "→ Monto: $amount, Remitente: $sender")
    }

    private data class ParsedResult(
        val amount: Double,
        val sender: String,
        val securityCode: String? = null // Nullable, porque no siempre estará presente
    )

    /* ───────────── Parser de notificaciones ───────────── */

    private fun parseNotificationContent(packageName: String, title: String?, text: String?): ParsedResult {
        // Valores por defecto
        var amount = 0.0
        var sender = "Desconocido"
        var securityCode: String? = null // NUEVO: Variable para el código de seguridad

        val fullText = "$title $text".lowercase()

        try {
            // Buscar patrones de monto (S/XX.XX or SXXX,XX)
            val amountPattern = Pattern.compile("s/\\s*(\\d+([.,]\\d+)?)|s\\.?\\s*(\\d+([.,]\\d+)?)")
            val amountMatcher = amountPattern.matcher(fullText)

            if (amountMatcher.find()) {
                val amountStr = amountMatcher.group(0)
                    .replace("s/", "")
                    .replace("s.", "")
                    .replace("s", "")
                    .replace(",", ".")
                    .trim()

                try {
                    amount = amountStr.toDouble()
                } catch (e: Exception) {
                    Log.e("NotificationParser", "Error parsing amount: $amountStr", e)
                }
            }

            // Extraer remitente según la app
            when (packageName) {
                "pe.com.interbank.mobilebanking" -> { // Plin
                    // Extract sender and amount from Interbank Plin notifications
                    val body = text ?: ""
                    val pattern = Pattern.compile(
                        "(.+?)\\s+te ha plineado\\s*s/\\s*([0-9]+(?:[.,][0-9]{1,2})?)",
                        Pattern.CASE_INSENSITIVE
                    )
                    val matcher = pattern.matcher(body)
                    if (matcher.find()) {
                        // Sender name
                        sender = matcher.group(1).trim()
                        // Amount override (optional, based on group)
                        amount = matcher.group(2).replace(",", ".").toDoubleOrNull() ?: amount
                    }

                }
                "com.bcp.innovacxion.yapeapp" -> { // Yape
                    val body = text ?: ""
                    val patternWithCode = Pattern.compile(
                        "(.+?)\\s+te envió un pago por\\s*s/\\s*([0-9]+(?:[.,][0-9]{1,2})?).*?c[oó]d\\.?\\s*de seguridad\\s*(?:es)?[: ]*([0-9]+)",
                        Pattern.CASE_INSENSITIVE
                    )
                    var matcher = patternWithCode.matcher(body)

                    if (matcher.find()) {
                        Log.d("NotificationParser", "Yape: Patrón CON código encontrado.")
                        sender = matcher.group(1)?.trim() ?: "Desconocido"
                        amount = matcher.group(2)?.replace(",",".")?.toDoubleOrNull() ?: 0.0
                        // ¡AQUÍ ESTÁ LA MAGIA! Guardamos el código en su propia variable
                        securityCode = matcher.group(3)?.trim()
                        // YA NO es necesario modificar el 'sender'. Lo dejamos limpio.
                        // sender = "$sender (CÓDIGO: $securityCode)" // <--- LÍNEA ELIMINADA

                    } else {
                        Log.d("NotificationParser", "Yape: Patrón CON código no encontrado. Intentando patrón SIN código.")
                        val patternWithoutCode = Pattern.compile(
                            "(.+?)\\s+te envió un pago por\\s*s/\\s*([0-9]+(?:[.,][0-9]{1,2})?)",
                            Pattern.CASE_INSENSITIVE
                        )
                        matcher = patternWithoutCode.matcher(body)
                        if (matcher.find()) {
                            sender = matcher.group(1)?.trim() ?: "Desconocido"
                            amount = matcher.group(2)?.replace(",",".")?.toDoubleOrNull() ?: 0.0
                        }
                    }
                }
                "com.applemoncash" -> { // Lemon Cash
                    // Use the notification body to extract the sender’s name
                    val body = text ?: ""
                    val patternLC = Pattern.compile("(.+?) te envió dinero", Pattern.CASE_INSENSITIVE)
                    val matcherLC = patternLC.matcher(body)
                    if (matcherLC.find()) {
                        sender = matcherLC.group(1).trim()
                    }
                }
                "pe.indigital.tunki.user" -> { // Tunki / Agora
                    // Extract sender and amount from Tunki payment notifications using the notification text only
                    val body = text ?: ""
                    val pattern = Pattern.compile(
                        "(.+?)\\s+te pagó\\s*s/\\s*([0-9]+(?:[.,][0-9]{1,2})?)",
                        Pattern.CASE_INSENSITIVE
                    )
                    val matcher = pattern.matcher(body)
                    if (matcher.find()) {
                        sender = matcher.group(1).trim()
                        amount = matcher.group(2).replace(",", ".").toDoubleOrNull() ?: amount
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("NotificationParser", "Error parsing notification", e)
        }

        // Convert sender name to all uppercase
        //return Pair(amount, sender)
        sender = sender.uppercase()
        return ParsedResult(amount, sender, securityCode)
    }

    /* ───────────── Utilidades y persistencia ───────────── */

    private fun getApplicationName(pm: android.content.pm.PackageManager, pkg: String): String =
        try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
        catch (_: Exception) { pkg }

    private fun saveNotification(context: Context, n: NotificationData) {
        CoroutineScope(Dispatchers.IO).launch {
            val jsonList = Gson().toJson(listOf(n) + loadNotifications(context))
            context.dataStore.edit { it[stringPreferencesKey(NOTIFICATIONS_KEY)] = jsonList }
        }
    }

    suspend fun loadNotifications(context: Context): List<NotificationData> {
        val json = context.dataStore.data
            .map { it[stringPreferencesKey(NOTIFICATIONS_KEY)] ?: "[]" }
            .first()
        val type = object : com.google.gson.reflect.TypeToken<List<NotificationData>>() {}.type
        return Gson().fromJson(json, type)
    }
}