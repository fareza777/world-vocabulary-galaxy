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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import com.verbilume.app.data.AppGraph
import com.verbilume.app.data.AppSettings
import com.verbilume.app.data.RecentSearch
import com.verbilume.app.data.WordSummary
import com.verbilume.app.ui.LevelBadge
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.PaperBackground
import com.verbilume.app.ui.WordImage
import com.verbilume.app.ui.formatNumber
import com.verbilume.app.ui.graphViewModel
import com.verbilume.app.ui.theme.ManropeFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchUiState(
    val query: String = "",
    val results: List<WordSummary> = emptyList(),
    val recents: List<RecentSearch> = emptyList(),
    val suggestions: List<WordSummary> = emptyList(),
    val searching: Boolean = false,
    val totalWords: Int = 7298,
)

class SearchViewModel(private val graph: AppGraph) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val recent = withContext(Dispatchers.IO) { graph.learning.recentSearches() }
            val suggestions = withContext(Dispatchers.IO) {
                val seed = System.currentTimeMillis() / 3_600_000L
                graph.content.newWordBatch(2, emptySet(), seed, 8)
            }
            _state.value = _state.value.copy(recents = recent, suggestions = suggestions)
        }
    }

    fun updateQuery(value: String) {
        _state.value = _state.value.copy(query = value)
        searchJob?.cancel()
        if (value.isBlank()) {
            _state.value = _state.value.copy(results = emptyList(), searching = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(160)
            _state.value = _state.value.copy(searching = true)
            val hits = withContext(Dispatchers.IO) { graph.content.search(value.trim(), 40) }
            _state.value = _state.value.copy(results = hits, searching = false)
        }
    }

    fun submit() {
        val query = _state.value.query.trim()
        if (query.length < 2) return
        viewModelScope.launch {
            graph.learning.recordSearch(query)
            val recent = withContext(Dispatchers.IO) { graph.learning.recentSearches() }
            _state.value = _state.value.copy(recents = recent)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            graph.learning.clearSearchHistory()
            _state.value = _state.value.copy(recents = emptyList())
        }
    }
}

@Composable
fun SearchScreen(
    graph: AppGraph,
    settings: AppSettings,
    onBack: () -> Unit,
    onWord: (String) -> Unit,
) {
    val colors = LocalVerbiColors.current
    val viewModel = graphViewModel { SearchViewModel(graph) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(180)
        focus.requestFocus()
    }

    PaperBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.ArrowBack, "Back", tint = colors.ink, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.raised)
                        .border(1.dp, colors.line, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, tint = colors.inkFaint, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (state.query.isEmpty()) {
                            Text(
                                "Search words and meanings",
                                fontFamily = ManropeFamily,
                                fontSize = 15.sp,
                                color = colors.inkFaint,
                            )
                        }
                        BasicTextField(
                            value = state.query,
                            onValueChange = viewModel::updateQuery,
                            textStyle = TextStyle(
                                fontFamily = ManropeFamily,
                                fontSize = 15.sp,
                                color = colors.ink,
                                fontWeight = FontWeight.Medium,
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                viewModel.submit()
                                keyboard?.hide()
                            }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    if (state.query.isNotEmpty()) {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).clickable { viewModel.updateQuery("") },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Close, "Clear", tint = colors.inkSoft, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            when {
                state.query.isBlank() -> SearchLanding(
                    state = state,
                    showImages = settings.showImages,
                    onRecent = { viewModel.updateQuery(it) },
                    onClear = viewModel::clearHistory,
                    onWord = onWord,
                )
                state.searching && state.results.isEmpty() -> com.verbilume.app.ui.LoadingField(Modifier.padding(top = 60.dp))
                state.results.isEmpty() -> com.verbilume.app.ui.EmptyState(
                    title = "Nothing in this corner",
                    description = "No words match “${state.query}”. Try a shorter word or a different spelling.",
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item {
                        Text(
                            "${if (state.results.size == 1) "1 match" else "${formatNumber(state.results.size)} matches"} · tap to open",
                            fontFamily = ManropeFamily,
                            fontSize = 13.5.sp,
                            color = colors.inkFaint,
                            modifier = Modifier.padding(bottom = 10.dp, top = 4.dp),
                        )
                    }
                    items(state.results, key = { it.id }) { word ->
                        SearchRow(word, settings.showImages) { 
                            viewModel.submit()
                            onWord(word.id)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchLanding(
    state: SearchUiState,
    showImages: Boolean,
    onRecent: (String) -> Unit,
    onClear: () -> Unit,
    onWord: (String) -> Unit,
) {
    val colors = LocalVerbiColors.current
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 42.dp),
    ) {
        if (state.recents.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "RECENT",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        letterSpacing = 2.2.sp,
                        color = colors.inkFaint,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Clear",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = colors.accent,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClear).padding(6.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            items(state.recents, key = { it.usedAt }) { recent ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onRecent(recent.query) }
                        .padding(vertical = 11.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.History, null, tint = colors.inkFaint, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        recent.query,
                        fontFamily = ManropeFamily,
                        fontSize = 14.sp,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text("↗", fontSize = 14.sp, color = colors.inkFaint)
                }
            }
            item { Spacer(Modifier.height(22.dp)) }
        }

        item {
            Text(
                "A FEW LIGHTS",
                fontFamily = ManropeFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                letterSpacing = 2.2.sp,
                color = colors.inkFaint,
            )
            Spacer(Modifier.height(12.dp))
        }
        items(state.suggestions, key = { it.id }) { word ->
            SearchRow(word, showImages) { onWord(word.id) }
        }
        item { Spacer(Modifier.height(18.dp)) }
        item {
            Text(
                "Look up any of the 7,298 words in the atlas. Search reads meanings too — try “a feeling of calm”.",
                fontFamily = ManropeFamily,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                color = colors.inkFaint,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun SearchRow(word: WordSummary, showImages: Boolean, onClick: () -> Unit) {
    val colors = LocalVerbiColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showImages) {
            WordImage(
                word.image,
                Modifier.size(58.dp),
                corner = 15.dp,
                contentDescription = word.word,
            )
            Spacer(Modifier.width(13.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    word.word,
                    fontFamily = com.verbilume.app.ui.theme.FrauncesFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                LevelBadge(word.levelLabel)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                word.firstSense,
                fontFamily = ManropeFamily,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                color = colors.inkSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            word.pos,
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = colors.inkFaint,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
