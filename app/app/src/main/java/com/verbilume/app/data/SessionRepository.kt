package com.verbilume.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

enum class QuizKind { MEANING, PICTURE, LISTEN, SPELLING, RECALL }

enum class TypingMode(val wire: String, val title: String, val instruction: String) {
    WORDS("words", "Words", "Type the word that matches the meaning"),
    SENTENCES("sentences", "Sentences", "Type each sentence exactly as written"),
    DICTATION("dictation", "Dictation", "Listen, then type what you hear"),
}

data class TypingLine(
    val word: WordSummary,
    val prompt: String,
    val target: String,
    val audio: String?,
)

data class QuizOption(val text: String, val wordId: String, val correct: Boolean)

data class QuizQuestion(
    val word: WordSummary,
    val detail: WordDetail,
    val kind: QuizKind,
    val options: List<QuizOption> = emptyList(),
    val answer: String,
)

data class FlashcardItem(val word: WordSummary, val detail: WordDetail)
data class MatchPair(val word: WordSummary, val definition: String)

class SessionRepository(
    private val content: ContentRepository,
    private val learning: LearningRepository,
) {
    suspend fun buildLearn(settings: AppSettings, limit: Int = 20): List<CardWithWord> =
        withContext(Dispatchers.IO) {
            val due = learning.dueCards(System.currentTimeMillis(), limit)
            val room = (limit - due.size).coerceAtLeast(0)
            val extraNew = if (room > 0) {
                learning.newWords(settings, System.currentTimeMillis() / 86_400_000L, room)
                    .map { word -> CardWithWord(Fsrs.newCard(word.id, word.word, word.topic, word.cefr), word) }
            } else emptyList()
            due + extraNew
        }

    suspend fun detail(word: WordSummary): WordDetail? = withContext(Dispatchers.IO) { content.detail(word.id) }

    suspend fun buildQuiz(
        settings: AppSettings,
        mode: PracticeMode,
        count: Int = 10,
        seed: Long = System.currentTimeMillis(),
    ): List<QuizQuestion> = withContext(Dispatchers.IO) {
        val due = learning.dueCards(System.currentTimeMillis(), count).map { it.word }
        val fresh = content.newWordBatch(settings.level, settings.effectiveTopics, seed, count * 3)
        val pool = LinkedHashMap<String, WordSummary>()
        due.forEach { pool[it.id] = it }
        fresh.shuffled(Random(seed)).forEach { pool.putIfAbsent(it.id, it) }
        val selected = pool.values.shuffled(Random(seed xor 0x51ed270bL)).take(count)
        buildQuestions(selected, mode, seed)
    }

    suspend fun buildDaily(settings: AppSettings, count: Int = 12): List<QuizQuestion> =
        withContext(Dispatchers.IO) {
            val day = LocalDate.now().toString()
            val seed = ContentRepository.fnv1a("daily:$day:${settings.level}")
            val pool = content.newWordBatch(settings.level, settings.effectiveTopics, seed, count * 3)
            buildQuestions(pool.take(count), PracticeMode.DAILY, seed)
        }

    suspend fun buildFlashcards(settings: AppSettings, limit: Int = 24): List<FlashcardItem> =
        withContext(Dispatchers.IO) {
            val due = learning.dueCards(System.currentTimeMillis(), limit).map { it.word }
            val marks = learning.bookmarks().map { it.word }
            val curated = content.newWordBatch(settings.level, settings.effectiveTopics, System.currentTimeMillis() / 86_400_000L, limit)
            val pool = LinkedHashMap<String, WordSummary>()
            due.forEach { pool[it.id] = it }
            marks.forEach { pool.putIfAbsent(it.id, it) }
            curated.forEach { pool.putIfAbsent(it.id, it) }
            pool.values.take(limit).mapNotNull { word ->
                content.detail(word.id)?.let { FlashcardItem(word, it) }
            }
        }

    suspend fun buildMatch(settings: AppSettings, pairs: Int = 7): List<MatchPair> =
        withContext(Dispatchers.IO) {
            val seed = System.currentTimeMillis()
            val source = content.newWordBatch(settings.level, settings.effectiveTopics, seed, pairs * 3)
            val seenDefs = HashSet<String>()
            source.mapNotNull { word ->
                val detail = content.detail(word.id) ?: return@mapNotNull null
                val def = detail.senses.firstOrNull()?.definition ?: return@mapNotNull null
                if (!seenDefs.add(def.lowercase())) return@mapNotNull null
                MatchPair(word, def)
            }.take(pairs)
        }

    suspend fun buildTyping(
        settings: AppSettings,
        mode: TypingMode,
        count: Int = 8,
    ): List<TypingLine> = withContext(Dispatchers.IO) {
        val seed = System.currentTimeMillis()
        if (mode == TypingMode.WORDS) {
            content.newWordBatch(settings.level, settings.effectiveTopics, seed, count * 2)
                .mapNotNull { word ->
                    val detail = content.detail(word.id) ?: return@mapNotNull null
                    val prompt = detail.senses.firstOrNull()?.definition?.trim().orEmpty()
                    if (prompt.isBlank()) return@mapNotNull null
                    TypingLine(word, prompt, word.word, detail.primaryAudio(settings.accent))
                }
                .take(count)
        } else {
            val seen = HashSet<String>()
            content.examplesForTyping(count * 3, seed)
                .mapNotNull { pair ->
                    val (word, text) = pair
                    val clean = text.trim()
                    if (clean.isBlank() || !seen.add(clean.lowercase())) return@mapNotNull null
                    val detail = content.detail(word.id) ?: return@mapNotNull null
                    val index = detail.examples.indexOfFirst { it.text.trim() == clean }
                    TypingLine(
                        word = word,
                        prompt = if (mode == TypingMode.DICTATION) "Listen closely, then reproduce the sentence." else clean,
                        target = clean,
                        audio = if (index >= 0) detail.exampleAudio(index) else null,
                    )
                }
                .filter { mode != TypingMode.DICTATION || it.audio != null }
                .take(count)
        }
    }

    private fun buildQuestions(words: List<WordSummary>, mode: PracticeMode, seed: Long): List<QuizQuestion> {
        val out = mutableListOf<QuizQuestion>()
        words.forEachIndexed { index, word ->
            val detail = content.detail(word.id) ?: return@forEachIndexed
            val questionSeed = seed + ContentRepository.fnv1a(word.id) + index * 97L
            val kind = when (mode) {
                PracticeMode.MEANING -> QuizKind.MEANING
                PracticeMode.PICTURE -> QuizKind.PICTURE
                PracticeMode.LISTEN -> QuizKind.LISTEN
                PracticeMode.SPELLING -> QuizKind.SPELLING
                PracticeMode.RECALL -> QuizKind.RECALL
                PracticeMode.DAILY -> dailyKind(index, word, questionSeed)
                else -> QuizKind.MEANING
            }
            val definition = detail.senses.firstOrNull()?.definition ?: word.firstSense
            val options = when (kind) {
                QuizKind.MEANING -> {
                    val distractors = content.distractors(word, questionSeed, 6)
                        .map { it.firstSense.trim() }
                        .filter { it.isNotBlank() && it.lowercase() != definition.lowercase() }
                        .distinctBy { it.lowercase() }
                    val all = (distractors.take(3).map { it to false } + (definition to true))
                        .shuffled(Random(questionSeed))
                    all.map { (text, correct) ->
                        QuizOption(text, if (correct) word.id else "", correct)
                    }
                }
                QuizKind.PICTURE, QuizKind.LISTEN -> {
                    val distractors = content.distractors(word, questionSeed, 6)
                    val all = (distractors.take(3).map { it to false } + (word to true))
                        .shuffled(Random(questionSeed))
                    all.map { (candidate, correct) ->
                        QuizOption(candidate.word, candidate.id, correct)
                    }
                }
                else -> emptyList()
            }
            out += QuizQuestion(word, detail, kind, options, word.word)
        }
        return out
    }

    private fun dailyKind(index: Int, word: WordSummary, seed: Long): QuizKind {
        val hasImage = word.image.isNotBlank()
        val rotation = listOf(
            QuizKind.MEANING, QuizKind.LISTEN, QuizKind.RECALL,
            if (hasImage) QuizKind.PICTURE else QuizKind.MEANING,
            QuizKind.SPELLING, QuizKind.MEANING, QuizKind.RECALL,
            QuizKind.LISTEN, QuizKind.SPELLING, QuizKind.MEANING,
            if (hasImage) QuizKind.PICTURE else QuizKind.LISTEN, QuizKind.RECALL,
        )
        return rotation[index % rotation.size]
    }
}
