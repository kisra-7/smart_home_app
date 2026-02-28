package com.example.alrawi_app

import android.app.Application
import android.util.Log
import com.facebook.drawee.backends.pipeline.Fresco
import com.thingclips.smart.home.sdk.ThingHomeSdk
import java.lang.reflect.Proxy

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        try { Fresco.initialize(this) }
        catch (t: Throwable) { Log.w(TAG, "⚠️ Fresco.init failed (continuing)", t) }

        try {
            ThingHomeSdk.init(this)
            ThingHomeSdk.setDebugMode(true)
            Log.d(TAG, "✅ ThingHomeSdk.init OK")
        } catch (t: Throwable) {
            Log.e(TAG, "❌ ThingHomeSdk.init failed", t)
            return
        }

        initBizBundleReflectively()

        // ✅ Official requirement: register AbsBizBundleFamilyService implementation
        registerBizBundleFamilyService()
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

        Log.e(TAG, "❌ BizBundle initializer not found.")
    }

    private fun registerBizBundleFamilyService() {
        val wrapperCandidates = listOf(
            "com.thingclips.smart.bizbundle.TuyaWrapper",
            "com.thingclips.smart.android.bizbundle.TuyaWrapper",
            "com.tuya.smart.bizbundle.TuyaWrapper",
            "com.tuya.smart.android.bizbundle.TuyaWrapper"
        )

        val familyServiceClassCandidates = listOf(
            "com.thingclips.smart.bizbundle.family.api.AbsBizBundleFamilyService",
            "com.thingclips.smart.android.bizbundle.family.api.AbsBizBundleFamilyService",
            "com.tuya.smart.bizbundle.family.api.AbsBizBundleFamilyService",
            "com.tuya.smart.android.bizbundle.family.api.AbsBizBundleFamilyService"
        )

        try {
            val wrapperClass = wrapperCandidates.firstNotNullOfOrNull {
                try { Class.forName(it) } catch (_: Throwable) { null }
            } ?: run {
                Log.w(TAG, "⚠️ TuyaWrapper not found. (Family service registration skipped)")
                return
            }

            val absFamilyClass = familyServiceClassCandidates.firstNotNullOfOrNull {
                try { Class.forName(it) } catch (_: Throwable) { null }
            } ?: run {
                Log.w(TAG, "⚠️ AbsBizBundleFamilyService class not found. (Registration skipped)")
                return
            }

            val register = wrapperClass.methods.firstOrNull { m ->
                m.name == "registerService" && m.parameterTypes.size == 2
            } ?: run {
                Log.w(TAG, "⚠️ TuyaWrapper.registerService not found")
                return
            }

            register.invoke(null, absFamilyClass, BizBundleFamilyServiceImpl.instance)
            Log.d(TAG, "✅ Registered BizBundleFamilyServiceImpl with TuyaWrapper")
        } catch (t: Throwable) {
            Log.e(TAG, "❌ registerBizBundleFamilyService failed", t)
        }
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

    companion object { private const val TAG = "MainApplication" }
}