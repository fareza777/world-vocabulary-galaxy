package com.verbilume.app.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.verbilume.app.data.AchievementState
import com.verbilume.app.data.AppGraph
import com.verbilume.app.ui.LoadingField
import com.verbilume.app.ui.PageTopBar
import com.verbilume.app.ui.PaperBackground
import com.verbilume.app.ui.graphViewModel
import com.verbilume.app.ui.theme.FrauncesFamily
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.theme.ManropeFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class AchievementsUiState(
    val loading: Boolean = true,
    val achievements: List<AchievementState> = emptyList(),
)

class AchievementsViewModel(private val graph: AppGraph) : ViewModel() {
    private val _state = MutableStateFlow(AchievementsUiState())
    val state: StateFlow<AchievementsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            graph.learning.dataVersion.collect {
                runCatching { graph.learning.achievements() }
                    .onSuccess { _state.value = AchievementsUiState(false, it) }
                    .onFailure { _state.value = _state.value.copy(loading = false) }
            }
        }
    }
}

private enum class TrophyFilter(val label: String) { ALL("All"), EARNED("Earned"), NEXT("In progress") }

private val earnedDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

@Composable
fun AchievementsScreen(
    graph: AppGraph,
    onBack: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val viewModel = graphViewModel { AchievementsViewModel(graph) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(TrophyFilter.ALL) }

    val unlocked = state.achievements.count { it.unlocked }
    val all = state.achievements
    val visible = remember(all, filter) {
        when (filter) {
            TrophyFilter.ALL -> all
            TrophyFilter.EARNED -> all.filter { it.unlocked }
            TrophyFilter.NEXT -> all.filter { !it.unlocked }.sortedByDescending { it.fraction }
        }
    }

    PaperBackground {
        Column(Modifier.fillMaxSize()) {
            PageTopBar("Achievements", onBack = onBack)
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingField() }
                return@Column
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(2) }) {
                    Column {
                        Text(
                            "Small proofs of\nquiet work.",
                            fontFamily = FrauncesFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 32.sp,
                            lineHeight = 36.sp,
                            letterSpacing = (-0.6).sp,
                            color = colors.ink,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Every trophy is tied to something you actually did — no shortcuts, no purchases.",
                            fontFamily = ManropeFamily,
                            fontSize = 13.5.sp,
                            lineHeight = 18.sp,
                            color = colors.inkSoft,
                            textAlign = TextAlign.Justify,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "$unlocked of ${all.size} earned",
                                fontFamily = ManropeFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                                color = colors.ink,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (all.isEmpty()) "0%" else "${(unlocked * 100f / all.size).roundToInt()}%",
                                fontFamily = ManropeFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = colors.brass,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.sunken)) {
                            Box(
                                Modifier
                                    .fillMaxWidth(if (all.isEmpty()) 0f else unlocked.toFloat() / all.size)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(colors.brass),
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TrophyFilter.entries.forEach { option ->
                                val active = option == filter
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (active) colors.ink else colors.raised)
                                        .border(1.dp, if (active) colors.ink else colors.line, RoundedCornerShape(12.dp))
                                        .clickable { filter = option }
                                        .padding(horizontal = 13.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        option.label,
                                        fontFamily = ManropeFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = if (active) colors.paper else colors.inkSoft,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
                if (visible.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                if (filter == TrophyFilter.EARNED) "No trophies here yet" else "Everything is earned",
                                fontFamily = FrauncesFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 22.sp,
                                color = colors.ink,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (filter == TrophyFilter.EARNED) "Finish a review, open the daily challenge, or start a streak to earn your first." else "Every achievement in the collection has been unlocked.",
                                fontFamily = ManropeFamily,
                                fontSize = 13.sp,
                                lineHeight = 17.sp,
                                color = colors.inkFaint,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    items(visible, key = { it.def.id }) { achievement ->
                        TrophyCard(achievement)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrophyCard(state: AchievementState) {
    val colors = LocalVerbiColors.current
    val tierColor = when (state.def.tier) {
        "gold" -> colors.brassBright
        "silver" -> colors.inkSoft
        "diamond" -> colors.teal
        else -> colors.brass
    }
    val tint = if (state.unlocked) tierColor else colors.inkFaint
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 178.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (state.unlocked) colors.raised else colors.sunken.copy(alpha = 0.5f))
            .border(
                1.dp,
                if (state.unlocked) tierColor.copy(alpha = 0.3f) else colors.line.copy(alpha = 0.6f),
                RoundedCornerShape(22.dp),
            )
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (state.unlocked) tint.copy(alpha = 0.14f) else colors.line.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    achievementIcon(state.def.icon),
                    null,
                    tint = tint,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                state.def.tier.uppercase(),
                fontFamily = ManropeFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                letterSpacing = 1.2.sp,
                color = tint.copy(alpha = 0.85f),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            state.def.title,
            fontFamily = FrauncesFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 21.sp,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (state.unlocked) colors.ink else colors.inkSoft,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            state.def.description,
            fontFamily = ManropeFamily,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = colors.inkFaint,
        )
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.line.copy(alpha = 0.7f))) {
            Box(
                Modifier
                    .fillMaxWidth(state.fraction)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (state.unlocked) tint else tint.copy(alpha = 0.55f)),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (state.unlocked) {
                state.unlockedAt?.let {
                    "Earned ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(earnedDate)}"
                } ?: "Earned"
            } else {
                "${state.progress.coerceAtMost(state.def.target)} / ${state.def.target}"
            },
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = if (state.unlocked) tint else colors.inkFaint,
        )
    }
}

private fun achievementIcon(key: String): ImageVector = when (key) {
    "star" -> Icons.Rounded.Star
    "key" -> Icons.Rounded.VpnKey
    "flame" -> Icons.Rounded.LocalFireDepartment
    "moon" -> Icons.Rounded.DarkMode
    "sparkles" -> Icons.Rounded.AutoAwesome
    "book" -> Icons.Rounded.MenuBook
    "roots" -> Icons.Rounded.Park
    "compass" -> Icons.Rounded.Explore
    "bolt" -> Icons.Rounded.Bolt
    "sun" -> Icons.Rounded.WbSunny
    "feather" -> Icons.Rounded.Edit
    "owl" -> Icons.Rounded.Visibility
    "sunrise" -> Icons.Rounded.WbTwilight
    "bookmark" -> Icons.Rounded.Bookmark
    "calendar" -> Icons.Rounded.CalendarMonth
    "crown" -> Icons.Rounded.WorkspacePremium
    "quill" -> Icons.Rounded.Edit
    "doors" -> Icons.Rounded.MeetingRoom
    "hourglass" -> Icons.Rounded.HourglassEmpty
    "candle" -> Icons.Rounded.LightMode
    else -> Icons.Rounded.EmojiEvents
}
