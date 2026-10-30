package com.geostamp.camera.ui.components

import android.content.res.Configuration
import android.graphics.Color as AndroidColor
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
import com.geostamp.camera.data.model.StampLayout
import com.geostamp.camera.data.model.StampPosition
import com.geostamp.camera.data.model.StampStyle
import com.geostamp.camera.location.LocationState
import com.geostamp.camera.ui.camera.CameraUiState
import com.geostamp.camera.ui.theme.CyanPrimary
import kotlinx.coroutines.delay
import java.util.Locale

private val BrandYellow = Color(0xFFFFCC00)

/**
 * Watermark card width that adapts to the actual phone screen instead of a fixed dp.
 * Portrait uses ~92% of screen width, landscape ~40%, scaled by the user's size choice
 * and clamped so the card always stays on screen.
 */
@Composable
private fun rememberResponsiveCardWidth(isLandscape: Boolean, scale: Float = 1f): Dp {
    val cfg = LocalConfiguration.current
    val widthDp = cfg.screenWidthDp.toFloat()
    val base = if (isLandscape) {
        (widthDp * 0.40f).coerceIn(270f, 400f)
    } else {
        (widthDp * 0.92f).coerceIn(280f, 410f)
    }
    return (base * scale).coerceAtMost(widthDp - 12f).dp
}

/** Map thumbnail size relative to the smallest screen dimension, so it stays small on every phone. */
@Composable
private fun rememberResponsiveMapSize(isLandscape: Boolean, scale: Float = 1f): Dp {
    val cfg = LocalConfiguration.current
    val shortest = minOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat()
    val base = if (isLandscape) {
        (shortest * 0.16f).coerceIn(48f, 72f)
    } else {
        (shortest * 0.20f).coerceIn(56f, 84f)
    }
    return (base * scale).dp
}

/** Primary watermark text colour selected by the user (white by default). */
@Composable
private fun rememberStampTextColor(config: StampConfig): Color {
    return remember(config.fontColor) {
        try {
            Color(AndroidColor.parseColor(config.fontColor.hex))
        } catch (_: Exception) {
            Color.White
        }
    }
}

