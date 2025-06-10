package com.minka.app

data class NotificationData(
    val id: String,
    val title: String?,
    val text: String?,
    val appName: String,
    val packageName: String,
    val date: Long = System.currentTimeMillis(),
    val amount: Double = 0.0,
    val senderName: String = "Desconocido",
    val message: String? = null
) {
    /**
     * Secondary constructor for simple info messages (e.g., Doze mode notifications).
     */
    constructor(info: String) : this(
        id = "",
        title = null,
        text = info,
        appName = "",
        packageName = "",
        date = System.currentTimeMillis(),
        amount = 0.0,
        senderName = "Desconocido",
        message = null
    )
}
