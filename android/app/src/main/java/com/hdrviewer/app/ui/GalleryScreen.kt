package com.hdrviewer.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hdrviewer.app.MediaShare
import com.hdrviewer.app.R
import com.hdrviewer.app.data.GalleryFilter
import com.hdrviewer.app.data.MediaRepository
import com.hdrviewer.app.data.GalleryMedia
import com.hdrviewer.app.data.GallerySortOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    vm: GalleryViewModel,
    topBarTitle: String?,
    onOpenViewer: (List<GalleryMedia>, Int) -> Unit,
) {
    val context = LocalContext.current

    val displayFlat = remember(vm.items, vm.searchQuery, vm.sortOrder, vm.mediaFilter, vm.selectedBucketId) {
        vm.displayList()
    }
    val gridCells = remember(vm.items, vm.searchQuery, vm.sortOrder, vm.mediaFilter, vm.selectedBucketId) {
        vm.displayGridCells()
    }

    val gridState = rememberLazyGridState()

    LaunchedEffect(vm.pendingGalleryScrollToStoreKey, gridCells) {
        val key = vm.pendingGalleryScrollToStoreKey ?: return@LaunchedEffect
        if (gridCells.isEmpty()) return@LaunchedEffect
        val index = gridCells.indexOfFirst { cell ->
            cell is GalleryGridCell.Thumb && cell.media.storeKey == key
        }
        if (index >= 0) {
            gridState.scrollToItem(index)
        }
        vm.consumePendingGalleryScroll()
    }

    val haptic = LocalHapticFeedback.current
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) vm.load(context)
    }

    val deleteMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.load(context)
        }
    }

    var searchOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var overflowMenuOpen by remember { mutableStateOf(false) }
    var detailItem by remember { mutableStateOf<GalleryMedia?>(null) }
    var deleteConfirmBulk by remember { mutableStateOf(false) }

    val titleText = topBarTitle ?: stringResource(R.string.app_name)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            if (vm.selectionMode) {
                TopAppBar(
                    windowInsets = appTopBarWindowInsets(),
                    title = { Text(stringResource(R.string.gallery_selected_fmt, vm.selectedKeys.size)) },
                    navigationIcon = {
                        TextButton(onClick = { vm.exitSelectionMode() }) {
                            Text(stringResource(R.string.gallery_exit_select))
                        }
                    },
                    actions = {
                        if (vm.selectedKeys.size == 1) {
                            IconButton(
                                onClick = {
                                    detailItem = displayFlat.firstOrNull { it.storeKey in vm.selectedKeys }
                                },
                            ) {
                                Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.viewer_detail_cd))
                            }
                        }
                        IconButton(
                            onClick = { vm.selectAllInList(displayFlat) },
                            enabled = displayFlat.isNotEmpty(),
                        ) {
                            Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.gallery_select_all))
                        }
                        IconButton(
                            onClick = {
                                val selected = displayFlat.filter { it.storeKey in vm.selectedKeys }
                                MediaShare.share(context, selected)
                            },
                            enabled = vm.selectedKeys.isNotEmpty(),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.gallery_share))
                        }
                        IconButton(
                            onClick = { deleteConfirmBulk = true },
                            enabled = vm.selectedKeys.isNotEmpty(),
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.gallery_delete))
                        }
                    },
                )
            } else {
                TopAppBar(
                    windowInsets = appTopBarWindowInsets(),
                    title = {
                        if (searchOpen) {
                            OutlinedTextField(
                                value = vm.searchQuery,
                                onValueChange = { vm.searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.gallery_search_hint)) },
                                singleLine = true,
                            )
                        } else {
                            Text(
                                titleText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    navigationIcon = {
                        if (searchOpen) {
                            TextButton(onClick = { searchOpen = false }) {
                                Text(stringResource(R.string.back))
                            }
                        }
                    },
                    actions = {
                        if (!searchOpen) {
                            IconButton(onClick = { searchOpen = true }) {
                                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.gallery_search))
                            }
                            Box {
                                IconButton(onClick = { sortMenuOpen = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.gallery_sort))
                                }
                                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_newest)) },
                                        onClick = {
                                            vm.updateSortOrder(GallerySortOrder.NEWEST_FIRST)
                                            sortMenuOpen = false
                                        },
                                        leadingIcon = if (vm.sortOrder == GallerySortOrder.NEWEST_FIRST) {
                                            { Icon(Icons.Filled.Check, contentDescription = null) }
                                        } else {
                                            null
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_oldest)) },
                                        onClick = {
                                            vm.updateSortOrder(GallerySortOrder.OLDEST_FIRST)
                                            sortMenuOpen = false
                                        },
                                        leadingIcon = if (vm.sortOrder == GallerySortOrder.OLDEST_FIRST) {
                                            { Icon(Icons.Filled.Check, contentDescription = null) }
                                        } else {
                                            null
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_name)) },
                                        onClick = {
                                            vm.updateSortOrder(GallerySortOrder.NAME_ASC)
                                            sortMenuOpen = false
                                        },
                                        leadingIcon = if (vm.sortOrder == GallerySortOrder.NAME_ASC) {
                                            { Icon(Icons.Filled.Check, contentDescription = null) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    pickMedia.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                                    )
                                },
                            ) {
                                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = stringResource(R.string.gallery_pick_media_cd))
                            }
                            Box {
                                IconButton(onClick = { overflowMenuOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.gallery_overflow_cd))
                                }
                                DropdownMenu(
                                    expanded = overflowMenuOpen,
                                    onDismissRequest = { overflowMenuOpen = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.gallery_select_mode)) },
                                        onClick = {
                                            vm.selectionMode = true
                                            overflowMenuOpen = false
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null)
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!vm.selectionMode) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = vm.mediaFilter == GalleryFilter.ALL,
                        onClick = { vm.mediaFilter = GalleryFilter.ALL },
                        label = { Text(stringResource(R.string.gallery_filter_all)) },
                    )
                    FilterChip(
                        selected = vm.mediaFilter == GalleryFilter.PHOTOS,
                        onClick = { vm.mediaFilter = GalleryFilter.PHOTOS },
                        label = { Text(stringResource(R.string.gallery_filter_photos)) },
                    )
                    FilterChip(
                        selected = vm.mediaFilter == GalleryFilter.VIDEOS,
                        onClick = { vm.mediaFilter = GalleryFilter.VIDEOS },
                        label = { Text(stringResource(R.string.gallery_filter_videos)) },
                    )
                    FilterChip(
                        selected = vm.mediaFilter == GalleryFilter.GIFS,
                        onClick = { vm.mediaFilter = GalleryFilter.GIFS },
                        label = { Text(stringResource(R.string.gallery_filter_gif)) },
                    )
                }
            }
            Box(Modifier.fillMaxSize()) {
                if (vm.loading && displayFlat.isEmpty()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else if (displayFlat.isEmpty() && !vm.loading) {
                    Text(
                        stringResource(R.string.empty_gallery),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = vm.gridMinSizeDp.dp),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(
                            items = gridCells,
                            key = { cell ->
                                when (cell) {
                                    is GalleryGridCell.DayHeader -> "h_${cell.label}"
                                    is GalleryGridCell.Thumb -> cell.media.storeKey
                                }
                            },
                            span = { cell ->
                                when (cell) {
                                    is GalleryGridCell.DayHeader -> GridItemSpan(maxLineSpan)
                                    is GalleryGridCell.Thumb -> GridItemSpan(1)
                                }
                            },
                        ) { cell ->
                            when (cell) {
                                is GalleryGridCell.DayHeader -> {
                                    Text(
                                        text = cell.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                    )
                                }
                                is GalleryGridCell.Thumb -> {
                                    val item = cell.media
                                    val selected = item.storeKey in vm.selectedKeys
                                    Box(
                                        Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .combinedClickable(
                                                onClick = {
                                                    if (vm.selectionMode) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        vm.toggleSelect(item.storeKey)
                                                    } else {
                                                        val idx = displayFlat.indexOfFirst { it.storeKey == item.storeKey }
                                                        onOpenViewer(displayFlat, idx.coerceAtLeast(0))
                                                    }
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    if (!vm.selectionMode) vm.selectionMode = true
                                                    vm.toggleSelect(item.storeKey)
                                                },
                                            ),
                                    ) {
                                        AsyncImage(
                                            model = galleryGridImageRequest(context, item),
                                            contentDescription = item.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        if (item.isVideo) {
                                            Icon(
                                                Icons.Filled.PlayCircleFilled,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.92f),
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .padding(8.dp),
                                            )
                                        }
                                        if (vm.selectionMode) {
                                            Box(
                                                Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(6.dp),
                                            ) {
                                                Icon(
                                                    imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(Color.Black.copy(alpha = 0.45f))
                                                        .padding(2.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    detailItem?.let { d ->
        MediaDetailDialog(media = d, onDismiss = { detailItem = null })
    }

    if (deleteConfirmBulk) {
        AlertDialog(
            onDismissRequest = { deleteConfirmBulk = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirmBulk = false
                        val uris = displayFlat.filter { it.storeKey in vm.selectedKeys }.map { it.uri }
                        val pending = MediaRepository.createDeleteRequest(context, uris)
                        if (pending != null) {
                            deleteMediaLauncher.launch(
                                IntentSenderRequest.Builder(pending.intentSender).build(),
                            )
                        } else {
                            vm.deleteSelected(context) { }
                        }
                    },
                ) { Text(stringResource(R.string.delete_confirm_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmBulk = false }) {
                    Text(stringResource(R.string.delete_confirm_cancel))
                }
            },
        )
    }
}

@Composable
private fun MediaDetailDialog(
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
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(media.dateModifiedSec * 1000))
    } else "—"
    val resStr = if (media.width > 0 && media.height > 0) "${media.width}×${media.height}" else "—"
    val sizeStr = formatByteSize(media.sizeBytes)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailLine(stringResource(R.string.detail_name), media.displayName ?: "—")
                DetailLine(stringResource(R.string.detail_type), typeLabel)
                DetailLine(stringResource(R.string.detail_resolution), resStr)
                DetailLine(stringResource(R.string.detail_date), dateStr)
                DetailLine(stringResource(R.string.detail_size), sizeStr)
                DetailLine(stringResource(R.string.detail_uri), media.uri.toString())
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.detail_close)) }
        },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatByteSize(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.getDefault(), "%.2f GB", gb)
}
