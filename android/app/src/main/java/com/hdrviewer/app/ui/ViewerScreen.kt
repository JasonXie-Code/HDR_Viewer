package com.hdrviewer.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.decode.BitmapFactoryDecoder
import coil.imageLoader
import coil.request.ImageRequest
import com.hdrviewer.app.BuildConfig
import com.hdrviewer.app.HDRV_VIEWER_TAG
import com.hdrviewer.app.MediaShare
import com.hdrviewer.app.R
import com.hdrviewer.app.data.GalleryMedia
import com.hdrviewer.app.data.MediaRepository
import com.hdrviewer.app.debug.PreviewHistogramLog
import com.hdrviewer.app.findActivity
import com.hdrviewer.app.ui.preview.HdrBitmapImage
import com.hdrviewer.app.ui.preview.GifHdrAnimatedImage
import com.hdrviewer.app.ui.preview.GifHdrFrame
import com.hdrviewer.app.ui.preview.HdrGainmapHelper
import com.hdrviewer.app.ui.preview.decodeGifFramesForHdr
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(
    vm: GalleryViewModel,
    startKey: String,
    onBack: () -> Unit,
) {
    val items = remember(vm.items, vm.searchQuery, vm.sortOrder, vm.mediaFilter, vm.selectedBucketId) {
        vm.displayList()
    }
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val initialPage = items.indexOfFirst { it.storeKey == startKey }.coerceIn(0, items.lastIndex)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { items.size })
    val context = LocalContext.current

    val deleteMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.load(context)
            vm.setGalleryReturnFocusKey(null)
            onBack()
        }
    }

    LaunchedEffect(Unit) {
        vm.onViewerEnter()
    }

    fun finishViewerToGallery() {
        val idx = pagerState.settledPage.coerceIn(0, items.lastIndex)
        vm.setGalleryReturnFocusKey(items[idx].storeKey)
        onBack()
    }

    BackHandler(onBack = ::finishViewerToGallery)

    /** 必须用 [PagerState.settledPage]：若用 [PagerState.currentPage]，滑动过程中每帧变化会触发整页（含顶栏）重组，左右滑图会明显掉帧。 */
    val currentItem = items[pagerState.settledPage.coerceIn(0, items.lastIndex)]

    var showDeleteOne by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    val toggleChrome = { chromeVisible = !chromeVisible }

    /** 全屏媒体在下层；顶栏叠加在上层，显隐不改变 [ViewerBrightnessLayer] 布局，避免画面跳动。 */
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        ViewerBrightnessLayer(
            vm = vm,
            items = items,
            pagerState = pagerState,
            chromeVisible = chromeVisible,
            onToggleChrome = toggleChrome,
        )
        if (chromeVisible) {
            TopAppBar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                windowInsets = appTopBarWindowInsets(),
                title = {
                    Text(
                        currentItem.displayName ?: "",
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = ::finishViewerToGallery) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { MediaShare.share(context, listOf(currentItem)) },
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.viewer_share_cd))
                    }
                    IconButton(onClick = { showDetail = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.viewer_detail_cd))
                    }
                    IconButton(onClick = { showDeleteOne = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.viewer_delete_cd))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.35f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        }
    }

    if (showDeleteOne) {
        AlertDialog(
            onDismissRequest = { showDeleteOne = false },
            title = { Text(stringResource(R.string.delete_one_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteOne = false
                        val pending = MediaRepository.createDeleteRequest(context, listOf(currentItem.uri))
                        if (pending != null) {
                            deleteMediaLauncher.launch(
                                IntentSenderRequest.Builder(pending.intentSender).build(),
                            )
                        } else {
                            vm.deleteOne(context, currentItem) {
                                vm.setGalleryReturnFocusKey(null)
                                onBack()
                            }
                        }
                    },
                ) { Text(stringResource(R.string.delete_confirm_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteOne = false }) {
                    Text(stringResource(R.string.delete_confirm_cancel))
                }
            },
        )
    }

    if (showDetail) {
        SimpleMediaDetailDialog(
            media = currentItem,
            onDismiss = { showDetail = false },
        )
    }
}

