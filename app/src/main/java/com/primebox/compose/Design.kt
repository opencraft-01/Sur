@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.primebox.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.primebox.data.Card

// ---------------------------------------------------------------- shared elements

/** Holder so screens can take part in shared element transitions. */
class SharedCtx(
    val st: androidx.compose.animation.SharedTransitionScope,
    val av: androidx.compose.animation.AnimatedVisibilityScope
)

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPoster(ctx: SharedCtx?, key: String): Modifier =
    if (ctx == null) this else with(ctx.st) {
        this@sharedPoster.sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = ctx.av,
            // keep the morphing poster inside the card shape while it flies
            clipInOverlayDuringTransition = androidx.compose.animation.OverlayClip(
                RoundedCornerShape(12.dp)
            )
        )
    }

// ---------------------------------------------------------------- design tokens

@Immutable
data class CineColors(
    val bgTop: Color,
    val bgBottom: Color,
    val sheet: Color,
    val glass: Color,
    val glassStrong: Color,
    val glassBorder: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val accent2: Color,
    val gold: Color,
    val isDark: Boolean
)

val DarkCine = CineColors(
    bgTop = Color(0xFF1F2029),
    bgBottom = Color(0xFF08080A),
    sheet = Color(0xFF15161C),
    glass = Color(0xB322232B),
    glassStrong = Color(0xE622232B),
    glassBorder = Color(0x1FFFFFFF),
    text = Color(0xFFFFFFFF),
    textMuted = Color(0xFF9E9EA9),
    accent = Color(0xFFE50914),
    accent2 = Color(0xFF0072FF),
    gold = Color(0xFFF7BB0E),
    isDark = true
)

val LightCine = CineColors(
    bgTop = Color(0xFFF5F7FA),
    bgBottom = Color(0xFFE4E7EB),
    sheet = Color(0xFFFAFBFC),
    glass = Color(0xBFFFFFFF),
    glassStrong = Color(0xE6FFFFFF),
    glassBorder = Color(0x1A000000),
    text = Color(0xFF12131A),
    textMuted = Color(0xFF5A5D6C),
    accent = Color(0xFFD90429),
    accent2 = Color(0xFF0066FF),
    gold = Color(0xFFF2A900),
    isDark = false
)

val LocalCine = compositionLocalOf { DarkCine }

object Cine {
    val c: CineColors
        @Composable @ReadOnlyComposable get() = LocalCine.current
}

@Composable
private fun anim(target: Color): Color =
    animateColorAsState(targetValue = target, animationSpec = tween(450), label = "themeColor").value

@Composable
private fun animateCine(t: CineColors): CineColors = CineColors(
    bgTop = anim(t.bgTop),
    bgBottom = anim(t.bgBottom),
    sheet = anim(t.sheet),
    glass = anim(t.glass),
    glassStrong = anim(t.glassStrong),
    glassBorder = anim(t.glassBorder),
    text = anim(t.text),
    textMuted = anim(t.textMuted),
    accent = anim(t.accent),
    accent2 = anim(t.accent2),
    gold = anim(t.gold),
    isDark = t.isDark
)

/** mode: 0 = system, 1 = light, 2 = dark (matches Store.themeMode) */
@Composable
fun CineTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val colors = animateCine(if (dark) DarkCine else LightCine)
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            background = colors.bgBottom,
            surface = colors.sheet,
            onBackground = colors.text,
            onSurface = colors.text
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            background = colors.bgBottom,
            surface = colors.sheet,
            onBackground = colors.text,
            onSurface = colors.text
        )
    }
    CompositionLocalProvider(LocalCine provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Full-screen app background: vertical obsidian gradient + faint perspective floor. */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val c = Cine.c
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.bgTop, c.bgBottom)))
    ) {
        content()
    }
}

// ---------------------------------------------------------------- modifiers

/** Frosted glass surface (translucent + border). */
@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(16.dp), strong: Boolean = false): Modifier {
    val c = Cine.c
    val shadowMod = if (!c.isDark) {
        Modifier.shadow(10.dp, shape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
    } else Modifier
    // Light theme needs a nearly opaque surface, otherwise glass-on-white is invisible
    val fill = if (c.isDark) {
        if (strong) c.glassStrong else c.glass
    } else {
        if (strong) c.sheet.copy(alpha = 0.97f) else c.sheet.copy(alpha = 0.90f)
    }
    return this
        .then(shadowMod)
        .clip(shape)
        .background(fill)
}

@Composable
fun Modifier.shimmer(): Modifier {
    val c = Cine.c
    val t = rememberInfiniteTransition(label = "shimmer")
    val x = t.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX"
    )
    val base = if (c.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f)
    val hi = if (c.isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.13f)
    return this.drawBehind {
        val w = size.width
        drawRect(
            Brush.linearGradient(
                colors = listOf(base, hi, base),
                start = Offset(x.value * w, 0f),
                end = Offset(x.value * w + w, size.height * 0.3f)
            )
        )
    }
}

