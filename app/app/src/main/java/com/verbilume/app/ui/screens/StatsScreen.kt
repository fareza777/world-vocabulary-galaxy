package com.verbilume.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.verbilume.app.data.AppGraph
import com.verbilume.app.data.AppSettings
import com.verbilume.app.data.ContentMeta
import com.verbilume.app.data.DayStat
import com.verbilume.app.data.ForecastDay
import com.verbilume.app.data.LearningStats
import com.verbilume.app.data.TopicProgress
import com.verbilume.app.data.TypingStats
import com.verbilume.app.ui.LoadingField
import com.verbilume.app.ui.PageTopBar
import com.verbilume.app.ui.PaperBackground
import com.verbilume.app.ui.formatNumber
import com.verbilume.app.ui.graphViewModel
import com.verbilume.app.ui.plural
import com.verbilume.app.ui.theme.FrauncesFamily
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.theme.ManropeFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

data class StatsPageState(
    val loading: Boolean = true,
    val stats: LearningStats? = null,
    val heat: List<DayStat> = emptyList(),
    val forecast: List<ForecastDay> = emptyList(),
    val topics: List<TopicProgress> = emptyList(),
    val typing: TypingStats? = null,
    val meta: ContentMeta? = null,
)

class StatsPageViewModel(private val graph: AppGraph) : ViewModel() {
    private val _state = MutableStateFlow(StatsPageState())
    val state: StateFlow<StatsPageState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(graph.settings.settings, graph.learning.dataVersion) { settings, _ -> settings }
                .collect { settings -> load(settings) }
        }
    }

    private suspend fun load(settings: AppSettings) {
        runCatching {
            val stats = graph.learning.stats(settings)
            val heat = graph.learning.heatmap(16)
            val forecast = graph.learning.forecast(7)
            val topics = graph.learning.topicProgress()
            val typing = graph.learning.typingStats()
            val meta = withContext(Dispatchers.IO) { graph.content.meta }
            StatsPageState(false, stats, heat, forecast, topics, typing, meta)
        }.onSuccess { _state.value = it }
            .onFailure { _state.value = _state.value.copy(loading = false) }
    }
}

@Composable
fun StatsScreen(
    graph: AppGraph,
    onBack: () -> Unit,
    onAtlas: () -> Unit,
    onTyping: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val viewModel = graphViewModel { StatsPageViewModel(graph) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats = state.stats

    PaperBackground {
        Column(Modifier.fillMaxSize()) {
            PageTopBar("Statistics", onBack = onBack)
            if (state.loading && stats == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingField() }
                return@Column
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Text(
                        "The shape of\nyour progress.",
                        fontFamily = FrauncesFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                        letterSpacing = (-0.6).sp,
                        color = colors.ink,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (stats != null) {
                            "${plural(stats.daysStudied, "day")} studied · ${formatNumber(stats.totalReviews)} reviews · ${formatNumber(stats.totalXp)} XP earned"
                        } else {
                            "Your study record will appear here."
                        },
                        fontFamily = ManropeFamily,
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                        color = colors.inkSoft,
                    )
                }

                item { Spacer(Modifier.height(4.dp)) }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        InsightTile("Reviews", formatNumber(stats?.totalReviews ?: 0), "all time", colors.teal, Modifier.weight(1f))
                        InsightTile("Recall", "${((stats?.accuracy30 ?: 0f) * 100).roundToInt()}%", "last 30 days", colors.brass, Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        InsightTile("Mastered", formatNumber(stats?.masteredWords ?: 0), "stability ≥ 21d", colors.accent, Modifier.weight(1f))
                        InsightTile(
                            "Streak",
                            "${stats?.streak ?: 0}d",
                            "best ${stats?.longestStreak ?: 0}d",
                            colors.inkSoft,
                            Modifier.weight(1f),
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    HeatmapCard(state.heat)
                }

                item { ForecastCard(state.forecast) }

                item {
                    TopicsCard(
                        topics = state.topics,
                        totalWords = state.meta?.wordCount ?: 0,
                        onAtlas = onAtlas,
                    )
                }

                item { TypingCard(stats = state.typing, onTyping = onTyping) }
            }
        }
    }
}

