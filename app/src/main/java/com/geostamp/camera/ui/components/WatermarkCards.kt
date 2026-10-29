package com.geostamp.camera.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geostamp.camera.data.model.StampConfig
import com.geostamp.camera.data.model.StampPosition
import com.geostamp.camera.data.model.StampStyle
import com.geostamp.camera.location.LocationState
import com.geostamp.camera.ui.camera.CameraUiState
import com.geostamp.camera.ui.theme.CyanPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BrandYellow = Color(0xFFFFCC00)

@Composable
fun LiveWatermarkOverlay(
    uiState: CameraUiState,
    config: StampConfig,
    onCardClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val alignment = when (config.position) {
        StampPosition.TOP_LEFT -> Alignment.TopStart
        StampPosition.TOP_RIGHT -> Alignment.TopEnd
        StampPosition.BOTTOM_LEFT -> Alignment.BottomStart
        StampPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        contentAlignment = alignment
    ) {
        Box(modifier = Modifier.clickable(onClick = onCardClick)) {
            AnimatedContent(
                targetState = config.style,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "WatermarkStyleTransition"
            ) { targetStyle ->
                when (targetStyle) {
                    StampStyle.CLASSIC_MAP_BADGE -> ClassicMapWatermarkCard(uiState, config)
                    StampStyle.MODERN_MINIMAL -> ModernMinimalWatermarkCard(uiState, config)
                    StampStyle.SURVEYOR_GRID -> SurveyorGridWatermarkCard(uiState, config)
                    StampStyle.FROSTED_GLASS -> FrostedGlassWatermarkCard(uiState, config)
                    StampStyle.HIGH_CONTRAST_DARK -> HighContrastDarkWatermarkCard(uiState, config)
                    StampStyle.CLEAN_OUTLINE -> CleanOutlineWatermarkCard(uiState, config)
                }
            }
        }
    }
}

// 1. CLASSIC MAP BADGE (Matches reference standard)
@Composable
fun ClassicMapWatermarkCard(
    uiState: CameraUiState,
    config: StampConfig
) {
    val dateText = remember(config.dateTimePattern) {
        val sdf = try {
            SimpleDateFormat("EEEE, dd/MM/yyyy hh:mm a 'GMT' XXX", Locale.getDefault())
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
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xD905080E))
            .border(0.8.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Real Satellite Map Snapshot
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF161F28))
                    .border(0.6.dp, Color(0x40FFFFFF), RoundedCornerShape(4.dp))
            ) {
                if (uiState.mapSnapshot != null) {
                    Image(
                        bitmap = uiState.mapSnapshot.asImageBitmap(),
                        contentDescription = "Live Satellite Map",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                        Text(
                            "Google",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp, bottom = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Text Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = headerString,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xCC0E1A29))
                            .border(0.6.dp, CyanPrimary, RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = "📷 GPS Map Camera",
                            color = Color.White,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = streetString,
                    color = Color(0xFFD6E0EA),
                    fontSize = 8.5.sp,
                    lineHeight = 10.sp,
                    maxLines = 1
                )

                Text(
                    text = coordString,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Plus Code : $plusCodeString",
                    color = Color(0xFFB0BEC5),
                    fontSize = 8.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateText,
                        color = Color.White,
                        fontSize = 8.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "${weather.weatherEmoji} ${String.format(Locale.US, "%.2f° C", weather.temperatureCelsius)}",
                        color = Color(0xFFFFD54F),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (config.customTitle.isNotBlank() && config.customTitle != "GPS Map Camera") {
                    Text(
                        text = config.customTitle,
                        color = Color(0xFFCFD8DC),
                        fontSize = 7.5.sp,
                        maxLines = 1
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Person Name : ${if (config.personName.isNotBlank()) config.personName else "-"}",
                        color = Color(0xFFB0BEC5),
                        fontSize = 8.sp
                    )
                    Text(
                        text = "🧭 ${sensor.compassDegrees}° ${sensor.compassDirection}",
                        color = Color(0xFF81D4FA),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🍃 ${String.format(Locale.US, "%.2f km/h", weather.windSpeedKmh)}",
                        color = Color(0xFFB0BEC5),
                        fontSize = 7.5.sp
                    )
                    Text(
                        text = "💧 ${weather.humidityPercent}%",
                        color = Color(0xFFB0BEC5),
                        fontSize = 7.5.sp
                    )
                    Text(
                        text = "🧭 ${String.format(Locale.US, "%.0f hpa", sensor.pressureHpa)}",
                        color = Color(0xFFB0BEC5),
                        fontSize = 7.5.sp
                    )
                    Text(
                        text = "🧲 ${String.format(Locale.US, "%.2f µT", sensor.magneticFieldMicroTesla)}",
                        color = Color(0xFFB0BEC5),
                        fontSize = 7.5.sp
                    )
                }
            }
        }
    }
}

