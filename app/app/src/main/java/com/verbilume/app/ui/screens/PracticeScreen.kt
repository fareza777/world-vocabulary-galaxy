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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.verbilume.app.data.AppGraph
import com.verbilume.app.data.AppSettings
import com.verbilume.app.data.LearningStats
import com.verbilume.app.ui.LoadingField
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.PaperBackground
import com.verbilume.app.ui.PrimaryButton
import com.verbilume.app.ui.SectionHeading
import com.verbilume.app.ui.VerbiMark
import com.verbilume.app.ui.formatNumber
import com.verbilume.app.ui.graphViewModel
import com.verbilume.app.ui.plural
import com.verbilume.app.ui.theme.FrauncesFamily
import com.verbilume.app.ui.theme.ManropeFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PracticeUiState(
    val loading: Boolean = true,
    val stats: LearningStats? = null,
    val challengeDone: Boolean = false,
    val challengeScore: Int = 0,
    val bestWpm: Int = 0,
)

class PracticeViewModel(private val graph: AppGraph) : ViewModel() {
    private val _state = MutableStateFlow(PracticeUiState())
    val state: StateFlow<PracticeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(graph.settings.settings, graph.learning.dataVersion) { settings, _ -> settings }
                .collect { load(it) }
        }
    }

    private suspend fun load(settings: AppSettings) {
        runCatching {
            val stats = graph.learning.stats(settings)
            val challenge = graph.learning.challengeToday()
            val typing = graph.learning.typingStats()
            PracticeUiState(false, stats, challenge.first, challenge.second, typing.bestWpm)
        }.onSuccess { _state.value = it }
            .onFailure { _state.value = _state.value.copy(loading = false) }
    }
}

private data class PracticeEntry(
    val title: String,
    val caption: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit,
)

