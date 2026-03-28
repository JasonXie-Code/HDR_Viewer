package com.hdrviewer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hdrviewer.app.BuildConfig
import com.hdrviewer.app.display.SystemBrightnessDebugLog
import com.hdrviewer.app.display.SystemBrightnessSession
import com.hdrviewer.app.ui.HdrViewerApp
import com.hdrviewer.app.ui.theme.HdrViewerTheme

class MainActivity : ComponentActivity() {

    private var savedWindowColorMode: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedWindowColorMode = window.colorMode
        /** HDR 窗口模式仅在全屏预览时由 [com.hdrviewer.app.ui.ViewerBrightnessPolicyEffects] 开启；列表为默认模式。 */
        enableEdgeToEdge()
        applyImmersiveSystemBars()
        setContent {
            HdrViewerTheme {
                HdrViewerApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (BuildConfig.DEBUG) {
            SystemBrightnessDebugLog.logState(this, "MainActivity.onResume")
        }
        /** 部分机型从多任务返回后会恢复系统栏显示，需再次隐藏。 */
        applyImmersiveSystemBars()
    }

    override fun onPause() {
        /** 全屏预览拉满系统亮度后，部分机型上仅依赖 Compose 生命周期会漏恢复；此处兜底。 */
        if (BuildConfig.DEBUG) {
            SystemBrightnessDebugLog.logState(this, "MainActivity.onPause_before_restore")
        }
        SystemBrightnessSession.restoreIfBoosted(this)
        if (BuildConfig.DEBUG) {
            SystemBrightnessDebugLog.logState(this, "MainActivity.onPause_after_restore")
        }
        super.onPause()
    }

    override fun onStop() {
        if (BuildConfig.DEBUG) {
            SystemBrightnessDebugLog.logState(this, "MainActivity.onStop")
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG) {
            SystemBrightnessDebugLog.logState(this, "MainActivity.onDestroy")
        }
        savedWindowColorMode?.let { window.setColorMode(it) }
        super.onDestroy()
    }

    /**
     * 全局隐藏系统状态栏与底部导航/手势条；自边缘滑动可短暂唤出（[BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE]）。
     * 各界面通过 [androidx.compose.foundation.layout.WindowInsets] 避让刘海与手势区。
     */
    private fun applyImmersiveSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
