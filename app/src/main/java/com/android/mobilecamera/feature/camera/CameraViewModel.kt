package com.android.mobilecamera.feature.camera

import android.app.Application
import android.content.Context
import android.util.Log
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
import com.android.mobilecamera.R
import com.android.mobilecamera.data.database.AppDatabase
import com.android.mobilecamera.data.database.MediaType
import com.android.mobilecamera.data.repository.MediaRepository
import com.android.mobilecamera.infrastructure.camera.CameraManager
import com.android.mobilecamera.infrastructure.media.MediaManager
import com.android.mobilecamera.infrastructure.media.ThumbnailGenerator
import com.android.mobilecamera.infrastructure.permissions.PermissionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "CameraViewModel"

sealed interface CameraMessage {
    data class ResourceError(val resId: Int) : CameraMessage
}

data class CameraUiState(
    val isVideoMode: Boolean = false,
    val isRecording: Boolean = false,
    val recordingDuration: Long = 0L,
    val isCameraAvailable: Boolean = true,
    val aspectRatio: Int = AspectRatio.RATIO_4_3,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val message: CameraMessage? = null
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val mediaManager = MediaManager(context)
    private val repository = MediaRepository(AppDatabase.getDatabase(context).mediaDao(), mediaManager)
    private val permissionManager = PermissionManager(context)
    private val cameraManager = CameraManager(context, mediaManager)
    val flashMode = cameraManager.flashMode
    val torchState = cameraManager.torchState
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState = _uiState.asStateFlow()

    fun bindCamera(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        cameraManager.bindCameraUseCases(
            lifecycleOwner = lifecycleOwner,
            surfaceProvider = surfaceProvider,
            isVideoMode = _uiState.value.isVideoMode,
            onCameraInitialized = { _, _ ->
                _uiState.update { it.copy(isCameraAvailable = true) }
            },
            onError = { e ->
                Log.e(TAG, "Camera initialization failed", e)
                onCameraInitError()
            }
        )
    }

    fun onCaptureClick() {
        if (_uiState.value.isVideoMode) toggleVideoRecording() else takePhoto()
    }

    private fun takePhoto() {
        viewModelScope.launch {
            try {
                val uri = cameraManager.takePhoto()
                val thumb = ThumbnailGenerator.generateForPhoto(context, uri.toString())
                repository.saveMedia(uri.toString(), MediaType.PHOTO, thumbnailPath = thumb)
            } catch (e: Exception) {
                Log.e(TAG, "Photo capture failed", e)
                _uiState.update {
                    it.copy(message = CameraMessage.ResourceError(R.string.error_photo_capture))
                }
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
                    _uiState.update { it.copy(isRecording = false) }
                    viewModelScope.launch {
                        val thumb = ThumbnailGenerator.generateForVideo(context, uri.toString())
                        repository.saveMedia(uri.toString(), MediaType.VIDEO, duration, thumbnailPath = thumb)
                    }
                },
                onError = {
                    _uiState.update {
                        it.copy(
                            isRecording = false,
                            message = CameraMessage.ResourceError(R.string.error_video_recording)
                        )
                    }
                }
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
            _uiState.update {
                it.copy(message = CameraMessage.ResourceError(R.string.error_aspect_ratio_change))
            }
            return
        }
        cameraManager.setAspectRatio(ratio)
        _uiState.update { it.copy(aspectRatio = ratio) }
    }

    fun toggleFlash() {
        if (_uiState.value.isVideoMode) {
            val newState = !cameraManager.torchState.value
            cameraManager.toggleTorch(newState)
        } else {
            val currentMode = cameraManager.flashMode.value
            val newMode = if (currentMode == ImageCapture.FLASH_MODE_OFF)
                ImageCapture.FLASH_MODE_ON
            else
                ImageCapture.FLASH_MODE_OFF
            cameraManager.setFlashMode(newMode)
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

    override fun onCleared() {
        super.onCleared()
        cameraManager.cleanup()
    }

    private fun onCameraInitError() {
        _uiState.update {
            it.copy(
                isCameraAvailable = false,
                message = CameraMessage.ResourceError(R.string.error_camera_init)
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}

class CameraViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return CameraViewModel(application) as T
    }
}