@Composable
fun PracticeScreen(
    graph: AppGraph,
    settings: AppSettings,
    onLearn: () -> Unit,
    onDaily: () -> Unit,
    onFlashcards: () -> Unit,
    onQuiz: (String) -> Unit,
    onMatch: () -> Unit,
    onTyping: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val viewModel = graphViewModel { PracticeViewModel(graph) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats = state.stats
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    PaperBackground {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 128.dp + navBarBottom),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            item(span = { GridItemSpan(2) }) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VerbiMark(Modifier.size(26.dp), color = colors.ink, accent = colors.accent)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "PRACTICE",
                            fontFamily = ManropeFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 3.4.sp,
                            color = colors.ink,
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(
                        "Make it stay.",
                        fontFamily = FrauncesFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 34.sp,
                        color = colors.ink,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (stats == null) "A few minutes today keeps the words bright."
                        else if (stats.dueNow > 0) "${plural(stats.dueNow, "word")} ready for review. ${plural(stats.newAvailable, "new light")} waiting."
                        else "You are clear for now — try a new word or a quick game.",
                        fontFamily = ManropeFamily,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = colors.inkSoft,
                        textAlign = TextAlign.Justify,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item(span = { GridItemSpan(2) }) {
                DailyCard(
                    done = state.challengeDone,
                    score = state.challengeScore,
                    onClick = onDaily,
                )
            }

            item(span = { GridItemSpan(2) }) {
                GuidedCard(
                    due = stats?.dueNow ?: 0,
                    fresh = stats?.newAvailable ?: 0,
                    loading = state.loading,
                    onClick = onLearn,
                )
            }

            item(span = { GridItemSpan(2) }) {
                Column(Modifier.padding(top = 12.dp, bottom = 2.dp)) {
                    SectionHeading("Choose a way in", eyebrow = "EIGHT PRACTICE ROOMS")
                }
            }

            val entries = listOf(
                PracticeEntry("Flashcards", "Calm, self-paced study", Icons.Rounded.CollectionsBookmark, colors.teal) { onFlashcards() },
                PracticeEntry("Meaning match", "Match each word to its meaning", Icons.Rounded.Translate, colors.brassBright) { onQuiz("meaning") },
                PracticeEntry("Picture finder", "Name what you see", Icons.Rounded.Image, colors.accent) { onQuiz("picture") },
                PracticeEntry("Listen & choose", "Train your ear", Icons.Rounded.Headphones, colors.teal) { onQuiz("listen") },
                PracticeEntry("Ear spelling", "Hear it, then write it", Icons.Rounded.Edit, colors.brass) { onQuiz("spelling") },
                PracticeEntry("Active recall", "Write from the meaning", Icons.Rounded.Psychology, colors.accent) { onQuiz("recall") },
                PracticeEntry("Word pairing", "Beat the clock", Icons.Rounded.Link, colors.teal) { onMatch() },
                PracticeEntry("Typing studio", if (state.bestWpm > 0) "Best ${state.bestWpm} WPM" else "Words, sentences, dictation", Icons.Rounded.Keyboard, colors.inkSoft) { onTyping() },
            )
            entries.forEach { entry ->
                item {
                    ModeCard(entry)
                }
            }

            item(span = { GridItemSpan(2) }) {
                Text(
                    "Reviews are scheduled by FSRS-6, tuned to your recall. ${formatNumber(stats?.totalReviews ?: 0)} reviews so far.",
                    fontFamily = ManropeFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = colors.inkFaint,
                    textAlign = TextAlign.Justify,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DailyCard(done: Boolean, score: Int, onClick: () -> Unit) {
    val colors = LocalVerbiColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF12283A), Color(0xFF091520))))
            .clickable(onClick = onClick)
            .padding(22.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WorkspacePremium, null, tint = colors.brassBright, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "DAILY CHALLENGE",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    letterSpacing = 2.2.sp,
                    color = colors.brassBright,
                )
                Spacer(Modifier.weight(1f))
                if (done) {
                    Text(
                        "COMPLETE",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.4.sp,
                        color = Color(0xFF7CC8BC),
                    )
                } else {
                    Text(
                        "12 QUESTIONS",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.2.sp,
                        color = Color(0xFFF2E8D7).copy(alpha = 0.38f),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (done) "Tomorrow brings a new sky." else "A dozen small proofs\nof progress.",
                fontFamily = FrauncesFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 25.sp,
                lineHeight = 29.sp,
                color = Color(0xFFF2E8D7),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (done) {
                    "You scored $score/12 today. Come back after midnight for a fresh set."
                } else {
                    "Mixed meanings, pictures, sounds and spelling. One attempt per day, with a completion bonus."
                },
                fontFamily = ManropeFamily,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = Color(0xFFF2E8D7).copy(alpha = 0.55f),
                textAlign = TextAlign.Justify,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (done) "View today's result" else "Take today's challenge",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFFF2E8D7),
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.ArrowForward, null, tint = colors.brassBright, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun GuidedCard(due: Int, fresh: Int, loading: Boolean, onClick: () -> Unit) {
    val colors = LocalVerbiColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(colors.raised)
            .border(1.dp, colors.line, RoundedCornerShape(26.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(colors.teal.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = colors.teal, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Guided review",
                fontFamily = FrauncesFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                color = colors.ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (loading) "Counting your queue…" else "$due due · $fresh new available",
                fontFamily = ManropeFamily,
                fontSize = 13.sp,
                color = colors.inkSoft,
            )
        }
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(colors.ink)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ArrowForward, "Start", tint = colors.paper, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun ModeCard(entry: PracticeEntry) {
    val colors = LocalVerbiColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.raised)
            .border(1.dp, colors.line.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
            .clickable(onClick = entry.onClick)
            .padding(16.dp),
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(entry.tint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(entry.icon, null, tint = entry.tint, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(
            entry.title,
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = colors.ink,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            entry.caption,
            fontFamily = ManropeFamily,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            color = colors.inkFaint,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
