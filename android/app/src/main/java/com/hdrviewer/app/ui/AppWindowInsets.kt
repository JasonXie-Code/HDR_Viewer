package com.hdrviewer.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable

/**
 * 沉浸式（系统状态栏隐藏）下各页 [androidx.compose.material3.TopAppBar] 统一使用的窗口边衬。
 *
 * 仅取 [safeDrawing] 的顶边与左右：在 status bar 不可见时仍能反映刘海/挖孔与横向安全区，避免默认仅依赖
 * [androidx.compose.foundation.layout.statusBars] 时顶部 inset 为 0 而闯入摄像头区域；与手写
 * statusBars ∪ displayCutout 相比，列表与全屏预览顶栏边距一致，减少重复或遗漏。
 */
@Composable
fun appTopBarWindowInsets(): WindowInsets =
    WindowInsets.safeDrawing.only(
        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
    )
