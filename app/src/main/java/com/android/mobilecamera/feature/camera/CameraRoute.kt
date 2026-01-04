package com.android.mobilecamera.screens.camera

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat

@Composable
fun CameraRoute(
    onNavigateToGallery: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var hasPermission by remember { mutableStateOf(context.hasCameraAccess()) }
    var showRationaleDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            hasPermission = true
        } else {
            Toast.makeText(context, "Нужны права :(", Toast.LENGTH_SHORT).show()
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
        if (!context.hasCameraAccess()) checkAndRequestPermissions()
    }

    CameraScreenContent(
        hasPermission = hasPermission,
        showRationaleDialog = showRationaleDialog,
        onPermissionRequest = { checkAndRequestPermissions() },
        onRationaleDismiss = { showRationaleDialog = false },
        onRationaleConfirm = {
            showRationaleDialog = false
            permissionLauncher.launch(requiredCameraPermissions)
        },
        onNavigateToGallery = onNavigateToGallery
    )
}