package com.android.mobilecamera.feature.camera.ui

import android.annotation.SuppressLint
import androidx.camera.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
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
    onCameraInitError: (Exception) -> Unit,
    onTapToFocus: (MeteringPoint) -> Unit,
    onZoomChange: (Float) -> Unit,
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

    if (!uiState.isCameraAvailable) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Камера недоступна на этом устройстве",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        return
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
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        onZoomChange(zoom)
                    }
                },
            factory = { ctx ->
                val previewView = androidx.camera.view.PreviewView(ctx).apply {
                    // FIT_CENTER: Картинка целиком, возможны черные полосы. НЕ РАСТЯНУТА.
                    scaleType = androidx.camera.view.PreviewView.ScaleType.FIT_CENTER

                    // 2. Ловим нажатие (Tap) на уровне Android View
                    setOnTouchListener { view, event ->
                        if (event.action == android.view.MotionEvent.ACTION_UP) {
                            val v = view as androidx.camera.view.PreviewView

                            // МАГИЯ: Фабрика сама учитывает FIT_CENTER и черные полосы
                            val factory = v.meteringPointFactory
                            val point = factory.createPoint(event.x, event.y)

                            // Вызываем колбэк, который уйдет во ViewModel
                            onTapToFocus(point)

                            // Сохраняем координату просто чтобы нарисовать кружок на экране
                            focusPoint = Offset(event.x, event.y)

                            view.performClick()
                        }
                        return@setOnTouchListener true
                    }
                }
                val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        onControllerCreated(cameraProvider, previewView, lifecycleOwner)
                    } catch (e: Exception) {
                        onCameraInitError(e)
                    }
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

        // Вспышка
        IconButton(
            onClick = onToggleFlash,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 16.dp)
        ) {
            val flashIcon = when {
                uiState.isVideoMode -> {
                    if (uiState.isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff
                }
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

        // ========== КНОПКИ СООТНОШЕНИЯ СТОРОН ==========
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
                AspectRatioButton(
                    text = "4:3",
                    isSelected = uiState.aspectRatio == AspectRatio.RATIO_4_3,
                    onClick = { onAspectRatioChange(AspectRatio.RATIO_4_3) }
                )
                AspectRatioButton(
                    text = "16:9",
                    isSelected = uiState.aspectRatio == AspectRatio.RATIO_16_9,
                    onClick = { onAspectRatioChange(AspectRatio.RATIO_16_9) }
                )
            }
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


@SuppressLint("DefaultLocale")
private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}