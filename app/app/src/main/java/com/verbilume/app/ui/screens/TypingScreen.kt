package com.verbilume.app.ui.screens

import androidx.activity.compose.LocalActivity

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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.verbilume.app.data.AchievementDef
import com.verbilume.app.data.AppGraph
import com.verbilume.app.data.AppSettings
import com.verbilume.app.data.PracticeMode
import com.verbilume.app.data.TypingLine
import com.verbilume.app.data.TypingMode
import com.verbilume.app.ui.EmptyState
import com.verbilume.app.ui.LoadingField
import com.verbilume.app.ui.PaperBackground
import com.verbilume.app.ui.PrimaryButton
import com.verbilume.app.ui.friendlyError
import com.verbilume.app.ui.graphViewModel
import com.verbilume.app.ui.normalizeTypedAnswer
import com.verbilume.app.ui.theme.FrauncesFamily
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.theme.ManropeFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class TypingUiState(
    val loading: Boolean = true,
    val mode: TypingMode = TypingMode.WORDS,
    val lines: List<TypingLine> = emptyList(),
    val index: Int = 0,
    val input: String = "",
    val submitted: Boolean = false,
    val lastDistance: Int = 0,
    val lastAccuracy: Int = 0,
    val lastCorrect: Boolean = false,
    val completed: Int = 0,
    val exact: Int = 0,
    val totalChars: Int = 0,
    val correctChars: Int = 0,
    val bestWpm: Int = 0,
    val bestAccuracy: Int = 0,
    val finished: Boolean = false,
    val xp: Int = 0,
    val seconds: Int = 0,
    val streak: Int = 0,
    val achievements: List<AchievementDef> = emptyList(),
    val error: String? = null,
) {
    val current: TypingLine? get() = lines.getOrNull(index)
    val progress: Float
        get() = if (lines.isEmpty()) 0f
        else ((index + if (submitted) 1 else 0).toFloat() / lines.size).coerceIn(0f, 1f)
    val accuracy: Int
        get() = if (totalChars == 0) 100 else (correctChars * 100.0 / totalChars).roundToInt()
    val wpm: Int
        get() {
            val seconds = seconds.coerceAtLeast(10)
            return ((correctChars / 5.0) / (seconds / 60.0)).roundToInt().coerceAtLeast(0)
        }
}

class TypingViewModel(
    private val graph: AppGraph,
    private val settings: AppSettings,
) : ViewModel() {
    private val _state = MutableStateFlow(TypingUiState())
    val state: StateFlow<TypingUiState> = _state.asStateFlow()
    private var startedAt = System.currentTimeMillis()
    private var persisted = false
    private var busy = false

    init { load(TypingMode.WORDS) }

    fun load(mode: TypingMode) {
        viewModelScope.launch {
            _state.value = TypingUiState(loading = true, mode = mode)
            persisted = false
            busy = false
            startedAt = System.currentTimeMillis()
            val typing = runCatching { graph.learning.typingStats() }.getOrNull()
            runCatching { graph.sessions.buildTyping(settings, mode, 8) }
                .onSuccess { lines ->
                    _state.value = TypingUiState(
                        loading = false,
                        mode = mode,
                        lines = lines,
                        bestWpm = typing?.bestWpm ?: 0,
                        bestAccuracy = typing?.bestAccuracy ?: 0,
                    )
                }
                .onFailure {
                    _state.value = TypingUiState(
                        loading = false,
                        mode = mode,
                        bestWpm = typing?.bestWpm ?: 0,
                        bestAccuracy = typing?.bestAccuracy ?: 0,
                        error = friendlyError(it, "This practice run could not be prepared. Please try again."),
                    )
                }
        }
    }

    fun updateInput(value: String) {
        if (busy || _state.value.submitted) return
        _state.value = _state.value.copy(input = value)
    }

    fun submit() {
        val state = _state.value
        val line = state.current ?: return
        if (state.submitted || busy) return
        val normTyped = normalizeTypedAnswer(state.input)
        if (normTyped.isBlank()) return
        busy = true
        val normTarget = normalizeTypedAnswer(line.target)
        val targetChars = normTarget.length.coerceAtLeast(1)
        val distance = editDistance(normTyped, normTarget)
        val gained = (targetChars - distance).coerceAtLeast(0)
        val correct = normTyped == normTarget
        _state.value = state.copy(
            submitted = true,
            lastDistance = distance,
            lastAccuracy = (gained * 100.0 / targetChars).roundToInt().coerceIn(0, 100),
            lastCorrect = correct,
            completed = state.completed + 1,
            exact = state.exact + if (correct) 1 else 0,
            totalChars = state.totalChars + targetChars,
            correctChars = state.correctChars + gained,
        )
        busy = false
    }

    fun next() {
        val state = _state.value
        if (!state.submitted || busy) return
        if (state.index + 1 >= state.lines.size) {
            finish()
        } else {
            _state.value = state.copy(index = state.index + 1, input = "", submitted = false)
        }
    }

    private fun finish() {
        if (persisted) return
        persisted = true
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(1)
        _state.value = _state.value.copy(finished = true, seconds = seconds)
        viewModelScope.launch {
            persist(seconds)
        }
    }

    private suspend fun persist(seconds: Int) {
        val state = _state.value
        if (state.completed == 0) return
        val accuracy = state.accuracy
        val wpm = state.wpm
        val outcome = runCatching {
            graph.learning.recordTyping(
                mode = state.mode.wire,
                chars = state.totalChars,
                correctChars = state.correctChars,
                wpm = wpm,
                accuracy = accuracy,
            )
        }.getOrNull()
        val (_, unlocked) = runCatching {
            graph.learning.logSession(
                mode = PracticeMode.TYPING,
                startedAt = startedAt,
                reviewed = state.completed,
                correct = state.exact,
                seconds = seconds,
                bonusXp = 0,
            )
        }.getOrDefault(0 to emptyList())
        val streak = runCatching { graph.learning.streak() }.getOrDefault(0)
        _state.update {
            it.copy(
                xp = outcome?.xp ?: 0,
                streak = streak,
                achievements = ((outcome?.newAchievements ?: emptyList()) + unlocked).distinctBy { a -> a.id },
                seconds = seconds,
            )
        }
    }

    fun abandon() {
        if (persisted) return
        val state = _state.value
        if (state.completed == 0) return
        persisted = true
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(1)
        graph.scope.launch { persist(seconds) }
    }
}