// ---------------------------------------------------------------- components

@Composable
fun PosterArt(
    card: Card,
    modifier: Modifier = Modifier,
    showTitle: Boolean = false,
    titleSize: TextUnit = 22.sp,
    ctx: SharedCtx? = null
) {
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF2A2C38), Color(0xFF101116))))) {
        if (card.poster != null) {
            val context = LocalContext.current
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(card.poster)
                    .crossfade(160)
                    .build(),
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { Box(Modifier.fillMaxSize().shimmer()) },
                error = {
                    if (showTitle) {
                        Text(
                            card.name.uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = titleSize,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 14.dp)
                        )
                    }
                }
            )
        } else if (showTitle) {
            Text(
                card.name.uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = titleSize,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 4f), 12f)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 14.dp)
            )
        }
    }
}

/** Hero-model overload, so the ported CineGlass deck can draw a Movie directly. */
@Composable
fun PosterArt(
    movie: Movie,
    modifier: Modifier = Modifier,
    showTitle: Boolean = false,
    titleSize: TextUnit = 22.sp
) = PosterArt(
    card = Card(movie.title, movie.videoUrl, movie.posterUrl, movie.genre, movie.quality, null),
    modifier = modifier,
    showTitle = showTitle,
    titleSize = titleSize
)

@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = Cine.c.text,
    /** true when the button sits on artwork: uses a dark scrim in both themes */
    overArt: Boolean = false
) {
    val base = modifier.size(size)
    val styled = if (overArt) {
        base
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.42f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable(onClick = onClick)
    } else {
        base.glass(CircleShape).clickable(onClick = onClick)
    }
    Box(styled, contentAlignment = Alignment.Center) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (overArt) Color.White else tint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

@Composable
fun GlowButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = Cine.c
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .shadow(14.dp, shape, ambientColor = c.accent, spotColor = c.accent)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(c.accent, Color(0xFFFF3D4A))))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun MiniPlay(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    val c = Cine.c
    Box(
        modifier
            .size(size)
            .shadow(10.dp, CircleShape, ambientColor = c.accent, spotColor = c.accent)
            .clip(CircleShape)
            .background(c.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

@Composable
fun GlassChip(
    text: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    leadingTint: Color = Color.Unspecified,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val c = Cine.c
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .then(
                if (selected) {
                    Modifier
                        .clip(shape)
                        .background(c.accent)
                } else {
                    Modifier.glass(shape)
                }
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Icon(
                leading,
                null,
                tint = if (leadingTint == Color.Unspecified) (if (selected) Color.White else c.gold) else leadingTint,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text,
            color = if (selected) Color.White else c.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun QualityBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun CineSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null
) {
    val c = Cine.c
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = c.text, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(
            Modifier.then(if (onSeeAll != null) Modifier.clickable { onSeeAll() } else Modifier).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("See all", color = c.textMuted, fontSize = 12.sp)
            Icon(Icons.Filled.ChevronRight, null, tint = c.textMuted, modifier = Modifier.size(16.dp))
        }
    }
}

/** Poster tile: artwork + quality badge only, reference style. */
@Composable
fun MovieCard(
    card: Card,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 118.dp,
    height: Dp = 177.dp,
    ribbon: String? = null,
    ctx: SharedCtx? = null
) {
    val c = Cine.c
    Box(
        modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        PosterArt(card, Modifier.fillMaxSize().sharedPoster(ctx, "poster-${card.url}"))
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.68f to Color.Transparent, 1f to Color(0x99000000))
            )
        )
        if (!card.quality.isNullOrBlank()) {
            Text(
                card.quality,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        if (ribbon != null) {
            Text(
                ribbon,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(c.accent)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
    }
}

/** Wide 16:9 tile with the name underneath (continue watching). */
@Composable
fun EpisodeCard(
    card: Card,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 196.dp,
    subtitle: String? = null,
    ctx: SharedCtx? = null
) {
    val c = Cine.c
    Column(modifier.width(width).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
            PosterArt(card, Modifier.fillMaxSize().sharedPoster(ctx, "poster-${card.url}"))
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xAA000000))
                )
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            if (!card.quality.isNullOrBlank()) {
                QualityBadge(card.quality, Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            card.name,
            color = c.text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, color = c.textMuted, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
fun MovieCardSkeleton(width: Dp = 118.dp, height: Dp = 177.dp) {
    Box(Modifier.width(width).height(height).clip(RoundedCornerShape(10.dp)).shimmer())
}

fun glowText(color: Color) = TextStyle(shadow = Shadow(color.copy(alpha = 0.85f), Offset.Zero, 28f))
