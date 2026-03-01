package com.example.alrawi_app

import android.app.Application
import android.util.Log
import com.thingclips.smart.home.sdk.ThingHomeSdk

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            ThingHomeSdk.init(this)
            ThingHomeSdk.setDebugMode(true)
            Log.d(TAG, "✅ ThingHomeSdk.init OK")
        } catch (t: Throwable) {
            Log.e(TAG, "❌ ThingHomeSdk.init failed", t)
            return
        }

        initBizBundleReflectively()
    }

    private fun initBizBundleReflectively() {
        val candidates = listOf(
            "com.thingclips.smart.bizbundle.initializer.BizBundleInitializer",
            "com.thingclips.smart.android.bizbundle.initializer.BizBundleInitializer",
            "com.tuya.smart.bizbundle.initializer.BizBundleInitializer",
            "com.tuya.smart.android.bizbundle.initializer.BizBundleInitializer"
        )

        for (className in candidates) {
            try {
                val initializerClass = Class.forName(className)

                // init(Application)
                initializerClass.methods.firstOrNull { m ->
                    m.name == "init" &&
                        m.parameterTypes.size == 1 &&
                        Application::class.java.isAssignableFrom(m.parameterTypes[0])
                }?.let { m ->
                    m.invoke(null, this)
                    Log.d(TAG, "✅ BizBundle init OK via $className.init(Application)")
                    return
                }

                // init(Application, Route, Service)
                val init3 = initializerClass.methods.firstOrNull { m ->
                    m.name == "init" && m.parameterTypes.size == 3 &&
                        Application::class.java.isAssignableFrom(m.parameterTypes[0])
                }

                if (init3 != null) {
                    // pass null listeners (safe)
                    init3.invoke(null, this, null, null)
                    Log.d(TAG, "✅ BizBundle init OK via $className.init(Application, Route, Service)")
                    return
                }

            } catch (t: Throwable) {
                Log.w(TAG, "BizBundle init failed for $className: ${t.message}")
            }
        }

        Log.e(TAG, "❌ BizBundle initializer not found.")
    }

    companion object {
        private const val TAG = "MainApplication"
    }
}