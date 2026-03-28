package com.hdrviewer.app.ui

import android.content.pm.ActivityInfo
import com.hdrviewer.app.BuildConfig
import com.hdrviewer.app.display.SystemBrightnessDebugLog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hdrviewer.app.display.SystemBrightnessSession
import com.hdrviewer.app.findActivity

/**
 * **仅全屏预览**时启用：
 * - 窗口 [android.view.Window.setColorMode] 为 [ActivityInfo.COLOR_MODE_HDR]；
 * - 在已授权 [android.provider.Settings.System.canWrite] 时，将**系统亮度条**拉至最高（与 [SystemBrightnessSession] 成对恢复）。
 *
 * 离开全屏或应用进入后台时恢复颜色模式与系统亮度（见 [DisposableEffect] / 生命周期）；
 * [com.hdrviewer.app.MainActivity.onPause] 中亦调用 [SystemBrightnessSession.restoreIfBoosted] 兜底。
 */
@Composable
fun ViewerBrightnessPolicyEffects(isViewerRoute: Boolean) {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return
    val window = activity.window
    val lifecycleOwner = LocalLifecycleOwner.current

    SideEffect {
        if (isViewerRoute) {
            window.setColorMode(ActivityInfo.COLOR_MODE_HDR)
        } else {
            window.setColorMode(ActivityInfo.COLOR_MODE_DEFAULT)
        }
    }

    if (!isViewerRoute) {
        return
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (BuildConfig.DEBUG) {
                        SystemBrightnessDebugLog.logState(activity, "ViewerPolicy_Lifecycle_ON_RESUME")
                    }
                    SystemBrightnessSession.captureAndBoost(activity)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    if (BuildConfig.DEBUG) {
                        SystemBrightnessDebugLog.logState(activity, "ViewerPolicy_Lifecycle_ON_PAUSE")
                    }
                    SystemBrightnessSession.restoreIfBoosted(activity)
                }
                else -> {}
            }
        }
        if (BuildConfig.DEBUG) {
            SystemBrightnessDebugLog.logState(activity, "ViewerPolicy_DisposableEffect_initial")
        }
        SystemBrightnessSession.captureAndBoost(activity)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            if (BuildConfig.DEBUG) {
                SystemBrightnessDebugLog.logState(activity, "ViewerPolicy_DisposableEffect_onDispose_before_restore")
            }
            lifecycleOwner.lifecycle.removeObserver(observer)
            SystemBrightnessSession.restoreIfBoosted(activity)
        }
    }
}