// 2. MODERN MINIMAL
@Composable
fun ModernMinimalWatermarkCard(
    uiState: CameraUiState,
    config: StampConfig
) {
    val loc = when (val s = uiState.locationState) {
        is LocationState.Ready -> s.snapshot
        else -> null
    }
    val coordString = remember(loc, config.coordinateFormat) {
        if (loc != null) StampConfig.formatCoordinates(loc.latitude, loc.longitude, config.coordinateFormat)
        else "37.7749° N, 122.4194° W"
    }
    val dateText = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
    val address = uiState.detailedAddress?.fullStreetAddress ?: "San Francisco, CA"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xE60A0E16))
            .border(1.dp, Color(0x6000E5FF), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (uiState.mapSnapshot != null) {
                Image(
                    bitmap = uiState.mapSnapshot.asImageBitmap(),
                    contentDescription = "Map Mini",
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, CyanPrimary, RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = coordString,
                    color = CyanPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = address,
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                Text(
                    text = "$dateText • ${uiState.weatherSnapshot.weatherEmoji} ${uiState.weatherSnapshot.temperatureCelsius.toInt()}°C",
                    color = Color(0xFFB0BEC5),
                    fontSize = 9.sp
                )
            }
        }
    }
}

// 3. SURVEYOR GRID
@Composable
fun SurveyorGridWatermarkCard(
    uiState: CameraUiState,
    config: StampConfig
) {
    val loc = when (val s = uiState.locationState) {
        is LocationState.Ready -> s.snapshot
        else -> null
    }
    val coordStr = remember(loc, config.coordinateFormat) {
        if (loc != null) StampConfig.formatCoordinates(loc.latitude, loc.longitude, config.coordinateFormat)
        else "22.374124° N, 82.735036° E"
    }
    val dateText = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()) }
    val project = if (config.customTitle.isNotBlank()) config.customTitle else "GPS Map Camera"
    val inspector = if (config.personName.isNotBlank()) config.personName else "Inspector"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xF2060A10))
            .border(1.2.dp, BrandYellow, RoundedCornerShape(4.dp))
    ) {
        Column {
            // Row 1
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("PROJ: $project", color = BrandYellow, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text("INSP: $inspector", color = Color.White, fontSize = 9.sp)
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x55FFCC00)))

            // Row 2
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("GPS: $coordStr", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                Text("ACC: ±${loc?.accuracyMeters?.let { "%.1fm".format(Locale.US, it) } ?: "3.2m"}", color = Color(0xFF00E676), fontSize = 9.sp)
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x55FFCC00)))

            // Row 3
            Text(
                text = "LOC: ${uiState.detailedAddress?.fullStreetAddress ?: "223, Rampur, Korba"}",
                color = Color(0xFFD6E0EA),
                fontSize = 8.5.sp,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x55FFCC00)))

            // Row 4
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("TIME: $dateText", color = Color.LightGray, fontSize = 8.5.sp)
                Text("CLIMATE: ${uiState.weatherSnapshot.weatherEmoji} ${uiState.weatherSnapshot.temperatureCelsius.toInt()}°C • 🧭 ${uiState.sensorSnapshot.compassDegrees}°", color = Color(0xFF81D4FA), fontSize = 8.5.sp)
            }
        }
    }
}

