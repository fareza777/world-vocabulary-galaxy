package com.verbilume.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.verbilume.app.data.AchievementDef
import com.verbilume.app.data.AppGraph
import com.verbilume.app.data.AppSettings
import com.verbilume.app.data.FsrsRating
import com.verbilume.app.data.PracticeMode
import com.verbilume.app.data.QuizKind
import com.verbilume.app.data.QuizOption
import com.verbilume.app.data.QuizQuestion
import com.verbilume.app.data.primaryAudio
import com.verbilume.app.ui.EmptyState
import com.verbilume.app.ui.LoadingField
import com.verbilume.app.ui.PaperBackground
import com.verbilume.app.ui.PrimaryButton
import com.verbilume.app.ui.SpeakerButton
import com.verbilume.app.ui.WordImage
import com.verbilume.app.ui.answerMatches
import com.verbilume.app.ui.friendlyError
import com.verbilume.app.ui.graphViewModel
import com.verbilume.app.ui.normalizeTypedAnswer
import com.verbilume.app.ui.theme.FrauncesFamily
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.theme.ManropeFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Feedback { CORRECT, WRONG }

data class QuizUiState(
    val loading: Boolean = true,
    val mode: PracticeMode = PracticeMode.MEANING,
    val questions: List<QuizQuestion> = emptyList(),
    val index: Int = 0,
    val input: String = "",
    val feedback: Feedback? = null,
    val selectedId: String? = null,
    val finished: Boolean = false,
    val correct: Int = 0,
    val answered: Int = 0,
    val xp: Int = 0,
    val streak: Int = 0,
    val seconds: Int = 0,
    val bonusXp: Int = 0,
    val achievements: List<AchievementDef> = emptyList(),
    val alreadyDone: Boolean = false,
    val previousScore: Int = 0,
    val error: String? = null,
) {
    val current: QuizQuestion? get() = questions.getOrNull(index)
    val progress: Float get() = if (questions.isEmpty()) 0f else index.toFloat() / questions.size
}

