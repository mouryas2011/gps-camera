package com.geostamp.camera.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geostamp.camera.data.model.AspectRatioOption
import com.geostamp.camera.ui.theme.AccentGreen
import kotlin.math.abs

private val BrandYellow = Color(0xFFFFCC00)

// 1. Aspect Ratio Framing Mask (Black bars when 1:1 or 16:9)
@Composable
fun AspectFramingMask(
    aspectRatio: AspectRatioOption,
    modifier: Modifier = Modifier
) {
    if (aspectRatio == AspectRatioOption.RATIO_4_3) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight

        if (aspectRatio == AspectRatioOption.RATIO_1_1) {
            // Square mask: top and bottom letterbox
            val barH = (h - w) / 2f
            if (barH > 0.dp) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxWidth().height(barH).background(Color(0xD9000000)))
                    Spacer(modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.fillMaxWidth().height(barH).background(Color(0xD9000000)))
                }
            }
        }
    }
}

// 2. Rule of Thirds (3x3) Grid
@Composable
fun RuleOfThirdsGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val gridColor = Color(0x40FFFFFF)
        val strokeWidth = 1.dp.toPx()

        // Vertical lines
        drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth)
        drawLine(gridColor, Offset((2f * w) / 3f, 0f), Offset((2f * w) / 3f, h), strokeWidth)

        // Horizontal lines
        drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth)
        drawLine(gridColor, Offset(0f, (2f * h) / 3f), Offset(w, (2f * h) / 3f), strokeWidth)
    }
}

// 3. Electronic Horizon & Tilt Level Gauge (Surveyor Tool)
@Composable
fun HorizonLevelGauge(
    pitchDegrees: Int,
    rollDegrees: Int,
    modifier: Modifier = Modifier
) {
    val isLevel = abs(rollDegrees) <= 1
    val indicatorColor = if (isLevel) AccentGreen else BrandYellow

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Rotating Horizon line
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(44.dp)
                .rotate(rollDegrees.toFloat()),
            contentAlignment = Alignment.Center
        ) {
            // Horizontal line
            Canvas(modifier = Modifier.fillMaxSize()) {
                val midY = size.height / 2f
                val w = size.width

                // Left wing
                drawLine(
                    color = indicatorColor,
                    start = Offset(0f, midY),
                    end = Offset(w * 0.38f, midY),
                    strokeWidth = 2.dp.toPx()
                )

                // Right wing
                drawLine(
                    color = indicatorColor,
                    start = Offset(w * 0.62f, midY),
                    end = Offset(w, midY),
                    strokeWidth = 2.dp.toPx()
                )

                // Center crosshair
                drawCircle(
                    color = indicatorColor,
                    radius = 3.dp.toPx(),
                    center = Offset(w / 2f, midY)
                )
            }
        }

        // Tilt Badge
        Box(
            modifier = Modifier
                .offset(y = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xCC000000))
                .border(1.dp, indicatorColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(
                text = if (isLevel) "LEVEL 0°" else "${if (rollDegrees > 0) "+" else ""}${rollDegrees}°",
                color = indicatorColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

// 4. Tap to Focus Ring with animation
@Composable
fun TapFocusRing(
    focusPoint: Offset?,
    modifier: Modifier = Modifier
) {
    if (focusPoint == null) return
    val density = LocalDensity.current

    val scale = remember { Animatable(1.4f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(focusPoint) {
        scale.snapTo(1.4f)
        alpha.snapTo(1f)
        scale.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(250, easing = FastOutSlowInEasing)
        )
        kotlinx.coroutines.delay(1200)
        alpha.animateTo(0f, animationSpec = tween(300))
    }

    if (alpha.value > 0.05f) {
        Box(
            modifier = modifier
                .offset(
                    x = with(density) { focusPoint.x.toDp() - 36.dp },
                    y = with(density) { focusPoint.y.toDp() - 36.dp }
                )
                .size(72.dp)
                .border(1.8.dp, BrandYellow.copy(alpha = alpha.value), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Little focus cross
            Text(
                text = "+",
                color = BrandYellow.copy(alpha = alpha.value),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// 5. Timer Countdown Overlay
@Composable
fun TimerCountdownOverlay(
    secondsRemaining: Int?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = secondsRemaining != null && secondsRemaining > 0,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x80000000)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC000000))
                    .border(3.5.dp, BrandYellow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${secondsRemaining ?: 0}",
                    color = BrandYellow,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
