package com.example.alrawi_app

import android.app.Application
import android.util.Log
import com.thingclips.smart.home.sdk.ThingHomeSdk

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThingHomeSdk.init(this)
        Log.d("MainApplication", "ThingHomeSdk init OK")
    }
}
