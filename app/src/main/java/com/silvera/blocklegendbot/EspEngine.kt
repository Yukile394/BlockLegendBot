package com.silvera.blocklegendbot

import android.graphics.*
import android.media.Image
import android.os.Handler
import android.os.HandlerThread
import java.nio.ByteBuffer

/**
 * Block Legend ESP motoru.
 *
 * MediaProjection'dan gelen Bitmap'i analiz eder:
 *  - Düşman rengi   → Kırmızı box  (ENEMY)
 *  - Item/bonus     → Sarı box     (ITEM)
 *  - Boss tespiti   → Mor box      (BOSS)
 *
 * Renk eşiği ve küme boyutu dışarıdan ayarlanabilir.
 */
class EspEngine(private val onBoxesReady: (List<EspOverlayView.EspBox>) -> Unit) {

    // ── Ayarlanabilir renk hedefleri ──────────────────────────────────
    data class ColorTarget(
        val name    : String,
        val hsvMin  : FloatArray,   // [H_min, S_min, V_min]
        val hsvMax  : FloatArray,   // [H_max, S_max, V_max]
        val boxColor: Int,
        val hWrap   : Boolean = false   // kırmızı için H 0-15 + 345-360
    )

    private val targets = mutableListOf(
        // Kırmızı düşman rengi  (H≈0-15 veya 345-360, yüksek doygunluk)
        ColorTarget(
            name     = "ENEMY",
            hsvMin   = floatArrayOf(340f, 0.55f, 0.45f),
            hsvMax   = floatArrayOf(360f, 1.0f,  1.0f),
            boxColor = Color.RED,
            hWrap    = true
        ),
        // Altın/item sarısı  (H≈35-60)
        ColorTarget(
            name     = "ITEM",
            hsvMin   = floatArrayOf(35f, 0.6f, 0.7f),
            hsvMax   = floatArrayOf(60f, 1.0f, 1.0f),
            boxColor = Color.YELLOW
        ),
        // Mor boss (H≈270-310)
        ColorTarget(
            name     = "BOSS",
            hsvMin   = floatArrayOf(265f, 0.4f, 0.35f),
            hsvMax   = floatArrayOf(315f, 1.0f, 1.0f),
            boxColor = 0xFFAA00FF.toInt()
        )
    )

    // Algılanacak minimum piksel kümesi boyutu (küçük nesneleri filtreler)
    var minClusterPixels = 120

    // Bitmap'i ne kadar ölçeklendirilerek analiz edilsin (hız/hassasiyet dengesi)
    var scanScale = 0.25f

    private val bgThread = HandlerThread("EspEngine").also { it.start() }
    private val bgHandler = Handler(bgThread.looper)

    // ── Public API ────────────────────────────────────────────────────

    /** MediaProjection Image'ını işle */
    fun processImage(image: Image, screenW: Int, screenH: Int) {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride   = planes[0].rowStride
        val rowPadding  = rowStride - pixelStride * screenW

        val bmp = Bitmap.createBitmap(
            screenW + rowPadding / pixelStride,
            screenH,
            Bitmap.Config.ARGB_8888
        )
        bmp.copyPixelsFromBuffer(buffer)
        image.close()

        // Küçük boyutta analiz (performans)
        val sw = (screenW  * scanScale).toInt()
        val sh = (screenH  * scanScale).toInt()
        val small = Bitmap.createScaledBitmap(bmp, sw, sh, false)
        bmp.recycle()

        bgHandler.post {
            val boxes = analyze(small, screenW, screenH, sw, sh)
            small.recycle()
            onBoxesReady(boxes)
        }
    }

    fun destroy() {
        bgThread.quitSafely()
    }

    // ── Analiz ────────────────────────────────────────────────────────

    private fun analyze(
        bmp: Bitmap,
        realW: Int, realH: Int,
        sw: Int, sh: Int
    ): List<EspOverlayView.EspBox> {
        val scaleX = realW.toFloat() / sw
        val scaleY = realH.toFloat() / sh

        val result = mutableListOf<EspOverlayView.EspBox>()
        val hsv = FloatArray(3)

        targets.forEach { target ->
            // Eşleşen piksel koordinatlarını topla
            val matched = mutableListOf<Pair<Int,Int>>()

            for (y in 0 until sh) {
                for (x in 0 until sw) {
                    val pixel = bmp.getPixel(x, y)
                    Color.colorToHSV(pixel, hsv)
                    if (matchesTarget(hsv, target)) {
                        matched.add(x to y)
                    }
                }
            }

            if (matched.size < minClusterPixels) return@forEach

            // Basit bounding-box cluster (uzak pikselleri ayır)
            clusterize(matched, clusterDist = 20).forEach { cluster ->
                if (cluster.size < minClusterPixels) return@forEach
                val minX = cluster.minOf { it.first }  * scaleX
                val maxX = cluster.maxOf { it.first }  * scaleX
                val minY = cluster.minOf { it.second } * scaleY
                val maxY = cluster.maxOf { it.second } * scaleY

                result.add(
                    EspOverlayView.EspBox(
                        rect  = RectF(minX, minY, maxX, maxY),
                        label = target.name,
                        color = target.boxColor
                    )
                )
            }
        }
        return result
    }

    private fun matchesTarget(hsv: FloatArray, t: ColorTarget): Boolean {
        val (h, s, v) = Triple(hsv[0], hsv[1], hsv[2])
        val hOk = if (t.hWrap) {
            (h >= t.hsvMin[0]) || (h <= 15f)   // kırmızı wrap
        } else {
            h in t.hsvMin[0]..t.hsvMax[0]
        }
        return hOk
            && s in t.hsvMin[1]..t.hsvMax[1]
            && v in t.hsvMin[2]..t.hsvMax[2]
    }

    /** Koordinat listesini mesafeye göre kümelere ayır (greedy) */
    private fun clusterize(
        points: List<Pair<Int,Int>>,
        clusterDist: Int
    ): List<List<Pair<Int,Int>>> {
        val clusters = mutableListOf<MutableList<Pair<Int,Int>>>()
        val visited  = BooleanArray(points.size)

        for (i in points.indices) {
            if (visited[i]) continue
            val cluster = mutableListOf(points[i])
            visited[i] = true
            for (j in i + 1 until points.size) {
                if (visited[j]) continue
                val dx = points[i].first  - points[j].first
                val dy = points[i].second - points[j].second
                if (dx * dx + dy * dy <= clusterDist * clusterDist) {
                    cluster.add(points[j])
                    visited[j] = true
                }
            }
            clusters.add(cluster)
        }
        return clusters
    }
}
