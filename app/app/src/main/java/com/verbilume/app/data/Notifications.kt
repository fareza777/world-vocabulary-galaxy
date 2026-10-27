package com.verbilume.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.verbilume.app.MainActivity
import com.verbilume.app.R
import com.verbilume.app.VerbilumeApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class NotificationScheduler(private val context: Context) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_daily),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_daily_desc)
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun schedule(hour: Int, minute: Int) {
        ensureChannel()
        val now = ZonedDateTime.now()
        var next = now.withHour(hour.coerceIn(0, 23)).withMinute(minute.coerceIn(0, 59)).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay.toMinutes().coerceAtLeast(1), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }

    companion object {
        const val CHANNEL_ID = "daily_study"
        const val UNIQUE_NAME = "verbilume_daily_reminder"
        const val TAG = "verbilume-reminder"
    }
}

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? VerbilumeApplication ?: return@withContext Result.success()
        val graph = app.graph
        val settings = graph.settings.settings.first()
        if (!settings.remindersEnabled) return@withContext Result.success()

        val today = graph.learning.todayStats()
        if (today.reviews >= settings.dailyGoal) return@withContext Result.success()

        val due = graph.learning.dueCount()
        val challenge = graph.learning.challengeToday()
        val word = graph.content.wordOfTheDay(
            LocalDate.now().toString(),
            settings.level,
            settings.effectiveTopics,
        )
        val title: String
        val body: String
        when {
            due > 0 -> {
                title = if (due == 1) "One word is waiting" else "$due words are waiting"
                body = "A few quiet minutes will keep your constellation bright."
            }
            !challenge.first -> {
                title = "Today's challenge is unopened"
                body = "Twelve small questions, one new constellation."
            }
            word != null -> {
                title = "Word of the day · ${word.word}"
                body = word.firstSense.take(110)
            }
            else -> {
                title = "A little light for today"
                body = "Open Verbilume for a few new words."
            }
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext Result.success()
        }
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            return@withContext Result.success()
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route", "practice")
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            7,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, NotificationScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setColor(0xFFB55336.toInt())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(1001, notification)
        Result.success()
    }
}
