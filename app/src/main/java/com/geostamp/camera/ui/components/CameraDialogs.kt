package com.geostamp.camera.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geostamp.camera.data.model.StampConfig
import com.geostamp.camera.data.model.StampStyle
import com.geostamp.camera.location.DetailedAddress
import com.geostamp.camera.location.LocationState
import com.geostamp.camera.ui.theme.AccentGreen
import com.geostamp.camera.ui.theme.CyanPrimary
import java.util.Locale

private val BrandYellow = Color(0xFFFFCC00)

// 1. Template Chooser Bottom Sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateChooserSheet(
    currentStyle: StampStyle,
    onSelectStyle: (StampStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val templates = listOf(
        TemplateItem(
            style = StampStyle.CLASSIC_MAP_BADGE,
            title = "Classic GPS Map Badge",
            subtitle = "Live satellite map tile, coordinates, Plus Code, compass, weather & sensors",
            tag = "Most Popular"
        ),
        TemplateItem(
            style = StampStyle.MODERN_MINIMAL,
            title = "Modern Minimal Pill",
            subtitle = "Sleek compact badge with circular satellite map, coordinates & climate chip",
            tag = "Compact"
        ),
        TemplateItem(
            style = StampStyle.SURVEYOR_GRID,
            title = "Surveyor / Technical Grid",
            subtitle = "Engineering table grid with bordered cells for Project, Inspector & GPS",
            tag = "Technical"
        ),
        TemplateItem(
            style = StampStyle.FROSTED_GLASS,
            title = "Frosted Glass Badge",
            subtitle = "Translucent acrylic card with cyan glow border and vertical accent bar",
            tag = "Aesthetic"
        ),
        TemplateItem(
            style = StampStyle.HIGH_CONTRAST_DARK,
            title = "High Contrast Dark",
            subtitle = "Deep solid black background with high-visibility yellow & bold white text",
            tag = "High Visibility"
        ),
        TemplateItem(
            style = StampStyle.CLEAN_OUTLINE,
            title = "Clean Text Outline",
            subtitle = "Minimalist text with drop-shadow outline directly on image without card",
            tag = "Minimal"
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141C28)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Choose Watermark Template",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onDismiss) {
                    Text("Close", color = BrandYellow)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(templates) { item ->
                    val isSelected = item.style == currentStyle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFF1B2A3D) else Color(0xFF1A2230))
                            .border(
                                width = if (isSelected) 1.8.dp else 1.dp,
                                color = if (isSelected) BrandYellow else Color(0xFF283648),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onSelectStyle(item.style)
                                onDismiss()
                            }
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.title,
                                        color = if (isSelected) BrandYellow else Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isSelected) BrandYellow.copy(alpha = 0.2f) else Color(0xFF243242))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = item.tag,
                                            color = if (isSelected) BrandYellow else Color.LightGray,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.subtitle,
                                    color = Color(0xFFB0BEC5),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .background(BrandYellow, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class TemplateItem(
    val style: StampStyle,
    val title: String,
    val subtitle: String,
    val tag: String
)

// 2. High-Tech Location Details Dialog
@Composable
fun LocationDetailsDialog(
    locationState: LocationState,
    detailedAddress: DetailedAddress?,
    plusCode: String?,
    onRefreshFix: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val loc = when (locationState) {
        is LocationState.Ready -> locationState.snapshot
        else -> null
    }

    fun copyCoordinates() {
        if (loc != null) {
            val coords = String.format(Locale.US, "%.6f, %.6f", loc.latitude, loc.longitude)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("GPS Coordinates", coords))
            Toast.makeText(context, "Coordinates copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    fun openInMaps() {
        if (loc != null) {
            val uri = Uri.parse("geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}(GPS Fix)")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                val webUri = Uri.parse("https://maps.google.com/?q=${loc.latitude},${loc.longitude}")
                context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("GPS Satellite Fix", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (loc != null) AccentGreen.copy(alpha = 0.2f) else BrandYellow.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (loc != null) "±${String.format(Locale.US, "%.1fm", loc.accuracyMeters)} Locked" else "Acquiring...",
                        color = if (loc != null) AccentGreen else BrandYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (loc != null) {
                    DetailRow("Latitude", String.format(Locale.US, "%.6f°", loc.latitude))
                    DetailRow("Longitude", String.format(Locale.US, "%.6f°", loc.longitude))
                    DetailRow("Accuracy", "±${String.format(Locale.US, "%.1fm", loc.accuracyMeters)} (High Precision)")
                    loc.altitudeMeters?.let {
                        DetailRow("Altitude", String.format(Locale.US, "%.1f m above sea level", it))
                    }
                    if (!plusCode.isNullOrBlank()) {
                        DetailRow("Plus Code", plusCode)
                    }
                    val addr = detailedAddress?.fullStreetAddress
                    if (!addr.isNullOrBlank()) {
                        Column {
                            Text("Address", color = Color.Gray, fontSize = 11.sp)
                            Text(addr, color = Color.White, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF223040))
                                .clickable { copyCoordinates() }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ContentCopy, null, tint = CyanPrimary, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Coords", color = Color.White, fontSize = 12.sp)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF223040))
                                .clickable { openInMaps() }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Map, null, tint = CyanPrimary, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("View on Map", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    Text(
                        "Connecting to GPS satellites and obtaining fresh latitude, longitude, and elevation fix...",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onRefreshFix()
                onDismiss()
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, null, tint = BrandYellow, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refresh Fix", color = BrandYellow, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.White)
            }
        },
        containerColor = Color(0xFF182230)
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// 3. Notes, Title, and Inspector Tag Dialog
@Composable
fun NotesAndTagsDialog(
    config: StampConfig,
    onSave: (title: String, person: String, company: String, notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(config.customTitle) }
    var person by remember { mutableStateOf(if (config.personName == "-") "" else config.personName) }
    var company by remember { mutableStateOf(config.companyName) }
    var notes by remember { mutableStateOf(config.notes) }

    val presets = listOf("Site Inspection", "Land Survey", "Construction", "Delivery Proof", "Field Audit")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Watermark Title & Notes", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.take(3).forEach { p ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF223040))
                                .clickable { title = p }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(p, color = CyanPrimary, fontSize = 10.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Project / Stamp Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandYellow,
                        focusedLabelColor = BrandYellow
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = person,
                    onValueChange = { person = it },
                    label = { Text("Inspector / Person Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandYellow,
                        focusedLabelColor = BrandYellow
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Company / Organization (Optional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandYellow,
                        focusedLabelColor = BrandYellow
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Custom Field Notes") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandYellow,
                        focusedLabelColor = BrandYellow
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(title, person.ifBlank { "-" }, company, notes)
                onDismiss()
            }) {
                Text("Save", color = BrandYellow, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.LightGray)
            }
        },
        containerColor = Color(0xFF182230)
    )
}

// 4. Project Folder / Album Dialog
@Composable
fun ProjectFolderDialog(
    currentFolder: String,
    onSelectFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newFolderName by remember { mutableStateOf("") }
    val defaultFolders = listOf("Default", "Site Survey", "Inspection Work", "Road Work", "Delivery Proof")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Project Folder", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Photos will be tagged and stored under this album project:", color = Color.Gray, fontSize = 12.sp)

                defaultFolders.forEach { f ->
                    val isSelected = f == currentFolder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF1E2D40) else Color(0xFF151D28))
                            .border(1.dp, if (isSelected) BrandYellow else Color(0xFF263238), RoundedCornerShape(8.dp))
                            .clickable {
                                onSelectFolder(f)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, tint = if (isSelected) BrandYellow else Color.Gray, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(f, color = if (isSelected) BrandYellow else Color.White, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = { Text("Create new folder name...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyanPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (newFolderName.isNotBlank()) {
                    onSelectFolder(newFolderName.trim())
                }
                onDismiss()
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CreateNewFolder, null, tint = BrandYellow, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Apply Folder", color = BrandYellow, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White)
            }
        },
        containerColor = Color(0xFF182230)
    )
}
