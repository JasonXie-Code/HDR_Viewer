package com.hdrviewer.app.data

import android.net.Uri

/**
 * @param dateModifiedSec [MediaStore] 的秒级时间戳（部分列为秒、部分为毫秒，查询时统一为秒）
 */
data class GalleryMedia(
    val id: Long,
    val uri: Uri,
    val displayName: String?,
    val isVideo: Boolean,
    val mimeType: String?,
    val dateModifiedSec: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
) {
    /** 跨图/视频表唯一键（两表 _ID 可能重复） */
    val storeKey: String get() = if (isVideo) "v_$id" else "i_$id"

    /** MediaStore 中 GIF 为图片行，mime 为 `image/gif` */
    val isAnimatedGif: Boolean
        get() = !isVideo && mimeType.equals("image/gif", ignoreCase = true)
}
