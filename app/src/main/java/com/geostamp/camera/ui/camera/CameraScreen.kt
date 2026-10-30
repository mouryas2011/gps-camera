package com.geostamp.camera.ui.camera

import android.Manifest
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Timer3
import androidx.compose.material.icons.filled.Timer10
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.geostamp.camera.data.model.AspectRatioOption
import com.geostamp.camera.data.model.CapturedPhoto
import com.geostamp.camera.data.model.StampPosition
import com.geostamp.camera.location.LocationState
import com.geostamp.camera.ui.components.AspectFramingMask
import com.geostamp.camera.ui.components.HorizonLevelGauge
import com.geostamp.camera.ui.components.LiveWatermarkOverlay
import com.geostamp.camera.ui.components.LocationDetailsDialog
import com.geostamp.camera.ui.components.NotesAndTagsDialog
import com.geostamp.camera.ui.components.ProjectFolderDialog
import com.geostamp.camera.ui.components.RuleOfThirdsGrid
import com.geostamp.camera.ui.components.TapFocusRing
import com.geostamp.camera.ui.components.TemplateChooserSheet
import com.geostamp.camera.ui.components.TimerCountdownOverlay
import com.geostamp.camera.ui.theme.AccentAmber
import com.geostamp.camera.ui.theme.AccentGreen
import com.geostamp.camera.ui.theme.AccentRed
import com.geostamp.camera.ui.theme.CyanPrimary

