package com.android.mobilecamera.feature.viewer

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.mobilecamera.feature.viewer.ui.MediaViewerContent

@Composable
fun MediaViewerRoute(
    mediaId: Int,
    onNavigateBack: () -> Unit = {},
    viewModel: MediaViewerViewModel = viewModel(
        factory = MediaViewerViewModelFactory(
            LocalContext.current.applicationContext as Application,
            mediaId
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    MediaViewerContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack
    )
}