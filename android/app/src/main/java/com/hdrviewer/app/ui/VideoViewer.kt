package com.hdrviewer.app.ui

import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * @param isActive 仅当前停留页为 true 时播放；避免相邻预加载页同时出声或解码。
 *
 * 亮度由全屏 [DualSegmentBrightnessEffects] 的窗口 headroom 覆盖；视频帧不走 Gainmap（本阶段无第二段像素提亮）。
 */
@Composable
fun VideoViewer(
    uri: Uri,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {},
) {
    val context = LocalContext.current
    val appCtx = context.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestSingleTap = rememberUpdatedState(onSingleTap)
    val exo = remember {
        ExoPlayer.Builder(appCtx).build()
    }

    LaunchedEffect(uri) {
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
    }

    LaunchedEffect(isActive) {
        exo.playWhenReady = isActive
        if (isActive) {
            exo.play()
        } else {
            exo.pause()
        }
    }

    DisposableEffect(lifecycleOwner, isActive) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exo.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (isActive) {
                        exo.playWhenReady = true
                        exo.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(exo) {
        onDispose {
            exo.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true
                controllerShowTimeoutMs = 2500
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                val gd = GestureDetector(
                    ctx,
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            latestSingleTap.value()
                            return true
                        }
                    },
                )
                setOnTouchListener { v, event ->
                    gd.onTouchEvent(event)
                    v.onTouchEvent(event)
                }
            }
        },
        modifier = modifier,
        update = { view ->
            view.player = exo
        },
    )
}
