package com.kairosera

import android.app.Application
import com.kairosera.core.notifications.NotificationChannels
import com.kairosera.feature.widgets.Widgets
import kotlinx.coroutines.launch

class KairosApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.ensure(this)
        container.appScope.launch {
            container.seedDefaults()
            // Startup consistency check: recreate any missing alarm or notification from the database.
            container.scheduler.rebuild("startup")
            // Keeps placed widgets in step with the app; does nothing when no widget is placed.
            Widgets.startSync(this@KairosApp, container)
        }
    }
}