@Composable
private fun InsightTile(label: String, value: String, caption: String, tint: Color, modifier: Modifier = Modifier) {
    val colors = LocalVerbiColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.raised)
            .border(1.dp, colors.line, RoundedCornerShape(20.dp))
            .padding(horizontal = 15.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
            Spacer(Modifier.width(7.dp))
            Text(
                label.uppercase(),
                fontFamily = ManropeFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.5.sp,
                color = colors.inkFaint,
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            value,
            fontFamily = FrauncesFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            color = colors.ink,
            maxLines = 1,
        )
        Text(
            caption,
            fontFamily = ManropeFamily,
            fontSize = 12.5.sp,
            color = colors.inkFaint,
        )
    }
}

@Composable
private fun HeatmapCard(heat: List<DayStat>) {
    val colors = LocalVerbiColors.current
    val weeks = remember(heat) { heat.chunked(7) }
    val levels = remember(colors) {
        listOf(
            colors.line.copy(alpha = 0.5f),
            colors.accent.copy(alpha = 0.2f),
            colors.accent.copy(alpha = 0.42f),
            colors.accent.copy(alpha = 0.68f),
            colors.accent,
        )
    }
    val activeDays = heat.count { it.xp > 0 }
    val totalXp = heat.sumOf { it.xp }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.raised)
            .border(1.dp, colors.line, RoundedCornerShape(24.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "RHYTHM",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    color = colors.brass,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sixteen weeks of light",
                    fontFamily = FrauncesFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp,
                    color = colors.ink,
                )
            }
            Text(
                "$activeDays active",
                fontFamily = ManropeFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = colors.inkSoft,
            )
        }
        Spacer(Modifier.height(16.dp))
        Canvas(Modifier.fillMaxWidth().height(132.dp)) {
            if (weeks.isEmpty()) return@Canvas
            val gap = 3.dp.toPx()
            val cell = minOf(
                (size.width - gap * (weeks.size - 1)) / weeks.size,
                (size.height - gap * 6) / 7,
            )
            val totalWidth = cell * weeks.size + gap * (weeks.size - 1)
            val startX = (size.width - totalWidth) / 2f
            weeks.forEachIndexed { column, week ->
                week.forEachIndexed { row, day ->
                    val level = when {
                        day.xp <= 0 -> 0
                        day.xp < 20 -> 1
                        day.xp < 60 -> 2
                        day.xp < 120 -> 3
                        else -> 4
                    }
                    val topLeft = Offset(startX + column * (cell + gap), row * (cell + gap))
                    drawRoundRect(
                        color = levels[level],
                        topLeft = topLeft,
                        size = Size(cell, cell),
                        cornerRadius = CornerRadius(cell * 0.24f),
                    )
                    val isToday = day.day == LocalDate.now().toString()
                    if (isToday) {
                        drawRoundRect(
                            color = colors.brass,
                            topLeft = topLeft,
                            size = Size(cell, cell),
                            cornerRadius = CornerRadius(cell * 0.24f),
                            style = Stroke(width = 1.4.dp.toPx()),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "LESS",
                fontFamily = ManropeFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                letterSpacing = 1.sp,
                color = colors.inkFaint,
            )
            Spacer(Modifier.width(6.dp))
            levels.forEach { level ->
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(level)
                )
                Spacer(Modifier.width(3.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                "MORE",
                fontFamily = ManropeFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                letterSpacing = 1.sp,
                color = colors.inkFaint,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${formatNumber(totalXp)} XP in view",
                fontFamily = ManropeFamily,
                fontSize = 12.5.sp,
                color = colors.inkFaint,
            )
        }
    }
}

@Composable
private fun ForecastCard(forecast: List<ForecastDay>) {
    val colors = LocalVerbiColors.current
    val maxDue = forecast.maxOfOrNull { it.due }?.coerceAtLeast(1) ?: 1
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.raised)
            .border(1.dp, colors.line, RoundedCornerShape(24.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "THE WEEK AHEAD",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    color = colors.brass,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Reviews scheduled by FSRS",
                    fontFamily = FrauncesFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp,
                    color = colors.ink,
                )
            }
            Text(
                "${forecast.sumOf { it.due }} due",
                fontFamily = ManropeFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = colors.inkSoft,
            )
        }
        Spacer(Modifier.height(18.dp))
        if (forecast.isEmpty()) {
            Text("No reviews are scheduled yet.", fontFamily = ManropeFamily, fontSize = 13.sp, color = colors.inkFaint)
        } else {
            Row(
                Modifier.fillMaxWidth().height(118.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                forecast.forEach { day ->
                    val fraction = day.due.toFloat() / maxDue
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (day.due > 0) "${day.due}" else "",
                            fontFamily = ManropeFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            color = if (day.isToday) colors.accent else colors.inkSoft,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.height(72.dp).fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.56f)
                                    .height(max(3f, 72f * fraction).dp)
                                    .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                    .background(if (day.isToday) colors.accent else colors.teal.copy(alpha = 0.55f)),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (day.isToday) "Today" else LocalDate.parse(day.day).dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.US),
                            fontFamily = ManropeFamily,
                            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp,
                            color = if (day.isToday) colors.accent else colors.inkFaint,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicsCard(topics: List<TopicProgress>, totalWords: Int, onAtlas: () -> Unit) {
    val colors = LocalVerbiColors.current
    val visible = remember(topics) { topics.filter { it.seen > 0 }.sortedByDescending { it.seen }.take(6) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.raised)
            .border(1.dp, colors.line, RoundedCornerShape(24.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "YOUR REALMS",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    color = colors.brass,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Where the words live",
                    fontFamily = FrauncesFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp,
                    color = colors.ink,
                )
            }
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onAtlas).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Atlas",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = colors.accent,
                )
                Icon(Icons.Rounded.ArrowForward, null, tint = colors.accent, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        if (visible.isEmpty()) {
            Text(
                "No realm has been opened yet. Start a review and your map will begin to fill.",
                fontFamily = ManropeFamily,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = colors.inkFaint,
            )
        } else {
            visible.forEachIndexed { index, topic ->
                if (index > 0) Spacer(Modifier.height(13.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(topic.topic.color)))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        topic.topic.label,
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${topic.seen}/${topic.total}",
                        fontFamily = ManropeFamily,
                        fontSize = 12.5.sp,
                        color = colors.inkFaint,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(colors.sunken)) {
                    Box(
                        Modifier
                            .fillMaxWidth(topic.fraction.coerceIn(0f, 1f))
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(topic.topic.color)),
                    )
                }
            }
            if (totalWords > 0) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Across ${topics.size} realms and ${formatNumber(totalWords)} words.",
                    fontFamily = ManropeFamily,
                    fontSize = 12.5.sp,
                    color = colors.inkFaint,
                )
            }
        }
    }
}

