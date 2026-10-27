package com.verbilume.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Translate
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbilume.app.data.AppGraph
import com.verbilume.app.data.ContentMeta
import com.verbilume.app.ui.LoadingField
import com.verbilume.app.ui.PageTopBar
import com.verbilume.app.ui.PaperBackground
import com.verbilume.app.ui.PrimaryButton
import com.verbilume.app.ui.formatNumber
import com.verbilume.app.ui.theme.FrauncesFamily
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.theme.ManropeFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun OfflineScreen(
    graph: AppGraph,
    onBack: () -> Unit,
    onBrowse: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val meta by produceState<ContentMeta?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { runCatching { graph.content.meta }.getOrNull() }
    }

    PaperBackground {
        Column(Modifier.fillMaxSize()) {
            PageTopBar("Offline atlas", onBack = onBack)
            if (meta == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingField() }
                return@Column
            }
            val content = meta!!
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Text(
                        "Everything is\nalready here.",
                        fontFamily = FrauncesFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 32.sp,
                        lineHeight = 36.sp,
                        letterSpacing = (-0.6).sp,
                        color = colors.ink,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "The complete dictionary, every illustration and every recording ship inside the app. There are no packs to download and nothing expires.",
                        fontFamily = ManropeFamily,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = colors.inkSoft,
                        textAlign = TextAlign.Justify,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                }

                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.ink)
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(colors.paper.copy(alpha = 0.09f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.OfflinePin, null, tint = colors.brassBright, modifier = Modifier.size(23.dp))
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "AVAILABLE OFFLINE",
                                fontFamily = ManropeFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 1.8.sp,
                                color = colors.brassBright,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Airplane mode is welcome.",
                                fontFamily = FrauncesFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 18.sp,
                                color = colors.paper,
                            )
                            Text(
                                "Content version ${content.version.ifBlank { "1" }} · no network permission",
                                fontFamily = ManropeFamily,
                                fontSize = 12.5.sp,
                                color = colors.paper.copy(alpha = 0.48f),
                            )
                        }
                        Icon(Icons.Rounded.CloudOff, null, tint = colors.paper.copy(alpha = 0.35f), modifier = Modifier.size(20.dp))
                    }
                }

                item { Spacer(Modifier.height(4.dp)) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LibraryStat("Words", formatNumber(content.wordCount), Icons.Rounded.Translate, colors.accent, Modifier.weight(1f))
                        LibraryStat("Illustrations", formatNumber(content.wordCount), Icons.Rounded.Image, colors.brass, Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LibraryStat("Audio clips", formatNumber(content.audioCount), Icons.Rounded.Headphones, colors.teal, Modifier.weight(1f))
                        LibraryStat("Examples", formatNumber(content.exampleCount), Icons.Rounded.Translate, colors.inkSoft, Modifier.weight(1f))
                    }
                }

                item {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "EVERY REALM INCLUDED",
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        letterSpacing = 2.sp,
                        color = colors.brass,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }

                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(colors.raised)
                            .border(1.dp, colors.line, RoundedCornerShape(22.dp)),
                    ) {
                        content.topics.forEachIndexed { index, topic ->
                            if (index > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .height(1.dp)
                                        .background(colors.line.copy(alpha = 0.55f))
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(topic.color)))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    topic.label,
                                    fontFamily = ManropeFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colors.ink,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${formatNumber(topic.count)} words",
                                    fontFamily = ManropeFamily,
                                    fontSize = 12.5.sp,
                                    color = colors.inkFaint,
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Rounded.CheckCircle, null, tint = colors.teal, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton("Browse the atlas", onClick = onBrowse, modifier = Modifier.fillMaxWidth())
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Illustrations, vocabulary and example sentences come from the Word Atlas open collection, licensed CC BY 4.0. Pronunciation audio is generated with ElevenLabs voices and bundled for offline study.",
                        fontFamily = ManropeFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        color = colors.inkFaint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryStat(label: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    val colors = LocalVerbiColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.raised)
            .border(1.dp, colors.line, RoundedCornerShape(20.dp))
            .padding(15.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(10.dp))
        Text(
            value,
            fontFamily = FrauncesFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            color = colors.ink,
        )
        Text(
            label,
            fontFamily = ManropeFamily,
            fontSize = 13.sp,
            color = colors.inkFaint,
        )
    }
}
