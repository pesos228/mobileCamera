package com.android.mobilecamera.feature.gallery

import android.app.Activity
import android.app.Application
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.mobilecamera.feature.gallery.ui.GalleryContent
import com.android.mobilecamera.R

@Composable
private fun formatMessage(message: GalleryMessage): String {
    return when (message) {
        is GalleryMessage.DeleteSuccess -> {
            pluralStringResource(
                R.plurals.deleted_files,
                message.count,
                message.count
            )
        }
        is GalleryMessage.SimpleMessage -> {
            stringResource(message.resId)
        }
    }
}

@Composable
fun GalleryRoute(
    onNavigateToViewer: (Int) -> Unit,
    viewModel: GalleryViewModel = viewModel(
        factory = GalleryViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val pendingDeleteRequest by viewModel.pendingDeleteRequest.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val errorPermissionText = stringResource(R.string.msg_permission_request_error)
    val currentMessage = uiState.message?.let { formatMessage(it) }

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
                    errorPermissionText,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(currentMessage) {
        currentMessage?.let { text ->
            snackbarHostState.showSnackbar(
                message = text,
                duration = SnackbarDuration.Short,
                withDismissAction = true
            )
            viewModel.clearMessage()
        }
    }

    GalleryContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
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