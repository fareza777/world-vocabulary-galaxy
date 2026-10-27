package com.verbilume.app.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Canvas as RenderCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.verbilume.app.data.AppGraph
import com.verbilume.app.data.GalaxyNode
import com.verbilume.app.data.StarProgress
import com.verbilume.app.data.TopicInfo
import com.verbilume.app.data.WordSummary
import com.verbilume.app.ui.LevelBadge
import com.verbilume.app.ui.LoadingField
import com.verbilume.app.ui.VerbiMark
import com.verbilume.app.ui.WordImage
import com.verbilume.app.ui.graphViewModel
import com.verbilume.app.ui.theme.FrauncesFamily
import com.verbilume.app.ui.theme.ManropeFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private val Sky = Color(0xFF07121C)
private val SkyDeep = Color(0xFF040B12)
private val Cream = Color(0xFFF2E8D7)
private val SkyBrass = Color(0xFFE0B86A)
private val SkyTerracotta = Color(0xFFD97E5E)

private class GalaxyStar(
    val id: String,
    val word: String,
    val topic: Int,
    val sizeBucket: Int,
    val sizeClass: Int,
    val nx: Float,
    val ny: Float,
    val learned: Boolean,
    val mastered: Boolean,
    val due: Boolean,
)

private class StarGroup(
    val topic: Int,
    val sizeClass: Int,
    val learned: Boolean,
    val indices: IntArray,
    val buffer: FloatArray,
    val paint: Paint,
)

private class BackdropGroup(
    val radius: Float,
    val alpha: Float,
    val fractions: FloatArray,
    val buffer: FloatArray,
    val paint: Paint,
)

private class StarSnapshot(
    val image: ImageBitmap,
    val zoom: Float,
    val pan: Offset,
)

private fun DrawScope.drawStarLayer(
    stars: List<GalaxyStar>,
    groups: List<StarGroup>,
    masteredIndices: List<Int>,
    dueIndices: List<Int>,
    colorsByTopic: List<Color>,
    originX: Float,
    originY: Float,
    span: Float,
    zoomFactor: Float,
    selectedTopic: Int?,
) {
    for (group in groups) {
        val buffer = group.buffer
        var write = 0
        for (index in group.indices) {
            val star = stars[index]
            buffer[write++] = originX + star.nx * span
            buffer[write++] = originY + star.ny * span
        }
        val color = colorsByTopic.getOrElse(group.topic) { SkyBrass }
        val alpha = when {
            selectedTopic != null && group.topic != selectedTopic -> 0.09f
            group.learned -> 0.85f
            else -> 0.61f
        }
        group.paint.color = color.copy(alpha = alpha)
        group.paint.strokeWidth = (1.52f + group.sizeClass * 0.68f) * zoomFactor * 2f
        drawContext.canvas.drawRawPoints(PointMode.Points, buffer, group.paint)
    }

    for (index in masteredIndices) {
        val star = stars[index]
        val p = Offset(originX + star.nx * span, originY + star.ny * span)
        if (p.x < -30f || p.y < -30f || p.x > size.width + 30f || p.y > size.height + 30f) continue
        val radius = (1.35f + star.sizeBucket * 0.34f) * zoomFactor
        drawCircle(SkyBrass.copy(alpha = 0.16f), radius = radius * 3.2f, center = p)
        drawCircle(SkyBrass.copy(alpha = 0.85f), radius = radius * 1.55f, center = p)
    }

    for (index in dueIndices) {
        val star = stars[index]
        if (selectedTopic != null && star.topic != selectedTopic) continue
        val p = Offset(originX + star.nx * span, originY + star.ny * span)
        if (p.x < -30f || p.y < -30f || p.x > size.width + 30f || p.y > size.height + 30f) continue
        val radius = (1.35f + star.sizeBucket * 0.34f) * zoomFactor
        drawCircle(
            color = SkyTerracotta.copy(alpha = 0.8f),
            radius = radius + 3.4f,
            center = p,
            style = Stroke(width = 1.2f),
        )
    }
}

