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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.verbilume.app.data.AppGraph
import com.verbilume.app.data.CEFR_LEVELS
import com.verbilume.app.data.ContentMeta
import com.verbilume.app.data.LearningRepository
import com.verbilume.app.data.WordQuery
import com.verbilume.app.data.WordSummary
import com.verbilume.app.ui.PaperBackground
import com.verbilume.app.ui.SectionHeading
import com.verbilume.app.ui.VerbiMark
import com.verbilume.app.ui.WordImage
import com.verbilume.app.ui.WordTile
import com.verbilume.app.ui.formatNumber
import com.verbilume.app.ui.graphViewModel
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.theme.ManropeFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AtlasUiState(
    val loading: Boolean = true,
    val topics: List<com.verbilume.app.data.TopicInfo> = emptyList(),
    val words: List<WordSummary> = emptyList(),
    val total: Int = 0,
    val selectedTopic: Int? = null,
    val selectedLevel: Int? = null,
    val meta: ContentMeta? = null,
    val seenByTopic: Map<Int, Int> = emptyMap(),
    val loadingMore: Boolean = false,
)

class AtlasViewModel(private val graph: AppGraph) : ViewModel() {
    private val _state = MutableStateFlow(AtlasUiState())
    val state: StateFlow<AtlasUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) { graph.content.meta }
            val progress = runCatching { graph.learning.topicProgress() }.getOrDefault(emptyList())
            _state.value = _state.value.copy(
                meta = meta,
                seenByTopic = progress.mapIndexed { index, item -> index to item.seen }.toMap(),
            )
            refresh()
        }
    }

    fun selectTopic(index: Int?) {
        if (_state.value.selectedTopic == index) return
        _state.value = _state.value.copy(selectedTopic = index)
        refresh()
    }

    fun selectLevel(level: Int?) {
        if (_state.value.selectedLevel == level) return
        _state.value = _state.value.copy(selectedLevel = level)
        refresh()
    }

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val current = _state.value
            val result = withContext(Dispatchers.IO) {
                val page = graph.content.page(
                    WordQuery(
                        topic = current.selectedTopic,
                        cefr = current.selectedLevel,
                        limit = PAGE,
                        offset = 0,
                    )
                )
                page to graph.content.count(current.selectedTopic, current.selectedLevel)
            }
            _state.value = _state.value.copy(loading = false, words = result.first, total = result.second)
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || current.words.size >= current.total) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true)
            val more = withContext(Dispatchers.IO) {
                graph.content.page(
                    WordQuery(
                        topic = current.selectedTopic,
                        cefr = current.selectedLevel,
                        limit = PAGE,
                        offset = current.words.size,
                    )
                )
            }
            _state.value = _state.value.copy(loadingMore = false, words = _state.value.words + more)
        }
    }

    private companion object { const val PAGE = 60 }
}

