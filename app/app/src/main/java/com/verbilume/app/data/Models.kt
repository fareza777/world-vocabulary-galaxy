package com.verbilume.app.data

import org.json.JSONArray
import org.json.JSONObject

data class TopicInfo(
    val key: String,
    val label: String,
    val count: Int,
    val color: Long,
) {
    val art: String get() = "atlas/brand/topic-$key.webp"
}

data class LevelInfo(val index: Int, val label: String, val count: Int)

data class ContentMeta(
    val version: String,
    val wordCount: Int,
    val senseCount: Int,
    val exampleCount: Int,
    val collocationCount: Int,
    val audioCount: Int,
    val levels: List<LevelInfo>,
    val topics: List<TopicInfo>,
    val attribution: String,
) {
    companion object {
        fun parse(json: String): ContentMeta {
            val root = JSONObject(json)
            val levelsObj = root.getJSONObject("levels")
            val levels = listOf("A1", "A2", "B1", "B2", "C1", "C2").mapIndexed { i, key ->
                LevelInfo(i, key, levelsObj.optInt(key))
            }
            val topicsJson = root.getJSONArray("topics")
            val topics = buildList {
                for (i in 0 until topicsJson.length()) {
                    val t = topicsJson.getJSONObject(i)
                    add(
                        TopicInfo(
                            key = t.getString("key"),
                            label = t.getString("label"),
                            count = t.getInt("count"),
                            color = parseHex(t.getString("color")),
                        )
                    )
                }
            }
            return ContentMeta(
                version = root.optString("contentVersion", "1"),
                wordCount = root.optInt("words"),
                senseCount = root.optInt("senses"),
                exampleCount = root.optInt("examples"),
                collocationCount = root.optInt("collocations"),
                audioCount = root.optInt("audio"),
                levels = levels,
                topics = topics,
                attribution = "Vocabulary, illustrations and recordings by Word Atlas contributors, licensed CC BY 4.0. English-first edition by Verbilume.",
            )
        }

        private fun parseHex(hex: String): Long = hex.removePrefix("#").toLong(16) or 0xFF000000L
    }
}

data class WordSummary(
    val id: String,
    val word: String,
    val ipaUk: String?,
    val ipaUs: String?,
    val cefr: Int,
    val topic: Int,
    val image: String,
    val pos: String,
    val firstSense: String,
) {
    val levelLabel: String get() = CEFR_LEVELS[cefr.coerceIn(0, 5)]
    val assetImage: String get() = image
}

val CEFR_LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")

data class Sense(val pos: String, val definition: String)
data class Example(val text: String)
data class FamilyItem(val word: String, val pos: String?)

data class WordDetail(
    val summary: WordSummary,
    val audioUk: String?,
    val audioUs: String?,
    val audioEx1: String?,
    val audioEx2: String?,
    val senses: List<Sense>,
    val examples: List<Example>,
    val collocations: List<String>,
    val family: List<FamilyItem>,
    val synonyms: List<String>,
    val antonyms: List<String>,
) {
    val image get() = summary.image
    val word get() = summary.word
    val id get() = summary.id
}

data class BookmarkEntry(val word: WordSummary, val createdAt: Long)

data class CardWithWord(val card: FsrsCard, val word: WordSummary)

data class CardRecord(
    val wordId: String,
    val word: String,
    val topic: Int,
    val cefr: Int,
    val due: Long,
    val stability: Double,
    val difficulty: Double,
    val elapsedDays: Int,
    val scheduledDays: Int,
    val learningSteps: Int,
    val reps: Int,
    val lapses: Int,
    val state: Int,
    val lastReview: Long?,
    val firstSeen: Long,
    val lastGrade: Int,
)

data class DayStat(
    val day: String,
    val reviews: Int = 0,
    val newWords: Int = 0,
    val correct: Int = 0,
    val total: Int = 0,
    val xp: Int = 0,
    val seconds: Int = 0,
    val sessions: Int = 0,
    val challengeDone: Boolean = false,
    val challengeScore: Int = 0,
    val typingChars: Int = 0,
    val typingCorrect: Int = 0,
) {
    val accuracy: Float get() = if (total == 0) 0f else correct.toFloat() / total
}

