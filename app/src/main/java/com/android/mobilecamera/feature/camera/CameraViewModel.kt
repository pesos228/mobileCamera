package com.android.mobilecamera.feature.camera

import android.app.Application
import android.content.Context
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.mobilecamera.data.database.AppDatabase
import com.android.mobilecamera.data.database.MediaType
import com.android.mobilecamera.data.repository.MediaRepository
import com.android.mobilecamera.infrastructure.camera.CameraManager
import com.android.mobilecamera.infrastructure.media.MediaManager
import com.android.mobilecamera.infrastructure.media.ThumbnailGenerator
import com.android.mobilecamera.infrastructure.permissions.PermissionManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CameraUiState(
    val isVideoMode: Boolean = false,
    val isRecording: Boolean = false,
    val recordingDuration: Long = 0L,
    val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val showFlashAnimation: Boolean = false,
    val isTorchOn: Boolean = false,
    val isCameraAvailable: Boolean = true,
    val aspectRatio: Int = AspectRatio.RATIO_4_3,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK
)

sealed class CameraEvent {
    data class ShowToast(val message: String) : CameraEvent()
}

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val mediaManager = MediaManager(context)
    private val repository = MediaRepository(AppDatabase.getDatabase(context).mediaDao(), mediaManager)
    private val permissionManager = PermissionManager(context)
    private val cameraManager = CameraManager(context, mediaManager)

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = Channel<CameraEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            cameraManager.torchState.collect { isTorchOn ->
                _uiState.update { it.copy(isTorchOn = isTorchOn) }
            }
        }
    }

    fun bindCamera(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        cameraManager.bindCameraUseCases(
            lifecycleOwner = lifecycleOwner,
            surfaceProvider = surfaceProvider,
            isVideoMode = _uiState.value.isVideoMode,
            onCameraInitialized = { _, _ ->
                _uiState.update { it.copy(isCameraAvailable = true) }
            },
            onError = { e -> onCameraInitError(e) }
        )
    }

    fun onCaptureClick() {
        if (_uiState.value.isVideoMode) toggleVideoRecording() else takePhoto()
    }

    private fun takePhoto() {
        viewModelScope.launch {
            _uiState.update { it.copy(showFlashAnimation = true) }
            delay(100)
            _uiState.update { it.copy(showFlashAnimation = false) }

            try {
                val uri = cameraManager.takePhoto()
                val thumb = ThumbnailGenerator.generateForPhoto(context, uri.toString())
                repository.saveMedia(uri.toString(), MediaType.PHOTO, thumbnailPath = thumb)
                sendToast("Фото сохранено")
            } catch (e: Exception) {
                sendToast("Ошибка фото: ${e.message}")
            }
        }
    }

    private fun toggleVideoRecording() {
        if (_uiState.value.isRecording) {
            cameraManager.stopVideoRecording()
            _uiState.update { it.copy(isRecording = false) }
        } else {
            val hasAudio = permissionManager.hasAllPermissions()
            _uiState.update { it.copy(recordingDuration = 0L) }

            val recording = cameraManager.startVideoRecording(
                withAudio = hasAudio,
                onVideoSaved = { uri, duration ->
                    viewModelScope.launch {
                        val thumb = ThumbnailGenerator.generateForVideo(context, uri.toString())
                        repository.saveMedia(uri.toString(), MediaType.VIDEO, duration, thumbnailPath = thumb)
                        sendToast("Видео сохранено")
                    }
                },
                onError = { msg -> sendToast(msg) }
            )

            if (recording != null) {
                _uiState.update { it.copy(isRecording = true) }
                startTimer()
            }
        }
    }

    fun toggleCameraMode() {
        if (_uiState.value.isRecording) return
        val newMode = !_uiState.value.isVideoMode
        _uiState.update { it.copy(isVideoMode = newMode) }
    }

    fun switchCamera() {
        val newLens = cameraManager.toggleCameraLens()
        _uiState.update { it.copy(lensFacing = newLens) }
    }

    fun setAspectRatio(ratio: Int) {
        if (_uiState.value.isRecording) {
            sendToast("Нельзя менять формат при записи")
            return
        }
        cameraManager.setAspectRatio(ratio)
        _uiState.update { it.copy(aspectRatio = ratio) }
    }

    fun toggleFlash() {
        if (_uiState.value.isVideoMode) {
            val newState = !_uiState.value.isTorchOn
            cameraManager.toggleTorch(newState)
        } else {
            val newMode = if (_uiState.value.flashMode == ImageCapture.FLASH_MODE_OFF)
                ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
            cameraManager.setFlashMode(newMode)
            _uiState.update { it.copy(flashMode = newMode) }
        }
    }

    fun onZoomEvent(scaleFactor: Float) {
        cameraManager.setZoom(scaleFactor)
    }

    fun onTapToFocus(point: MeteringPoint) {
        cameraManager.setFocus(point)
    }

    private fun startTimer() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            while (_uiState.value.isRecording) {
                delay(100)
                val elapsed = System.currentTimeMillis() - startTime
                _uiState.update { it.copy(recordingDuration = elapsed) }
            }
        }
    }

    private fun sendToast(msg: String) {
        viewModelScope.launch { _events.send(CameraEvent.ShowToast(msg)) }
    }

    override fun onCleared() {
        super.onCleared()
        cameraManager.cleanup()
    }

    fun onCameraInitError(e: Exception) {
        _uiState.update { it.copy(isCameraAvailable = false) }
        sendToast("Ошибка инициализации камеры: ${e.message}")
    }
}

class CameraViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return CameraViewModel(application) as T
    }
}