private val BrandYellow = Color(0xFFFFCC00)

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onNavigateToGallery: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenPhotoDetail: (CapturedPhoto) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    val uiState by viewModel.uiState.collectAsState()
    val stampConfig by viewModel.stampConfig.collectAsState()

    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var currentZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(8f) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }

    var hasCameraPermission by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(false) }

    // Dialog & Sheet states
    var showNotesDialog by remember { mutableStateOf(false) }
    var showLocationDetailsDialog by remember { mutableStateOf(false) }
    var showTemplateSheet by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (hasLocationPermission) {
            viewModel.startLocation()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshRecentPhoto()

        val camGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val locGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        hasCameraPermission = camGranted
        hasLocationPermission = locGranted

        if (!camGranted || !locGranted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            viewModel.startLocation()
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            viewModel.stopLocation()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Camera Viewfinder
        if (hasCameraPermission) {
            val previewView = remember {
                PreviewView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            }

            LaunchedEffect(hasCameraPermission, uiState.lensFacing) {
                currentZoom = 1f
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .setFlashMode(uiState.flashMode)
                            .build()
                        imageCapture = capture

                        val requestedSelector = CameraSelector.Builder()
                            .requireLensFacing(uiState.lensFacing)
                            .build()

                        val selector = if (cameraProvider.hasCamera(requestedSelector)) {
                            requestedSelector
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }

                        cameraProvider.unbindAll()
                        val cam = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview,
                            capture
                        )
                        cameraControl = cam.cameraControl

                        cam.cameraInfo.zoomState.observe(lifecycleOwner) { zoomState ->
                            maxZoom = zoomState.maxZoomRatio.coerceAtMost(8f)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(context))
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            val newZoom = (currentZoom * zoom).coerceIn(1f, maxZoom)
                            currentZoom = newZoom
                            viewModel.setZoomRatio(newZoom, cameraControl)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            focusPoint = offset
                            val factory = SurfaceOrientedMeteringPointFactory(
                                size.width.toFloat(),
                                size.height.toFloat()
                            )
                            val point = factory.createPoint(offset.x, offset.y)
                            val action = FocusMeteringAction.Builder(point).build()
                            cameraControl?.startFocusAndMetering(action)
                        }
                    },
                factory = { previewView },
                update = {
                    imageCapture?.flashMode = uiState.flashMode
                }
            )
        } else {
            // Permission Rationale
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = AccentAmber,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Camera & Location Permission Required",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "GPS Map Camera needs camera and location access to capture photos with verified GPS coordinates and addresses.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    TextButton(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    ) {
                        Text("Grant Permission", color = BrandYellow, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. Aspect Ratio Framing Mask (Visual crop simulation for 1:1 or 16:9)
        AspectFramingMask(aspectRatio = stampConfig.aspectRatio)

        // 3. Grid / Horizon Level Viewfinder Tools
        if (uiState.gridMode == 1) {
            RuleOfThirdsGrid()
        } else if (uiState.gridMode == 2) {
            HorizonLevelGauge(
                pitchDegrees = uiState.sensorSnapshot.pitchDegrees,
                rollDegrees = uiState.sensorSnapshot.rollDegrees
            )
        }

        // 4. Tap to focus reticle ring
        TapFocusRing(focusPoint = focusPoint)

        // 5. Timer countdown overlay (e.g. 3... 2... 1...)
        TimerCountdownOverlay(secondsRemaining = uiState.countdownRemaining)

        // 6. Vertical Exposure Slider on Right Side
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
                .height(200.dp)
                .width(36.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = -dragAmount.y / 180f
                        val newProgress = (uiState.exposureProgress + delta).coerceIn(0f, 1f)
                        viewModel.setExposureProgress(newProgress, cameraControl)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(160.dp)
                    .background(Color(0x55FFFFFF))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(2.5.dp)
                    .height((160 * uiState.exposureProgress).dp)
                    .background(BrandYellow)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-(160 * uiState.exposureProgress)).dp)
                    .size(26.dp)
                    .background(Color.Black, CircleShape)
                    .border(1.2.dp, BrandYellow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = "Exposure",
                    tint = BrandYellow,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // 7. Top Controls Bar (Aspect Ratio, Flash/Torch, Grid/Level, Timer, Notes, GPS Info, Lens, Settings)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 36.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Aspect Ratio Cycle Button with label
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x77000000))
                    .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(8.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.cycleAspectRatio()
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stampConfig.aspectRatio.label,
                    color = BrandYellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Flash / Torch Mode Toggle
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.cycleFlashMode(cameraControl)
            }) {
                Icon(
                    imageVector = when {
                        uiState.isTorchOn -> Icons.Default.FlashlightOn
                        uiState.flashMode == ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        uiState.flashMode == ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    },
                    contentDescription = "Flash",
                    tint = if (uiState.isTorchOn || uiState.flashMode != ImageCapture.FLASH_MODE_OFF) BrandYellow else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Grid & Horizon Level Toggle
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.cycleGridMode()
            }) {
                Icon(
                    imageVector = when (uiState.gridMode) {
                        1 -> Icons.Default.GridOn
                        2 -> Icons.Default.Straighten
                        else -> Icons.Default.GridOn
                    },
                    contentDescription = "Grid",
                    tint = if (uiState.gridMode > 0) BrandYellow else Color(0x99FFFFFF),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Timer (Off, 3s, 5s, 10s)
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.cycleTimer()
            }) {
                Icon(
                    imageVector = when (uiState.timerSeconds) {
                        3 -> Icons.Default.Timer3
                        10 -> Icons.Default.Timer10
                        else -> Icons.Default.Timer
                    },
                    contentDescription = "Timer",
                    tint = if (uiState.timerSeconds > 0) BrandYellow else Color(0x99FFFFFF),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Notes / Title Dialog
            IconButton(onClick = { showNotesDialog = true }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                    contentDescription = "Custom Tag",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // GPS Location Details
            IconButton(onClick = { showLocationDetailsDialog = true }) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location info",
                    tint = when (uiState.locationState) {
                        is LocationState.Ready -> AccentGreen
                        is LocationState.Acquiring -> AccentAmber
                        else -> AccentRed
                    },
                    modifier = Modifier.size(24.dp)
                )
            }

            // Switch Camera Lens (Front/Back)
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.toggleLens()
            }) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch Camera",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Settings
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 8. Live Watermark Overlay (Positioned dynamically according to stampConfig.position)
        val isTopWatermark = stampConfig.position == StampPosition.TOP_LEFT || stampConfig.position == StampPosition.TOP_RIGHT
        val watermarkPaddingModifier = if (isTopWatermark) {
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 84.dp)
        } else {
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 158.dp)
        }

        LiveWatermarkOverlay(
            uiState = uiState,
            config = stampConfig,
            onCardClick = { showTemplateSheet = true },
            modifier = watermarkPaddingModifier
        )

        // 9. Zoom Pills (0.5x, 1x, 2x, 5x)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 114.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val zoomLevels = listOf(1.0f, 2.0f, 5.0f)
            zoomLevels.forEach { targetZoom ->
                val isSelected = currentZoom in (targetZoom - 0.4f)..(targetZoom + 0.4f)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) BrandYellow else Color(0x9910141C))
                        .border(1.dp, if (isSelected) BrandYellow else Color(0x33FFFFFF), CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentZoom = targetZoom.coerceAtMost(maxZoom)
                            viewModel.setZoomRatio(currentZoom, cameraControl)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${targetZoom.toInt()}x",
                        color = if (isSelected) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 10. Mode Selector (PHOTO / VIDEO) with smooth animated pills
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 74.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PHOTO Mode
            val isPhoto = uiState.currentMode == CameraMode.PHOTO
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isPhoto) BrandYellow else Color.Transparent)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.setMode(CameraMode.PHOTO)
                    }
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "PHOTO",
                    color = if (isPhoto) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            // VIDEO Mode
            val isVideo = uiState.currentMode == CameraMode.VIDEO
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isVideo) BrandYellow else Color.Transparent)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.setMode(CameraMode.VIDEO)
                    }
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.isVideoRecording) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AccentRed)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (uiState.isVideoRecording) "REC %02d:%02d".format(uiState.recordingSeconds / 60, uiState.recordingSeconds % 60) else "VIDEO",
                        color = if (isVideo) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 11. Bottom Controls Bar (Preview, Location, Shutter, Folder, Templates)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xF0080C12))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Preview thumbnail
                BottomBarItem(label = "Preview", onClick = onNavigateToGallery) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E2836))
                            .border(1.5.dp, Color(0xFF37474F), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.lastPhoto != null) {
                            AsyncImage(
                                model = uiState.lastPhoto!!.uri,
                                contentDescription = "Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Gallery",
                                tint = Color.LightGray,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // 2. GPS Location status button
                BottomBarItem(label = "Location", onClick = { showLocationDetailsDialog = true }) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0x991E2836)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = when (uiState.locationState) {
                                is LocationState.Ready -> AccentGreen
                                is LocationState.Acquiring -> AccentAmber
                                else -> AccentRed
                            },
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // 3. Shutter / Record Button
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .border(3.5.dp, if (uiState.currentMode == CameraMode.VIDEO) AccentRed else Color.White, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                uiState.isCapturing || uiState.isSaving -> Color.DarkGray
                                uiState.currentMode == CameraMode.VIDEO -> AccentRed
                                else -> Color.White
                            }
                        )
                        .clickable(enabled = !uiState.isCapturing && !uiState.isSaving && hasCameraPermission) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (uiState.currentMode == CameraMode.VIDEO) {
                                viewModel.toggleVideoRecording()
                            } else {
                                imageCapture?.let { viewModel.triggerCapture(context, it) }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            color = BrandYellow,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                // 4. Folder / Project Organizer
                BottomBarItem(label = "Folder", onClick = { showFolderDialog = true }) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0x991E2836)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Project Folder",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 5. Watermark Templates Chooser
                BottomBarItem(label = "Templates", onClick = { showTemplateSheet = true }) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0x991E2836)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Templates",
                            tint = BrandYellow,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // 12. Floating Snackbar for info/error messages
        AnimatedVisibility(
            visible = uiState.infoMessage != null || uiState.errorMessage != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 95.dp, start = 20.dp, end = 20.dp)
        ) {
            val isError = uiState.errorMessage != null
            val message = uiState.errorMessage ?: uiState.infoMessage ?: ""

            Snackbar(
                containerColor = if (isError) AccentRed else Color(0xFF1B322A),
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(onClick = { viewModel.dismissMessage() }) {
                        Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 13. Dialogs & Bottom Sheets
        if (showNotesDialog) {
            NotesAndTagsDialog(
                config = stampConfig,
                onSave = { title, person, company, notes ->
                    viewModel.updateCustomTitle(title)
                    viewModel.updatePersonName(person)
                    viewModel.updateCompanyName(company)
                    viewModel.updateNotes(notes)
                },
                onDismiss = { showNotesDialog = false }
            )
        }

        if (showLocationDetailsDialog) {
            LocationDetailsDialog(
                locationState = uiState.locationState,
                detailedAddress = uiState.detailedAddress,
                plusCode = uiState.plusCode,
                onRefreshFix = { viewModel.refreshLocationAndMetadata() },
                onDismiss = { showLocationDetailsDialog = false }
            )
        }

        if (showFolderDialog) {
            ProjectFolderDialog(
                currentFolder = uiState.selectedFolder,
                onSelectFolder = { folder ->
                    viewModel.setSelectedFolder(folder)
                },
                onDismiss = { showFolderDialog = false }
            )
        }

        if (showTemplateSheet) {
            TemplateChooserSheet(
                currentStyle = stampConfig.style,
                onSelectStyle = { style ->
                    viewModel.setStampStyle(style)
                },
                onDismiss = { showTemplateSheet = false }
            )
        }
    }
}

@Composable
private fun BottomBarItem(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        content()
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.LightGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
