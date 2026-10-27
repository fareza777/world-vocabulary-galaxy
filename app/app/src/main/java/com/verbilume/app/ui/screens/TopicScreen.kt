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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.verbilume.app.data.AppGraph
import com.verbilume.app.data.AppSettings
import com.verbilume.app.data.TopicInfo
import com.verbilume.app.data.TopicProgress
import com.verbilume.app.data.WordSummary
import com.verbilume.app.ui.BackButton
import com.verbilume.app.ui.EmptyState
import com.verbilume.app.ui.LoadingField
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.PaperBackground
import com.verbilume.app.ui.WordImage
import com.verbilume.app.ui.WordTile
import com.verbilume.app.ui.formatNumber
import com.verbilume.app.ui.graphViewModel
import com.verbilume.app.ui.theme.FrauncesFamily
import com.verbilume.app.ui.theme.ManropeFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TopicUiState(
    val loading: Boolean = true,
    val topic: TopicInfo? = null,
    val progress: TopicProgress? = null,
    val words: List<WordSummary> = emptyList(),
    val myLevelOnly: Boolean = false,
    val level: Int = 2,
)

class TopicViewModel(
    private val graph: AppGraph,
    private val topicIndex: Int,
    settings: AppSettings,
) : ViewModel() {
    private val _state = MutableStateFlow(TopicUiState(level = settings.level))
    val state: StateFlow<TopicUiState> = _state.asStateFlow()

    init { load(settings.level) }

    fun toggleLevel() {
        val current = _state.value
        _state.value = current.copy(myLevelOnly = !current.myLevelOnly)
        load(current.level)
    }

    private fun load(userLevel: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, level = userLevel)
            val onlyMine = _state.value.myLevelOnly
            val loaded = withContext(Dispatchers.IO) {
                val meta = graph.content.meta
                val topic = meta.topics.getOrNull(topicIndex)
                val progress = runCatching { graph.learning.topicProgress() }.getOrNull()?.getOrNull(topicIndex)
                val words = if (topic == null) emptyList() else {
                    graph.content.topicWords(topicIndex, if (onlyMine) userLevel else null, 400)
                }
                Triple(topic, progress, words)
            }
            _state.value = _state.value.copy(
                loading = false,
                topic = loaded.first,
                progress = loaded.second,
                words = loaded.third,
            )
        }
    }
}

@Composable
fun TopicScreen(
    graph: AppGraph,
    settings: AppSettings,
    topicIndex: Int,
    onBack: () -> Unit,
    onWord: (String) -> Unit,
) {
    val colors = LocalVerbiColors.current
    val viewModel = graphViewModel(key = "topic-$topicIndex") { TopicViewModel(graph, topicIndex, settings) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val topic = state.topic
    val accent = topic?.let { Color(it.color) } ?: colors.brass

    PaperBackground {
        if (topic == null && state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingField() }
        } else if (topic == null) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                BackButton(onBack)
                EmptyState("Realm not found", "This topic is not part of the current atlas.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                item(span = { GridItemSpan(2) }) {
                    Box(
                        Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(30.dp)),
                    ) {
                        WordImage(topic.art, Modifier.fillMaxSize(), corner = 30.dp)
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to colors.ink.copy(alpha = 0.2f),
                                        0.5f to Color.Transparent,
                                        1f to colors.ink.copy(alpha = 0.86f),
                                    )
                                )
                        )
                        BackButton(onBack, Modifier.padding(12.dp).align(Alignment.TopStart))
                        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                            Text(
                                "REALM",
                                fontFamily = ManropeFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                letterSpacing = 2.4.sp,
                                color = Color(0xFFFBF6EC).copy(alpha = 0.68f),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                topic.label,
                                fontFamily = FrauncesFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 34.sp,
                                lineHeight = 38.sp,
                                color = Color(0xFFFBF6EC),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${formatNumber(topic.count)} words · A1–C2",
                                fontFamily = ManropeFamily,
                                fontSize = 13.sp,
                                color = Color(0xFFFBF6EC).copy(alpha = 0.72f),
                            )
                        }
                    }
                }

                item(span = { GridItemSpan(2) }) {
                    Column(Modifier.padding(top = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "YOUR FOOTPRINT",
                                    fontFamily = ManropeFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    letterSpacing = 2.2.sp,
                                    color = colors.inkFaint,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${state.progress?.seen ?: 0} seen · ${state.progress?.mastered ?: 0} mastered",
                                    fontFamily = ManropeFamily,
                                    fontSize = 14.sp,
                                    color = colors.inkSoft,
                                )
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (state.myLevelOnly) colors.ink else colors.raised)
                                    .border(1.dp, if (state.myLevelOnly) colors.ink else colors.line, RoundedCornerShape(12.dp))
                                    .clickable(onClick = viewModel::toggleLevel)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    if (state.myLevelOnly) "Up to ${settings.levelLabel}" else "All levels",
                                    fontFamily = ManropeFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    color = if (state.myLevelOnly) colors.paper else colors.inkSoft,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)).background(colors.line)) {
                            Box(
                                Modifier
                                    .fillMaxWidth(((state.progress?.seen ?: 0).toFloat() / topic.count.coerceAtLeast(1)).coerceIn(0f, 1f))
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(accent)
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }

                if (state.loading) {
                    item(span = { GridItemSpan(2) }) { LoadingField(Modifier.padding(top = 20.dp)) }
                } else if (state.words.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        EmptyState("Nothing here yet", "No words match this level in ${topic.label}.")
                    }
                } else {
                    items(state.words, key = { it.id }) { word ->
                        WordTile(word, onClick = { onWord(word.id) })
                    }
                }
            }
        }
    }
}
