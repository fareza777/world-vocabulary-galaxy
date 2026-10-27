package com.verbilume.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

class AtlasDatabase(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("atlas_assets", Context.MODE_PRIVATE)

    val meta: ContentMeta by lazy {
        val json = appContext.assets.open("meta.json").bufferedReader().use { it.readText() }
        ContentMeta.parse(json)
    }

    val content: SQLiteDatabase by lazy {
        ensureContentCopied()
        SQLiteDatabase.openDatabase(
            contentFile().absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
    }

    val progress: SQLiteDatabase by lazy {
        val db = SQLiteDatabase.openOrCreateDatabase(progressFile(), null)
        db.enableWriteAheadLogging()
        db.rawQuery("PRAGMA synchronous=NORMAL", null).use { it.moveToFirst() }
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cards (
                word_id TEXT PRIMARY KEY,
                word TEXT NOT NULL DEFAULT '',
                topic INTEGER NOT NULL DEFAULT 0,
                cefr INTEGER NOT NULL DEFAULT 0,
                due INTEGER NOT NULL,
                stability REAL NOT NULL DEFAULT 0,
                difficulty REAL NOT NULL DEFAULT 0,
                elapsed_days INTEGER NOT NULL DEFAULT 0,
                scheduled_days INTEGER NOT NULL DEFAULT 0,
                learning_steps INTEGER NOT NULL DEFAULT 0,
                reps INTEGER NOT NULL DEFAULT 0,
                lapses INTEGER NOT NULL DEFAULT 0,
                state INTEGER NOT NULL DEFAULT 0,
                last_review INTEGER,
                first_seen INTEGER NOT NULL,
                last_grade INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS cards_due_idx ON cards(due)")
        db.execSQL("CREATE INDEX IF NOT EXISTS cards_state_idx ON cards(state)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bookmarks (
                word_id TEXT PRIMARY KEY,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS day_stats (
                day TEXT PRIMARY KEY,
                reviews INTEGER NOT NULL DEFAULT 0,
                new_words INTEGER NOT NULL DEFAULT 0,
                correct INTEGER NOT NULL DEFAULT 0,
                total INTEGER NOT NULL DEFAULT 0,
                xp INTEGER NOT NULL DEFAULT 0,
                seconds INTEGER NOT NULL DEFAULT 0,
                sessions INTEGER NOT NULL DEFAULT 0,
                challenge_done INTEGER NOT NULL DEFAULT 0,
                challenge_score INTEGER NOT NULL DEFAULT 0,
                typing_chars INTEGER NOT NULL DEFAULT 0,
                typing_correct INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS achievements (
                id TEXT PRIMARY KEY,
                unlocked_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                started_at INTEGER NOT NULL,
                ended_at INTEGER NOT NULL,
                mode TEXT NOT NULL,
                reviewed INTEGER NOT NULL,
                correct INTEGER NOT NULL,
                xp INTEGER NOT NULL,
                seconds INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS search_history (
                query TEXT PRIMARY KEY,
                used_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS typing_runs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ran_at INTEGER NOT NULL,
                mode TEXT NOT NULL,
                chars INTEGER NOT NULL,
                correct INTEGER NOT NULL,
                wpm INTEGER NOT NULL,
                accuracy INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db
    }

    private fun contentFile(): File = File(appContext.noBackupFilesDir, "content-v${meta.version.hashCode()}.db")

    fun progressFile(): File = File(appContext.noBackupFilesDir, "progress.db")

    fun contentSourcePath(): String = contentFile().absolutePath

    private fun ensureContentCopied() {
        val file = contentFile()
        val wanted = meta.version
        if (file.exists() && prefs.getString("content_version", null) == wanted && file.length() > 1_000_000) {
            return
        }
        val temp = File(file.parentFile, file.name + ".tmp")
        appContext.assets.open("atlas.db").use { input ->
            temp.outputStream().use { output ->
                input.copyTo(output, 256 * 1024)
            }
        }
        if (file.exists()) file.delete()
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
        prefs.edit().putString("content_version", wanted).apply()
        // Remove content databases from old versions.
        file.parentFile?.listFiles()?.filter {
            it.name.startsWith("content-v") && it.name.endsWith(".db") && it != file
        }?.forEach { it.delete() }
    }
}
