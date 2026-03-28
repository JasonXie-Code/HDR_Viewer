package com.hdrviewer.app.debug

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.Window
import com.hdrviewer.app.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 全屏预览区 **实际合成到窗口后的像素**（`PixelCopy`）的线性亮度（Rec.709 luma）直方图，写入 logcat，
 * 供 `misc/brightness_monitor.py` 解析。过滤示例：`adb logcat -s HDRV_Histogram:D`
 *
 * 说明：包含 **AGSL / HDR 扩展** 与 **ContentScale.Fit** 后的 letterbox 区域（与肉眼所见一致）。
 *
 * 触发时机（当前工程）：**亮度滑块数值连续不变满 1 秒**后，对当前落定页采一次（见 [com.hdrviewer.app.ui.ViewerScreen]）。
 */
object PreviewHistogramLog {
    const val TAG = "HDRV_Histogram"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val copyInFlight = AtomicBoolean(false)

    /**
     * 从 [window] 中截取 [rect]（窗口坐标系）的像素并统计直方图。
     * 在 **主线程** 发起 [PixelCopy]，统计在后台线程执行。
     *
     * @param brightnessPct 当前 **0～200%** 会话/有效亮度，写入 JSON 便于与 `tone_chain` 对照；可为 null。
     */
    fun logFromWindowPixelCopy(
        window: Window,
        rect: Rect,
        storeKey: String,
        brightnessPct: Float? = null,
    ) {
        if (!BuildConfig.DEBUG) return
        if (rect.width() <= 0 || rect.height() <= 0) return
        if (!copyInFlight.compareAndSet(false, true)) return

        val safeRect = clipRectToWindow(window, rect) ?: run {
            copyInFlight.set(false)
            return
        }

        fun runCopy(r: Rect, allowRetry: Boolean) {
            val rw = r.width()
            val rh = r.height()
            val dest =
                try {
                    Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
                } catch (_: OutOfMemoryError) {
                    if (allowRetry) {
                        val shrunk = shrinkRectUniform(r, maxSide = 480)
                        val smaller = shrunk?.let { clipRectToWindow(window, it) }
                        if (smaller != null &&
                            (smaller.width() != r.width() || smaller.height() != r.height())
                        ) {
                            runCopy(smaller, allowRetry = false)
                            return
                        }
                    }
                    Log.w(TAG, "histogram_json_error=oom_create_bitmap")
                    copyInFlight.set(false)
                    return
                }

            val handler = Handler(Looper.getMainLooper())
            PixelCopy.request(window, r, dest, { result ->
                if (result != PixelCopy.SUCCESS) {
                    dest.recycle()
                    Log.w(TAG, "histogram_json_error=pixelcopy_$result")
                    copyInFlight.set(false)
                    return@request
                }
                scope.launch {
                    try {
                        val json =
                            computeHistogramJson(
                                dest,
                                storeKey,
                                schemaVersion = 2,
                                captureW = rw,
                                captureH = rh,
                                note = "display_window_pixelcopy",
                                brightnessPct = brightnessPct,
                            )
                        Log.d(TAG, "histogram_json=$json")
                    } catch (e: Exception) {
                        Log.w(TAG, "histogram_json_error=${e.message}")
                    } finally {
                        dest.recycle()
                        copyInFlight.set(false)
                    }
                }
            }, handler)
        }

        runCopy(safeRect, allowRetry = true)
    }

    private fun clipRectToWindow(window: Window, rect: Rect): Rect? {
        val dv = window.decorView
        val dw = dv.width
        val dh = dv.height
        if (dw <= 0 || dh <= 0) return null
        val left = rect.left.coerceIn(0, dw - 1)
        val top = rect.top.coerceIn(0, dh - 1)
        val right = rect.right.coerceIn(left + 1, dw)
        val bottom = rect.bottom.coerceIn(top + 1, dh)
        if (right <= left || bottom <= top) return null
        return Rect(left, top, right, bottom)
    }

    /** 长边不超过 [maxSide]，同比缩小并相对原 [rect] 居中，用于 PixelCopy 降采样、减轻 OOM。 */
    private fun shrinkRectUniform(rect: Rect, maxSide: Int): Rect? {
        val rw = rect.width()
        val rh = rect.height()
        if (rw <= 0 || rh <= 0) return null
        val scale = minOf(1f, maxSide.toFloat() / maxOf(rw, rh))
        if (scale >= 1f) return Rect(rect)
        val nw = maxOf((rw * scale).toInt(), 1)
        val nh = maxOf((rh * scale).toInt(), 1)
        val left = rect.left + (rw - nw) / 2
        val top = rect.top + (rh - nh) / 2
        return Rect(left, top, left + nw, top + nh)
    }

    private fun computeHistogramJson(
        bitmap: Bitmap,
        storeKey: String,
        schemaVersion: Int,
        captureW: Int,
        captureH: Int,
        note: String,
        brightnessPct: Float? = null,
    ): String {
        val w0 = bitmap.width
        val h0 = bitmap.height
        val maxDim = 512
        val scale = minOf(maxDim.toFloat() / maxOf(w0, h0), 1f)
        val sw = maxOf((w0 * scale).toInt(), 1)
        val sh = maxOf((h0 * scale).toInt(), 1)
        val work =
            if (sw == w0 && sh == h0) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, sw, sh, true)
            }
        try {
            val w = work.width
            val h = work.height
            val pixels = IntArray(w * h)
            work.getPixels(pixels, 0, w, 0, 0, w, h)
            val bins = IntArray(64)
            var n = 0
            var clipHigh = 0
            for (p in pixels) {
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val y = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)
                if (y >= 250) clipHigh++
                val bin = (y * 64) shr 8
                bins[bin]++
                n++
            }
            val ja = JSONArray()
            for (b in bins) {
                ja.put(b)
            }
            val o = JSONObject()
            o.put("v", schemaVersion)
            o.put("key", storeKey)
            o.put("w", captureW)
            o.put("h", captureH)
            o.put("sw", w)
            o.put("sh", h)
            o.put("bins", ja)
            o.put("n", n)
            o.put("clip_frac", if (n > 0) clipHigh.toDouble() / n else 0.0)
            o.put("note", note)
            if (brightnessPct != null) {
                o.put("brightness_pct", brightnessPct.toDouble())
            }
            return o.toString()
        } finally {
            if (work !== bitmap) {
                work.recycle()
            }
        }
    }
}
