package com.hdrviewer.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlin.math.PI
import kotlin.math.abs

/**
 * 默认 [consume]=false 时不抢占事件；在 [onGesture] 内按「已放大 / 多指 / 正在缩放」再手动 consume，
 * 以便 1× 单指横滑交给外层 HorizontalPager。
 */
internal suspend fun PointerInputScope.detectCustomTransformGestures(
    panZoomLock: Boolean = false,
    consume: Boolean = true,
    pass: PointerEventPass = PointerEventPass.Main,
    onGestureStart: (PointerInputChange) -> Unit = {},
    onGesture: (
        centroid: Offset,
        pan: Offset,
        zoom: Float,
        rotation: Float,
        mainPointer: PointerInputChange,
        changes: List<PointerInputChange>,
    ) -> Unit,
    onGestureEnd: (PointerInputChange) -> Unit = {},
) {
    awaitEachGesture {
        var rotation = 0f
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop
        var lockedToPanZoom = false

        val down = awaitFirstDown(requireUnconsumed = false, pass = pass)
        onGestureStart(down)

        var pointer = down
        var pointerId = down.id

        while (true) {
            val event = awaitPointerEvent(pass = pass)
            if (event.changes.any { it.isConsumed }) break

            val pointerInputChange =
                event.changes.firstOrNull { it.id == pointerId }
                    ?: event.changes.first()
            pointerId = pointerInputChange.id
            pointer = pointerInputChange

            val zoomChange = event.calculateZoom()
            val rotationChange = event.calculateRotation()
            val panChange = event.calculatePan()

            if (!pastTouchSlop) {
                zoom *= zoomChange
                rotation += rotationChange
                pan += panChange

                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                val zoomMotion = abs(1 - zoom) * centroidSize
                val rotationMotion =
                    abs(rotation * PI.toFloat() * centroidSize / 180f)
                val panMotion = pan.getDistance()

                if (zoomMotion > touchSlop ||
                    rotationMotion > touchSlop ||
                    panMotion > touchSlop
                ) {
                    pastTouchSlop = true
                    lockedToPanZoom = panZoomLock && rotationMotion < touchSlop
                }
            }

            if (pastTouchSlop) {
                val effectiveRotation = if (lockedToPanZoom) 0f else rotationChange
                if (effectiveRotation != 0f ||
                    zoomChange != 1f ||
                    panChange != Offset.Zero
                ) {
                    onGesture(
                        event.calculateCentroid(useCurrent = false),
                        panChange,
                        zoomChange,
                        effectiveRotation,
                        pointer,
                        event.changes,
                    )
                }

                if (consume) {
                    event.changes.forEach { change ->
                        if (change.previousPosition != change.position) {
                            change.consume()
                        }
                    }
                }
            }

            if (!event.changes.any { it.pressed }) break
        }
        onGestureEnd(pointer)
    }
}
