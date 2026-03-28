package com.hdrviewer.app.display

import android.app.Activity
import android.provider.Settings
import com.hdrviewer.app.BuildConfig

/**
 * 全屏预览拉满系统亮度条与恢复的**单一事实来源**。
 *
 * 仅依赖 Compose 生命周期时，部分机型/路径下 [androidx.lifecycle.Lifecycle.Event.ON_PAUSE] 或
 * [androidx.compose.runtime.DisposableEffect] 的 [onDispose] 与 Activity 暂停顺序不一致，可能导致漏恢复。
 * [Activity.onPause] 中再调用 [restoreIfBoosted] 作为兜底。
 */
object SystemBrightnessSession {

    private val snapshot = SystemBrightnessSnapshot()

    @Volatile
    private var boosted: Boolean = false

    /** 当前是否仍保持「预览拉满」状态（供 [restoreIfBoosted] 判断）。 */
    fun isBoosted(): Boolean = boosted

    /**
     * 记录当前系统亮度与模式，并在已授权时拉至 255；成功写入后 [boosted] 为 true。
     *
     * **已 [boosted] 时不再 [SystemBrightnessSnapshot.captureFresh]**：注册 [androidx.lifecycle.LifecycleObserver]
     * 后，若 Activity 已在 RESUMED，会**立刻**再派发 [androidx.lifecycle.Lifecycle.Event.ON_RESUME]，
     * 此时系统亮度已是 255，若再次 capture 会**覆盖**快照，导致 [restoreIfBoosted] 写回 255 而非用户原值。
     */
    fun captureAndBoost(activity: Activity) {
        SystemBrightnessDebugLog.logState(activity, "captureAndBoost_before", snapshot)
        if (!boosted) {
            snapshot.captureFresh(activity.contentResolver)
        } else if (BuildConfig.DEBUG) {
            SystemBrightnessDebugLog.logState(
                activity,
                "captureAndBoost_skip_captureFresh_already_boosted",
                snapshot,
            )
        }
        SystemBrightnessDebugLog.logState(activity, "captureAndBoost_after_capture", snapshot)
        if (!Settings.System.canWrite(activity)) {
            SystemBrightnessDebugLog.logState(activity, "captureAndBoost_skip_no_canWrite", snapshot)
            return
        }
        SystemBrightnessSnapshot.applyManualLevel(activity, 255)
        boosted = true
        SystemBrightnessDebugLog.logState(activity, "captureAndBoost_after_apply255", snapshot)
    }

    /**
     * 若曾拉满过系统亮度，则恢复快照并清除 [boosted]；可安全多次调用（幂等）。
     */
    fun restoreIfBoosted(activity: Activity) {
        SystemBrightnessDebugLog.logState(activity, "restoreIfBoosted_enter", snapshot)
        if (!boosted) {
            SystemBrightnessDebugLog.logState(activity, "restoreIfBoosted_noop_not_boosted", snapshot)
            return
        }
        snapshot.restore(activity)
        boosted = false
        SystemBrightnessDebugLog.logState(activity, "restoreIfBoosted_after_restore", snapshot)
    }
}