private fun editDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in a.indices) {
        current[0] = i + 1
        for (j in b.indices) {
            val cost = if (a[i] == b[j]) 0 else 1
            current[j + 1] = minOf(
                current[j] + 1,
                previous[j + 1] + 1,
                previous[j] + cost,
            )
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[b.length]
}

@Composable
fun TypingScreen(
    graph: AppGraph,
    settings: AppSettings,
    onClose: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val viewModel = graphViewModel { TypingViewModel(graph, settings) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val line = state.current
    val activity = LocalActivity.current
    val playing by graph.audio.playing.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val choosing = state.completed == 0 && !state.submitted

    LaunchedEffect(line?.target, state.index) {
        if (line == null) return@LaunchedEffect
        if (state.mode == TypingMode.DICTATION && !state.submitted &&
            settings.autoplayAudio && settings.soundEnabled
        ) {
            line.audio?.let { graph.audio.play(it) }
        }
    }
    LaunchedEffect(state.index, state.mode, state.submitted) {
        if (!state.submitted && state.lines.isNotEmpty()) {
            delay(220)
            focus.requestFocus()
        }
    }

    if (state.finished) {
        val allExact = state.exact == state.completed && state.completed > 0
        SessionSummary(
            eyebrow = "Typing studio · ${state.mode.title}",
            headline = when {
                allExact -> "Clean copy,\nall the way."
                state.accuracy >= 90 -> "The keys are\nwarming up."
                else -> "Every keystroke\ncounts."
            },
            message = "${state.exact} of ${state.completed} lines copied exactly, at ${state.accuracy}% character accuracy and ${state.wpm} WPM.",
            reviewed = state.completed,
            correct = state.exact,
            xp = state.xp,
            seconds = state.seconds,
            streak = state.streak,
            achievements = state.achievements,
            celebrate = !settings.reduceMotion,
            onDone = { graph.ads.showInterstitial(activity) { onClose() } },
            onAgain = { viewModel.load(state.mode) },
            againLabel = "Another pass",
        )
        return
    }

    PaperBackground {
        Column(Modifier.fillMaxSize().imePadding()) {
            SessionTopBar(
                title = "Typing · ${state.mode.title}",
                progress = state.progress,
                counter = if (state.lines.isEmpty()) "—" else "${(state.index + 1).coerceAtMost(state.lines.size)}/${state.lines.size}",
                onClose = {
                    viewModel.abandon()
                    onClose()
                },
            )

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingField() }
                state.error != null || line == null -> Column(Modifier.fillMaxSize().padding(20.dp)) {
                    EmptyState(
                        "The typing desk is empty",
                        state.error ?: "No lines could be prepared for this mode. Try another mode or level.",
                    )
                    PrimaryButton("Back", onClick = onClose, modifier = Modifier.fillMaxWidth())
                }
                else -> Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    if (choosing) {
                        Spacer(Modifier.height(4.dp))
                        TypingModeRow(
                            selected = state.mode,
                            enabled = state.completed == 0 && !state.submitted,
                            onSelect = viewModel::load,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.mode.instruction.uppercase(),
                            fontFamily = ManropeFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            letterSpacing = 1.8.sp,
                            color = colors.accent,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.completed > 0) {
                            Icon(Icons.Rounded.Check, null, tint = colors.teal, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${state.exact}/${state.completed}",
                                fontFamily = ManropeFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = colors.inkSoft,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))

                    when (state.mode) {
                        TypingMode.WORDS -> WordPrompt(line, state.submitted)
                        TypingMode.SENTENCES -> SentencePrompt(line, state.input, state.submitted)
                        TypingMode.DICTATION -> DictationPrompt(
                            line = line,
                            playing = playing == line.audio,
                            submitted = state.submitted,
                            onPlay = {
                                if (playing == line.audio) graph.audio.stop()
                                else line.audio?.let { graph.audio.play(it) }
                            },
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    TypingInput(
                        value = state.input,
                        mode = state.mode,
                        enabled = !state.submitted,
                        focus = focus,
                        onValueChange = viewModel::updateInput,
                        onSubmit = {
                            keyboard?.hide()
                            viewModel.submit()
                        },
                    )
                    Spacer(Modifier.height(12.dp))

                    if (!state.submitted) {
                        PrimaryButton(
                            text = if (state.mode == TypingMode.DICTATION) "Check what I heard" else "Check line",
                            onClick = {
                                keyboard?.hide()
                                viewModel.submit()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.input.isNotBlank(),
                        )
                    } else {
                        TypingFeedback(
                            state = state,
                            line = line,
                            onNext = {
                                keyboard?.hide()
                                viewModel.next()
                            },
                        )
                    }
                    Spacer(Modifier.height(26.dp))
                }
            }
        }
    }
}

@Composable
private fun TypingModeRow(
    selected: TypingMode,
    enabled: Boolean,
    onSelect: (TypingMode) -> Unit,
) {
    val colors = LocalVerbiColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.sunken)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        TypingMode.entries.forEach { mode ->
            val active = mode == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) colors.raised else colors.sunken)
                    .then(if (!active) Modifier.border(1.dp, colors.line.copy(alpha = 0.5f), RoundedCornerShape(12.dp)) else Modifier)
                    .clickable(enabled = enabled) { onSelect(mode) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    mode.title,
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = if (active) colors.ink else colors.inkSoft,
                )
            }
        }
    }
}

