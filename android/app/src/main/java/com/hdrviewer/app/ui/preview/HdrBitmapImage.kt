package com.hdrviewer.app.ui.preview

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Bitmap
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun HdrBitmapImage(
    bitmap: Bitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    saturation: Float = 1f,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                adjustViewBounds = false
            }
        },
        update = { view ->
            view.setContentDescription(contentDescription)
            view.scaleType = contentScale.toImageViewScaleType()
            /** Gainmap 在 [Bitmap] 上原地更新时引用不变，必须每次刷新，否则 [ImageView] 不重绘。 */
            view.setImageBitmap(bitmap)
            if (saturation > 1f) {
                val matrix = ColorMatrix().apply { setSaturation(saturation) }
                view.setColorFilter(ColorMatrixColorFilter(matrix))
            } else {
                view.clearColorFilter()
            }
            view.invalidate()
        },
    )
}

private fun ContentScale.toImageViewScaleType(): ImageView.ScaleType = when (this) {
    ContentScale.Crop -> ImageView.ScaleType.CENTER_CROP
    ContentScale.FillBounds -> ImageView.ScaleType.FIT_XY
    ContentScale.Inside -> ImageView.ScaleType.CENTER_INSIDE
    else -> ImageView.ScaleType.FIT_CENTER
}
