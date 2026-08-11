package com.primalsword.priore

import android.app.Application

class PrioreApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PrioreNotifications.createChannels(this)
    }
}
