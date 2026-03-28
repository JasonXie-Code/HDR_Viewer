package com.hdrviewer.app.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay

/**
 * 循环播放已带 Gainmap 的帧序列（亮度需在组合前已对每帧调用 [HdrGainmapHelper.applyGainmapInPlace]）。
 */
@Composable
fun GifHdrAnimatedImage(
    frames: List<GifHdrFrame>,
    brightnessKey: Int,
    effectiveBrightnessPct: Float,
    isOriginalUltraHdr: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    if (frames.isEmpty()) return

    /** 动图多帧反复原地叠饱和度会累积失真；此处仅 Gainmap，饱和度增强仅静态图。 */
    remember(brightnessKey, frames) {
        frames.forEach {
            HdrGainmapHelper.applyGainmapInPlace(
                it.bitmap,
                effectiveBrightnessPct,
                isOriginalUltraHdr,
            )
        }
    }

    var frameIndex by remember(frames) { mutableIntStateOf(0) }

    LaunchedEffect(frames) {
        frameIndex = 0
        var idx = 0
        while (true) {
            delay(frames[idx].durationMs.coerceAtLeast(1L))
            idx = (idx + 1) % frames.size
            frameIndex = idx
        }
    }

    HdrBitmapImage(
        bitmap = frames[frameIndex].bitmap,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
    )
}
