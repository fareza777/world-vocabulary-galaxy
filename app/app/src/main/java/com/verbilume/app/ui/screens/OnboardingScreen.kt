package com.verbilume.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.verbilume.app.data.Accent
import com.verbilume.app.data.AppGraph
import com.verbilume.app.data.AppSettings
import com.verbilume.app.ui.LevelBadge
import com.verbilume.app.ui.PaperBackground
import com.verbilume.app.ui.PrimaryButton
import com.verbilume.app.ui.ProgressDots
import com.verbilume.app.ui.VerbiMark
import com.verbilume.app.ui.WordImage
import com.verbilume.app.ui.levelDescription
import com.verbilume.app.ui.levelTitle
import com.verbilume.app.ui.theme.FrauncesFamily
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.theme.ManropeFamily
import kotlinx.coroutines.launch

private data class IntroPage(
    val kicker: String,
    val title: String,
    val body: String,
    val image: String,
)

private val introPages = listOf(
    IntroPage(
        "AN ILLUSTRATED ATLAS",
        "Every word,\na little light.",
        "Explore 7,298 English words, each with its own illustration, pronunciation and example sentence. A dictionary you will actually want to open.",
        "atlas/brand/hero.webp",
    ),
    IntroPage(
        "A LIVING MAP",
        "Wander a galaxy\nmade of language.",
        "Nineteen constellations, one for every corner of life. Zoom through the Word Galaxy and discover the words orbiting each idea.",
        "atlas/brand/night.webp",
    ),
    IntroPage(
        "MEMORY, NOT CRAMMING",
        "Come back for a\nfew quiet minutes.",
        "Our FSRS memory engine notices what is fading and brings it back at the right moment. Gentle reviews, lasting knowledge.",
        "atlas/brand/listen.webp",
    ),
)

private val totalSteps = 6