// 4. FROSTED GLASS
@Composable
fun FrostedGlassWatermarkCard(
    uiState: CameraUiState,
    config: StampConfig
) {
    val loc = when (val s = uiState.locationState) {
        is LocationState.Ready -> s.snapshot
        else -> null
    }
    val coordStr = remember(loc, config.coordinateFormat) {
        if (loc != null) StampConfig.formatCoordinates(loc.latitude, loc.longitude, config.coordinateFormat)
        else "37.774929° N, 122.419416° W"
    }
    val dateText = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xD90D1520))
            .border(1.2.dp, Color(0x9900E5FF), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(48.dp)
                    .background(CyanPrimary, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = (config.customTitle.ifBlank { "GPS MAP CAMERA" }).uppercase(),
                    color = CyanPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = coordStr,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$dateText • ${uiState.weatherSnapshot.weatherEmoji} ${uiState.weatherSnapshot.temperatureCelsius.toInt()}°C",
                    color = Color(0xFFB0BEC5),
                    fontSize = 8.5.sp
                )
                Text(
                    text = uiState.detailedAddress?.fullStreetAddress ?: "Civic Center, San Francisco, CA",
                    color = Color(0xFFD6E0EA),
                    fontSize = 8.5.sp,
                    maxLines = 1
                )
            }
        }
    }
}

// 5. HIGH CONTRAST DARK
@Composable
fun HighContrastDarkWatermarkCard(
    uiState: CameraUiState,
    config: StampConfig
) {
    val loc = when (val s = uiState.locationState) {
        is LocationState.Ready -> s.snapshot
        else -> null
    }
    val coordStr = remember(loc, config.coordinateFormat) {
        if (loc != null) StampConfig.formatCoordinates(loc.latitude, loc.longitude, config.coordinateFormat)
        else "37.7749° N, 122.4194° W"
    }
    val dateText = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFA040608))
            .border(1.dp, Color(0xFF263238), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(
                text = (config.customTitle.ifBlank { "GPS MAP CAMERA" }).uppercase(),
                color = BrandYellow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = coordStr,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$dateText  •  Alt: ${loc?.altitudeMeters?.let { "%.1fm".format(Locale.US, it) } ?: "45m"}",
                color = Color(0xFFCFD8DC),
                fontSize = 8.5.sp
            )
            Text(
                text = uiState.detailedAddress?.fullStreetAddress ?: "Market Street, San Francisco",
                color = Color(0xFFB0BEC5),
                fontSize = 8.5.sp,
                maxLines = 1
            )
        }
    }
}

// 6. CLEAN OUTLINE
@Composable
fun CleanOutlineWatermarkCard(
    uiState: CameraUiState,
    config: StampConfig
) {
    val loc = when (val s = uiState.locationState) {
        is LocationState.Ready -> s.snapshot
        else -> null
    }
    val coordStr = remember(loc, config.coordinateFormat) {
        if (loc != null) StampConfig.formatCoordinates(loc.latitude, loc.longitude, config.coordinateFormat)
        else "37.7749° N, 122.4194° W"
    }
    val dateText = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()) }

    Column(modifier = Modifier.padding(8.dp)) {
        Text(
            text = (config.customTitle.ifBlank { "GPS MAP CAMERA" }).uppercase(),
            color = BrandYellow,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = coordStr,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "$dateText • 🧭 ${uiState.sensorSnapshot.compassDegrees}° ${uiState.sensorSnapshot.compassDirection}",
            color = Color.White,
            fontSize = 8.5.sp
        )
        Text(
            text = uiState.detailedAddress?.fullStreetAddress ?: "Location Address",
            color = Color.White,
            fontSize = 8.5.sp,
            maxLines = 1
        )
    }
}
