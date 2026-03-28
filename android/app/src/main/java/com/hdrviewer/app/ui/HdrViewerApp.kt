package com.hdrviewer.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.hdrviewer.app.R
import com.hdrviewer.app.ui.navigation.AppDestinations
import com.hdrviewer.app.ui.navigation.MainNavHost

private const val PREFS_NAME = "hdrviewer_prefs"
private const val KEY_WRITE_SETTINGS_PROMPT_DONE = "write_settings_prompt_done"

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HdrViewerApp() {
    val perms = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        ),
    )

    LaunchedEffect(Unit) {
        if (!perms.allPermissionsGranted) {
            perms.launchMultiplePermissionRequest()
        }
    }

    val appContext = LocalContext.current.applicationContext as android.app.Application
    val vm: GalleryViewModel = viewModel(factory = GalleryViewModelFactory(appContext))
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route.orEmpty()

    val onViewerLikeRoute = currentRoute.startsWith("${AppDestinations.VIEWER}/")
    val onAlbumDetailRoute = currentRoute.startsWith("${AppDestinations.ALBUM_DETAIL}/")
    val showBottomBar = !onViewerLikeRoute && !onAlbumDetailRoute

    val lifecycleOwner = LocalLifecycleOwner.current
    var showWriteSettingsDialog by remember { mutableStateOf(false) }

    fun prefs() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun markWriteSettingsPromptDone() {
        prefs().edit().putBoolean(KEY_WRITE_SETTINGS_PROMPT_DONE, true).apply()
    }

    fun openWriteSettingsScreen() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${appContext.packageName}")
        }
        runCatching { appContext.startActivity(intent) }
    }

    LaunchedEffect(perms.allPermissionsGranted) {
        if (!perms.allPermissionsGranted) return@LaunchedEffect
        if (Settings.System.canWrite(appContext)) {
            markWriteSettingsPromptDone()
            showWriteSettingsDialog = false
        } else if (!prefs().getBoolean(KEY_WRITE_SETTINGS_PROMPT_DONE, false)) {
            showWriteSettingsDialog = true
        }
    }

    DisposableEffect(lifecycleOwner, perms.allPermissionsGranted) {
        if (!perms.allPermissionsGranted) {
            return@DisposableEffect onDispose { }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            if (Settings.System.canWrite(appContext)) {
                markWriteSettingsPromptDone()
                showWriteSettingsDialog = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        ViewerBrightnessPolicyEffects(isViewerRoute = onViewerLikeRoute)
        GlobalHdrHeadroomForGallery(active = !onViewerLikeRoute)

        when {
            !perms.allPermissionsGranted -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(24.dp),
                    ) {
                        Text(
                            stringResource(R.string.grant_permission),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(
                            onClick = { perms.launchMultiplePermissionRequest() },
                            modifier = Modifier.padding(top = 16.dp),
                        ) { Text(stringResource(R.string.request_perm)) }
                        val context = LocalContext.current
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.padding(top = 8.dp),
                        ) { Text(stringResource(R.string.open_settings)) }
                    }
                }
            }

            else -> {
                Scaffold(
                    contentWindowInsets = WindowInsets(0.dp),
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                /** 勿用 [androidx.navigation.NavController.graph] 的 startDestinationId：底栏与 [NavHost] 同帧测量时 graph 可能尚未就绪，会触发崩溃。 */
                                val tabOpts = navOptions {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(AppDestinations.PHOTOS) {
                                        saveState = true
                                    }
                                }
                                NavigationBarItem(
                                    icon = { Icon(Icons.Filled.Photo, contentDescription = null) },
                                    label = { Text(stringResource(R.string.nav_photos)) },
                                    selected = currentRoute == AppDestinations.PHOTOS,
                                    onClick = {
                                        navController.navigate(AppDestinations.PHOTOS, tabOpts)
                                    },
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Filled.PhotoAlbum, contentDescription = null) },
                                    label = { Text(stringResource(R.string.nav_albums)) },
                                    selected = currentRoute == AppDestinations.ALBUMS,
                                    onClick = {
                                        navController.navigate(AppDestinations.ALBUMS, tabOpts)
                                    },
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                    label = { Text(stringResource(R.string.nav_settings)) },
                                    selected = currentRoute == AppDestinations.SETTINGS,
                                    onClick = {
                                        navController.navigate(AppDestinations.SETTINGS, tabOpts)
                                    },
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    MainNavHost(
                        navController = navController,
                        vm = vm,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }
            }
        }

        if (showWriteSettingsDialog && perms.allPermissionsGranted && showBottomBar) {
            AlertDialog(
                onDismissRequest = {
                    markWriteSettingsPromptDone()
                    showWriteSettingsDialog = false
                },
                title = { Text(stringResource(R.string.write_settings_prompt_title)) },
                text = { Text(stringResource(R.string.write_settings_hint)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            openWriteSettingsScreen()
                            markWriteSettingsPromptDone()
                            showWriteSettingsDialog = false
                        },
                    ) { Text(stringResource(R.string.grant_write_settings)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            markWriteSettingsPromptDone()
                            showWriteSettingsDialog = false
                        },
                    ) { Text(stringResource(R.string.write_settings_later)) }
                },
            )
        }
    }
}
