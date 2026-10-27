package com.verbilume.app.ui.screens

import androidx.activity.compose.LocalActivity

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
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
import com.verbilume.app.data.FlashcardItem
import com.verbilume.app.data.FsrsRating
import com.verbilume.app.data.PracticeMode
import com.verbilume.app.data.primaryAudio
import com.verbilume.app.ui.EmptyState
import com.verbilume.app.ui.LevelBadge
import com.verbilume.app.ui.LoadingField
import com.verbilume.app.ui.PaperBackground
import com.verbilume.app.ui.PrimaryButton
import com.verbilume.app.ui.SecondaryButton
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

data class FlashcardUiState(
    val loading: Boolean = true,
    val cards: List<FlashcardItem> = emptyList(),
    val index: Int = 0,
    val flipped: Boolean = false,
    val finished: Boolean = false,
    val reviewed: Int = 0,
    val correct: Int = 0,
    val xp: Int = 0,
    val streak: Int = 0,
    val seconds: Int = 0,
    val achievements: List<AchievementDef> = emptyList(),
    val error: String? = null,
) {
    val current: FlashcardItem? get() = cards.getOrNull(index)
    val progress: Float get() = if (cards.isEmpty()) 0f else index.toFloat() / cards.size
}

class FlashcardViewModel(
    private val graph: AppGraph,
    private val settings: AppSettings,
) : ViewModel() {
    private val _state = MutableStateFlow(FlashcardUiState())
    val state: StateFlow<FlashcardUiState> = _state.asStateFlow()
    private var startedAt = System.currentTimeMillis()
    private var cardAt = System.currentTimeMillis()
    private var logged = false
    private var busy = false

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = FlashcardUiState(loading = true)
            logged = false
            startedAt = System.currentTimeMillis()
            runCatching { graph.sessions.buildFlashcards(settings, 24) }
                .onSuccess { cards ->
                    cardAt = System.currentTimeMillis()
                    _state.value = FlashcardUiState(loading = false, cards = cards)
                }
                .onFailure {
                    _state.value = FlashcardUiState(
                        loading = false,
                        error = friendlyError(it, "This deck could not be prepared. Please try again."),
                    )
                }
        }
    }

    fun flip() {
        if (busy) return
        _state.value = _state.value.copy(flipped = !_state.value.flipped)
    }

    fun grade(knewIt: Boolean) {
        val state = _state.value
        val card = state.current ?: return
        if (!state.flipped || busy) return
        busy = true
        viewModelScope.launch {
            val seconds = ((System.currentTimeMillis() - cardAt) / 1000).toInt().coerceIn(1, 120)
            val outcome = runCatching {
                graph.learning.review(
                    word = card.word,
                    rating = if (knewIt) FsrsRating.GOOD else FsrsRating.AGAIN,
                    correct = knewIt,
                    seconds = seconds,
                    dailyGoal = settings.dailyGoal,
                    learnMode = true,
                )
            }.getOrNull()
            val next = _state.value.index + 1
            val finished = next >= _state.value.cards.size
            _state.value = _state.value.copy(
                index = next.coerceAtMost(_state.value.cards.size),
                flipped = false,
                finished = finished,
                reviewed = _state.value.reviewed + 1,
                correct = _state.value.correct + if (knewIt) 1 else 0,
                xp = _state.value.xp + (outcome?.xpGained ?: 0),
                streak = outcome?.streak ?: _state.value.streak,
                achievements = (_state.value.achievements + (outcome?.newAchievements ?: emptyList())).distinctBy { it.id },
            )
            cardAt = System.currentTimeMillis()
            busy = false
            if (finished) finish()
        }
    }

    private suspend fun finish() {
        if (logged) return
        logged = true
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(1)
        val state = _state.value
        val (_, achievements) = graph.learning.logSession(
            mode = PracticeMode.FLASHCARD,
            startedAt = startedAt,
            reviewed = state.reviewed,
            correct = state.correct,
            seconds = seconds,
            bonusXp = 0,
        )
        _state.value = _state.value.copy(
            seconds = seconds,
            finished = true,
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
                mode = PracticeMode.FLASHCARD,
                startedAt = startedAt,
                reviewed = state.reviewed,
                correct = state.correct,
                seconds = seconds,
                bonusXp = 0,
            )
        }
    }
}

