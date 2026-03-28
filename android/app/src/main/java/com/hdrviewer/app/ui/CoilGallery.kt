package com.hdrviewer.app.ui

import android.content.Context
import coil.decode.BitmapFactoryDecoder
import coil.request.ImageRequest
import com.hdrviewer.app.data.GalleryMedia

/**
 * 相册网格缩略图：GIF 仅解码首帧，避免一屏多格同时跑动画。
 */
fun ImageRequest.Builder.galleryGridThumbnail(item: GalleryMedia): ImageRequest.Builder = apply {
    if (item.isAnimatedGif) {
        decoderFactory(BitmapFactoryDecoder.Factory())
    }
}

fun galleryGridImageRequest(context: Context, item: GalleryMedia): ImageRequest {
    return ImageRequest.Builder(context)
        .data(item.uri)
        .galleryGridThumbnail(item)
        .crossfade(true)
        .size(512)
        .build()
}
