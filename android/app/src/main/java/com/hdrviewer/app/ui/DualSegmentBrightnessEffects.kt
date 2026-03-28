package com.hdrviewer.app.ui

import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.hdrviewer.app.BuildConfig
import com.hdrviewer.app.HDRV_SEGMENT_LOG_TAG
import com.hdrviewer.app.findActivity
import com.hdrviewer.app.ui.preview.HdrGainmapHelper

/**
 * 全屏预览窗口效果（应用启动时 [com.hdrviewer.app.MainActivity] 已将窗口设为 **`COLOR_MODE_HDR`**）：
 * - **常亮** [FLAG_KEEP_SCREEN_ON]
 * - **HDR headroom**：固定请求 [HdrGainmapHelper.HEADROOM_CAP]（API 上限 6×），
 *   让面板按物理能力自行截断，不受系统 API peakLuminanceNits 低估影响。
 */
@Composable
fun DualSegmentBrightnessEffects(keepScreenOn: Boolean) {
    val activity = LocalContext.current.findActivity()
    val window = activity?.window

    LaunchedEffect(Unit) {
        if (BuildConfig.DEBUG) {
            Log.d(HDRV_SEGMENT_LOG_TAG, "viewer_brightness headroom=${HdrGainmapHelper.HEADROOM_CAP} (fixed cap)")
        }
    }

    DisposableEffect(window, keepScreenOn) {
        if (window == null) {
            return@DisposableEffect onDispose { }
        }
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (keepScreenOn) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    HdrBoostEffect(
        enabled = true,
        desiredHeadroom = HdrGainmapHelper.HEADROOM_CAP,
    )
}
