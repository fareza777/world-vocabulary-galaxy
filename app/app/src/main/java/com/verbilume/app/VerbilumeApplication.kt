package com.verbilume.app

import android.app.Application
import com.verbilume.app.data.AppGraph

class VerbilumeApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.warmUp()
        graph.ads.initialize()
        graph.billing.start()
    }
}
