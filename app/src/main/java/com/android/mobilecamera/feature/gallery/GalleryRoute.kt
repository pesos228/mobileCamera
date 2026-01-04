package com.android.mobilecamera.feature.gallery

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.mobilecamera.feature.gallery.ui.GalleryContent

@Composable
fun GalleryRoute(
    onNavigateToViewer: (Int) -> Unit,
    viewModel: GalleryViewModel = viewModel(
        factory = GalleryViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    GalleryContent(
        uiState = uiState,
        onMediaClick = { itemId ->
            if (uiState.isSelectionMode) {
                viewModel.toggleSelection(itemId)
            } else {
                onNavigateToViewer(itemId)
            }
        },
        onMediaLongClick = { itemId ->
            if (!uiState.isSelectionMode) {
                viewModel.startSelectionMode(itemId)
            }
        },
        onClearClick = { viewModel.clearAll() },
        onAddMockClick = { viewModel.createMocks() },
        onDeleteSelected = { viewModel.deleteSelected() },
        onClearSelection = { viewModel.clearSelection() }
    )
}