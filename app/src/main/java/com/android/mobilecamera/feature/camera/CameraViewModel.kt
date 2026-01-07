package com.android.mobilecamera.feature.camera

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
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
import com.android.mobilecamera.infrastructure.media.MediaManager
import com.android.mobilecamera.infrastructure.media.ThumbnailGenerator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val aspectRatio: Int = AspectRatio.RATIO_4_3,
    val isCameraAvailable: Boolean = true
)

sealed class CameraEvent {
    data class ShowToast(val message: String) : CameraEvent()
}

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val repository = MediaRepository(AppDatabase.getDatabase(context).mediaDao())
    private val mediaManager = MediaManager(context)

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

    private var onPreviewCreated: ((Preview) -> Unit)? = null
    private var activeRecording: Recording? = null

    fun bindCamera(
        provider: ProcessCameraProvider,
        onSetupPreview: (Preview) -> Unit,
        owner: LifecycleOwner
    ) {
        cameraProvider = provider
        lifecycleOwner = owner
        onPreviewCreated = onSetupPreview

        createUseCases()
        rebindUseCases()
    }

    private fun createUseCases(forceDefault: Boolean = false) {
        val resolutionSelector = if (forceDefault) {
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()
        } else {
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(
                    AspectRatioStrategy(
                        _uiState.value.aspectRatio,
                        AspectRatioStrategy.FALLBACK_RULE_AUTO
                    )
                )
                .build()
        }

        preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
            .also { onPreviewCreated?.invoke(it) }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(resolutionSelector)
            .setFlashMode(_uiState.value.flashMode)
            .build()

        val recorderBuilder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.fromOrderedList(
                    listOf(Quality.FHD, Quality.HD, Quality.SD, Quality.LOWEST), // <-- Добавил LOWEST
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.LOWEST) // <-- Разрешаем падать до самого дна
                )
            )

        if (!forceDefault) {
            recorderBuilder.setAspectRatio(_uiState.value.aspectRatio)
        }

        videoCapture = VideoCapture.withOutput(recorderBuilder.build())
    }

    private fun rebindUseCases() {
        val provider = cameraProvider ?: return
        val owner = lifecycleOwner ?: return

        fun bind(useCustomAspectRatio: Boolean) {
            try {
                // unbindAll НЕ останавливает запись, если используется asPersistentRecording()
                provider.unbindAll()

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(_uiState.value.lensFacing)
                    .build()

                if (!useCustomAspectRatio) {
                    createUseCases(forceDefault = true)
                }

                // ==============================================================
                // 🔥 ШАГ 1: Динамический список UseCases
                // На API 26 это спасет от зависания при переключении
                // ==============================================================
                val useCases = mutableListOf<UseCase>()

                // 1. Превью нужно всегда
                preview?.let { useCases.add(it) }

                // 2. В режиме видео - только видео. В режиме фото - только фото.
                // Это снижает нагрузку на шину данных и позволяет переключаться без краша.
                if (_uiState.value.isVideoMode) {
                    videoCapture?.let { useCases.add(it) }
                } else {
                    imageCapture?.let { useCases.add(it) }
                }

                camera = provider.bindToLifecycle(
                    owner,
                    cameraSelector,
                    *useCases.toTypedArray()
                )

                _uiState.update {
                    it.copy(
                        cameraControl = camera?.cameraControl,
                        cameraInfo = camera?.cameraInfo,
                        isCameraAvailable = true
                    )
                }

                if (_uiState.value.isTorchOn && _uiState.value.isVideoMode) {
                    camera?.cameraControl?.enableTorch(true)
                }

            } catch (e: Exception) {
                Log.e("CameraVM", "Binding failed", e)

                if (useCustomAspectRatio) {
                    Log.w("CameraVM", "Retrying with default configuration...")
                    bind(useCustomAspectRatio = false)
                } else {
                    onCameraInitError(e)
                }
            }
        }

        bind(useCustomAspectRatio = true)
    }

    fun toggleCameraMode() {
        // Если идет запись, режим (Фото<->Видео) менять нельзя.
        // А вот камеру (Фронт<->Тыл) менять можно (см. switchCamera).
        if (_uiState.value.isRecording) {
            return
        }

        val currentUiState = _uiState.value
        val newIsVideoMode = !currentUiState.isVideoMode

        if (newIsVideoMode) {
            if (currentUiState.isTorchOn) {
                camera?.cameraControl?.enableTorch(true)
            }
        } else {
            camera?.cameraControl?.enableTorch(false)
        }

        _uiState.update { it.copy(isVideoMode = newIsVideoMode) }
        rebindUseCases()
    }

    fun onCaptureClick() {
        if (_uiState.value.isVideoMode) {
            toggleVideoRecording()
        } else {
            takePhoto()
        }
    }

    private fun takePhoto() {
        val capture = imageCapture
        // Если мы в режиме видео, imageCapture не привязан
        if (capture == null) {
            // Можно попробовать переключиться, но для безопасности просто игнорируем
            // или логируем, так как кнопка в UI должна вызывать toggleVideoRecording
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(showFlashAnimation = true) }
            delay(100)
            _uiState.update { it.copy(showFlashAnimation = false) }
        }

        val outputOptions = mediaManager.createPhotoOutputOptions()

        capture.takePicture(
            outputOptions,
            Executors.newSingleThreadExecutor(),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: return
                    viewModelScope.launch {
                        val thumbPath = ThumbnailGenerator.generateForPhoto(context, savedUri.toString())
                        repository.saveMedia(
                            path = savedUri.toString(),
                            type = MediaType.PHOTO,
                            thumbnailPath = thumbPath
                        )
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

            val outputOptions = mediaManager.createVideoOutputOptions()

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
                .asPersistentRecording() // 🔥 КЛЮЧЕВОЕ: Позволяет переключать камеру без остановки
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
                                        val thumbPath = ThumbnailGenerator.generateForVideo(context, uri.toString())
                                        repository.saveMedia(
                                            path = uri.toString(),
                                            type = MediaType.VIDEO,
                                            duration = duration,
                                            thumbnailPath = thumbPath
                                        )
                                        _events.send(CameraEvent.ShowToast("Видео сохранено"))
                                    }
                                } else {
                                    viewModelScope.launch {
                                        _events.send(CameraEvent.ShowToast("Ошибка записи"))
                                    }
                                }
                            } else {
                                viewModelScope.launch {
                                    val thumbPath = ThumbnailGenerator.generateForVideo(context, uri.toString())
                                    repository.saveMedia(
                                        path = uri.toString(),
                                        type = MediaType.VIDEO,
                                        duration = duration,
                                        thumbnailPath = thumbPath
                                    )
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
        // ✅ УБРАНА БЛОКИРОВКА ПРИ ЗАПИСИ
        // Благодаря asPersistentRecording() и разделению UseCase в rebindUseCases(),
        // переключение будет безопасным даже на API 26.

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

    fun setAspectRatio(ratio: Int) {
        if (_uiState.value.isRecording) {
            viewModelScope.launch {
                _events.send(CameraEvent.ShowToast("Нельзя менять соотношение во время записи"))
            }
            return
        }

        _uiState.update { it.copy(aspectRatio = ratio) }

        createUseCases()
        rebindUseCases()
    }

    override fun onCleared() {
        super.onCleared()
        onPreviewCreated = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        camera = null
        preview = null
        imageCapture = null
        videoCapture = null
        lifecycleOwner = null
        activeRecording?.close()
        activeRecording = null
    }

    fun onCameraInitError(e: Exception) {
        _uiState.update { it.copy(isCameraAvailable = false) }
        viewModelScope.launch {
            _events.send(CameraEvent.ShowToast("Ошибка инициализации камеры: ${e.message}"))
        }
    }

    fun onTapToFocus(meteringPoint: MeteringPoint) {
        val cameraControl = _uiState.value.cameraControl ?: return

        val action = FocusMeteringAction.Builder(meteringPoint)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        cameraControl.startFocusAndMetering(action)
    }

    fun onZoomEvent(scaleFactor: Float) {
        val cameraControl = _uiState.value.cameraControl ?: return
        val cameraInfo = _uiState.value.cameraInfo ?: return

        val currentZoom = cameraInfo.zoomState.value?.zoomRatio ?: 1f
        val maxZoom = cameraInfo.zoomState.value?.maxZoomRatio ?: 10f
        val minZoom = cameraInfo.zoomState.value?.minZoomRatio ?: 1f

        // scaleFactor > 1 (увеличение), < 1 (уменьшение)
        val newZoom = (currentZoom * scaleFactor).coerceIn(minZoom, maxZoom)

        cameraControl.setZoomRatio(newZoom)
    }
}

class CameraViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return CameraViewModel(application) as T
    }
}