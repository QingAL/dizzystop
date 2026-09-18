package com.qingal.dizzystop

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class MotionCueService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: MotionCueOverlayView? = null
    private var sensorController: MotionSensorController? = null
    private var screenReceiverRegistered = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> sensorController?.stop()
                Intent.ACTION_SCREEN_ON -> {
                    if (!Settings.canDrawOverlays(this@MotionCueService)) {
                        stopSelf()
                    } else if (sensorController?.start() == false) {
                        showSensorUnavailableAndStop()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        broadcastState()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        if (!showOverlay()) {
            Toast.makeText(this, R.string.overlay_window_failed, Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        val view = overlayView ?: run {
            stopSelf()
            return
        }
        sensorController = MotionSensorController(this) { accelerationX, accelerationY ->
            view.setAcceleration(accelerationX, accelerationY)
        }
        registerScreenReceiver()

        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager.isInteractive && sensorController?.start() == false) {
            showSensorUnavailableAndStop()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        sensorController?.release()
        sensorController = null

        if (screenReceiverRegistered) {
            unregisterReceiver(screenStateReceiver)
            screenReceiverRegistered = false
        }

        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
            view.release()
        }
        overlayView = null

        isRunning = false
        broadcastState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay(): Boolean {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Android 12+ only lets touches pass through a non-touchable
            // application overlay when its window opacity does not exceed 0.8.
            alpha = 0.8f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        return runCatching {
            overlayView = MotionCueOverlayView(this).also { view ->
                windowManager.addView(view, params)
            }
        }.isSuccess
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenReceiverRegistered = true
    }

    private fun showSensorUnavailableAndStop() {
        Toast.makeText(this, R.string.sensor_unavailable, Toast.LENGTH_LONG).show()
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_text)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_motion_cue)
        .setContentTitle(getString(R.string.notification_title))
        .setContentText(getString(R.string.notification_text))
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .addAction(
            R.drawable.ic_motion_cue,
            getString(R.string.notification_stop),
            PendingIntent.getService(
                this,
                1,
                stopIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun broadcastState() {
        sendBroadcast(
            Intent(ACTION_STATE_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_RUNNING, isRunning)
        )
        MotionCueTileService.requestUpdate(this)
    }

    companion object {
        private const val CHANNEL_ID = "motion_cue_service"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.qingal.dizzystop.action.START"
        const val ACTION_STOP = "com.qingal.dizzystop.action.STOP"
        const val ACTION_STATE_CHANGED = "com.qingal.dizzystop.action.STATE_CHANGED"
        const val EXTRA_RUNNING = "running"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun startIntent(context: Context) =
            Intent(context, MotionCueService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context) =
            Intent(context, MotionCueService::class.java).setAction(ACTION_STOP)
    }
}