private fun rasterizeStarLayer(
    width: Int,
    height: Int,
    density: Density,
    stars: List<GalaxyStar>,
    groups: List<StarGroup>,
    masteredIndices: List<Int>,
    dueIndices: List<Int>,
    colorsByTopic: List<Color>,
    zoom: Float,
    pan: Offset,
    selectedTopic: Int?,
): ImageBitmap {
    val span = min(width, height) * 0.46f * zoom
    val originX = width / 2f + pan.x
    val originY = height / 2f + pan.y
    val zoomFactor = 0.85f + (zoom - 1f) * 0.05f
    val bitmap = ImageBitmap(width, height)
    CanvasDrawScope().draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = RenderCanvas(bitmap),
        size = Size(width.toFloat(), height.toFloat()),
    ) {
        drawStarLayer(
            stars = stars,
            groups = groups,
            masteredIndices = masteredIndices,
            dueIndices = dueIndices,
            colorsByTopic = colorsByTopic,
            originX = originX,
            originY = originY,
            span = span,
            zoomFactor = zoomFactor,
            selectedTopic = selectedTopic,
        )
    }
    return bitmap
}

data class GalaxyUiState(
    val loading: Boolean = true,
    val nodes: List<GalaxyNode> = emptyList(),
    val topics: List<TopicInfo> = emptyList(),
    val progress: Map<String, StarProgress> = emptyMap(),
)

class GalaxyViewModel(private val graph: AppGraph) : ViewModel() {
    private val _state = MutableStateFlow(GalaxyUiState())
    val state: StateFlow<GalaxyUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                GalaxyUiState(
                    loading = false,
                    nodes = graph.content.galaxyNodes(),
                    topics = graph.content.meta.topics,
                    progress = runCatching { graph.learning.starProgress() }.getOrDefault(emptyMap()),
                )
            }
            _state.value = loaded
        }
    }
}

