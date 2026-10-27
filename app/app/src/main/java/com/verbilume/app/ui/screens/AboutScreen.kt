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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbilume.app.BuildConfig
import com.verbilume.app.data.AppGraph
import com.verbilume.app.data.ContentMeta
import com.verbilume.app.ui.PageTopBar
import com.verbilume.app.ui.PaperBackground
import com.verbilume.app.ui.VerbiMark
import com.verbilume.app.ui.formatNumber
import com.verbilume.app.ui.theme.FrauncesFamily
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.theme.ManropeFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AboutScreen(
    graph: AppGraph,
    onBack: () -> Unit,
    onPrivacy: () -> Unit,
    onLicenses: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val context = LocalContext.current
    val meta by produceState<ContentMeta?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { runCatching { graph.content.meta }.getOrNull() }
    }

    fun share() {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Verbilume — a visual dictionary and spaced-repetition practice for English. Every word, a little light.\nhttps://play.google.com/store/apps/details?id=com.verbilume.app",
                    )
                },
                "Share Verbilume",
            )
        )
    }

    fun rate() {
        val opened = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.verbilume.app")))
        }.isSuccess || runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.verbilume.app")))
        }.isSuccess
        if (!opened) Toast.makeText(context, "No app store or browser is available on this device.", Toast.LENGTH_SHORT).show()
    }

    PaperBackground {
        Column(Modifier.fillMaxSize()) {
            PageTopBar("About", onBack = onBack)
            LazyColumn(
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Spacer(Modifier.height(10.dp))
                    VerbiMark(Modifier.size(76.dp), color = colors.ink, accent = colors.accent)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Verbilume",
                        fontFamily = FrauncesFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 42.sp,
                        letterSpacing = (-1).sp,
                        color = colors.ink,
                    )
                    Text(
                        "Every word, a little light.",
                        fontFamily = FrauncesFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 16.sp,
                        color = colors.accent,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(colors.sunken)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            "VERSION ${BuildConfig.VERSION_NAME}",
                            fontFamily = ManropeFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            letterSpacing = 1.4.sp,
                            color = colors.inkFaint,
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(
                        "A field guide for the words you meet. Verbilume pairs a hand-arranged visual dictionary with a serious memory model, so each word returns exactly when it is about to fade.",
                        fontFamily = ManropeFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = colors.inkSoft,
                        textAlign = TextAlign.Justify,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(24.dp))
                }

                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FeaturePill(if (meta != null) formatNumber(meta!!.wordCount) else "7,298", "words", Icons.Rounded.AutoAwesome, colors.accent, Modifier.weight(1f))
                        FeaturePill(if (meta != null) formatNumber(meta!!.audioCount) else "22,905", "clips", Icons.Rounded.Headphones, colors.teal, Modifier.weight(1f))
                        FeaturePill("FSRS-6", "scheduler", Icons.Rounded.Psychology, colors.brass, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(22.dp))
                }

                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.raised)
                            .border(1.dp, colors.line, RoundedCornerShape(24.dp))
                            .padding(18.dp),
                    ) {
                        Text(
                            "THE IDEA",
                            fontFamily = ManropeFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 2.sp,
                            color = colors.brass,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Most vocabulary apps optimise for streaks. Verbilume optimises for remembering — a quiet place to look at a word, hear it, use it, and let the algorithm decide when you should meet it again.",
                            fontFamily = FrauncesFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp,
                            lineHeight = 26.sp,
                            color = colors.ink,
                            textAlign = TextAlign.Justify,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "The atlas is drawn as a night sky. Pan through the constellations, follow a topic, or simply open the day's word. No account, no advertising, no connection required.",
                            fontFamily = ManropeFamily,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = colors.inkSoft,
                            textAlign = TextAlign.Justify,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                }

                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(colors.raised)
                            .border(1.dp, colors.line, RoundedCornerShape(22.dp)),
                    ) {
                        AboutRow("Privacy policy", "What stays on your device", Icons.Rounded.Lock, onPrivacy)
                        AboutDivider()
                        AboutRow("Licences & attribution", "Open knowledge, properly credited", Icons.Rounded.Gavel, onLicenses)
                        AboutDivider()
                        AboutRow("Share Verbilume", "Send a little light to a friend", Icons.Rounded.Share, ::share)
                        AboutDivider()
                        AboutRow("Rate on Google Play", "Tell us how the atlas feels", Icons.Rounded.Favorite, ::rate)
                    }
                    Spacer(Modifier.height(22.dp))
                }

                item {
                    Text(
                        "Vocabulary, definitions, example sentences and illustrations from the Word Atlas open collection, licensed CC BY 4.0. Pronunciation audio generated with ElevenLabs voices. Illustrations and text in the source collection were produced with AI assistance and checked automatically.",
                        fontFamily = ManropeFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = colors.inkFaint,
                        textAlign = TextAlign.Justify,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Made for quiet, curious minds.",
                        fontFamily = FrauncesFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 14.sp,
                        color = colors.inkFaint,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturePill(value: String, label: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    val colors = LocalVerbiColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.raised)
            .border(1.dp, colors.line, RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            value,
            fontFamily = FrauncesFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = colors.ink,
            maxLines = 1,
        )
        Text(
            label,
            fontFamily = ManropeFamily,
            fontSize = 12.5.sp,
            color = colors.inkFaint,
        )
    }
}

@Composable
private fun AboutRow(title: String, caption: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = LocalVerbiColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(colors.sunken.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = colors.inkSoft, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink)
            Text(caption, fontFamily = ManropeFamily, fontSize = 12.5.sp, color = colors.inkFaint)
        }
        Icon(Icons.Rounded.ArrowForward, null, tint = colors.inkFaint, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun AboutDivider() {
    val colors = LocalVerbiColors.current
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(colors.line.copy(alpha = 0.55f)))
}
