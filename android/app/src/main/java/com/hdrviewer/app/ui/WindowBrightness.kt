package com.hdrviewer.app.ui

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import com.hdrviewer.app.display.BrightnessMapper
import com.hdrviewer.app.findActivity

/**
 * 全屏预览时：保持亮屏，并按「目标 nit」映射更新窗口 [screenBrightness]（SDR 路径下尽量拉高内容可读性）；
 * 离开组合件时恢复进入前的亮度。
 */
@Composable
fun WindowBrightnessEffect(
    targetNit: Float,
    nitRange: ClosedFloatingPointRange<Float>,
) {
    val activity = LocalContext.current.findActivity() ?: return
    val window = activity.window

    DisposableEffect(window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val attrs = window.attributes
        val previousBrightness = attrs.screenBrightness
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            attrs.screenBrightness = previousBrightness
            window.attributes = attrs
        }
    }

    SideEffect {
        val attrs = window.attributes
        attrs.screenBrightness = BrightnessMapper.nitToScreenBrightness(
            targetNit,
            nitRange.start,
            nitRange.endInclusive,
        )
        window.attributes = attrs
    }
}
