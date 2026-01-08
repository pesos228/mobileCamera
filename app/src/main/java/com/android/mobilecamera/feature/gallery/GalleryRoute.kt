package com.android.mobilecamera.feature.gallery

import android.app.Activity
import android.app.Application
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
    val pendingDeleteRequest by viewModel.pendingDeleteRequest.collectAsState()
    val context = LocalContext.current

    val deletePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDeletePermissionResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(pendingDeleteRequest) {
        pendingDeleteRequest?.let { intentSender ->
            try {
                deletePermissionLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build()
                )
            } catch (_: Exception) {
                viewModel.onDeletePermissionResult(false)
                Toast.makeText(
                    context,
                    "Ошибка запроса разрешения",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(uiState.deleteMessage) {
        uiState.deleteMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearDeleteMessage()
        }
    }

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
        onClearSelection = { viewModel.clearSelection() },
    )
}