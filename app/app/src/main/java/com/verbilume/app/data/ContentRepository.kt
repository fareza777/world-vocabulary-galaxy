package com.verbilume.app.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlin.math.abs

data class GalaxyNode(
    val id: String,
    val word: String,
    val topic: Int,
    val cefr: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val seed: Float,
    val image: String,
)

data class WordQuery(
    val topic: Int? = null,
    val cefr: Int? = null,
    val text: String? = null,
    val limit: Int = 60,
    val offset: Int = 0,
)

class ContentRepository(private val database: AtlasDatabase) {
    private val content: SQLiteDatabase get() = database.content
    val meta: ContentMeta get() = database.meta

    private val summarySelect = """
        SELECT w.id, w.word, w.ipa_uk, w.ipa_us, w.cefr, w.topic, w.image, w.pos,
               COALESCE((SELECT s.definition FROM senses s WHERE s.word_id = w.id ORDER BY s.idx LIMIT 1), '') AS first_sense
        FROM words w
    """.trimIndent()

    fun search(query: String, limit: Int = 60): List<WordSummary> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val prefix = "$escaped%"
        val contains = "%$escaped%"
        val sql = """
            $summarySelect
            WHERE w.word LIKE ? ESCAPE '\' OR w.search_text LIKE ? ESCAPE '\'
            ORDER BY
                CASE WHEN lower(w.word) = lower(?) THEN 0
                     WHEN lower(w.word) LIKE lower(?) ESCAPE '\' THEN 1
                     ELSE 2 END,
                length(w.word), w.word COLLATE NOCASE
            LIMIT ?
        """.trimIndent()
        return content.rawQuery(sql, arrayOf(contains, contains, q, prefix, limit.toString())).use { c ->
            buildList { while (c.moveToNext()) add(mapSummary(c)) }
        }
    }

    fun searchByLetter(letter: String, limit: Int = 80): List<WordSummary> {
        val sql = "$summarySelect WHERE w.word LIKE ? ORDER BY length(w.word), w.word COLLATE NOCASE LIMIT ?"
        return content.rawQuery(sql, arrayOf("$letter%", limit.toString())).use { c ->
            buildList { while (c.moveToNext()) add(mapSummary(c)) }
        }
    }

    fun word(id: String): WordSummary? {
        val sql = "$summarySelect WHERE w.id = ?"
        return content.rawQuery(sql, arrayOf(id)).use { c ->
            if (c.moveToFirst()) mapSummary(c) else null
        }
    }

    fun words(ids: List<String>): Map<String, WordSummary> {
        if (ids.isEmpty()) return emptyMap()
        val result = HashMap<String, WordSummary>(ids.size)
        ids.chunked(400).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val sql = "$summarySelect WHERE w.id IN ($placeholders)"
            content.rawQuery(sql, chunk.toTypedArray()).use { c ->
                while (c.moveToNext()) {
                    val item = mapSummary(c)
                    result[item.id] = item
                }
            }
        }
        return result
    }

    fun detail(id: String): WordDetail? {
        val row = content.rawQuery(
            """
            SELECT w.id, w.word, w.ipa_uk, w.ipa_us, w.cefr, w.topic, w.image, w.pos,
                   COALESCE((SELECT s.definition FROM senses s WHERE s.word_id = w.id ORDER BY s.idx LIMIT 1), '') AS first_sense,
                   w.audio_uk, w.audio_us, w.audio_ex1, w.audio_ex2
            FROM words w WHERE w.id = ?
            """.trimIndent(),
            arrayOf(id),
        ).use { c ->
            if (!c.moveToFirst()) null else WordRow(
                summary = mapSummary(c),
                audioUk = c.stringOrNull(9),
                audioUs = c.stringOrNull(10),
                audioEx1 = c.stringOrNull(11),
                audioEx2 = c.stringOrNull(12),
            )
        } ?: return null

        val senses = queryStrings("SELECT pos, definition FROM senses WHERE word_id = ? ORDER BY idx", id) {
            Sense(it.getString(0), it.getString(1))
        }
        val examples = queryStrings("SELECT text FROM examples WHERE word_id = ? ORDER BY idx", id) {
            Example(it.getString(0))
        }
        val collocations = queryStrings("SELECT text FROM collocations WHERE word_id = ? ORDER BY idx", id) {
            it.getString(0)
        }
        val family = queryStrings("SELECT text FROM family WHERE word_id = ? ORDER BY idx", id) {
            val text = it.getString(0)
            val open = text.lastIndexOf('(')
            if (open > 0 && text.endsWith(")")) {
                FamilyItem(text.substring(0, open).trim(), text.substring(open + 1, text.length - 1).trim())
            } else {
                FamilyItem(text.trim(), null)
            }
        }
        val synonyms = queryStrings("SELECT text FROM synonyms WHERE word_id = ? ORDER BY idx", id) { it.getString(0) }
        val antonyms = queryStrings("SELECT text FROM antonyms WHERE word_id = ? ORDER BY idx", id) { it.getString(0) }

        return WordDetail(
            summary = row.summary,
            audioUk = row.audioUk,
            audioUs = row.audioUs,
            audioEx1 = row.audioEx1,
            audioEx2 = row.audioEx2,
            senses = senses,
            examples = examples,
            collocations = collocations,
            family = family,
            synonyms = synonyms,
            antonyms = antonyms,
        )
    }

    private data class WordRow(
        val summary: WordSummary,
        val audioUk: String?,
        val audioUs: String?,
        val audioEx1: String?,
        val audioEx2: String?,
    )

    fun page(query: WordQuery): List<WordSummary> {
        val args = mutableListOf<String>()
        val where = mutableListOf<String>()
        if (query.topic != null) {
            where += "w.topic = ?"
            args += query.topic.toString()
        }
        if (query.cefr != null) {
            where += "w.cefr = ?"
            args += query.cefr.toString()
        }
        val text = query.text?.trim().orEmpty()
        if (text.isNotEmpty()) {
            where += "(w.word LIKE ? ESCAPE '\\' OR w.search_text LIKE ? ESCAPE '\\')"
            val escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            args += "%$escaped%"
            args += "%$escaped%"
        }
        val clause = if (where.isEmpty()) "" else "WHERE " + where.joinToString(" AND ")
        val sql = "$summarySelect $clause ORDER BY w.word COLLATE NOCASE LIMIT ? OFFSET ?"
        args += query.limit.toString()
        args += query.offset.toString()
        return content.rawQuery(sql, args.toTypedArray()).use { c ->
            buildList { while (c.moveToNext()) add(mapSummary(c)) }
        }
    }

    fun count(topic: Int? = null, cefr: Int? = null): Int {
        val args = mutableListOf<String>()
        val where = mutableListOf<String>()
        if (topic != null) { where += "topic = ?"; args += topic.toString() }
        if (cefr != null) { where += "cefr = ?"; args += cefr.toString() }
        val clause = if (where.isEmpty()) "" else "WHERE " + where.joinToString(" AND ")
        return content.rawQuery("SELECT COUNT(*) FROM words $clause", args.toTypedArray()).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun topicWords(topic: Int, levelMax: Int? = null, limit: Int = 40): List<WordSummary> {
        val sql = if (levelMax == null) {
            "$summarySelect WHERE w.topic = ? ORDER BY w.word COLLATE NOCASE LIMIT ?"
        } else {
            "$summarySelect WHERE w.topic = ? AND w.cefr <= ? ORDER BY w.word COLLATE NOCASE LIMIT ?"
        }
        val args = if (levelMax == null) arrayOf(topic.toString(), limit.toString())
        else arrayOf(topic.toString(), levelMax.toString(), limit.toString())
        return content.rawQuery(sql, args).use { c ->
            buildList { while (c.moveToNext()) add(mapSummary(c)) }
        }
    }

    fun wordOfTheDay(dayKey: String, level: Int, topics: Set<Int>): WordSummary? {
        val args = mutableListOf<String>()
        var where = "w.cefr = ?"
        args += level.coerceIn(0, 5).toString()
        if (topics.isNotEmpty()) {
            where += " AND w.topic IN (${topics.joinToString(",")})"
        }
        val count = content.rawQuery("SELECT COUNT(*) FROM words w WHERE $where", args.toTypedArray()).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        if (count == 0) {
            return page(WordQuery(limit = 1)).firstOrNull()
        }
        val index = (fnv1a(dayKey) % count).toInt().coerceAtLeast(0)
        val sql = "$summarySelect WHERE $where ORDER BY w.word COLLATE NOCASE LIMIT 1 OFFSET ?"
        return content.rawQuery(sql, (args + index.toString()).toTypedArray()).use { c ->
            if (c.moveToFirst()) mapSummary(c) else null
        }
    }

    fun newWordBatch(level: Int, topics: Set<Int>, seed: Long, limit: Int): List<WordSummary> {
        val safeLevel = level.coerceIn(0, 5)
        val lower = (safeLevel - 1).coerceAtLeast(0)
        val args = mutableListOf<String>()
        val where = mutableListOf("w.cefr BETWEEN ? AND ?")
        args += lower.toString()
        args += safeLevel.toString()
        if (topics.isNotEmpty()) {
            where += "w.topic IN (${topics.joinToString(",")})"
        }
        val total = content.rawQuery("SELECT COUNT(*) FROM words w WHERE ${where.joinToString(" AND ")}", args.toTypedArray()).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        if (total == 0) return emptyList()
        val random = kotlin.random.Random(seed)
        val offsets = LinkedHashSet<Int>()
        var guard = 0
        while (offsets.size < minOf(limit, total) && guard < limit * 30) {
            offsets += random.nextInt(total)
            guard++
        }
        val out = ArrayList<WordSummary>(offsets.size)
        offsets.forEach { offset ->
            val sql = "$summarySelect WHERE ${where.joinToString(" AND ")} ORDER BY w.word COLLATE NOCASE LIMIT 1 OFFSET ?"
            content.rawQuery(sql, (args + offset.toString()).toTypedArray()).use { c ->
                if (c.moveToFirst()) out += mapSummary(c)
            }
        }
        return out
    }

    fun eligibleCount(level: Int, topics: Set<Int>): Int {
        val safeLevel = level.coerceIn(0, 5)
        val lower = (safeLevel - 1).coerceAtLeast(0)
        var sql = "SELECT COUNT(*) FROM words WHERE cefr BETWEEN ? AND ?"
        val args = mutableListOf(lower.toString(), safeLevel.toString())
        if (topics.isNotEmpty()) {
            sql += " AND topic IN (${topics.joinToString(",")})"
        }
        return content.rawQuery(sql, args.toTypedArray()).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun distractors(target: WordSummary, seed: Long, count: Int = 3): List<WordSummary> {
        val pool = LinkedHashMap<String, WordSummary>()
        fun collect(sql: String, args: Array<String>) {
            content.rawQuery(sql, args).use { c ->
                while (c.moveToNext() && pool.size < 60) {
                    val item = mapSummary(c)
                    if (item.id != target.id && item.firstSense.isNotBlank() &&
                        item.firstSense.lowercase() != target.firstSense.lowercase()
                    ) pool[item.id] = item
                }
            }
        }
        collect(
            "$summarySelect WHERE w.id != ? AND w.topic = ? AND w.pos = ? ORDER BY w.cefr, w.word LIMIT 60",
            arrayOf(target.id, target.topic.toString(), target.pos),
        )
        if (pool.size < count) {
            collect(
                "$summarySelect WHERE w.id != ? AND w.pos = ? ORDER BY w.cefr, w.word LIMIT 80",
                arrayOf(target.id, target.pos),
            )
        }
        if (pool.size < count) {
            collect(
                "$summarySelect WHERE w.id != ? AND w.topic = ? ORDER BY w.cefr, w.word LIMIT 80",
                arrayOf(target.id, target.topic.toString()),
            )
        }
        if (pool.isEmpty()) return emptyList()
        val list = pool.values.toList()
        val random = kotlin.random.Random(seed)
        return list.shuffled(random).take(count)
    }

    fun galaxyNodes(): List<GalaxyNode> {
        val sql = "SELECT id, word, topic, cefr, image, gx, gy, gz, star_seed FROM words ORDER BY id"
        return content.rawQuery(sql, null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        GalaxyNode(
                            id = c.getString(0),
                            word = c.getString(1),
                            topic = c.getInt(2),
                            cefr = c.getInt(3),
                            image = c.getString(4),
                            x = c.getDouble(5).toFloat(),
                            y = c.getDouble(6).toFloat(),
                            z = c.getDouble(7).toFloat(),
                            seed = c.getDouble(8).toFloat(),
                        )
                    )
                }
            }
        }
    }

    fun randomWord(seed: Long, level: Int? = null): WordSummary? {
        val args = mutableListOf<String>()
        var where = "1=1"
        if (level != null) { where += " AND cefr <= ?"; args += level.toString() }
        val total = content.rawQuery("SELECT COUNT(*) FROM words WHERE $where", args.toTypedArray()).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        if (total == 0) return null
        val offset = kotlin.random.Random(seed).nextInt(total)
        return content.rawQuery(
            "$summarySelect WHERE $where ORDER BY w.word COLLATE NOCASE LIMIT 1 OFFSET ?",
            (args + offset.toString()).toTypedArray(),
        ).use { if (it.moveToFirst()) mapSummary(it) else null }
    }

    fun topicCountsByLevel(level: Int): Map<Int, Int> {
        val sql = "SELECT topic, COUNT(*) FROM words WHERE cefr <= ? GROUP BY topic"
        return content.rawQuery(sql, arrayOf(level.toString())).use { c ->
            buildMap { while (c.moveToNext()) put(c.getInt(0), c.getInt(1)) }
        }
    }

    fun examplesForTyping(limit: Int, seed: Long): List<Pair<WordSummary, String>> {
        val total = content.rawQuery(
            "SELECT COUNT(*) FROM examples e WHERE length(e.text) BETWEEN 18 AND 110",
            null,
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        if (total == 0) return emptyList()
        val random = kotlin.random.Random(seed)
        val offsets = LinkedHashSet<Int>()
        var guard = 0
        while (offsets.size < minOf(limit, total) && guard < limit * 40) {
            offsets += random.nextInt(total)
            guard++
        }
        return offsets.mapNotNull { offset ->
            content.rawQuery(
                """
                SELECT e.text, e.word_id FROM examples e
                WHERE length(e.text) BETWEEN 18 AND 110
                ORDER BY e.word_id, e.idx LIMIT 1 OFFSET ?
                """.trimIndent(),
                arrayOf(offset.toString()),
            ).use { c ->
                if (!c.moveToFirst()) null
                else {
                    val text = c.getString(0)
                    val wid = c.getString(1)
                    val w = word(wid)
                    if (w == null) null else w to text
                }
            }
        }
    }

    private fun <T> queryStrings(sql: String, id: String, mapper: (Cursor) -> T): List<T> {
        return content.rawQuery(sql, arrayOf(id)).use { c ->
            buildList { while (c.moveToNext()) add(mapper(c)) }
        }
    }

    private fun mapSummary(c: Cursor): WordSummary = WordSummary(
        id = c.getString(0),
        word = c.getString(1),
        ipaUk = c.stringOrNull(2),
        ipaUs = c.stringOrNull(3),
        cefr = c.getInt(4),
        topic = c.getInt(5),
        image = c.getString(6),
        pos = c.getString(7).orEmpty(),
        firstSense = c.getString(8).orEmpty(),
    )

    private fun Cursor.stringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

    companion object {
        fun fnv1a(value: String): Long {
            var hash = -3750763034362895579L
            for (ch in value) {
                hash = hash xor ch.code.toLong()
                hash *= 1099511628211L
            }
            return hash
        }
    }
}