class QuizViewModel(
    private val graph: AppGraph,
    private val settings: AppSettings,
    private val mode: PracticeMode,
) : ViewModel() {
    private val _state = MutableStateFlow(QuizUiState(mode = mode))
    val state: StateFlow<QuizUiState> = _state.asStateFlow()
    private var startedAt = System.currentTimeMillis()
    private var questionAt = System.currentTimeMillis()
    private var logged = false
    private var busy = false

    init { start() }

    fun start() {
        viewModelScope.launch {
            _state.value = QuizUiState(mode = mode, loading = true)
            logged = false
            startedAt = System.currentTimeMillis()
            if (mode == PracticeMode.DAILY) {
                val (done, score) = withContext(kotlinx.coroutines.Dispatchers.IO) { graph.learning.challengeToday() }
                if (done) {
                    _state.value = QuizUiState(loading = false, mode = mode, finished = true, alreadyDone = true, previousScore = score)
                    return@launch
                }
            }
            runCatching {
                if (mode == PracticeMode.DAILY) graph.sessions.buildDaily(settings, 12)
                else graph.sessions.buildQuiz(settings, mode, 10)
            }.onSuccess { questions ->
                questionAt = System.currentTimeMillis()
                _state.value = QuizUiState(loading = false, mode = mode, questions = questions)
            }.onFailure {
                _state.value = QuizUiState(
                    loading = false,
                    mode = mode,
                    error = friendlyError(it, "This set of questions could not be prepared. Please try again."),
                )
            }
        }
    }

    fun updateInput(value: String) {
        if (_state.value.feedback != null) return
        _state.value = _state.value.copy(input = value)
    }

    fun choose(option: QuizOption) {
        val state = _state.value
        if (state.feedback != null || busy) return
        busy = true
        _state.value = state.copy(selectedId = option.wordId.ifBlank { option.text })
        submit(option.correct)
    }

    fun submitText() {
        val state = _state.value
        val question = state.current ?: return
        if (state.feedback != null || state.input.isBlank() || busy) return
        busy = true
        submit(answerMatches(state.input, question.answer))
    }

    private fun submit(correct: Boolean) {
        val state = _state.value
        val question = state.current ?: return
        viewModelScope.launch {
            val seconds = ((System.currentTimeMillis() - questionAt) / 1000).toInt().coerceIn(1, 300)
            val outcome = runCatching {
                graph.learning.review(
                    word = question.word,
                    rating = if (correct) FsrsRating.GOOD else FsrsRating.AGAIN,
                    correct = correct,
                    seconds = seconds,
                    dailyGoal = settings.dailyGoal,
                    learnMode = false,
                )
            }.getOrNull()
            _state.value = _state.value.copy(
                feedback = if (correct) Feedback.CORRECT else Feedback.WRONG,
                correct = _state.value.correct + if (correct) 1 else 0,
                answered = _state.value.answered + 1,
                xp = _state.value.xp + (outcome?.xpGained ?: 0),
                streak = outcome?.streak ?: _state.value.streak,
                achievements = (_state.value.achievements + (outcome?.newAchievements ?: emptyList())).distinctBy { it.id },
            )
            busy = false
        }
    }

    fun next() {
        val state = _state.value
        if (state.feedback == null) return
        if (state.index + 1 >= state.questions.size) {
            finish()
        } else {
            questionAt = System.currentTimeMillis()
            _state.value = state.copy(
                index = state.index + 1,
                input = "",
                feedback = null,
                selectedId = null,
            )
        }
    }

    private fun finish() {
        if (logged) {
            _state.value = _state.value.copy(finished = true)
            return
        }
        logged = true
        val state = _state.value
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(1)
        _state.value = state.copy(finished = true, seconds = seconds)
        viewModelScope.launch {
            var bonus = 0
            var achievementList = _state.value.achievements
            if (mode == PracticeMode.DAILY) {
                val (earned, unlocked) = graph.learning.markChallenge(_state.value.correct)
                bonus = earned
                achievementList = (achievementList + unlocked).distinctBy { it.id }
            }
            val (_, unlocked) = graph.learning.logSession(
                mode = mode,
                startedAt = startedAt,
                reviewed = _state.value.answered,
                correct = _state.value.correct,
                seconds = seconds,
                bonusXp = 0,
            )
            _state.value = _state.value.copy(
                bonusXp = bonus,
                achievements = (achievementList + unlocked).distinctBy { it.id },
            )
        }
    }

    fun abandon() {
        if (logged) return
        val state = _state.value
        if (state.answered == 0) return
        logged = true
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(1)
        graph.scope.launch {
            graph.learning.logSession(
                mode = mode,
                startedAt = startedAt,
                reviewed = state.answered,
                correct = state.correct,
                seconds = seconds,
                bonusXp = 0,
            )
        }
    }
}

private val QuizKind.title: String
    get() = when (this) {
        QuizKind.MEANING -> "Meaning"
        QuizKind.PICTURE -> "Picture"
        QuizKind.LISTEN -> "Listening"
        QuizKind.SPELLING -> "Spelling"
        QuizKind.RECALL -> "Recall"
    }

