package com.hdrviewer.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.hdrviewer.app.findActivity
import kotlin.math.abs

/**
 * 全屏预览内请求 [android.view.Window.setDesiredHdrHeadroom]（设备推导上限），
 * 配合 [com.hdrviewer.app.ui.preview.HdrGainmapHelper] 与 `COLOR_MODE_HDR`。
 *
 * [desiredHeadroom] 为 **设备** [com.hdrviewer.app.display.DisplayCapabilityInfo.maxHeadroomRatio]（裁剪至约 1.01～6），
 * 会话级固定，与滑块解耦；滑块仅改变 Gainmap 的 ratio。
 */
@Composable
fun HdrBoostEffect(
    enabled: Boolean,
    desiredHeadroom: Float,
) {
    val activity = LocalContext.current.findActivity() ?: return
    val window = activity.window

    val hr = desiredHeadroom.coerceIn(1.01f, 6f)

    DisposableEffect(enabled) {
        if (!enabled) {
            onDispose { }
        } else {
            val prevHeadroom = window.desiredHdrHeadroom
            onDispose {
                window.setDesiredHdrHeadroom(prevHeadroom)
            }
        }
    }

    val lastWrittenHr = remember { FloatArray(1) { Float.NaN } }
    SideEffect {
        if (!enabled) {
            return@SideEffect
        }
        val prev = lastWrittenHr[0]
        if (prev.isNaN() || abs(hr - prev) > 0.002f) {
            window.setDesiredHdrHeadroom(hr)
            lastWrittenHr[0] = hr
        }
    }
}
