package com.example.alrawi_app

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import com.thingclips.smart.home.sdk.ThingHomeSdk

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val (appKey, appSecret) = readThingKeysFromManifest()

        if (appKey.isNullOrBlank() || appSecret.isNullOrBlank()) {
            throw RuntimeException(
                "THING_APP_KEY / THING_APP_SECRET missing in AndroidManifest.xml"
            )
        }

        ThingHomeSdk.init(this, appKey, appSecret)
        Log.d("MainApplication", "ThingHomeSdk initialized with key=$appKey")
    }

    private fun readThingKeysFromManifest(): Pair<String?, String?> {
        return try {
            val appInfo = packageManager.getApplicationInfo(
                packageName,
                PackageManager.GET_META_DATA
            )
            val md = appInfo.metaData
            val key = md?.getString("THING_APP_KEY")
            val secret = md?.getString("THING_APP_SECRET")
            Pair(key, secret)
        } catch (e: Exception) {
            Pair(null, null)
        }
    }
}
