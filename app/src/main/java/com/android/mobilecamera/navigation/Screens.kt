package com.android.mobilecamera.navigation

sealed class Screen(val route: String) {
    data object Camera : Screen("camera")
    data object Gallery : Screen("gallery")
    data object Viewer : Screen("viewer/{mediaId}") {
        fun createRoute(mediaId: Int) = "viewer/$mediaId"
    }
}