package com.verbilume.app.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

class LearningRepository(
    private val database: AtlasDatabase,
    private val content: ContentRepository,
) {
    private val progress: SQLiteDatabase get() = database.progress
    private val mutex = Mutex()
    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    private val dayFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    suspend fun dueCards(now: Long = System.currentTimeMillis(), limit: Int = 60): List<CardWithWord> =
        withContext(Dispatchers.IO) {
            val ids = mutableListOf<Pair<String, Long>>()
            progress.rawQuery(
                "SELECT word_id, due FROM cards WHERE state > 0 AND due <= ? ORDER BY due LIMIT ?",
                arrayOf(now.toString(), limit.toString()),
            ).use { c ->
                while (c.moveToNext()) ids += c.getString(0) to c.getLong(1)
            }
            val words = content.words(ids.map { it.first })
            ids.mapNotNull { (id, _) -> words[id]?.let { word -> card(id)?.let { CardWithWord(it, word) } } }
        }

    suspend fun card(wordId: String): FsrsCard? = withContext(Dispatchers.IO) {
        progress.rawQuery("SELECT * FROM cards WHERE word_id = ?", arrayOf(wordId)).use { c ->
            if (c.moveToFirst()) mapCard(c) else null
        }
    }

    suspend fun cardsFor(wordIds: List<String>): Map<String, FsrsCard> = withContext(Dispatchers.IO) {
        val out = HashMap<String, FsrsCard>()
        wordIds.chunked(400).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            progress.rawQuery(
                "SELECT * FROM cards WHERE word_id IN ($placeholders)",
                chunk.toTypedArray(),
            ).use { c ->
                while (c.moveToNext()) {
                    val card = mapCard(c)
                    out[card.wordId] = card
                }
            }
        }
        out
    }

    suspend fun newWords(settings: AppSettings, seed: Long, limit: Int): List<WordSummary> =
        withContext(Dispatchers.IO) {
            if (limit <= 0) return@withContext emptyList()
            val known = knownIds()
            val out = LinkedHashMap<String, WordSummary>()
            var attempt = 0
            while (out.size < limit && attempt < 5) {
                val batch = content.newWordBatch(
                    settings.level,
                    settings.effectiveTopics,
                    seed + attempt * 7919L,
                    limit * 4,
                )
                for (word in batch) {
                    if (out.size >= limit) break
                    if (word.id !in known) out[word.id] = word
                }
                attempt++
            }
            out.values.toList()
        }

    suspend fun learningQueue(settings: AppSettings, now: Long = System.currentTimeMillis()): List<CardWithWord> =
        withContext(Dispatchers.IO) {
            val due = dueCards(now, 40)
            val fresh = newWords(settings, now / 86_400_000L, settings.dailyNewWords)
            val freshCards = fresh.map { word ->
                CardWithWord(
                    Fsrs.newCard(word.id, word.word, word.topic, word.cefr, now),
                    word,
                )
            }
            due + freshCards
        }

    suspend fun review(
        word: WordSummary,
        rating: FsrsRating,
        correct: Boolean,
        seconds: Int,
        dailyGoal: Int,
        learnMode: Boolean = true,
    ): ReviewOutcome = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val day = todayKey()
            val existing = progress.rawQuery(
                "SELECT * FROM cards WHERE word_id = ?", arrayOf(word.id),
            ).use { if (it.moveToFirst()) mapCard(it) else null }
            val isNew = existing == null
            val base = existing ?: Fsrs.newCard(word.id, word.word, word.topic, word.cefr, now)
            val updated = Fsrs.review(
                base.copy(word = word.word, topic = word.topic, cefr = word.cefr),
                now,
                rating,
            )
            val xp = when {
                learnMode -> intArrayOf(2, 6, 9, 12)[rating.value - 1] + if (isNew) 4 else 0
                correct -> 10 + if (isNew) 4 else 0
                else -> 3
            }
            progress.beginTransaction()
            try {
                progress.insertWithOnConflict(
                    "cards", null, cardValues(updated), SQLiteDatabase.CONFLICT_REPLACE,
                )
                bumpDayStats(
                    day = day,
                    reviews = 1,
                    newWords = if (isNew) 1 else 0,
                    correct = if (correct) 1 else 0,
                    total = 1,
                    xp = xp,
                    seconds = seconds.coerceIn(0, 900),
                )
                val unlocked = evaluateAchievementsLocked(now)
                progress.setTransactionSuccessful()
                val todayReviews = todayStat(day).reviews
                val streak = currentStreak()
                _dataVersion.value++
                ReviewOutcome(
                    xpGained = xp,
                    newAchievements = unlocked,
                    card = updated,
                    streak = streak,
                    todayReviews = todayReviews,
                    goalReached = todayReviews >= dailyGoal,
                )
            } finally {
                progress.endTransaction()
            }
        }
    }

    suspend fun stats(settings: AppSettings): LearningStats = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val today = todayStat(todayKey())
        val totals = progress.rawQuery(
            "SELECT COALESCE(SUM(xp),0), COALESCE(SUM(reviews),0), COUNT(*) FROM day_stats WHERE reviews > 0",
            null,
        ).use { c ->
            if (c.moveToFirst()) Triple(c.getInt(0), c.getInt(1), c.getInt(2)) else Triple(0, 0, 0)
        }
        val retention = sinceStats(LocalDate.now().minusDays(29))
        val totalXp = totals.first
        val xp = xpProgress(totalXp)
        val started = count("SELECT COUNT(*) FROM cards")
        val mastered = count("SELECT COUNT(*) FROM cards WHERE state = 2 AND stability >= 21")
        val due = count("SELECT COUNT(*) FROM cards WHERE state > 0 AND due <= $now")
        val learning = count("SELECT COUNT(*) FROM cards WHERE state IN (1,3)")
        val eligible = content.eligibleCount(settings.level, settings.effectiveTopics)
        val knownEligible = cardsEligibleCount(settings.level, settings.effectiveTopics)
        val days = recentDayKeys(400)
        LearningStats(
            streak = streakFrom(days),
            longestStreak = longestStreakFrom(recentDayKeys(2000)),
            reviewsToday = today.reviews,
            newWordsToday = today.newWords,
            xpToday = today.xp,
            secondsToday = today.seconds,
            dailyGoal = settings.dailyGoal,
            totalXp = totalXp,
            totalReviews = totals.second,
            totalWordsStarted = started,
            masteredWords = mastered,
            dueNow = due,
            learningNow = learning,
            newAvailable = max(0, eligible - knownEligible),
            accuracy30 = if (retention.second == 0) 0f else retention.first.toFloat() / retention.second,
            daysStudied = totals.third,
            challengeDoneToday = today.challengeDone,
            challengeScoreToday = today.challengeScore,
            xpLevel = xp.first,
            xpIntoLevel = xp.second,
            xpForNextLevel = xp.third,
        )
    }

    suspend fun todayStats(): DayStat = withContext(Dispatchers.IO) { todayStat(todayKey()) }

    suspend fun dueCount(now: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        count("SELECT COUNT(*) FROM cards WHERE state > 0 AND due <= $now")
    }

    suspend fun heatmap(weeks: Int = 16): List<DayStat> = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val start = today.with(DayOfWeek.MONDAY).minusWeeks((weeks - 1).toLong())
        val end = start.plusDays(weeks * 7L - 1)
        val map = rawDays(start, end).associateBy { it.day }
        buildList {
            var day = start
            while (!day.isAfter(end)) {
                val key = day.format(dayFormatter)
                add(map[key] ?: DayStat(key))
                day = day.plusDays(1)
            }
        }
    }

    suspend fun forecast(days: Int = 7): List<ForecastDay> = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val end = today.plusDays(days.toLong())
        val counts = HashMap<String, Int>()
        progress.rawQuery(
            """
            SELECT date(due / 1000, 'unixepoch', 'localtime') AS d, COUNT(*)
            FROM cards WHERE state > 0 AND due < ? GROUP BY d
            """.trimIndent(),
            arrayOf((end.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()).toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val key = c.getString(0) ?: continue
                counts[key] = (counts[key] ?: 0) + c.getInt(1)
            }
        }
        val todayKey = today.format(dayFormatter)
        val overdue = counts.entries.filter { it.key < todayKey }.sumOf { it.value }
        buildList {
            for (i in 0 until days) {
                val day = today.plusDays(i.toLong())
                val key = day.format(dayFormatter)
                add(ForecastDay(key, (counts[key] ?: 0) + if (i == 0) overdue else 0, i == 0))
            }
        }
    }

    suspend fun topicProgress(): List<TopicProgress> = withContext(Dispatchers.IO) {
        val rows = HashMap<Int, Pair<Int, Int>>()
        progress.rawQuery(
            "SELECT topic, COUNT(*), SUM(CASE WHEN state = 2 AND stability >= 21 THEN 1 ELSE 0 END) FROM cards GROUP BY topic",
            null,
        ).use { c ->
            while (c.moveToNext()) rows[c.getInt(0)] = c.getInt(1) to c.getInt(2)
        }
        content.meta.topics.mapIndexed { index, topic ->
            val (seen, mastered) = rows[index] ?: (0 to 0)
            TopicProgress(topic, seen, mastered, topic.count)
        }
    }

    suspend fun bookmarks(): List<BookmarkEntry> = withContext(Dispatchers.IO) {
        val rows = mutableListOf<Pair<String, Long>>()
        progress.rawQuery("SELECT word_id, created_at FROM bookmarks ORDER BY created_at DESC", null).use { c ->
            while (c.moveToNext()) rows += c.getString(0) to c.getLong(1)
        }
        val words = content.words(rows.map { it.first })
        rows.mapNotNull { (id, at) -> words[id]?.let { BookmarkEntry(it, at) } }
    }

    suspend fun isBookmarked(wordId: String): Boolean = withContext(Dispatchers.IO) {
        count("SELECT COUNT(*) FROM bookmarks WHERE word_id = ${sqlString(wordId)}") > 0
    }

    suspend fun toggleBookmark(wordId: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val exists = progress.rawQuery(
                "SELECT 1 FROM bookmarks WHERE word_id = ?", arrayOf(wordId),
            ).use { it.moveToFirst() }
            if (exists) {
                progress.delete("bookmarks", "word_id = ?", arrayOf(wordId))
                _dataVersion.value++
                false
            } else {
                progress.insertWithOnConflict(
                    "bookmarks", null,
                    ContentValues().apply {
                        put("word_id", wordId)
                        put("created_at", System.currentTimeMillis())
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                evaluateAchievementsLocked(System.currentTimeMillis())
                _dataVersion.value++
                true
            }
        }
    }

    suspend fun recentSearches(limit: Int = 8): List<RecentSearch> = withContext(Dispatchers.IO) {
        progress.rawQuery(
            "SELECT query, used_at FROM search_history ORDER BY used_at DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c ->
            buildList { while (c.moveToNext()) add(RecentSearch(c.getString(0), c.getLong(1))) }
        }
    }

    suspend fun recordSearch(query: String) = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) return@withContext
        progress.execSQL(
            "INSERT OR REPLACE INTO search_history(query, used_at) VALUES(?, ?)",
            arrayOf<Any>(clean, System.currentTimeMillis()),
        )
        progress.execSQL(
            "DELETE FROM search_history WHERE query NOT IN (SELECT query FROM search_history ORDER BY used_at DESC LIMIT 12)",
        )
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        progress.delete("search_history", null, null)
    }

    suspend fun streak(): Int = withContext(Dispatchers.IO) { currentStreak() }

    suspend fun logSession(
        mode: PracticeMode,
        startedAt: Long,
        reviewed: Int,
        correct: Int,
        seconds: Int,
        bonusXp: Int,
    ): Pair<Int, List<AchievementDef>> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            var appliedBonus = 0
            progress.beginTransaction()
            try {
                progress.execSQL(
                    "INSERT INTO sessions(started_at, ended_at, mode, reviewed, correct, xp, seconds) VALUES(?,?,?,?,?,?,?)",
                    arrayOf<Any>(startedAt, now, mode.wire, reviewed, correct, bonusXp, seconds),
                )
                bumpDayStats(
                    todayKey(),
                    sessions = 1,
                    xp = bonusXp,
                    seconds = if (mode == PracticeMode.TYPING) seconds else 0,
                )
                appliedBonus = bonusXp
                val unlocked = evaluateAchievementsLocked(now)
                progress.setTransactionSuccessful()
                _dataVersion.value++
                appliedBonus to unlocked
            } finally {
                progress.endTransaction()
            }
        }
    }

    suspend fun markChallenge(score: Int): Pair<Int, List<AchievementDef>> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val today = todayStat(todayKey())
            val first = !today.challengeDone
            val bonus = if (first) 30 + score * 2 else 0
            progress.beginTransaction()
            try {
                progress.execSQL(
                    """
                    UPDATE day_stats SET challenge_done = 1,
                        challenge_score = MAX(challenge_score, ?), xp = xp + ?
                    WHERE day = ?
                    """.trimIndent(),
                    arrayOf<Any>(score, bonus, todayKey()),
                )
                val unlocked = evaluateAchievementsLocked(now)
                progress.setTransactionSuccessful()
                _dataVersion.value++
                bonus to unlocked
            } finally {
                progress.endTransaction()
            }
        }
    }

    suspend fun challengeToday(): Pair<Boolean, Int> = withContext(Dispatchers.IO) {
        val today = todayStat(todayKey())
        today.challengeDone to today.challengeScore
    }

    suspend fun recordTyping(mode: String, chars: Int, correctChars: Int, wpm: Int, accuracy: Int): TypingOutcome =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val now = System.currentTimeMillis()
                val xp = max(4, chars / 12) + if (accuracy >= 98) 5 else 0
                progress.beginTransaction()
                try {
                    progress.execSQL(
                        "INSERT INTO typing_runs(ran_at, mode, chars, correct, wpm, accuracy) VALUES(?,?,?,?,?,?)",
                        arrayOf<Any>(now, mode, chars, correctChars, wpm, accuracy),
                    )
                    bumpDayStats(todayKey(), xp = xp, typingChars = chars, typingCorrect = correctChars)
                    val unlocked = evaluateAchievementsLocked(now)
                    progress.setTransactionSuccessful()
                    _dataVersion.value++
                    TypingOutcome(xp, unlocked)
                } finally {
                    progress.endTransaction()
                }
            }
        }

    suspend fun typingStats(): TypingStats = withContext(Dispatchers.IO) {
        progress.rawQuery(
            """
            SELECT COALESCE(MAX(wpm),0), COALESCE(MAX(accuracy),0),
                   COALESCE((SELECT SUM(chars) FROM typing_runs),0),
                   COUNT(*)
            FROM typing_runs
            """.trimIndent(),
            null,
        ).use { c ->
            if (c.moveToFirst()) TypingStats(c.getInt(0), c.getInt(1), c.getInt(2), c.getInt(3))
            else TypingStats(0, 0, 0, 0)
        }
    }

    suspend fun achievements(): List<AchievementState> = withContext(Dispatchers.IO) {
        val unlocked = HashMap<String, Long>()
        progress.rawQuery("SELECT id, unlocked_at FROM achievements", null).use { c ->
            while (c.moveToNext()) unlocked[c.getString(0)] = c.getLong(1)
        }
        val metrics = achievementMetrics()
        ACHIEVEMENTS.map { def ->
            AchievementState(def, metrics[def.metric] ?: 0, unlocked[def.id])
        }
    }

    suspend fun starProgress(): Map<String, StarProgress> = withContext(Dispatchers.IO) {
        val out = HashMap<String, StarProgress>()
        progress.rawQuery("SELECT word_id, state, due, stability FROM cards", null).use { c ->
            while (c.moveToNext()) {
                out[c.getString(0)] = StarProgress(c.getInt(1), c.getLong(2), c.getDouble(3))
            }
        }
        out
    }

    suspend fun masteredCount(): Int = withContext(Dispatchers.IO) {
        count("SELECT COUNT(*) FROM cards WHERE state = 2 AND stability >= 21")
    }

    suspend fun resetProgress() = withContext(Dispatchers.IO) {
        mutex.withLock {
            progress.beginTransaction()
            try {
                listOf("cards", "bookmarks", "day_stats", "achievements", "sessions", "search_history", "typing_runs")
                    .forEach { progress.delete(it, null, null) }
                progress.setTransactionSuccessful()
            } finally {
                progress.endTransaction()
            }
            progress.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            _dataVersion.value++
        }
    }

    fun exportJson(): String = buildString {
        append("{\"app\":\"Verbilume\",\"version\":1,\"exported_at\":").append(System.currentTimeMillis())
        append(",\"cards\":")
        append(exportTable("cards"))
        append(",\"bookmarks\":")
        append(exportTable("bookmarks"))
        append(",\"day_stats\":")
        append(exportTable("day_stats"))
        append(",\"achievements\":")
        append(exportTable("achievements"))
        append(",\"sessions\":")
        append(exportTable("sessions"))
        append(",\"typing_runs\":")
        append(exportTable("typing_runs"))
        append("}")
    }

    fun isValidBackup(json: String): Boolean = runCatching {
        JSONObject(json).optString("app") == "Verbilume"
    }.getOrDefault(false)

    suspend fun importJson(json: String): Int = withContext(Dispatchers.IO) {
        val root = try { JSONObject(json) } catch (e: Exception) {
            throw IllegalArgumentException("This file is not a Verbilume backup.")
        }
        if (root.optString("app") != "Verbilume") throw IllegalArgumentException("This file is not a Verbilume backup.")
        mutex.withLock {
            progress.beginTransaction()
            try {
                listOf("cards", "bookmarks", "day_stats", "achievements", "sessions", "typing_runs")
                    .forEach { progress.delete(it, null, null) }
                val cards = root.optJSONArray("cards") ?: JSONArray()
                for (i in 0 until cards.length()) {
                    val o = cards.getJSONObject(i)
                    val values = ContentValues().apply {
                        o.keys().forEach { key -> put(key, o.get(key).let { if (it == JSONObject.NULL) null else it.toString() }) }
                    }
                    progress.insertWithOnConflict("cards", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
                importSimpleTable("bookmarks", root.optJSONArray("bookmarks"))
                importSimpleTable("day_stats", root.optJSONArray("day_stats"))
                importSimpleTable("achievements", root.optJSONArray("achievements"))
                importSimpleTable("sessions", root.optJSONArray("sessions"))
                importSimpleTable("typing_runs", root.optJSONArray("typing_runs"))
                progress.setTransactionSuccessful()
                _dataVersion.value++
                cards.length()
            } finally {
                progress.endTransaction()
            }
        }
    }

    private fun importSimpleTable(table: String, array: JSONArray?) {
        if (array == null) return
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val values = ContentValues().apply {
                o.keys().forEach { key -> put(key, o.get(key).let { if (it == JSONObject.NULL) null else it.toString() }) }
            }
            progress.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun exportTable(table: String): String {
        val rows = JSONArray()
        progress.rawQuery("SELECT * FROM $table", null).use { c ->
            while (c.moveToNext()) {
                val obj = JSONObject()
                c.columnNames.forEachIndexed { index, name ->
                    when (c.getType(index)) {
                        Cursor.FIELD_TYPE_INTEGER -> obj.put(name, c.getLong(index))
                        Cursor.FIELD_TYPE_FLOAT -> obj.put(name, c.getDouble(index))
                        Cursor.FIELD_TYPE_NULL -> obj.put(name, JSONObject.NULL)
                        else -> obj.put(name, c.getString(index))
                    }
                }
                rows.put(obj)
            }
        }
        return rows.toString()
    }

    private fun bumpDayStats(
        day: String,
        reviews: Int = 0,
        newWords: Int = 0,
        correct: Int = 0,
        total: Int = 0,
        xp: Int = 0,
        seconds: Int = 0,
        sessions: Int = 0,
        typingChars: Int = 0,
        typingCorrect: Int = 0,
    ) {
        val updated = progress.compileStatement(
            """
            UPDATE day_stats SET reviews = reviews + ?, new_words = new_words + ?,
                correct = correct + ?, total = total + ?, xp = xp + ?, seconds = seconds + ?,
                sessions = sessions + ?, typing_chars = typing_chars + ?, typing_correct = typing_correct + ?
            WHERE day = ?
            """.trimIndent(),
        ).use { statement ->
            statement.bindLong(1, reviews.toLong())
            statement.bindLong(2, newWords.toLong())
            statement.bindLong(3, correct.toLong())
            statement.bindLong(4, total.toLong())
            statement.bindLong(5, xp.toLong())
            statement.bindLong(6, seconds.toLong())
            statement.bindLong(7, sessions.toLong())
            statement.bindLong(8, typingChars.toLong())
            statement.bindLong(9, typingCorrect.toLong())
            statement.bindString(10, day)
            statement.executeUpdateDelete()
        }
        if (updated == 0) {
            progress.execSQL(
                """
                INSERT INTO day_stats(day, reviews, new_words, correct, total, xp, seconds, sessions, typing_chars, typing_correct)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """.trimIndent(),
                arrayOf<Any>(day, reviews, newWords, correct, total, xp, seconds, sessions, typingChars, typingCorrect),
            )
        }
    }

    private fun todayStat(day: String): DayStat {
        progress.rawQuery("SELECT * FROM day_stats WHERE day = ?", arrayOf(day)).use { c ->
            return if (c.moveToFirst()) mapDay(c) else DayStat(day)
        }
    }

    private fun rawDays(start: LocalDate, end: LocalDate): List<DayStat> {
        progress.rawQuery(
            "SELECT * FROM day_stats WHERE day BETWEEN ? AND ? ORDER BY day",
            arrayOf(start.format(dayFormatter), end.format(dayFormatter)),
        ).use { c ->
            return buildList { while (c.moveToNext()) add(mapDay(c)) }
        }
    }

    private fun sinceStats(start: LocalDate): Pair<Int, Int> {
        return progress.rawQuery(
            "SELECT COALESCE(SUM(correct),0), COALESCE(SUM(total),0) FROM day_stats WHERE day >= ?",
            arrayOf(start.format(dayFormatter)),
        ).use { if (it.moveToFirst()) it.getInt(0) to it.getInt(1) else 0 to 0 }
    }

    private fun recentDayKeys(limit: Int): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        progress.rawQuery(
            "SELECT day FROM day_stats WHERE reviews > 0 OR typing_chars > 0 ORDER BY day DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                runCatching { LocalDate.parse(c.getString(0), dayFormatter) }.getOrNull()?.let { out += it }
            }
        }
        return out
    }

    private fun currentStreak(): Int = streakFrom(recentDayKeys(500))

    private fun streakFrom(daysDescending: List<LocalDate>): Int {
        if (daysDescending.isEmpty()) return 0
        val today = LocalDate.now()
        val latest = daysDescending.first()
        if (latest != today && latest != today.minusDays(1)) return 0
        var streak = 1
        var cursor = latest
        for (i in 1 until daysDescending.size) {
            val day = daysDescending[i]
            if (day == cursor.minusDays(1)) {
                streak++
                cursor = day
            } else if (day != cursor) {
                break
            }
        }
        return streak
    }

    private fun longestStreakFrom(descending: List<LocalDate>): Int {
        if (descending.isEmpty()) return 0
        val ascending = descending.reversed()
        var longest = 1
        var run = 1
        for (i in 1 until ascending.size) {
            if (ascending[i] == ascending[i - 1].plusDays(1)) {
                run++
                longest = max(longest, run)
            } else if (ascending[i] != ascending[i - 1]) {
                run = 1
            }
        }
        return longest
    }

    private fun xpProgress(totalXp: Int): Triple<Int, Int, Int> {
        var level = 1
        var remaining = totalXp
        var need = 150
        while (remaining >= need && level < 99) {
            remaining -= need
            level++
            need = 150 + (level - 1) * 100
        }
        return Triple(level, remaining, need)
    }

    private fun cardsEligibleCount(level: Int, topics: Set<Int>): Int {
        val args = mutableListOf(level.toString())
        var sql = "SELECT COUNT(*) FROM cards WHERE cefr <= ?"
        if (topics.isNotEmpty()) {
            sql += " AND topic IN (${topics.joinToString(",")})"
        }
        return progress.rawQuery(sql, args.toTypedArray()).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    private fun knownIds(): Set<String> {
        val out = HashSet<String>(8000)
        progress.rawQuery("SELECT word_id FROM cards", null).use { c ->
            while (c.moveToNext()) out += c.getString(0)
        }
        return out
    }

    private fun evaluateAchievementsLocked(now: Long): List<AchievementDef> {
        val unlocked = HashSet<String>()
        progress.rawQuery("SELECT id FROM achievements", null).use { c ->
            while (c.moveToNext()) unlocked += c.getString(0)
        }
        val metrics = achievementMetrics()
        val newly = mutableListOf<AchievementDef>()
        for (def in ACHIEVEMENTS) {
            if (def.id in unlocked) continue
            if ((metrics[def.metric] ?: 0) >= def.target) {
                progress.insertWithOnConflict(
                    "achievements", null,
                    ContentValues().apply {
                        put("id", def.id)
                        put("unlocked_at", now)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
                newly += def
            }
        }
        return newly
    }

    private fun achievementMetrics(): Map<String, Int> {
        val metrics = HashMap<String, Int>()
        metrics["totalWords"] = count("SELECT COUNT(*) FROM cards")
        metrics["mastered"] = count("SELECT COUNT(*) FROM cards WHERE state = 2 AND stability >= 21")
        metrics["totalReviews"] = count("SELECT COALESCE(SUM(reviews),0) FROM day_stats")
        metrics["totalXp"] = count("SELECT COALESCE(SUM(xp),0) FROM day_stats")
        metrics["daysStudied"] = count("SELECT COUNT(*) FROM day_stats WHERE reviews > 0")
        metrics["streak"] = currentStreak()
        metrics["longestStreak"] = longestStreakFrom(recentDayKeys(2000))
        metrics["perfectSessions"] = count("SELECT COUNT(*) FROM sessions WHERE reviewed >= 10 AND correct = reviewed")
        metrics["nightOwl"] = if (count(
                "SELECT COUNT(*) FROM sessions WHERE CAST(strftime('%H', started_at/1000, 'unixepoch','localtime') AS INTEGER) >= 22 " +
                    "OR CAST(strftime('%H', started_at/1000, 'unixepoch','localtime') AS INTEGER) < 5",
            ) > 0
        ) 1 else 0
        metrics["earlyBird"] = if (count(
                "SELECT COUNT(*) FROM sessions WHERE CAST(strftime('%H', started_at/1000, 'unixepoch','localtime') AS INTEGER) BETWEEN 5 AND 7",
            ) > 0
        ) 1 else 0
        metrics["bookmarks"] = count("SELECT COUNT(*) FROM bookmarks")
        metrics["challenges"] = count("SELECT COUNT(*) FROM day_stats WHERE challenge_done = 1")
        metrics["perfectChallenge"] = if (count("SELECT COALESCE(MAX(challenge_score),0) FROM day_stats") >= 12) 1 else 0
        metrics["typingChars"] = count("SELECT COALESCE(SUM(chars),0) FROM typing_runs")
        metrics["modesUsed"] = count("SELECT COUNT(DISTINCT mode) FROM sessions")
        metrics["marathon"] = count("SELECT COALESCE(MAX(reviews),0) FROM day_stats")
        metrics["totalSeconds"] = count("SELECT COALESCE(SUM(seconds),0) FROM day_stats")
        metrics["sessions"] = count("SELECT COUNT(*) FROM sessions")
        return metrics
    }

    private fun count(sql: String): Int = progress.rawQuery(sql, null).use {
        if (it.moveToFirst()) it.getInt(0) else 0
    }

    private fun todayKey(): String = LocalDate.now().format(dayFormatter)

    private fun mapCard(c: Cursor): FsrsCard = FsrsCard(
        wordId = c.getString(c.getColumnIndexOrThrow("word_id")),
        word = c.getString(c.getColumnIndexOrThrow("word")),
        topic = c.getInt(c.getColumnIndexOrThrow("topic")),
        cefr = c.getInt(c.getColumnIndexOrThrow("cefr")),
        due = c.getLong(c.getColumnIndexOrThrow("due")),
        stability = c.getDouble(c.getColumnIndexOrThrow("stability")),
        difficulty = c.getDouble(c.getColumnIndexOrThrow("difficulty")),
        elapsedDays = c.getInt(c.getColumnIndexOrThrow("elapsed_days")),
        scheduledDays = c.getInt(c.getColumnIndexOrThrow("scheduled_days")),
        learningSteps = c.getInt(c.getColumnIndexOrThrow("learning_steps")),
        reps = c.getInt(c.getColumnIndexOrThrow("reps")),
        lapses = c.getInt(c.getColumnIndexOrThrow("lapses")),
        state = FsrsState.entries[c.getInt(c.getColumnIndexOrThrow("state")).coerceIn(0, 3)],
        lastReview = if (c.isNull(c.getColumnIndexOrThrow("last_review"))) null
        else c.getLong(c.getColumnIndexOrThrow("last_review")),
        firstSeen = c.getLong(c.getColumnIndexOrThrow("first_seen")),
        lastGrade = c.getInt(c.getColumnIndexOrThrow("last_grade")),
    )

    private fun cardValues(card: FsrsCard): ContentValues = ContentValues().apply {
        put("word_id", card.wordId)
        put("word", card.word)
        put("topic", card.topic)
        put("cefr", card.cefr)
        put("due", card.due)
        put("stability", card.stability)
        put("difficulty", card.difficulty)
        put("elapsed_days", card.elapsedDays)
        put("scheduled_days", card.scheduledDays)
        put("learning_steps", card.learningSteps)
        put("reps", card.reps)
        put("lapses", card.lapses)
        put("state", card.state.ordinal)
        if (card.lastReview == null) putNull("last_review") else put("last_review", card.lastReview)
        put("first_seen", card.firstSeen)
        put("last_grade", card.lastGrade)
    }

    private fun mapDay(c: Cursor): DayStat = DayStat(
        day = c.getString(c.getColumnIndexOrThrow("day")),
        reviews = c.getInt(c.getColumnIndexOrThrow("reviews")),
        newWords = c.getInt(c.getColumnIndexOrThrow("new_words")),
        correct = c.getInt(c.getColumnIndexOrThrow("correct")),
        total = c.getInt(c.getColumnIndexOrThrow("total")),
        xp = c.getInt(c.getColumnIndexOrThrow("xp")),
        seconds = c.getInt(c.getColumnIndexOrThrow("seconds")),
        sessions = c.getInt(c.getColumnIndexOrThrow("sessions")),
        challengeDone = c.getInt(c.getColumnIndexOrThrow("challenge_done")) == 1,
        challengeScore = c.getInt(c.getColumnIndexOrThrow("challenge_score")),
        typingChars = c.getInt(c.getColumnIndexOrThrow("typing_chars")),
        typingCorrect = c.getInt(c.getColumnIndexOrThrow("typing_correct")),
    )

    private fun sqlString(value: String): String = "'${value.replace("'", "''")}'"

    companion object {
        val ACHIEVEMENTS = listOf(
            AchievementDef("first_light", "First light", "Learn your very first word", "star", "bronze", 1, "totalWords"),
            AchievementDef("first_review", "A door opens", "Complete one review", "key", "bronze", 1, "totalReviews"),
            AchievementDef("week_glow", "Seven evenings", "Study for 7 days in a row", "flame", "silver", 7, "streak"),
            AchievementDef("month_sky", "A month of stars", "Reach a 30-day streak", "moon", "gold", 30, "longestStreak"),
            AchievementDef("hundred", "Hundred lights", "Start learning 100 words", "sparkles", "bronze", 100, "totalWords"),
            AchievementDef("thousand", "Thousand lights", "Start learning 1,000 words", "sparkles", "silver", 1000, "totalWords"),
            AchievementDef("librarian", "The librarian", "Start learning 2,500 words", "book", "gold", 2500, "totalWords"),
            AchievementDef("master_100", "Deep roots", "Master 100 words", "roots", "silver", 100, "mastered"),
            AchievementDef("master_1000", "Master cartographer", "Master 1,000 words", "compass", "diamond", 1000, "mastered"),
            AchievementDef("xp_1000", "A thousand sparks", "Earn 1,000 lifetime XP", "bolt", "bronze", 1000, "totalXp"),
            AchievementDef("xp_10000", "Radiant mind", "Earn 10,000 lifetime XP", "sun", "gold", 10000, "totalXp"),
            AchievementDef("perfect_10", "Flawless page", "Finish a 10+ card session without a mistake", "feather", "silver", 1, "perfectSessions"),
            AchievementDef("night_owl", "Night observer", "Study after 10 pm", "owl", "bronze", 1, "nightOwl"),
            AchievementDef("early_bird", "Dawn reader", "Study between 5 and 7 am", "sunrise", "bronze", 1, "earlyBird"),
            AchievementDef("collector", "Pressed flowers", "Bookmark 20 words", "bookmark", "silver", 20, "bookmarks"),
            AchievementDef("daily_7", "Ritual of light", "Complete 7 daily challenges", "calendar", "gold", 7, "challenges"),
            AchievementDef("challenge_flawless", "Perfect constellation", "Score 12/12 in a daily challenge", "crown", "diamond", 1, "perfectChallenge"),
            AchievementDef("typist", "Quick quill", "Type 5,000 characters in Typing Studio", "quill", "silver", 5000, "typingChars"),
            AchievementDef("polyglot", "Many doorways", "Try six different practice modes", "doors", "silver", 6, "modesUsed"),
            AchievementDef("marathon", "A hundred in a day", "Review 100 words in one day", "hourglass", "gold", 100, "marathon"),
            AchievementDef("devoted", "Ten quiet hours", "Spend 10 hours learning", "candle", "gold", 36000, "totalSeconds"),
        )
    }
}
