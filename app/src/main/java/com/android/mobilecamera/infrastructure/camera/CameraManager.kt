package com.android.mobilecamera.infrastructure.camera

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.android.mobilecamera.infrastructure.media.MediaManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraManager(
    private val context: Context,
    private val mediaManager: MediaManager
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var preview: Preview? = null
    private var recorder: Recorder? = null

    private var currentSurfaceProvider: Preview.SurfaceProvider? = null
    private var activeRecording: Recording? = null
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var aspectRatio: Int = AspectRatio.RATIO_4_3
    private var flashMode: Int = ImageCapture.FLASH_MODE_OFF

    private val _torchState = MutableStateFlow(false)
    val torchState: StateFlow<Boolean> = _torchState.asStateFlow()

    private val _zoomState = MutableStateFlow(1f)

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        isVideoMode: Boolean,
        onCameraInitialized: (CameraControl, CameraInfo) -> Unit,
        onError: (Exception) -> Unit
    ) {
        this.currentSurfaceProvider = surfaceProvider
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(lifecycleOwner, isVideoMode, onCameraInitialized)
            } catch (e: Exception) {
                onError(e)
            }
        }, mainExecutor)
    }

    fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        isVideoMode: Boolean,
        onCameraInitialized: (CameraControl, CameraInfo) -> Unit
    ) {
        val provider = cameraProvider ?: return
        val surfaceProvider = currentSurfaceProvider ?: return

        try {
            provider.unbindAll()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(
                    AspectRatioStrategy(aspectRatio, AspectRatioStrategy.FALLBACK_RULE_AUTO)
                )
                .build()

            preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()
                .apply { setSurfaceProvider(surfaceProvider) }

            val useCases = mutableListOf<UseCase>(preview!!)

            if (isVideoMode) {
                if (recorder == null) {
                    recorder = Recorder.Builder()
                        .setQualitySelector(
                            QualitySelector.fromOrderedList(
                                listOf(Quality.FHD, Quality.HD, Quality.SD, Quality.LOWEST),
                                FallbackStrategy.lowerQualityOrHigherThan(Quality.LOWEST)
                            )
                        )
                        .setAspectRatio(aspectRatio)
                        .build()
                }
                videoCapture = VideoCapture.withOutput(recorder!!)
                useCases.add(videoCapture!!)
            } else {
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setResolutionSelector(resolutionSelector)
                    .setFlashMode(flashMode)
                    .build()
                useCases.add(imageCapture!!)
            }

            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCases.toTypedArray()
            )

            camera?.let { cam ->
                _zoomState.value = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f

                onCameraInitialized(cam.cameraControl, cam.cameraInfo)

                if (isVideoMode && cam.cameraInfo.hasFlashUnit()) {
                    cam.cameraControl.enableTorch(_torchState.value)
                } else {
                    if (_torchState.value) {
                        _torchState.value = false
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("CameraManager", "Binding failed", e)
        }
    }

    fun toggleTorch(enabled: Boolean) {
        val cam = camera ?: return

        if (cam.cameraInfo.hasFlashUnit()) {
            cam.cameraControl.enableTorch(enabled)
            _torchState.value = enabled
        } else {
            _torchState.value = false
        }
    }

    fun setZoom(scaleFactor: Float) {
        val cam = camera ?: return
        val info = cam.cameraInfo
        val control = cam.cameraControl
        val state = info.zoomState.value ?: return

        val current = _zoomState.value
        val newZoom = (current * scaleFactor).coerceIn(state.minZoomRatio, state.maxZoomRatio)

        if (newZoom != current) {
            _zoomState.value = newZoom
            control.setZoomRatio(newZoom)
        }
    }

    fun toggleCameraLens(): Int {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }

        _torchState.value = false

        return lensFacing
    }

    fun setFocus(meteringPoint: MeteringPoint) {
        val control = camera?.cameraControl ?: return
        val action = FocusMeteringAction.Builder(meteringPoint)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        control.startFocusAndMetering(action)
    }

    suspend fun takePhoto(): Uri = suspendCancellableCoroutine { continuation ->
        val capture = imageCapture ?: run {
            continuation.resumeWithException(IllegalStateException("ImageCapture not ready"))
            return@suspendCancellableCoroutine
        }
        val outputOptions = mediaManager.createPhotoOutputOptions()
        capture.takePicture(
            outputOptions,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val uri = output.savedUri
                    if (uri != null) continuation.resume(uri)
                    else continuation.resumeWithException(RuntimeException("Photo saved but URI is null"))
                }
                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWithException(exception)
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    fun startVideoRecording(
        withAudio: Boolean,
        onVideoSaved: (Uri, Long) -> Unit,
        onError: (String) -> Unit
    ): Recording? {
        val capture = videoCapture ?: return null
        val outputOptions = mediaManager.createVideoOutputOptions()
        activeRecording = capture.output
            .prepareRecording(context, outputOptions)
            .apply { if (withAudio) withAudioEnabled() }
            .asPersistentRecording()
            .start(mainExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Finalize -> {
                        val uri = event.outputResults.outputUri
                        val duration = event.recordingStats.recordedDurationNanos / 1_000_000
                        if (!event.hasError()) {
                            onVideoSaved(uri, duration)
                        } else {
                            if (uri != Uri.EMPTY && duration > 0) onVideoSaved(uri, duration)
                            else onError("Video capture failed: ${event.error}")
                        }
                        activeRecording = null
                    }
                }
            }
        return activeRecording
    }

    fun stopVideoRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun setAspectRatio(ratio: Int) {
        aspectRatio = ratio
        recorder = null
    }

    fun setFlashMode(mode: Int) {
        flashMode = mode
        imageCapture?.flashMode = mode
    }

    fun cleanup() {
        cameraProvider?.unbindAll()
        activeRecording?.stop()
        recorder = null
        _torchState.value = false
    }
}