package com.geostamp.camera.ui.camera

import android.app.Application
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geostamp.camera.data.model.CapturedPhoto
import com.geostamp.camera.data.model.StampConfig
import com.geostamp.camera.data.repository.SettingsRepository
import com.geostamp.camera.image.ImageProcessor
import com.geostamp.camera.location.GeocoderHelper
import com.geostamp.camera.location.LocationHelper
import com.geostamp.camera.location.LocationSnapshot
import com.geostamp.camera.location.LocationState
import com.geostamp.camera.storage.MediaStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

data class CameraUiState(
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val zoomRatio: Float = 1.0f,
    val isCapturing: Boolean = false,
    val isSaving: Boolean = false,
    val lastPhoto: CapturedPhoto? = null,
    val locationState: LocationState = LocationState.Idle,
    val currentAddress: String? = null,
    val infoMessage: String? = null,
    val errorMessage: String? = null
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val locationHelper = LocationHelper(application)

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    val stampConfig: StateFlow<StampConfig> = settingsRepository.configFlow

    init {
        // Collect location state
        viewModelScope.launch {
            locationHelper.state.collect { locState ->
                _uiState.value = _uiState.value.copy(locationState = locState)

                // If location is ready, resolve address asynchronously
                if (locState is LocationState.Ready) {
                    val snapshot = locState.snapshot
                    launch(Dispatchers.IO) {
                        val address = GeocoderHelper.getReadableAddress(
                            application,
                            snapshot.latitude,
                            snapshot.longitude
                        )
                        _uiState.value = _uiState.value.copy(currentAddress = address)
                    }
                }
            }
        }

        // Load most recent photo for bottom thumbnail
        viewModelScope.launch {
            val photos = MediaStoreHelper.getCapturedPhotos(application)
            _uiState.value = _uiState.value.copy(lastPhoto = photos.firstOrNull())
        }
    }

    fun startLocation() {
        locationHelper.startMonitoring()
    }

    fun stopLocation() {
        locationHelper.stopMonitoring()
    }

    fun toggleLens() {
        val next = if (_uiState.value.lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        _uiState.value = _uiState.value.copy(lensFacing = next)
    }

    fun cycleFlashMode() {
        val next = when (_uiState.value.flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
        _uiState.value = _uiState.value.copy(flashMode = next)
    }

    fun setZoomRatio(ratio: Float) {
        _uiState.value = _uiState.value.copy(zoomRatio = ratio.coerceIn(1.0f, 10.0f))
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(infoMessage = null, errorMessage = null)
    }

    fun capturePhoto(context: Context, imageCapture: ImageCapture) {
        if (_uiState.value.isCapturing || _uiState.value.isSaving) return

        _uiState.value = _uiState.value.copy(isCapturing = true)

        val executor = ContextCompat.getMainExecutor(context)
        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    _uiState.value = _uiState.value.copy(isCapturing = false, isSaving = true)
                    viewModelScope.launch {
                        processAndSaveCapturedImage(context, imageProxy)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    _uiState.value = _uiState.value.copy(
                        isCapturing = false,
                        errorMessage = "Camera capture failed: ${exception.localizedMessage}"
                    )
                }
            }
        )
    }

    private suspend fun processAndSaveCapturedImage(context: Context, imageProxy: ImageProxy) {
        try {
            val buffer: ByteBuffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            imageProxy.close()

            // 1. Obtain fresh location fix if possible
            val freshLocation = locationHelper.getFreshFixForCapture(timeoutMillis = 3500L)
                ?: when (val s = _uiState.value.locationState) {
                    is LocationState.Ready -> s.snapshot
                    else -> null
                }

            // 2. Resolve address
            val address = if (freshLocation != null) {
                GeocoderHelper.getReadableAddress(
                    context,
                    freshLocation.latitude,
                    freshLocation.longitude
                ) ?: _uiState.value.currentAddress
            } else null

            // 3. Stamp image
            val isFront = _uiState.value.lensFacing == CameraSelector.LENS_FACING_FRONT
            val processed = withContext(Dispatchers.Default) {
                ImageProcessor.stampAndProcessImage(
                    rawJpegBytes = bytes,
                    location = freshLocation,
                    address = address,
                    config = stampConfig.value,
                    lensFacingFront = isFront
                )
            }

            // 4. Save to MediaStore
            val timestamp = System.currentTimeMillis()
            val savedPhoto = MediaStoreHelper.savePhoto(
                context = context,
                jpegBytes = processed.jpegBytes,
                timestamp = timestamp,
                latitude = freshLocation?.latitude,
                longitude = freshLocation?.longitude,
                address = address,
                accuracy = freshLocation?.accuracyMeters,
                altitude = freshLocation?.altitudeMeters
            )

            _uiState.value = _uiState.value.copy(
                isSaving = false,
                lastPhoto = savedPhoto,
                infoMessage = "Photo stamped & saved to Gallery"
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isSaving = false,
                errorMessage = "Failed to stamp/save photo: ${e.localizedMessage}"
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationHelper.stopMonitoring()
    }
}
