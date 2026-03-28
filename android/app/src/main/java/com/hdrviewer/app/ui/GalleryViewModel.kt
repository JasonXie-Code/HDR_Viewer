package com.hdrviewer.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hdrviewer.app.data.GalleryFilter
import com.hdrviewer.app.data.GalleryMedia
import com.hdrviewer.app.data.GallerySortOrder
import com.hdrviewer.app.data.MediaAlbum
import com.hdrviewer.app.data.MediaRepository
import com.hdrviewer.app.data.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GalleryViewModel(
    application: Application,
    private val prefs: UserPreferencesRepository,
) : AndroidViewModel(application) {
    /**
     * 全屏预览亮度滑块 **0～200%**；进入预览时由 [onViewerEnter] 从「默认预览亮度」载入。
     */
    private var sessionBrightnessPctState by mutableStateOf(100f)
    val sessionBrightnessPct: Float get() = sessionBrightnessPctState

    /**
     * 当前画面 Gainmap / 预览使用的亮度（0～200%）。
     * 与 [sessionBrightnessPct] 在「亮度面板关闭」时一致；面板打开时由 [setViewerPreviewBrightnessPct] 节流更新，避免与滑块同层重组抢主线程。
     */
    private var viewerBrightnessPctState by mutableStateOf(100f)
    val viewerBrightnessPct: Float get() = viewerBrightnessPctState

    fun setSessionBrightnessPct(value: Float) {
        val v = value.coerceIn(0f, 200f)
        sessionBrightnessPctState = v
        viewerBrightnessPctState = v
    }

    /** 仅更新画面预览亮度（会话持久值见 [setSessionBrightnessPct]）。 */
    fun setViewerPreviewBrightnessPct(value: Float) {
        viewerBrightnessPctState = value.coerceIn(0f, 200f)
    }

    /** 由 [UserPreferencesRepository] 驱动（设置页与首次加载）。 */
    var sessionSaturationBoostEnabled by mutableStateOf(false)
        private set

    /** 全屏预览是否保持常亮（设置页可关）。 */
    var keepScreenOnInViewer by mutableStateOf(true)
        private set

    private var pendingGalleryScrollToStoreKeyState by mutableStateOf<String?>(null)
    val pendingGalleryScrollToStoreKey: String? get() = pendingGalleryScrollToStoreKeyState

    fun setGalleryReturnFocusKey(storeKey: String?) {
        pendingGalleryScrollToStoreKeyState = storeKey
    }

    fun consumePendingGalleryScroll() {
        pendingGalleryScrollToStoreKeyState = null
    }

    var items by mutableStateOf<List<GalleryMedia>>(emptyList())
        private set
    var albums by mutableStateOf<List<MediaAlbum>>(emptyList())
        private set
    var selectedBucketId: String? by mutableStateOf(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var searchQuery by mutableStateOf("")
    var sortOrder by mutableStateOf(GallerySortOrder.NEWEST_FIRST)
        private set
    var mediaFilter by mutableStateOf(GalleryFilter.ALL)
    var selectionMode by mutableStateOf(false)
    var selectedKeys by mutableStateOf(setOf<String>())
        private set

    /** 网格列宽下限（dp），来自设置。 */
    var gridMinSizeDp by mutableStateOf(108)
        private set

    init {
        viewModelScope.launch {
            prefs.prefsFlow
                .map { Triple(it.saturationBoostEnabled, it.sortOrder, it.gridMinSizeDp) }
                .distinctUntilChanged()
                .collect { (sat, sort, grid) ->
                    sessionSaturationBoostEnabled = sat
                    sortOrder = sort
                    gridMinSizeDp = grid
                }
        }
        viewModelScope.launch {
            prefs.prefsFlow
                .map { it.keepScreenOn }
                .distinctUntilChanged()
                .collect { keepScreenOnInViewer = it }
        }
    }

    fun updateSortOrder(order: GallerySortOrder) {
        sortOrder = order
        viewModelScope.launch { prefs.setSortOrder(order) }
    }

    fun onViewerEnter() {
        viewModelScope.launch {
            val snap = prefs.prefsFlow.first()
            val v = snap.defaultBrightnessPct.coerceIn(0f, 200f)
            sessionBrightnessPctState = v
            viewerBrightnessPctState = v
        }
    }

    fun displayList(): List<GalleryMedia> {
        var list = items
        list = when (mediaFilter) {
            GalleryFilter.ALL -> list
            GalleryFilter.PHOTOS -> list.filter { !it.isVideo }
            GalleryFilter.VIDEOS -> list.filter { it.isVideo }
            GalleryFilter.GIFS -> list.filter { it.isAnimatedGif }
        }
        val q = searchQuery.trim().lowercase()
        if (q.isNotEmpty()) {
            list = list.filter { m ->
                m.displayName?.lowercase()?.contains(q) == true
            }
        }
        list = when (sortOrder) {
            GallerySortOrder.NEWEST_FIRST -> list.sortedByDescending { it.dateModifiedSec }
            GallerySortOrder.OLDEST_FIRST -> list.sortedBy { it.dateModifiedSec }
            GallerySortOrder.NAME_ASC -> list.sortedBy { it.displayName?.lowercase() ?: "" }
        }
        return list
    }

    fun displayGridCells(): List<GalleryGridCell> {
        val flat = displayList()
        if (flat.isEmpty()) return emptyList()
        if (sortOrder == GallerySortOrder.NAME_ASC) {
            return flat.map { GalleryGridCell.Thumb(it) }
        }
        val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val grouped = flat.groupBy { m ->
            dayKeyFormat.format(Date(m.dateModifiedSec.coerceAtLeast(0L) * 1000L))
        }
        val orderedKeys = when (sortOrder) {
            GallerySortOrder.NEWEST_FIRST -> grouped.keys.sortedDescending()
            GallerySortOrder.OLDEST_FIRST -> grouped.keys.sorted()
            else -> grouped.keys.sortedDescending()
        }
        val longFmt = DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault())
        return buildList {
            for (key in orderedKeys) {
                val medias = grouped[key] ?: continue
                val label = run {
                    val parsed = dayKeyFormat.parse(key)
                    if (parsed != null) longFmt.format(parsed) else key
                }
                add(GalleryGridCell.DayHeader(label))
                val sortedMedias = when (sortOrder) {
                    GallerySortOrder.NEWEST_FIRST -> medias.sortedByDescending { it.dateModifiedSec }
                    GallerySortOrder.OLDEST_FIRST -> medias.sortedBy { it.dateModifiedSec }
                    else -> medias
                }
                for (m in sortedMedias) {
                    add(GalleryGridCell.Thumb(m))
                }
            }
        }
    }

    fun load(context: Context) {
        viewModelScope.launch {
            loading = true
            withContext(Dispatchers.IO) {
                val app = context.applicationContext
                albums = MediaRepository.listAlbums(app)
                items = MediaRepository.queryMedia(app, selectedBucketId)
            }
            loading = false
        }
    }

    fun selectAlbum(context: Context, bucketId: String?) {
        selectedBucketId = bucketId
        load(context)
    }

    fun toggleSelect(key: String) {
        selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
    }

    fun selectAllInList(list: List<GalleryMedia>) {
        selectedKeys = list.map { it.storeKey }.toSet()
    }

    fun clearSelection() {
        selectedKeys = emptySet()
        selectionMode = false
    }

    fun exitSelectionMode() {
        selectedKeys = emptySet()
        selectionMode = false
    }

    fun deleteOne(context: Context, media: GalleryMedia, onDone: (deletedCount: Int) -> Unit) {
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                MediaRepository.deleteUris(context.applicationContext, listOf(media.uri))
            }
            load(context)
            onDone(n)
        }
    }

    fun deleteSelected(context: Context, onDone: (deletedCount: Int) -> Unit) {
        val uris = items.filter { it.storeKey in selectedKeys }.map { it.uri }
        if (uris.isEmpty()) {
            onDone(0)
            return
        }
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                MediaRepository.deleteUris(context.applicationContext, uris)
            }
            exitSelectionMode()
            load(context)
            onDone(n)
        }
    }
}
