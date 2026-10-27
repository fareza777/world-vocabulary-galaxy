package com.verbilume.app.ui.screens

import androidx.activity.compose.LocalActivity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.verbilume.app.data.AchievementDef
import com.verbilume.app.data.AppGraph
import com.verbilume.app.data.AppSettings
import com.verbilume.app.data.FsrsRating
import com.verbilume.app.data.MatchPair
import com.verbilume.app.data.PracticeMode
import com.verbilume.app.ui.EmptyState
import com.verbilume.app.ui.LoadingField
import com.verbilume.app.ui.PaperBackground
import com.verbilume.app.ui.PrimaryButton
import com.verbilume.app.ui.friendlyError
import com.verbilume.app.ui.graphViewModel
import com.verbilume.app.ui.theme.FrauncesFamily
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.theme.ManropeFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

data class MatchUiState(
    val loading: Boolean = true,
    val pairs: List<MatchPair> = emptyList(),
    val order: List<Int> = emptyList(),
    val words: List<MatchPair> = emptyList(),
    val solved: Set<String> = emptySet(),
    val currentOrderIndex: Int = 0,
    val mistakes: Int = 0,
    val lastMatch: String? = null,
    val wrongWord: String? = null,
    val finished: Boolean = false,
    val reviewed: Int = 0,
    val xp: Int = 0,
    val streak: Int = 0,
    val seconds: Int = 0,
    val achievements: List<AchievementDef> = emptyList(),
    val error: String? = null,
) {
    val currentPairIndex: Int? get() = order.getOrNull(currentOrderIndex)
    val currentPair: MatchPair? get() = currentPairIndex?.let { pairs.getOrNull(it) }
    val progress: Float get() = if (pairs.isEmpty()) 0f else solved.size.toFloat() / pairs.size
}

