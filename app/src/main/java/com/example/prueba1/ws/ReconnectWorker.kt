package com.example.prueba1.ws

import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Worker que relanza el [WebSocketService] cuando la conexión
 * WebSocket se cae.  Usa back‑off exponencial para no agotar la batería.
 */
class ReconnectWorker(
    ctx: Context,
    params: WorkerParameters
) : Worker(ctx, params) {

    override fun doWork(): Result {
        val svc = Intent(applicationContext, WebSocketService::class.java)
        applicationContext.startForegroundService(svc)
        return Result.success()
    }

    companion object {
        fun enqueue(ctx: Context) {
            val prefs = ctx.getSharedPreferences("ws_prefs", Context.MODE_PRIVATE)
            if (prefs.getString("clientId", null).isNullOrEmpty()) {
                // No hay cliente vinculado, no enfilemos nada
                return
            }
            val req = OneTimeWorkRequestBuilder<ReconnectWorker>()
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(ctx).enqueueUniqueWork(
                "ws_reconnect",
                ExistingWorkPolicy.REPLACE,
                req
            )
        }
    }
}