@Composable
private fun WordPrompt(line: TypingLine, submitted: Boolean) {
    val colors = LocalVerbiColors.current
    Column {
        Text(
            "TYPE THE WORD",
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            letterSpacing = 2.sp,
            color = colors.inkFaint,
        )
        Spacer(Modifier.height(9.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(colors.raised)
                .border(1.dp, colors.line, RoundedCornerShape(26.dp))
                .padding(22.dp),
        ) {
            Column {
                Text(
                    line.prompt,
                    fontFamily = FrauncesFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                    color = colors.ink,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (submitted) line.target else "${line.target.length} letters · ${line.word.levelLabel} · ${line.word.pos}",
                    fontFamily = ManropeFamily,
                    fontWeight = if (submitted) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.5.sp,
                    color = if (submitted) colors.teal else colors.inkFaint,
                )
            }
        }
    }
}

@Composable
private fun SentencePrompt(line: TypingLine, input: String, submitted: Boolean) {
    val colors = LocalVerbiColors.current
    val annotated = remember(line.target, input, submitted, colors.isDark) {
        buildTypingTarget(line.target, input, submitted, colors.ink, colors.teal, colors.accent)
    }
    Column {
        Text(
            "COPY THE SENTENCE",
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            letterSpacing = 2.sp,
            color = colors.inkFaint,
        )
        Spacer(Modifier.height(9.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(colors.raised)
                .border(1.dp, colors.line, RoundedCornerShape(26.dp))
                .padding(22.dp),
        ) {
            Column {
                Text(
                    annotated,
                    fontFamily = FrauncesFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 21.sp,
                    lineHeight = 29.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "From “${line.word.word}” · ${line.word.levelLabel}",
                    fontFamily = ManropeFamily,
                    fontSize = 13.sp,
                    color = colors.inkFaint,
                )
            }
        }
    }
}

private fun buildTypingTarget(
    target: String,
    input: String,
    submitted: Boolean,
    ink: androidx.compose.ui.graphics.Color,
    teal: androidx.compose.ui.graphics.Color,
    accent: androidx.compose.ui.graphics.Color,
): AnnotatedString = buildAnnotatedString {
    target.forEachIndexed { index, char ->
        val typed = input.getOrNull(index)
        val style = when {
            !submitted && typed == null -> SpanStyle(color = ink)
            !submitted && typed == char -> SpanStyle(color = teal)
            !submitted && typed != null && typed != char -> SpanStyle(color = accent, background = accent.copy(alpha = 0.14f))
            submitted -> SpanStyle(color = ink)
            else -> SpanStyle(color = ink)
        }
        withStyle(style) { append(char) }
    }
    if (!submitted && input.length > target.length) {
        withStyle(SpanStyle(color = accent, background = accent.copy(alpha = 0.14f))) {
            append(input.substring(target.length))
        }
    }
}

@Composable
private fun DictationPrompt(
    line: TypingLine,
    playing: Boolean,
    submitted: Boolean,
    onPlay: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.ink)
            .padding(24.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "THE SENTENCE IS IN YOUR EARS",
                fontFamily = ManropeFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                color = colors.brassBright,
            )
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(colors.paper.copy(alpha = 0.1f))
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.VolumeUp,
                    "Replay sentence",
                    tint = if (line.audio == null) colors.paper.copy(alpha = 0.35f) else colors.brassBright,
                    modifier = Modifier.size(42.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                when {
                    line.audio == null -> "Audio unavailable for this line"
                    playing -> "Playing…"
                    else -> "Tap to replay"
                },
                fontFamily = ManropeFamily,
                fontSize = 12.5.sp,
                color = colors.paper.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "From “${line.word.word}” · ${line.word.levelLabel}",
                    fontFamily = ManropeFamily,
                    fontSize = 12.5.sp,
                    color = colors.paper.copy(alpha = 0.4f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "· ${line.target.length} characters",
                    fontFamily = ManropeFamily,
                    fontSize = 12.5.sp,
                    color = colors.paper.copy(alpha = 0.4f),
                )
            }
            if (submitted) {
                Spacer(Modifier.height(14.dp))
                Text(
                    line.target,
                    fontFamily = FrauncesFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                    color = colors.paper,
                )
            }
        }
    }
}

