package com.android.mobilecamera.feature.camera.ui

import android.annotation.SuppressLint
import androidx.camera.core.*
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.mobilecamera.feature.camera.CameraUiState
import com.android.mobilecamera.feature.camera.ui.components.AspectRatioButton
import com.android.mobilecamera.feature.camera.ui.components.CaptureButton
import com.android.mobilecamera.feature.camera.ui.components.ModeButton

@Composable
fun CameraScreenContent(
    uiState: CameraUiState,
    onCapture: () -> Unit,
    onSwitchMode: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleFlash: () -> Unit,
    onAspectRatioChange: (Int) -> Unit,
    onBindCamera: (Preview.SurfaceProvider) -> Unit,
    hasPermission: Boolean,
    onPermissionRequest: () -> Unit,
    onNavigateToGallery: () -> Unit,
    showRationaleDialog: Boolean,
    onRationaleDismiss: () -> Unit,
    onRationaleConfirm: () -> Unit,
    onTapToFocus: (MeteringPoint) -> Unit,
    onZoomChange: (Float) -> Unit,
) {
    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = onRationaleDismiss,
            title = { Text("Требуется разрешение") },
            text = { Text("Для работы камеры необходимо дать разрешение на использование камеры и микрофона.") },
            confirmButton = { Button(onClick = onRationaleConfirm) { Text("Дать разрешение") } },
            dismissButton = { TextButton(onClick = onRationaleDismiss) { Text("Отмена") } }
        )
    }

    if (!uiState.isCameraAvailable) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Камера недоступна", color = Color.White)
        }
        return
    }

    if (!hasPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = onPermissionRequest) { Text("Дать доступ к камере") }
        }
        return
    }

    val density = LocalDensity.current
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var surfaceProvider by remember { mutableStateOf<Preview.SurfaceProvider?>(null) }

    // Реактивный биндинг камеры при изменении параметров
    LaunchedEffect(
        surfaceProvider,
        uiState.isVideoMode,
        uiState.lensFacing,
        uiState.aspectRatio
    ) {
        surfaceProvider?.let { provider ->
            onBindCamera(provider)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    onZoomChange(zoom)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    previewViewRef?.let { view ->
                        val factory = view.meteringPointFactory
                        val point = factory.createPoint(offset.x, offset.y)
                        onTapToFocus(point)
                        focusPoint = offset
                    }
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    previewViewRef = this
                    surfaceProvider = this.surfaceProvider
                }
            }
        )

        focusPoint?.let { point ->
            LaunchedEffect(point) {
                kotlinx.coroutines.delay(1500)
                focusPoint = null
            }
            Box(
                modifier = Modifier
                    .offset(x = with(density) { point.x.toDp() - 40.dp }, y = with(density) { point.y.toDp() - 40.dp })
                    .size(80.dp)
                    .border(2.dp, Color.Yellow, CircleShape)
            )
        }

        AnimatedVisibility(
            visible = uiState.showFlashAnimation,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White))
        }

        if (uiState.isRecording) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
                    .background(Color.Red.copy(alpha = 0.7f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color.White, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(formatDuration(uiState.recordingDuration), color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        IconButton(
            onClick = onToggleFlash,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 16.dp, start = 16.dp)
        ) {
            val flashIcon = when {
                uiState.isVideoMode -> if (uiState.isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff
                else -> if (uiState.flashMode == ImageCapture.FLASH_MODE_OFF) Icons.Default.FlashOff else Icons.Default.FlashOn
            }
            Icon(flashIcon, contentDescription = "Flash", tint = Color.White)
        }

        if (!uiState.isRecording) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AspectRatioButton("4:3", uiState.aspectRatio == AspectRatio.RATIO_4_3) { onAspectRatioChange(AspectRatio.RATIO_4_3) }
                AspectRatioButton("16:9", uiState.aspectRatio == AspectRatio.RATIO_16_9) { onAspectRatioChange(AspectRatio.RATIO_16_9) }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(bottom = 32.dp, top = 16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onNavigateToGallery, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color.White, modifier = Modifier.fillMaxSize())
                }
                CaptureButton(isRecording = uiState.isRecording, isVideoMode = uiState.isVideoMode, onClick = onCapture)
                IconButton(onClick = onSwitchCamera, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Cameraswitch, contentDescription = "Switch", tint = Color.White, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            if (!uiState.isRecording) {
                Row(
                    modifier = Modifier.background(Color.DarkGray.copy(alpha = 0.5f), CircleShape).padding(4.dp)
                ) {
                    ModeButton("ФОТО", !uiState.isVideoMode, Icons.Default.PhotoCamera) { if (uiState.isVideoMode) onSwitchMode() }
                    ModeButton("ВИДЕО", uiState.isVideoMode, Icons.Default.Videocam) { if (!uiState.isVideoMode) onSwitchMode() }
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}