@Composable
fun FlashcardScreen(
    graph: AppGraph,
    settings: AppSettings,
    onClose: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val viewModel = graphViewModel { FlashcardViewModel(graph, settings) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val activity = LocalActivity.current
    val card = state.current
    val audio = card?.detail?.primaryAudio(settings.accent)
    val playing by graph.audio.playing.collectAsStateWithLifecycle()
    val rotation by animateFloatAsState(
        targetValue = if (state.flipped) 180f else 0f,
        animationSpec = tween(420),
        label = "cardFlip",
    )

    LaunchedEffect(card?.word?.id) {
        if (card != null && settings.autoplayAudio && settings.soundEnabled) {
            audio?.let { graph.audio.play(it) }
        }
    }

    if (state.finished) {
        SessionSummary(
            eyebrow = "Free study",
            headline = "A calmer deck,\nwell met.",
            message = if (state.cards.isEmpty()) "No cards were in this deck." else "${state.reviewed} cards turned. Each one nudged the scheduler forward.",
            reviewed = state.reviewed,
            correct = state.correct,
            xp = state.xp,
            seconds = state.seconds,
            streak = state.streak,
            achievements = state.achievements,
            celebrate = !settings.reduceMotion,
            onDone = { graph.ads.showInterstitial(activity) { onClose() } },
            onAgain = { viewModel.load() },
            againLabel = "Shuffle a new deck",
        )
        return
    }

    PaperBackground {
        Column(Modifier.fillMaxSize()) {
            SessionTopBar(
                title = "Flashcards",
                progress = state.progress,
                counter = if (state.cards.isEmpty()) "—" else "${(state.index + 1).coerceAtMost(state.cards.size)}/${state.cards.size}",
                onClose = {
                    viewModel.abandon()
                    onClose()
                },
            )
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingField() }
                state.error != null -> Column(Modifier.fillMaxSize().padding(20.dp)) {
                    EmptyState("The deck is empty", state.error ?: "Try again later.")
                    PrimaryButton("Back", onClick = onClose, modifier = Modifier.fillMaxWidth())
                }
                card == null -> Column(Modifier.fillMaxSize().padding(20.dp)) {
                    EmptyState("A quiet deck", "No words available for flashcards right now. Add a few bookmarks or start a review to fill the deck.")
                    PrimaryButton("Back", onClick = onClose, modifier = Modifier.fillMaxWidth())
                }
                else -> {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 22.dp),
                    ) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Tap the card to turn it over.",
                            fontFamily = ManropeFamily,
                            fontSize = 13.sp,
                            color = colors.inkFaint,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(18.dp))
                        Box(contentAlignment = Alignment.TopCenter) {
                            Box(
                                Modifier
                                    .padding(top = 14.dp, start = 12.dp, end = 12.dp)
                                    .fillMaxWidth()
                                    .height(430.dp)
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(colors.sunken.copy(alpha = 0.55f))
                            )
                            Box(
                                Modifier
                                    .padding(top = 7.dp, start = 6.dp, end = 6.dp)
                                    .fillMaxWidth()
                                    .height(430.dp)
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(colors.sunken.copy(alpha = 0.8f))
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(430.dp)
                                    .graphicsLayer {
                                        rotationY = rotation
                                        cameraDistance = 14f * density.density
                                    }
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(if (rotation <= 90f) colors.raised else colors.ink)
                                    .border(1.dp, colors.line, RoundedCornerShape(32.dp))
                                    .clickable(onClick = viewModel::flip),
                            ) {
                                if (rotation <= 90f) {
                                    Column(Modifier.fillMaxSize()) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(230.dp),
                                        ) {
                                            WordImage(card.detail.image, Modifier.fillMaxSize(), corner = 0.dp)
                                            Box(
                                                Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.verticalGradient(
                                                            0f to Color.Transparent,
                                                            1f to colors.raised.copy(alpha = 0.85f),
                                                        )
                                                    )
                                            )
                                            LevelBadge(card.word.levelLabel, Modifier.padding(16.dp).align(Alignment.TopStart))
                                        }
                                        Column(
                                            Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(
                                                card.word.word,
                                                fontFamily = FrauncesFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = headlineSize(card.word.word, 40),
                                                lineHeight = 44.sp,
                                                color = colors.ink,
                                                textAlign = TextAlign.Center,
                                            )
                                            if (settings.showIpa) {
                                                val ipa = if (settings.accent == com.verbilume.app.data.Accent.UK) {
                                                    card.word.ipaUk ?: card.word.ipaUs
                                                } else {
                                                    card.word.ipaUs ?: card.word.ipaUk
                                                }
                                                if (!ipa.isNullOrBlank()) {
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(ipa, fontFamily = ManropeFamily, fontSize = 13.5.sp, color = colors.inkFaint)
                                                }
                                            }
                                            Spacer(Modifier.height(14.dp))
                                            Text(
                                                "Tap to reveal",
                                                fontFamily = ManropeFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                letterSpacing = 1.2.sp,
                                                color = colors.inkFaint,
                                            )
                                        }
                                    }
                                } else {
                                    Column(
                                        Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { rotationY = 180f }
                                            .padding(24.dp),
                                    ) {
                                        Text(
                                            "MEANING",
                                            fontFamily = ManropeFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            letterSpacing = 2.2.sp,
                                            color = colors.brassBright,
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            card.detail.senses.firstOrNull()?.definition ?: card.word.firstSense,
                                            fontFamily = FrauncesFamily,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 23.sp,
                                            lineHeight = 31.sp,
                                            color = colors.paper,
                                        )
                                        card.detail.examples.firstOrNull()?.let { example ->
                                            Spacer(Modifier.height(16.dp))
                                            Text(
                                                "“${example.text}”",
                                                fontFamily = FrauncesFamily,
                                                fontStyle = FontStyle.Italic,
                                                fontSize = 15.sp,
                                                lineHeight = 22.sp,
                                                color = colors.paper.copy(alpha = 0.66f),
                                            )
                                        }
                                        Spacer(Modifier.weight(1f))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                card.word.word,
                                                fontFamily = ManropeFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = colors.paper.copy(alpha = 0.9f),
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            if (audio != null) {
                                                SpeakerButton(
                                                    onClick = {
                                                        if (playing == audio) graph.audio.stop() else graph.audio.play(audio)
                                                    },
                                                    playing = playing == audio,
                                                    size = 36.dp,
                                                    dark = true,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 22.dp, vertical = 14.dp),
                    ) {
                        if (!state.flipped) {
                            PrimaryButton(
                                text = "Reveal the card",
                                onClick = viewModel::flip,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SecondaryButton(
                                    text = "Still learning",
                                    onClick = { viewModel.grade(false) },
                                    modifier = Modifier.weight(1f),
                                )
                                PrimaryButton(
                                    text = "I knew it",
                                    onClick = { viewModel.grade(true) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}
