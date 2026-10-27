package com.verbilume.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbilume.app.ui.PageTopBar
import com.verbilume.app.ui.PaperBackground
import com.verbilume.app.ui.theme.FrauncesFamily
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.theme.ManropeFamily

private data class LegalSection(
    val heading: String,
    val body: String,
    val linkLabel: String? = null,
    val linkUrl: String? = null,
)

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val sections = listOf(
        LegalSection(
            "The short version",
            "Verbilume has no accounts, no analytics, no advertising and no internet permission. Your learning record stays on this device unless you deliberately export it.",
        ),
        LegalSection(
            "What the app stores",
            "On your device, in private app storage, Verbilume keeps: an optional display name; your settings (level, daily goal, appearance, reminders); learning cards and their review schedule; reviews, streaks and XP; saved words; recent searches; and typing speed and accuracy measurements.\n\nThis information exists so the app can teach you. It is not transmitted anywhere.",
        ),
        LegalSection(
            "Network access",
            "Verbilume does not request the Android INTERNET permission. The app cannot send your data over a network, and it does not embed third-party analytics, advertising or crash-reporting SDKs.\n\nThe Google Play store may independently collect installation and diagnostic information under Google's own privacy policy. Verbilume does not receive or link that information to your learning record.",
        ),
        LegalSection(
            "Notifications",
            "Daily reminders are scheduled locally by Android WorkManager. They contain no personal data and are optional. You can turn reminders off at any time in Settings, or revoke the notification permission in Android settings.",
        ),
        LegalSection(
            "Backups and export",
            "Progress export is manual. When you choose Export, Verbilume writes a JSON file to the destination you pick through Android's document picker. You control where that file goes and who can read it.\n\nWhen you import a backup, it replaces the learning record currently stored in the app. Verbilume never sees or uploads either file.",
        ),
        LegalSection(
            "Dictionary content",
            "The vocabulary, definitions, example sentences, illustrations and audio are bundled with the app and come from the Word Atlas open collection. This content contains no personal data. See Licences & attribution for details.",
        ),
        LegalSection(
            "Children",
            "Verbilume is suitable for general audiences. It has no sign-in, no social features, no purchases, no advertising and no data collection, so there is nothing for a child to share.",
        ),
        LegalSection(
            "Your choices",
            "You can edit your display name, disable reminders, clear your name, or erase all learning progress inside the app. Uninstalling Verbilume deletes the app's private storage, including your progress and settings. Exported backup files remain wherever you saved them.",
        ),
        LegalSection(
            "Changes",
            "If this policy changes, the updated text will ship with a new version of the app. The effective date will change accordingly.",
        ),
        LegalSection(
            "Contact",
            "Questions about privacy can be sent through the developer contact address listed on Verbilume's Google Play store page.",
        ),
    )
    LegalPage(
        title = "Privacy policy",
        subtitle = "Nothing leaves this device.",
        updated = "Effective 26 October 2026",
        sections = sections,
        onBack = onBack,
    )
}

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val mit = """
        MIT License

        Copyright (c) 2026 Word Atlas contributors

        Permission is hereby granted, free of charge, to any person obtaining a copy
        of this software and associated documentation files (the "Software"), to deal
        in the Software without restriction, including without limitation the rights
        to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
        copies of the Software, and to permit persons to whom the Software is
        furnished to do so, subject to the following conditions:

        The above copyright notice and this permission notice shall be included in all
        copies or substantial portions of the Software.

        THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
        IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
        FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
        AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
        LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
        OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
        SOFTWARE.
    """.trimIndent()

    val sections = listOf(
        LegalSection(
            "Word Atlas content",
            "Vocabulary, English definitions, example sentences, collocations, word families, synonyms, antonyms, illustrations and recordings are offered under Creative Commons Attribution 4.0 International (CC BY 4.0), by Word Atlas contributors, to the extent of the rights those contributors can grant.\n\nAttribution: Word Atlas contributors — CC BY 4.0.\n\nChanges for this app: the material was reorganized and re-indexed for offline Android delivery, and the interface presents English only. Illustrations, audio recordings and English definitions were not rewritten or altered. The source project is at github.com/designfuchen/word-atlas.",
            "Read the CC BY 4.0 licence",
            "https://creativecommons.org/licenses/by/4.0/",
        ),
        LegalSection(
            "Application code — MIT",
            "Portions of Verbilume are derived from Word Atlas, Copyright (c) 2026 Word Atlas contributors, licensed under the MIT License reproduced below.\n\n$mit",
        ),
        LegalSection(
            "Pronunciation recordings",
            "Audio was synthesized with ElevenLabs voices: “Alice” for British English and “Sarah” for American English. The CC BY 4.0 grant covers these finished audio files only. It does not cover the underlying voice models or voice identities, and it does not imply that ElevenLabs or the voice providers endorse this project.",
        ),
        LegalSection(
            "Artificial intelligence",
            "The source collection's text and illustrations were produced with AI models under the project's specifications and checked automatically; they were not reviewed entry by entry. They are provided as-is without any warranty of accuracy. Verbilume is not an official IELTS word list and is not affiliated with the IELTS partners.",
        ),
        LegalSection(
            "Typefaces",
            "Fraunces and Manrope are used under the SIL Open Font License 1.1. Both typefaces remain the property of their respective authors.",
            "Read the SIL Open Font License",
            "https://openfontlicense.org/",
        ),
        LegalSection(
            "Software libraries",
            "Verbilume is built with Kotlin, Jetpack Compose, AndroidX Lifecycle and Navigation, DataStore, WorkManager and Coil. These libraries are distributed under the Apache License 2.0 or their respective open-source licences. Their copyright notices remain with their authors.",
        ),
    )
    LegalPage(
        title = "Licences",
        subtitle = "Open knowledge, properly credited.",
        updated = "Verbilume 1.0.0",
        sections = sections,
        onBack = onBack,
    )
}

@Composable
private fun LegalPage(
    title: String,
    subtitle: String,
    updated: String,
    sections: List<LegalSection>,
    onBack: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val uriHandler = LocalUriHandler.current
    PaperBackground {
        Column(Modifier.fillMaxSize()) {
            PageTopBar(title, onBack = onBack)
            LazyColumn(
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 48.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Text(
                        subtitle,
                        fontFamily = FrauncesFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 30.sp,
                        lineHeight = 35.sp,
                        letterSpacing = (-0.5).sp,
                        color = colors.ink,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Text(
                        updated.uppercase(),
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        letterSpacing = 1.8.sp,
                        color = colors.accent,
                    )
                    Spacer(Modifier.height(20.dp))
                }
                sections.forEach { section ->
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(colors.raised)
                                .border(1.dp, colors.line, RoundedCornerShape(20.dp))
                                .padding(17.dp),
                        ) {
                            Text(
                                section.heading,
                                fontFamily = ManropeFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = colors.ink,
                            )
                            Spacer(Modifier.height(7.dp))
                            SelectionContainer {
                                Text(
                                    section.body,
                                    fontFamily = ManropeFamily,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = colors.inkSoft,
                                    textAlign = TextAlign.Justify,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (section.linkLabel != null && section.linkUrl != null) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    section.linkLabel,
                                    fontFamily = ManropeFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = colors.accent,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier.clickable { uriHandler.openUri(section.linkUrl) },
                                )
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Questions about licensing can be sent through the developer contact address on Verbilume's Google Play store page.",
                        fontFamily = ManropeFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = colors.inkFaint,
                        textAlign = TextAlign.Justify,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
