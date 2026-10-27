package com.verbilume.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.verbilume.app.data.AchievementDef
import com.verbilume.app.data.AppGraph
import com.verbilume.app.data.AppSettings
import com.verbilume.app.data.CardWithWord
import com.verbilume.app.data.Fsrs
import com.verbilume.app.data.FsrsCard
import com.verbilume.app.data.FsrsRating
import com.verbilume.app.data.FsrsState
import com.verbilume.app.data.PracticeMode
import com.verbilume.app.data.WordDetail
import com.verbilume.app.data.WordSummary
import com.verbilume.app.data.primaryAudio
import com.verbilume.app.ui.EmptyState
import com.verbilume.app.ui.LevelBadge
import com.verbilume.app.ui.LoadingField
import com.verbilume.app.ui.PaperBackground
import com.verbilume.app.ui.PrimaryButton
import com.verbilume.app.ui.SpeakerButton
import com.verbilume.app.ui.WordImage
import com.verbilume.app.ui.friendlyError
import com.verbilume.app.ui.graphViewModel
import com.verbilume.app.ui.headlineSize
import com.verbilume.app.ui.theme.FrauncesFamily
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.theme.ManropeFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LearnItem(
    val card: FsrsCard,
    val word: WordSummary,
    val detail: WordDetail,
    val topicLabel: String,
)

data class LearnUiState(
    val loading: Boolean = true,
    val items: List<LearnItem> = emptyList(),
    val index: Int = 0,
    val revealed: Boolean = false,
    val busy: Boolean = false,
    val finished: Boolean = false,
    val reviewed: Int = 0,
    val correct: Int = 0,
    val xp: Int = 0,
    val seconds: Int = 0,
    val streak: Int = 0,
    val achievements: List<AchievementDef> = emptyList(),
    val error: String? = null,
) {
    val current: LearnItem? get() = items.getOrNull(index)
    val progress: Float get() = if (items.isEmpty()) 0f else index.toFloat() / items.size
}

