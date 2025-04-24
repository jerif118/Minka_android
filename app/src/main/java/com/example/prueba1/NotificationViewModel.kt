    package com.minka.app

    import androidx.compose.runtime.mutableStateListOf
    import androidx.lifecycle.ViewModel
    import com.minka.app.NotificationData

    class NotificationViewModel : ViewModel() {
        val notifications = mutableStateListOf<NotificationData>()
        private val processedIds = mutableSetOf<String>()

        fun addNotification(n: NotificationData) {
            if (n.amount > 0 && processedIds.add(n.id)) {
                notifications.add(0,n)
            }
        }
    }
