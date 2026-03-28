package com.hdrviewer.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder

/**
 * 注册 Coil [ImageLoader]，启用 GIF 动图解码（全屏为动画；相册网格缩略图在 [com.hdrviewer.app.ui.galleryGridImageRequest] 中请求首帧）。
 */
class HdrViewerApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(ImageDecoderDecoder.Factory())
            }
            .build()
    }
}
