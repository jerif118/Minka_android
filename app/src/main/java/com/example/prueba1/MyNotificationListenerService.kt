package com.minka.app

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.regex.Pattern

/* ───────────────────────── Constantes DataStore ────────────────────────── */
private const val NOTIFICATIONS_KEY = "notifications"
private const val SELECTED_APPS_KEY = "selected_apps"

class MyNotificationListenerService : NotificationListenerService() {

    companion object {
        /** Recibirás TODO lo que provenga de estos paquetes */
        var allowedPackages = mutableStateListOf(
            "pe.com.interbank.mobilebanking",      // Plin
            "com.bcp.innovacxion.yapeapp",         // Yape
            "com.applemoncash"                     // Lemon Cash
        )

        /** Callback que tu ViewModel registra en MainActivity */
        var notificationListener: ((NotificationData) -> Unit)? = null
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

        // 3) Parsear para extraer monto y remitente
        val (amount, sender) = parseNotificationContent(sbn.packageName, title, text)

        // 4) Crear un NotificationData con los datos extraídos
        val n = NotificationData(
            id          = sbn.key,
            title       = title,
            text        = text,
            appName     = appLbl,
            packageName = sbn.packageName,
            date        = sbn.postTime,
            amount      = amount,
            senderName  = sender
        )

        // 5) Enviar al ViewModel
        notificationListener?.invoke(n)
        //enviar websocket
        WebSocketManager.sendNotification(n)

        // 6) Guardar en DataStore para histórico
        saveNotification(applicationContext, n)

        Log.d("NotificationListener", "→ [$appLbl] $title - $text")
        Log.d("NotificationListener", "→ Monto: $amount, Remitente: $sender")
    }

    /* ───────────── Parser de notificaciones ───────────── */

    private fun parseNotificationContent(packageName: String, title: String?, text: String?): Pair<Double, String> {
        // Valores por defecto
        var amount = 0.0
        var sender = "Desconocido"

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
                    if (fullText.contains("recibiste")) {
                        val senderPattern = Pattern.compile("(?:de|from)\\s+([\\w\\s]+?)(?:\\.|\\s+te)", Pattern.CASE_INSENSITIVE)
                        val senderMatcher = senderPattern.matcher(fullText)
                        if (senderMatcher.find()) {
                            sender = senderMatcher.group(1).trim()
                        }
                    }
                }
                "com.bcp.innovacxion.yapeapp" -> { // Yape
                    if (fullText.contains("te envió") || fullText.contains("te yapeo")) {
                        val senderPattern = Pattern.compile("([\\w\\s]+?)\\s+te", Pattern.CASE_INSENSITIVE)
                        val senderMatcher = senderPattern.matcher(fullText)
                        if (senderMatcher.find()) {
                            sender = senderMatcher.group(1).trim()
                        }
                    }
                }
                "com.applemoncash" -> { // Lemon Cash
                    if (fullText.contains("recibiste")) {
                        val senderPattern = Pattern.compile("de\\s+([\\w\\s]+)", Pattern.CASE_INSENSITIVE)
                        val senderMatcher = senderPattern.matcher(fullText)
                        if (senderMatcher.find()) {
                            sender = senderMatcher.group(1).trim()
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("NotificationParser", "Error parsing notification", e)
        }

        return Pair(amount, sender)
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