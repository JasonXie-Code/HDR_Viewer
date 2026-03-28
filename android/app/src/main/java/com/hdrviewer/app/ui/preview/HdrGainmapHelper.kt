package com.hdrviewer.app.ui.preview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Gainmap
import android.graphics.Paint

/**
 * 为 SDR [Bitmap] 附加 **合成 Gainmap**（1×1 均匀），供硬件 Canvas 在 `COLOR_MODE_HDR` 下扩展亮度。
 * 预览滑块会写入合成 Gainmap（含原 Ultra HDR 片源，以便 0～200% 均有可见调节）。
 *
 * 始终按 [HEADROOM_CAP]（Gainmap API 上限 6×）请求 headroom：
 * 系统 API 报告的 peakLuminanceNits 通常为全屏持续亮度，远低于面板局部峰值，
 * 因此不以 API 值限制 ratioMax，让面板自行截断。
 */
object HdrGainmapHelper {

    private const val RATIO_FLOOR = 0.05f

    /** Gainmap / setDesiredHdrHeadroom API 上限。 */
    const val HEADROOM_CAP = 6f

    /**
     * 可选饱和度：100% 时 [ColorMatrix.setSaturation] 参数 **1.0**（不变），200% 时 **1.14**（约 +14% 饱和度），中间线性。
     * 略偏保守，减轻肤色与高光过艳；可按产品再调 [SATURATION_MAX_AT_200].
     */
    private const val SATURATION_MAX_AT_200 = 1.14f

    private val sharedContents: Bitmap by lazy {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFFFFFFFF.toInt())
        }
    }

    /**
     * 滑块 0～200% → ratioMax。
     *
     * 设计意图：
     * - **0%** → `RATIO_FLOOR`（接近全黑）
     * - **100%** → **1.0**（≈ SDR 原图亮度，不增不减）
     * - **200%** → `HEADROOM_CAP`（面板 HDR 上限）
     *
     * 两段线性插值使 100% 精确落在 1.0。
     */
    fun ratioMaxFromBrightnessPct(brightnessPct: Float): Float {
        val pct = brightnessPct.coerceIn(0f, 200f)
        return if (pct <= 100f) {
            val t = pct / 100f
            RATIO_FLOOR + (1f - RATIO_FLOOR) * t
        } else {
            val t = (pct - 100f) / 100f
            1f + (HEADROOM_CAP - 1f) * t
        }
    }

    /**
     * 原地更新 [bitmap] 的 Gainmap 元数据，不拷贝像素。
     * 要求 [bitmap] 已是 mutable。
     * [isOriginalUltraHdr] 仅作语义保留，预览路径一律按 [brightnessPct] 写入 Gainmap。
     */
    fun applyGainmapInPlace(
        bitmap: Bitmap,
        brightnessPct: Float,
        @Suppress("UNUSED_PARAMETER") isOriginalUltraHdr: Boolean,
    ) {
        /** 原「自带 Ultra HDR 则跳过」会导致预览滑块对整图无效果；预览亮度以用户滑块为准，统一写入合成 Gainmap。 */
        val rMax = ratioMaxFromBrightnessPct(brightnessPct)

        val gainmap = Gainmap(sharedContents)
        gainmap.setRatioMin(RATIO_FLOOR, RATIO_FLOOR, RATIO_FLOOR)
        gainmap.setRatioMax(rMax, rMax, rMax)
        gainmap.setGamma(1f, 1f, 1f)
        gainmap.setDisplayRatioForFullHdr(HEADROOM_CAP)
        gainmap.setMinDisplayRatioForHdrTransition(1f)
        gainmap.setEpsilonSdr(1f / 128f, 1f / 128f, 1f / 128f)
        gainmap.setEpsilonHdr(1f / 128f, 1f / 128f, 1f / 128f)

        bitmap.setGainmap(gainmap)
    }

    /**
     * 视图层饱和度倍率：避免拖动滑块时反复整图拷贝。
     */
    fun saturationScaleForBrightnessPct(
        brightnessPct: Float,
        enabled: Boolean,
    ): Float {
        if (!enabled || brightnessPct <= 100f) return 1f
        val t = ((brightnessPct - 100f) / 100f).coerceIn(0f, 1f)
        return 1f + (SATURATION_MAX_AT_200 - 1f) * t
    }

    /**
     * 在 [applyGainmapInPlace] 之后对 **像素** 做可选饱和度增强（仅静态图 / GIF 路径调用；视频不走此路径）。
     * 亮度 ≤100% 或无开关时不修改像素。
     */
    fun applySaturationBoostInPlace(
        bitmap: Bitmap,
        brightnessPct: Float,
        enabled: Boolean,
    ) {
        val sat = saturationScaleForBrightnessPct(brightnessPct, enabled)
        if (sat <= 1f) return
        val cm = ColorMatrix()
        cm.setSaturation(sat)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        val cfg = bitmap.config ?: Bitmap.Config.ARGB_8888
        val temp = bitmap.copy(cfg, false)
        val canvas = Canvas(bitmap)
        canvas.drawBitmap(temp, 0f, 0f, paint)
        temp.recycle()
    }

    /**
     * 将 Coil/系统返回的 Bitmap 转为 mutable ARGB_8888 拷贝。
     * 仅在首次加载时调用一次，后续亮度变化不再拷贝像素。
     */
    fun toMutableArgb(input: Bitmap): Bitmap {
        val cfg = when (input.config) {
            null, Bitmap.Config.HARDWARE -> Bitmap.Config.ARGB_8888
            else -> input.config!!
        }
        return input.copy(cfg, true)
    }
}
