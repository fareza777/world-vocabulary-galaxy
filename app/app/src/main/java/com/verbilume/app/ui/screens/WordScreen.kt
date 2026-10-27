package com.verbilume.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.verbilume.app.data.AppGraph
import com.verbilume.app.data.AppSettings
import com.verbilume.app.data.FsrsCard
import com.verbilume.app.data.FsrsState
import com.verbilume.app.data.WordDetail
import com.verbilume.app.data.exampleAudio
import com.verbilume.app.data.primaryAudio
import com.verbilume.app.data.secondaryAudio
import com.verbilume.app.ui.BackButton
import com.verbilume.app.ui.EmptyState
import com.verbilume.app.ui.GradientRule
import com.verbilume.app.ui.LevelBadge
import com.verbilume.app.ui.LoadingField
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.PaperBackground
import com.verbilume.app.ui.PrimaryButton
import com.verbilume.app.ui.SecondaryButton
import com.verbilume.app.ui.SectionHeading
import com.verbilume.app.ui.SpeakerButton
import com.verbilume.app.ui.WordImage
import com.verbilume.app.ui.friendlyError
import com.verbilume.app.ui.graphViewModel
import com.verbilume.app.ui.headlineSize
import com.verbilume.app.ui.theme.FrauncesFamily
import com.verbilume.app.ui.theme.ManropeFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WordUiState(
    val loading: Boolean = true,
    val detail: WordDetail? = null,
    val card: FsrsCard? = null,
    val bookmarked: Boolean = false,
    val topicLabel: String = "Atlas",
    val error: String? = null,
)

