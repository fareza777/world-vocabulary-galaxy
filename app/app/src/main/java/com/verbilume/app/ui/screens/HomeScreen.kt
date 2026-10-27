package com.verbilume.app.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verbilume.app.data.AppGraph
import com.verbilume.app.data.AppSettings
import com.verbilume.app.data.LearningStats
import com.verbilume.app.data.TopicProgress
import com.verbilume.app.data.WordSummary
import com.verbilume.app.data.primaryAudio
import com.verbilume.app.ui.EmptyState
import com.verbilume.app.ui.LevelBadge
import com.verbilume.app.ui.LoadingField
import com.verbilume.app.ui.PaperBackground
import com.verbilume.app.ui.PrimaryButton
import com.verbilume.app.ui.ProgressRing
import com.verbilume.app.ui.SectionHeading
import com.verbilume.app.ui.SpeakerButton
import com.verbilume.app.ui.SurfaceCard
import com.verbilume.app.ui.VerbiMark
import com.verbilume.app.ui.WordImage
import com.verbilume.app.ui.WordTile
import com.verbilume.app.ui.formatNumber
import com.verbilume.app.ui.greeting
import com.verbilume.app.ui.initials
import com.verbilume.app.ui.plural
import com.verbilume.app.ui.prettyDate
import com.verbilume.app.ui.theme.FrauncesFamily
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.theme.ManropeFamily

@Composable
fun HomeScreen(
    graph: AppGraph,
    settings: AppSettings,
    onSearch: () -> Unit,
    onWord: (String) -> Unit,
    onAtlas: () -> Unit,
    onGalaxy: () -> Unit,
    onPractice: () -> Unit,
    onLearn: () -> Unit,
    onDaily: () -> Unit,
    onStats: () -> Unit,
    onTopic: (Int) -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val viewModel = com.verbilume.app.ui.graphViewModel { HomeViewModel(graph) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playing by graph.audio.playing.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    PaperBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 118.dp + navBarBottom),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                HomeHeader(
                    settings = settings,
                    onProfile = onProfile,
                    onSettings = onSettings,
                )
            }
            item {
                com.verbilume.app.ui.SearchFieldButton(
                    text = "Search words and meanings",
                    onClick = onSearch,
                )
            }
            val stats = state.stats
            if (state.loading && stats == null) {
                item { LoadingField() }
            } else if (state.error != null && stats == null) {
                item {
                    EmptyState(
                        title = "The atlas did not open",
                        description = state.error ?: "Please try again in a moment.",
                        action = { PrimaryButton("Try again", onClick = viewModel::refresh) },
                    )
                }
            } else if (stats != null) {
                val wotd = state.wordOfDay
                if (wotd != null) {
                    item {
                        WordOfDayCard(
                            word = wotd,
                            audioPlaying = playing != null,
                            ipa = if (settings.accent == com.verbilume.app.data.Accent.UK) wotd.ipaUk else wotd.ipaUs,
                            definition = wotd.firstSense,
                            onClick = { onWord(wotd.id) },
                            onAudio = {
                                if (settings.soundEnabled) {
                                    graph.audio.play(state.wordOfDayDetail?.primaryAudio(settings.accent))
                                }
                            },
                        )
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        StreakCard(stats.streak, Modifier.weight(1f).fillMaxHeight(), onStats)
                        GoalCard(stats, Modifier.weight(1f).fillMaxHeight(), onStats)
                    }
                }
                item {
                    DailyChallengeCard(
                        completed = stats.challengeDoneToday,
                        score = stats.challengeScoreToday,
                        onClick = onDaily,
                    )
                }
                item {
                    NextSessionCard(
                        stats = stats,
                        picks = state.picks,
                        onLearn = onLearn,
                        onPractice = onPractice,
                    )
                }
                if (state.topics.isNotEmpty()) {
                    item {
                        SectionHeading(
                            title = "The atlas",
                            eyebrow = "BROWSE BY CONSTELLATION",
                            action = "All 19",
                            onAction = onAtlas,
                        )
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                            contentPadding = PaddingValues(vertical = 2.dp),
                        ) {
                            itemsIndexed(state.topics) { index, topic ->
                                TopicCard(topic, onClick = { onTopic(index) })
                            }
                        }
                    }
                }
                if (state.picks.isNotEmpty()) {
                    item {
                        SectionHeading(
                            title = "Fresh lights",
                            eyebrow = "A FEW NEW WORDS",
                            action = "Practice",
                            onAction = onPractice,
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(state.picks) { word ->
                                WordTile(
                                    word,
                                    onClick = { onWord(word.id) },
                                    modifier = Modifier.width(154.dp),
                                )
                            }
                        }
                    }
                }
                item {
                    GalaxyInvitation(onClick = onGalaxy)
                }
                item {
                    Spacer(Modifier.height(6.dp))
                    com.verbilume.app.ui.GradientRule()
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "“Every word, a little light.”",
                        fontFamily = FrauncesFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 22.sp,
                        color = colors.inkSoft,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Vocabulary & art · Word Atlas contributors · CC BY 4.0\nEnglish-first edition by Verbilume",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkFaint,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    settings: AppSettings,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                prettyDate().uppercase(),
                fontFamily = ManropeFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                letterSpacing = 1.9.sp,
                color = colors.brass,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "${greeting()},",
                fontFamily = FrauncesFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 23.sp,
                color = colors.inkSoft,
            )
            Text(
                settings.displayName.ifBlank { "stargazer" },
                fontFamily = FrauncesFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        com.verbilume.app.ui.CircleIconButton(
            onClick = onSettings,
            background = colors.raised.copy(alpha = 0.6f),
        ) {
            Icon(Icons.Rounded.Settings, "Settings", Modifier.size(19.dp), tint = colors.inkSoft)
        }
        Spacer(Modifier.width(9.dp))
        Box(
            Modifier
                .size(47.dp)
                .clip(CircleShape)
                .background(colors.accent)
                .clickable(onClick = onProfile),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initials(settings.displayName),
                fontFamily = FrauncesFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp,
                color = Color(0xFFFFF7EA),
            )
        }
    }
}

