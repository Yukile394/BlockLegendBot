package com.silvera.blocklegendbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Burst-capable auto-clicker.
 * Her cycle'da MAX_BURST kadar stroke tek GestureDescription içine paketlenir
 * → çok daha yüksek CPS, throttle yok.
 */
class AutoClickService : AccessibilityService() {

    companion object {
        var instance: AutoClickService? = null
            private set

        const val MIN_INTERVAL_MS = 16L   // ~60 CPS teorik üst sınır
        const val MAX_BURST       = 5     // tek gesture içinde kaç tap
    }

    private val handler = Handler(Looper.getMainLooper())
    private var clickRunnable: Runnable? = null

    var isRunning = false
        private set

    // ── Lifecycle ─────────────────────────────────────────────────────
    override fun onServiceConnected() { instance = this }
    override fun onDestroy()         { stopClicking(); instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) = Unit
    override fun onInterrupt()       = stopClicking()

    // ── Public API ────────────────────────────────────────────────────

    /**
     * @param x          Hedef X
     * @param y          Hedef Y
     * @param intervalMs Tıklama aralığı ms (min 16)
     * @param burst      Her cycle'da kaç tap (1-5)
     */
    fun startClicking(x: Float, y: Float, intervalMs: Long = 100L, burst: Int = 1) {
        stopClicking()
        isRunning = true
        val safeInterval = intervalMs.coerceAtLeast(MIN_INTERVAL_MS)
        val safeBurst    = burst.coerceIn(1, MAX_BURST)

        clickRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                performBurst(x, y, safeBurst, safeInterval)
                handler.postDelayed(this, safeInterval * safeBurst)
            }
        }
        handler.post(clickRunnable!!)
    }

    fun stopClicking() {
        isRunning = false
        clickRunnable?.let { handler.removeCallbacks(it) }
        clickRunnable = null
    }

    // ── Internal ──────────────────────────────────────────────────────

    /**
     * Tek GestureDescription içine 'count' adet stroke koy.
     * Her stroke öncekinden strokeDelayMs sonra başlar → gerçek burst.
     */
    private fun performBurst(x: Float, y: Float, count: Int, strokeDelayMs: Long) {
        val builder = GestureDescription.Builder()
        repeat(count) { i ->
            val path = Path().apply { moveTo(x, y) }
            builder.addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    i * strokeDelayMs,   // startTime
                    40L                  // duration (kısa = tap)
                )
            )
        }
        dispatchGesture(builder.build(), object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) = Unit
            override fun onCancelled(g: GestureDescription) = Unit
        }, null)
    }
}
