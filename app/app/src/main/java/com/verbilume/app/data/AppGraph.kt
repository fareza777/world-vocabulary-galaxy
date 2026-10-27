package com.verbilume.app.data

import android.content.Context
import com.verbilume.app.ads.AdsManager
import com.verbilume.app.billing.PurchaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppGraph(context: Context) {
    val context: Context = context.applicationContext
    private val appContext = this.context
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database = AtlasDatabase(appContext)
    val content = ContentRepository(database)
    val learning = LearningRepository(database, content)
    val settings = SettingsRepository(appContext)
    val audio = AudioController(appContext)
    val notifications = NotificationScheduler(appContext)
    val shareCards = ShareCardFactory(appContext, content)
    val sessions = SessionRepository(content, learning)
    val ads = AdsManager(appContext)
    val billing = PurchaseManager(appContext, settings, ads)

    fun warmUp() {
        notifications.ensureChannel()
        scope.launch {
            runCatching {
                content.meta
                learning.dueCount()
            }
            val current = settings.settings.first()
            if (current.remindersEnabled) {
                notifications.schedule(current.reminderHour, current.reminderMinute)
            }
        }
    }
}
