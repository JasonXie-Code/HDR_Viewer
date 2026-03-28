package com.hdrviewer.app.data

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.hdrviewer.app.HDRV_VIEWER_TAG

object MediaRepository {

    /** [bucketId] 为 null 时返回全部图片与视频 */
    fun queryMedia(context: Context, bucketId: String?): List<GalleryMedia> {
        val images = queryImages(context, bucketId)
        val videos = queryVideos(context, bucketId)
        return (images + videos).sortedByDescending { it.dateModifiedSec }
    }

    fun queryAllMedia(context: Context): List<GalleryMedia> = queryMedia(context, bucketId = null)

    /** 扫描图库得到不重复相册（图片 + 视频 bucket 合并） */
    fun listAlbums(context: Context): List<MediaAlbum> {
        val map = LinkedHashMap<String, String>()
        val resolver = context.contentResolver

        fun scanImages() {
            val collection = imageCollectionUri()
            val projection = arrayOf(
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            )
            resolver.query(collection, projection, null, null, null)?.use { cursor ->
                val bidCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val bid = cursor.getString(bidCol) ?: continue
                    val name = cursor.getString(nameCol)?.takeIf { it.isNotBlank() } ?: bid
                    map.putIfAbsent(bid, name)
                }
            }
        }

        fun scanVideos() {
            val collection = videoCollectionUri()
            val projection = arrayOf(
                MediaStore.Video.Media.BUCKET_ID,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            )
            resolver.query(collection, projection, null, null, null)?.use { cursor ->
                val bidCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val bid = cursor.getString(bidCol) ?: continue
                    val name = cursor.getString(nameCol)?.takeIf { it.isNotBlank() } ?: bid
                    map.putIfAbsent(bid, name)
                }
            }
        }

        scanImages()
        scanVideos()
        return map.map { (id, name) ->
            MediaAlbum(
                bucketId = id,
                displayName = name,
                coverUri = coverUriForBucket(context, id),
                itemCount = countMediaInBucket(context, id),
            )
        }.sortedBy { it.displayName.lowercase() }
    }

    private fun countMediaInBucket(context: Context, bucketId: String): Int {
        val ic = countInBucket(context, imageCollectionUri(), MediaStore.Images.Media.BUCKET_ID, bucketId)
        val vc = countInBucket(context, videoCollectionUri(), MediaStore.Video.Media.BUCKET_ID, bucketId)
        return ic + vc
    }

    private fun countInBucket(context: Context, collection: Uri, bucketColumn: String, bucketId: String): Int {
        val resolver = context.contentResolver
        return resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "$bucketColumn = ?",
            arrayOf(bucketId),
            null,
        )?.use { it.count } ?: 0
    }

    private data class NewestInBucket(val uri: Uri, val dateModifiedSec: Long)

    private fun newestInBucket(
        context: Context,
        collection: Uri,
        bucketColumn: String,
        bucketId: String,
        isVideo: Boolean,
    ): NewestInBucket? {
        val resolver = context.contentResolver
        val idColName = if (isVideo) MediaStore.Video.Media._ID else MediaStore.Images.Media._ID
        val dateColName = if (isVideo) MediaStore.Video.Media.DATE_MODIFIED else MediaStore.Images.Media.DATE_MODIFIED
        val projection = arrayOf(idColName, dateColName)
        val sort = "$dateColName DESC"
        resolver.query(
            collection,
            projection,
            "$bucketColumn = ?",
            arrayOf(bucketId),
            sort,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val idCol = cursor.getColumnIndexOrThrow(idColName)
            val dateCol = cursor.getColumnIndexOrThrow(dateColName)
            val id = cursor.getLong(idCol)
            val dateRaw = cursor.getLong(dateCol)
            val dateSec = normalizeDateModifiedSec(dateRaw)
            val uri = ContentUris.withAppendedId(collection, id)
            return NewestInBucket(uri, dateSec)
        }
        return null
    }

    private fun coverUriForBucket(context: Context, bucketId: String): Uri? {
        val img = newestInBucket(
            context, imageCollectionUri(), MediaStore.Images.Media.BUCKET_ID, bucketId, isVideo = false,
        )
        val vid = newestInBucket(
            context, videoCollectionUri(), MediaStore.Video.Media.BUCKET_ID, bucketId, isVideo = true,
        )
        return when {
            img == null -> vid?.uri
            vid == null -> img.uri
            img.dateModifiedSec >= vid.dateModifiedSec -> img.uri
            else -> vid.uri
        }
    }

    /**
     * Android 11+ 对用户相册中**非本应用创建**的条目，直接 [android.content.ContentResolver.delete]
     * 常抛 [SecurityException]；需用系统 [MediaStore.createDeleteRequest] 弹窗由用户确认删除。
     */
    fun createDeleteRequest(context: Context, uris: List<Uri>): PendingIntent? {
        if (uris.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(context.applicationContext.contentResolver, uris)
        } else {
            null
        }
    }

    /** 直接删除（仅适用于仍允许直删的场景）；失败时静默并打日志。 */
    fun deleteUris(context: Context, uris: List<Uri>): Int {
        var n = 0
        val resolver = context.applicationContext.contentResolver
        for (uri in uris) {
            try {
                if (resolver.delete(uri, null, null) > 0) n++
            } catch (e: SecurityException) {
                Log.w(HDRV_VIEWER_TAG, "delete uri failed (need MediaStore delete request): $uri", e)
            }
        }
        return n
    }

    private fun imageCollectionUri(): Uri =
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private fun videoCollectionUri(): Uri =
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private fun queryImages(context: Context, bucketId: String?): List<GalleryMedia> {
        val resolver = context.contentResolver
        val collection = imageCollectionUri()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
        )
        val sort = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        val (sel, args) = bucketSelection(MediaStore.Images.Media.BUCKET_ID, bucketId)

        return buildList {
            resolver.query(collection, projection, sel, args, sort)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val wCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val hCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol)
                    val mime = cursor.getString(mimeCol)
                    val dateRaw = cursor.getLong(dateCol)
                    val dateSec = normalizeDateModifiedSec(dateRaw)
                    val w = cursor.getInt(wCol).let { if (it > 0) it else 0 }
                    val h = cursor.getInt(hCol).let { if (it > 0) it else 0 }
                    val size = cursor.getLong(sizeCol).let { if (it >= 0) it else 0L }
                    val uri = ContentUris.withAppendedId(collection, id)
                    add(
                        GalleryMedia(
                            id = id,
                            uri = uri,
                            displayName = name,
                            isVideo = false,
                            mimeType = mime,
                            dateModifiedSec = dateSec,
                            width = w,
                            height = h,
                            sizeBytes = size,
                        ),
                    )
                }
            }
        }
    }

    private fun queryVideos(context: Context, bucketId: String?): List<GalleryMedia> {
        val resolver = context.contentResolver
        val collection = videoCollectionUri()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE,
        )
        val sort = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        val (sel, args) = bucketSelection(MediaStore.Video.Media.BUCKET_ID, bucketId)

        return buildList {
            resolver.query(collection, projection, sel, args, sort)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val wCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val hCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol)
                    val mime = cursor.getString(mimeCol)
                    val dateRaw = cursor.getLong(dateCol)
                    val dateSec = normalizeDateModifiedSec(dateRaw)
                    val w = cursor.getInt(wCol).let { if (it > 0) it else 0 }
                    val h = cursor.getInt(hCol).let { if (it > 0) it else 0 }
                    val size = cursor.getLong(sizeCol).let { if (it >= 0) it else 0L }
                    val uri = ContentUris.withAppendedId(collection, id)
                    add(
                        GalleryMedia(
                            id = id,
                            uri = uri,
                            displayName = name,
                            isVideo = true,
                            mimeType = mime,
                            dateModifiedSec = dateSec,
                            width = w,
                            height = h,
                            sizeBytes = size,
                        ),
                    )
                }
            }
        }
    }

    private fun bucketSelection(bucketColumn: String, bucketId: String?): Pair<String?, Array<String>?> {
        if (bucketId == null) return null to null
        return "$bucketColumn = ?" to arrayOf(bucketId)
    }

    private fun normalizeDateModifiedSec(raw: Long): Long {
        if (raw <= 0L) return 0L
        return if (raw > 1_000_000_000_000L) raw / 1000L else raw
    }
}
