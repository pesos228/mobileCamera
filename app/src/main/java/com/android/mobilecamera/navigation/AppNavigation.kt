package com.android.mobilecamera.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.android.mobilecamera.feature.camera.CameraRoute
import com.android.mobilecamera.feature.gallery.GalleryRoute
import com.android.mobilecamera.feature.viewer.MediaViewerRoute


@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Camera.route
    ) {

        composable(Screen.Camera.route) {
            CameraRoute(
                onNavigateToGallery = {
                    navController.navigate(Screen.Gallery.route)
                }
            )
        }

        composable(Screen.Gallery.route) {
            GalleryRoute(
                onNavigateToViewer = { mediaId ->
                    navController.currentBackStackEntry?.let {
                        navController.navigate(Screen.Viewer.createRoute(mediaId)) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }

        composable(
            route = Screen.Viewer.route,
            arguments = listOf(navArgument("mediaId") { type = NavType.IntType })
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getInt("mediaId") ?: 0
            MediaViewerRoute(mediaId = mediaId, onNavigateBack = {navController.popBackStack()})
        }
    }
}