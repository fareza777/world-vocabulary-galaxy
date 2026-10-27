package com.verbilume.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.text.TextStyle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.verbilume.app.data.WordSummary
import com.verbilume.app.ui.theme.FrauncesFamily
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.theme.ManropeFamily
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun VerbiMark(
    modifier: Modifier = Modifier,
    color: Color = LocalVerbiColors.current.ink,
    accent: Color = LocalVerbiColors.current.accent,
    orbit: Color? = null,
    showOrbit: Boolean = true,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = minOf(w, h) / 2f
        if (showOrbit) {
            val orbitColor = orbit ?: color.copy(alpha = 0.26f)
            drawOval(
                color = orbitColor,
                topLeft = Offset(w * 0.05f, h * 0.28f),
                size = Size(w * 0.9f, h * 0.44f),
                style = Stroke(width = radius * 0.035f),
            )
            rotate(32f, center) {
                drawOval(
                    color = orbitColor.copy(alpha = 0.6f),
                    topLeft = Offset(w * 0.17f, h * 0.1f),
                    size = Size(w * 0.66f, h * 0.8f),
                    style = Stroke(width = radius * 0.03f),
                )
            }
            drawCircle(
                color = accent,
                radius = radius * 0.075f,
                center = Offset(w * 0.86f, h * 0.34f),
            )
            drawCircle(
                color = accent.copy(alpha = 0.65f),
                radius = radius * 0.045f,
                center = Offset(w * 0.13f, h * 0.68f),
            )
        }
        val star = Path()
        val cx = center.x
        val cy = center.y + h * 0.015f
        val outer = radius * 0.54f
        val inner = radius * 0.14f
        for (i in 0 until 8) {
            val r = if (i % 2 == 0) outer else inner
            val angle = Math.PI * i / 4 - Math.PI / 2
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close()
        drawPath(star, color = color)
        drawCircle(
            color = accent,
            radius = radius * 0.055f,
            center = Offset(cx + outer * 0.53f, cy - outer * 0.54f),
        )
    }
}

@Composable
fun PaperBackground(
    modifier: Modifier = Modifier,
    decorated: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val starSeed = remember { Random(91) }
    val dots = remember {
        List(if (colors.isDark) 60 else 26) {
            Triple(starSeed.nextFloat(), starSeed.nextFloat(), 0.6f + starSeed.nextFloat() * 1.8f)
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(colors.paper, colors.paper, colors.sunken.copy(alpha = if (colors.isDark) 0.9f else 0.55f))
                )
            )
            .drawWithCache {
                onDrawBehind {
                    if (!decorated) return@onDrawBehind
                    if (!colors.isDark) {
                        drawCircle(
                            color = colors.brass.copy(alpha = 0.06f),
                            radius = size.width * 0.55f,
                            center = Offset(size.width * 0.95f, -size.height * 0.03f),
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                        drawCircle(
                            color = colors.brass.copy(alpha = 0.045f),
                            radius = size.width * 0.72f,
                            center = Offset(size.width * 0.95f, -size.height * 0.03f),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    } else {
                        dots.forEach { (x, y, r) ->
                            drawCircle(
                                color = colors.ink.copy(alpha = 0.09f + r * 0.03f),
                                radius = r.dp.toPx() * 0.7f,
                                center = Offset(x * size.width, y * size.height),
                            )
                        }
                    }
                }
            }
    ) {
        content()
    }
}

@Composable
fun WordImage(
    asset: String,
    modifier: Modifier = Modifier,
    corner: Dp = 22.dp,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val colors = LocalVerbiColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/$asset")
            .crossfade(220)
            .build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        placeholder = ColorPainter(colors.sunken),
        error = ColorPainter(colors.sunken),
        modifier = modifier.clip(RoundedCornerShape(corner)),
    )
}

