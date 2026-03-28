package com.hdrviewer.app

/**
 * 全屏预览 **0～200%** 会话亮度写入 logcat，供 `misc/brightness_monitor.py` 解析。
 * 过滤示例：`adb logcat -s HDRV_Brightness:D`
 *
 * 另见 [com.hdrviewer.app.debug.PreviewHistogramLog]：有效亮度稳定 **1s** 后 **`PixelCopy`** 直方图 tag **`HDRV_Histogram`**（仅 debug 构建）。
 *
 * 分段 / 边界调试：`HDRV_Segment`（**debug**），用于分析 **99%～101%** 等中点附近背光与 headroom 变化。过滤：`adb logcat -s HDRV_Segment:D`
 *
 * 全屏预览性能与第二段 tone：`HDRV_Viewer`（**debug**），含 `gainmap_chain`、`pager_settled` 等。过滤：`adb logcat -s HDRV_Viewer:D`
 *
 * 系统亮度条快照/恢复链路（**debug**）：`HDRV_SysBrightness`，含 `MainActivity` 生命周期、`ViewerPolicy_*`、`captureAndBoost_*`、`restoreIfBoosted_*`。过滤：`adb logcat -s HDRV_SysBrightness:D`
 */
internal const val HDRV_BRIGHTNESS_LOG_TAG = "HDRV_Brightness"

/** 两段亮度分界、headroom、系统亮度等（仅 [BuildConfig.DEBUG] 输出）。 */
internal const val HDRV_SEGMENT_LOG_TAG = "HDRV_Segment"

/** Pager / 第二段 AGSL / 曝光乘数调试（仅 [BuildConfig.DEBUG] 输出）。 */
internal const val HDRV_VIEWER_TAG = "HDRV_Viewer"
