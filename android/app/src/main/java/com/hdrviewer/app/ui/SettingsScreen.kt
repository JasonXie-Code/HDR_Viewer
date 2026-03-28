package com.hdrviewer.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hdrviewer.app.BuildConfig
import com.hdrviewer.app.R
import com.hdrviewer.app.data.GallerySortOrder
import com.hdrviewer.app.data.UserPreferencesRepository
import com.hdrviewer.app.data.UserPrefsSnapshot
import com.hdrviewer.app.display.DisplayCapabilityInfo
import com.hdrviewer.app.findActivity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { UserPreferencesRepository(context.applicationContext) }
    var snap by remember { mutableStateOf<UserPrefsSnapshot?>(null) }
    var sortDialog by remember { mutableStateOf(false) }
    var gridDialog by remember { mutableStateOf(false) }
    var canWriteSystemSettings by remember {
        mutableStateOf(Settings.System.canWrite(context.applicationContext))
    }

    LaunchedEffect(Unit) {
        prefs.prefsFlow.collect { snap = it }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canWriteSystemSettings = Settings.System.canWrite(context.applicationContext)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openManageWriteSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        runCatching { context.startActivity(intent) }
    }

    val activity = context.findActivity()
    val displayCap = remember(activity) {
        if (activity != null) DisplayCapabilityInfo.detect(activity)
        else DisplayCapabilityInfo.fallback()
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                windowInsets = appTopBarWindowInsets(),
                title = { Text(stringResource(R.string.nav_settings)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        val s = snap
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSectionCard(title = stringResource(R.string.settings_section_display)) {
                Text(
                    stringResource(R.string.settings_brightness_policy_fullscreen),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
                if (s != null) {
                    Text(
                        stringResource(R.string.settings_default_brightness),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.brightness_pct_value, s.defaultBrightnessPct.roundToInt()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Slider(
                        value = s.defaultBrightnessPct,
                        onValueChange = { v ->
                            scope.launch { prefs.setDefaultBrightnessPct(kotlin.math.round(v)) }
                        },
                        valueRange = 0f..200f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.brightness_saturation_boost)) },
                        supportingContent = {
                            Text(stringResource(R.string.brightness_saturation_boost_desc))
                        },
                        trailingContent = {
                            Switch(
                                checked = s.saturationBoostEnabled,
                                onCheckedChange = { on ->
                                    scope.launch { prefs.setSaturationBoostEnabled(on) }
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_keep_screen_on)) },
                        supportingContent = {
                            Text(stringResource(R.string.settings_keep_screen_on_desc))
                        },
                        trailingContent = {
                            Switch(
                                checked = s.keepScreenOn,
                                onCheckedChange = { on ->
                                    scope.launch { prefs.setKeepScreenOn(on) }
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                }
            }

            SettingsSectionCard(title = stringResource(R.string.settings_section_preview_brightness_help)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.settings_preview_brightness_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.settings_preview_brightness_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
                Text(
                    stringResource(
                        R.string.display_cap_summary_gainmap,
                        displayCap.peakLuminanceNits.roundToInt(),
                        displayCap.maxAverageLuminanceNits.roundToInt(),
                        DisplayCapabilityInfo.DEFAULT_SDR_WHITE_NITS.roundToInt(),
                        String.format("%.2f", displayCap.maxHeadroomRatio),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SettingsSectionCard(title = stringResource(R.string.settings_section_system_brightness)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            if (canWriteSystemSettings) {
                                stringResource(R.string.settings_write_settings_granted)
                            } else {
                                stringResource(R.string.settings_write_settings_denied)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(R.string.settings_write_settings_detail),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            onClick = ::openManageWriteSettings,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(stringResource(R.string.settings_open_write_settings))
                        }
                    }
                }
            }

            SettingsSectionCard(title = stringResource(R.string.settings_section_gallery)) {
                if (s != null) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_default_sort)) },
                        supportingContent = {
                            Text(sortOrderLabel(s.sortOrder))
                        },
                        modifier = Modifier.padding(vertical = 2.dp),
                        trailingContent = {
                            TextButton(onClick = { sortDialog = true }) {
                                Text(stringResource(R.string.settings_change))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_grid_density)) },
                        supportingContent = {
                            Text(stringResource(R.string.settings_grid_density_fmt, s.gridMinSizeDp))
                        },
                        trailingContent = {
                            TextButton(onClick = { gridDialog = true }) {
                                Text(stringResource(R.string.settings_change))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                }
            }

            SettingsSectionCard(title = stringResource(R.string.settings_section_about)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_version)) },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )
            }
        }
    }

    if (sortDialog) {
        AlertDialog(
            onDismissRequest = { sortDialog = false },
            title = { Text(stringResource(R.string.settings_default_sort)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        GallerySortOrder.NEWEST_FIRST,
                        GallerySortOrder.OLDEST_FIRST,
                        GallerySortOrder.NAME_ASC,
                    ).forEach { order ->
                        TextButton(
                            onClick = {
                                scope.launch {
                                    prefs.setSortOrder(order)
                                    sortDialog = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(sortOrderLabel(order))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { sortDialog = false }) {
                    Text(stringResource(R.string.detail_close))
                }
            },
        )
    }

    if (gridDialog) {
        val options = listOf(96, 108, 120, 144)
        AlertDialog(
            onDismissRequest = { gridDialog = false },
            title = { Text(stringResource(R.string.settings_grid_density)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    options.forEach { dp ->
                        TextButton(
                            onClick = {
                                scope.launch { prefs.setGridMinSizeDp(dp) }
                                gridDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.settings_grid_density_fmt, dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { gridDialog = false }) {
                    Text(stringResource(R.string.detail_close))
                }
            },
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
            content()
        }
    }
}

@Composable
private fun sortOrderLabel(order: GallerySortOrder): String = stringResource(
    when (order) {
        GallerySortOrder.NEWEST_FIRST -> R.string.sort_newest
        GallerySortOrder.OLDEST_FIRST -> R.string.sort_oldest
        GallerySortOrder.NAME_ASC -> R.string.sort_name
    },
)
