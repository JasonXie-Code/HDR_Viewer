package com.hdrviewer.app.display

/**
 * 将界面上的「目标 nit」线性映射到窗口亮度系数 [0,1]（[android.view.WindowManager.LayoutParams.screenBrightness]）。
 * 用于 **SDR 内容尽量提亮**（户外可读意图），数值与真实 cd/m² 仅为估算，强光下额外亮度多由系统与环境光策略决定。
 */
object BrightnessMapper {
    fun nitToScreenBrightness(
        nit: Float,
        nitRangeMin: Float,
        nitRangeMax: Float,
    ): Float {
        if (nitRangeMax <= nitRangeMin) return 1f
        return ((nit - nitRangeMin) / (nitRangeMax - nitRangeMin)).coerceIn(0f, 1f)
    }
}
