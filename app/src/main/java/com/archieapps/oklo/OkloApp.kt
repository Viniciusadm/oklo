package com.archieapps.oklo

import android.app.Application
import com.archieapps.oklo.data.sync.SyncWorker

class OkloApp : Application() {
    lateinit var container: Container
        private set

    override fun onCreate() {
        super.onCreate()
        container = Container(this)
        SyncWorker.schedule(this)
    }
}
