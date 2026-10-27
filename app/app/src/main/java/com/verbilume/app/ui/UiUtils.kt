package com.verbilume.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.verbilume.app.data.CEFR_LEVELS
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Routes {
    const val HOME = "home"
    const val ATLAS = "atlas"
    const val GALAXY = "galaxy"
    const val PRACTICE = "practice"
    const val PROFILE = "profile"
    const val SEARCH = "search"
    const val STATS = "stats"
    const val ACHIEVEMENTS = "achievements"
    const val SETTINGS = "settings"
    const val OFFLINE = "offline"
    const val ABOUT = "about"
    const val PRIVACY = "privacy"
    const val LICENSES = "licenses"
    const val BOOKMARKS = "bookmarks"
    const val TYPING = "typing"
    const val LEARN = "session/learn"
    const val FLASHCARDS = "session/flashcards"
    const val MATCH = "session/match"
    const val DAILY = "session/daily"
    const val QUIZ = "session/quiz/{mode}"
    const val WORD = "word/{id}"
    const val TOPIC = "topic/{index}"

    fun word(id: String) = "word/$id"
    fun topic(index: Int) = "topic/$index"
    fun quiz(modeName: String) = "session/quiz/$modeName"

    val mainDestinations = setOf(HOME, ATLAS, GALAXY, PRACTICE, PROFILE)
}

private val levelDescriptions = listOf(
    "First steps" to "Simple words and everyday objects",
    "Everyday basics" to "Routines, places and useful phrases",
    "Confident traveler" to "Opinions, stories and real-life topics",
    "Fluent thinker" to "Abstract ideas and richer expression",
    "Nuanced reader" to "Precise language and subtle meaning",
    "Near-native" to "Rare, literary and specialist words",
)

fun levelDescription(index: Int): String = levelDescriptions[index.coerceIn(0, 5)].second
fun levelTitle(index: Int): String = levelDescriptions[index.coerceIn(0, 5)].first
fun levelLabel(index: Int): String = CEFR_LEVELS[index.coerceIn(0, 5)]

fun greeting(): String {
    val hour = LocalDateTime.now().hour
    return when {
        hour < 5 -> "Still awake"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        hour < 21 -> "Good evening"
        else -> "A quiet evening"
    }
}

fun prettyDate(date: LocalDate = LocalDate.now()): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))

fun compactDate(day: String): String = runCatching {
    LocalDate.parse(day).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
}.getOrDefault(day)

fun weekdayLetter(day: String): String = runCatching {
    LocalDate.parse(day).format(DateTimeFormatter.ofPattern("E", Locale.ENGLISH)).take(1)
}.getOrDefault("")

fun initials(name: String): String {
    val clean = name.trim()
    if (clean.isEmpty()) return "V"
    return clean.split(" ").filter { it.isNotBlank() }.take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifEmpty { "V" }
}

fun formatNumber(value: Int): String = "%,d".format(Locale.US, value)

fun plural(count: Int, singular: String, plural: String = singular + "s"): String =
    "${formatNumber(count)} ${if (count == 1) singular else plural}"

/** Never surface raw SQLite/exception text; fall back to calm, human copy. */
fun friendlyError(error: Throwable?, fallback: String = "Something went wrong. Please try again."): String {
    val message = error?.message?.trim().orEmpty()
    val technical = message.isEmpty() ||
        message.length > 160 ||
        message.contains("SQLITE", ignoreCase = true) ||
        message.contains("code ", ignoreCase = true) ||
        message.contains("Exception", ignoreCase = true) ||
        message.contains("null", ignoreCase = true)
    return if (technical) fallback else message
}

/** Keeps the longest entries ("telecommunication", 17 chars) on one line beside the speak button. */
fun headlineSize(word: String, base: Int): androidx.compose.ui.unit.TextUnit {
    val longest = word.split(" ").maxOfOrNull { it.length } ?: word.length
    val size = when {
        longest <= 10 -> base
        longest <= 13 -> base - 6
        longest <= 15 -> base - 11
        else -> base - 15
    }
    return size.coerceAtLeast(20).let { androidx.compose.ui.unit.TextUnit(it.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp) }
}

fun formatMinutes(seconds: Int): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
}

fun normalizeTypedAnswer(value: String): String = value
    .lowercase()
    .replace('’', '\'')
    .replace(Regex("[^a-z0-9' -]"), "")
    .trim()
    .replace(Regex("\\s+"), " ")

fun answerMatches(input: String, expected: String): Boolean {
    val typed = normalizeTypedAnswer(input)
    if (typed.isEmpty()) return false
    val answer = normalizeTypedAnswer(expected)
    if (typed == answer) return true
    return typed.replace(" ", "") == answer.replace(" ", "") && answer.length >= 4
}

fun levelColorIndex(cefr: Int): Int = cefr.coerceIn(0, 5)

@Composable
inline fun <reified T : ViewModel> graphViewModel(
    key: String? = null,
    crossinline create: () -> T,
): T {
    val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
    return if (key == null) viewModel(factory = factory) else viewModel(key = key, factory = factory)
}