data class LearningStats(
    val streak: Int = 0,
    val longestStreak: Int = 0,
    val reviewsToday: Int = 0,
    val newWordsToday: Int = 0,
    val xpToday: Int = 0,
    val secondsToday: Int = 0,
    val dailyGoal: Int = 30,
    val totalXp: Int = 0,
    val totalReviews: Int = 0,
    val totalWordsStarted: Int = 0,
    val masteredWords: Int = 0,
    val dueNow: Int = 0,
    val learningNow: Int = 0,
    val newAvailable: Int = 0,
    val accuracy30: Float = 0f,
    val daysStudied: Int = 0,
    val challengeDoneToday: Boolean = false,
    val challengeScoreToday: Int = 0,
    val xpLevel: Int = 1,
    val xpIntoLevel: Int = 0,
    val xpForNextLevel: Int = 1,
) {
    val goalFraction: Float get() = if (dailyGoal <= 0) 0f else (reviewsToday.toFloat() / dailyGoal).coerceIn(0f, 1f)
}

data class ForecastDay(val day: String, val due: Int, val isToday: Boolean)

data class StarProgress(val state: Int, val due: Long, val stability: Double)

data class TopicProgress(
    val topic: TopicInfo,
    val seen: Int,
    val mastered: Int,
    val total: Int,
) {
    val fraction: Float get() = if (total == 0) 0f else seen.toFloat() / total
}

data class AchievementDef(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val tier: String,
    val target: Int,
    val metric: String,
)

data class AchievementState(val def: AchievementDef, val progress: Int, val unlockedAt: Long?) {
    val unlocked: Boolean get() = unlockedAt != null
    val fraction: Float get() = if (def.target <= 0) 1f else (progress.toFloat() / def.target).coerceIn(0f, 1f)
}

data class ReviewOutcome(
    val xpGained: Int,
    val newAchievements: List<AchievementDef>,
    val card: FsrsCard,
    val streak: Int,
    val todayReviews: Int,
    val goalReached: Boolean,
)

data class SessionResult(
    val mode: String,
    val reviewed: Int,
    val correct: Int,
    val xp: Int,
    val seconds: Int,
    val bestStreak: Int,
    val newAchievements: List<AchievementDef>,
)

data class SearchHit(val word: WordSummary, val matchedIn: String? = null)

data class RecentSearch(val query: String, val usedAt: Long)

data class TypingStats(val bestWpm: Int, val bestAccuracy: Int, val chars: Int, val sessions: Int)
data class TypingOutcome(val xp: Int, val newAchievements: List<AchievementDef>)

enum class PracticeMode(val wire: String, val title: String, val subtitle: String) {
    LEARN("learn", "Guided review", "Let the algorithm choose what to revisit"),
    FLASHCARD("flashcard", "Flashcards", "A calm deck for free study"),
    MEANING("meaning", "Meaning match", "Choose the meaning that belongs"),
    PICTURE("picture", "Picture finder", "Name what you see"),
    LISTEN("listen", "Listen & choose", "Train your ear, one word at a time"),
    SPELLING("spelling", "Ear spelling", "Hear it, then write it"),
    RECALL("recall", "Active recall", "Write the word from its meaning"),
    MATCH("match", "Word pairing", "Match words to meanings against the clock"),
    DAILY("daily", "Daily challenge", "A dozen mixed questions, once a day"),
    TYPING("typing", "Typing studio", "Words, sentences and dictation"),
}

fun FsrsCard.toRecord(): CardRecord = CardRecord(
    wordId = wordId,
    word = word,
    topic = topic,
    cefr = cefr,
    due = due,
    stability = stability,
    difficulty = difficulty,
    elapsedDays = elapsedDays,
    scheduledDays = scheduledDays,
    learningSteps = learningSteps,
    reps = reps,
    lapses = lapses,
    state = state.ordinal,
    lastReview = lastReview,
    firstSeen = firstSeen,
    lastGrade = lastGrade,
)

fun JSONArray.toStringList(): List<String> = buildList {
    for (i in 0 until length()) add(getString(i))
}
