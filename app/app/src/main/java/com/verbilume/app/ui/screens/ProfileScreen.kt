package com.verbilume.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.verbilume.app.BuildConfig
import com.verbilume.app.data.AppGraph
import com.verbilume.app.data.AppSettings
import com.verbilume.app.data.LearningStats
import com.verbilume.app.ui.LoadingField
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.PaperBackground
import com.verbilume.app.ui.ProgressRing
import com.verbilume.app.ui.VerbiMark
import com.verbilume.app.ui.formatNumber
import com.verbilume.app.ui.graphViewModel
import com.verbilume.app.ui.initials
import com.verbilume.app.ui.plural
import com.verbilume.app.ui.theme.FrauncesFamily
import com.verbilume.app.ui.theme.ManropeFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val stats: LearningStats? = null,
    val achievements: Int = 0,
    val achievementTotal: Int = 0,
    val bookmarks: Int = 0,
)

class ProfileViewModel(private val graph: AppGraph) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(graph.settings.settings, graph.learning.dataVersion) { settings, _ -> settings }
                .collect { settings ->
                    runCatching {
                        val stats = graph.learning.stats(settings)
                        val achievements = graph.learning.achievements()
                        val bookmarks = graph.learning.bookmarks()
                        ProfileUiState(
                            loading = false,
                            stats = stats,
                            achievements = achievements.count { it.unlocked },
                            achievementTotal = achievements.size,
                            bookmarks = bookmarks.size,
                        )
                    }.onSuccess { _state.value = it }
                }
        }
    }
}

