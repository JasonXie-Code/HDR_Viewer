package com.hdrviewer.app.ui.preview

import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import com.hdrviewer.app.findActivity

/**
 * 将窗口设为 [ActivityInfo.COLOR_MODE_HDR]（**当前工程**在 [com.hdrviewer.app.MainActivity] 启动时全局设置，本 Composable 可不再使用）。
 *
 * 保留供将来按需按界面切换色域时使用。
 */
@Composable
fun UltraHdrWindowColorEffect(enabled: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    val window = activity.window

    DisposableEffect(enabled) {
        if (!enabled) {
            onDispose { }
        } else {
            val prev = window.colorMode
            onDispose {
                window.setColorMode(prev)
            }
        }
    }

    SideEffect {
        if (!enabled) return@SideEffect
        window.setColorMode(ActivityInfo.COLOR_MODE_HDR)
    }
}
