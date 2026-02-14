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
            Log.e(TAG, "❌ Missing THING_APP_KEY / THING_APP_SECRET in AndroidManifest.xml meta-data")
            return
        }

        // ✅ Core SDK init (required)
        ThingHomeSdk.init(this, appKey, appSecret)
        ThingHomeSdk.setDebugMode(true)
        Log.d(TAG, "✅ ThingHomeSdk initialized")
    }

    private fun readThingKeysFromManifest(): Pair<String?, String?> {
        return try {
            val info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val md = info.metaData
            Pair(md?.getString("THING_APP_KEY"), md?.getString("THING_APP_SECRET"))
        } catch (_: Throwable) {
            Pair(null, null)
        }
    }

    companion object {
        private const val TAG = "MainApplication"
    }
}