class MatchViewModel(
    private val graph: AppGraph,
    private val settings: AppSettings,
) : ViewModel() {
    private val _state = MutableStateFlow(MatchUiState())
    val state: StateFlow<MatchUiState> = _state.asStateFlow()
    private var startedAt = System.currentTimeMillis()
    private var questionAt = System.currentTimeMillis()
    private var logged = false
    private var busy = false
    private val pairMistakes = HashMap<String, Int>()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = MatchUiState(loading = true)
            logged = false
            startedAt = System.currentTimeMillis()
            pairMistakes.clear()
            runCatching { graph.sessions.buildMatch(settings, 7) }
                .onSuccess { pairs ->
                    val order = pairs.indices.shuffled(Random(System.currentTimeMillis()))
                    questionAt = System.currentTimeMillis()
                    _state.value = MatchUiState(
                        loading = false,
                        pairs = pairs,
                        order = order,
                        words = pairs.shuffled(Random(System.currentTimeMillis() xor 71L)),
                    )
                }
                .onFailure {
                    _state.value = MatchUiState(
                        loading = false,
                        error = friendlyError(it, "This round could not be prepared. Please try again."),
                    )
                }
        }
    }

    fun pick(wordId: String) {
        val state = _state.value
        val pair = state.currentPair ?: return
        if (busy || state.finished) return
        if (wordId == pair.word.id) {
            busy = true
            val mistakes = pairMistakes[pair.word.id] ?: 0
            val seconds = ((System.currentTimeMillis() - questionAt) / 1000).toInt().coerceIn(1, 120)
            _state.value = state.copy(
                solved = state.solved + pair.word.id,
                lastMatch = pair.word.word,
                wrongWord = null,
            )
            viewModelScope.launch {
                val outcome = runCatching {
                    graph.learning.review(
                        word = pair.word,
                        rating = if (mistakes == 0) FsrsRating.GOOD else FsrsRating.HARD,
                        correct = true,
                        seconds = seconds,
                        dailyGoal = settings.dailyGoal,
                        learnMode = true,
                    )
                }.getOrNull()
                delay(480)
                val current = _state.value
                val nextOrder = current.currentOrderIndex + 1
                val finished = nextOrder >= current.order.size
                _state.value = current.copy(
                    currentOrderIndex = nextOrder.coerceAtMost(current.order.size),
                    reviewed = current.reviewed + 1,
                    xp = current.xp + (outcome?.xpGained ?: 0),
                    streak = outcome?.streak ?: current.streak,
                    achievements = (current.achievements + (outcome?.newAchievements ?: emptyList())).distinctBy { it.id },
                    finished = finished,
                    lastMatch = null,
                )
                busy = false
                questionAt = System.currentTimeMillis()
                if (finished) finish()
            }
        } else {
            pairMistakes[pair.word.id] = (pairMistakes[pair.word.id] ?: 0) + 1
            _state.value = state.copy(
                mistakes = state.mistakes + 1,
                wrongWord = wordId,
            )
            viewModelScope.launch {
                delay(520)
                if (_state.value.wrongWord == wordId) _state.value = _state.value.copy(wrongWord = null)
            }
        }
    }

    private suspend fun finish() {
        if (logged) return
        logged = true
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(1)
        val state = _state.value
        val (_, achievements) = graph.learning.logSession(
            mode = PracticeMode.MATCH,
            startedAt = startedAt,
            reviewed = state.reviewed,
            correct = state.reviewed,
            seconds = seconds,
            bonusXp = 0,
        )
        _state.value = _state.value.copy(
            finished = true,
            seconds = seconds,
            achievements = (_state.value.achievements + achievements).distinctBy { it.id },
        )
    }

    fun abandon() {
        if (logged) return
        val state = _state.value
        if (state.reviewed == 0) return
        logged = true
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(1)
        graph.scope.launch {
            graph.learning.logSession(
                mode = PracticeMode.MATCH,
                startedAt = startedAt,
                reviewed = state.reviewed,
                correct = state.reviewed,
                seconds = seconds,
                bonusXp = 0,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MatchScreen(
    graph: AppGraph,
    settings: AppSettings,
    onClose: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val viewModel = graphViewModel { MatchViewModel(graph, settings) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    var elapsed by remember { mutableLongStateOf(0L) }
    val running = !state.loading && !state.finished

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        elapsed = 0L
        while (true) {
            delay(1000)
            elapsed++
        }
    }

    if (state.finished) {
        SessionSummary(
            eyebrow = "Word pairing",
            headline = if (state.mistakes == 0) "Not a single\nmiss." else "Paired, one\nby one.",
            message = "${state.reviewed} pairs matched with ${state.mistakes} ${if (state.mistakes == 1) "miss" else "misses"}. Matched words were scheduled as reviews.",
            reviewed = state.reviewed,
            correct = state.reviewed,
            xp = state.xp,
            seconds = state.seconds,
            streak = state.streak,
            achievements = state.achievements,
            celebrate = !settings.reduceMotion,
            onDone = { graph.ads.showInterstitial(activity) { onClose() } },
            onAgain = { viewModel.load() },
            againLabel = "New pairing",
        )
        return
    }

    PaperBackground {
        Column(Modifier.fillMaxSize()) {
            SessionTopBar(
                title = "Word pairing",
                progress = state.progress,
                counter = "${state.solved.size}/${state.pairs.size}",
                onClose = {
                    viewModel.abandon()
                    onClose()
                },
            )
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingField() }
                state.error != null || state.currentPair == null -> Column(Modifier.fillMaxSize().padding(20.dp)) {
                    EmptyState("No pairs on the table", state.error ?: "Not enough distinct words could be prepared. Try again.")
                    PrimaryButton("Back", onClick = onClose, modifier = Modifier.fillMaxWidth())
                }
                else -> {
                    val pair = state.currentPair!!
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                    ) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Timer, null, tint = colors.inkFaint, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    "${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}",
                                    fontFamily = ManropeFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = colors.inkSoft,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Bolt, null, tint = if (state.mistakes > 0) colors.accent else colors.inkFaint, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "${state.mistakes} misses",
                                    fontFamily = ManropeFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = if (state.mistakes > 0) colors.accent else colors.inkSoft,
                                )
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.pairs.indices.forEach { index ->
                                val solved = state.pairs[index].word.id in state.solved
                                val current = state.currentPairIndex == index
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            when {
                                                solved -> colors.teal
                                                current -> colors.brass
                                                else -> colors.line
                                            }
                                        )
                                )
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                        Text(
                            "WHICH WORD MEANS THIS?",
                            fontFamily = ManropeFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            letterSpacing = 2.sp,
                            color = colors.inkFaint,
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(26.dp))
                                .background(colors.ink)
                                .padding(22.dp),
                        ) {
                            Column {
                                Text(
                                    pair.definition,
                                    fontFamily = FrauncesFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 21.sp,
                                    lineHeight = 29.sp,
                                    color = colors.paper,
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "${state.solved.size + 1} of ${state.pairs.size} · tap the matching word below",
                                    fontFamily = ManropeFamily,
                                    fontSize = 12.5.sp,
                                    color = colors.paper.copy(alpha = 0.45f),
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = state.lastMatch != null,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            Row(
                                Modifier.padding(top = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(colors.teal)
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    "Matched “${state.lastMatch.orEmpty()}”",
                                    fontFamily = ManropeFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    color = colors.teal,
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "THE WORDS",
                            fontFamily = ManropeFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            letterSpacing = 2.sp,
                            color = colors.inkFaint,
                        )
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            state.words.forEach { option ->
                                val solved = option.word.id in state.solved
                                val wrong = state.wrongWord == option.word.id
                                val borderColor = when {
                                    solved -> colors.teal.copy(alpha = 0.25f)
                                    wrong -> colors.accent
                                    else -> colors.line
                                }
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            when {
                                                solved -> colors.teal.copy(alpha = 0.08f)
                                                wrong -> colors.accent.copy(alpha = 0.12f)
                                                else -> colors.raised
                                            }
                                        )
                                        .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                                        .clickable(enabled = !solved) { viewModel.pick(option.word.id) }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                ) {
                                    Text(
                                        option.word.word,
                                        fontFamily = ManropeFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = when {
                                            solved -> colors.inkFaint
                                            wrong -> colors.accent
                                            else -> colors.ink
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(30.dp))
                    }
                }
            }
        }
    }
}
