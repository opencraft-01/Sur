@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)

package com.primebox.compose

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.primebox.data.Card as MediaCard

// ------------------------------------------------------------ shared elements

/** Holder so screens can take part in shared element transitions. */
class SharedCtx(
    val st: SharedTransitionScope,
    val av: AnimatedVisibilityScope
)

/** Marks a poster as a shared element. No-op without a scope or a key. */
@Composable
fun Modifier.sharedPoster(ctx: SharedCtx?, key: String?): Modifier =
    if (ctx == null || key == null) this else with(ctx.st) {
        this@sharedPoster.sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = ctx.av
        )
    }

// ------------------------------------------------------------------- loading

/** Skeleton shimmer built from theme colours. */
@Composable
fun Modifier.shimmer(): Modifier {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f).compositeOver(base)
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x = transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX"
    )
    return this.drawBehind {
        val w = size.width
        drawRect(
            Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(x.value * w, 0f),
                end = Offset(x.value * w + w, size.height * 0.3f)
            )
        )
    }
}

/** Material 3 expressive wavy progress. `progress == null` means indeterminate. */
@Composable
fun WavyProgress(progress: Float?, modifier: Modifier = Modifier) {
    if (progress == null) {
        LinearWavyProgressIndicator(modifier = modifier.fillMaxWidth())
    } else {
        LinearWavyProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = modifier.fillMaxWidth())
    }
}

// -------------------------------------------------------------------- posters

@Composable
private fun BoxScope.TitleFallback(title: String, show: Boolean) {
    if (!show) return
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.align(Alignment.Center).padding(10.dp)
    )
}

/** Poster image with shimmer while loading and the title as fallback when it fails. */
@Composable
fun PosterArt(
    poster: String?,
    title: String,
    modifier: Modifier = Modifier,
    showTitleFallback: Boolean = true
) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        if (!poster.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(poster).crossfade(true).build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { Box(Modifier.fillMaxSize().shimmer()) },
                error = { TitleFallback(title, showTitleFallback) }
            )
        } else {
            TitleFallback(title, showTitleFallback)
        }
    }
}

/** Small tonal tag such as "HD" / "CAM". */
@Composable
fun QualityBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** Portrait poster (2:3) with the title underneath. `width == null` fills the parent (grids). */
@Composable
fun PosterCard(
    card: MediaCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = 120.dp,
    ctx: SharedCtx? = null,
    sharedKey: String? = null,
    showTitle: Boolean = true
) {
    Column(if (width != null) modifier.width(width) else modifier) {
        Card(
            onClick = onClick,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
        ) {
            Box(Modifier.fillMaxSize()) {
                PosterArt(card.poster, card.name, Modifier.fillMaxSize().sharedPoster(ctx, sharedKey))
                if (!card.quality.isNullOrBlank()) {
                    QualityBadge(card.quality, Modifier.align(Alignment.TopStart).padding(6.dp))
                }
            }
        }
        if (showTitle) {
            Text(
                card.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 6.dp)
            )
        }
    }
}

/** Wide 16:9 card with the title and an optional subtitle (continue watching). */
@Composable
fun LandscapeCard(
    card: MediaCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 196.dp,
    subtitle: String? = null,
    ctx: SharedCtx? = null,
    sharedKey: String? = null
) {
    Column(modifier.width(width)) {
        Card(
            onClick = onClick,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        ) {
            Box(Modifier.fillMaxSize()) {
                PosterArt(card.poster, card.name, Modifier.fillMaxSize().sharedPoster(ctx, sharedKey))
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).size(28.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.padding(4.dp))
                }
                if (!card.quality.isNullOrBlank()) {
                    QualityBadge(card.quality, Modifier.align(Alignment.TopEnd).padding(6.dp))
                }
            }
        }
        Text(
            card.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, top = 6.dp)
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}

@Composable
fun PosterSkeleton(width: Dp = 120.dp) {
    Box(Modifier.width(width).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.medium).shimmer())
}

// ------------------------------------------------------------------- layout

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) {
                Text("See all")
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Empty / error state with an optional action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.padding(2.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.padding(4.dp))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Formatting helper shared by download / update screens. */
fun bytesLabel(value: Long): String = when {
    value <= 0 -> "0 MB"
    value < 1024 * 1024 -> String.format(java.util.Locale.US, "%.0f KB", value / 1024.0)
    else -> String.format(java.util.Locale.US, "%.1f MB", value / (1024.0 * 1024.0))
}
