package com.example.alrawi_app

import android.app.Application
import android.util.Log
import com.facebook.drawee.backends.pipeline.Fresco
import com.thingclips.smart.home.sdk.ThingHomeSdk
import java.lang.reflect.Proxy

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // UI BizBundles use Fresco internally (device panels, images, QR flows, etc.)
        // Init early to avoid runtime crashes when launching BizBundle UI.
        try {
            Fresco.initialize(this)
        } catch (t: Throwable) {
            Log.w(TAG, "⚠️ Fresco.init failed (continuing)", t)
        }

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
        // Known initializer class name variants
        val candidates = listOf(
            // ThingClips
            "com.thingclips.smart.bizbundle.initializer.BizBundleInitializer",
            "com.thingclips.smart.android.bizbundle.initializer.BizBundleInitializer",

            // Tuya
            "com.tuya.smart.bizbundle.initializer.BizBundleInitializer",
            "com.tuya.smart.android.bizbundle.initializer.BizBundleInitializer"
        )

        for (className in candidates) {
            try {
                val initializerClass = Class.forName(className)

                // Try easiest: init(Application)
                initializerClass.methods.firstOrNull { m ->
                    m.name == "init" &&
                        m.parameterTypes.size == 1 &&
                        Application::class.java.isAssignableFrom(m.parameterTypes[0])
                }?.let { m ->
                    m.invoke(null, this)
                    Log.d(TAG, "✅ BizBundle init OK via $className.init(Application)")
                    return
                }

                // Try: init(Application, RouteEventListener, ServiceEventListener)
                val init3 = initializerClass.methods.firstOrNull { m ->
                    m.name == "init" && m.parameterTypes.size == 3 &&
                        Application::class.java.isAssignableFrom(m.parameterTypes[0])
                }

                if (init3 != null) {
                    val routeListenerType = init3.parameterTypes[1]
                    val serviceListenerType = init3.parameterTypes[2]

                    val routeListener = createLoggingProxy(routeListenerType, "RouteEventListener")
                    val serviceListener = createLoggingProxy(serviceListenerType, "ServiceEventListener")

                    init3.invoke(null, this, routeListener, serviceListener)
                    Log.d(TAG, "✅ BizBundle init OK via $className.init(Application, Route, Service)")
                    return
                }

                Log.w(TAG, "⚠️ Found $className but no matching init(...) method signature")
            } catch (t: Throwable) {
                Log.w(TAG, "BizBundle init failed for $className: ${t.javaClass.simpleName}: ${t.message}")
            }
        }

        Log.e(TAG, "❌ BizBundle initializer not found. Activator will crash with 'Must call onCreate(application) first'")
    }

    private fun createLoggingProxy(iface: Class<*>, label: String): Any {
        return Proxy.newProxyInstance(
            iface.classLoader,
            arrayOf(iface)
        ) { _, method, args ->
            // Some interfaces have onFaild(...) callbacks (yes, spelled like that in some SDKs)
            if (method.name.contains("onFail", ignoreCase = true)) {
                Log.e(TAG, "❌ $label callback: method=${method.name} args=${args?.toList()}")
            }
            null
        }
    }

    companion object {
        private const val TAG = "MainApplication"
    }
}
