package com.example.alrawi_app

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log

object BizBundleActivatorUi {
    private const val TAG = "BizBundleActivatorUi"
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Your APK shows the real class is under:
     * com.thingclips.smart.activator.plug.mesosphere.ThingDeviceActivatorManager
     */
    private val managerClassCandidates = listOf(
        "com.thingclips.smart.activator.plug.mesosphere.ThingDeviceActivatorManager",
        "com.thingclips.smart.activator.ThingDeviceActivatorManager",
        "com.tuya.smart.activator.ThingDeviceActivatorManager"
    )

    fun openAddDevice(
        activity: Activity,
        homeId: Long,
        onDevicesAdded: ((List<String>) -> Unit)? = null,
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

                // 1) Best-case: startDeviceActiveAction(Activity, long, Listener)
                if (onDevicesAdded != null) {
                    val listenerProxy = buildDeviceActiveListenerProxy(mgrClazz, onDevicesAdded)

                    val overload3 = mgrClazz.methods.firstOrNull { m ->
                        m.name == "startDeviceActiveAction" &&
                            m.parameterTypes.size == 3 &&
                            Activity::class.java.isAssignableFrom(m.parameterTypes[0]) &&
                            (m.parameterTypes[1] == java.lang.Long.TYPE || m.parameterTypes[1] == java.lang.Long::class.java)
                    }

                    if (overload3 != null && listenerProxy != null) {
                        overload3.invoke(instance, activity, homeId, listenerProxy)
                        onOk()
                        return@post
                    }

                    // 2) Otherwise, attach listener via addListener(...) if possible.
                    tryAttachDeviceActiveListener(mgrClazz, instance, onDevicesAdded)
                }

                // 3) Try overload: startDeviceActiveAction(Activity, long)
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

                // 4) Otherwise: setHomeId(long) if exists then startDeviceActiveAction(Activity)
                mgrClazz.methods.firstOrNull { m ->
                    m.name == "setHomeId" &&
                        m.parameterTypes.size == 1 &&
                        (m.parameterTypes[0] == java.lang.Long.TYPE || m.parameterTypes[0] == java.lang.Long::class.java)
                }?.invoke(instance, homeId)

                val overload1 = mgrClazz.methods.firstOrNull { m ->
                    m.name == "startDeviceActiveAction" &&
                        m.parameterTypes.size == 1 &&
                        Activity::class.java.isAssignableFrom(m.parameterTypes[0])
                } ?: throw NoSuchMethodException(
                    "startDeviceActiveAction(Activity/Activity,long/Activity,long,listener) not found on ${mgrClazz.name}"
                )

                overload1.invoke(instance, activity)
                onOk()
            } catch (t: Throwable) {
                Log.e(TAG, "openAddDevice failed", t)
                onErr(t)
            }
        }
    }

    private fun buildDeviceActiveListenerProxy(
        mgrClazz: Class<*>,
        onDevicesAdded: (List<String>) -> Unit
    ): Any? {
        // Try to find the listener type from a startDeviceActiveAction overload
        val anyListenerParam = mgrClazz.methods
            .firstOrNull { it.name == "startDeviceActiveAction" && it.parameterTypes.size == 3 }
            ?.parameterTypes
            ?.getOrNull(2)
            ?: return null

        return java.lang.reflect.Proxy.newProxyInstance(
            anyListenerParam.classLoader,
            arrayOf(anyListenerParam)
        ) { _, method, args ->
            try {
                if (method.name.equals("onDevicesAdd", ignoreCase = true)) {
                    val first = args?.firstOrNull()
                    val devIds = when (first) {
                        is List<*> -> first.filterIsInstance<String>()
                        is Array<*> -> first.filterIsInstance<String>()
                        else -> emptyList()
                    }
                    if (devIds.isNotEmpty()) onDevicesAdded(devIds)
                }
            } catch (_: Throwable) {
            }
            null
        }
    }

    private fun tryAttachDeviceActiveListener(
        mgrClazz: Class<*>,
        instance: Any?,
        onDevicesAdded: (List<String>) -> Unit
    ) {
        val addListener = mgrClazz.methods.firstOrNull { m ->
            m.name == "addListener" && m.parameterTypes.size == 1
        } ?: return

        val listenerInterface = addListener.parameterTypes[0]

        val proxy = java.lang.reflect.Proxy.newProxyInstance(
            listenerInterface.classLoader,
            arrayOf(listenerInterface)
        ) { _, method, args ->
            try {
                if (method.name.equals("onDevicesAdd", ignoreCase = true)) {
                    val first = args?.firstOrNull()
                    val devIds = when (first) {
                        is List<*> -> first.filterIsInstance<String>()
                        is Array<*> -> first.filterIsInstance<String>()
                        else -> emptyList()
                    }
                    if (devIds.isNotEmpty()) onDevicesAdded(devIds)
                }
            } catch (_: Throwable) {
            }
            null
        }

        try {
            addListener.invoke(instance, proxy)
        } catch (_: Throwable) {
        }
    }

    private fun resolveFirstExistingClass(candidates: List<String>): Class<*>? {
        for (name in candidates) {
            try {
                return Class.forName(name)
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun tryGetKotlinObjectInstance(clazz: Class<*>): Any? {
        return try {
            clazz.getDeclaredField("INSTANCE").get(null)
        } catch (_: Throwable) {
            null
        }
    }
}