package com.android.mobilecamera.feature.camera

import android.Manifest
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.mobilecamera.data.database.AppDatabase
import com.android.mobilecamera.data.database.MediaType
import com.android.mobilecamera.data.repository.MediaRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

data class CameraUiState(
    val isVideoMode: Boolean = false,
    val isRecording: Boolean = false,
    val recordingDuration: Long = 0L,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val showFlashAnimation: Boolean = false,
    val cameraControl: CameraControl? = null,
    val cameraInfo: CameraInfo? = null,
    val isTorchOn: Boolean = false,
    val aspectRatio: Int = AspectRatio.RATIO_4_3 // ← ДОБАВИЛИ
)

sealed class CameraEvent {
    data class ShowToast(val message: String) : CameraEvent()
}

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val repository = MediaRepository(AppDatabase.getDatabase(context).mediaDao())

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = Channel<CameraEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var previewView: androidx.camera.view.PreviewView? = null // ← ДОБАВИЛИ

    private var activeRecording: Recording? = null

    fun bindCamera(
        provider: ProcessCameraProvider,
        pView: androidx.camera.view.PreviewView,
        owner: LifecycleOwner
    ) {
        cameraProvider = provider
        lifecycleOwner = owner
        previewView = pView // ← СОХРАНЯЕМ

        createUseCases()
        rebindUseCases()
    }

    // ========== НОВАЯ ФУНКЦИЯ: СОЗДАНИЕ USE CASES ==========
    private fun createUseCases() {
        val currentAspectRatio = _uiState.value.aspectRatio

        preview = Preview.Builder()
            .setTargetAspectRatio(currentAspectRatio)
            .build()
            .apply {
                previewView?.let { setSurfaceProvider(it.surfaceProvider) }
            }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetAspectRatio(currentAspectRatio)
            .setFlashMode(_uiState.value.flashMode)
            .build()

        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.FHD, Quality.HD, Quality.SD),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
        )

        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .setAspectRatio(currentAspectRatio)
            .build()

        videoCapture = VideoCapture.withOutput(recorder)
    }

    private fun rebindUseCases() {
        val provider = cameraProvider ?: return
        val owner = lifecycleOwner ?: return

        try {
            provider.unbindAll()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(_uiState.value.lensFacing)
                .build()

            camera = provider.bindToLifecycle(
                owner,
                cameraSelector,
                preview,
                imageCapture,
                videoCapture
            )

            _uiState.update {
                it.copy(
                    cameraControl = camera?.cameraControl,
                    cameraInfo = camera?.cameraInfo
                )
            }

            // Восстанавливаем состояние фонарика
            if (_uiState.value.isTorchOn) {
                camera?.cameraControl?.enableTorch(true)
            }

        } catch (e: Exception) {
            Log.e("CameraVM", "Use case binding failed", e)
        }
    }

    fun toggleCameraMode() {
        _uiState.update { it.copy(isVideoMode = !it.isVideoMode) }
    }

    fun onCaptureClick() {
        if (_uiState.value.isVideoMode) {
            toggleVideoRecording()
        } else {
            takePhoto()
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(showFlashAnimation = true) }
            delay(100)
            _uiState.update { it.copy(showFlashAnimation = false) }
        }

        val name = "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MobileCamera")
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        capture.takePicture(
            outputOptions,
            Executors.newSingleThreadExecutor(),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: return
                    viewModelScope.launch {
                        repository.saveMedia(savedUri.toString(), MediaType.PHOTO)
                        _events.send(CameraEvent.ShowToast("Фото сохранено"))
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    viewModelScope.launch {
                        _events.send(CameraEvent.ShowToast("Ошибка: ${exception.message}"))
                    }
                }
            }
        )
    }

    private fun toggleVideoRecording() {
        val capture = videoCapture ?: return

        if (_uiState.value.isRecording) {
            activeRecording?.stop()
            activeRecording = null
            _uiState.update { it.copy(isRecording = false) }
        } else {
            val name = "VID_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MobileCamera")
            }

            val outputOptions = MediaStoreOutputOptions.Builder(
                context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(contentValues).build()

            _uiState.update { it.copy(recordingDuration = 0L) }

            val hasAudioPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            activeRecording = capture.output
                .prepareRecording(context, outputOptions)
                .apply {
                    if (hasAudioPermission) withAudioEnabled()
                }
                .asPersistentRecording()
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            _uiState.update { it.copy(isRecording = true) }
                            startTimer()
                        }
                        is VideoRecordEvent.Finalize -> {
                            val uri = event.outputResults.outputUri
                            val duration = event.recordingStats.recordedDurationNanos / 1_000_000

                            if (event.hasError()) {
                                if (uri != android.net.Uri.EMPTY && duration > 0) {
                                    viewModelScope.launch {
                                        repository.saveMedia(uri.toString(), MediaType.VIDEO, duration)
                                        _events.send(CameraEvent.ShowToast("Видео сохранено"))
                                    }
                                } else {
                                    viewModelScope.launch {
                                        _events.send(CameraEvent.ShowToast("Ошибка записи"))
                                    }
                                }
                            } else {
                                viewModelScope.launch {
                                    repository.saveMedia(uri.toString(), MediaType.VIDEO, duration)
                                    _events.send(CameraEvent.ShowToast("Видео сохранено"))
                                }
                            }

                            activeRecording?.close()
                            activeRecording = null
                            _uiState.update { it.copy(isRecording = false) }
                        }
                    }
                }
        }
    }

    private fun startTimer() {
        viewModelScope.launch {
            while (_uiState.value.isRecording) {
                delay(1000)
                _uiState.update { it.copy(recordingDuration = it.recordingDuration + 1000) }
            }
        }
    }

    fun switchCamera() {
        val newLens = if (_uiState.value.lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }

        _uiState.update { it.copy(lensFacing = newLens) }
        rebindUseCases()
    }

    fun toggleFlash() {
        if (_uiState.value.isVideoMode) {
            toggleTorch()
        } else {
            val newFlash = if (_uiState.value.flashMode == ImageCapture.FLASH_MODE_OFF) {
                ImageCapture.FLASH_MODE_ON
            } else {
                ImageCapture.FLASH_MODE_OFF
            }
            imageCapture?.flashMode = newFlash
            _uiState.update { it.copy(flashMode = newFlash) }
        }
    }

    private fun toggleTorch() {
        val newTorchState = !_uiState.value.isTorchOn
        camera?.cameraControl?.enableTorch(newTorchState)
        _uiState.update { it.copy(isTorchOn = newTorchState) }
    }

    // ========== НОВАЯ ФУНКЦИЯ: ПЕРЕКЛЮЧЕНИЕ ASPECT RATIO ==========
    fun setAspectRatio(ratio: Int) {
        if (_uiState.value.isRecording) {
            viewModelScope.launch {
                _events.send(CameraEvent.ShowToast("Нельзя менять соотношение во время записи"))
            }
            return
        }

        _uiState.update { it.copy(aspectRatio = ratio) }

        // Пересоздаем use cases с новым соотношением
        createUseCases()
        rebindUseCases()
    }
}

class CameraViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return CameraViewModel(application) as T
    }
}