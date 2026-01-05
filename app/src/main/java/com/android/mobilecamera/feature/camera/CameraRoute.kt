package com.android.mobilecamera.feature.camera

import android.app.Activity
import android.app.Application
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.mobilecamera.feature.camera.ui.CameraScreenContent

@Composable
fun CameraRoute(
    onNavigateToGallery: () -> Unit,
    viewModel: CameraViewModel = viewModel(
        factory = CameraViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsState()

    var hasPermission by remember { mutableStateOf(context.hasCameraAccess()) }
    var showRationaleDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CameraEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            hasPermission = true
        } else {
            Toast.makeText(context, "Нужны права для работы камеры", Toast.LENGTH_SHORT).show()
        }
    }

    fun checkAndRequestPermissions() {
        if (activity == null) return
        val shouldShowRationale = requiredCameraPermissions.any {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
        if (shouldShowRationale) {
            showRationaleDialog = true
        } else {
            permissionLauncher.launch(requiredCameraPermissions)
        }
    }

    LaunchedEffect(Unit) {
        if (!context.hasCameraAccess()) {
            checkAndRequestPermissions()
        }
    }

    CameraScreenContent(
        uiState = uiState,
        hasPermission = hasPermission,
        onPermissionRequest = { checkAndRequestPermissions() },
        showRationaleDialog = showRationaleDialog,
        onRationaleDismiss = { showRationaleDialog = false },
        onRationaleConfirm = {
            showRationaleDialog = false
            permissionLauncher.launch(requiredCameraPermissions)
        },
        onCapture = { viewModel.onCaptureClick() },
        onSwitchMode = { viewModel.toggleCameraMode() },
        onSwitchCamera = { viewModel.switchCamera() },
        onToggleFlash = { viewModel.toggleFlash() },
        onAspectRatioChange = { viewModel.setAspectRatio(it) },
        onControllerCreated = { provider, previewView, owner ->
            // ========== ПЕРЕДАЕМ CALLBACK ==========
            viewModel.bindCamera(
                provider = provider,
                onSetupPreview = { preview ->
                    preview.surfaceProvider = previewView.surfaceProvider
                },
                owner = owner
            )
        },
        onNavigateToGallery = onNavigateToGallery,
        onCameraInitError = {viewModel.onCameraInitError(it)}
    )
}