@Composable
fun QuizScreen(
    graph: AppGraph,
    settings: AppSettings,
    mode: PracticeMode,
    onClose: () -> Unit,
) {
    val viewModel = graphViewModel(key = "quiz-${mode.wire}") { QuizViewModel(graph, settings, mode) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalVerbiColors.current
    val question = state.current
    val playing by graph.audio.playing.collectAsStateWithLifecycle()
    val audio = question?.detail?.primaryAudio(settings.accent)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var imageVisible by remember(question?.word?.id) { mutableStateOf(true) }

    val isTyping = question?.kind == QuizKind.SPELLING || question?.kind == QuizKind.RECALL
    val autoPlay = question?.kind == QuizKind.LISTEN || question?.kind == QuizKind.SPELLING

    LaunchedEffect(question?.word?.id, state.feedback) {
        if (question != null && autoPlay && state.feedback == null && settings.autoplayAudio && settings.soundEnabled) {
            audio?.let { graph.audio.play(it) }
        }
    }
    LaunchedEffect(question?.word?.id, state.feedback) {
        if (isTyping && state.feedback == null) {
            kotlinx.coroutines.delay(180)
            focus.requestFocus()
        }
    }

    if (state.finished) {
        if (state.alreadyDone) {
            PaperBackground {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    EmptyState(
                        "Today's challenge is complete",
                        "You scored ${state.previousScore}/12. A fresh dozen arrives after midnight — keep the daily habit warm with a review session.",
                    )
                    PrimaryButton("Back to practice", onClick = onClose, modifier = Modifier.fillMaxWidth())
                }
            }
            return
        }
        SessionSummary(
            eyebrow = if (mode == PracticeMode.DAILY) "Daily challenge" else mode.title,
            headline = when {
                mode == PracticeMode.DAILY && state.correct == state.questions.size -> "A perfect\nconstellation."
                state.correct >= state.answered * 0.8f -> "Bright work."
                state.correct >= state.answered * 0.5f -> "The light is\nsteady."
                else -> "Every miss is\na marker."
            },
            message = when {
                mode == PracticeMode.DAILY -> "You finished today's twelve. The daily bonus has been added to your progress."
                else -> "${state.correct} of ${state.answered} correct. The scheduler has already adjusted tomorrow's queue."
            },
            reviewed = state.answered,
            correct = state.correct,
            xp = state.xp,
            seconds = state.seconds,
            streak = state.streak,
            achievements = state.achievements,
            bonusXp = state.bonusXp,
            celebrate = !settings.reduceMotion,
            onDone = onClose,
            onAgain = if (mode == PracticeMode.DAILY) null else ({ viewModel.start() }),
            againLabel = "New round",
        )
        return
    }

    PaperBackground {
        Column(Modifier.fillMaxSize().imePadding()) {
            SessionTopBar(
                title = if (mode == PracticeMode.DAILY) "Daily challenge · ${state.correct} correct" else mode.title,
                progress = state.progress,
                counter = if (state.questions.isEmpty()) "—" else "${(state.index + 1).coerceAtMost(state.questions.size)}/${state.questions.size}",
                onClose = {
                    viewModel.abandon()
                    onClose()
                },
            )

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingField() }
                state.error != null -> Column(Modifier.fillMaxSize().padding(20.dp)) {
                    EmptyState("This round could not open", state.error ?: "Try again from practice.")
                    PrimaryButton("Back", onClick = onClose, modifier = Modifier.fillMaxWidth())
                }
                question == null -> Column(Modifier.fillMaxSize().padding(20.dp)) {
                    EmptyState("No questions available", "Add more words to your level or try again later.")
                    PrimaryButton("Back", onClick = onClose, modifier = Modifier.fillMaxWidth())
                }
                else -> {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (mode == PracticeMode.DAILY) "DAILY · ${question.kind.title.uppercase()}" else "QUESTION ${state.index + 1}",
                                fontFamily = ManropeFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                letterSpacing = 2.sp,
                                color = colors.accent,
                            )
                            Spacer(Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Check, null, tint = colors.teal, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "${state.correct}",
                                    fontFamily = ManropeFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = colors.inkSoft,
                                )
                            }
                        }
                        Spacer(Modifier.height(18.dp))

                        when (question.kind) {
                            QuizKind.MEANING -> MeaningPrompt(question, audio, playing == audio, {
                                if (playing == audio) graph.audio.stop() else graph.audio.play(audio)
                            })
                            QuizKind.PICTURE -> PicturePrompt(question, imageVisible) { imageVisible = false }
                            QuizKind.LISTEN -> ListenPrompt(question, audio, playing == audio) {
                                if (playing == audio) graph.audio.stop() else graph.audio.play(audio)
                            }
                            QuizKind.SPELLING -> SpellingPrompt(question, audio, playing == audio) {
                                if (playing == audio) graph.audio.stop() else graph.audio.play(audio)
                            }
                            QuizKind.RECALL -> RecallPrompt(question)
                        }

                        Spacer(Modifier.height(22.dp))

                        if (isTyping) {
                            QuizInput(
                                value = state.input,
                                enabled = state.feedback == null,
                                focus = focus,
                                onSubmit = {
                                    keyboard?.hide()
                                    viewModel.submitText()
                                },
                                onValueChange = viewModel::updateInput,
                            )
                            Spacer(Modifier.height(14.dp))
                            AnimatedVisibility(visible = state.feedback == null) {
                                PrimaryButton(
                                    text = "Check answer",
                                    onClick = {
                                        keyboard?.hide()
                                        viewModel.submitText()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = state.input.isNotBlank(),
                                )
                            }
                        } else {
                            val options = question.options
                            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                options.forEachIndexed { index, option ->
                                    val selected = state.selectedId == (option.wordId.ifBlank { option.text })
                                    val reveal = state.feedback != null
                                    val isRight = option.correct
                                    OptionRow(
                                        letter = ('A' + index).toString(),
                                        text = option.text,
                                        selected = selected,
                                        reveal = reveal,
                                        correct = isRight,
                                        enabled = state.feedback == null,
                                        onClick = { viewModel.choose(option) },
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        AnimatedVisibility(
                            visible = state.feedback != null,
                            enter = fadeIn() + slideInVertically { it / 3 },
                        ) {
                            val correct = state.feedback == Feedback.CORRECT
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(if (correct) colors.teal.copy(alpha = 0.1f) else colors.accent.copy(alpha = 0.09f))
                                    .border(
                                        1.dp,
                                        if (correct) colors.teal.copy(alpha = 0.25f) else colors.accent.copy(alpha = 0.25f),
                                        RoundedCornerShape(22.dp),
                                    )
                                    .padding(16.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(if (correct) colors.teal else colors.accent),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            if (correct) Icons.Rounded.Check else Icons.Rounded.Close,
                                            null,
                                            tint = colors.paper,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(9.dp))
                                    Text(
                                        if (correct) "That's it." else "Not quite — it was “${question.word.word}”.",
                                        fontFamily = ManropeFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = colors.ink,
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    question.detail.senses.firstOrNull()?.definition ?: question.word.firstSense,
                                    fontFamily = ManropeFamily,
                                    fontSize = 14.sp,
                                    lineHeight = 19.sp,
                                    color = colors.inkSoft,
                                )
                                question.detail.examples.firstOrNull()?.let { example ->
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "“${example.text}”",
                                        fontFamily = FrauncesFamily,
                                        fontStyle = FontStyle.Italic,
                                        fontSize = 14.5.sp,
                                        lineHeight = 19.sp,
                                        color = colors.ink,
                                    )
                                }
                                Spacer(Modifier.height(14.dp))
                                PrimaryButton(
                                    text = if (state.index + 1 >= state.questions.size) "See results" else "Continue",
                                    onClick = {
                                        keyboard?.hide()
                                        viewModel.next()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MeaningPrompt(
    question: QuizQuestion,
    audio: String?,
    playing: Boolean,
    onPlay: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    Column {
        Text(
            "Which meaning belongs to this word?",
            fontFamily = ManropeFamily,
            fontSize = 14.sp,
            color = colors.inkSoft,
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                question.word.word,
                fontFamily = FrauncesFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 46.sp,
                lineHeight = 50.sp,
                letterSpacing = (-1).sp,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            if (audio != null) SpeakerButton(onClick = onPlay, playing = playing, size = 46.dp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            question.word.levelLabel + " · " + question.word.pos,
            fontFamily = ManropeFamily,
            fontSize = 12.5.sp,
            color = colors.inkFaint,
        )
    }
}

@Composable
private fun PicturePrompt(question: QuizQuestion, visible: Boolean, onFail: () -> Unit) {
    val colors = LocalVerbiColors.current
    Column {
        Text(
            "Which word names this picture?",
            fontFamily = ManropeFamily,
            fontSize = 14.sp,
            color = colors.inkSoft,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(30.dp)),
        ) {
            if (visible) {
                WordImage(question.word.image, Modifier.fillMaxSize(), corner = 30.dp, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize().background(colors.sunken), contentAlignment = Alignment.Center) {
                    Text("Image hidden", fontFamily = ManropeFamily, color = colors.inkFaint, fontSize = 13.5.sp)
                }
            }
        }
    }
}

@Composable
private fun ListenPrompt(question: QuizQuestion, audio: String?, playing: Boolean, onPlay: () -> Unit) {
    val colors = LocalVerbiColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(6.dp))
        Text(
            "Which word did you hear?",
            fontFamily = ManropeFamily,
            fontSize = 14.sp,
            color = colors.inkSoft,
        )
        Spacer(Modifier.height(22.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(170.dp)) {
            Box(
                Modifier
                    .size(170.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                colors.brass.copy(alpha = 0.2f),
                                colors.brass.copy(alpha = 0.04f),
                            )
                        )
                    )
            )
            Box(
                Modifier
                    .size(124.dp)
                    .clip(CircleShape)
                    .background(colors.ink),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.VolumeUp,
                    "Play word",
                    tint = colors.brassBright,
                    modifier = Modifier.size(45.dp).clickable(onClick = onPlay),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (playing) "Playing…" else if (audio == null) "Audio unavailable" else "Tap to hear it again",
            fontFamily = ManropeFamily,
            fontSize = 13.sp,
            color = colors.inkFaint,
        )
    }
}

@Composable
private fun SpellingPrompt(question: QuizQuestion, audio: String?, playing: Boolean, onPlay: () -> Unit) {
    val colors = LocalVerbiColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            "Hear it, then spell it.",
            fontFamily = ManropeFamily,
            fontSize = 14.sp,
            color = colors.inkSoft,
        )
        Spacer(Modifier.height(18.dp))
        SpeakerButton(onClick = onPlay, playing = playing, size = 88.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            question.word.word.map { if (it == ' ' || it == '-') it else '_' }.joinToString(" "),
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            letterSpacing = 2.sp,
            color = colors.inkFaint,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${question.word.levelLabel} · ${question.word.word.length} letters",
            fontFamily = ManropeFamily,
            fontSize = 13.sp,
            color = colors.inkFaint,
        )
    }
}

@Composable
private fun RecallPrompt(question: QuizQuestion) {
    val colors = LocalVerbiColors.current
    Column {
        Text(
            "Which word means this?",
            fontFamily = ManropeFamily,
            fontSize = 14.sp,
            color = colors.inkSoft,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(colors.raised)
                .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                .padding(18.dp),
        ) {
            Text(
                question.detail.senses.firstOrNull()?.definition ?: question.word.firstSense,
                fontFamily = FrauncesFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                color = colors.ink,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "${question.word.levelLabel} · ${question.word.pos} · ${question.word.word.length} letters",
            fontFamily = ManropeFamily,
            fontSize = 13.sp,
            color = colors.inkFaint,
        )
    }
}

@Composable
private fun QuizInput(
    value: String,
    enabled: Boolean,
    focus: FocusRequester,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.raised)
            .border(1.dp, if (enabled) colors.line else colors.line.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    "Type your answer…",
                    fontFamily = ManropeFamily,
                    fontSize = 15.sp,
                    color = colors.inkFaint,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = FrauncesFamily,
                    fontSize = 19.sp,
                    color = colors.ink,
                ),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (value.isNotEmpty() && enabled) {
            Text(
                "${normalizeTypedAnswer(value).length}",
                fontFamily = ManropeFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = colors.inkFaint,
            )
        }
    }
}

@Composable
private fun OptionRow(
    letter: String,
    text: String,
    selected: Boolean,
    reveal: Boolean,
    correct: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val background = when {
        reveal && correct -> colors.teal.copy(alpha = if (colors.isDark) 0.22f else 0.12f)
        reveal && selected && !correct -> colors.accent.copy(alpha = 0.12f)
        selected -> colors.sunken
        else -> colors.raised
    }
    val border = when {
        reveal && correct -> colors.teal.copy(alpha = 0.5f)
        reveal && selected && !correct -> colors.accent.copy(alpha = 0.5f)
        else -> colors.line
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(27.dp)
                .clip(CircleShape)
                .background(
                    when {
                        reveal && correct -> colors.teal
                        reveal && selected && !correct -> colors.accent
                        else -> colors.sunken
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (reveal && correct) {
                Icon(Icons.Rounded.Check, null, tint = colors.paper, modifier = Modifier.size(15.dp))
            } else if (reveal && selected) {
                Icon(Icons.Rounded.Close, null, tint = colors.paper, modifier = Modifier.size(14.dp))
            } else {
                Text(
                    letter,
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = colors.inkSoft,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            fontFamily = ManropeFamily,
            fontWeight = if (selected || (reveal && correct)) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.5.sp,
            lineHeight = 19.sp,
            color = colors.ink,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