/**
 * 全屏查看亮度外壳：**勿在此处读取** [GalleryViewModel.viewerBrightnessPct]。
 * 否则拖动 Sheet 内滑块时 [snapshotFlow]→[GalleryViewModel.setViewerPreviewBrightnessPct] 会每帧使本层重组，
 * 连带重组 [BrightnessSheetHost] 与 [Slider]，表现为单次 `onValueChange` 后即 `onValueChangeFinished`、滑块不跟手。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ViewerBrightnessLayer(
    vm: GalleryViewModel,
    items: List<GalleryMedia>,
    pagerState: PagerState,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
) {
    var showBrightnessSheet by remember { mutableStateOf(false) }
    val dismissBrightnessSheet = remember { { showBrightnessSheet = false } }

    Box(Modifier.fillMaxSize()) {
        ViewerBrightnessMediaSection(
            vm = vm,
            items = items,
            pagerState = pagerState,
            chromeVisible = chromeVisible,
            onToggleChrome = onToggleChrome,
            onOpenBrightnessSheet = { showBrightnessSheet = true },
        )
        if (showBrightnessSheet) {
            BrightnessSheetHost(
                vm = vm,
                onDismiss = dismissBrightnessSheet,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ViewerBrightnessMediaSection(
    vm: GalleryViewModel,
    items: List<GalleryMedia>,
    pagerState: PagerState,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    onOpenBrightnessSheet: () -> Unit,
) {
    val effectiveBrightnessPct = vm.viewerBrightnessPct
    val sampledBrightnessPct = effectiveBrightnessPct.roundToInt().coerceIn(0, 200).toFloat()
    val slider01 = sampledBrightnessPct / 200f

    var blockPagerForZoom by remember { mutableStateOf(false) }
    val settled = pagerState.settledPage
    LaunchedEffect(settled) {
        blockPagerForZoom = false
    }

    DualSegmentBrightnessEffects(keepScreenOn = vm.keepScreenOnInViewer)

    val ratioMaxPreview = remember(sampledBrightnessPct) {
        HdrGainmapHelper.ratioMaxFromBrightnessPct(sampledBrightnessPct)
    }

    ViewerDebugInstrumentation(
        pagerState = pagerState,
        effectivePct = sampledBrightnessPct,
        sessionBrightnessPct = vm.sessionBrightnessPct,
        slider01 = slider01,
        ratioMaxPreview = ratioMaxPreview,
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !blockPagerForZoom,
            /** 0：不预合成邻页，减轻全分辨率 + Gainmap 同屏多层的滑动掉帧；画质仍为逐页全分辨率解码。 */
            beyondBoundsPageCount = 0,
        ) { page ->
            val item = items[page]
            val activeSettled = pagerState.settledPage
            Box(Modifier.fillMaxSize()) {
                if (item.isVideo) {
                    VideoViewer(
                        uri = item.uri,
                        isActive = page == activeSettled,
                        modifier = Modifier.fillMaxSize(),
                        onSingleTap = onToggleChrome,
                    )
                } else {
                    ZoomableImageWithPunch(
                        item = item,
                        effectiveBrightnessPct = sampledBrightnessPct,
                        saturationBoostEnabled = vm.sessionSaturationBoostEnabled,
                        isHistogramActive = page == activeSettled,
                        onZoomedChange = { zoomed ->
                            if (page == activeSettled) {
                                blockPagerForZoom = zoomed
                            }
                        },
                        onSingleTap = onToggleChrome,
                    )
                }
            }
        }

        if (chromeVisible) {
            FloatingActionButton(
                onClick = onOpenBrightnessSheet,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(end = 20.dp, bottom = 24.dp)
                    .size(56.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = Color.White,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_brightness_sun),
                    contentDescription = stringResource(R.string.brightness_fab_cd),
                    modifier = Modifier.size(28.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

/**
 * 亮度面板宿主。
 *
 * **关键**：[sheetSliderState] 的 `.floatValue` **不得**在本函数的组合作用域中读取。
 * 传递 [MutableFloatState] 对象引用给 [BrightnessSheetContent]，
 * 让读取仅发生在子组合内——这样滑块值变化只使子树重组，
 * 不会使本层 lambda 重新执行、创建新回调实例、从而中断 Slider 拖拽手势。
 *
 * 使用全屏 [Box] 叠加层而非 [androidx.compose.ui.window.Dialog]，避免系统窗口对背后内容加深色遮罩。
 */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
private fun BrightnessSheetHost(
    vm: GalleryViewModel,
    onDismiss: () -> Unit,
) {
    val sheetSliderState = remember { mutableFloatStateOf(vm.sessionBrightnessPct) }

    LaunchedEffect(Unit) {
        sheetSliderState.floatValue = vm.sessionBrightnessPct
        snapshotFlow { sheetSliderState.floatValue }
            .sample(16.milliseconds)
            .collect { v -> vm.setViewerPreviewBrightnessPct(v) }
    }

    DisposableEffect(Unit) {
        onDispose {
            vm.setSessionBrightnessPct(sheetSliderState.floatValue)
        }
    }

    val onSliderFinished = remember<() -> Unit> {
        {
            vm.setSessionBrightnessPct(sheetSliderState.floatValue)
        }
    }

    BackHandler(onBack = onDismiss)

    /** 不用 `ModalBottomSheet`：其内部纵向 sheet 拖动手势会与 `Slider` 横向拖动争抢。 */
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
        )
        /**
         * 底栏整体上移，避免控件贴屏幕底缘；[Surface] 仍用 `fillMaxWidth` 铺满宽度。
         * 下方空隙由与面板同色的 [BrightnessSheetNavBarFill] 填充，避免露出黑条。
         */
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                BrightnessSheetContent(
                    sliderState = sheetSliderState,
                    onSliderChangeFinished = onSliderFinished,
                )
            }
            BrightnessSheetNavBarFill(liftDp = BrightnessSheetBottomLift)
        }
    }
}