class WordViewModel(private val graph: AppGraph, private val wordId: String) : ViewModel() {
    private val _state = MutableStateFlow(WordUiState())
    val state: StateFlow<WordUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val detail = graph.content.detail(wordId) ?: error("This word is not in the atlas.")
                    val card = graph.learning.card(wordId)
                    val bookmarked = graph.learning.isBookmarked(wordId)
                    val topic = graph.content.meta.topics.getOrNull(detail.summary.topic)?.label ?: "Atlas"
                    WordUiState(false, detail, card, bookmarked, topic)
                }
            }.onSuccess { loaded ->
                _state.value = loaded
            }.onFailure {
                _state.value = WordUiState(false, error = friendlyError(it, "Could not open this word. Please try again."))
            }
        }
    }

    fun toggleBookmark() {
        viewModelScope.launch {
            val value = graph.learning.toggleBookmark(wordId)
            _state.value = _state.value.copy(bookmarked = value)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordScreen(
    graph: AppGraph,
    settings: AppSettings,
    wordId: String,
    onBack: () -> Unit,
    onWord: (String) -> Unit,
    onReview: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel = graphViewModel(key = "word-$wordId") { WordViewModel(graph, wordId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playing by graph.audio.playing.collectAsStateWithLifecycle()
    var sharing by remember { mutableStateOf(false) }

    val detail = state.detail
    val mainAudio = detail?.primaryAudio(settings.accent)

    LaunchedEffect(detail?.id) {
        if (detail != null && settings.autoplayAudio && settings.soundEnabled) {
            mainAudio?.let { graph.audio.play(it) }
        }
    }

    fun openRelated(name: String) {
        scope.launch {
            val hit = withContext(Dispatchers.IO) { graph.content.search(name, 1).firstOrNull() }
            if (hit != null) onWord(hit.id)
        }
    }

    PaperBackground {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingField()
            }
            detail == null -> Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(20.dp),
            ) {
                BackButton(onBack)
                EmptyState(
                    title = "Lost in the atlas",
                    description = state.error ?: "This word could not be found.",
                )
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars),
                contentPadding = PaddingValues(bottom = 48.dp),
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BackButton(onBack)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "FIELD NOTE",
                            fontFamily = ManropeFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            letterSpacing = 2.6.sp,
                            color = colors.inkFaint,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        RoundAction(
                            onClick = viewModel::toggleBookmark,
                            active = state.bookmarked,
                        ) {
                            Icon(
                                if (state.bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                if (state.bookmarked) "Remove bookmark" else "Bookmark",
                                tint = if (state.bookmarked) colors.accent else colors.ink,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        RoundAction(
                            onClick = {
                                if (sharing) return@RoundAction
                                sharing = true
                                scope.launch {
                                    runCatching {
                                        val uri = graph.shareCards.create(detail)
                                        val intent = graph.shareCards.shareIntent(uri, detail)
                                        context.startActivity(Intent.createChooser(intent, "Share ${detail.word}"))
                                    }
                                    sharing = false
                                }
                            },
                            active = false,
                        ) {
                            Icon(
                                Icons.Rounded.Share,
                                "Share this word",
                                tint = colors.ink,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }

                if (settings.showImages) {
                    item {
                        Box(
                            Modifier
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                                .fillMaxWidth()
                                .height(330.dp)
                                .clip(RoundedCornerShape(32.dp)),
                        ) {
                            WordImage(detail.image, Modifier.fillMaxSize(), corner = 32.dp)
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0.55f to Color.Transparent,
                                            1f to colors.ink.copy(alpha = 0.55f),
                                        )
                                    )
                            )
                            Row(
                                Modifier.align(Alignment.BottomStart).padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LevelBadge(detail.summary.levelLabel, filled = false)
                                Spacer(Modifier.width(8.dp))
                                TopicPill(state.topicLabel)
                            }
                        }
                    }
                }

                item {
                    Column(Modifier.padding(horizontal = 22.dp)) {
                        Spacer(Modifier.height(if (settings.showImages) 10.dp else 20.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                detail.word,
                                fontFamily = FrauncesFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = headlineSize(detail.word, 43),
                                lineHeight = 47.sp,
                                letterSpacing = (-1).sp,
                                color = colors.ink,
                                modifier = Modifier.weight(1f),
                            )
                            if (mainAudio != null) {
                                SpeakerButton(
                                    onClick = {
                                        if (playing == mainAudio) graph.audio.stop()
                                        else graph.audio.play(mainAudio)
                                    },
                                    playing = playing == mainAudio,
                                    size = 48.dp,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                detail.senses.firstOrNull()?.pos ?: detail.summary.pos,
                                fontFamily = ManropeFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                                color = colors.accent,
                                fontStyle = FontStyle.Italic,
                            )
                            if (settings.showIpa) {
                                val ipa = if (settings.accent == com.verbilume.app.data.Accent.UK) {
                                    detail.summary.ipaUk ?: detail.summary.ipaUs
                                } else {
                                    detail.summary.ipaUs ?: detail.summary.ipaUk
                                }
                                if (!ipa.isNullOrBlank()) {
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        ipa,
                                        fontFamily = ManropeFamily,
                                        fontSize = 14.sp,
                                        color = colors.inkSoft,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AccentChip(
                                label = "UK",
                                path = detail.audioUk,
                                playingPath = playing,
                                onToggle = {
                                    val path = detail.audioUk ?: return@AccentChip
                                    if (playing == path) graph.audio.stop() else graph.audio.play(path)
                                },
                            )
                            AccentChip(
                                label = "US",
                                path = detail.audioUs,
                                playingPath = playing,
                                onToggle = {
                                    val path = detail.audioUs ?: return@AccentChip
                                    if (playing == path) graph.audio.stop() else graph.audio.play(path)
                                },
                            )
                            Spacer(Modifier.weight(1f))
                            if (!state.bookmarked) {
                                Text(
                                    "Save to your lexicon",
                                    fontFamily = ManropeFamily,
                                    fontSize = 12.5.sp,
                                    color = colors.inkFaint,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(onClick = viewModel::toggleBookmark)
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }

                item {
                    Column(Modifier.padding(horizontal = 22.dp, vertical = 24.dp)) {
                        GradientRule()
                    }
                }

                item {
                    Column(Modifier.padding(horizontal = 22.dp)) {
                        SectionHeading("Meaning", eyebrow = "THE PLAIN SENSE")
                        Spacer(Modifier.height(14.dp))
                        detail.senses.forEachIndexed { index, sense ->
                            Row(Modifier.padding(bottom = 14.dp)) {
                                Text(
                                    "${index + 1}",
                                    fontFamily = ManropeFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = colors.brass,
                                    modifier = Modifier.padding(top = 4.dp).width(24.dp),
                                )
                                Column {
                                    if (!sense.pos.isNullOrBlank()) {
                                        Text(
                                            sense.pos,
                                            fontFamily = ManropeFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = colors.inkFaint,
                                            letterSpacing = 0.4.sp,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                    }
                                    Text(
                                        sense.definition,
                                        fontFamily = ManropeFamily,
                                        fontSize = 16.sp,
                                        lineHeight = 25.sp,
                                        color = colors.ink,
                                    )
                                }
                            }
                        }
                    }
                }

                if (detail.examples.isNotEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
                            SectionHeading("In the wild", eyebrow = "EXAMPLE SENTENCES")
                            Spacer(Modifier.height(12.dp))
                            detail.examples.forEachIndexed { index, example ->
                                val audio = detail.exampleAudio(index)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        "“",
                                        fontFamily = FrauncesFamily,
                                        fontSize = 30.sp,
                                        lineHeight = 30.sp,
                                        color = colors.brass.copy(alpha = 0.55f),
                                        modifier = Modifier.width(22.dp),
                                    )
                                    Text(
                                        example.text,
                                        fontFamily = FrauncesFamily,
                                        fontStyle = FontStyle.Italic,
                                        fontSize = 16.sp,
                                        lineHeight = 24.sp,
                                        color = colors.ink.copy(alpha = 0.92f),
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (audio != null) {
                                        Spacer(Modifier.width(6.dp))
                                        SpeakerButton(
                                            onClick = {
                                                if (playing == audio) graph.audio.stop()
                                                else graph.audio.play(audio)
                                            },
                                            playing = playing == audio,
                                            size = 34.dp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (detail.collocations.isNotEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
                            SectionHeading("Words that travel together", eyebrow = "COLLOCATIONS")
                            Spacer(Modifier.height(14.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                detail.collocations.forEach { phrase ->
                                    Box(
                                        Modifier
                                            .padding(bottom = 8.dp)
                                            .clip(RoundedCornerShape(11.dp))
                                            .background(colors.brass.copy(alpha = if (colors.isDark) 0.16f else 0.1f))
                                            .border(1.dp, colors.brass.copy(alpha = 0.24f), RoundedCornerShape(11.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                    ) {
                                        Text(
                                            phrase,
                                            fontFamily = ManropeFamily,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.5.sp,
                                            color = colors.ink,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (detail.family.isNotEmpty() || detail.synonyms.isNotEmpty() || detail.antonyms.isNotEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
                            if (detail.family.isNotEmpty()) {
                                SectionHeading("The family", eyebrow = "WORD FORMS")
                                Spacer(Modifier.height(12.dp))
                                detail.family.forEach { member ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { openRelated(member.word) }
                                            .padding(vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            member.word,
                                            fontFamily = ManropeFamily,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = colors.ink,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (!member.pos.isNullOrBlank()) {
                                            Text(
                                                member.pos,
                                                fontFamily = ManropeFamily,
                                                fontStyle = FontStyle.Italic,
                                                fontSize = 12.5.sp,
                                                color = colors.inkFaint,
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                            if (detail.synonyms.isNotEmpty()) {
                                RelatedRow("Synonyms", detail.synonyms, colors.teal, ::openRelated)
                                Spacer(Modifier.height(12.dp))
                            }
                            if (detail.antonyms.isNotEmpty()) {
                                RelatedRow("Antonyms", detail.antonyms, colors.accent, ::openRelated)
                            }
                        }
                    }
                }

                item {
                    val card = state.card
                    SurfaceStudy(card, onReview)
                }

                item {
                    Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
                        Text(
                            "Illustration and audio from the Word Atlas open collection · CC BY 4.0",
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

@Composable
private fun RoundAction(active: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val colors = LocalVerbiColors.current
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (active) colors.accent.copy(alpha = 0.12f) else colors.raised.copy(alpha = 0.8f))
            .border(1.dp, if (active) colors.accent.copy(alpha = 0.35f) else colors.line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun TopicPill(label: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.5.sp,
            color = Color(0xFFFBF6EC),
        )
    }
}

@Composable
private fun AccentChip(
    label: String,
    path: String?,
    playingPath: String?,
    onToggle: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    if (path == null) return
    val active = playingPath == path
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) colors.ink else colors.sunken.copy(alpha = 0.65f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.VolumeUp,
            null,
            tint = if (active) colors.paper else colors.inkSoft,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            color = if (active) colors.paper else colors.inkSoft,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelatedRow(
    title: String,
    words: List<String>,
    tint: Color,
    onClick: (String) -> Unit,
) {
    val colors = LocalVerbiColors.current
    Column {
        Text(
            title.uppercase(),
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            letterSpacing = 2.sp,
            color = colors.inkFaint,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            words.forEach { word ->
                Box(
                    Modifier
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tint.copy(alpha = 0.1f))
                        .clickable { onClick(word) }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                ) {
                    Text(
                        word,
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.5.sp,
                        color = colors.ink,
                    )
                }
            }
        }
    }
}

@Composable
private fun SurfaceStudy(card: FsrsCard?, onReview: () -> Unit) {
    val colors = LocalVerbiColors.current
    val status = when {
        card == null -> "Not studied yet"
        card.state == FsrsState.NEW -> "New light"
        card.due <= System.currentTimeMillis() -> "Ready to review"
        else -> {
            val days = ((card.due - System.currentTimeMillis()) / 86_400_000.0).coerceAtLeast(0.0)
            when {
                days < 1 -> "Back later today"
                days < 2 -> "Back tomorrow"
                else -> "Back in ${days.toInt()} days"
            }
        }
    }
    Column(Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(colors.ink, colors.ink.copy(alpha = 0.88f))))
                .padding(22.dp),
        ) {
            Column {
                Text(
                    "KEEP IT GLOWING",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    letterSpacing = 2.2.sp,
                    color = colors.brassBright,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    status,
                    fontFamily = FrauncesFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 23.sp,
                    color = colors.paper,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (card == null) {
                        "This word will enter your spaced-repetition deck the first time you practise it."
                    } else {
                        "Stability ${"%.1f".format(card.stability)} days · ${card.reps} reviews · ${card.lapses} lapses"
                    },
                    fontFamily = ManropeFamily,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = colors.paper.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.paper.copy(alpha = 0.1f))
                        .clickable(onClick = onReview)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Start a review session",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = colors.paper,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.ArrowForward, null, tint = colors.brassBright, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
