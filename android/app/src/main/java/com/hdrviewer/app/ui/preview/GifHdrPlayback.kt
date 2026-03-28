package com.hdrviewer.app.ui.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.util.Log
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val GIF_HDR_LOG_TAG = "HDRV_GifHdr"

private const val MOVIE_FRAME_MS = 50

/**
 * 一帧：解码后的 mutable ARGB 位图 + 该帧显示时长（毫秒）。
 */
data class GifHdrFrame(
    val bitmap: Bitmap,
    val durationMs: Long,
)

/**
 * 用 [ImageDecoder] + [AnimatedImageDrawable] 将 GIF 解为逐帧位图（软件分配，便于 [Gainmap]）。
 * 帧控制通过反射调用（与部分 AGP/Kotlin 对 AnimatedImageDrawable 的存根一致）。
 * 失败或空结果时回退 [Movie] 按时间片采样（每步 [MOVIE_FRAME_MS] ms）。
 */
suspend fun decodeGifFramesForHdr(context: Context, uri: Uri): List<GifHdrFrame>? =
    withContext(Dispatchers.Default) {
        val fromAnim = runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val drawable = ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            if (drawable !is AnimatedImageDrawable) null
            else decodeAnimatedImageDrawable(drawable).takeIf { it.isNotEmpty() }
        }.onFailure {
            Log.w(GIF_HDR_LOG_TAG, "AnimatedImageDrawable decode failed uri=$uri", it)
        }.getOrNull()

        if (fromAnim != null) return@withContext fromAnim

        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                @Suppress("DEPRECATION")
                val movie = Movie.decodeStream(stream) ?: return@use null
                decodeWithMovie(movie)
            }
        }.onFailure {
            Log.w(GIF_HDR_LOG_TAG, "Movie fallback failed uri=$uri", it)
        }.getOrNull()
    }

private fun decodeAnimatedImageDrawable(aid: AnimatedImageDrawable): List<GifHdrFrame> {
    val clazz = aid.javaClass
    val getFrameCount = clazz.getMethod("getFrameCount")
    val seekTo = clazz.getMethod("seekTo", Long::class.javaPrimitiveType)
    val getFrameDuration = clazz.getMethod("getFrameDuration", Int::class.javaPrimitiveType)

    val w = aid.intrinsicWidth.coerceAtLeast(1)
    val h = aid.intrinsicHeight.coerceAtLeast(1)
    val n = getFrameCount.invoke(aid) as Int
    if (n <= 0) return emptyList()

    val out = mutableListOf<GifHdrFrame>()
    var timeMs = 0L
    for (i in 0 until n) {
        seekTo.invoke(aid, timeMs)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        aid.setBounds(0, 0, w, h)
        aid.draw(canvas)
        val durRaw = getFrameDuration.invoke(aid, i)
        val dur = when (durRaw) {
            is Int -> durRaw.toLong()
            is Long -> durRaw
            else -> (durRaw as Number).toLong()
        }.coerceAtLeast(1L)
        out.add(GifHdrFrame(bmp, dur))
        timeMs += dur
    }
    aid.stop()
    return out
}

@Suppress("DEPRECATION")
private fun decodeWithMovie(movie: Movie): List<GifHdrFrame>? {
    val duration = movie.duration()
    val w = movie.width().coerceAtLeast(1)
    val h = movie.height().coerceAtLeast(1)
    if (duration <= 0) {
        movie.setTime(0)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        movie.draw(canvas, 0f, 0f)
        return listOf(GifHdrFrame(bmp, 100L))
    }
    val out = mutableListOf<GifHdrFrame>()
    var t = 0
    while (t < duration) {
        movie.setTime(t)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        movie.draw(canvas, 0f, 0f)
        val step = min(MOVIE_FRAME_MS, duration - t).coerceAtLeast(1)
        out.add(GifHdrFrame(bmp, step.toLong()))
        t += step
    }
    return out.ifEmpty { null }
}
