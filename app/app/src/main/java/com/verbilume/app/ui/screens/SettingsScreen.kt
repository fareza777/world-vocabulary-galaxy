package com.verbilume.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.verbilume.app.data.Accent
import com.verbilume.app.data.AppGraph
import com.verbilume.app.data.AppSettings
import com.verbilume.app.data.ThemeMode
import com.verbilume.app.data.TopicInfo
import com.verbilume.app.ui.PageTopBar
import com.verbilume.app.ui.PaperBackground
import com.verbilume.app.ui.formatNumber
import com.verbilume.app.ui.theme.FrauncesFamily
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.theme.ManropeFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    graph: AppGraph,
    settings: AppSettings,
    onBack: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showName by remember { mutableStateOf(false) }
    var showTopics by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }

    val topics by produceState(initialValue = emptyList<TopicInfo>()) {
        value = withContext(Dispatchers.IO) { runCatching { graph.content.meta.topics }.getOrDefault(emptyList()) }
    }

    fun save(transform: (AppSettings) -> AppSettings) {
        scope.launch { graph.settings.update(transform) }
    }

    fun setReminders(enabled: Boolean) {
        scope.launch {
            graph.settings.update { it.copy(remindersEnabled = enabled) }
            if (enabled) graph.notifications.schedule(settings.reminderHour, settings.reminderMinute)
            else graph.notifications.cancel()
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) setReminders(true)
        else Toast.makeText(context, "Notification permission is needed for reminders.", Toast.LENGTH_SHORT).show()
    }

    fun toggleReminders(enabled: Boolean) {
        if (!enabled) {
            setReminders(false)
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else setReminders(true)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val json = withContext(Dispatchers.IO) { graph.learning.exportJson() }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("The destination could not be opened.")
                }
            }
            Toast.makeText(
                context,
                if (result.isSuccess) "Progress exported." else "Could not export progress.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("The selected file could not be read.")
                }.getOrNull()
            }
            when {
                text == null -> Toast.makeText(context, "That file could not be opened.", Toast.LENGTH_LONG).show()
                !withContext(Dispatchers.IO) { graph.learning.isValidBackup(text) } ->
                    Toast.makeText(context, "That file is not a valid Verbilume backup.", Toast.LENGTH_LONG).show()
                else -> pendingImport = text
            }
        }
    }

    fun applyTime(hour: Int, minute: Int) {
        scope.launch {
            graph.settings.update { it.copy(reminderHour = hour, reminderMinute = minute) }
            if (settings.remindersEnabled) graph.notifications.schedule(hour, minute)
        }
    }

    PaperBackground {
        Column(Modifier.fillMaxSize()) {
            PageTopBar("Settings", onBack = onBack)
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Text(
                        "Make the atlas\nyours.",
                        fontFamily = FrauncesFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 32.sp,
                        lineHeight = 36.sp,
                        letterSpacing = (-0.6).sp,
                        color = colors.ink,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Changes apply immediately. Your learning record never leaves this device unless you export it.",
                        fontFamily = ManropeFamily,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = colors.inkSoft,
                        textAlign = TextAlign.Justify,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                }

                item { SectionLabel("IDENTITY") }
                item {
                    SettingsCard {
                        ActionRow(
                            title = settings.displayName.ifBlank { "Wanderer" },
                            caption = "Display name",
                            onClick = { showName = true },
                            trailing = settings.displayName.ifBlank { "Set name" },
                        )
                    }
                }

                item { SectionLabel("LEARNING") }
                item {
                    SettingsCard {
                        Column(Modifier.padding(16.dp)) {
                            Text("YOUR LEVEL", fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.6.sp, color = colors.inkFaint)
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("A1", "A2", "B1", "B2", "C1", "C2").forEachIndexed { index, label ->
                                    ChoicePill(
                                        label = label,
                                        selected = settings.level == index,
                                        modifier = Modifier.weight(1f),
                                        horizontalPadding = 6.dp,
                                    ) { save { it.copy(level = index) } }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "New words are drawn from this level, with a little review from the one below.",
                                fontFamily = ManropeFamily,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                color = colors.inkFaint,
                            )
                        }
                        CardDivider()
                        Column(Modifier.padding(16.dp)) {
                            Text("DAILY REVIEW GOAL", fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.6.sp, color = colors.inkFaint)
                            Spacer(Modifier.height(10.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                listOf(10, 20, 30, 50).forEach { goal ->
                                    ChoicePill("$goal", settings.dailyGoal == goal) { save { it.copy(dailyGoal = goal) } }
                                }
                            }
                        }
                        CardDivider()
                        Column(Modifier.padding(16.dp)) {
                            Text("NEW WORDS EACH DAY", fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.6.sp, color = colors.inkFaint)
                            Spacer(Modifier.height(10.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                listOf(5, 8, 12).forEach { count ->
                                    ChoicePill("$count", settings.dailyNewWords == count) { save { it.copy(dailyNewWords = count) } }
                                }
                            }
                        }
                        CardDivider()
                        ActionRow(
                            title = "Preferred realms",
                            caption = if (settings.selectedTopics.isEmpty()) "All ${topics.size} realms included" else "${settings.selectedTopics.size} of ${topics.size} realms",
                            onClick = { showTopics = true },
                            trailing = "Choose",
                        )
                        CardDivider()
                        SettingSwitch(
                            title = "Include mastered words",
                            caption = "Let words you have mastered return to reviews",
                            checked = settings.includeMastered,
                            onCheckedChange = { value -> save { it.copy(includeMastered = value) } },
                        )
                    }
                }

                item { SectionLabel("SOUND & TOUCH") }
                item {
                    SettingsCard {
                        Column(Modifier.padding(16.dp)) {
                            Text("VOICE ACCENT", fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.6.sp, color = colors.inkFaint)
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.sunken)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                listOf(Accent.UK to "British · Alice", Accent.US to "American · Sarah").forEach { (accent, label) ->
                                    SegmentOption(label, settings.accent == accent, Modifier.weight(1f)) {
                                        save { it.copy(accent = accent) }
                                    }
                                }
                            }
                        }
                        CardDivider()
                        SettingSwitch(
                            title = "Pronunciation audio",
                            caption = "Play word and sentence audio",
                            checked = settings.soundEnabled,
                            onCheckedChange = { value -> save { it.copy(soundEnabled = value) } },
                        )
                        CardDivider()
                        SettingSwitch(
                            title = "Autoplay on reveal",
                            caption = "Hear words as soon as they appear",
                            checked = settings.autoplayAudio,
                            onCheckedChange = { value -> save { it.copy(autoplayAudio = value) } },
                        )
                        CardDivider()
                        SettingSwitch(
                            title = "Haptic feedback",
                            caption = "A quiet tap on answers and ratings",
                            checked = settings.hapticsEnabled,
                            onCheckedChange = { value -> save { it.copy(hapticsEnabled = value) } },
                        )
                    }
                }

                item { SectionLabel("APPEARANCE") }
                item {
                    SettingsCard {
                        Column(Modifier.padding(16.dp)) {
                            Text("THEME", fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.6.sp, color = colors.inkFaint)
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.sunken)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                listOf(ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark").forEach { (mode, label) ->
                                    SegmentOption(label, settings.theme == mode, Modifier.weight(1f)) {
                                        save { it.copy(theme = mode) }
                                    }
                                }
                            }
                        }
                        CardDivider()
                        SettingSwitch(
                            title = "Show IPA",
                            caption = "Display British and American pronunciation",
                            checked = settings.showIpa,
                            onCheckedChange = { value -> save { it.copy(showIpa = value) } },
                        )
                        CardDivider()
                        SettingSwitch(
                            title = "Show illustrations",
                            caption = "Use the painted atlas imagery",
                            checked = settings.showImages,
                            onCheckedChange = { value -> save { it.copy(showImages = value) } },
                        )
                        CardDivider()
                        SettingSwitch(
                            title = "Reduce motion",
                            caption = "Skip celebration animations",
                            checked = settings.reduceMotion,
                            onCheckedChange = { value -> save { it.copy(reduceMotion = value) } },
                        )
                    }
                }

                item { SectionLabel("REMINDERS") }
                item {
                    SettingsCard {
                        SettingSwitch(
                            title = "Daily reminder",
                            caption = "One quiet nudge when your reviews are due",
                            checked = settings.remindersEnabled,
                            onCheckedChange = ::toggleReminders,
                        )
                        CardDivider()
                        ActionRow(
                            title = "Reminder time",
                            caption = "Delivered every day",
                            onClick = { showTime = true },
                            trailing = "%02d:%02d".format(settings.reminderHour, settings.reminderMinute),
                        )
                    }
                }

                item { SectionLabel("YOUR DATA") }
                item {
                    SettingsCard {
                        ActionRow(
                            title = "Export progress",
                            caption = "Save cards, streaks and history as JSON",
                            onClick = { exportLauncher.launch("verbi-progress-${LocalDate.now()}.json") },
                            trailing = "Export",
                            icon = { Icon(Icons.Rounded.Upload, null, tint = colors.teal, modifier = Modifier.size(18.dp)) },
                        )
                        CardDivider()
                        ActionRow(
                            title = "Import progress",
                            caption = "Restore from a Verbilume backup",
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            trailing = "Import",
                            icon = { Icon(Icons.Rounded.Download, null, tint = colors.brass, modifier = Modifier.size(18.dp)) },
                        )
                        CardDivider()
                        ActionRow(
                            title = "Reset all progress",
                            caption = "Erase cards, streaks and achievements",
                            onClick = { showReset = true },
                            trailing = "Reset",
                            tint = colors.accent,
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Verbilume works entirely offline. There is no account, no analytics and no advertising.",
                        fontFamily = ManropeFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = colors.inkFaint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showName) {
        NameDialog(
            initial = settings.displayName,
            onDismiss = { showName = false },
            onSave = { name ->
                save { it.copy(displayName = name.trim().take(24)) }
                showName = false
            },
        )
    }

    if (showTopics) {
        TopicsDialog(
            topics = topics,
            selected = settings.selectedTopics,
            onToggle = { index ->
                scope.launch {
                    graph.settings.update { current ->
                        val next = current.selectedTopics.toMutableSet()
                        if (!next.add(index)) next.remove(index)
                        current.copy(selectedTopics = next)
                    }
                }
            },
            onClear = { save { it.copy(selectedTopics = emptySet()) } },
            onDismiss = { showTopics = false },
        )
    }

    val importText = pendingImport
    if (importText != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            containerColor = colors.raised,
            titleContentColor = colors.ink,
            textContentColor = colors.inkSoft,
            title = { Text("Restore this backup?", fontFamily = FrauncesFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp) },
            text = {
                Text(
                    "Your current cards, streak, bookmarks and achievements on this device will be replaced by the ones in this file. The illustrated dictionary itself is not affected.",
                    fontFamily = ManropeFamily,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val text = importText
                        pendingImport = null
                        scope.launch {
                            val result = runCatching { graph.learning.importJson(text) }
                            Toast.makeText(
                                context,
                                result.fold(
                                    { count -> "Restored $count cards from backup." },
                                    { "That file is not a valid Verbilume backup." },
                                ),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                ) {
                    Text("Restore", color = colors.accent, fontFamily = ManropeFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text("Cancel", color = colors.inkSoft, fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }

    if (showTime) {
        val timeState = rememberTimePickerState(
            initialHour = settings.reminderHour,
            initialMinute = settings.reminderMinute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            containerColor = colors.raised,
            titleContentColor = colors.ink,
            title = { Text("Reminder time", fontFamily = FrauncesFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp) },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(
                        state = timeState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = colors.sunken,
                            clockDialSelectedContentColor = colors.paper,
                            clockDialUnselectedContentColor = colors.inkSoft,
                            selectorColor = colors.accent,
                            containerColor = colors.raised,
                            timeSelectorSelectedContainerColor = colors.accent.copy(alpha = 0.22f),
                            timeSelectorSelectedContentColor = colors.accentDeep,
                            timeSelectorUnselectedContainerColor = colors.sunken,
                            timeSelectorUnselectedContentColor = colors.ink,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTime = false
                        applyTime(timeState.hour, timeState.minute)
                    },
                ) {
                    Text("Save", color = colors.accent, fontFamily = ManropeFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) {
                    Text("Cancel", color = colors.inkSoft, fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            containerColor = colors.raised,
            titleContentColor = colors.ink,
            textContentColor = colors.inkSoft,
            title = { Text("Erase all progress?", fontFamily = FrauncesFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp) },
            text = {
                Text(
                    "Every card, streak, bookmark and achievement will be permanently removed from this device. The illustrated dictionary itself is not affected. Export a backup first if you might want this record later.",
                    fontFamily = ManropeFamily,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReset = false
                        scope.launch {
                            runCatching { graph.learning.resetProgress() }
                            Toast.makeText(context, "Progress erased.", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Text("Erase everything", color = colors.accent, fontFamily = ManropeFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) {
                    Text("Keep it", color = colors.inkSoft, fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    val colors = LocalVerbiColors.current
    Spacer(Modifier.height(8.dp))
    Text(
        text,
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.5.sp,
        letterSpacing = 2.sp,
        color = colors.brass,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val colors = LocalVerbiColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.raised)
            .border(1.dp, colors.line, RoundedCornerShape(22.dp)),
    ) {
        content()
    }
}

@Composable
private fun CardDivider() {
    val colors = LocalVerbiColors.current
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(colors.line.copy(alpha = 0.6f)))
}

@Composable
private fun SettingSwitch(
    title: String,
    caption: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalVerbiColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink)
            Spacer(Modifier.height(2.dp))
            Text(caption, fontFamily = ManropeFamily, fontSize = 12.5.sp, lineHeight = 17.sp, color = colors.inkFaint)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.paper,
                checkedTrackColor = colors.accent,
                checkedBorderColor = colors.accent,
                uncheckedThumbColor = colors.inkFaint,
                uncheckedTrackColor = colors.sunken,
                uncheckedBorderColor = colors.line,
            ),
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    caption: String,
    onClick: () -> Unit,
    trailing: String? = null,
    tint: androidx.compose.ui.graphics.Color = LocalVerbiColors.current.ink,
    icon: (@Composable () -> Unit)? = null,
) {
    val colors = LocalVerbiColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(colors.sunken),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = tint)
            Spacer(Modifier.height(2.dp))
            Text(caption, fontFamily = ManropeFamily, fontSize = 12.5.sp, color = colors.inkFaint)
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                trailing,
                fontFamily = ManropeFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = if (tint == colors.ink) colors.inkSoft else tint,
            )
        }
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Rounded.ArrowForward, null, tint = colors.inkFaint, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun ChoicePill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    onClick: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.ink else colors.sunken.copy(alpha = 0.6f))
            .border(1.dp, if (selected) colors.ink else colors.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.5.sp,
            color = if (selected) colors.paper else colors.inkSoft,
        )
    }
}

@Composable
private fun SegmentOption(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalVerbiColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.raised else colors.sunken)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = if (selected) colors.ink else colors.inkFaint,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val colors = LocalVerbiColors.current
    var draft by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.raised,
        titleContentColor = colors.ink,
        textContentColor = colors.inkSoft,
        title = { Text("What should we call you?", fontFamily = FrauncesFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp) },
        text = {
            Column {
                Text("Shown on your profile. Nothing is sent anywhere.", fontFamily = ManropeFamily, fontSize = 13.sp, color = colors.inkSoft)
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.sunken)
                        .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (draft.isEmpty()) {
                        Text("Your name", fontFamily = FrauncesFamily, fontSize = 18.sp, color = colors.inkFaint)
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { if (it.length <= 24) draft = it },
                        textStyle = TextStyle(fontFamily = FrauncesFamily, fontSize = 18.sp, color = colors.ink),
                        cursorBrush = SolidColor(colors.accent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { onSave(draft) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                Text("Save", color = colors.accent, fontFamily = ManropeFamily, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.inkSoft, fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@Composable
private fun TopicsDialog(
    topics: List<TopicInfo>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalVerbiColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.raised,
        titleContentColor = colors.ink,
        title = { Text("Your realms", fontFamily = FrauncesFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp) },
        text = {
            Column {
                Text(
                    if (selected.isEmpty()) "All realms are included. Tap any realm to study only a chosen few." else "${selected.size} realms selected. New words and practice come from these.",
                    fontFamily = ManropeFamily,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = colors.inkSoft,
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    itemsIndexed(topics) { index, topic ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(index) }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selected.isEmpty() || index in selected,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = colors.accent,
                                    checkmarkColor = colors.paper,
                                    uncheckedColor = colors.inkFaint,
                                ),
                            )
                            Spacer(Modifier.width(4.dp))
                            Column(Modifier.weight(1f)) {
                                Text(topic.label, fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink)
                                Text("${formatNumber(topic.count)} words", fontFamily = ManropeFamily, fontSize = 12.5.sp, color = colors.inkFaint)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = colors.accent, fontFamily = ManropeFamily, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (selected.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text("All realms", color = colors.inkSoft, fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold)
                }
            }
        },
    )
}
