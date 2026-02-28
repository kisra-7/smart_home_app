package com.example.alrawi_app

import android.app.Application
import android.util.Log
import com.facebook.drawee.backends.pipeline.Fresco
import com.thingclips.smart.home.sdk.ThingHomeSdk
import java.lang.reflect.Proxy

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

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

        // ✅ After BizBundle init: if logged in + have saved homeId, bootstrap.
        try {
            if (ThingHomeSdk.getUserInstance().isLogin) {
                val savedHomeId = TuyaBridge.getSavedHomeId(this)
                if (savedHomeId > 0L) {
                    TuyaBridge.bootstrapBizContext(
                        ctx = this,
                        homeId = savedHomeId,
                        onOk = { Log.d(TAG, "✅ bootstrapBizContext at app start OK homeId=$savedHomeId") },
                        onErr = { t -> Log.w(TAG, "bootstrapBizContext at app start failed: ${t.message}") }
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "bootstrapBizContext at app start crashed: ${t.message}")
        }
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

                initializerClass.methods.firstOrNull { m ->
                    m.name == "init" &&
                        m.parameterTypes.size == 1 &&
                        Application::class.java.isAssignableFrom(m.parameterTypes[0])
                }?.let { m ->
                    m.invoke(null, this)
                    Log.d(TAG, "✅ BizBundle init OK via $className.init(Application)")
                    return
                }

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

                Log.w(TAG, "⚠️ Found $className but no matching init(...) signature")
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