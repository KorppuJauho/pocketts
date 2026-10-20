package io.github.korppujauho.mobilets6

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager

/**
 * Keeps the app running while connected, with the screen off or another app open: a notification (Android's rule
 * for that), and locks that keep the processor and Wi-Fi awake so voice keeps flowing.
 */
class ConnectionService : Service() {

    companion object {
        private const val CHANNEL = "connection"
        private const val ID = 1
        private const val ACTION_SHOW = "show"
        private const val ACTION_DISCONNECT = "disconnect"
        private const val EXTRA_TEXT = "text"

        fun start(context: Context, text: String) {
            context.startForegroundService(
                Intent(context, ConnectionService::class.java).setAction(ACTION_SHOW).putExtra(EXTRA_TEXT, text)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectionService::class.java))
        }

        private const val MESSAGES = "messages"
        private var nextMessageId = 100

        /** A private message or a poke while the app isn't on screen. */
        fun notifyMessage(context: Context, title: String, text: String) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(MESSAGES) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(MESSAGES, context.getString(R.string.notification_messages), NotificationManager.IMPORTANCE_HIGH)
                )
            }
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = Notification.Builder(context, MESSAGES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                nm.notify(nextMessageId++, n)
            } catch (_: SecurityException) {
                // Notifications not allowed
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            Session.disconnect()
            return START_NOT_STICKY
        }
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: getString(R.string.app_name)
        startForeground(ID, notification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        if (wakeLock == null) {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MobileTS6:connection").apply { acquire() }
            @Suppress("DEPRECATION")
            wifiLock = (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "MobileTS6:connection").apply { acquire() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        wifiLock = null
        super.onDestroy()
    }

    // Swiping the app away from recent apps leaves the server too.
    override fun onTaskRemoved(rootIntent: Intent?) {
        Session.disconnect()
        super.onTaskRemoved(rootIntent)
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnect = PendingIntent.getService(
            this, 1, Intent(this, ConnectionService::class.java).setAction(ACTION_DISCONNECT), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.disconnect), disconnect).build())
            .build()
    }
}