@Composable
fun OnboardingScreen(
    graph: AppGraph,
    initial: AppSettings,
    onComplete: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var level by rememberSaveable { mutableIntStateOf(initial.level.coerceIn(0, 5)) }
    var goal by rememberSaveable { mutableIntStateOf(initial.dailyGoal) }
    var newWords by rememberSaveable { mutableIntStateOf(initial.dailyNewWords) }
    var name by rememberSaveable { mutableStateOf(initial.displayName) }
    var accent by rememberSaveable { mutableStateOf(initial.accent) }
    var reminders by rememberSaveable { mutableStateOf(initial.remindersEnabled) }
    var reminderHour by rememberSaveable { mutableIntStateOf(initial.reminderHour) }
    var reminderMinute by rememberSaveable { mutableIntStateOf(initial.reminderMinute) }
    var finishing by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            scope.launch {
                graph.settings.update { it.copy(remindersEnabled = false) }
                graph.notifications.cancel()
            }
        }
    }

    fun finish() {
        if (finishing) return
        finishing = true
        scope.launch {
            graph.settings.completeOnboarding(
                name = name,
                level = level,
                goal = goal,
                newWords = newWords,
                accent = accent,
                reminders = reminders,
                reminderHour = reminderHour,
                reminderMinute = reminderMinute,
            )
            if (reminders) {
                if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(
                        graph.context, Manifest.permission.POST_NOTIFICATIONS,
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                graph.notifications.schedule(reminderHour, reminderMinute)
            }
            onComplete()
        }
    }

    PaperBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).clickable { step-- },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.ArrowBack, "Back", tint = colors.ink, modifier = Modifier.size(20.dp))
                    }
                } else {
                    VerbiMark(Modifier.size(28.dp), color = colors.ink, accent = colors.accent)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "VERBILUME",
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    letterSpacing = 3.2.sp,
                    color = colors.ink,
                )
                Spacer(Modifier.weight(1f))
                if (step < 3) {
                    Text(
                        "Skip",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = colors.inkSoft,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { step = 3 }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it / 5 else it / 5 } + fadeOut()) using
                        SizeTransform(clip = false)
                },
                label = "onboardingStep",
                modifier = Modifier.weight(1f).clipToBounds(),
            ) { current ->
                when {
                    current < 3 -> IntroStep(introPages[current])
                    current == 3 -> LevelStep(level) { level = it }
                    current == 4 -> RhythmStep(
                        goal = goal,
                        onGoal = { goal = it },
                        newWords = newWords,
                        onNewWords = { newWords = it },
                        reminders = reminders,
                        onReminders = { reminders = it },
                        reminderHour = reminderHour,
                        reminderMinute = reminderMinute,
                        onTime = { h, m -> reminderHour = h; reminderMinute = m },
                    )
                    else -> IdentityStep(
                        name = name,
                        onName = { name = it.take(24) },
                        accent = accent,
                        onAccent = { accent = it },
                    )
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProgressDots(totalSteps, step)
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = when (step) {
                        0 -> "Begin the journey"
                        in 1..4 -> "Continue"
                        else -> "Enter Verbilume"
                    },
                    onClick = {
                        if (step < totalSteps - 1) step++ else finish()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (step == 4 && reminders) {
                    Text(
                        "We will ask for notification permission when you begin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkFaint,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                } else {
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
private fun IntroStep(page: IntroPage) {
    val colors = LocalVerbiColors.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = (maxHeight * 0.44f).coerceIn(200.dp, 340.dp)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .clip(RoundedCornerShape(30.dp)),
            ) {
                WordImage(page.image, Modifier.fillMaxSize(), corner = 30.dp, contentScale = ContentScale.Crop)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0x11000000), Color(0x33000000)),
                        )
                    )
                )
                VerbiMark(
                    Modifier.align(Alignment.TopEnd).padding(18.dp).size(38.dp),
                    color = Color(0xFFFFF7EA),
                    accent = Color(0xFFE3B25C),
                )
            }
            Spacer(Modifier.height(26.dp))
            Text(
                page.kicker,
                fontFamily = ManropeFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                letterSpacing = 2.2.sp,
                color = colors.brass,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                page.title,
                fontFamily = FrauncesFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 39.sp,
                lineHeight = 43.sp,
                letterSpacing = (-0.7).sp,
                color = colors.ink,
            )
            Spacer(Modifier.height(14.dp))
            Text(page.body, style = MaterialTheme.typography.bodyLarge, color = colors.inkSoft)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LevelStep(selected: Int, onSelect: (Int) -> Unit) {
    val colors = LocalVerbiColors.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Text(
            "CALIBRATE YOUR ATLAS",
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            letterSpacing = 2.2.sp,
            color = colors.brass,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Where are you\nright now?",
            fontFamily = FrauncesFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 36.sp,
            lineHeight = 40.sp,
            color = colors.ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "We will tune new words and reviews around your level. You can change this at any time.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkSoft,
        )
        Spacer(Modifier.height(20.dp))
        for (index in 0..5) {
            val chosen = index == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (chosen) colors.accent.copy(alpha = 0.09f) else colors.raised.copy(alpha = 0.5f))
                    .border(
                        width = if (chosen) 1.6.dp else 1.dp,
                        color = if (chosen) colors.accent else colors.line,
                        shape = RoundedCornerShape(18.dp),
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LevelBadge(levelLabelFor(index), filled = chosen)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(levelTitle(index), fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.ink)
                    Text(levelDescription(index), style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
                }
                if (chosen) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = Color(0xFFFFF7EA), modifier = Modifier.size(15.dp))
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
        }
    }
}

private fun levelLabelFor(index: Int): String = com.verbilume.app.data.CEFR_LEVELS[index]

