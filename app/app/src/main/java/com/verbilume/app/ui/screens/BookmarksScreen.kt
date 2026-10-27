package com.verbilume.app.ui.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.verbilume.app.data.AppGraph
import com.verbilume.app.data.BookmarkEntry
import com.verbilume.app.ui.EmptyState
import com.verbilume.app.ui.LoadingField
import com.verbilume.app.ui.PageTopBar
import com.verbilume.app.ui.PaperBackground
import com.verbilume.app.ui.PrimaryButton
import com.verbilume.app.ui.SecondaryButton
import com.verbilume.app.ui.WordTile
import com.verbilume.app.ui.graphViewModel
import com.verbilume.app.ui.theme.FrauncesFamily
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.theme.ManropeFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BookmarksUiState(
    val loading: Boolean = true,
    val entries: List<BookmarkEntry> = emptyList(),
)

class BookmarksViewModel(graph: AppGraph) : ViewModel() {
    private val _state = MutableStateFlow(BookmarksUiState())
    val state: StateFlow<BookmarksUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            graph.learning.dataVersion.collect {
                runCatching { graph.learning.bookmarks() }
                    .onSuccess { _state.value = BookmarksUiState(false, it) }
                    .onFailure { _state.value = _state.value.copy(loading = false) }
            }
        }
    }
}

@Composable
fun BookmarksScreen(
    graph: AppGraph,
    onBack: () -> Unit,
    onWord: (String) -> Unit,
    onFlashcards: () -> Unit,
    onAtlas: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val viewModel = graphViewModel { BookmarksViewModel(graph) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    PaperBackground {
        Column(Modifier.fillMaxSize()) {
            PageTopBar("Saved words", onBack = onBack)
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingField() }
                return@Column
            }
            if (state.entries.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    Column {
                        EmptyState(
                            "An empty press",
                            "Tap the ribbon on any word to keep it here. Saved words travel with you, even offline.",
                        )
                        PrimaryButton("Browse the atlas", onClick = onAtlas, modifier = Modifier.fillMaxWidth())
                    }
                }
                return@Column
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(2) }) {
                    Column {
                        Text(
                            "Words you kept\nclose.",
                            fontFamily = FrauncesFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 32.sp,
                            lineHeight = 36.sp,
                            letterSpacing = (-0.6).sp,
                            color = colors.ink,
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "${state.entries.size} saved ${if (state.entries.size == 1) "word" else "words"} · ready for a calm review",
                            fontFamily = ManropeFamily,
                            fontSize = 13.sp,
                            color = colors.inkSoft,
                        )
                        Spacer(Modifier.height(14.dp))
                        SecondaryButton(
                            text = "Study saved words",
                            onClick = onFlashcards,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
                items(state.entries, key = { it.word.id }) { entry ->
                    WordTile(entry.word, onClick = { onWord(entry.word.id) })
                }
            }
        }
    }
}