@Composable
private fun TypingCard(stats: TypingStats?, onTyping: () -> Unit) {
    val colors = LocalVerbiColors.current
    val runs = stats?.sessions ?: 0
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.ink)
            .clickable(onClick = onTyping)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(colors.paper.copy(alpha = 0.09f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Keyboard, null, tint = colors.brassBright, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "TYPING STUDIO",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.8.sp,
                    color = colors.brassBright,
                )
                Text(
                    if (runs > 0) "A steady hand" else "Words, sentences, dictation",
                    fontFamily = FrauncesFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    color = colors.paper,
                )
            }
            Icon(Icons.Rounded.ArrowForward, null, tint = colors.paper.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(16.dp))
        if (runs > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                DarkStat("Best speed", "${stats?.bestWpm ?: 0} WPM")
                DarkStat("Accuracy", "${stats?.bestAccuracy ?: 0}%")
                DarkStat("Typed", formatNumber(stats?.chars ?: 0))
            }
        } else {
            Text(
                "Type whole sentences or train your ear with dictation. Every keystroke is measured for speed and accuracy.",
                fontFamily = ManropeFamily,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = colors.paper.copy(alpha = 0.58f),
            )
        }
    }
}

@Composable
private fun DarkStat(label: String, value: String) {
    val colors = LocalVerbiColors.current
    Column {
        Text(
            label.uppercase(),
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp,
            letterSpacing = 1.2.sp,
            color = colors.paper.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            fontFamily = FrauncesFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            color = colors.brassBright,
        )
    }
}