@Composable
private fun RhythmStep(
    goal: Int,
    onGoal: (Int) -> Unit,
    newWords: Int,
    onNewWords: (Int) -> Unit,
    reminders: Boolean,
    onReminders: (Boolean) -> Unit,
    reminderHour: Int,
    reminderMinute: Int,
    onTime: (Int, Int) -> Unit,
) {
    val colors = LocalVerbiColors.current
    val goals = listOf(
        10 to "A gentle start",
        20 to "A steady habit",
        30 to "The daily ritual",
        50 to "Deep immersion",
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Text(
            "SET YOUR RHYTHM",
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            letterSpacing = 2.2.sp,
            color = colors.brass,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "What does a good\nday look like?",
            fontFamily = FrauncesFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 36.sp,
            lineHeight = 40.sp,
            color = colors.ink,
        )
        Spacer(Modifier.height(8.dp))
        Text("Small and repeatable beats big and occasional.", style = MaterialTheme.typography.bodyMedium, color = colors.inkSoft)
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            goals.take(2).forEach { (count, label) -> GoalCard(count, label, goal == count, Modifier.weight(1f)) { onGoal(count) } }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            goals.drop(2).forEach { (count, label) -> GoalCard(count, label, goal == count, Modifier.weight(1f)) { onGoal(count) } }
        }
        Spacer(Modifier.height(24.dp))
        Text("NEW WORDS EACH DAY", fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, letterSpacing = 1.8.sp, color = colors.inkFaint)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5 to "Light", 8 to "Balanced", 12 to "Ambitious").forEach { (count, label) ->
                val selected = newWords == count
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) colors.ink else colors.raised.copy(alpha = 0.5f))
                        .border(1.dp, if (selected) colors.ink else colors.line, RoundedCornerShape(14.dp))
                        .clickable { onNewWords(count) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("$count", fontFamily = FrauncesFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = if (selected) colors.paper else colors.ink)
                    Text(label, fontFamily = ManropeFamily, fontSize = 12.5.sp, color = if (selected) colors.paper.copy(alpha = 0.7f) else colors.inkFaint)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.raised.copy(alpha = 0.55f))
                .border(1.dp, colors.line, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(colors.brass.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Notifications, null, tint = colors.brass, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("A gentle reminder", fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.ink)
                Text("One thoughtful nudge a day. Never noisy.", style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
            }
            Switch(
                checked = reminders,
                onCheckedChange = onReminders,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFFFF7EA),
                    checkedTrackColor = colors.accent,
                    uncheckedThumbColor = colors.inkFaint,
                    uncheckedTrackColor = colors.sunken,
                ),
            )
        }
        if (reminders) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple(8, 0, "8:00"), Triple(12, 30, "12:30"), Triple(19, 30, "19:30"), Triple(21, 0, "21:00")).forEach { (h, m, label) ->
                    val selected = reminderHour == h && reminderMinute == m
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) colors.brass.copy(alpha = 0.16f) else Color.Transparent)
                            .border(1.dp, if (selected) colors.brass else colors.line, RoundedCornerShape(12.dp))
                            .clickable { onTime(h, m) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = if (selected) colors.brass else colors.inkSoft)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GoalCard(count: Int, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalVerbiColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) colors.accent.copy(alpha = 0.09f) else colors.raised.copy(alpha = 0.5f))
            .border(if (selected) 1.6.dp else 1.dp, if (selected) colors.accent else colors.line, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text("$count", fontFamily = FrauncesFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, color = colors.ink)
        Text("cards a day", fontFamily = ManropeFamily, fontSize = 12.5.sp, letterSpacing = 0.6.sp, color = colors.inkFaint)
        Spacer(Modifier.height(8.dp))
        Text(label, fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (selected) colors.accent else colors.inkSoft)
    }
}

@Composable
private fun IdentityStep(
    name: String,
    onName: (String) -> Unit,
    accent: Accent,
    onAccent: (Accent) -> Unit,
) {
    val colors = LocalVerbiColors.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.size(86.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            VerbiMark(Modifier.size(54.dp), color = colors.ink, accent = colors.accent)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "ONE LAST THING",
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            letterSpacing = 2.2.sp,
            color = colors.brass,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "What should we\ncall you?",
            fontFamily = FrauncesFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 36.sp,
            lineHeight = 40.sp,
            color = colors.ink,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Your name, or leave it blank", color = colors.inkFaint, style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.line,
                focusedContainerColor = colors.raised.copy(alpha = 0.7f),
                unfocusedContainerColor = colors.raised.copy(alpha = 0.4f),
                cursorColor = colors.accent,
                focusedTextColor = colors.ink,
                unfocusedTextColor = colors.ink,
            ),
        )
        Spacer(Modifier.height(26.dp))
        Text(
            "WHICH VOICE WOULD YOU LIKE?",
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            letterSpacing = 1.8.sp,
            color = colors.inkFaint,
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccentCard("British", "Alice · soft and clear", accent == Accent.UK, Modifier.weight(1f)) { onAccent(Accent.UK) }
            AccentCard("American", "Sarah · warm and bright", accent == Accent.US, Modifier.weight(1f)) { onAccent(Accent.US) }
        }
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.ink)
                .padding(20.dp),
        ) {
            Column {
                Text(
                    "A quiet promise",
                    fontFamily = FrauncesFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    color = colors.paper,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Verbilume keeps every word, image and recording on your device. Your progress stays private — no account, and the whole atlas works offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.paper.copy(alpha = 0.72f),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun AccentCard(title: String, subtitle: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalVerbiColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) colors.accent.copy(alpha = 0.09f) else colors.raised.copy(alpha = 0.5f))
            .border(if (selected) 1.6.dp else 1.dp, if (selected) colors.accent else colors.line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(title, fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.ink)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.inkFaint, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