/** 底栏整体上移量：在 [Surface] 下方用同色块垫高，避免控件贴底。 */
private val BrightnessSheetBottomLift = 24.dp

/**
 * [Surface] 下方延伸区域：含 [liftDp]（整体上移）与系统导航栏高度，与面板同色，避免底部黑条。
 */
@Composable
private fun BrightnessSheetNavBarFill(liftDp: Dp) {
    val density = LocalDensity.current
    val navPx = WindowInsets.navigationBars.getBottom(density)
    val navH = with(density) { navPx.toDp() }
    val totalH = liftDp + navH
    if (totalH > 0.dp) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(totalH)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
    }
}

/**
 * Debug 专用：第二段强度链路、Pager 落定（仅 [BuildConfig.DEBUG]）。
 * 过滤：`adb logcat -s HDRV_Viewer:D`
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ViewerDebugInstrumentation(
    pagerState: PagerState,
    effectivePct: Float,
    sessionBrightnessPct: Float,
    slider01: Float,
    ratioMaxPreview: Float,
) {
    if (!BuildConfig.DEBUG) return

    LaunchedEffect(
        slider01,
        ratioMaxPreview,
        effectivePct,
        sessionBrightnessPct,
    ) {
        Log.d(
            HDRV_VIEWER_TAG,
            "gainmap_chain viewerPct=${String.format("%.1f", effectivePct)} sessionPct=${String.format("%.1f", sessionBrightnessPct)} " +
                "slider01=${String.format("%.4f", slider01)} headroom_cap=${HdrGainmapHelper.HEADROOM_CAP} " +
                "ratio_max=${String.format("%.4f", ratioMaxPreview)}",
        )
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { s ->
                Log.d(
                    HDRV_VIEWER_TAG,
                    "pager_settled page=$s currentPage=${pagerState.currentPage} " +
                        "offsetFr=${String.format("%.3f", pagerState.currentPageOffsetFraction)}",
                )
            }
    }

}

@Composable
private fun ZoomableImageWithPunch(
    item: GalleryMedia,
    effectiveBrightnessPct: Float,
    saturationBoostEnabled: Boolean,
    isHistogramActive: Boolean,
    onZoomedChange: (Boolean) -> Unit,
    onSingleTap: () -> Unit,
) {
    val context = LocalContext.current
    val latestSingleTap = rememberUpdatedState(onSingleTap)

    var scale by remember(item.storeKey) { mutableFloatStateOf(1f) }
    var offset by remember(item.storeKey) { mutableStateOf(Offset.Zero) }
    var layoutCoords by remember(item.storeKey) { mutableStateOf<LayoutCoordinates?>(null) }
    val layoutCoordsState = rememberUpdatedState(layoutCoords)

    LaunchedEffect(item.storeKey) { onZoomedChange(false) }

    if (BuildConfig.DEBUG) {
        LaunchedEffect(effectiveBrightnessPct, item.storeKey, isHistogramActive) {
            if (!isHistogramActive) return@LaunchedEffect
            delay(1000L)
            var coords: LayoutCoordinates? = null
            for (i in 0 until 60) {
                val c = layoutCoordsState.value
                if (c != null && c.isAttached) { coords = c; break }
                delay(16L)
            }
            val c = coords ?: return@LaunchedEffect
            if (!c.isAttached) return@LaunchedEffect
            val act = context.findActivity() ?: return@LaunchedEffect
            val pos = c.positionInWindow()
            val sz = c.size
            val rect = Rect(
                pos.x.roundToInt(), pos.y.roundToInt(),
                (pos.x + sz.width).roundToInt(), (pos.y + sz.height).roundToInt(),
            )
            PreviewHistogramLog.logFromWindowPixelCopy(
                act.window, rect, item.storeKey, brightnessPct = effectiveBrightnessPct,
            )
        }
    }

    val zoomGestureModifier = Modifier
        .graphicsLayer(
            scaleX = scale, scaleY = scale,
            translationX = offset.x, translationY = offset.y,
        )
        .pointerInput(item.storeKey) {
            detectTapGestures(
                onTap = { latestSingleTap.value() },
                onDoubleTap = {
                    if (scale <= 1.02f) {
                        scale = 1.3f
                        offset = Offset.Zero
                        onZoomedChange(true)
                    } else {
                        scale = 1f
                        offset = Offset.Zero
                        onZoomedChange(false)
                    }
                },
            )
        }
        .pointerInput(item.storeKey) {
            detectCustomTransformGestures(
                consume = false,
                onGesture = { _, panChange, zoomChange, _, _, changes ->
                    val pressedCount = changes.count { it.pressed }
                    val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                    scale = newScale
                    if (newScale > 1.02f) {
                        val maxPan = 2000f * newScale
                        offset = Offset(
                            (offset.x + panChange.x).coerceIn(-maxPan, maxPan),
                            (offset.y + panChange.y).coerceIn(-maxPan, maxPan),
                        )
                        onZoomedChange(true)
                    } else {
                        scale = 1f; offset = Offset.Zero; onZoomedChange(false)
                    }
                    val shouldConsume = newScale > 1.02f || pressedCount > 1 || zoomChange != 1f
                    if (shouldConsume) {
                        changes.forEach { change ->
                            if (change.previousPosition != change.position) change.consume()
                        }
                    }
                },
            )
        }
        .onGloballyPositioned { layoutCoords = it }

    val brightnessKey = effectiveBrightnessPct.roundToInt().coerceIn(0, 200)

    if (item.isAnimatedGif) {
        ZoomableGifWithHdr(
            item = item,
            context = context,
            effectiveBrightnessPct = effectiveBrightnessPct,
            saturationBoostEnabled = saturationBoostEnabled,
            brightnessKey = brightnessKey,
            zoomGestureModifier = zoomGestureModifier,
        )
    } else {
        var baseBitmap by remember(item.storeKey) { mutableStateOf<Bitmap?>(null) }
        var isOriginalUltraHdr by remember(item.storeKey) { mutableStateOf(false) }

        LaunchedEffect(item.uri, item.storeKey) {
            val request = ImageRequest.Builder(context)
                .data(item.uri)
                .allowHardware(false)
                .crossfade(false)
                .build()
            val result = context.imageLoader.execute(request)
            val bmp = (result.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
            isOriginalUltraHdr = bmp.hasGainmap()
            baseBitmap = HdrGainmapHelper.toMutableArgb(bmp)
        }

        val saturation = remember(brightnessKey, saturationBoostEnabled) {
            HdrGainmapHelper.saturationScaleForBrightnessPct(
                brightnessPct = effectiveBrightnessPct,
                enabled = saturationBoostEnabled,
            )
        }

        baseBitmap?.let { bmp ->
            HdrGainmapHelper.applyGainmapInPlace(
                bmp, effectiveBrightnessPct, isOriginalUltraHdr,
            )
            HdrBitmapImage(
                bitmap = bmp,
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                saturation = saturation,
                modifier = Modifier.fillMaxSize().then(zoomGestureModifier),
            )
        }
    }
}

@Composable
private fun ZoomableGifWithHdr(
    item: GalleryMedia,
    context: Context,
    effectiveBrightnessPct: Float,
    saturationBoostEnabled: Boolean,
    brightnessKey: Int,
    zoomGestureModifier: Modifier,
) {
    var previewBitmap by remember(item.storeKey) { mutableStateOf<Bitmap?>(null) }
    var isOriginalUltraHdr by remember(item.storeKey) { mutableStateOf(false) }
    var decodedFrames by remember(item.storeKey) { mutableStateOf<List<GifHdrFrame>?>(null) }

    LaunchedEffect(item.uri, item.storeKey) {
        previewBitmap = null
        decodedFrames = null
        val previewReq = ImageRequest.Builder(context)
            .data(item.uri)
            .allowHardware(false)
            .crossfade(false)
            .decoderFactory(BitmapFactoryDecoder.Factory())
            .build()
        val previewResult = context.imageLoader.execute(previewReq)
        val previewBmp = (previewResult.drawable as? BitmapDrawable)?.bitmap
        if (previewBmp != null) {
            isOriginalUltraHdr = previewBmp.hasGainmap()
            previewBitmap = HdrGainmapHelper.toMutableArgb(previewBmp)
        }
        val full = decodeGifFramesForHdr(context, item.uri)
        if (full != null) {
            previewBitmap = null
            decodedFrames = full
        } else {
            decodedFrames = emptyList()
        }
    }

    DisposableEffect(decodedFrames) {
        val frames = decodedFrames
        onDispose {
            frames?.forEach { it.bitmap.recycle() }
        }
    }

    DisposableEffect(previewBitmap) {
        val p = previewBitmap
        onDispose { p?.recycle() }
    }

    val stillDecoding = decodedFrames == null
    val frames = decodedFrames
    val useAnimation = frames != null && frames.size > 1
    val singleDecoded = frames != null && frames.size == 1

    Box(Modifier.fillMaxSize().then(zoomGestureModifier)) {
        when {
            useAnimation -> {
                val list = frames!!
                GifHdrAnimatedImage(
                    frames = list,
                    brightnessKey = brightnessKey,
                    effectiveBrightnessPct = effectiveBrightnessPct,
                    isOriginalUltraHdr = isOriginalUltraHdr,
                    contentDescription = item.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            singleDecoded -> {
                val bmp = frames!![0].bitmap
                val saturation = remember(brightnessKey, saturationBoostEnabled) {
                    HdrGainmapHelper.saturationScaleForBrightnessPct(
                        brightnessPct = effectiveBrightnessPct,
                        enabled = saturationBoostEnabled,
                    )
                }
                HdrGainmapHelper.applyGainmapInPlace(
                    bmp, effectiveBrightnessPct, isOriginalUltraHdr,
                )
                HdrBitmapImage(
                    bitmap = bmp,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Fit,
                    saturation = saturation,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            previewBitmap != null -> {
                val bmp = previewBitmap!!
                val saturation = remember(brightnessKey, saturationBoostEnabled) {
                    HdrGainmapHelper.saturationScaleForBrightnessPct(
                        brightnessPct = effectiveBrightnessPct,
                        enabled = saturationBoostEnabled,
                    )
                }
                HdrGainmapHelper.applyGainmapInPlace(
                    bmp, effectiveBrightnessPct, isOriginalUltraHdr,
                )
                HdrBitmapImage(
                    bitmap = bmp,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Fit,
                    saturation = saturation,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (stillDecoding && previewBitmap != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

/**
 * 亮度面板内容。接收 [MutableFloatState] 对象引用而非原始 [Float]，
 * 确保 `.floatValue` **仅在本组合作用域内读取**——
 * 滑块拖动引起的值变化只使本子树重组，不波及父级 [BrightnessSheetHost]。
 */
