package com.geostamp.camera.ui.components

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geostamp.camera.data.model.StampConfig
import com.geostamp.camera.data.model.StampPosition
import com.geostamp.camera.data.model.StampStyle
import com.geostamp.camera.location.LocationState
import com.geostamp.camera.ui.camera.CameraUiState
import com.geostamp.camera.ui.theme.CyanPrimary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BrandYellow = Color(0xFFFFCC00)

/**
 * Watermark card width that adapts to the actual phone screen instead of a fixed dp.
 * Portrait uses ~84% of screen width, landscape ~36%, both clamped to readable limits.
 */
@Composable
private fun rememberResponsiveCardWidth(isLandscape: Boolean): Dp {
    val cfg = LocalConfiguration.current
    val widthDp = cfg.screenWidthDp.toFloat()
    return if (isLandscape) {
        (widthDp * 0.36f).coerceIn(250f, 360f).dp
    } else {
        (widthDp * 0.84f).coerceIn(260f, 380f).dp
    }
}

/** Map thumbnail size relative to the smallest screen dimension, so it stays small on every phone. */
@Composable
private fun rememberResponsiveMapSize(isLandscape: Boolean): Dp {
    val cfg = LocalConfiguration.current
    val shortest = minOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat()
    return if (isLandscape) {
        (shortest * 0.12f).coerceIn(38f, 56f).dp
    } else {
        (shortest * 0.13f).coerceIn(44f, 64f).dp
    }
}

