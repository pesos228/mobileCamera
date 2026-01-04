package com.android.mobilecamera.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.mobilecamera.feature.camera.CameraRoute
import com.android.mobilecamera.feature.gallery.GalleryRoute


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
            GalleryRoute()
        }
    }
}