class LearnViewModel(
    private val graph: AppGraph,
    private val settings: AppSettings,
) : ViewModel() {
    private val _state = MutableStateFlow(LearnUiState())
    val state: StateFlow<LearnUiState> = _state.asStateFlow()
    private var cardShownAt = System.currentTimeMillis()
    private var startedAt = System.currentTimeMillis()
    private var requeues = HashMap<String, Int>()
    private var logged = false

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = LearnUiState(loading = true)
            logged = false
            requeues = HashMap()
            startedAt = System.currentTimeMillis()
            runCatching {
                val queue = graph.sessions.buildLearn(settings, 20)
                val meta = graph.content.meta
                queue.mapNotNull { item ->
                    val detail = graph.sessions.detail(item.word) ?: return@mapNotNull null
                    LearnItem(
                        card = item.card,
                        word = item.word,
                        detail = detail,
                        topicLabel = meta.topics.getOrNull(item.word.topic)?.label ?: "Atlas",
                    )
                }
            }.onSuccess { items ->
                cardShownAt = System.currentTimeMillis()
                _state.value = LearnUiState(loading = false, items = items)
            }.onFailure {
                _state.value = LearnUiState(
                    loading = false,
                    error = friendlyError(it, "The review could not be prepared. Please try again."),
                )
            }
        }
    }

    fun reveal() {
        _state.value = _state.value.copy(revealed = true)
    }

    fun rate(rating: FsrsRating) {
        val current = _state.value
        val item = current.current ?: return
        if (current.busy) return
        _state.value = current.copy(busy = true)
        viewModelScope.launch {
            runCatching {
                val seconds = ((System.currentTimeMillis() - cardShownAt) / 1000).toInt().coerceIn(1, 120)
                val outcome = graph.learning.review(
                    word = item.word,
                    rating = rating,
                    correct = rating != FsrsRating.AGAIN,
                    seconds = seconds,
                    dailyGoal = settings.dailyGoal,
                    learnMode = true,
                )
                outcome
            }.onSuccess { outcome ->
                val previous = _state.value
                var items = previous.items
                val attempts = requeues[item.word.id] ?: 0
                if (rating == FsrsRating.AGAIN && attempts < 2) {
                    requeues[item.word.id] = attempts + 1
                    items = items + item.copy(card = outcome.card)
                } else if (previous.index < items.lastIndex) {
                    items = items.toMutableList().also {
                        it[previous.index] = item.copy(card = outcome.card)
                    }
                }
                val nextIndex = previous.index + 1
                val finished = nextIndex >= items.size
                val elapsed = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(1)
                _state.value = previous.copy(
                    items = items,
                    index = nextIndex.coerceAtMost(items.size),
                    revealed = false,
                    busy = false,
                    finished = finished,
                    reviewed = previous.reviewed + 1,
                    correct = previous.correct + if (rating != FsrsRating.AGAIN) 1 else 0,
                    xp = previous.xp + outcome.xpGained,
                    seconds = if (finished) elapsed else previous.seconds,
                    streak = outcome.streak,
                    achievements = (previous.achievements + outcome.newAchievements).distinctBy { it.id },
                )
                cardShownAt = System.currentTimeMillis()
                if (finished) finish()
            }.onFailure {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    private suspend fun finish() {
        if (logged) return
        logged = true
        val current = _state.value
        val seconds = current.seconds.takeIf { it > 0 }
            ?: ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(1)
        val (_, newAchievements) = graph.learning.logSession(
            mode = PracticeMode.LEARN,
            startedAt = startedAt,
            reviewed = current.reviewed,
            correct = current.correct,
            seconds = seconds,
            bonusXp = 0,
        )
        _state.value = _state.value.copy(
            achievements = (_state.value.achievements + newAchievements).distinctBy { it.id },
            seconds = seconds,
            finished = true,
        )
    }

    fun abandon() {
        val current = _state.value
        if (logged || current.reviewed == 0) return
        logged = true
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(1)
        graph.scope.launch {
            graph.learning.logSession(
                mode = PracticeMode.LEARN,
                startedAt = startedAt,
                reviewed = current.reviewed,
                correct = current.correct,
                seconds = seconds,
                bonusXp = 0,
            )
        }
    }
}

private fun shortInterval(card: FsrsCard, now: Long): String {
    val ms = (card.due - now).coerceAtLeast(0)
    return when {
        card.state == FsrsState.NEW -> "now"
        ms < 60_000 -> "<1m"
        ms < 3_600_000 -> "${(ms / 60_000).coerceAtLeast(1)}m"
        ms < 86_400_000 -> "${(ms / 3_600_000).coerceAtLeast(1)}h"
        card.scheduledDays > 0 -> "${card.scheduledDays}d"
        else -> "${(ms / 86_400_000).coerceAtLeast(1)}d"
    }
}

@Composable
fun LearnScreen(
    graph: AppGraph,
    settings: AppSettings,
    onClose: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val viewModel = graphViewModel { LearnViewModel(graph, settings) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playing by graph.audio.playing.collectAsStateWithLifecycle()
    val item = state.current
    val audio = item?.detail?.primaryAudio(settings.accent)
    val now = System.currentTimeMillis()
    val previews = item?.let { Fsrs.preview(it.card, now) } ?: emptyMap()

    LaunchedEffect(item?.word?.id) {
        if (item != null && settings.autoplayAudio && settings.soundEnabled) {
            audio?.let { graph.audio.play(it) }
        }
    }

    if (state.finished) {
        SessionSummary(
            eyebrow = "Guided review",
            headline = "The sky holds\na little more.",
            message = if (state.reviewed == 1) "One word revisited, one memory strengthened." else "${state.reviewed} words revisited with FSRS scheduling.",
            reviewed = state.reviewed,
            correct = state.correct,
            xp = state.xp,
            seconds = state.seconds,
            streak = state.streak,
            achievements = state.achievements,
            celebrate = !settings.reduceMotion,
            onDone = {
                viewModel.abandon()
                onClose()
            },
            onAgain = { viewModel.load() },
            againLabel = "Keep reviewing",
        )
        return
    }

    PaperBackground {
        Column(Modifier.fillMaxSize()) {
            SessionTopBar(
                title = "Guided review",
                progress = state.progress,
                counter = if (state.items.isEmpty()) "—" else "${(state.index + 1).coerceAtMost(state.items.size)}/${state.items.size}",
                onClose = {
                    viewModel.abandon()
                    onClose()
                },
            )

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingField() }
                state.error != null -> Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState("The session could not open", state.error ?: "Please try again.")
                    PrimaryButton("Back", onClick = onClose, modifier = Modifier.fillMaxWidth())
                }
                item == null -> Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(40.dp))
                    EmptyState(
                        "Nothing due right now",
                        "You are caught up. New words will appear here when your daily queue refreshes, or explore the atlas for something new.",
                    )
                    PrimaryButton("Back to practice", onClick = onClose, modifier = Modifier.fillMaxWidth())
                }
                else -> {
                    AnimatedContent(
                        targetState = item,
                        transitionSpec = {
                            (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(200)) { -it / 4 } + fadeOut(tween(160)))
                        },
                        label = "learnCard",
                        modifier = Modifier.weight(1f),
                    ) { active ->
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp),
                        ) {
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(268.dp)
                                    .clip(RoundedCornerShape(30.dp)),
                            ) {
                                WordImage(active.detail.image, Modifier.fillMaxSize(), corner = 30.dp)
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                0.55f to Color.Transparent,
                                                1f to colors.ink.copy(alpha = 0.5f),
                                            )
                                        )
                                )
                                Row(
                                    Modifier.align(Alignment.BottomStart).padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    LevelBadge(active.word.levelLabel)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        active.topicLabel,
                                        fontFamily = ManropeFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp,
                                        color = Color(0xFFFBF6EC).copy(alpha = 0.8f),
                                    )
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    active.word.word,
                                    fontFamily = FrauncesFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = headlineSize(active.word.word, 40),
                                    letterSpacing = (-0.8).sp,
                                    color = colors.ink,
                                    modifier = Modifier.weight(1f),
                                )
                                if (audio != null) {
                                    SpeakerButton(
                                        onClick = {
                                            if (playing == audio) graph.audio.stop() else graph.audio.play(audio)
                                        },
                                        playing = playing == audio,
                                        size = 46.dp,
                                    )
                                }
                            }
                            val ipa = if (settings.accent == com.verbilume.app.data.Accent.UK) {
                                active.word.ipaUk ?: active.word.ipaUs
                            } else {
                                active.word.ipaUs ?: active.word.ipaUk
                            }
                            if (settings.showIpa && !ipa.isNullOrBlank()) {
                                Text(
                                    ipa,
                                    fontFamily = ManropeFamily,
                                    fontSize = 14.sp,
                                    color = colors.inkFaint,
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            AnimatedVisibility(visible = state.revealed, enter = fadeIn(tween(260))) {
                                Column {
                                    Text(
                                        active.detail.senses.firstOrNull()?.definition ?: active.word.firstSense,
                                        fontFamily = ManropeFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 16.5.sp,
                                        lineHeight = 26.sp,
                                        color = colors.ink,
                                    )
                                    active.detail.examples.firstOrNull()?.let { example ->
                                        Spacer(Modifier.height(14.dp))
                                        Text(
                                            "“${example.text}”",
                                            fontFamily = FrauncesFamily,
                                            fontStyle = FontStyle.Italic,
                                            fontSize = 16.sp,
                                            lineHeight = 23.sp,
                                            color = colors.inkSoft,
                                        )
                                    }
                                    if (active.detail.collocations.isNotEmpty()) {
                                        Spacer(Modifier.height(14.dp))
                                        Text(
                                            active.detail.collocations.take(3).joinToString(" · "),
                                            fontFamily = ManropeFamily,
                                            fontSize = 13.sp,
                                            color = colors.brass,
                                        )
                                    }
                                    Spacer(Modifier.height(24.dp))
                                }
                            }
                            if (!state.revealed) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(colors.sunken.copy(alpha = 0.55f))
                                        .clickable(onClick = viewModel::reveal)
                                        .padding(vertical = 18.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "Reveal the meaning",
                                        fontFamily = ManropeFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = colors.inkSoft,
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = state.revealed,
                        enter = fadeIn(tween(260)) + androidx.compose.animation.slideInVertically(tween(260)) { it / 3 },
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(colors.raised)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                FsrsRating.entries.forEach { rating ->
                                    val preview = previews[rating]?.let { shortInterval(it, now) } ?: "—"
                                    RatingButton(
                                        rating = rating,
                                        interval = preview,
                                        enabled = !state.busy,
                                        modifier = Modifier.weight(1f),
                                        onClick = { viewModel.rate(rating) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.VolumeUp, null, tint = colors.inkFaint, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    "Choose how well the word came back to you.",
                                    fontFamily = ManropeFamily,
                                    fontSize = 12.5.sp,
                                    color = colors.inkFaint,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingButton(
    rating: FsrsRating,
    interval: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val (label, tint) = when (rating) {
        FsrsRating.AGAIN -> "Again" to colors.accent
        FsrsRating.HARD -> "Hard" to colors.brass
        FsrsRating.GOOD -> "Good" to colors.teal
        FsrsRating.EASY -> "Easy" to colors.ink
    }
    Column(
        modifier
            .clip(RoundedCornerShape(15.dp))
            .background(tint.copy(alpha = if (colors.isDark) 0.2f else 0.1f))
            .border(1.dp, tint.copy(alpha = 0.24f), RoundedCornerShape(15.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            color = tint,
        )
        Text(
            interval,
            fontFamily = ManropeFamily,
            fontSize = 12.sp,
            color = colors.inkFaint,
        )
    }
}