@Composable
fun AtlasScreen(
    graph: AppGraph,
    onWord: (String) -> Unit,
    onTopic: (Int) -> Unit,
    onSearch: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val viewModel = graphViewModel { AtlasViewModel(graph) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    PaperBackground {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 128.dp + navBarBottom),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            item(span = { GridItemSpan(2) }) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VerbiMark(Modifier.size(26.dp), color = colors.ink, accent = colors.accent)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "THE ATLAS",
                            fontFamily = ManropeFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 3.4.sp,
                            color = colors.ink,
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(colors.raised)
                                .border(1.dp, colors.line, CircleShape)
                                .clickable(onClick = onSearch),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Search, "Search the atlas", tint = colors.ink, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(
                        "A visual dictionary,\nquietly arranged.",
                        fontFamily = com.verbilume.app.ui.theme.FrauncesFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 31.sp,
                        lineHeight = 35.sp,
                        letterSpacing = (-0.6).sp,
                        color = colors.ink,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${formatNumber(state.meta?.wordCount ?: 7298)} words",
                            fontFamily = ManropeFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = colors.inkSoft,
                        )
                        Dot(colors.inkFaint)
                        Text(
                            "${state.meta?.topics?.size ?: 19} realms",
                            fontFamily = ManropeFamily,
                            fontSize = 14.sp,
                            color = colors.inkSoft,
                        )
                        Dot(colors.inkFaint)
                        Text(
                            "A1 to C2",
                            fontFamily = ManropeFamily,
                            fontSize = 14.sp,
                            color = colors.inkSoft,
                        )
                    }
                }
            }

            item(span = { GridItemSpan(2) }) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    item {
                        AllTopicsCard(selected = state.selectedTopic == null) {
                            viewModel.selectTopic(null)
                        }
                    }
                    items(state.meta?.topics.orEmpty().withIndex().toList(), key = { it.value.key }) { indexed ->
                        val topic = indexed.value
                        TopicCard(
                            label = topic.label,
                            count = topic.count,
                            art = topic.art,
                            accent = Color(topic.color),
                            selected = state.selectedTopic == indexed.index,
                            seen = state.seenByTopic[indexed.index],
                            onClick = { viewModel.selectTopic(indexed.index) },
                        )
                    }
                }
            }

            item(span = { GridItemSpan(2) }) {
                Column {
                    SectionHeading(
                        title = "Field guide",
                        eyebrow = if (state.selectedTopic == null) "EVERY WORD, ILLUSTRATED" else "ONE REALM AT A TIME",
                        action = null,
                        onAction = null,
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        contentPadding = PaddingValues(end = 6.dp),
                    ) {
                        item {
                            LevelPill("All", selected = state.selectedLevel == null) { viewModel.selectLevel(null) }
                        }
                        items(CEFR_LEVELS.indices.toList()) { index ->
                            LevelPill(CEFR_LEVELS[index], selected = state.selectedLevel == index) {
                                viewModel.selectLevel(if (state.selectedLevel == index) null else index)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Tune, null, tint = colors.inkFaint, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${formatNumber(state.total)} words in view",
                            fontFamily = ManropeFamily,
                            fontSize = 13.5.sp,
                            color = colors.inkFaint,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            if (state.loading) {
                item(span = { GridItemSpan(2) }) {
                    com.verbilume.app.ui.LoadingField(Modifier.padding(top = 30.dp))
                }
            } else if (state.words.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    com.verbilume.app.ui.EmptyState(
                        title = "No words here yet",
                        description = "Try a different realm or level.",
                    )
                }
            } else {
                items(state.words, key = { it.id }) { word ->
                    WordTile(word = word, onClick = { onWord(word.id) })
                }
                if (state.words.size < state.total) {
                    item(span = { GridItemSpan(2) }) {
                        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                            com.verbilume.app.ui.SecondaryButton(
                                text = if (state.loadingMore) "Gathering…" else "Show more words",
                                onClick = viewModel::loadMore,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        Modifier.padding(horizontal = 8.dp).size(3.dp).clip(CircleShape).background(color.copy(alpha = 0.6f))
    )
}

@Composable
private fun LevelPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalVerbiColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.ink else colors.raised)
            .border(1.dp, if (selected) colors.ink else colors.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.5.sp,
            color = if (selected) colors.paper else colors.inkSoft,
        )
    }
}

@Composable
private fun TopicCard(
    label: String,
    count: Int,
    art: String,
    accent: Color,
    selected: Boolean,
    seen: Int?,
    onClick: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    Column(
        Modifier
            .width(154.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.raised)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.line, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.04f).clip(RoundedCornerShape(18.dp))) {
            WordImage(art, Modifier.fillMaxSize(), corner = 18.dp)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.42f to Color.Transparent,
                            1f to colors.ink.copy(alpha = 0.9f),
                        )
                    )
            )
            Column(
                Modifier.align(Alignment.BottomStart).padding(12.dp),
            ) {
                Text(
                    label,
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    color = Color(0xFFFBF6EC),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "${formatNumber(count)} words",
                    fontFamily = ManropeFamily,
                    fontSize = 12.5.sp,
                    color = Color(0xFFFBF6EC).copy(alpha = 0.85f),
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
        if (seen != null && seen > 0) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.weight(1f).height(3.dp).clip(CircleShape).background(colors.line)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((seen.toFloat() / count.coerceAtLeast(1)).coerceIn(0f, 1f))
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "$seen",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = colors.inkFaint,
                )
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AllTopicsCard(selected: Boolean, onClick: () -> Unit) {
    val colors = LocalVerbiColors.current
    Column(
        Modifier
            .width(122.dp)
            .height(196.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) colors.ink else colors.sunken)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        VerbiMark(
            Modifier.size(30.dp),
            color = if (selected) colors.paper else colors.inkSoft,
            accent = if (selected) colors.brassBright else colors.accent,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "All\nrealms",
            fontFamily = com.verbilume.app.ui.theme.FrauncesFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            lineHeight = 22.sp,
            color = if (selected) colors.paper else colors.ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (selected) "Showing everything" else "Wander the whole atlas",
            fontFamily = ManropeFamily,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            color = if (selected) colors.paper.copy(alpha = 0.65f) else colors.inkSoft,
        )
    }
}