@Composable
fun LevelBadge(
    level: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val colors = LocalVerbiColors.current
    val tint = when (level) {
        "A1", "A2" -> colors.teal
        "B1", "B2" -> colors.brass
        "C1", "C2" -> colors.accent
        else -> colors.inkSoft
    }
    Box(
        modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (filled) tint else tint.copy(alpha = 0.13f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            level,
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 0.6.sp,
            color = if (filled) Color.White else tint,
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val colors = LocalVerbiColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.976f else 1f, label = "buttonScale")
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(54.dp)
            .scale(scale),
        shape = RoundedCornerShape(18.dp),
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = Color(0xFFFFF7EA),
            disabledContainerColor = colors.accent.copy(alpha = 0.35f),
            disabledContentColor = Color(0xFFFFF7EA).copy(alpha = 0.6f),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(9.dp))
        }
        Text(text, fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.1.sp)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    val colors = LocalVerbiColors.current
    Surface(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = colors.sunken.copy(alpha = if (colors.isDark) 0.55f else 0.7f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) { icon(); Spacer(Modifier.width(8.dp)) }
            Text(text, fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink)
        }
    }
}

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = LocalVerbiColors.current.raised,
    corner: Dp = 26.dp,
    content: @Composable () -> Unit,
) {
    val colors = LocalVerbiColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(color)
            .border(1.dp, colors.line.copy(alpha = if (colors.isDark) 1f else 0.7f), RoundedCornerShape(corner))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        content()
    }
}

@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalVerbiColors.current
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(
                    eyebrow.uppercase(),
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    letterSpacing = 2.1.sp,
                    color = colors.brass,
                )
                Spacer(Modifier.height(5.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, color = colors.ink)
        }
        if (action != null && onAction != null) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(action, fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = colors.accent)
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = colors.accent)
            }
        }
    }
}

@Composable
fun CircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalVerbiColors.current.ink,
    background: Color = LocalVerbiColors.current.raised,
    icon: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.size(42.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = background,
        tonalElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) { icon() }
    }
}

@Composable
fun SpeakerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    size: Dp = 42.dp,
    dark: Boolean = false,
) {
    val colors = LocalVerbiColors.current
    val pulse by rememberInfiniteTransition(label = "speaker").animateFloat(
        initialValue = 1f,
        targetValue = if (playing) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "speakerPulse",
    )
    Surface(
        modifier = modifier
            .size(size)
            .scale(if (playing) pulse else 1f)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (dark) Color.White.copy(alpha = 0.16f) else colors.sunken,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.VolumeUp,
                contentDescription = "Play pronunciation",
                tint = if (dark) Color(0xFFFFF7EA) else colors.ink,
                modifier = Modifier.size(size * 0.46f),
            )
        }
    }
}

@Composable
fun BookmarkIconButton(
    bookmarked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
) {
    val colors = LocalVerbiColors.current
    val scale by animateFloatAsState(if (bookmarked) 1.06f else 1f, label = "bookmark")
    Surface(
        modifier = modifier.size(42.dp).scale(scale).clickable(onClick = onClick),
        shape = CircleShape,
        color = if (dark) Color.White.copy(alpha = 0.16f) else colors.sunken,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark word",
                tint = if (dark) Color(0xFFFFF7EA) else if (bookmarked) colors.accent else colors.inkSoft,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 7.dp,
    trackColor: Color = LocalVerbiColors.current.line,
    color: Color = LocalVerbiColors.current.brass,
    content: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(650), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2f
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - strokePx, size.height - strokePx),
                style = Stroke(strokePx, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - strokePx, size.height - strokePx),
                style = Stroke(strokePx, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

@Composable
fun StatPill(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val colors = LocalVerbiColors.current
    Surface(
        modifier = modifier,
        color = colors.raised.copy(alpha = 0.82f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(8.dp))
            Column {
                Text(value, fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.ink)
                Text(label, fontFamily = ManropeFamily, fontSize = 12.5.sp, color = colors.inkFaint, letterSpacing = 0.3.sp)
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = LocalVerbiColors.current
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VerbiMark(Modifier.size(58.dp), color = colors.inkFaint, accent = colors.accent.copy(alpha = 0.7f))
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = colors.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.inkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

@Composable
fun WordTile(
    word: WordSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalVerbiColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    ) {
        Box {
            WordImage(word.image, Modifier.fillMaxWidth().height(126.dp), corner = 22.dp)
            Box(
                Modifier
                    .padding(9.dp)
                    .align(Alignment.TopEnd)
            ) { LevelBadge(word.levelLabel) }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            word.word,
            style = MaterialTheme.typography.titleLarge,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            word.firstSense,
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkFaint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalVerbiColors.current
    CircleIconButton(
        onClick = onClick,
        modifier = modifier,
        background = colors.raised.copy(alpha = 0.8f),
    ) {
        Icon(Icons.Rounded.ArrowBack, "Back", Modifier.size(20.dp), tint = colors.ink)
    }
}

@Composable
fun PageTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalVerbiColors.current
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onClick = onBack)
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            letterSpacing = 2.4.sp,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
    }
}

@Composable
fun SearchFieldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalVerbiColors.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = colors.raised.copy(alpha = 0.75f),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = colors.inkFaint)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.inkFaint)
        }
    }
}

