package com.android.mobilecamera.feature.camera

import android.app.Activity
import android.app.Application
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.mobilecamera.R
import com.android.mobilecamera.feature.camera.ui.CameraScreenContent
import com.android.mobilecamera.infrastructure.permissions.PermissionManager

@Composable
private fun formatCameraMessage(message: CameraMessage): String {
    return when (message) {
        is CameraMessage.ResourceError -> stringResource(message.resId)
    }
}

@Composable
fun CameraRoute(
    onNavigateToGallery: () -> Unit,
    viewModel: CameraViewModel = viewModel(
        factory = CameraViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionManager = remember { PermissionManager(context) }
    val uiState by viewModel.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val currentMessage = uiState.message?.let { formatCameraMessage(it) }

    var hasPermission by remember { mutableStateOf(permissionManager.hasAllPermissions()) }
    var showRationaleDialog by remember { mutableStateOf(false) }

    val permissionNeededText = stringResource(R.string.msg_permissions_required)

    val flashMode by viewModel.flashMode.collectAsState()
    val isTorchOn by viewModel.torchState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            hasPermission = true
        } else {
            Toast.makeText(context, permissionNeededText, Toast.LENGTH_SHORT).show()
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

    fun checkAndRequestPermissions() {
        if (activity == null) return

        val requiredPermissions = permissionManager.getRequiredPermissions()

        val shouldShowRationale = requiredPermissions.any {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }

        if (shouldShowRationale) {
            showRationaleDialog = true
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionManager.hasAllPermissions()) {
            checkAndRequestPermissions()
        }
    }

    CameraScreenContent(
        uiState = uiState,
        flashMode = flashMode,
        isTorchOn = isTorchOn,
        snackbarHostState = snackbarHostState,
        hasPermission = hasPermission,
        onPermissionRequest = { checkAndRequestPermissions() },
        showRationaleDialog = showRationaleDialog,
        onRationaleDismiss = { showRationaleDialog = false },
        onRationaleConfirm = {
            showRationaleDialog = false
            permissionLauncher.launch(permissionManager.getRequiredPermissions())
        },
        onCapture = { viewModel.onCaptureClick() },
        onSwitchMode = { viewModel.toggleCameraMode() },
        onSwitchCamera = { viewModel.switchCamera() },
        onToggleFlash = { viewModel.toggleFlash() },
        onAspectRatioChange = { viewModel.setAspectRatio(it) },
        onNavigateToGallery = onNavigateToGallery,
        onTapToFocus = { point -> viewModel.onTapToFocus(point) },
        onZoomChange = { zoom -> viewModel.onZoomEvent(zoom) },
        onBindCamera = { surfaceProvider ->
            viewModel.bindCamera(lifecycleOwner, surfaceProvider)
        },
    )
}