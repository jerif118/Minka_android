    package com.minka.app

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
