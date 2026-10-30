package com.geostamp.camera.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.geostamp.camera.data.model.AspectRatioOption
import com.geostamp.camera.data.model.CoordinateFormat
import com.geostamp.camera.data.model.StampConfig
import com.geostamp.camera.data.model.StampFontColor
import com.geostamp.camera.data.model.StampFontSize
import com.geostamp.camera.data.model.StampLayout
import com.geostamp.camera.data.model.StampPosition
import com.geostamp.camera.data.model.StampSize
import com.geostamp.camera.data.model.StampStyle
import com.geostamp.camera.data.repository.SettingsRepository
import com.geostamp.camera.ui.theme.AccentRed
import com.geostamp.camera.ui.theme.CyanPrimary
import com.geostamp.camera.ui.theme.OnyxBackground
import com.geostamp.camera.ui.theme.OnyxSurface

private val BrandYellow = Color(0xFFFFCC00)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPrivacy: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context) }
    var config by remember { mutableStateOf(repository.configFlow.value) }
    var showResetDialog by remember { mutableStateOf(false) }

    fun update(newConfig: StampConfig) {
        config = newConfig
        repository.updateConfig(newConfig)
    }

    Scaffold(
        containerColor = OnyxBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Stamp & Camera Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
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
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset Defaults",
                            tint = BrandYellow
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OnyxSurface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // 1. LIVE PREVIEW CARD
            SectionTitle("LIVE WATERMARK PREVIEW")
            LiveStampPreviewCard(config = config)

            Spacer(modifier = Modifier.height(24.dp))

            // 2. ASPECT RATIO
            SectionTitle("CAMERA ASPECT RATIO")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AspectRatioButton(
                    title = "4:3 (Standard)",
                    isSelected = config.aspectRatio == AspectRatioOption.RATIO_4_3,
                    modifier = Modifier.weight(1f)
                ) { update(config.copy(aspectRatio = AspectRatioOption.RATIO_4_3)) }

                AspectRatioButton(
                    title = "16:9 (Wide)",
                    isSelected = config.aspectRatio == AspectRatioOption.RATIO_16_9,
                    modifier = Modifier.weight(1f)
                ) { update(config.copy(aspectRatio = AspectRatioOption.RATIO_16_9)) }

                AspectRatioButton(
                    title = "1:1 (Square)",
                    isSelected = config.aspectRatio == AspectRatioOption.RATIO_1_1,
                    modifier = Modifier.weight(1f)
                ) { update(config.copy(aspectRatio = AspectRatioOption.RATIO_1_1)) }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. STAMP POSITION
            SectionTitle("STAMP POSITION ON PHOTO")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PositionButton(
                    title = "Top Left",
                    isSelected = config.position == StampPosition.TOP_LEFT,
                    modifier = Modifier.weight(1f)
                ) { update(config.copy(position = StampPosition.TOP_LEFT)) }

                PositionButton(
                    title = "Top Right",
                    isSelected = config.position == StampPosition.TOP_RIGHT,
                    modifier = Modifier.weight(1f)
                ) { update(config.copy(position = StampPosition.TOP_RIGHT)) }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PositionButton(
                    title = "Bottom Left",
                    isSelected = config.position == StampPosition.BOTTOM_LEFT,
                    modifier = Modifier.weight(1f)
                ) { update(config.copy(position = StampPosition.BOTTOM_LEFT)) }

                PositionButton(
                    title = "Bottom Right",
                    isSelected = config.position == StampPosition.BOTTOM_RIGHT,
                    modifier = Modifier.weight(1f)
                ) { update(config.copy(position = StampPosition.BOTTOM_RIGHT)) }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // 3b. WATERMARK APPEARANCE & CUSTOMIZATION
            SectionTitle("WATERMARK APPEARANCE")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PositionButton(
                    title = "Horizontal",
                    isSelected = config.layout == StampLayout.HORIZONTAL,
                    modifier = Modifier.weight(1f)
                ) { update(config.copy(layout = StampLayout.HORIZONTAL)) }

                PositionButton(
                    title = "Vertical",
                    isSelected = config.layout == StampLayout.VERTICAL,
                    modifier = Modifier.weight(1f)
                ) { update(config.copy(layout = StampLayout.VERTICAL)) }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("Watermark Size", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StampSize.entries.forEach { size ->
                    PositionButton(
                        title = size.label,
                        isSelected = config.stampSize == size,
                        modifier = Modifier.weight(1f)
                    ) { update(config.copy(stampSize = size)) }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("Font Size", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StampFontSize.entries.forEach { size ->
                    PositionButton(
                        title = size.label,
                        isSelected = config.fontSize == size,
                        modifier = Modifier.weight(1f)
                    ) { update(config.copy(fontSize = size)) }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("Font Color", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StampFontColor.entries.forEach { colorOption ->
                    val swatch = remember(colorOption) {
                        try {
                            Color(android.graphics.Color.parseColor(colorOption.hex))
                        } catch (_: Exception) {
                            Color.White
                        }
                    }
                    val selected = config.fontColor == colorOption
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(swatch)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) BrandYellow else Color(0xFF233040),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { update(config.copy(fontColor = colorOption)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Background Transparency", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = "${((1f - config.backgroundAlpha) * 100).toInt()}%",
                    color = CyanPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = config.backgroundAlpha,
                onValueChange = { update(config.copy(backgroundAlpha = it)) },
                valueRange = 0.35f..0.95f,
                colors = SliderDefaults.colors(
                    thumbColor = BrandYellow,
                    activeTrackColor = BrandYellow,
                    inactiveTrackColor = Color(0xFF233040)
                )
            )

            Spacer(modifier = Modifier.height(26.dp))

            // 4. STAMP VISUAL STYLE (All 6 Styles)
            SectionTitle("STAMP VISUAL STYLE")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StyleSelectorItem(
                    title = "Classic GeoStamp Camera",
                    desc = "Premium horizontal card: satellite map with red pin on the left, GPS details on the right",
                    isSelected = config.style == StampStyle.CLASSIC_MAP_BADGE
                ) { update(config.copy(style = StampStyle.CLASSIC_MAP_BADGE)) }

                StyleSelectorItem(
                    title = "Modern Minimal Pill",
                    desc = "Sleek compact horizontal badge with circular mini-map & climate chip",
                    isSelected = config.style == StampStyle.MODERN_MINIMAL
                ) { update(config.copy(style = StampStyle.MODERN_MINIMAL)) }

                StyleSelectorItem(
                    title = "Surveyor / Technical Grid",
                    desc = "Engineering inspection table with high-visibility grid borders for field reports",
                    isSelected = config.style == StampStyle.SURVEYOR_GRID
                ) { update(config.copy(style = StampStyle.SURVEYOR_GRID)) }

                StyleSelectorItem(
                    title = "Frosted Glass Badge",
                    desc = "Translucent dark glass card with cyan vertical bar & glowing border",
                    isSelected = config.style == StampStyle.FROSTED_GLASS
                ) { update(config.copy(style = StampStyle.FROSTED_GLASS)) }

                StyleSelectorItem(
                    title = "High Contrast Dark",
                    desc = "Solid deep black card with golden yellow & bold white text",
                    isSelected = config.style == StampStyle.HIGH_CONTRAST_DARK
                ) { update(config.copy(style = StampStyle.HIGH_CONTRAST_DARK)) }

                StyleSelectorItem(
                    title = "Clean Text Outline",
                    desc = "Minimalist white text with drop-shadow outline directly on photo",
                    isSelected = config.style == StampStyle.CLEAN_OUTLINE
                ) { update(config.copy(style = StampStyle.CLEAN_OUTLINE)) }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // 5. CUSTOM TAGS & DETAILS
            SectionTitle("CUSTOM WATERMARK LABELS")
            OutlinedTextField(
                value = config.customTitle,
                onValueChange = { update(config.copy(customTitle = it)) },
                label = { Text("Project / Custom Title") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = BrandYellow,
                    focusedLabelColor = BrandYellow
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = if (config.personName == "-") "" else config.personName,
                onValueChange = { update(config.copy(personName = it.ifBlank { "-" })) },
                label = { Text("Inspector / Person Name") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = BrandYellow,
                    focusedLabelColor = BrandYellow
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = config.companyName,
                onValueChange = { update(config.copy(companyName = it)) },
                label = { Text("Company / Organization (Optional)") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = BrandYellow,
                    focusedLabelColor = BrandYellow
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = config.notes,
                onValueChange = { update(config.copy(notes = it)) },
                label = { Text("Custom Note (Optional)") },
                placeholder = { Text("e.g. Site inspection completed") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = BrandYellow,
                    focusedLabelColor = BrandYellow
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(26.dp))

            // 6. WATERMARK CONTENT TOGGLES
            SectionTitle("INFORMATION TO INCLUDE")
            SettingToggleRow(
                title = "Local Date & Time",
                subtitle = "Format: ${config.dateTimePattern}",
                checked = config.showDateTime
            ) { update(config.copy(showDateTime = it)) }

            if (config.showDateTime) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val patterns = listOf(
                        "yyyy-MM-dd HH:mm:ss" to "24-Hour (ISO)",
                        "dd/MM/yyyy hh:mm a" to "UK/IN (12h)",
                        "MM/dd/yyyy hh:mm a" to "US (12h)"
                    )
                    patterns.forEach { (pattern, label) ->
                        val isSelected = config.dateTimePattern == pattern
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF003844) else Color(0xFF141C26))
                                .border(1.dp, if (isSelected) CyanPrimary else Color(0xFF233040), RoundedCornerShape(8.dp))
                                .clickable { update(config.copy(dateTimePattern = pattern)) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) CyanPrimary else Color.LightGray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            SettingToggleRow(
                title = "GPS Coordinates",
                subtitle = "Latitude and longitude",
                checked = config.showCoordinates
            ) { update(config.copy(showCoordinates = it)) }

            SettingToggleRow(
                title = "Reverse Geocoded Address",
                subtitle = "Street, locality, city, and country",
                checked = config.showAddress
            ) { update(config.copy(showAddress = it)) }

            SettingToggleRow(
                title = "Captured By Note",
                subtitle = "Show \"Note: Captured by ...\" in the watermark",
                checked = config.showCapturedByNote
            ) { update(config.copy(showCapturedByNote = it)) }

            SettingToggleRow(
                title = "Plus Code (Open Location Code)",
                subtitle = "Google Plus Code (e.g. 7MJ49PFP+J2)",
                checked = config.showPlusCode
            ) { update(config.copy(showPlusCode = it)) }

            SettingToggleRow(
                title = "Live Weather & Temp",
                subtitle = "Temperature, weather icon, humidity & wind speed",
                checked = config.showWeather
            ) { update(config.copy(showWeather = it)) }

            SettingToggleRow(
                title = "Sensors (Compass & Pressure)",
                subtitle = "Azimuth bearing, pressure (hPa) & magnetic field (µT)",
                checked = config.showSensors
            ) { update(config.copy(showSensors = it)) }

            SettingToggleRow(
                title = "Square Map Tile Badge",
                subtitle = "Overlay live satellite mini-map on watermark",
                checked = config.showMapSnapshot
            ) { update(config.copy(showMapSnapshot = it)) }

            SettingToggleRow(
                title = "Location Accuracy",
                subtitle = "GPS precision in meters (e.g., ±4.2m)",
                checked = config.showAccuracy
            ) { update(config.copy(showAccuracy = it)) }

            SettingToggleRow(
                title = "Altitude",
                subtitle = "Height above sea level",
                checked = config.showAltitude
            ) { update(config.copy(showAltitude = it)) }

            Spacer(modifier = Modifier.height(26.dp))

            // 7. COORDINATE FORMAT
            SectionTitle("COORDINATE FORMAT")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PositionButton(
                    title = "Decimal Degrees\n(37.7749° N)",
                    isSelected = config.coordinateFormat == CoordinateFormat.DECIMAL,
                    modifier = Modifier.weight(1f)
                ) { update(config.copy(coordinateFormat = CoordinateFormat.DECIMAL)) }

                PositionButton(
                    title = "Degrees & Minutes\n(37°46'29.6\"N)",
                    isSelected = config.coordinateFormat == CoordinateFormat.DMS,
                    modifier = Modifier.weight(1f)
                ) { update(config.copy(coordinateFormat = CoordinateFormat.DMS)) }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // 8. PRIVACY POLICY
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF161F2B))
                    .border(1.dp, Color(0xFF223040), RoundedCornerShape(14.dp))
                    .clickable(onClick = onNavigateToPrivacy)
                    .padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Privacy & Data Policy",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "100% on-device • Zero network tracking",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.LightGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }

        // Reset Dialog
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset to Defaults?", color = Color.White) },
                text = {
                    Text(
                        "Are you sure you want to reset all watermark positions, styles, and toggles to default settings?",
                        color = Color.LightGray
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        config = repository.resetToDefaults()
                        showResetDialog = false
                    }) {
                        Text("Reset", color = AccentRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1E2836)
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = BrandYellow,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun AspectRatioButton(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0xFF263428) else Color(0xFF141C26))
            .border(
                1.5.dp,
                if (isSelected) BrandYellow else Color(0xFF233040),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) BrandYellow else Color.LightGray,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun PositionButton(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF263428) else Color(0xFF141C26))
            .border(
                1.5.dp,
                if (isSelected) BrandYellow else Color(0xFF233040),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.White else Color.LightGray,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun StyleSelectorItem(
    title: String,
    desc: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF1B2836) else Color(0xFF141C26))
            .border(
                1.5.dp,
                if (isSelected) BrandYellow else Color(0xFF233040),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = title,
                color = if (isSelected) BrandYellow else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = desc,
                color = Color.LightGray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BrandYellow,
                uncheckedTrackColor = Color(0xFF233040),
                uncheckedThumbColor = Color.LightGray
            )
        )
    }
}

@Composable
fun LiveStampPreviewCard(config: StampConfig) {
    val sampleDate = remember(config.dateTimePattern) {
        StampConfig.formatDateTime(System.currentTimeMillis(), config.dateTimePattern)
    }

    val sampleCoords = remember(config.coordinateFormat) {
        StampConfig.formatCoordinates(37.774929, -122.419416, config.coordinateFormat)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0A0F17))
            .border(1.dp, Color(0xFF1E2838), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        // Grid pattern
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(0.5.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
        )

        val alignment = when (config.position) {
            StampPosition.TOP_LEFT -> Alignment.TopStart
            StampPosition.TOP_RIGHT -> Alignment.TopEnd
            StampPosition.BOTTOM_LEFT -> Alignment.BottomStart
            StampPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
        }

        Box(
            modifier = Modifier
                .align(alignment)
                .then(
                    when (config.style) {
                        StampStyle.CLASSIC_MAP_BADGE -> Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xE00A0F16))
                            .border(1.dp, Color(0x6000E5FF), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                        StampStyle.MODERN_MINIMAL -> Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xE00D1520))
                            .border(1.dp, BrandYellow, RoundedCornerShape(10.dp))
                            .padding(8.dp)
                        StampStyle.SURVEYOR_GRID -> Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xF0080C12))
                            .border(1.dp, BrandYellow, RoundedCornerShape(4.dp))
                            .padding(6.dp)
                        StampStyle.FROSTED_GLASS -> Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xD010151E))
                            .border(1.dp, Color(0x8000E5FF), RoundedCornerShape(10.dp))
                            .padding(8.dp)
                        StampStyle.HIGH_CONTRAST_DARK -> Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xF506080C))
                            .border(1.dp, Color(0xFF233040), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                        StampStyle.CLEAN_OUTLINE -> Modifier.padding(8.dp)
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
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Column {
                    if (config.showCustomTitle && config.customTitle.isNotBlank()) {
                        Text(
                            text = config.customTitle.uppercase(),
                            color = BrandYellow,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (config.showDateTime) {
                        Text(
                            text = sampleDate,
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (config.showCoordinates) {
                        Text(
                            text = sampleCoords,
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (config.showAccuracy || config.showAltitude) {
                        Text(
                            text = "±3.8m • Alt: 42.0m",
                            color = Color(0xFFCAD1DB),
                            fontSize = 8.sp
                        )
                    }
                    if (config.showAddress) {
                        Text(
                            text = "Market St, San Francisco, CA, USA",
                            color = Color(0xFFB0BCCC),
                            fontSize = 7.5.sp,
                            maxLines = 1
                        )
                    }
                    val note = StampConfig.noteLine(config)
                    if (note != null) {
                        Text(
                            text = note,
                            color = Color(0xFF9AA7B4),
                            fontSize = 7.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