@Composable
private fun WordOfDayCard(
    word: WordSummary,
    ipa: String?,
    definition: String,
    audioPlaying: Boolean,
    onClick: () -> Unit,
    onAudio: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    SurfaceCard(onClick = onClick, corner = 30.dp, color = colors.ink) {
        Box(Modifier.fillMaxWidth().height(370.dp)) {
            WordImage(word.image, Modifier.fillMaxSize(), corner = 30.dp)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color(0x14000000),
                        0.45f to Color(0x22000000),
                        0.72f to Color(0xCC0B1622),
                        1f to Color(0xF20B1622),
                    )
                )
            )
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x33FFFFFF))
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Star, null, Modifier.size(12.dp), tint = Color(0xFFE3B25C))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "WORD OF THE DAY",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.3.sp,
                        color = Color(0xFFFFF7EA),
                    )
                }
                Spacer(Modifier.weight(1f))
                SpeakerButton(onClick = onAudio, playing = audioPlaying, dark = true)
            }
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LevelBadge(word.levelLabel, filled = true)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "·  ${word.pos}",
                        fontFamily = ManropeFamily,
                        fontSize = 13.5.sp,
                        color = Color(0xCCFFF7EA),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    word.word,
                    fontFamily = FrauncesFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 43.sp,
                    lineHeight = 46.sp,
                    color = Color(0xFFFFF7EA),
                )
                if (ipa != null) {
                    Text(ipa, fontFamily = ManropeFamily, fontSize = 14.sp, color = Color(0xBFFFF7EA))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    definition,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xDDFFF7EA),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StreakCard(streak: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalVerbiColors.current
    SurfaceCard(modifier, onClick = onClick) {
        Column(Modifier.fillMaxHeight().padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.LocalFireDepartment,
                    null,
                    Modifier.size(21.dp),
                    tint = if (streak > 0) colors.accent else colors.inkFaint,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "STREAK",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.5.sp,
                    color = colors.inkFaint,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$streak",
                    fontFamily = FrauncesFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 34.sp,
                    lineHeight = 36.sp,
                    color = colors.ink,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    if (streak == 1) "day" else "days",
                    fontFamily = ManropeFamily,
                    fontSize = 13.5.sp,
                    color = colors.inkSoft,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (streak == 0) "Light the first one" else "Come back tomorrow",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GoalCard(stats: LearningStats, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalVerbiColors.current
    SurfaceCard(modifier, onClick = onClick) {
        Column(Modifier.fillMaxHeight().padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(stats.goalFraction, Modifier.size(52.dp), stroke = 5.dp) {
                    Text(
                        "${stats.reviewsToday}",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = colors.ink,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "TODAY",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.5.sp,
                        color = colors.inkFaint,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "of ${stats.dailyGoal}",
                        fontFamily = FrauncesFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 19.sp,
                        color = colors.ink,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (stats.goalFraction >= 1f) "Goal reached" else "${formatNumber(stats.newAvailable)} new",
                style = MaterialTheme.typography.bodySmall,
                color = if (stats.goalFraction >= 1f) colors.teal else colors.inkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DailyChallengeCard(completed: Boolean, score: Int, onClick: () -> Unit) {
    val colors = LocalVerbiColors.current
    if (completed) {
        SurfaceCard(onClick = onClick, color = colors.teal.copy(alpha = if (colors.isDark) 0.24f else 0.1f)) {
            Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(colors.teal.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(21.dp), tint = colors.teal)
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("Daily challenge complete", fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.ink)
                    Text("$score of 12 · come back tomorrow for a new set", style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = colors.inkFaint)
            }
        }
        return
    }
    SurfaceCard(onClick = onClick, color = Color(0xFF12202B)) {
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.matchParentSize()) {
                val stars = listOf(
                    Triple(0.82f, 0.24f, 2.2f), Triple(0.9f, 0.62f, 1.2f), Triple(0.73f, 0.78f, 1.6f),
                    Triple(0.64f, 0.18f, 1.0f), Triple(0.95f, 0.36f, 0.9f),
                )
                stars.forEach { (x, y, r) ->
                    drawCircle(Color(0xFFE3B25C).copy(alpha = 0.6f), r.dp.toPx(), Offset(x * size.width, y * size.height))
                }
            }
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "DAILY CHALLENGE",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.8.sp,
                        color = Color(0xFFE3B25C),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Twelve questions.\nOne bright start.",
                        fontFamily = FrauncesFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 23.sp,
                        lineHeight = 27.sp,
                        color = Color(0xFFFFF7EA),
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "Mixed modes · bonus XP",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xBFFFF7EA),
                    )
                }
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Color(0xFFE3B25C)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.ArrowForward, "Start daily challenge", Modifier.size(23.dp), tint = Color(0xFF12202B))
                }
            }
        }
    }
}

