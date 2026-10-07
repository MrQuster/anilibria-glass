package com.vmax.hyperboost

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Controller.init(this)
    }
}
