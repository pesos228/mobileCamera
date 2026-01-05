package com.android.mobilecamera.feature.camera.ui

import android.annotation.SuppressLint
import androidx.camera.core.*
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.android.mobilecamera.feature.camera.CameraUiState

@Composable
fun CameraScreenContent(
    uiState: CameraUiState,
    onCapture: () -> Unit,
    onSwitchMode: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleFlash: () -> Unit,
    onControllerCreated: (
        provider: androidx.camera.lifecycle.ProcessCameraProvider,
        previewView: androidx.camera.view.PreviewView,
        lifecycleOwner: LifecycleOwner
    ) -> Unit,
    hasPermission: Boolean,
    onPermissionRequest: () -> Unit,
    onNavigateToGallery: () -> Unit,
    showRationaleDialog: Boolean,
    onRationaleDismiss: () -> Unit,
    onRationaleConfirm: () -> Unit,
) {
    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = onRationaleDismiss,
            title = { Text("Требуется разрешение") },
            text = { Text("Для работы камеры необходимо дать разрешение на использование камеры и микрофона.") },
            confirmButton = {
                Button(onClick = onRationaleConfirm) {
                    Text("Дать разрешение")
                }
            },
            dismissButton = {
                TextButton(onClick = onRationaleDismiss) {
                    Text("Отмена")
                }
            }
        )
    }

    if (!hasPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = onPermissionRequest) {
                Text("Дать доступ к камере")
            }
        }
        return
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val density = LocalDensity.current

    var focusPoint by remember { mutableStateOf<Offset?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Превью камеры с жестами
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(uiState.cameraControl) {
                    detectTapGestures { offset ->
                        val cameraControl = uiState.cameraControl ?: return@detectTapGestures

                        val factory = SurfaceOrientedMeteringPointFactory(
                            size.width.toFloat(),
                            size.height.toFloat()
                        )
                        val point = factory.createPoint(offset.x, offset.y)

                        val action = FocusMeteringAction.Builder(point)
                            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                            .build()

                        cameraControl.startFocusAndMetering(action)
                        focusPoint = offset
                    }
                }
                .pointerInput(uiState.cameraControl, uiState.cameraInfo) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val cameraControl = uiState.cameraControl ?: return@detectTransformGestures
                        val cameraInfo = uiState.cameraInfo ?: return@detectTransformGestures

                        val currentZoom = cameraInfo.zoomState.value?.zoomRatio ?: 1f
                        val maxZoom = cameraInfo.zoomState.value?.maxZoomRatio ?: 10f
                        val minZoom = cameraInfo.zoomState.value?.minZoomRatio ?: 1f

                        val newZoom = (currentZoom * zoom).coerceIn(minZoom, maxZoom)
                        cameraControl.setZoomRatio(newZoom)
                    }
                },
            factory = { ctx ->
                val previewView = androidx.camera.view.PreviewView(ctx).apply {
                    scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    onControllerCreated(cameraProvider, previewView, lifecycleOwner)
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // Индикатор фокуса
        focusPoint?.let { point ->
            LaunchedEffect(point) {
                kotlinx.coroutines.delay(1500)
                focusPoint = null
            }

            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { point.x.toDp() - 40.dp },
                        y = with(density) { point.y.toDp() - 40.dp }
                    )
                    .size(80.dp)
                    .border(2.dp, Color.Yellow, CircleShape)
            )
        }

        // АНИМАЦИЯ ВСПЫШКИ
        AnimatedVisibility(
            visible = uiState.showFlashAnimation,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
        }

        // Индикатор записи
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
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color.White, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDuration(uiState.recordingDuration),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // ========== ОБНОВЛЕННАЯ КНОПКА ВСПЫШКИ ==========
        // Показываем всегда (и для фото, и для видео)
        IconButton(
            onClick = onToggleFlash,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 16.dp)
        ) {
            val flashIcon = when {
                // Видео режим: показываем состояние фонарика
                uiState.isVideoMode -> {
                    if (uiState.isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff
                }
                // Фото режим: показываем flash mode
                else -> {
                    if (uiState.flashMode == ImageCapture.FLASH_MODE_OFF) {
                        Icons.Default.FlashOff
                    } else {
                        Icons.Default.FlashOn
                    }
                }
            }

            Icon(
                imageVector = flashIcon,
                contentDescription = "Flash",
                tint = Color.White
            )
        }

        // Нижняя панель
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
                // Галерея
                IconButton(onClick = onNavigateToGallery, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = "Gallery",
                        tint = Color.White,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Кнопка затвора
                CaptureButton(
                    isRecording = uiState.isRecording,
                    isVideoMode = uiState.isVideoMode,
                    onClick = onCapture
                )

                // Смена камеры
                IconButton(onClick = onSwitchCamera, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.Cameraswitch,
                        contentDescription = "Switch",
                        tint = Color.White,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Переключатель режимов
            if (!uiState.isRecording) {
                Row(
                    modifier = Modifier
                        .background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)
                        .padding(4.dp)
                ) {
                    ModeButton(
                        text = "ФОТО",
                        isSelected = !uiState.isVideoMode,
                        icon = Icons.Default.PhotoCamera,
                        onClick = { if (uiState.isVideoMode) onSwitchMode() }
                    )
                    ModeButton(
                        text = "ВИДЕО",
                        isSelected = uiState.isVideoMode,
                        icon = Icons.Default.Videocam,
                        onClick = { if (!uiState.isVideoMode) onSwitchMode() }
                    )
                }
            }
        }
    }
}

@Composable
fun CaptureButton(
    isRecording: Boolean,
    isVideoMode: Boolean,
    onClick: () -> Unit
) {
    val color by animateColorAsState(
        if (isVideoMode) Color.Red else Color.White,
        label = "color"
    )
    val outerScale by animateFloatAsState(
        if (isRecording) 1.2f else 1f,
        label = "scale"
    )
    val innerShape = if (isRecording) MaterialTheme.shapes.small else CircleShape
    val innerSize = if (isRecording) 30.dp else 60.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.scale(outerScale)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .border(4.dp, Color.White, CircleShape)
        )
        Button(
            onClick = onClick,
            shape = innerShape,
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = Modifier.size(innerSize),
            contentPadding = PaddingValues(0.dp)
        ) {}
    }
}

@Composable
fun ModeButton(
    text: String,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) Color.Black.copy(alpha = 0.5f) else Color.Transparent
    val contentColor = if (isSelected) Color.Yellow else Color.White

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        shape = CircleShape
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@SuppressLint("DefaultLocale")
private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}