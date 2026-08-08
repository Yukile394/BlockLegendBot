package com.silvera.blocklegendbot

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.graphics.Color

/**
 * Ana overlay servisi.
 * Hem floating ▶/■ butonu hem de ESP canvas içerir.
 * MediaProjection intent'i MainActivity'den gelir.
 */
class OverlayService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val EXTRA_TARGET_X    = "targetX"
        const val EXTRA_TARGET_Y    = "targetY"
        const val EXTRA_INTERVAL_MS = "intervalMs"
        const val EXTRA_BURST       = "burst"
        const val EXTRA_ESP_ON      = "espOn"
    }

    private lateinit var wm: WindowManager
    private lateinit var btn: TextView
    private lateinit var espView: EspOverlayView

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var espEngine: EspEngine? = null

    private var targetX    = 540f
    private var targetY    = 960f
    private var intervalMs = 100L
    private var burst      = 1
    private var espEnabled = false

    // ── Lifecycle ─────────────────────────────────────────────────────
    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        startForegroundNotif()
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        setupEspView()
        setupFloatingButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_STICKY

        targetX    = intent.getFloatExtra(EXTRA_TARGET_X, targetX)
        targetY    = intent.getFloatExtra(EXTRA_TARGET_Y, targetY)
        intervalMs = intent.getLongExtra(EXTRA_INTERVAL_MS, intervalMs)
        burst      = intent.getIntExtra(EXTRA_BURST, burst)
        espEnabled = intent.getBooleanExtra(EXTRA_ESP_ON, espEnabled)

        // MediaProjection başlat (ESP için)
        if (espEnabled) {
            val code = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
            val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
            if (data != null) startProjection(code, data)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        stopEverything()
        if (::btn.isInitialized)     wm.removeView(btn)
        if (::espView.isInitialized) wm.removeView(espView)
        super.onDestroy()
    }

    // ── ESP View ──────────────────────────────────────────────────────
    private fun setupEspView() {
        espView = EspOverlayView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(espView, params)
    }

    // ── Floating button ───────────────────────────────────────────────
    private fun setupFloatingButton() {
        btn = TextView(this).apply {
            text = "▶"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(0xCC1DB954.toInt())
            setPadding(28, 28, 28, 28)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 16; y = 200 }

        var lastX = 0f; var lastY = 0f; var drag = false
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN  -> { lastX = event.rawX; lastY = event.rawY; drag = false; true }
                MotionEvent.ACTION_MOVE  -> {
                    val dx = event.rawX - lastX; val dy = event.rawY - lastY
                    if (dx*dx + dy*dy > 25) drag = true
                    params.x += dx.toInt(); params.y += dy.toInt()
                    lastX = event.rawX; lastY = event.rawY
                    wm.updateViewLayout(btn, params); true
                }
                MotionEvent.ACTION_UP    -> { if (!drag) toggleClicking(); true }
                else -> false
            }
        }
        wm.addView(btn, params)
    }

    // ── Toggle ────────────────────────────────────────────────────────
    private fun toggleClicking() {
        val svc = AutoClickService.instance ?: return
        if (svc.isRunning) {
            stopEverything()
            btn.text = "▶"
            btn.setBackgroundColor(0xCC1DB954.toInt())
        } else {
            svc.startClicking(targetX, targetY, intervalMs, burst)
            btn.text = "■"
            btn.setBackgroundColor(0xCCE53935.toInt())
        }
    }

    private fun stopEverything() {
        AutoClickService.instance?.stopClicking()
        mediaProjection?.stop()
        mediaProjection = null
        imageReader?.close()
        imageReader = null
        espEngine?.destroy()
        espEngine = null
        espView.boxes = emptyList()
    }

    // ── MediaProjection / ESP ─────────────────────────────────────────
    private fun startProjection(resultCode: Int, data: Intent) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val W = metrics.widthPixels
        val H = metrics.heightPixels
        val D = metrics.densityDpi

        espEngine = EspEngine { boxes ->
            espView.boxes = boxes
        }

        imageReader = ImageReader.newInstance(W, H, android.graphics.PixelFormat.RGBA_8888, 2)
        imageReader!!.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            espEngine?.processImage(image, W, H)
        }, null)

        mediaProjection!!.createVirtualDisplay(
            "EspCapture", W, H, D,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )
    }
}

// ── Foreground notification (MediaProjection zorunluluğu) ─────────
private fun startForegroundNotif() {
    val notif = android.app.Notification.Builder(this, "bot_channel")
        .setContentTitle("Block Legend Bot")
        .setContentText("ESP + Auto-clicker çalışıyor")
        .setSmallIcon(android.R.drawable.ic_menu_compass)
        .build()
    startForeground(1, notif)
}
