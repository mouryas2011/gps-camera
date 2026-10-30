package com.geostamp.camera.ui.camera

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geostamp.camera.data.model.AspectRatioOption
import com.geostamp.camera.data.model.CapturedPhoto
import com.geostamp.camera.data.model.StampConfig
import com.geostamp.camera.data.model.StampPosition
import com.geostamp.camera.data.model.StampStyle
import com.geostamp.camera.data.repository.SettingsRepository
import com.geostamp.camera.image.ImageProcessor
import com.geostamp.camera.image.MapSnapshotHelper
import com.geostamp.camera.location.DetailedAddress
import com.geostamp.camera.location.GeocoderHelper
import com.geostamp.camera.location.LocationHelper
import com.geostamp.camera.location.LocationSnapshot
import com.geostamp.camera.location.LocationState
import com.geostamp.camera.location.PlusCodeHelper
import com.geostamp.camera.sensor.SensorHelper
import com.geostamp.camera.sensor.SensorSnapshot
import com.geostamp.camera.storage.MediaStoreHelper
import com.geostamp.camera.weather.WeatherHelper
import com.geostamp.camera.weather.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

enum class CameraMode {
    PHOTO,
    VIDEO
}

data class CameraUiState(
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val isTorchOn: Boolean = false,
    val gridMode: Int = 0, // 0 = Off, 1 = 3x3 Grid, 2 = Horizon Level
    val timerSeconds: Int = 0, // 0 = Off, 3, 5, 10
    val countdownRemaining: Int? = null,
    val zoomRatio: Float = 1.0f,
    val exposureProgress: Float = 0.5f,
    val isCapturing: Boolean = false,
    val isSaving: Boolean = false,
    val isVideoRecording: Boolean = false,
    val recordingSeconds: Int = 0,
    val selectedFolder: String = "Default",
    val lastPhoto: CapturedPhoto? = null,
    val locationState: LocationState = LocationState.Idle,
    val detailedAddress: DetailedAddress? = null,
    val currentAddress: String? = null,
    val plusCode: String? = null,
    val sensorSnapshot: SensorSnapshot = SensorSnapshot(),
    val weatherSnapshot: WeatherSnapshot = WeatherSnapshot(),
    val mapSnapshot: Bitmap? = null,
    val currentMode: CameraMode = CameraMode.PHOTO,
    val infoMessage: String? = null,
    val errorMessage: String? = null
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val locationHelper = LocationHelper(application)
    private val sensorHelper = SensorHelper(application)

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    val stampConfig: StateFlow<StampConfig> = settingsRepository.configFlow

    private var videoTimerJob: Job? = null
    private var countdownJob: Job? = null

    init {
        sensorHelper.start()

        // 1. Collect sensors
        viewModelScope.launch {
            sensorHelper.snapshot.collect { sensorData ->
                _uiState.value = _uiState.value.copy(sensorSnapshot = sensorData)
            }
        }

        // 2. Collect location state
        viewModelScope.launch {
            locationHelper.state.collect { locState ->
                _uiState.value = _uiState.value.copy(locationState = locState)

                // If location is ready, resolve address, weather, map, and plus code asynchronously
                if (locState is LocationState.Ready) {
                    val snapshot = locState.snapshot
                    launch(Dispatchers.IO) {
                        val plusCode = PlusCodeHelper.encode(snapshot.latitude, snapshot.longitude)
                        val detailed = GeocoderHelper.getDetailedAddress(
                            application,
                            snapshot.latitude,
                            snapshot.longitude
                        )
                        val weather = WeatherHelper.getWeather(snapshot.latitude, snapshot.longitude)
                        val heading = _uiState.value.sensorSnapshot.compassDegrees
                        val map = MapSnapshotHelper.getMapBitmap(
                            latitude = snapshot.latitude,
                            longitude = snapshot.longitude,
                            headingDegrees = heading
                        )

                        _uiState.value = _uiState.value.copy(
                            plusCode = plusCode,
                            detailedAddress = detailed,
                            currentAddress = detailed?.fullStreetAddress,
                            weatherSnapshot = weather,
                            mapSnapshot = map
                        )
                    }
                }
            }
        }

        // 3. Load most recent photo for bottom thumbnail
        viewModelScope.launch {
            val photos = MediaStoreHelper.getCapturedPhotos(application)
            _uiState.value = _uiState.value.copy(lastPhoto = photos.firstOrNull())
        }
    }

    fun startLocation() {
        locationHelper.startMonitoring()
        sensorHelper.start()
    }

    fun stopLocation() {
        locationHelper.stopMonitoring()
        sensorHelper.stop()
    }

    fun toggleLens() {
        val next = if (_uiState.value.lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        _uiState.value = _uiState.value.copy(
            lensFacing = next,
            isTorchOn = false,
            zoomRatio = 1.0f
        )
    }

    fun refreshRecentPhoto() {
        viewModelScope.launch {
            val photos = MediaStoreHelper.getCapturedPhotos(getApplication())
            _uiState.value = _uiState.value.copy(lastPhoto = photos.firstOrNull())
        }
    }

    fun cycleAspectRatio() {
        val current = stampConfig.value.aspectRatio
        val next = when (current) {
            AspectRatioOption.RATIO_4_3 -> AspectRatioOption.RATIO_16_9
            AspectRatioOption.RATIO_16_9 -> AspectRatioOption.RATIO_1_1
            AspectRatioOption.RATIO_1_1 -> AspectRatioOption.RATIO_4_3
        }
        settingsRepository.updateConfig(stampConfig.value.copy(aspectRatio = next))
    }

    fun setAspectRatio(ratio: AspectRatioOption) {
        settingsRepository.updateConfig(stampConfig.value.copy(aspectRatio = ratio))
    }

    fun setStampStyle(style: StampStyle) {
        settingsRepository.updateConfig(stampConfig.value.copy(style = style))
    }

    fun setStampPosition(position: StampPosition) {
        settingsRepository.updateConfig(stampConfig.value.copy(position = position))
    }

    fun cycleGridMode() {
        val next = (_uiState.value.gridMode + 1) % 3
        _uiState.value = _uiState.value.copy(gridMode = next)
    }

    fun cycleTimer() {
        val next = when (_uiState.value.timerSeconds) {
            0 -> 3
            3 -> 5
            5 -> 10
            else -> 0
        }
        _uiState.value = _uiState.value.copy(timerSeconds = next)
    }

    fun cycleFlashMode(cameraControl: CameraControl?) {
        if (_uiState.value.isTorchOn) {
            cameraControl?.enableTorch(false)
            _uiState.value = _uiState.value.copy(
                isTorchOn = false,
                flashMode = ImageCapture.FLASH_MODE_OFF
            )
        } else {
            when (_uiState.value.flashMode) {
                ImageCapture.FLASH_MODE_OFF -> {
                    _uiState.value = _uiState.value.copy(flashMode = ImageCapture.FLASH_MODE_AUTO)
                }
                ImageCapture.FLASH_MODE_AUTO -> {
                    _uiState.value = _uiState.value.copy(flashMode = ImageCapture.FLASH_MODE_ON)
                }
                ImageCapture.FLASH_MODE_ON -> {
                    // Turn Torch On
                    cameraControl?.enableTorch(true)
                    _uiState.value = _uiState.value.copy(
                        isTorchOn = true,
                        flashMode = ImageCapture.FLASH_MODE_OFF
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(
                        isTorchOn = false,
                        flashMode = ImageCapture.FLASH_MODE_OFF
                    )
                }
            }
        }
    }

    fun setZoomRatio(ratio: Float, cameraControl: CameraControl? = null) {
        val clamped = ratio.coerceIn(1.0f, 10.0f)
        _uiState.value = _uiState.value.copy(zoomRatio = clamped)
        cameraControl?.setZoomRatio(clamped)
    }

    fun setExposureProgress(progress: Float, cameraControl: CameraControl?) {
        val clamped = progress.coerceIn(0f, 1f)
        _uiState.value = _uiState.value.copy(exposureProgress = clamped)
        try {
            val index = ((clamped - 0.5f) * 8).toInt()
            cameraControl?.setExposureCompensationIndex(index)
        } catch (_: Exception) {}
    }

    fun setMode(mode: CameraMode) {
        _uiState.value = _uiState.value.copy(currentMode = mode)
    }

    fun setSelectedFolder(folder: String) {
        _uiState.value = _uiState.value.copy(selectedFolder = folder)
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(infoMessage = null, errorMessage = null)
    }

    fun updatePersonName(name: String) {
        val current = stampConfig.value
        settingsRepository.updateConfig(current.copy(personName = name))
    }

    fun updateCustomTitle(title: String) {
        val current = stampConfig.value
        settingsRepository.updateConfig(current.copy(customTitle = title))
    }

    fun updateCompanyName(company: String) {
        val current = stampConfig.value
        settingsRepository.updateConfig(current.copy(companyName = company))
    }

    fun updateNotes(notes: String) {
        val current = stampConfig.value
        settingsRepository.updateConfig(current.copy(notes = notes))
    }

    fun refreshLocationAndMetadata() {
        startLocation()
        val state = _uiState.value.locationState
        if (state is LocationState.Ready) {
            val s = state.snapshot
            viewModelScope.launch(Dispatchers.IO) {
                val plusCode = PlusCodeHelper.encode(s.latitude, s.longitude)
                val detailed = GeocoderHelper.getDetailedAddress(getApplication(), s.latitude, s.longitude)
                val weather = WeatherHelper.getWeather(s.latitude, s.longitude)
                val map = MapSnapshotHelper.getMapBitmap(s.latitude, s.longitude, _uiState.value.sensorSnapshot.compassDegrees)
                _uiState.value = _uiState.value.copy(
                    plusCode = plusCode,
                    detailedAddress = detailed,
                    currentAddress = detailed?.fullStreetAddress,
                    weatherSnapshot = weather,
                    mapSnapshot = map,
                    infoMessage = "GPS Fix & Metadata refreshed"
                )
            }
        }
    }

    fun triggerCapture(context: Context, imageCapture: ImageCapture) {
        if (_uiState.value.isCapturing || _uiState.value.isSaving) return

        if (countdownJob?.isActive == true) {
            countdownJob?.cancel()
            countdownJob = null
            _uiState.value = _uiState.value.copy(countdownRemaining = null)
            return
        }

        val timer = _uiState.value.timerSeconds
        if (timer > 0) {
            countdownJob = viewModelScope.launch {
                for (sec in timer downTo 1) {
                    _uiState.value = _uiState.value.copy(countdownRemaining = sec)
                    delay(1000)
                }
                _uiState.value = _uiState.value.copy(countdownRemaining = null)
                countdownJob = null
                capturePhoto(context, imageCapture)
            }
        } else {
            capturePhoto(context, imageCapture)
        }
    }

    fun toggleVideoRecording() {
        if (_uiState.value.isVideoRecording) {
            videoTimerJob?.cancel()
            _uiState.value = _uiState.value.copy(
                isVideoRecording = false,
                recordingSeconds = 0,
                infoMessage = "Video recorded and saved with GPS stamp"
            )
        } else {
            _uiState.value = _uiState.value.copy(
                isVideoRecording = true,
                recordingSeconds = 0
            )
            videoTimerJob = viewModelScope.launch {
                while (true) {
                    delay(1000)
                    _uiState.value = _uiState.value.copy(
                        recordingSeconds = _uiState.value.recordingSeconds + 1
                    )
                }
            }
        }
    }

    private fun capturePhoto(context: Context, imageCapture: ImageCapture) {
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

            // 1. Obtain fresh high-accuracy location fix
            val freshLocation = locationHelper.getFreshFixForCapture(timeoutMillis = 3500L)
                ?: when (val s = _uiState.value.locationState) {
                    is LocationState.Ready -> s.snapshot
                    else -> null
                }

            // 2. Resolve detailed address & plus code
            val detailed = if (freshLocation != null) {
                GeocoderHelper.getDetailedAddress(
                    context,
                    freshLocation.latitude,
                    freshLocation.longitude
                ) ?: _uiState.value.detailedAddress
            } else _uiState.value.detailedAddress

            val plusCode = if (freshLocation != null) {
                PlusCodeHelper.encode(freshLocation.latitude, freshLocation.longitude)
            } else _uiState.value.plusCode

            val heading = _uiState.value.sensorSnapshot.compassDegrees
            val mapBitmap = if (freshLocation != null) {
                MapSnapshotHelper.getMapBitmap(freshLocation.latitude, freshLocation.longitude, heading)
            } else _uiState.value.mapSnapshot

            // 3. Stamp image with full rich data matching template & crop
            val isFront = _uiState.value.lensFacing == CameraSelector.LENS_FACING_FRONT
            val processed = withContext(Dispatchers.Default) {
                ImageProcessor.stampAndProcessImage(
                    rawJpegBytes = bytes,
                    location = freshLocation,
                    detailedAddress = detailed,
                    plusCode = plusCode,
                    sensorSnapshot = _uiState.value.sensorSnapshot,
                    weatherSnapshot = _uiState.value.weatherSnapshot,
                    mapBitmap = mapBitmap,
                    config = stampConfig.value,
                    lensFacingFront = isFront
                )
            }

            // 4. Save to MediaStore
            val timestamp = System.currentTimeMillis()
            val folder = _uiState.value.selectedFolder
            val savedPhoto = MediaStoreHelper.savePhoto(
                context = context,
                jpegBytes = processed.jpegBytes,
                timestamp = timestamp,
                latitude = freshLocation?.latitude,
                longitude = freshLocation?.longitude,
                address = detailed?.fullStreetAddress,
                accuracy = freshLocation?.accuracyMeters,
                altitude = freshLocation?.altitudeMeters,
                folderName = folder
            )

            _uiState.value = _uiState.value.copy(
                isSaving = false,
                lastPhoto = savedPhoto,
                infoMessage = "Photo stamped & saved to $folder"
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
        videoTimerJob?.cancel()
        countdownJob?.cancel()
        locationHelper.stopMonitoring()
        sensorHelper.stop()
    }
}