/** Semi-transparent charcoal panel colour with the user's transparency choice. */
@Composable
private fun rememberStampBackground(config: StampConfig): Color {
    return remember(config.backgroundAlpha) {
        Color(0xFF0A0E14).copy(alpha = config.backgroundAlpha.coerceIn(0.35f, 0.95f))
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
        StampConfig.formatDateTime(currentTimeMillis, pattern)
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

// 1. CLASSIC MAP BADGE (premium reference layout: map left, info panel right)
@Composable
fun ClassicMapWatermarkCard(
    uiState: CameraUiState,
    config: StampConfig
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val sizeScale = config.stampSize.scaleFactor
    val fontScale = config.fontSize.scaleFactor
    val cardMaxWidth = rememberResponsiveCardWidth(isLandscape, sizeScale)
    val mapSize = rememberResponsiveMapSize(isLandscape, sizeScale)
    val textColor = rememberStampTextColor(config)
    val panelColor = rememberStampBackground(config)
    val isVertical = config.layout == StampLayout.VERTICAL

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

    val noteLine = remember(config.notes, config.showCapturedByNote, config.customTitle) {
        StampConfig.noteLine(config)
    }

    val extraParts = buildList {
        if (config.showPlusCode && !plusCodeString.isNullOrBlank()) add("Plus Code: $plusCodeString")
        if (config.showAccuracy && loc != null) add(String.format(Locale.US, "±%.1fm", loc.accuracyMeters))
        if (config.showAltitude && loc?.altitudeMeters != null) add(String.format(Locale.US, "Alt: %.1fm", loc.altitudeMeters))
        if (config.showWeather) add("${weather.weatherEmoji} ${String.format(Locale.US, "%.1f°C", weather.temperatureCelsius)}")
        if (config.showCompass || config.showSensors) add("🧭 ${sensor.compassDegrees}° ${sensor.compassDirection}")
    }

    val tagParts = buildList {
        if (config.showCustomTitle && config.customTitle.isNotBlank() &&
            config.customTitle != headerString && config.customTitle != "GeoStamp Camera"
        ) add(config.customTitle)
        if (config.personName.isNotBlank() && config.personName != "-") add(config.personName)
        if (config.companyName.isNotBlank()) add(config.companyName)
    }

    Box(
        modifier = Modifier
            .widthIn(max = cardMaxWidth)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(panelColor)
            .border(0.8.dp, Color(0x26FFFFFF), RoundedCornerShape(12.dp))
            .padding(6.dp)
    ) {
        val infoContent: @Composable (Modifier) -> Unit = { infoModifier ->
            Column(
                modifier = infoModifier,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = headerString,
                    color = textColor,
                    fontSize = (11f * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (config.showAddress && !streetString.isNullOrBlank()) {
                    Text(
                        text = streetString,
                        color = textColor.copy(alpha = 0.88f),
                        fontSize = (8.5f * fontScale).sp,
                        lineHeight = (10f * fontScale).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (config.showCoordinates) {
                    Text(
                        text = coordString,
                        color = textColor,
                        fontSize = (9.5f * fontScale).sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (config.showDateTime) {
                    Text(
                        text = dateText,
                        color = textColor,
                        fontSize = (8.5f * fontScale).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (extraParts.isNotEmpty()) {
                    Text(
                        text = extraParts.joinToString("  •  "),
                        color = Color(0xFFB0BEC5),
                        fontSize = (7.5f * fontScale).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (tagParts.isNotEmpty()) {
                    Text(
                        text = tagParts.joinToString("  •  "),
                        color = Color(0xFFCFD8DC),
                        fontSize = (7.5f * fontScale).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (noteLine != null) {
                    Text(
                        text = noteLine,
                        color = Color(0xFF9AA7B4),
                        fontSize = (7.5f * fontScale).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        val mapContent: @Composable (Modifier) -> Unit = { mapModifier ->
            Box(
                modifier = mapModifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF161F28))
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
                            fontSize = (8f * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                        )
                    }
                }
            }
        }

        if (isVertical) {
            Column {
                if (config.showMapSnapshot) {
                    mapContent(Modifier.fillMaxWidth().height(mapSize))
                    Spacer(modifier = Modifier.height(6.dp))
                }
                infoContent(Modifier.fillMaxWidth())
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (config.showMapSnapshot) {
                    mapContent(Modifier.size(mapSize))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                infoContent(Modifier.weight(1f))
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
    val sizeScale = config.stampSize.scaleFactor
    val fontScale = config.fontSize.scaleFactor
    val cardMaxWidth = rememberResponsiveCardWidth(isLandscape, sizeScale)
    val mapSize = rememberResponsiveMapSize(isLandscape, sizeScale)
    val textColor = rememberStampTextColor(config)
    val panelColor = rememberStampBackground(config)

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
    val noteLine = remember(config.notes, config.showCapturedByNote, config.customTitle) {
        StampConfig.noteLine(config)
    }

    Box(
        modifier = Modifier
            .widthIn(max = cardMaxWidth)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(panelColor)
            .border(1.dp, Color(0x6000E5FF), RoundedCornerShape(12.dp))
            .padding(if (isLandscape) 7.dp else 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (config.showCoordinates) {
                    Text(
                        text = coordString,
                        color = textColor,
                        fontSize = ((if (isLandscape) 10f else 11f) * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (config.showAddress && !address.isNullOrBlank()) {
                    Text(
                        text = address,
                        color = textColor.copy(alpha = 0.88f),
                        fontSize = ((if (isLandscape) 9f else 10f) * fontScale).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                        fontSize = ((if (isLandscape) 8f else 9f) * fontScale).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (noteLine != null) {
                    Text(
                        text = noteLine,
                        color = Color(0xFF9AA7B4),
                        fontSize = (7.5f * fontScale).sp,
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
    val cardMaxWidth = rememberResponsiveCardWidth(isLandscape, config.stampSize.scaleFactor)

    val loc = when (val s = uiState.locationState) {
        is LocationState.Ready -> s.snapshot
        else -> null
    }
    val coordStr = remember(loc, config.coordinateFormat) {
        if (loc != null) StampConfig.formatCoordinates(loc.latitude, loc.longitude, config.coordinateFormat)
        else "Acquiring..."
    }
    val dateText = rememberLiveFormattedDate(config.dateTimePattern)
    val project = if (config.customTitle.isNotBlank()) config.customTitle else "GeoStamp Camera"
    val inspector = if (config.personName.isNotBlank() && config.personName != "-") config.personName else "Inspector"

    Box(
        modifier = Modifier
            .widthIn(max = cardMaxWidth)
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(rememberStampBackground(config))
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
    val cardMaxWidth = rememberResponsiveCardWidth(isLandscape, config.stampSize.scaleFactor)

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
            .background(rememberStampBackground(config))
            .border(1.2.dp, Color(0x9900E5FF), RoundedCornerShape(12.dp))
            .padding(if (isLandscape) 7.dp else 8.dp)
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
                val note = StampConfig.noteLine(config)
                if (note != null) {
                    Text(
                        text = note,
                        color = Color(0xFF9AA7B4),
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
    val cardMaxWidth = rememberResponsiveCardWidth(isLandscape, config.stampSize.scaleFactor)

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
            .background(rememberStampBackground(config))
            .border(1.dp, Color(0xFF263238), RoundedCornerShape(8.dp))
            .padding(if (isLandscape) 7.dp else 8.dp)
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
            val note = StampConfig.noteLine(config)
            if (note != null) {
                Text(
                    text = note,
                    color = Color(0xFF9AA7B4),
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
    val cardMaxWidth = rememberResponsiveCardWidth(isLandscape, config.stampSize.scaleFactor)

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
            .padding(5.dp)
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
        val note = StampConfig.noteLine(config)
        if (note != null) {
            Text(
                text = note,
                color = Color(0xFFE0E0E0),
                fontSize = 7.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