@Composable
private fun BrightnessSheetContent(
    sliderState: MutableFloatState,
    onSliderChangeFinished: (() -> Unit)? = null,
) {
    val brightnessPct = sliderState.floatValue

    val stableOnValueChange = remember<(Float) -> Unit>(sliderState) {
        { v -> sliderState.floatValue = v }
    }

    /** 勿将 [Slider] 放在 [verticalScroll] 内：会与横向拖动手势冲突。 */
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.brightness_pct_value, brightnessPct.roundToInt()),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
        )
        Slider(
            value = brightnessPct,
            onValueChange = stableOnValueChange,
            onValueChangeFinished = { onSliderChangeFinished?.invoke() },
            valueRange = 0f..200f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SimpleMediaDetailDialog(
    media: GalleryMedia,
    onDismiss: () -> Unit,
) {
    val typeLabel = stringResource(
        when {
            media.isVideo -> R.string.type_video
            media.isAnimatedGif -> R.string.type_gif
            else -> R.string.type_photo
        },
    )
    val dateStr = if (media.dateModifiedSec > 0) {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(media.dateModifiedSec * 1000))
    } else "—"
    val resStr = if (media.width > 0 && media.height > 0) "${media.width}×${media.height}" else "—"
    val sizeStr = formatByteSize(media.sizeBytes)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailLineViewer(stringResource(R.string.detail_name), media.displayName ?: "—")
                DetailLineViewer(stringResource(R.string.detail_type), typeLabel)
                DetailLineViewer(stringResource(R.string.detail_resolution), resStr)
                DetailLineViewer(stringResource(R.string.detail_date), dateStr)
                DetailLineViewer(stringResource(R.string.detail_size), sizeStr)
                DetailLineViewer(stringResource(R.string.detail_uri), media.uri.toString())
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.detail_close)) }
        },
    )
}

@Composable
private fun DetailLineViewer(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatByteSize(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(java.util.Locale.getDefault(), "%.2f GB", gb)
}