@Composable
fun GalaxyScreen(
    graph: AppGraph,
    onWord: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val viewModel = graphViewModel { GalaxyViewModel(graph) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 96)
    val view = LocalView.current

    var selectedTopic by remember { mutableStateOf<Int?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var selectedWord by remember { mutableStateOf<WordSummary?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (previousStatus != null) controller.isAppearanceLightStatusBars = previousStatus
            if (previousNav != null) controller.isAppearanceLightNavigationBars = previousNav
        }
    }

    val colorsByTopic = remember(state.topics) { state.topics.map { Color(it.color) } }

    val stars = remember(state.nodes, state.progress) {
        val now = System.currentTimeMillis()
        state.nodes.map { node ->
            val progress = state.progress[node.id]
            GalaxyStar(
                id = node.id,
                word = node.word,
                topic = node.topic,
                sizeBucket = node.cefr,
                sizeClass = node.cefr / 2,
                nx = node.x,
                ny = node.z * 0.66f + node.y * 0.9f,
                learned = progress != null && progress.state != 0,
                mastered = progress != null && progress.state == 2 && progress.stability >= 21.0,
                due = progress != null && progress.due <= now,
            )
        }
    }
    val starGroups = remember(stars) {
        val buckets = LinkedHashMap<Int, MutableList<Int>>()
        stars.forEachIndexed { index, star ->
            if (!star.mastered) {
                val key = (star.topic shl 8) or (star.sizeClass shl 1) or if (star.learned) 1 else 0
                buckets.getOrPut(key) { ArrayList() }.add(index)
            }
        }
        buckets.map { (key, indices) ->
            StarGroup(
                topic = key shr 8,
                sizeClass = (key shr 1) and 0x7F,
                learned = (key and 1) == 1,
                indices = indices.toIntArray(),
                buffer = FloatArray(indices.size * 2),
                paint = Paint().apply { strokeCap = StrokeCap.Round },
            )
        }
    }
    val masteredIndices = remember(stars) { stars.indices.filter { stars[it].mastered } }
    val dueIndices = remember(stars) { stars.indices.filter { stars[it].due } }
    val selectedStar = remember(selectedId, stars) { stars.firstOrNull { it.id == selectedId } }
    val backdropGroups = remember {
        val random = Random(44)
        val lists = Array(3) { ArrayList<Float>() }
        repeat(260) {
            val x = random.nextFloat()
            val y = random.nextFloat()
            val radius = 0.6f + random.nextFloat() * 1.9f
            random.nextFloat()
            val bucket = if (radius < 1.15f) 0 else if (radius < 1.9f) 1 else 2
            lists[bucket].add(x)
            lists[bucket].add(y)
        }
        val radii = floatArrayOf(1.1f, 1.8f, 2.6f)
        val alphas = floatArrayOf(0.22f, 0.32f, 0.42f)
        lists.mapIndexed { index, fractions ->
            BackdropGroup(
                radius = radii[index],
                alpha = alphas[index],
                fractions = fractions.toFloatArray(),
                buffer = FloatArray(fractions.size),
                paint = Paint().apply {
                    color = Cream.copy(alpha = alphas[index])
                    strokeWidth = radii[index]
                    strokeCap = StrokeCap.Round
                },
            )
        }
    }
    val labelStyle = remember {
        TextStyle(
            color = Cream.copy(alpha = 0.72f),
            fontSize = 12.5.sp,
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.SemiBold,
        )
    }
    val labelStyleSelected = remember {
        TextStyle(
            color = Cream,
            fontSize = 12.5.sp,
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.SemiBold,
        )
    }

    val backgroundBitmap = remember(size) {
        if (size.width <= 0 || size.height <= 0) {
            null
        } else {
            val bitmap = ImageBitmap(size.width, size.height)
            CanvasDrawScope().draw(
                density = density,
                layoutDirection = LayoutDirection.Ltr,
                canvas = RenderCanvas(bitmap),
                size = Size(size.width.toFloat(), size.height.toFloat()),
            ) {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF12293D), Sky, SkyDeep),
                        center = Offset(size.width * 0.62f, size.height * 0.38f),
                        radius = max(size.width, size.height) * 0.86f,
                    )
                )
                drawCircle(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF254B63).copy(alpha = 0.38f), Color.Transparent),
                        center = Offset(size.width * 0.3f, size.height * 0.62f),
                        radius = size.width * 0.45f,
                    ),
                    radius = size.width * 0.45f,
                    center = Offset(size.width * 0.3f, size.height * 0.62f),
                )
                drawCircle(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF4A2C3F).copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(size.width * 0.82f, size.height * 0.2f),
                        radius = size.width * 0.34f,
                    ),
                    radius = size.width * 0.34f,
                    center = Offset(size.width * 0.82f, size.height * 0.2f),
                )
                for (group in backdropGroups) {
                    val fractions = group.fractions
                    val buffer = group.buffer
                    var index = 0
                    var write = 0
                    while (index < fractions.size) {
                        buffer[write++] = fractions[index++] * size.width
                        buffer[write++] = fractions[index++] * size.height
                    }
                    drawContext.canvas.drawRawPoints(PointMode.Points, buffer, group.paint)
                }
            }
            bitmap
        }
    }

    val gesturing = remember { mutableStateOf(false) }
    val starSnapshot = remember { mutableStateOf<StarSnapshot?>(null) }
    var displayZoom by remember { mutableFloatStateOf(1f) }
    var rasterTick by remember { mutableIntStateOf(0) }
    val snapshotGroups = remember(starGroups) {
        starGroups.map {
            StarGroup(
                topic = it.topic,
                sizeClass = it.sizeClass,
                learned = it.learned,
                indices = it.indices,
                buffer = FloatArray(it.indices.size * 2),
                paint = Paint().apply { strokeCap = StrokeCap.Round },
            )
        }
    }
    LaunchedEffect(stars, selectedTopic, size, rasterTick) {
        if (size.width <= 0 || size.height <= 0) {
            starSnapshot.value = null
            return@LaunchedEffect
        }
        if (rasterTick > 0) {
            delay(220)
            while (gesturing.value) delay(90)
        }
        val zoomAt = zoom
        val panAt = pan
        val bitmap = withContext(Dispatchers.Default) {
            rasterizeStarLayer(
                width = size.width,
                height = size.height,
                density = density,
                stars = stars,
                groups = snapshotGroups,
                masteredIndices = masteredIndices,
                dueIndices = dueIndices,
                colorsByTopic = colorsByTopic,
                zoom = zoomAt,
                pan = panAt,
                selectedTopic = selectedTopic,
            )
        }
        starSnapshot.value = StarSnapshot(bitmap, zoomAt, panAt)
    }

    fun base(canvas: IntSize): Float = min(canvas.width, canvas.height) * 0.46f
    fun project(node: GalaxyNode, canvas: IntSize): Offset {
        val b = base(canvas) * zoom
        return Offset(
            x = canvas.width / 2f + node.x * b + pan.x,
            y = canvas.height / 2f + node.z * b * 0.66f + node.y * b * 0.9f + pan.y,
        )
    }
    fun selectNode(id: String) {
        selectedId = id
        scope.launch {
            selectedWord = withContext(Dispatchers.IO) { graph.content.word(id) }
        }
    }
    fun tapAt(position: Offset) {
        if (size == IntSize.Zero) return
        val threshold = with(density) { 28.dp.toPx() }
        var best: GalaxyNode? = null
        var bestDistance = threshold * threshold
        state.nodes.forEach { node ->
            val p = project(node, size)
            val d = (p.x - position.x) * (p.x - position.x) + (p.y - position.y) * (p.y - position.y)
            if (d < bestDistance) {
                bestDistance = d
                best = node
            }
        }
        val hit = best
        if (hit != null) selectNode(hit.id) else {
            selectedId = null
            selectedWord = null
        }
    }

    Box(Modifier.fillMaxSize().background(Sky)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, gesturePan, gestureZoom, _ ->
                        if (size == IntSize.Zero) return@detectTransformGestures
                        val oldZoom = zoom
                        val newZoom = (zoom * gestureZoom).coerceIn(1f, 11f)
                        if (newZoom != oldZoom) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val v = centroid - center - pan
                            pan += v * (1f - newZoom / oldZoom)
                        }
                        zoom = newZoom
                        pan += gesturePan
                        val limit = min(size.width, size.height) * 0.55f * zoom
                        pan = Offset(pan.x.coerceIn(-limit, limit), pan.y.coerceIn(-limit, limit))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tapAt(it) },
                        onDoubleTap = { point ->
                            val target = if (zoom > 2.4f) 1f else 4.2f
                            if (size != IntSize.Zero) {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val v = point - center - pan
                                pan += v * (1f - target / zoom)
                            }
                            zoom = target
                            displayZoom = target
                            rasterTick++
                        },
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        gesturing.value = true
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                        } while (event.changes.any { it.pressed })
                        gesturing.value = false
                        displayZoom = zoom
                        rasterTick++
                    }
                },
        ) {
            backgroundBitmap?.let { drawImage(it) }

            if (!gesturing.value && size != IntSize.Zero) {
                val b = base(size) * zoom
                val originX = size.width / 2f + pan.x
                val originY = size.height / 2f + pan.y
                val zoomFactor = 0.85f + (zoom - 1f) * 0.05f

                drawStarLayer(
                    stars = stars,
                    groups = starGroups,
                    masteredIndices = masteredIndices,
                    dueIndices = dueIndices,
                    colorsByTopic = colorsByTopic,
                    originX = originX,
                    originY = originY,
                    span = b,
                    zoomFactor = zoomFactor,
                    selectedTopic = selectedTopic,
                )

                selectedStar?.let { star ->
                    val p = Offset(originX + star.nx * b, originY + star.ny * b)
                    val radius = (1.35f + star.sizeBucket * 0.34f) * zoomFactor
                    drawCircle(Cream.copy(alpha = 0.9f), radius = radius + 8.5f, center = p, style = Stroke(width = 1.6f))
                }

                val focusLabels = zoom >= 2.15f || selectedTopic != null
                if (focusLabels) {
                    val labelLimit = if (selectedTopic != null) 34 else 24
                    var labels = 0
                    val labelGap = 58.dp.toPx()
                    val placed = ArrayList<Offset>(labelLimit)

                    for (star in stars) {
                        val inTopic = selectedTopic == null || star.topic == selectedTopic
                        if (!inTopic || labels >= labelLimit) continue
                        val p = Offset(originX + star.nx * b, originY + star.ny * b)
                        if (p.x < -30f || p.y < -30f || p.x > size.width + 30f || p.y > size.height + 30f) continue
                        val tooClose = placed.any { existing ->
                            hypot((existing.x - p.x).toDouble(), (existing.y - p.y).toDouble()) < labelGap
                        }
                        if (tooClose) continue
                        placed.add(p)
                        labels++
                        val style = if (star.id == selectedId) labelStyleSelected else labelStyle
                        val layout = textMeasurer.measure(star.word, style)
                        val topLeft = Offset(p.x + 7f, p.y - layout.size.height / 2f)
                        drawRoundRect(
                            color = SkyDeep.copy(alpha = 0.64f),
                            topLeft = Offset(topLeft.x - 4f, topLeft.y - 1f),
                            size = Size(layout.size.width + 8f, layout.size.height.toFloat() + 2f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                        )
                        drawText(layout, topLeft = topLeft)
                    }
                }
            }
        }

        StarSnapshotLayer(
            gesturing = gesturing,
            snapshot = starSnapshot,
            zoom = { zoom },
            pan = { pan },
        )

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                VerbiMark(Modifier.size(26.dp), color = Cream.copy(alpha = 0.85f), accent = SkyBrass)
                Spacer(Modifier.width(10.dp))
                Text(
                    "THE GALAXY",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 3.4.sp,
                    color = Cream.copy(alpha = 0.8f),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, Cream.copy(alpha = 0.14f), CircleShape)
                        .clickable(onClick = onSearch),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Search, "Search", tint = Cream, modifier = Modifier.size(19.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "A sky of words.",
                fontFamily = FrauncesFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 32.sp,
                color = Cream,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "Every star is a word. Gold stars are mastered; terracotta rings are due for review.",
                fontFamily = ManropeFamily,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = Cream.copy(alpha = 0.52f),
                textAlign = TextAlign.Justify,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(end = 12.dp),
            ) {
                item {
                    SkyChip(
                        label = "All stars",
                        selected = selectedTopic == null,
                        onClick = { selectedTopic = null },
                    )
                }
                itemsIndexed(state.topics) { index, topic ->
                    SkyChip(
                        label = topic.label,
                        selected = selectedTopic == index,
                        color = Color(topic.color),
                        onClick = { selectedTopic = if (selectedTopic == index) null else index },
                    )
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .padding(bottom = 78.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = selectedWord != null,
                enter = slideInVertically { it / 2 } + fadeIn(),
                exit = slideOutVertically { it / 2 } + fadeOut(),
            ) {
                val word = selectedWord
                if (word != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xF20F1F2B))
                            .border(1.dp, Cream.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
                            .clickable { onWord(word.id) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WordImage(word.image, Modifier.size(62.dp), corner = 14.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    word.word,
                                    fontFamily = FrauncesFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 20.sp,
                                    color = Cream,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.width(8.dp))
                                LevelBadge(word.levelLabel)
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                word.firstSense,
                                fontFamily = ManropeFamily,
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                color = Cream.copy(alpha = 0.6f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(Icons.Rounded.ArrowForward, null, tint = SkyBrass, modifier = Modifier.size(18.dp).padding(start = 2.dp))
                    }
                }
            }
            if (selectedWord != null) Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xB30A1822))
                        .border(1.dp, Cream.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkyControl(Icons.Rounded.Remove, "Zoom out") {
                        zoom = (zoom / 1.4f).coerceAtLeast(1f)
                        displayZoom = zoom
                        rasterTick++
                    }
                    ZoomPercent { displayZoom }
                    SkyControl(Icons.Rounded.Add, "Zoom in") {
                        zoom = (zoom * 1.4f).coerceAtMost(11f)
                        displayZoom = zoom
                        rasterTick++
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xB30A1822))
                        .border(1.dp, Cream.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                        .clickable {
                            zoom = 1f
                            displayZoom = 1f
                            rasterTick++
                            pan = Offset.Zero
                            selectedId = null
                            selectedWord = null
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        "Reset view",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = Cream.copy(alpha = 0.8f),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Pinch · tap a star",
                    fontFamily = ManropeFamily,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    color = Cream.copy(alpha = 0.38f),
                )
            }
        }

        if (state.loading) {
            Box(
                Modifier.fillMaxSize().background(Sky.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LoadingField()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Charting the sky…",
                        fontFamily = ManropeFamily,
                        fontSize = 13.5.sp,
                        color = Cream.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StarSnapshotLayer(
    gesturing: State<Boolean>,
    snapshot: State<StarSnapshot?>,
    zoom: () -> Float,
    pan: () -> Offset,
) {
    if (!gesturing.value) return
    val snap = snapshot.value ?: return
    Image(
        bitmap = snap.image,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val s = zoom() / snap.zoom
                scaleX = s
                scaleY = s
                translationX = pan().x - snap.pan.x * s
                translationY = pan().y - snap.pan.y * s
            },
    )
}

@Composable
private fun ZoomPercent(readZoom: () -> Float) {
    Text(
        "${(readZoom() * 100).toInt()}%",
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.5.sp,
        color = Cream.copy(alpha = 0.7f),
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}

@Composable
private fun SkyChip(
    label: String,
    selected: Boolean,
    color: Color = SkyBrass,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Cream.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (selected) Cream else Cream.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(if (selected) Sky else color))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.5.sp,
            color = if (selected) Sky else Cream.copy(alpha = 0.78f),
        )
    }
}

@Composable
private fun SkyControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Cream.copy(alpha = 0.75f), modifier = Modifier.size(17.dp))
    }
}
