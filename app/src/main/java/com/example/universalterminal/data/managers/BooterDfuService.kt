package com.example.universalterminal.data.managers


import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import no.nordicsemi.android.dfu.DfuBaseService

class BooterDfuService : DfuBaseService() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun getNotificationTarget(): Class<out Activity>? =
        Class.forName(ACTIVITY) as? Class<Activity>


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DfuBaseService.NOTIFICATION_CHANNEL_DFU,
                getString(android.R.string.ok),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Firmware update"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val ACTIVITY = "com.example.universalterminal.presentation.theme.MainActivity"
    }
}