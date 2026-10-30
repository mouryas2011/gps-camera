package com.geostamp.camera.ui.gallery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.geostamp.camera.data.model.CapturedPhoto
import com.geostamp.camera.storage.MediaStoreHelper
import com.geostamp.camera.ui.theme.AccentGreen
import com.geostamp.camera.ui.theme.AccentRed
import com.geostamp.camera.ui.theme.CyanPrimary
import com.geostamp.camera.ui.theme.OnyxBackground
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BrandYellow = Color(0xFFFFCC00)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    photo: CapturedPhoto,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var imageScale by remember { mutableFloatStateOf(1f) }
    var imageOffset by remember { mutableStateOf(Offset.Zero) }

    val formattedDate = remember(photo.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, yyyy  hh:mm:ss a", Locale.getDefault())
        sdf.format(Date(photo.timestamp))
    }

    val formattedFileSize = remember(photo.size) {
        val mb = photo.size / (1024.0 * 1024.0)
        String.format(Locale.US, "%.2f MB", mb)
    }

    fun openInMaps() {
        if (photo.latitude != null && photo.longitude != null) {
            val label = Uri.encode(photo.address ?: photo.displayName)
            val uri = Uri.parse("geo:${photo.latitude},${photo.longitude}?q=${photo.latitude},${photo.longitude}($label)")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                val webUri = Uri.parse("https://maps.google.com/?q=${photo.latitude},${photo.longitude}")
                context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
            }
        }
    }

    fun copyCoordinates() {
        if (photo.latitude != null && photo.longitude != null) {
            val coords = String.format(Locale.US, "%.6f, %.6f", photo.latitude, photo.longitude)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("GPS Coordinates", coords))
            Toast.makeText(context, "Coordinates copied: $coords", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyAddress() {
        val addr = photo.address
        if (!addr.isNullOrBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Photo Address", addr))
            Toast.makeText(context, "Address copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = OnyxBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = photo.displayName,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (photo.latitude != null && photo.longitude != null) {
                        IconButton(onClick = { openInMaps() }) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "Open in Maps",
                                tint = CyanPrimary
                            )
                        }
                    }
                    IconButton(onClick = { MediaStoreHelper.sharePhoto(context, photo) }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = CyanPrimary
                        )
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = AccentRed
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xDD0A0E14)
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Full Screen Zoomable & Pannable Image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 220.dp)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            imageScale = (imageScale * zoom).coerceIn(1f, 5f)
                            if (imageScale > 1f) {
                                imageOffset += pan
                            } else {
                                imageOffset = Offset.Zero
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (imageScale > 1f) {
                                    imageScale = 1f
                                    imageOffset = Offset.Zero
                                } else {
                                    imageScale = 2.5f
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = photo.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = imageScale
                            scaleY = imageScale
                            translationX = imageOffset.x
                            translationY = imageOffset.y
                        }
                )
            }

            // Bottom Metadata Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0xF5101620))
                    .border(1.dp, Color(0xFF1E2838), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VERIFIED GPS STAMP METADATA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandYellow,
                            letterSpacing = 1.2.sp
                        )

                        if (photo.latitude != null && photo.longitude != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF003844))
                                        .border(1.dp, CyanPrimary, RoundedCornerShape(8.dp))
                                        .clickable { copyCoordinates() }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            tint = CyanPrimary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Coords",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF003844))
                                        .border(1.dp, CyanPrimary, RoundedCornerShape(8.dp))
                                        .clickable { openInMaps() }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Map,
                                            contentDescription = null,
                                            tint = CyanPrimary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Map",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Date & Time
                    MetadataRow(
                        icon = Icons.Default.Schedule,
                        title = "Timestamp",
                        value = formattedDate
                    )

                    // Coordinates
                    if (photo.latitude != null && photo.longitude != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val latDir = if (photo.latitude >= 0) "N" else "S"
                        val lonDir = if (photo.longitude >= 0) "E" else "W"
                        val coordsText = String.format(
                            Locale.US,
                            "%.6f° %s, %.6f° %s",
                            kotlin.math.abs(photo.latitude),
                            latDir,
                            kotlin.math.abs(photo.longitude),
                            lonDir
                        )
                        MetadataRow(
                            icon = Icons.Default.LocationOn,
                            title = "Coordinates",
                            value = coordsText
                        )
                    }

                    // Accuracy & Altitude
                    if (photo.accuracy != null || photo.altitude != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val accAltText = buildString {
                            if (photo.accuracy != null) append(String.format(Locale.US, "Accuracy: ±%.1fm", photo.accuracy))
                            if (photo.altitude != null) {
                                if (isNotEmpty()) append("  •  ")
                                append(String.format(Locale.US, "Altitude: %.1fm", photo.altitude))
                            }
                        }
                        MetadataRow(
                            icon = Icons.Default.LocationOn,
                            title = "GPS Precision",
                            value = accAltText
                        )
                    }

                    // Address
                    if (!photo.address.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        MetadataRow(
                            icon = Icons.Default.LocationOn,
                            title = "Resolved Address (Tap to copy)",
                            value = photo.address,
                            onClick = { copyAddress() }
                        )
                    }

                    // Storage
                    Spacer(modifier = Modifier.height(10.dp))
                    MetadataRow(
                        icon = Icons.Default.Storage,
                        title = "Storage",
                        value = "$formattedFileSize  •  Pictures/GeoStamp Camera"
                    )
                }
            }
        }

        // Delete Confirmation Dialog
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete Photo?", color = Color.White) },
                text = {
                    Text(
                        "Are you sure you want to permanently delete this photo from your gallery?",
                        color = Color.LightGray
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            coroutineScope.launch {
                                MediaStoreHelper.deletePhoto(context, photo)
                                onNavigateBack()
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = AccentRed)
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1E2836)
            )
        }
    }
}

@Composable
private fun MetadataRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color(0xFF1A2330), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CyanPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
