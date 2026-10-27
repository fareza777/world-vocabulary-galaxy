package com.verbilume.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("verbi_settings")

enum class ThemeMode { LIGHT, DARK }
enum class Accent { UK, US }

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val displayName: String = "",
    val level: Int = 2,
    val dailyGoal: Int = 30,
    val dailyNewWords: Int = 8,
    val accent: Accent = Accent.US,
    val theme: ThemeMode = ThemeMode.LIGHT,
    val remindersEnabled: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 30,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val autoplayAudio: Boolean = true,
    val showIpa: Boolean = true,
    val showImages: Boolean = true,
    val includeMastered: Boolean = false,
    val reduceMotion: Boolean = false,
    val selectedTopics: Set<Int> = emptySet(),
) {
    val levelLabel: String get() = CEFR_LEVELS[level.coerceIn(0, 5)]
    val effectiveTopics: Set<Int> get() = selectedTopics
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val name = stringPreferencesKey("display_name")
        val level = intPreferencesKey("level")
        val goal = intPreferencesKey("daily_goal")
        val newWords = intPreferencesKey("daily_new_words")
        val accent = stringPreferencesKey("accent")
        val theme = stringPreferencesKey("theme")
        val reminders = booleanPreferencesKey("reminders_enabled")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val sound = booleanPreferencesKey("sound_enabled")
        val haptics = booleanPreferencesKey("haptics_enabled")
        val autoplay = booleanPreferencesKey("autoplay_audio")
        val showIpa = booleanPreferencesKey("show_ipa")
        val showImages = booleanPreferencesKey("show_images")
        val includeMastered = booleanPreferencesKey("include_mastered")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val topics = stringPreferencesKey("selected_topics")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            onboardingComplete = p[Keys.onboarding] ?: false,
            displayName = p[Keys.name] ?: "",
            level = p[Keys.level] ?: 2,
            dailyGoal = p[Keys.goal] ?: 30,
            dailyNewWords = p[Keys.newWords] ?: 8,
            accent = runCatching { Accent.valueOf(p[Keys.accent] ?: "US") }.getOrDefault(Accent.US),
            theme = runCatching { ThemeMode.valueOf(p[Keys.theme] ?: "LIGHT") }.getOrDefault(ThemeMode.LIGHT),
            remindersEnabled = p[Keys.reminders] ?: false,
            reminderHour = p[Keys.reminderHour] ?: 19,
            reminderMinute = p[Keys.reminderMinute] ?: 30,
            soundEnabled = p[Keys.sound] ?: true,
            hapticsEnabled = p[Keys.haptics] ?: true,
            autoplayAudio = p[Keys.autoplay] ?: true,
            showIpa = p[Keys.showIpa] ?: true,
            showImages = p[Keys.showImages] ?: true,
            includeMastered = p[Keys.includeMastered] ?: false,
            reduceMotion = p[Keys.reduceMotion] ?: false,
            selectedTopics = (p[Keys.topics] ?: "").split(",").mapNotNull { it.toIntOrNull() }.toSet(),
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { p ->
            val current = AppSettings(
                onboardingComplete = p[Keys.onboarding] ?: false,
                displayName = p[Keys.name] ?: "",
                level = p[Keys.level] ?: 2,
                dailyGoal = p[Keys.goal] ?: 30,
                dailyNewWords = p[Keys.newWords] ?: 8,
                accent = runCatching { Accent.valueOf(p[Keys.accent] ?: "US") }.getOrDefault(Accent.US),
                theme = runCatching { ThemeMode.valueOf(p[Keys.theme] ?: "LIGHT") }.getOrDefault(ThemeMode.LIGHT),
                remindersEnabled = p[Keys.reminders] ?: false,
                reminderHour = p[Keys.reminderHour] ?: 19,
                reminderMinute = p[Keys.reminderMinute] ?: 30,
                soundEnabled = p[Keys.sound] ?: true,
                hapticsEnabled = p[Keys.haptics] ?: true,
                autoplayAudio = p[Keys.autoplay] ?: true,
                showIpa = p[Keys.showIpa] ?: true,
                showImages = p[Keys.showImages] ?: true,
                includeMastered = p[Keys.includeMastered] ?: false,
                reduceMotion = p[Keys.reduceMotion] ?: false,
                selectedTopics = (p[Keys.topics] ?: "").split(",").mapNotNull { it.toIntOrNull() }.toSet(),
            )
            val next = transform(current)
            p[Keys.onboarding] = next.onboardingComplete
            p[Keys.name] = next.displayName
            p[Keys.level] = next.level
            p[Keys.goal] = next.dailyGoal
            p[Keys.newWords] = next.dailyNewWords
            p[Keys.accent] = next.accent.name
            p[Keys.theme] = next.theme.name
            p[Keys.reminders] = next.remindersEnabled
            p[Keys.reminderHour] = next.reminderHour
            p[Keys.reminderMinute] = next.reminderMinute
            p[Keys.sound] = next.soundEnabled
            p[Keys.haptics] = next.hapticsEnabled
            p[Keys.autoplay] = next.autoplayAudio
            p[Keys.showIpa] = next.showIpa
            p[Keys.showImages] = next.showImages
            p[Keys.includeMastered] = next.includeMastered
            p[Keys.reduceMotion] = next.reduceMotion
            p[Keys.topics] = next.selectedTopics.joinToString(",") { it.toString() }
        }
    }

    suspend fun completeOnboarding(
        name: String,
        level: Int,
        goal: Int,
        newWords: Int,
        accent: Accent,
        reminders: Boolean,
        reminderHour: Int = 19,
        reminderMinute: Int = 30,
        topics: Set<Int> = emptySet(),
    ) {
        update {
            it.copy(
                onboardingComplete = true,
                displayName = name.trim(),
                level = level,
                dailyGoal = goal,
                dailyNewWords = newWords,
                accent = accent,
                remindersEnabled = reminders,
                reminderHour = reminderHour,
                reminderMinute = reminderMinute,
                selectedTopics = topics,
            )
        }
    }
}
