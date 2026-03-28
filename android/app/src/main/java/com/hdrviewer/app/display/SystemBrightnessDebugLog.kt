package com.hdrviewer.app.display

import android.app.Activity
import android.provider.Settings
import android.util.Log
import com.hdrviewer.app.BuildConfig

/**
 * 仅 [BuildConfig.DEBUG]：记录系统亮度条与 [SystemBrightnessSession] 状态，便于排查「退出未还原」。
 *
 * 过滤：`adb logcat -s HDRV_SysBrightness:D`
 */
internal object SystemBrightnessDebugLog {

    const val TAG = "HDRV_SysBrightness"

    fun logState(
        activity: Activity,
        where: String,
        snapshot: SystemBrightnessSnapshot? = null,
    ) {
        if (!BuildConfig.DEBUG) return
        val cr = activity.contentResolver
        val curB = runCatching {
            Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, -1)
        }.getOrElse { -1 }
        val curMode = runCatching {
            Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, -1)
        }.getOrElse { -1 }
        val canWrite = Settings.System.canWrite(activity)
        val boosted = SystemBrightnessSession.isBoosted()
        val snapPart = snapshot?.debugSnapshotFields().orEmpty().let { if (it.isNotEmpty()) " $it" else "" }
        Log.d(
            TAG,
            "[$where] sys_b=$curB sys_mode=$curMode canWrite=$canWrite boosted=$boosted$snapPart",
        )
    }
}
