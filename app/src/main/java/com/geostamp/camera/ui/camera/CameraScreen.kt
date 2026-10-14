package com.geostamp.camera.ui.camera

import android.Manifest
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.geostamp.camera.data.model.CapturedPhoto
import com.geostamp.camera.data.model.StampConfig
import com.geostamp.camera.data.model.StampStyle
import com.geostamp.camera.location.LocationState
import com.geostamp.camera.ui.theme.AccentAmber
import com.geostamp.camera.ui.theme.AccentGreen
import com.geostamp.camera.ui.theme.AccentRed
import com.geostamp.camera.ui.theme.CyanPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val density = LocalDensity.current

    val uiState by viewModel.uiState.collectAsState()
    val stampConfig by viewModel.stampConfig.collectAsState()

    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var currentZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(8f) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }

    var hasCameraPermission by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(false) }

    var showNotesDialog by remember { mutableStateOf(false) }
    var showLocationDetailsDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }

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
                        Text("Grant Permission", color = BrandYellow, fontSize = 16.sp)
                    }
                }
            }
        }

        // 2. Dual Concentric Focus Reticle (as shown in reference screenshot)
        val defaultReticleCenter = Offset(
            x = LocalContext.current.resources.displayMetrics.widthPixels / 2f,
            y = LocalContext.current.resources.displayMetrics.heightPixels * 0.45f
        )
        val activeReticlePoint = focusPoint ?: defaultReticleCenter

        Box(
            modifier = Modifier
                .offset(
                    x = with(density) { (activeReticlePoint.x).toDp() - 65.dp },
                    y = with(density) { (activeReticlePoint.y).toDp() - 65.dp }
                )
                .size(130.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer thin circle
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .border(1.dp, Color(0x66FFFFFF), CircleShape)
            )
            // Center yellow crosshair
            Text(
                text = "+",
                color = BrandYellow,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.offset(y = (-2).dp)
            )
            // Secondary concentric offset circle (as seen in reference)
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .offset(x = 10.dp, y = 14.dp)
                    .border(1.5.dp, Color(0xAAFFFFFF), CircleShape)
            )
            // White crosshair in secondary circle
            Text(
                text = "+",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.offset(x = 10.dp, y = 14.dp)
            )
        }

        // 3. Vertical Exposure Slider on Right Side (with sun icon and yellow active track)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .height(220.dp)
                .width(36.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = -dragAmount.y / 200f
                        val newProgress = (uiState.exposureProgress + delta).coerceIn(0f, 1f)
                        viewModel.setExposureProgress(newProgress, cameraControl)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Track background line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(180.dp)
                    .background(Color(0x55FFFFFF))
            )
            // Active yellow line
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(2.5.dp)
                    .height((180 * uiState.exposureProgress).dp)
                    .background(BrandYellow)
            )
            // Sun Icon Knob
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-(180 * uiState.exposureProgress)).dp)
                    .size(28.dp)
                    .background(Color.Black, CircleShape)
                    .border(1.dp, BrandYellow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = "Exposure",
                    tint = BrandYellow,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 4. Aspect Ratio Badge at Top Left (e.g. [4:3])
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 98.dp, start = 18.dp)
                .size(24.dp)
                .border(1.5.dp, Color(0xCCFFFFFF), RoundedCornerShape(4.dp))
        )

        // 5. Top Controls Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 38.dp, start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Aspect Ratio toggle
            IconButton(onClick = { /* Aspect ratio cycle */ }) {
                Icon(
                    imageVector = Icons.Default.AspectRatio,
                    contentDescription = "Aspect Ratio",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Flash Mode Toggle
            IconButton(onClick = { viewModel.cycleFlashMode() }) {
                Icon(
                    imageVector = when (uiState.flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    },
                    contentDescription = "Flash",
                    tint = if (uiState.flashMode != ImageCapture.FLASH_MODE_OFF) BrandYellow else Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Notes / Title dialog
            IconButton(onClick = { showNotesDialog = true }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                    contentDescription = "Custom Tag",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Location Pin Details
            IconButton(onClick = { showLocationDetailsDialog = true }) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location info",
                    tint = when (uiState.locationState) {
                        is LocationState.Ready -> AccentGreen
                        is LocationState.Acquiring -> AccentAmber
                        else -> AccentRed
                    },
                    modifier = Modifier.size(26.dp)
                )
            }

            // Switch camera lens
            IconButton(onClick = { viewModel.toggleLens() }) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch Camera",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Settings
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // 6. Live Watermark Overlay Badge (Classic Map on left, rich metadata on right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 190.dp, start = 12.dp, end = 12.dp)
        ) {
            ClassicMapWatermarkCard(
                uiState = uiState,
                config = stampConfig
            )
        }

        // 7. Zoom Pill Switchers (1x in yellow circle, 2x in dark circle)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 145.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1x Button
            val is1x = currentZoom < 1.6f
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (is1x) BrandYellow else Color(0x9910141C))
                    .clickable {
                        currentZoom = 1.0f
                        cameraControl?.setZoomRatio(1.0f)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "1x",
                    color = if (is1x) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            // 2x Button
            val is2x = currentZoom >= 1.6f
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (is2x) BrandYellow else Color(0x9910141C))
                    .clickable {
                        currentZoom = 2.0f.coerceAtMost(maxZoom)
                        cameraControl?.setZoomRatio(currentZoom)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "2x",
                    color = if (is2x) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // 8. Mode Selector (PHOTO / VIDEO)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PHOTO
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(BrandYellow)
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "PHOTO",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            // VIDEO
            Text(
                text = "VIDEO",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable {
                        viewModel.setMode(CameraMode.VIDEO)
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        // 9. Bottom Controls Bar (Preview, Location, Shutter, Default, Templates)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xE6080C12))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Preview
                BottomBarItem(
                    label = "Preview",
                    onClick = onNavigateToGallery
                ) {
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

                // 2. Location
                BottomBarItem(
                    label = "Location",
                    onClick = { showLocationDetailsDialog = true }
                ) {
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
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // 3. Shutter Button
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .border(3.5.dp, Color.White, CircleShape)
                        .padding(5.dp)
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
                            color = BrandYellow,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                // 4. Default / Folder
                BottomBarItem(
                    label = "Default",
                    onClick = onNavigateToGallery
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0x991E2836)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Default Folder",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 5. Templates
                BottomBarItem(
                    label = "Templates",
                    onClick = { showTemplateDialog = true }
                ) {
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
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
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

        // Dialog: Custom Note / Person Name
        if (showNotesDialog) {
            var tempName by remember { mutableStateOf(stampConfig.personName) }
            var tempTitle by remember { mutableStateOf(stampConfig.customTitle) }

            AlertDialog(
                onDismissRequest = { showNotesDialog = false },
                title = { Text("Watermark Title & Name", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = tempTitle,
                            onValueChange = { tempTitle = it },
                            label = { Text("Title") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = BrandYellow,
                                focusedLabelColor = BrandYellow
                            )
                        )
                        OutlinedTextField(
                            value = tempName,
                            onValueChange = { tempName = it },
                            label = { Text("Person Name") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = BrandYellow,
                                focusedLabelColor = BrandYellow
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateCustomTitle(tempTitle)
                        viewModel.updatePersonName(tempName)
                        showNotesDialog = false
                    }) {
                        Text("Save", color = BrandYellow, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNotesDialog = false }) {
                        Text("Cancel", color = Color.LightGray)
                    }
                },
                containerColor = Color(0xFF1E2836)
            )
        }

        // Dialog: Exact Location Info & Refresh
        if (showLocationDetailsDialog) {
            val loc = when (val s = uiState.locationState) {
                is LocationState.Ready -> s.snapshot
                else -> null
            }

            AlertDialog(
                onDismissRequest = { showLocationDetailsDialog = false },
                title = { Text("Exact GPS Fix", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (loc != null) {
                            Text("Latitude: ${loc.latitude}", color = Color.White, fontSize = 14.sp)
                            Text("Longitude: ${loc.longitude}", color = Color.White, fontSize = 14.sp)
                            Text("Accuracy: ±${String.format(Locale.US, "%.1fm", loc.accuracyMeters)}", color = AccentGreen, fontWeight = FontWeight.Bold)
                            if (loc.altitudeMeters != null) {
                                Text("Altitude: ${String.format(Locale.US, "%.1fm", loc.altitudeMeters)}", color = Color.LightGray, fontSize = 14.sp)
                            }
                            if (!uiState.plusCode.isNullOrBlank()) {
                                Text("Plus Code: ${uiState.plusCode}", color = BrandYellow, fontSize = 14.sp)
                            }
                            if (!uiState.currentAddress.isNullOrBlank()) {
                                Text("Address:\n${uiState.currentAddress}", color = Color.LightGray, fontSize = 13.sp)
                            }
                        } else {
                            Text("Acquiring high accuracy GPS satellite fix...", color = AccentAmber)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.startLocation()
                        showLocationDetailsDialog = false
                    }) {
                        Text("Refresh Fix", color = BrandYellow, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLocationDetailsDialog = false }) {
                        Text("Close", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1E2836)
            )
        }

        // Dialog: Template Chooser
        if (showTemplateDialog) {
            AlertDialog(
                onDismissRequest = { showTemplateDialog = false },
                title = { Text("Choose Watermark Template", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Selected Template: Classic Map Badge (Matches Reference Image)", color = BrandYellow, fontSize = 14.sp)
                        Text("Includes live satellite map tile, coordinates, Plus Code, compass, weather, and sensors.", color = Color.LightGray, fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTemplateDialog = false }) {
                        Text("Done", color = BrandYellow, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color(0xFF1E2836)
            )
        }
    }
}

@Composable
fun ClassicMapWatermarkCard(
    uiState: CameraUiState,
    config: StampConfig
) {
    val dateText = remember(config.dateTimePattern) {
        val sdf = try {
            SimpleDateFormat(config.dateTimePattern, Locale.getDefault())
        } catch (_: Exception) {
            SimpleDateFormat("EEEE, dd/MM/yyyy hh:mm a 'GMT' Z", Locale.getDefault())
        }
        sdf.format(Date())
    }

    val loc = when (val s = uiState.locationState) {
        is LocationState.Ready -> s.snapshot
        else -> null
    }

    val coordString = remember(loc, config.coordinateFormat) {
        if (loc != null) {
            StampConfig.formatCoordinates(loc.latitude, loc.longitude, config.coordinateFormat)
        } else {
            "Lat 22.374124° Long 82.735036°"
        }
    }

    val plusCodeString = uiState.plusCode ?: "7MJ49PFP+J2"
    val headerString = uiState.detailedAddress?.shortHeader ?: "Korba, Chhattisgarh, India 🇮🇳"
    val streetString = uiState.detailedAddress?.fullStreetAddress ?: "223, Rampur, Korba, Chhattisgarh 495677, India"

    val weather = uiState.weatherSnapshot
    val sensor = uiState.sensorSnapshot

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xE6080D14))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Square Map Snapshot
            Box(
                modifier = Modifier
                    .size(105.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E272E))
                    .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(6.dp))
            ) {
                if (uiState.mapSnapshot != null) {
                    Image(
                        bitmap = uiState.mapSnapshot!!.asImageBitmap(),
                        contentDescription = "Map Snapshot",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Fallback radar
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Google", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right: Text Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Top Header Row with Logo Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = headerString,
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )

                    // Mini Logo Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x99101C2E))
                            .border(0.8.dp, CyanPrimary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "📷 GPS Map Camera",
                            color = Color.White,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Street Address
                Text(
                    text = streetString,
                    color = Color(0xFFD2DCE6),
                    fontSize = 9.sp,
                    maxLines = 1
                )

                // Lat & Long
                Text(
                    text = coordString,
                    color = Color(0xFFFFF176),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )

                // Plus Code
                Text(
                    text = "Plus Code : $plusCodeString",
                    color = Color(0xFFB0BEC5),
                    fontSize = 8.5.sp
                )

                // Date & Time
                Text(
                    text = dateText,
                    color = Color.White,
                    fontSize = 8.5.sp,
                    maxLines = 1
                )

                // Person Name
                if (config.personName.isNotBlank() && config.personName != "-") {
                    Text(
                        text = "Person Name : ${config.personName}",
                        color = Color(0xFFCAD1DB),
                        fontSize = 8.5.sp
                    )
                }

                // Weather & Compass
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${weather.weatherEmoji} ${String.format(Locale.US, "%.1f° C", weather.temperatureCelsius)}",
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "🧭 ${sensor.compassDegrees}° ${sensor.compassDirection}",
                        color = Color(0xFF80D8FF),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Bottom Environmental Sensor Row
                Text(
                    text = "💨 ${String.format(Locale.US, "%.1f km/h", weather.windSpeedKmh)}   💧 ${weather.humidityPercent}%   🧭 ${String.format(Locale.US, "%.0f hpa", sensor.pressureHpa)}   🧲 ${String.format(Locale.US, "%.1f µT", sensor.magneticFieldMicroTesla)}",
                    color = Color(0xFF80D8FF),
                    fontSize = 8.sp,
                    maxLines = 1
                )
            }
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