@Composable
fun ProfileScreen(
    graph: AppGraph,
    settings: AppSettings,
    onStats: () -> Unit,
    onAchievements: () -> Unit,
    onBookmarks: () -> Unit,
    onTyping: () -> Unit,
    onOffline: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val context = LocalContext.current
    val viewModel = graphViewModel { ProfileViewModel(graph) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats = state.stats
    val name = settings.displayName.ifBlank { "Wanderer" }

    fun shareApp() {
        val text = "Verbilume — a visual dictionary and spaced-repetition practice for English. Every word, a little light.\nhttps://play.google.com/store/apps/details?id=com.verbilume.app"
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "Share Verbilume",
            )
        )
    }

    fun rateApp() {
        val packageName = "com.verbilume.app"
        val opened = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        }.isSuccess || runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        }.isSuccess
        if (!opened) Toast.makeText(context, "No app store or browser is available on this device.", Toast.LENGTH_SHORT).show()
    }

    PaperBackground {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 128.dp + navBarBottom),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VerbiMark(Modifier.size(26.dp), color = colors.ink, accent = colors.accent)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "YOUR JOURNEY",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 3.4.sp,
                        color = colors.ink,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(colors.raised)
                            .border(1.dp, colors.line, CircleShape)
                            .clickable(onClick = onSettings),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Settings, "Settings", tint = colors.ink, modifier = Modifier.size(20.dp))
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        ProgressRing(
                            fraction = stats?.let { if (it.xpForNextLevel <= 0) 0f else it.xpIntoLevel.toFloat() / it.xpForNextLevel } ?: 0f,
                            modifier = Modifier.size(88.dp),
                            stroke = 4.dp,
                            color = colors.accent,
                        )
                        Box(
                            Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(colors.ink, colors.ink.copy(alpha = 0.76f)))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                initials(name),
                                fontFamily = FrauncesFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 28.sp,
                                color = colors.paper,
                            )
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            name,
                            fontFamily = FrauncesFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 30.sp,
                            color = colors.ink,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Level ${settings.levelLabel} · ${if (stats != null) "XP rank ${stats.xpLevel}" else "finding your footing"}",
                            fontFamily = ManropeFamily,
                            fontSize = 13.5.sp,
                            color = colors.inkSoft,
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.brass.copy(alpha = 0.11f))
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                        ) {
                            Text(
                                if (stats != null) "${formatNumber(stats.totalXp)} XP earned" else "— XP",
                                fontFamily = ManropeFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = colors.brass,
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileStat("Streak", stats?.let { "${it.streak}d" } ?: "—", "best ${stats?.longestStreak ?: 0}d", Modifier.weight(1f), colors.accent)
                    ProfileStat("Mastered", stats?.let { formatNumber(it.masteredWords) } ?: "—", "of 7,298", Modifier.weight(1f), colors.brass)
                    ProfileStat("Reviewed", stats?.let { formatNumber(it.totalReviews) } ?: "—", "all time", Modifier.weight(1f), colors.teal)
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.raised)
                        .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                        .padding(16.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "RANK ${stats?.xpLevel ?: 1}",
                                fontFamily = ManropeFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                letterSpacing = 2.sp,
                                color = colors.inkFaint,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${stats?.xpIntoLevel ?: 0} / ${stats?.xpForNextLevel ?: 100} XP",
                                fontFamily = ManropeFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                                color = colors.inkSoft,
                            )
                        }
                        Spacer(Modifier.height(9.dp))
                        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(colors.line)) {
                            Box(
                                Modifier
                                    .fillMaxWidth(stats?.let { if (it.xpForNextLevel <= 0) 0f else (it.xpIntoLevel.toFloat() / it.xpForNextLevel).coerceIn(0f, 1f) } ?: 0f)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Brush.horizontalGradient(listOf(colors.accent, colors.brassBright)))
                            )
                        }
                        Spacer(Modifier.height(9.dp))
                        Text(
                            stats?.let { "${plural(it.daysStudied, "day")} studied · ${"%.0f".format(it.accuracy30 * 100)}% recall over 30 days" }
                                ?: "Your study record will appear here.",
                            fontFamily = ManropeFamily,
                            fontSize = 13.sp,
                            color = colors.inkFaint,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(26.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickTile("Insights", Icons.Rounded.BarChart, colors.teal, Modifier.weight(1f), onStats)
                    QuickTile("Trophies", Icons.Rounded.EmojiEvents, colors.brass, Modifier.weight(1f), onAchievements)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickTile("Saved", Icons.Rounded.Bookmark, colors.accent, Modifier.weight(1f), onBookmarks)
                    QuickTile("Typing", Icons.Rounded.Keyboard, colors.inkSoft, Modifier.weight(1f), onTyping)
                }
            }

            item {
                Spacer(Modifier.height(22.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.raised)
                        .border(1.dp, colors.line, RoundedCornerShape(24.dp)),
                ) {
                    MenuRow("Your library", "Saved words, achievements and records", Icons.Rounded.Star) { onAchievements() }
                    MenuDivider()
                    MenuRow("Offline atlas", "All 7,298 words, audio included", Icons.Rounded.CloudOff) { onOffline() }
                    MenuDivider()
                    MenuRow("Settings", "Goal, level, reminders and appearance", Icons.Rounded.Settings) { onSettings() }
                    MenuDivider()
                    MenuRow("Share Verbilume", "Send a little light to a friend", Icons.Rounded.Share, action = ::shareApp)
                    MenuDivider()
                    MenuRow("Rate on Google Play", "Tell us how the atlas feels", Icons.Rounded.Favorite, action = ::rateApp)
                    MenuDivider()
                    MenuRow("About Verbilume", "Story, attribution and licences", Icons.Rounded.Info) { onAbout() }
                }
            }

            item {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Verbilume ${BuildConfig.VERSION_NAME}",
                        fontFamily = ManropeFamily,
                        fontSize = 12.5.sp,
                        color = colors.inkFaint,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${state.achievements}/${state.achievementTotal} achievements · ${state.bookmarks} saved",
                        fontFamily = ManropeFamily,
                        fontSize = 12.5.sp,
                        color = colors.inkFaint,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Vocabulary, illustrations and audio from the Word Atlas open collection, licensed CC BY 4.0.",
                    fontFamily = ManropeFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = colors.inkFaint.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun ProfileStat(label: String, value: String, cap: String, modifier: Modifier, tint: Color) {
    val colors = LocalVerbiColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.raised)
            .border(1.dp, colors.line, RoundedCornerShape(20.dp))
            .padding(vertical = 14.dp, horizontal = 13.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
        Spacer(Modifier.height(9.dp))
        Text(
            value,
            fontFamily = FrauncesFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            color = colors.ink,
            maxLines = 1,
        )
        Text(
            label,
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = colors.inkSoft,
        )
        Text(
            cap,
            fontFamily = ManropeFamily,
            fontSize = 12.sp,
            color = colors.inkFaint,
        )
    }
}

@Composable
private fun QuickTile(
    title: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.raised)
            .border(1.dp, colors.line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Rounded.ArrowForward, null, tint = colors.inkFaint, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun MenuRow(
    title: String,
    caption: String,
    icon: ImageVector,
    action: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = action).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(colors.sunken.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = colors.inkSoft, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontFamily = ManropeFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp,
                color = colors.ink,
            )
            Text(
                caption,
                fontFamily = ManropeFamily,
                fontSize = 12.5.sp,
                color = colors.inkFaint,
            )
        }
        Icon(Icons.Rounded.ArrowForward, null, tint = colors.inkFaint, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun MenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(LocalVerbiColors.current.line.copy(alpha = 0.6f))
    )
}
