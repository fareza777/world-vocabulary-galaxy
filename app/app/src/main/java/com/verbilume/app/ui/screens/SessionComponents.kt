package com.verbilume.app.ui.screens

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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbilume.app.data.AchievementDef
import com.verbilume.app.ui.Confetti
import com.verbilume.app.ui.PaperBackground
import com.verbilume.app.ui.PrimaryButton
import com.verbilume.app.ui.SecondaryButton
import com.verbilume.app.ui.VerbiMark
import com.verbilume.app.ui.theme.FrauncesFamily
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.theme.ManropeFamily

@Composable
fun SessionTopBar(
    title: String,
    progress: Float,
    counter: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalVerbiColors.current
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(colors.raised).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, "Close session", tint = colors.ink, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title.uppercase(),
                fontFamily = ManropeFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                color = colors.inkFaint,
            )
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)).background(colors.line)) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(colors.accent, colors.brassBright)))
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            counter,
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.5.sp,
            color = colors.inkSoft,
        )
    }
}

@Composable
fun SessionSummary(
    eyebrow: String,
    headline: String,
    message: String,
    reviewed: Int,
    correct: Int,
    xp: Int,
    seconds: Int,
    streak: Int,
    achievements: List<AchievementDef>,
    bonusXp: Int = 0,
    celebrate: Boolean = true,
    onDone: () -> Unit,
    onAgain: (() -> Unit)? = null,
    againLabel: String = "Go again",
) {
    val colors = LocalVerbiColors.current
    Box(Modifier.fillMaxSize()) {
        PaperBackground {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 26.dp)
                    .padding(top = 26.dp, bottom = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                VerbiMark(Modifier.size(74.dp), color = colors.ink, accent = colors.accent)
                Spacer(Modifier.height(26.dp))
                Text(
                    eyebrow.uppercase(),
                    fontFamily = ManropeFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    letterSpacing = 2.8.sp,
                    color = colors.accent,
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    headline,
                    fontFamily = FrauncesFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 37.sp,
                    lineHeight = 41.sp,
                    letterSpacing = (-0.7).sp,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    message,
                    fontFamily = ManropeFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = colors.inkSoft,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    SummaryStat("Words", "$reviewed", Modifier.weight(1f))
                    SummaryStat("Correct", "$correct", Modifier.weight(1f))
                    SummaryStat("Minutes", if (seconds < 60) "<1" else "${seconds / 60}", Modifier.weight(1f))
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    SummaryStat("XP earned", "+${xp + bonusXp}", Modifier.weight(1f), accent = true)
                    SummaryStat("Streak", "${streak}d", Modifier.weight(1f))
                }
                if (bonusXp > 0) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Includes a +$bonusXp daily challenge bonus",
                        fontFamily = ManropeFamily,
                        fontSize = 13.sp,
                        color = colors.brass,
                    )
                }

                if (achievements.isNotEmpty()) {
                    Spacer(Modifier.height(26.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(colors.brass.copy(alpha = 0.08f))
                            .border(1.dp, colors.brass.copy(alpha = 0.2f), RoundedCornerShape(22.dp))
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.EmojiEvents, null, tint = colors.brass, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (achievements.size == 1) "A NEW TROPHY" else "${achievements.size} NEW TROPHIES",
                                fontFamily = ManropeFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                letterSpacing = 1.8.sp,
                                color = colors.brass,
                            )
                        }
                        achievements.forEach { achievement ->
                            Spacer(Modifier.height(11.dp))
                            Text(
                                achievement.title,
                                fontFamily = FrauncesFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 18.sp,
                                color = colors.ink,
                            )
                            Text(
                                achievement.description,
                                fontFamily = ManropeFamily,
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                color = colors.inkSoft,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(30.dp))
                PrimaryButton(
                    text = "Return to practice",
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onAgain != null) {
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton(
                        text = againLabel,
                        onClick = onAgain,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "Every review strengthens the memory. Keep the constellation glowing.",
                    fontFamily = ManropeFamily,
                    fontSize = 13.sp,
                    color = colors.inkFaint,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (celebrate) Confetti(trigger = true, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun SummaryStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val colors = LocalVerbiColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (accent) colors.ink else colors.raised)
            .border(1.dp, if (accent) colors.ink else colors.line, RoundedCornerShape(18.dp))
            .padding(vertical = 13.dp, horizontal = 12.dp),
    ) {
        Text(
            value,
            fontFamily = FrauncesFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            color = if (accent) colors.paper else colors.ink,
        )
        Text(
            label,
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.5.sp,
            color = if (accent) colors.paper.copy(alpha = 0.55f) else colors.inkFaint,
        )
    }
}
