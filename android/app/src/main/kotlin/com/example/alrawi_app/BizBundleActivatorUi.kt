package com.example.alrawi_app

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log

object BizBundleActivatorUi {
    private const val TAG = "BizBundleActivatorUi"
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * IMPORTANT:
     * Your APK shows the real class is under:
     * com.thingclips.smart.activator.plug.mesosphere.ThingDeviceActivatorManager
     *
     * We still keep fallback candidates just in case.
     */
    private val managerClassCandidates = listOf(
        // ✅ Found in your APK (dex strings output)
        "com.thingclips.smart.activator.plug.mesosphere.ThingDeviceActivatorManager",

        // Fallbacks (docs / older namespaces)
        "com.thingclips.smart.activator.ThingDeviceActivatorManager",
        "com.tuya.smart.activator.ThingDeviceActivatorManager"
    )

    fun openAddDevice(
        activity: Activity,
        homeId: Long,
        onOk: () -> Unit,
        onErr: (Throwable) -> Unit
    ) {
        if (homeId <= 0L) {
            onErr(IllegalArgumentException("homeId must be > 0"))
            return
        }

        mainHandler.post {
            try {
                val mgrClazz = resolveFirstExistingClass(managerClassCandidates)
                    ?: throw ClassNotFoundException(
                        "No ThingDeviceActivatorManager found. Tried: $managerClassCandidates"
                    )

                val instance = tryGetKotlinObjectInstance(mgrClazz)

                // Try overload #1: startDeviceActiveAction(Activity, long)
                val overload2 = mgrClazz.methods.firstOrNull { m ->
                    m.name == "startDeviceActiveAction" &&
                            m.parameterTypes.size == 2 &&
                            Activity::class.java.isAssignableFrom(m.parameterTypes[0]) &&
                            (m.parameterTypes[1] == java.lang.Long.TYPE || m.parameterTypes[1] == java.lang.Long::class.java)
                }

                if (overload2 != null) {
                    overload2.invoke(instance, activity, homeId)
                    onOk()
                    return@post
                }

                // Otherwise: setHomeId(long) if exists
                mgrClazz.methods.firstOrNull { m ->
                    m.name == "setHomeId" &&
                            m.parameterTypes.size == 1 &&
                            (m.parameterTypes[0] == java.lang.Long.TYPE || m.parameterTypes[0] == java.lang.Long::class.java)
                }?.invoke(instance, homeId)

                // Then: startDeviceActiveAction(Activity)
                val overload1 = mgrClazz.methods.firstOrNull { m ->
                    m.name == "startDeviceActiveAction" &&
                            m.parameterTypes.size == 1 &&
                            Activity::class.java.isAssignableFrom(m.parameterTypes[0])
                } ?: throw NoSuchMethodException(
                    "startDeviceActiveAction(Activity/Activity,long) not found on ${mgrClazz.name}"
                )

                overload1.invoke(instance, activity)
                onOk()
            } catch (t: Throwable) {
                Log.e(TAG, "openAddDevice failed", t)
                onErr(t)
            }
        }
    }

    private fun resolveFirstExistingClass(candidates: List<String>): Class<*>? {
        for (name in candidates) {
            try {
                return Class.forName(name)
            } catch (_: Throwable) {
                // keep trying
            }
        }
        return null
    }

    /**
     * Works for Kotlin `object` singletons (INSTANCE) and also for static-method classes.
     */
    private fun tryGetKotlinObjectInstance(clazz: Class<*>): Any? {
        return try {
            clazz.getDeclaredField("INSTANCE").get(null)
        } catch (_: Throwable) {
            null
        }
    }
}