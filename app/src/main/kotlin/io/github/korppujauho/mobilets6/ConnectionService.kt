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
        private const val EXTRA_CAMERA = "camera"
        private const val EXTRA_PROJECTION = "projection"

        /** Runs once the service is in the foreground with its new kinds (screen sharing must wait for that). */
        @Volatile var onForeground: (() -> Unit)? = null

        fun start(
            context: Context,
            text: String,
            // By default what's being shared now (so other updates don't drop the camera or the screen)
            camera: Boolean = Session.state.value.stream.sharing == io.github.korppujauho.mobilets6.proto.Streams.Kind.CAMERA,
            projection: Boolean = Session.state.value.stream.sharing == io.github.korppujauho.mobilets6.proto.Streams.Kind.SCREEN,
        ) {
            context.startForegroundService(
                Intent(context, ConnectionService::class.java).setAction(ACTION_SHOW).putExtra(EXTRA_TEXT, text)
                    .putExtra(EXTRA_CAMERA, camera).putExtra(EXTRA_PROJECTION, projection)
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
        // The microphone may be used in the background only with its permission granted (and the service saying so)
        val mic = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val camera = intent?.getBooleanExtra(EXTRA_CAMERA, false) == true &&
            checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val projection = intent?.getBooleanExtra(EXTRA_PROJECTION, false) == true
        val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
            (if (mic) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0) or
            (if (camera) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0) or
            (if (projection) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0)
        try {
            startForeground(ID, notification(text), types)
        } catch (e: Exception) {
            AppLog.log("Foreground with kinds $types refused, playback only: $e")
            startForeground(ID, notification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        }
        onForeground?.let { run ->
            onForeground = null
            run()
        }
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