@Composable
fun Confetti(
    trigger: Boolean,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(Color(0xFFD8A94D), Color(0xFFC55E3F), Color(0xFF2E6F68), Color(0xFF8CBFE3)),
) {
    if (!trigger) return
    val progress = remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(2200, easing = LinearEasing))
    }
    val random = remember { Random(73) }
    val particles = remember {
        List(70) {
            ConfettiParticle(
                x = random.nextFloat(),
                drift = (random.nextFloat() - 0.5f) * 0.35f,
                delay = random.nextFloat() * 0.3f,
                size = 4f + random.nextFloat() * 8f,
                rotation = random.nextFloat() * 360f,
                color = colors[random.nextInt(colors.size)],
                shape = random.nextInt(3),
            )
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        particles.forEach { p ->
            val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val y = -20.dp.toPx() + local * (size.height + 40.dp.toPx())
            val x = (p.x + p.drift * local) * size.width
            val alpha = (1f - (local - 0.75f).coerceAtLeast(0f) / 0.25f).coerceIn(0f, 1f)
            rotate(p.rotation + local * 540f, Offset(x, y)) {
                if (p.shape == 0) {
                    drawCircle(p.color.copy(alpha = alpha), radius = p.size.dp.toPx() / 2, center = Offset(x, y))
                } else {
                    drawRect(
                        p.color.copy(alpha = alpha),
                        topLeft = Offset(x - p.size.dp.toPx() / 2, y - p.size.dp.toPx() / 4),
                        size = Size(p.size.dp.toPx(), p.size.dp.toPx() / 2),
                    )
                }
            }
        }
    }
}

private data class ConfettiParticle(
    val x: Float,
    val drift: Float,
    val delay: Float,
    val size: Float,
    val rotation: Float,
    val color: Color,
    val shape: Int,
)

@Composable
fun LoadingField(modifier: Modifier = Modifier) {
    val colors = LocalVerbiColors.current
    val transition = rememberInfiniteTransition(label = "loading")
    val alpha by transition.animateFloat(
        0.35f, 0.9f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "loadingAlpha",
    )
    Column(
        modifier.fillMaxWidth().padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VerbiMark(Modifier.size(48.dp).graphicsLayer { this.alpha = alpha }, color = colors.ink, accent = colors.accent)
        Spacer(Modifier.height(16.dp))
        Text("Gathering your words…", style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
    }
}

@Composable
fun GradientRule(modifier: Modifier = Modifier) {
    val colors = LocalVerbiColors.current
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, colors.brass.copy(alpha = 0.5f), Color.Transparent)),
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

@Composable
fun rememberHaptic(): (Boolean) -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) {
        { enabled: Boolean ->
            if (enabled) runCatching { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
            Unit
        }
    }
}

@Composable
fun ProgressDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val colors = LocalVerbiColors.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val active = index == current
            val width by animateFloatAsState(if (active) 24f else 7f, label = "dot")
            Box(
                Modifier
                    .height(7.dp)
                    .width(width.dp)
                    .clip(CircleShape)
                    .background(if (active) colors.accent else colors.ink.copy(alpha = 0.16f))
            )
        }
    }
}
