@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.primebox.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * "For You" hero deck, ported one-to-one from the CineGlass reference:
 * a stack of cards where the front one is full size, the rest peek to the
 * right, drag slides the deck, and the front card slides out to the left.
 */
@Composable
fun HeroStage(
    movies: List<Movie>,
    ctx: SharedCtx?,
    topInset: Dp = 0.dp,
    cardW: Dp = 300.dp,
    cardH: Dp = 420.dp,
    onOpen: (Movie) -> Unit,
    onPlay: (Movie) -> Unit
) {
    if (movies.isEmpty()) return
    val c = Cine.c
    val hero = remember(movies) { movies.take(6) }
    val n = hero.size
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val progress = remember { Animatable(0f) }
    val center by remember { derivedStateOf { progress.value.roundToInt() } }
    var touching by remember { mutableStateOf(false) }
    val stridePx by rememberUpdatedState(with(density) { cardW.toPx() })
    val lastIndex = (n - 1).toFloat()

    // auto advance — walks forward and stops at the last card, never wraps
    LaunchedEffect(touching, n) {
        if (!touching) {
            while (true) {
                delay(4200)
                val next = progress.value.roundToInt() + 1
                if (next > n - 1) break
                progress.animateTo(next.toFloat(), tween(650, easing = FastOutSlowInEasing))
            }
        }
    }

    val dragState = rememberDraggableState { delta ->
        scope.launch {
            progress.snapTo((progress.value - delta / stridePx).coerceIn(0f, lastIndex))
        }
    }

    Box(Modifier.fillMaxWidth()) {
        // ambient purple / blue glow behind the header
        if (c.isDark) {
            Box(
                Modifier.fillMaxWidth().height(330.dp).background(
                    Brush.verticalGradient(
                        0f to Color(0xFF3A1457).copy(alpha = 0.55f),
                        0.55f to Color(0xFF1B1840).copy(alpha = 0.25f),
                        1f to Color.Transparent
                    )
                )
            )
            Box(
                Modifier.fillMaxWidth().height(330.dp).background(
                    Brush.radialGradient(
                        listOf(Color(0xFF0B2A7A).copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = 900f
                    )
                )
            )
        }

        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(topInset + 12.dp))
            Text(
                "For You",
                color = c.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 13.dp).height(34.dp)
            )
            Spacer(Modifier.height(14.dp))

            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(cardH)
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        onDragStarted = { touching = true },
                        onDragStopped = { v ->
                            touching = false
                            val fling = (-v / stridePx * 0.35f).coerceIn(-1.2f, 1.2f)
                            val target = (progress.value + fling).roundToInt().coerceIn(0, n - 1)
                            scope.launch {
                                progress.animateTo(target.toFloat(), spring(dampingRatio = 0.82f, stiffness = 260f))
                            }
                        }
                    )
            ) {
                val startPad = maxWidth * 0.041f
                Box(Modifier.padding(start = startPad)) {
                    for (i in (center - 2)..(center + 3)) {
                        if (i !in 0 until n) continue
                        key(i) {
                            val movie = hero[i]
                            DeckCard(
                                index = i,
                                movie = movie,
                                isCenter = i == center,
                                progress = progress,
                                cardW = cardW,
                                cardH = cardH,
                                ctx = ctx,
                                onCardClick = {
                                    if (i == center) onOpen(movie)
                                    else scope.launch {
                                        progress.animateTo(i.toFloat(), tween(450, easing = FastOutSlowInEasing))
                                    }
                                },
                                onPlay = { onPlay(movie) },
                                onAdd = { onOpen(movie) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun badgeFor(m: Movie): String = when (m.id % 4) {
    0 -> "Newly Added"
    1 -> "IMDb ${"%.1f".format(m.rating * 2)}"
    2 -> "Top 10 Today"
    else -> "New Episodes Fri"
}

@Composable
private fun DeckCard(
    index: Int,
    movie: Movie,
    isCenter: Boolean,
    progress: Animatable<Float, AnimationVector1D>,
    cardW: Dp,
    cardH: Dp,
    ctx: SharedCtx?,
    onCardClick: () -> Unit,
    onPlay: () -> Unit,
    onAdd: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .zIndex(-index.toFloat())
            .size(cardW, cardH)
            .graphicsLayer {
                val off = index - progress.value
                val peek = 18.5.dp.toPx()
                if (off >= 0f) {
                    val k = off.coerceAtMost(3f)
                    translationX = k * peek
                    val s = 1f - 0.055f * k
                    scaleX = s
                    scaleY = s
                    alpha = if (off > 3f) (1f - (off - 3f) * 5f).coerceIn(0f, 1f) else 1f
                } else {
                    translationX = if (off >= -1f) off * size.width
                    else -size.width + (off + 1f).coerceAtLeast(-1.5f) * 13.dp.toPx()
                    scaleX = 1f
                    scaleY = 1f
                    alpha = if (off < -2.5f) 0f else 1f
                }
            }
            .clip(shape)
            .background(Color(0xFF0B0B0F))
            .drawWithContent {
                drawContent()
                val off = index - progress.value
                val dim = when {
                    off >= 0f -> {
                        val k = off.coerceAtMost(3f)
                        (0.12f * k + 0.1f * k * k).coerceAtMost(0.8f)
                    }
                    off < -1f -> 0.25f
                    else -> 0f
                }
                if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
            }
            .clickable(onClick = onCardClick)
    ) {
        PosterArt(
            movie,
            Modifier.fillMaxSize().then(if (isCenter) Modifier.sharedPoster(ctx, "poster-hero-${movie.id}") else Modifier),
            showTitle = true,
            titleSize = 34.sp
        )

        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.52f to Color.Transparent,
                    0.78f to Color.Black.copy(alpha = 0.72f),
                    1f to Color.Black.copy(alpha = 0.96f)
                )
            )
        )

        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(15.dp)
                .height(29.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color.Black.copy(alpha = 0.38f))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(15.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.AutoAwesome, null, tint = Color(0xFF7CC4FF), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(badgeFor(movie), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
        ) {
            Text(
                listOf(movie.release, movie.genre, movie.age, movie.quality)
                    .filter { it.isNotBlank() }
                    .joinToString("  •  "),
                color = Color(0xFFB9B9C1),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassAction(Modifier.weight(1f), onPlay) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Watch Now", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                Spacer(Modifier.width(10.dp))
                GlassAction(Modifier.width(50.dp), onAdd) {
                    Icon(Icons.Filled.Add, "Add", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun GlassAction(modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.42f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        content()
    }
}