@Composable
private fun rememberLiveFormattedDate(pattern: String): String {
    val currentTimeMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(1000L)
            value = System.currentTimeMillis()
        }
    }
    return remember(pattern, currentTimeMillis / 1000L) {
        val sdf = try {
            SimpleDateFormat(pattern, Locale.getDefault())
        } catch (_: Exception) {
            SimpleDateFormat("EEEE, dd/MM/yyyy hh:mm a 'GMT' Z", Locale.getDefault())
        }
        sdf.format(Date(currentTimeMillis))
    }
}

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
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val cardMaxWidth = rememberResponsiveCardWidth(isLandscape)
    val mapSize = rememberResponsiveMapSize(isLandscape)

    val dateText = rememberLiveFormattedDate(config.dateTimePattern)

    val loc = when (val s = uiState.locationState) {
        is LocationState.Ready -> s.snapshot
        else -> null
    }

    val coordString = remember(loc, config.coordinateFormat) {
        if (loc != null) {
            StampConfig.formatCoordinates(loc.latitude, loc.longitude, config.coordinateFormat)
        } else {
            "Acquiring GPS coordinates..."
        }
    }

    val plusCodeString = uiState.plusCode
    val headerString = when {
        config.showAddress && !uiState.detailedAddress?.shortHeader.isNullOrBlank() -> uiState.detailedAddress!!.shortHeader
        config.showCustomTitle && config.customTitle.isNotBlank() -> config.customTitle
        loc != null -> "GPS Location Locked 📍"
        else -> "Acquiring Satellite Fix..."
    }
    val streetString = uiState.detailedAddress?.fullStreetAddress
    val weather = uiState.weatherSnapshot
    val sensor = uiState.sensorSnapshot

    Box(
        modifier = Modifier
            .widthIn(max = cardMaxWidth)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xD905080E))
            .border(0.8.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            .padding(if (isLandscape) 5.dp else 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Text Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 1.dp else 1.5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = headerString,
                        color = Color.White,
                        fontSize = if (isLandscape) 10.sp else 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xCC0E1A29))
                            .border(0.6.dp, CyanPrimary, RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "📷 GPS Map Camera",
                            color = Color.White,
                            fontSize = if (isLandscape) 6.5.sp else 7.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (config.showAddress && !streetString.isNullOrBlank()) {
                    Text(
                        text = streetString,
                        color = Color(0xFFD6E0EA),
                        fontSize = if (isLandscape) 7.5.sp else 8.5.sp,
                        lineHeight = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (config.showCoordinates) {
                    Text(
                        text = coordString,
                        color = Color.White,
                        fontSize = if (isLandscape) 8.sp else 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val accPlusParts = buildList {
                    if (config.showPlusCode && !plusCodeString.isNullOrBlank()) add("Plus Code : $plusCodeString")
                    if (config.showAccuracy && loc != null) add(String.format(Locale.US, "±%.1fm", loc.accuracyMeters))
                    if (config.showAltitude && loc?.altitudeMeters != null) add(String.format(Locale.US, "Alt: %.1fm", loc.altitudeMeters))
                }
                if (accPlusParts.isNotEmpty()) {
                    Text(
                        text = accPlusParts.joinToString(" • "),
                        color = Color(0xFFB0BEC5),
                        fontSize = if (isLandscape) 7.sp else 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (config.showDateTime || config.showWeather) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (config.showDateTime) {
                            Text(
                                text = dateText,
                                color = Color.White,
                                fontSize = if (isLandscape) 7.sp else 8.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                        if (config.showWeather) {
                            Text(
                                text = "${weather.weatherEmoji} ${String.format(Locale.US, "%.2f° C", weather.temperatureCelsius)}",
                                color = Color(0xFFFFD54F),
                                fontSize = if (isLandscape) 7.sp else 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                val tagParts = buildList {
                    if (config.showCustomTitle && config.customTitle.isNotBlank() && config.customTitle != "GPS Map Camera") add(config.customTitle)
                    if (config.companyName.isNotBlank()) add(config.companyName)
                    if (config.notes.isNotBlank()) add(config.notes)
                }
                if (tagParts.isNotEmpty()) {
                    Text(
                        text = tagParts.joinToString(" • "),
                        color = Color(0xFFCFD8DC),
                        fontSize = if (isLandscape) 7.sp else 7.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                        fontSize = if (isLandscape) 7.sp else 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (config.showCompass || config.showSensors) {
                        Text(
                            text = "🧭 ${sensor.compassDegrees}° ${sensor.compassDirection}",
                            color = Color(0xFF81D4FA),
                            fontSize = if (isLandscape) 7.sp else 8.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (config.showSensors) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🍃 ${String.format(Locale.US, "%.2f km/h", weather.windSpeedKmh)}",
                            color = Color(0xFFB0BEC5),
                            fontSize = if (isLandscape) 6.5.sp else 7.5.sp
                        )
                        Text(
                            text = "💧 ${weather.humidityPercent}%",
                            color = Color(0xFFB0BEC5),
                            fontSize = if (isLandscape) 6.5.sp else 7.5.sp
                        )
                        Text(
                            text = "🧭 ${String.format(Locale.US, "%.0f hpa", sensor.pressureHpa)}",
                            color = Color(0xFFB0BEC5),
                            fontSize = if (isLandscape) 6.5.sp else 7.5.sp
                        )
                        Text(
                            text = "🧲 ${String.format(Locale.US, "%.2f µT", sensor.magneticFieldMicroTesla)}",
                            color = Color(0xFFB0BEC5),
                            fontSize = if (isLandscape) 6.5.sp else 7.5.sp
                        )
                    }
                }
            }

            // Right: Real Satellite Map Snapshot (if enabled), small & responsive
            if (config.showMapSnapshot) {
                Spacer(modifier = Modifier.width(if (isLandscape) 5.dp else 7.dp))
                Box(
                    modifier = Modifier
                        .size(mapSize)
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
                                fontSize = if (isLandscape) 8.sp else 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                            )
                        }
                    }
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
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val cardMaxWidth = rememberResponsiveCardWidth(isLandscape)
    val mapSize = rememberResponsiveMapSize(isLandscape)

    val loc = when (val s = uiState.locationState) {
        is LocationState.Ready -> s.snapshot
        else -> null
    }
    val coordString = remember(loc, config.coordinateFormat) {
        if (loc != null) StampConfig.formatCoordinates(loc.latitude, loc.longitude, config.coordinateFormat)
        else "Acquiring GPS..."
    }
    val dateText = rememberLiveFormattedDate(config.dateTimePattern)
    val address = uiState.detailedAddress?.fullStreetAddress

    Box(
        modifier = Modifier
            .widthIn(max = cardMaxWidth)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xE60A0E16))
            .border(1.dp, Color(0x6000E5FF), RoundedCornerShape(12.dp))
            .padding(if (isLandscape) 8.dp else 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (config.showCoordinates) {
                    Text(
                        text = coordString,
                        color = CyanPrimary,
                        fontSize = if (isLandscape) 10.sp else 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (config.showAddress && !address.isNullOrBlank()) {
                    Text(
                        text = address,
                        color = Color.White,
                        fontSize = if (isLandscape) 9.sp else 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val metaParts = buildList {
                    if (config.showDateTime) add(dateText)
                    if (config.showWeather) add("${uiState.weatherSnapshot.weatherEmoji} ${uiState.weatherSnapshot.temperatureCelsius.toInt()}°C")
                    if (config.notes.isNotBlank()) add(config.notes)
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" • "),
                        color = Color(0xFFB0BEC5),
                        fontSize = if (isLandscape) 8.sp else 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right: small responsive map thumbnail
            if (config.showMapSnapshot && uiState.mapSnapshot != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Image(
                    bitmap = uiState.mapSnapshot.asImageBitmap(),
                    contentDescription = "Map Mini",
                    modifier = Modifier
                        .size(mapSize)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, CyanPrimary, RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
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
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val cardMaxWidth = rememberResponsiveCardWidth(isLandscape)

    val loc = when (val s = uiState.locationState) {
        is LocationState.Ready -> s.snapshot
        else -> null
    }
    val coordStr = remember(loc, config.coordinateFormat) {
        if (loc != null) StampConfig.formatCoordinates(loc.latitude, loc.longitude, config.coordinateFormat)
        else "Acquiring..."
    }
    val dateText = rememberLiveFormattedDate(config.dateTimePattern)
    val project = if (config.customTitle.isNotBlank()) config.customTitle else "GPS Map Camera"
    val inspector = if (config.personName.isNotBlank() && config.personName != "-") config.personName else "Inspector"

    Box(
        modifier = Modifier
            .widthIn(max = cardMaxWidth)
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
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("PROJ: $project", color = BrandYellow, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("INSP: $inspector", color = Color.White, fontSize = 8.5.sp, maxLines = 1)
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x55FFCC00)))

            // Row 2
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("GPS: ${if (config.showCoordinates) coordStr else "-"}", color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    text = "ACC: ${if (config.showAccuracy && loc != null) "±%.1fm".format(Locale.US, loc.accuracyMeters) else "-"}",
                    color = Color(0xFF00E676),
                    fontSize = 8.5.sp
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x55FFCC00)))

            // Row 3
            val locLine = buildString {
                if (config.showAddress && !uiState.detailedAddress?.fullStreetAddress.isNullOrBlank()) {
                    append(uiState.detailedAddress!!.fullStreetAddress)
                }
                if (config.notes.isNotBlank()) {
                    if (isNotEmpty()) append(" | ")
                    append(config.notes)
                }
            }.ifBlank { coordStr }
            Text(
                text = "LOC: $locLine",
                color = Color(0xFFD6E0EA),
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
            )
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x55FFCC00)))

            // Row 4
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.5.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("TIME: ${if (config.showDateTime) dateText else "-"}", color = Color.LightGray, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (config.showWeather || config.showCompass || config.showSensors) {
                    Text(
                        text = "CLIMATE: ${uiState.weatherSnapshot.weatherEmoji} ${uiState.weatherSnapshot.temperatureCelsius.toInt()}°C • 🧭 ${uiState.sensorSnapshot.compassDegrees}°",
                        color = Color(0xFF81D4FA),
                        fontSize = 8.sp
                    )
                }
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
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val cardMaxWidth = rememberResponsiveCardWidth(isLandscape)

    val loc = when (val s = uiState.locationState) {
        is LocationState.Ready -> s.snapshot
        else -> null
    }
    val coordStr = remember(loc, config.coordinateFormat) {
        if (loc != null) StampConfig.formatCoordinates(loc.latitude, loc.longitude, config.coordinateFormat)
        else "Acquiring GPS..."
    }
    val dateText = rememberLiveFormattedDate(config.dateTimePattern)

    Box(
        modifier = Modifier
            .widthIn(max = cardMaxWidth)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xD90D1520))
            .border(1.2.dp, Color(0x9900E5FF), RoundedCornerShape(12.dp))
            .padding(if (isLandscape) 8.dp else 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(if (isLandscape) 42.dp else 48.dp)
                    .background(CyanPrimary, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                if (config.showCustomTitle && config.customTitle.isNotBlank()) {
                    Text(
                        text = config.customTitle.uppercase(),
                        color = CyanPrimary,
                        fontSize = if (isLandscape) 10.sp else 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                if (config.showCoordinates) {
                    Text(
                        text = coordStr,
                        color = Color.White,
                        fontSize = if (isLandscape) 9.sp else 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                val metaParts = buildList {
                    if (config.showDateTime) add(dateText)
                    if (config.showWeather) add("${uiState.weatherSnapshot.weatherEmoji} ${uiState.weatherSnapshot.temperatureCelsius.toInt()}°C")
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" • "),
                        color = Color(0xFFB0BEC5),
                        fontSize = if (isLandscape) 8.sp else 8.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (config.showAddress && !uiState.detailedAddress?.fullStreetAddress.isNullOrBlank()) {
                    Text(
                        text = uiState.detailedAddress!!.fullStreetAddress,
                        color = Color(0xFFD6E0EA),
                        fontSize = if (isLandscape) 8.sp else 8.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (config.notes.isNotBlank()) {
                    Text(
                        text = config.notes,
                        color = Color(0xFFB0BEC5),
                        fontSize = 7.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val cardMaxWidth = rememberResponsiveCardWidth(isLandscape)

    val loc = when (val s = uiState.locationState) {
        is LocationState.Ready -> s.snapshot
        else -> null
    }
    val coordStr = remember(loc, config.coordinateFormat) {
        if (loc != null) StampConfig.formatCoordinates(loc.latitude, loc.longitude, config.coordinateFormat)
        else "Acquiring GPS..."
    }
    val dateText = rememberLiveFormattedDate(config.dateTimePattern)

    Box(
        modifier = Modifier
            .widthIn(max = cardMaxWidth)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFA040608))
            .border(1.dp, Color(0xFF263238), RoundedCornerShape(8.dp))
            .padding(if (isLandscape) 8.dp else 10.dp)
    ) {
        Column {
            if (config.showCustomTitle && config.customTitle.isNotBlank()) {
                Text(
                    text = config.customTitle.uppercase(),
                    color = BrandYellow,
                    fontSize = if (isLandscape) 10.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            if (config.showCoordinates) {
                Text(
                    text = coordStr,
                    color = Color.White,
                    fontSize = if (isLandscape) 9.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            val metaParts = buildList {
                if (config.showDateTime) add(dateText)
                if (config.showAltitude && loc?.altitudeMeters != null) {
                    add(String.format(Locale.US, "Alt: %.1fm", loc.altitudeMeters))
                }
                if (config.showAccuracy && loc != null) {
                    add(String.format(Locale.US, "±%.1fm", loc.accuracyMeters))
                }
            }
            if (metaParts.isNotEmpty()) {
                Text(
                    text = metaParts.joinToString("  •  "),
                    color = Color(0xFFCFD8DC),
                    fontSize = if (isLandscape) 8.sp else 8.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (config.showAddress && !uiState.detailedAddress?.fullStreetAddress.isNullOrBlank()) {
                Text(
                    text = uiState.detailedAddress!!.fullStreetAddress,
                    color = Color(0xFFB0BEC5),
                    fontSize = if (isLandscape) 8.sp else 8.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (config.notes.isNotBlank()) {
                Text(
                    text = config.notes,
                    color = Color(0xFFB0BEC5),
                    fontSize = 7.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// 6. CLEAN OUTLINE
@Composable
fun CleanOutlineWatermarkCard(
    uiState: CameraUiState,
    config: StampConfig
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val cardMaxWidth = rememberResponsiveCardWidth(isLandscape)

    val loc = when (val s = uiState.locationState) {
        is LocationState.Ready -> s.snapshot
        else -> null
    }
    val coordStr = remember(loc, config.coordinateFormat) {
        if (loc != null) StampConfig.formatCoordinates(loc.latitude, loc.longitude, config.coordinateFormat)
        else "Acquiring GPS..."
    }
    val dateText = rememberLiveFormattedDate(config.dateTimePattern)

    Column(
        modifier = Modifier
            .widthIn(max = cardMaxWidth)
            .padding(6.dp)
    ) {
        if (config.showCustomTitle && config.customTitle.isNotBlank()) {
            Text(
                text = config.customTitle.uppercase(),
                color = BrandYellow,
                fontSize = if (isLandscape) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        if (config.showCoordinates) {
            Text(
                text = coordStr,
                color = Color.White,
                fontSize = if (isLandscape) 9.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        val metaParts = buildList {
            if (config.showDateTime) add(dateText)
            if (config.showCompass || config.showSensors) {
                add("🧭 ${uiState.sensorSnapshot.compassDegrees}° ${uiState.sensorSnapshot.compassDirection}")
            }
        }
        if (metaParts.isNotEmpty()) {
            Text(
                text = metaParts.joinToString(" • "),
                color = Color.White,
                fontSize = if (isLandscape) 8.sp else 8.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (config.showAddress && !uiState.detailedAddress?.fullStreetAddress.isNullOrBlank()) {
            Text(
                text = uiState.detailedAddress!!.fullStreetAddress,
                color = Color.White,
                fontSize = if (isLandscape) 8.sp else 8.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (config.notes.isNotBlank()) {
            Text(
                text = config.notes,
                color = Color(0xFFE0E0E0),
                fontSize = 7.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
