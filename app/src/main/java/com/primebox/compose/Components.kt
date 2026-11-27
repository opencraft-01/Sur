package com.primebox.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.primebox.data.Card
import kotlin.math.PI
import kotlin.math.sin

/** Animated skeleton block (Compose shimmer). */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, corner: Int = 16) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier
            .clip(RoundedCornerShape(corner.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(progress * 400f, 0f),
                    end = Offset(progress * 400f + 260f, 260f)
                )
            )
    )
}

/** CloudStream-style poster card: 114x180, radius 10dp, title inside over a scrim. */
@Composable
fun PosterCard(
    card: Card,
    width: Int = 114,
    height: Int = 180,
    progress: Float? = null,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.width(width.dp).padding(end = 10.dp)) {
        Box(
            modifier = Modifier
                .width(width.dp)
                .height(height.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { onClick() }
        ) {
            if (card.poster.isNullOrBlank()) {
                ShimmerBox(Modifier.fillMaxSize(), corner = 10)
            } else {
                AsyncImage(
                    model = card.poster,
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.62f to Color.Transparent,
                            1f to Color(0xE60A0A0D)
                        )
                    )
            )
            if (!card.quality.isNullOrBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(5.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)
                ) {
                    Text(
                        card.quality,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            Text(
                text = card.name,
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 5.dp, vertical = 5.dp)
            )
            if (progress != null) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                )
            }
        }
    }
}

/** Pill chip. */
@Composable
fun PillChip(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

/** Small label badge. */
@Composable
fun Badge(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHigh
) {
    Surface(color = background, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/** Section header, CloudStream spacing: 12dp vertical, 5dp text padding. */
@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 15.dp, end = 15.dp, top = 12.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 5.dp)
        )
        if (action != null && onAction != null) {
            Text(
                action,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onAction() }.padding(start = 8.dp)
            )
        }
    }
}

/** Empty / error state with an action button. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onAction != null) {
            androidx.compose.material3.Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * Smooth Material-3 style wavy progress line, same behaviour as the old view:
 * the wave travels while a transfer runs and the fill animates to the new value.
 */
@Composable
fun WavyProgress(progress: Float, modifier: Modifier = Modifier) {
    val animated by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(480),
        label = "wavyProgress"
    )
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val accent = MaterialTheme.colorScheme.primary

    androidx.compose.foundation.Canvas(modifier = modifier.height(22.dp).fillMaxWidth()) {
        val stroke = 7.dp.toPx()
        val cy = size.height / 2f
        val startX = stroke / 2f + 1f
        val endX = size.width - stroke / 2f - 1f
        if (endX <= startX) return@Canvas

        drawLine(
            color = track,
            start = Offset(startX, cy),
            end = Offset(endX, cy),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        val fillEnd = startX + (endX - startX) * animated
        if (fillEnd <= startX + 1f) return@Canvas

        val amplitude = size.height * 0.17f
        val wavelength = 44.dp.toPx()
        val path = Path()
        path.moveTo(startX, cy)
        var x = startX
        while (x < fillEnd) {
            val y = cy + amplitude * sin((x / wavelength) * 2 * PI.toFloat() + phase)
            path.lineTo(x, y)
            x += 3f
        }
        val endY = cy + amplitude * sin((fillEnd / wavelength) * 2 * PI.toFloat() + phase)
        path.lineTo(fillEnd, endY)

        drawPath(
            path = path,
            color = accent,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawCircle(color = accent, radius = stroke * 0.72f, center = Offset(fillEnd, endY))
    }
}

/** Formatting helpers shared by download / update screens. */
fun bytesLabel(value: Long): String = when {
    value <= 0 -> "0 MB"
    value < 1024 * 1024 -> String.format(java.util.Locale.US, "%.0f KB", value / 1024.0)
    else -> String.format(java.util.Locale.US, "%.1f MB", value / (1024.0 * 1024.0))
}