@Composable
private fun NextSessionCard(
    stats: LearningStats,
    picks: List<WordSummary>,
    onLearn: () -> Unit,
    onPractice: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val waiting = stats.dueNow + stats.newAvailable
    SurfaceCard {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "YOUR NEXT SESSION",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.6.sp,
                        color = colors.brass,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (waiting > 0) "A few words are waiting" else "Everything is tended",
                        fontFamily = FrauncesFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 23.sp,
                        color = colors.ink,
                    )
                }
                if (picks.isNotEmpty()) {
                    Row {
                        picks.take(3).forEachIndexed { index, word ->
                            Box(
                                Modifier
                                    .offset(x = (-12 * index).dp)
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(colors.raised),
                            ) {
                                WordImage(word.image, Modifier.fillMaxSize(), corner = 20.dp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (waiting > 0) {
                    "${formatNumber(stats.dueNow)} ready to revisit · ${plural(stats.newAvailable, "new word")} in your range"
                } else {
                    "Reviews are scheduled. A free practice set is a lovely way to keep the thread."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkSoft,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (waiting > 0) {
                    PrimaryButton("Begin a session", onClick = onLearn, modifier = Modifier.fillMaxWidth())
                } else {
                    PrimaryButton("Open practice", onClick = onPractice, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun TopicCard(topic: TopicProgress, onClick: () -> Unit) {
    val colors = LocalVerbiColors.current
    Box(
        Modifier
            .width(142.dp)
            .height(168.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
    ) {
        WordImage(topic.topic.art, Modifier.fillMaxSize(), corner = 22.dp)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x11000000), Color(0xAA0B1622)))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(13.dp)) {
            Text(
                topic.topic.label,
                fontFamily = FrauncesFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                color = Color(0xFFFFF7EA),
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${topic.seen}/${topic.total}",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = Color(0xBFFFF7EA),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(Color(0x33FFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.ChevronRight, null, Modifier.size(14.dp), tint = Color(0xFFFFF7EA))
                }
            }
        }
    }
}

@Composable
private fun GalaxyInvitation(onClick: () -> Unit) {
    val colors = LocalVerbiColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
    ) {
        WordImage("atlas/brand/night.webp", Modifier.fillMaxSize(), corner = 26.dp)
        Box(Modifier.fillMaxSize().background(Color(0x6608131D)))
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color(0xE608131D), Color(0x5508131D)))
            )
        )
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VerbiMark(Modifier.size(22.dp), color = Color(0xFFFFF7EA), accent = Color(0xFFE3B25C))
                Spacer(Modifier.width(8.dp))
                Text(
                    "THE WORD GALAXY",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.8.sp,
                    color = Color(0xFFE3B25C),
                )
            }
            Column {
                Text(
                    "7,298 words,\nconstellations of meaning.",
                    fontFamily = FrauncesFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    color = Color(0xFFFFF7EA),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Wander the map",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = Color(0xFFFFF7EA),
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.ArrowForward, null, Modifier.size(16.dp), tint = Color(0xFFE3B25C))
                }
            }
        }
    }
}
