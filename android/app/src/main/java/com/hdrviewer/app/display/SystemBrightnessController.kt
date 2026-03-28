package com.hdrviewer.app.display

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import kotlin.math.roundToInt

/**
 * 系统亮度快照与恢复（需 [Settings.System.canWrite]）。
 * 用于预览页退出后恢复用户原先的亮度与手动/自动模式。
 */
class SystemBrightnessSnapshot {
    var brightness: Int? = null
        private set
    var mode: Int? = null
        private set

    /** 每次进入前台前调用：覆盖快照为当前系统值，便于退出时恢复。 */
    fun captureFresh(cr: ContentResolver) {
        brightness = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
        mode = Settings.System.getInt(
            cr,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        )
    }

    internal fun debugSnapshotFields(): String = "snap_b=$brightness snap_m=$mode"

    fun restore(context: Context) {
        val cr = context.contentResolver
        val b = brightness ?: return
        val m = mode ?: return
        if (!Settings.System.canWrite(context)) return
        runCatching {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, b)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, m)
        }
    }

    companion object {
        fun applyManualLevel(context: Context, level: Int) {
            if (!Settings.System.canWrite(context)) return
            val cr = context.contentResolver
            val lv = level.coerceIn(0, 255)
            runCatching {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, lv)
            }
        }

        /** 滑块 0..0.5 段：线性映射到系统亮度 0..255 */
        fun levelFromFirstSegment(normalized: Float): Int =
            (normalized.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
    }
}
