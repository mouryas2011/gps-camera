package com.geostamp.camera.ui.settings

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import com.geostamp.camera.data.model.CoordinateFormat
import com.geostamp.camera.data.model.StampConfig
import com.geostamp.camera.data.model.StampPosition
import com.geostamp.camera.data.model.StampStyle
import com.geostamp.camera.data.repository.SettingsRepository
import com.geostamp.camera.ui.theme.CyanPrimary
import com.geostamp.camera.ui.theme.OnyxBackground
import com.geostamp.camera.ui.theme.OnyxSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPrivacy: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context) }
    var config by remember { mutableStateOf(repository.configFlow.value) }

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
                        text = "Stamp Settings",
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
            // LIVE PREVIEW CARD
            SectionTitle("LIVE WATERMARK PREVIEW")
            LiveStampPreviewCard(config = config)

            Spacer(modifier = Modifier.height(24.dp))

            // STAMP POSITION
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

            // STAMP VISUAL STYLE
            SectionTitle("STAMP VISUAL STYLE")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StyleSelectorItem(
                    title = "Classic GPS Map Camera",
                    desc = "Square satellite map badge with red pin, Plus Code, weather & sensors",
                    isSelected = config.style == StampStyle.CLASSIC_MAP_BADGE
                ) { update(config.copy(style = StampStyle.CLASSIC_MAP_BADGE)) }

                StyleSelectorItem(
                    title = "Frosted Glass Badge",
                    desc = "Translucent dark glass card with cyan accent border",
                    isSelected = config.style == StampStyle.FROSTED_GLASS
                ) { update(config.copy(style = StampStyle.FROSTED_GLASS)) }

                StyleSelectorItem(
                    title = "High Contrast Dark",
                    desc = "Solid deep black card with neon cyan top accent",
                    isSelected = config.style == StampStyle.HIGH_CONTRAST_DARK
                ) { update(config.copy(style = StampStyle.HIGH_CONTRAST_DARK)) }

                StyleSelectorItem(
                    title = "Clean Text Outline",
                    desc = "Minimalist white text with drop-shadow outline directly on photo",
                    isSelected = config.style == StampStyle.CLEAN_OUTLINE
                ) { update(config.copy(style = StampStyle.CLEAN_OUTLINE)) }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // WATERMARK CONTENT TOGGLES
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

            SettingToggleRow(
                title = "App / Project Title",
                subtitle = "Custom header text on the stamp",
                checked = config.showCustomTitle
            ) { update(config.copy(showCustomTitle = it)) }

            if (config.showCustomTitle) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = config.customTitle,
                    onValueChange = { update(config.copy(customTitle = it)) },
                    label = { Text("Watermark Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = Color(0xFF2A3644),
                        focusedLabelColor = CyanPrimary,
                        unfocusedLabelColor = Color.LightGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            // COORDINATE FORMAT
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

            // PRIVACY NOTICE BUTTON
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF161F2B))
                    .border(1.dp, Color(0xFF223042), RoundedCornerShape(14.dp))
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
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = CyanPrimary,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
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
            .background(if (isSelected) Color(0xFF003844) else Color(0xFF141C26))
            .border(
                1.5.dp,
                if (isSelected) CyanPrimary else Color(0xFF233040),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.White else Color.LightGray,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
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
            .background(if (isSelected) Color(0xFF003844) else Color(0xFF141C26))
            .border(
                1.5.dp,
                if (isSelected) CyanPrimary else Color(0xFF233040),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = title,
                color = if (isSelected) CyanPrimary else Color.White,
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
                checkedTrackColor = CyanPrimary,
                uncheckedTrackColor = Color(0xFF233040),
                uncheckedThumbColor = Color.LightGray
            )
        )
    }
}

@Composable
fun LiveStampPreviewCard(config: StampConfig) {
    val sampleDate = remember(config.dateTimePattern) {
        val sdf = try {
            java.text.SimpleDateFormat(config.dateTimePattern, Locale.getDefault())
        } catch (_: Exception) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        }
        sdf.format(Date())
    }

    val sampleCoords = remember(config.coordinateFormat) {
        StampConfig.formatCoordinates(37.774929, -122.419416, config.coordinateFormat)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0A0F17))
            .border(1.dp, Color(0xFF1E2838), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        // Grid pattern lines simulating camera viewfinder
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(0.5.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
        )

        // Stamp Card Box positioned dynamically
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
                        StampStyle.CLEAN_OUTLINE -> Modifier
                            .padding(8.dp)
                    }
                )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (config.style == StampStyle.FROSTED_GLASS) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(42.dp)
                            .background(CyanPrimary, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Column {
                    if (config.showCustomTitle && config.customTitle.isNotBlank()) {
                        Text(
                            text = config.customTitle.uppercase(),
                            color = CyanPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (config.showDateTime) {
                        Text(
                            text = sampleDate,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (config.showCoordinates) {
                        Text(
                            text = sampleCoords,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (config.showAccuracy || config.showAltitude) {
                        val accAlt = buildString {
                            if (config.showAccuracy) append("Accuracy: ±3.8m")
                            if (config.showAltitude) {
                                if (isNotEmpty()) append(" • ")
                                append("Alt: 42.0m")
                            }
                        }
                        Text(
                            text = accAlt,
                            color = Color(0xFFCAD1DB),
                            fontSize = 8.sp
                        )
                    }
                    if (config.showAddress) {
                        Text(
                            text = "Market St, San Francisco, CA, USA",
                            color = Color(0xFFB0BCCC),
                            fontSize = 8.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