@Composable
private fun TypingInput(
    value: String,
    mode: TypingMode,
    enabled: Boolean,
    focus: FocusRequester,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val multiline = mode != TypingMode.WORDS
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) colors.raised else colors.sunken)
            .border(1.dp, if (enabled) colors.accent.copy(alpha = 0.45f) else colors.line, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                when (mode) {
                    TypingMode.WORDS -> "Type the word…"
                    TypingMode.SENTENCES -> "Type the sentence…"
                    TypingMode.DICTATION -> "Type what you hear…"
                },
                fontFamily = if (multiline) ManropeFamily else FrauncesFamily,
                fontSize = if (multiline) 15.sp else 21.sp,
                color = colors.inkFaint,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            textStyle = TextStyle(
                fontFamily = if (multiline) ManropeFamily else FrauncesFamily,
                fontWeight = FontWeight.Medium,
                fontSize = if (multiline) 16.sp else 22.sp,
                lineHeight = if (multiline) 23.sp else 28.sp,
                color = colors.ink,
            ),
            cursorBrush = SolidColor(colors.accent),
            minLines = if (multiline) 2 else 1,
            maxLines = if (multiline) 6 else 2,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        )
    }
}

@Composable
private fun TypingFeedback(
    state: TypingUiState,
    line: TypingLine,
    onNext: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val correct = state.lastCorrect
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (correct) colors.teal.copy(alpha = 0.1f) else colors.accent.copy(alpha = 0.08f))
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
                when {
                    correct -> "Exactly right."
                    state.lastDistance == 1 -> "One character away."
                    else -> "${state.lastDistance} characters away."
                },
                fontFamily = ManropeFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = colors.ink,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${state.lastAccuracy}%",
                fontFamily = ManropeFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (correct) colors.teal else colors.accent,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (state.mode == TypingMode.WORDS) "The word was" else "The line was",
            fontFamily = ManropeFamily,
            fontSize = 12.5.sp,
            color = colors.inkFaint,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            line.target,
            fontFamily = FrauncesFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp,
            lineHeight = 25.sp,
            color = colors.ink,
        )
        if (!correct && state.input.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "You typed: ${state.input}",
                fontFamily = ManropeFamily,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                color = colors.inkSoft,
                maxLines = 4,
            )
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            text = if (state.index + 1 >= state.lines.size) "See summary" else "Next line",
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
