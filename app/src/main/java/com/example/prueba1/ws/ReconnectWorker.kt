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
        /**
         * Encola (o re‑emplaza) un intento único de reconexión con back‑off
         * exponencial.  Se usa desde [WebSocketManager.listener].
         */
        fun enqueue(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<ReconnectWorker>()
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.SECONDS       // primer intento tras 30 s
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