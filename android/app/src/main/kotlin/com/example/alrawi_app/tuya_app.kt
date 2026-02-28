package com.example.alrawi_app

import android.app.Application
import android.util.Log
import com.thingclips.smart.home.sdk.ThingHomeSdk

class TuyaApp : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            ThingHomeSdk.init(this)
            ThingHomeSdk.setDebugMode(true)
            Log.d(TAG, "ThingHomeSdk initialized from Application")
        } catch (t: Throwable) {
            Log.e(TAG, "ThingHomeSdk init failed", t)
        }
    }

    companion object {
        private const val TAG = "TuyaApp"
    }
}