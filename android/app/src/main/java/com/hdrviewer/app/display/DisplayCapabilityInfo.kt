package com.hdrviewer.app.display

import android.app.Activity
import android.util.Log
import com.hdrviewer.app.BuildConfig

/**
 * 当前屏幕的 HDR 亮度能力快照（来自 [android.view.Display.getHdrCapabilities]）。
 *
 * [maxHeadroomRatio] 为 **峰值 nit / 假定 SDR 全屏白 nit**（非 API 的 peak/avg 比），
 * 用于 [android.view.Window.setDesiredHdrHeadroom] 与静态图 Gainmap 的 `ratioMax` 上限。
 */
data class DisplayCapabilityInfo(
    /** 面板瞬时峰值亮度（nit），通常仅小面积 / 短时间可达。 */
    val peakLuminanceNits: Float,
    /** 面板全屏持续最大均值亮度（nit）（API 语义；可能与峰值同报）。 */
    val maxAverageLuminanceNits: Float,
    /** 面板最暗黑电平（nit）。 */
    val minLuminanceNits: Float,
    /** 支持的 HDR 传输类型（HDR10 / HLG / Dolby Vision …）。 */
    val supportedHdrTypes: IntArray,
    /**
     * **设备 HDR 扩展比上限**：`peakLuminanceNits / assumedSdrWhiteNits`，裁剪至 [1, 6]。
     * 与日常 SDR 白（约 500–800 nit）对比，而非与 [maxAverageLuminanceNits] 简单相除。
     */
    val maxHeadroomRatio: Float,
    /** API 原始 peak/avg 比（诊断用，raw≤1 时 API 未区分小面积峰值与全屏均值）。 */
    val peakToAvgRatioRaw: Float,
) {
    val isHdrCapable: Boolean get() = supportedHdrTypes.isNotEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DisplayCapabilityInfo) return false
        return peakLuminanceNits == other.peakLuminanceNits &&
            maxAverageLuminanceNits == other.maxAverageLuminanceNits &&
            minLuminanceNits == other.minLuminanceNits &&
            supportedHdrTypes.contentEquals(other.supportedHdrTypes) &&
            maxHeadroomRatio == other.maxHeadroomRatio &&
            peakToAvgRatioRaw == other.peakToAvgRatioRaw
    }

    override fun hashCode(): Int {
        var result = peakLuminanceNits.hashCode()
        result = 31 * result + maxAverageLuminanceNits.hashCode()
        result = 31 * result + minLuminanceNits.hashCode()
        result = 31 * result + supportedHdrTypes.contentHashCode()
        result = 31 * result + maxHeadroomRatio.hashCode()
        result = 31 * result + peakToAvgRatioRaw.hashCode()
        return result
    }

    companion object {
        private const val TAG = "HDRV_Display"
        private const val DEFAULT_PEAK_NITS = 1000f
        private const val DEFAULT_AVG_NITS = 500f
        /** 假定 SDR 全屏白（nit），用于 peak/假定白 → headroom；非仪器测量，可产品调参。 */
        const val DEFAULT_SDR_WHITE_NITS = 800f
        private const val HEADROOM_CAP = 6.0f

        fun detect(activity: Activity): DisplayCapabilityInfo {
            val display = activity.display
            if (display == null) {
                if (BuildConfig.DEBUG) Log.w(TAG, "display==null, using fallback")
                return fallback()
            }
            val caps = display.hdrCapabilities
            if (caps == null) {
                if (BuildConfig.DEBUG) Log.w(TAG, "hdrCapabilities==null, using fallback")
                return fallback()
            }

            val peak = caps.desiredMaxLuminance.takeIf { it > 0f } ?: DEFAULT_PEAK_NITS
            val avg = caps.desiredMaxAverageLuminance.takeIf { it > 0f } ?: DEFAULT_AVG_NITS
            val minL = caps.desiredMinLuminance.coerceAtLeast(0f)
            val types = caps.supportedHdrTypes ?: intArrayOf()

            val rawRatio = if (avg > 0f) peak / avg else 1f
            val maxHr = (peak / DEFAULT_SDR_WHITE_NITS).coerceIn(1f, HEADROOM_CAP)

            if (rawRatio <= 1.01f && BuildConfig.DEBUG) {
                Log.w(
                    TAG,
                    "peak≈avg (rawRatio=%.4f): using maxHeadroomRatio=peak/assumed_sdr=%.2f (assumed_sdr=%.0f nit)"
                        .format(rawRatio, maxHr, DEFAULT_SDR_WHITE_NITS),
                )
            }

            val info = DisplayCapabilityInfo(
                peakLuminanceNits = peak,
                maxAverageLuminanceNits = avg,
                minLuminanceNits = minL,
                supportedHdrTypes = types,
                maxHeadroomRatio = maxHr,
                peakToAvgRatioRaw = rawRatio,
            )

            Log.d(
                TAG,
                "display_capability peak=%.0f avg=%.0f min=%.3f max_hr=%.2f assumed_sdr=%.0f raw_peak_avg=%.4f hdr_types=%s"
                    .format(peak, avg, minL, maxHr, DEFAULT_SDR_WHITE_NITS, rawRatio, types.joinToString(",")),
            )
            return info
        }

        fun fallback() = DisplayCapabilityInfo(
            peakLuminanceNits = DEFAULT_PEAK_NITS,
            maxAverageLuminanceNits = DEFAULT_AVG_NITS,
            minLuminanceNits = 0f,
            supportedHdrTypes = intArrayOf(),
            maxHeadroomRatio = (DEFAULT_PEAK_NITS / DEFAULT_SDR_WHITE_NITS).coerceIn(1f, HEADROOM_CAP),
            peakToAvgRatioRaw = DEFAULT_PEAK_NITS / DEFAULT_AVG_NITS,
        )
    }
}
