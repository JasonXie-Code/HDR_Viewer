package com.hdrviewer.app.data

import android.net.Uri

/**
 * [bucketId] 对应 [MediaStore.MediaColumns.BUCKET_ID]；「全部」在 UI 层用 null 表示，不构造本类。
 */
data class MediaAlbum(
    val bucketId: String,
    val displayName: String,
    /** 相册内最新一条媒体的封面；可为 null（空相册）。 */
    val coverUri: Uri? = null,
    val itemCount: Int = 0,
)
