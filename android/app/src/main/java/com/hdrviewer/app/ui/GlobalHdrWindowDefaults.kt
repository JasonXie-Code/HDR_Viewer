package com.hdrviewer.app.ui

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.hdrviewer.app.findActivity

/**
 * **非全屏预览** 时把 [android.view.Window.setDesiredHdrHeadroom] 固定为 **1.0**；
 * 窗口 **`COLOR_MODE_*`** 由 [ViewerBrightnessPolicyEffects] 在列表为 DEFAULT、全屏为 HDR。
 *
 * 进入 [ViewerScreen] 后由 [HdrBoostEffect] 接管 headroom，本 composable 应 **不** 再写入（见 [active]）。
 */
@Composable
fun GlobalHdrHeadroomForGallery(active: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    val window = activity.window
    SideEffect {
        if (active) {
            window.setDesiredHdrHeadroom(1f)
        }
    }
}
