package com.geostamp.camera.ui.camera

import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.geostamp.camera.data.model.CapturedPhoto
import com.geostamp.camera.location.LocationState
import com.geostamp.camera.ui.theme.AccentGreen
import com.geostamp.camera.ui.theme.AccentAmber
import com.geostamp.camera.ui.theme.AccentRed
import com.geostamp.camera.ui.theme.CyanPrimary
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.geostamp.camera.data.model.StampConfig
import com.geostamp.camera.data.model.StampPosition
import com.geostamp.camera.data.model.StampStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val density = LocalDensity.current
    val uiState by viewModel.uiState.collectAsState()
    val stampConfig by viewModel.stampConfig.collectAsState()

    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var currentZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(5f) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }

    var hasCameraPermission by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    // Runtime Permission Launcher
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

    // Check permissions on mount
    LaunchedEffect(Unit) {
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
        // Camera Preview View
        if (hasCameraPermission) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            val newZoom = (currentZoom * zoom).coerceIn(1f, maxZoom)
                            currentZoom = newZoom
                            cameraControl?.setZoomRatio(newZoom)
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
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .setFlashMode(uiState.flashMode)
                            .build()
                        imageCapture = capture

                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(uiState.lensFacing)
                            .build()

                        try {
                            cameraProvider.unbindAll()
                            val cam = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
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
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                update = {
                    imageCapture?.flashMode = uiState.flashMode
                }
            )
        } else {
            // Permission Rationale fallback
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
                        text = "Camera Permission Required",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "GPS Map Camera needs camera access to capture photos with visible location stamps.",
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
                        Text("Grant Permission", color = CyanPrimary, fontSize = 16.sp)
                    }
                }
            }
        }

        // Tap-to-focus ring indicator
        focusPoint?.let { pt ->
            LaunchedEffect(pt) {
                kotlinx.coroutines.delay(1200)
                focusPoint = null
            }
            val xDp = with(density) { pt.x.toDp() - 34.dp }
            val yDp = with(density) { pt.y.toDp() - 34.dp }
            Box(
                modifier = Modifier
                    .offset(x = xDp, y = yDp)
                    .size(68.dp)
                    .border(2.dp, CyanPrimary, CircleShape)
            )
        }

        // Live Viewfinder Watermark Overlay
        ViewfinderWatermarkBadge(
            config = stampConfig,
            uiState = uiState
        )

        // Top Status & Controls Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 42.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash Mode Toggle
            IconButton(
                onClick = { viewModel.cycleFlashMode() },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0x9910141C), CircleShape)
            ) {
                Icon(
                    imageVector = when (uiState.flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    },
                    contentDescription = "Flash mode",
                    tint = if (uiState.flashMode != ImageCapture.FLASH_MODE_OFF) CyanPrimary else Color.White
                )
            }

            // GPS Status Pill
            GpsStatusPill(
                locationState = uiState.locationState,
                onEnableGpsClicked = {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                },
                onRequestPermissionClicked = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )

            // Settings Button
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0x9910141C), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }
        }

        // Quick Zoom Selector Pills
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 125.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x9910141C))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(1.0f, 2.0f, 5.0f).forEach { zoomLevel ->
                val isSelected = kotlin.math.abs(currentZoom - zoomLevel) < 0.25f
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) CyanPrimary else Color.Transparent)
                        .clickable {
                            val clamped = zoomLevel.coerceAtMost(maxZoom)
                            currentZoom = clamped
                            cameraControl?.setZoomRatio(clamped)
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${zoomLevel.toInt()}x",
                        color = if (isSelected) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Bottom Controls Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xBB080C12))
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Last Photo Thumbnail / Gallery
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1E2836))
                        .border(1.5.dp, Color(0xFF37474F), RoundedCornerShape(14.dp))
                        .clickable {
                            if (uiState.lastPhoto != null) {
                                onOpenPhotoDetail(uiState.lastPhoto!!)
                            } else {
                                onNavigateToGallery()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.lastPhoto != null) {
                        AsyncImage(
                            model = uiState.lastPhoto!!.uri,
                            contentDescription = "Latest photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = Color.LightGray,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Center: Shutter Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(4.dp, CyanPrimary, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(if (uiState.isCapturing || uiState.isSaving) Color.DarkGray else Color.White)
                        .clickable(enabled = !uiState.isCapturing && !uiState.isSaving && hasCameraPermission) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            imageCapture?.let { viewModel.capturePhoto(context, it) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            color = CyanPrimary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Right: Switch Lens Button
                IconButton(
                    onClick = { viewModel.toggleLens() },
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0x991E2836), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch camera",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Info / Error Floating Banner
        AnimatedVisibility(
            visible = uiState.infoMessage != null || uiState.errorMessage != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp, start = 20.dp, end = 20.dp)
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
    }
}

@Composable
fun GpsStatusPill(
    locationState: LocationState,
    onEnableGpsClicked: () -> Unit,
    onRequestPermissionClicked: () -> Unit
) {
    val (dotColor, statusText, icon) = when (locationState) {
        is LocationState.Ready -> {
            val acc = locationState.snapshot.accuracyMeters
            val text = if (acc > 0) {
                String.format(Locale.US, "GPS ±%.0fm", acc)
            } else {
                "GPS Locked"
            }
            Triple(AccentGreen, text, Icons.Default.GpsFixed)
        }
        is LocationState.Acquiring -> {
            Triple(AccentAmber, "Acquiring GPS…", Icons.Default.GpsNotFixed)
        }
        is LocationState.LocationDisabled -> {
            Triple(AccentAmber, "Location Disabled", Icons.Default.GpsOff)
        }
        is LocationState.PermissionRequired -> {
            Triple(AccentRed, "Location Needed", Icons.Default.GpsOff)
        }
        is LocationState.Unavailable -> {
            Triple(AccentRed, "No GPS Fix", Icons.Default.GpsOff)
        }
        is LocationState.Idle -> {
            Triple(Color.Gray, "GPS Inactive", Icons.Default.GpsOff)
        }
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xCC10141C))
            .border(1.dp, dotColor.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .clickable {
                when (locationState) {
                    is LocationState.LocationDisabled -> onEnableGpsClicked()
                    is LocationState.PermissionRequired -> onRequestPermissionClicked()
                    else -> {}
                }
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = dotColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = statusText,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun ViewfinderWatermarkBadge(
    config: StampConfig,
    uiState: CameraUiState
) {
    val liveDate = remember(config.dateTimePattern) {
        val sdf = try {
            SimpleDateFormat(config.dateTimePattern, Locale.getDefault())
        } catch (_: Exception) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        }
        sdf.format(Date())
    }

    val locSnapshot = when (val s = uiState.locationState) {
        is LocationState.Ready -> s.snapshot
        else -> null
    }

    val coordText = remember(locSnapshot, config.coordinateFormat) {
        if (locSnapshot != null) {
            StampConfig.formatCoordinates(locSnapshot.latitude, locSnapshot.longitude, config.coordinateFormat)
        } else {
            when (uiState.locationState) {
                is LocationState.Acquiring -> "Acquiring GPS fix…"
                is LocationState.LocationDisabled -> "Location Disabled"
                is LocationState.PermissionRequired -> "Permission Required"
                else -> "No GPS Lock"
            }
        }
    }

    val accAltText = remember(locSnapshot, config.showAccuracy, config.showAltitude) {
        buildString {
            if (config.showAccuracy && locSnapshot != null && locSnapshot.accuracyMeters > 0) {
                append(String.format(Locale.US, "Accuracy: ±%.1fm", locSnapshot.accuracyMeters))
            }
            if (config.showAltitude && locSnapshot?.altitudeMeters != null) {
                if (isNotEmpty()) append(" • ")
                append(String.format(Locale.US, "Alt: %.1fm", locSnapshot.altitudeMeters))
            }
        }.takeIf { it.isNotBlank() }
    }

    val alignment = when (config.position) {
        StampPosition.TOP_LEFT -> Alignment.TopStart
        StampPosition.TOP_RIGHT -> Alignment.TopEnd
        StampPosition.BOTTOM_LEFT -> Alignment.BottomStart
        StampPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
    }

    val paddingModifier = when (config.position) {
        StampPosition.TOP_LEFT -> Modifier.padding(top = 98.dp, start = 16.dp, end = 60.dp)
        StampPosition.TOP_RIGHT -> Modifier.padding(top = 98.dp, end = 16.dp, start = 60.dp)
        StampPosition.BOTTOM_LEFT -> Modifier.padding(bottom = 175.dp, start = 16.dp, end = 60.dp)
        StampPosition.BOTTOM_RIGHT -> Modifier.padding(bottom = 175.dp, end = 16.dp, start = 60.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(paddingModifier)
    ) {
        Box(
            modifier = Modifier
                .align(alignment)
                .then(
                    when (config.style) {
                        StampStyle.FROSTED_GLASS -> Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xB310151E))
                            .border(1.dp, Color(0x6600E5FF), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                        StampStyle.HIGH_CONTRAST_DARK -> Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xD905080C))
                            .border(1.dp, Color(0xFF223040), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                        StampStyle.CLEAN_OUTLINE -> Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x4D000000))
                            .padding(8.dp)
                    }
                )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (config.style == StampStyle.FROSTED_GLASS) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(44.dp)
                            .background(CyanPrimary, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column {
                    if (config.showCustomTitle && config.customTitle.isNotBlank()) {
                        Text(
                            text = config.customTitle.uppercase(),
                            color = CyanPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (config.showDateTime) {
                        Text(
                            text = liveDate,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (config.showCoordinates) {
                        Text(
                            text = coordText,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (!accAltText.isNullOrBlank()) {
                        Text(
                            text = accAltText,
                            color = Color(0xFFCAD1DB),
                            fontSize = 9.sp
                        )
                    }
                    if (config.showAddress && !uiState.currentAddress.isNullOrBlank()) {
                        Text(
                            text = uiState.currentAddress,
                            color = Color(0xFFB0BCCC),
                            fontSize = 9.sp,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

