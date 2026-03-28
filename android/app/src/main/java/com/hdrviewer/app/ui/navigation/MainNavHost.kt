package com.hdrviewer.app.ui.navigation

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.hdrviewer.app.ui.AlbumsScreen
import com.hdrviewer.app.ui.GalleryScreen
import com.hdrviewer.app.ui.GalleryViewModel
import com.hdrviewer.app.ui.SettingsScreen
import com.hdrviewer.app.ui.ViewerScreen

private fun isMainTabRoute(route: String?): Boolean {
    if (route == null) return false
    return route == AppDestinations.PHOTOS ||
        route == AppDestinations.ALBUMS ||
        route == AppDestinations.SETTINGS
}

/** 底部栏三 Tab 之间切换：无过渡动画；进入预览/相册内页等仍保留滑动+淡入淡出。 */
private fun isTabToTabSwitch(initialRoute: String?, targetRoute: String?): Boolean =
    isMainTabRoute(initialRoute) && isMainTabRoute(targetRoute)

@Composable
fun MainNavHost(
    navController: NavHostController,
    vm: GalleryViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    NavHost(
        navController = navController,
        startDestination = AppDestinations.PHOTOS,
        modifier = modifier,
        enterTransition = {
            if (isTabToTabSwitch(initialState.destination.route, targetState.destination.route)) {
                EnterTransition.None
            } else {
                slideInHorizontally(animationSpec = tween(300), initialOffsetX = { it }) +
                    fadeIn(animationSpec = tween(300))
            }
        },
        exitTransition = {
            if (isTabToTabSwitch(initialState.destination.route, targetState.destination.route)) {
                ExitTransition.None
            } else {
                slideOutHorizontally(animationSpec = tween(300), targetOffsetX = { -it }) +
                    fadeOut(animationSpec = tween(300))
            }
        },
        popEnterTransition = {
            if (isTabToTabSwitch(initialState.destination.route, targetState.destination.route)) {
                EnterTransition.None
            } else {
                slideInHorizontally(animationSpec = tween(300), initialOffsetX = { -it }) +
                    fadeIn(animationSpec = tween(300))
            }
        },
        popExitTransition = {
            if (isTabToTabSwitch(initialState.destination.route, targetState.destination.route)) {
                ExitTransition.None
            } else {
                slideOutHorizontally(animationSpec = tween(300), targetOffsetX = { it }) +
                    fadeOut(animationSpec = tween(300))
            }
        },
    ) {
        composable(AppDestinations.PHOTOS) {
            LaunchedEffect(Unit) {
                vm.selectAlbum(context, null)
            }
            GalleryScreen(
                vm = vm,
                topBarTitle = null,
                onOpenViewer = { list, index ->
                    val item = list.getOrNull(index) ?: return@GalleryScreen
                    navController.navigate(AppDestinations.viewerRoute(item.storeKey))
                },
            )
        }
        composable(AppDestinations.ALBUMS) {
            AlbumsScreen(
                vm = vm,
                onOpenAlbum = { bucketId ->
                    navController.navigate(AppDestinations.albumDetailRoute(bucketId))
                },
            )
        }
        composable(AppDestinations.SETTINGS) {
            SettingsScreen()
        }
        composable(
            route = "${AppDestinations.ALBUM_DETAIL}/{bucketId}",
            arguments = listOf(
                navArgument("bucketId") { type = NavType.StringType },
            ),
        ) { entry ->
            val raw = entry.arguments?.getString("bucketId") ?: return@composable
            val bucketId = Uri.decode(raw)
            val title = vm.albums.find { it.bucketId == bucketId }?.displayName
            LaunchedEffect(bucketId) {
                vm.selectAlbum(context, bucketId)
            }
            GalleryScreen(
                vm = vm,
                topBarTitle = title,
                onOpenViewer = { list, index ->
                    val item = list.getOrNull(index) ?: return@GalleryScreen
                    navController.navigate(AppDestinations.viewerRoute(item.storeKey))
                },
            )
        }
        composable(
            route = "${AppDestinations.VIEWER}/{startKey}",
            arguments = listOf(
                navArgument("startKey") { type = NavType.StringType },
            ),
        ) { entry ->
            val raw = entry.arguments?.getString("startKey") ?: return@composable
            val startKey = Uri.decode(raw)
            ViewerScreen(
                vm = vm,
                startKey = startKey,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
