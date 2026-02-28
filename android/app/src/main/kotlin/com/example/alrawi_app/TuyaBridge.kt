package com.example.alrawi_app

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy

import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.android.user.api.ILoginCallback
import com.thingclips.smart.android.user.api.IRegisterCallback
import com.thingclips.smart.android.user.api.ILogoutCallback
import com.thingclips.smart.android.user.bean.User
import com.thingclips.smart.sdk.api.IResultCallback
import com.thingclips.smart.home.sdk.callback.IThingGetHomeListCallback
import com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback
import com.thingclips.smart.home.sdk.bean.HomeBean

object TuyaBridge {

    private const val TAG = "TuyaBridge"
    private val mainHandler = Handler(Looper.getMainLooper())

    // ---------- Activity holder ----------
    private var activityRef: WeakReference<Activity>? = null

    @JvmStatic
    fun bindActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    @JvmStatic
    fun unbindActivity(activity: Activity) {
        val cur = activityRef?.get()
        if (cur === activity) activityRef = null
    }

    val currentActivity: Activity?
        get() = activityRef?.get()

    // ---------- Channel holder (optional events) ----------
    private var channel: MethodChannel? = null

    @JvmStatic
    fun setChannel(ch: MethodChannel) {
        channel = ch
    }

    private fun emit(event: String, args: Any? = null) {
        try {
            channel?.invokeMethod(event, args)
        } catch (t: Throwable) {
            Log.w(TAG, "emit($event) failed: ${t.message}")
        }
    }

    // ==========================================================
    // ✅ Biz context bootstrap (best-effort)
    // ==========================================================
    private fun ensureBizContextAsync(homeId: Long, onOk: () -> Unit, onErr: (Throwable) -> Unit) {
        if (homeId <= 0L) {
            onErr(IllegalArgumentException("homeId must be > 0"))
            return
        }

        mainHandler.post {
            try {
                switchCurrentFamilyReflectively(
                    homeId = homeId,
                    onSwitched = {
                        warmHomeDetail(
                            homeId = homeId,
                            onWarm = {
                                trySetActivatorHomeId(homeId)
                                onOk()
                            },
                            onErr = onErr
                        )
                    },
                    onErr = { t ->
                        // fallback: warm only
                        Log.w(TAG, "⚠️ switchCurrentFamily failed, fallback warm only: ${t.message}")
                        warmHomeDetail(
                            homeId = homeId,
                            onWarm = {
                                trySetActivatorHomeId(homeId)
                                onOk()
                            },
                            onErr = onErr
                        )
                    }
                )
            } catch (t: Throwable) {
                onErr(t)
            }
        }
    }

    private fun warmHomeDetail(homeId: Long, onWarm: () -> Unit, onErr: (Throwable) -> Unit) {
        try {
            val home = ThingHomeSdk.newHomeInstance(homeId)
            home.getHomeDetail(object : IThingHomeResultCallback {
                override fun onSuccess(bean: HomeBean?) {
                    Log.d(TAG, "✅ warmHomeDetail OK homeId=$homeId name=${bean?.name}")
                    onWarm()
                }

                override fun onError(errorCode: String?, errorMsg: String?) {
                    onErr(RuntimeException("warmHomeDetail failed: ${errorCode ?: ""} ${errorMsg ?: ""}".trim()))
                }
            })
        } catch (t: Throwable) {
            onErr(t)
        }
    }

    private fun switchCurrentFamilyReflectively(homeId: Long, onSwitched: () -> Unit, onErr: (Throwable) -> Unit) {
        val candidates = listOf(
            "com.thingclips.smart.commonbiz.family.FamilyManagerCoreKit",
            "com.thingclips.smart.family.api.FamilyManagerCoreKit",
            "com.thingclips.smart.family.FamilyManagerCoreKit",
            "com.tuya.smart.commonbiz.family.FamilyManagerCoreKit"
        )

        var last: Throwable? = null

        for (cn in candidates) {
            try {
                val clazz = Class.forName(cn)
                val instance = getSingletonInstance(clazz)

                val m = clazz.methods.firstOrNull { method ->
                    method.name.contains("setCurrent", ignoreCase = true) &&
                        method.parameterTypes.isNotEmpty() &&
                        (method.parameterTypes[0] == java.lang.Long.TYPE || method.parameterTypes[0] == java.lang.Long::class.java)
                } ?: throw NoSuchMethodException("No setCurrent* method found on $cn")

                val params = m.parameterTypes

                // setCurrent*(long)
                if (params.size == 1) {
                    m.invoke(instance, homeId)
                    Log.d(TAG, "✅ switchCurrentFamily OK via $cn.${m.name}(long)")
                    onSwitched()
                    return
                }

                // setCurrent*(long, callback)
                if (params.size >= 2) {
                    val cbType = params[1]
                    val cb = Proxy.newProxyInstance(
                        cbType.classLoader,
                        arrayOf(cbType)
                    ) { _, method, args ->
                        when {
                            method.name.equals("onSuccess", ignoreCase = true) -> {
                                Log.d(TAG, "✅ switchCurrentFamily callback onSuccess")
                                onSwitched()
                            }
                            method.name.equals("onError", ignoreCase = true) -> {
                                val code = args?.getOrNull(0)?.toString() ?: ""
                                val msg = args?.getOrNull(1)?.toString() ?: ""
                                onErr(RuntimeException("switchCurrentFamily onError: $code $msg".trim()))
                            }
                        }
                        null
                    }

                    val invokeArgs = arrayOfNulls<Any>(params.size)
                    invokeArgs[0] = homeId
                    invokeArgs[1] = cb
                    m.invoke(instance, *invokeArgs)

                    Log.d(TAG, "➡️ switchCurrentFamily invoked via $cn.${m.name}(long, cb)")
                    return
                }

            } catch (t: Throwable) {
                last = t
            }
        }

        onErr(last ?: RuntimeException("switchCurrentFamily failed (no candidates matched)"))
    }

    private fun getSingletonInstance(clazz: Class<*>): Any? {
        try { return clazz.getDeclaredField("INSTANCE").get(null) } catch (_: Throwable) {}
        try {
            val gi = clazz.methods.firstOrNull { it.name == "getInstance" && it.parameterTypes.isEmpty() }
            if (gi != null) return gi.invoke(null)
        } catch (_: Throwable) {}
        return null
    }

    private fun trySetActivatorHomeId(homeId: Long) {
        val candidates = listOf(
            "com.thingclips.smart.activator.plug.mesosphere.ThingDeviceActivatorManager",
            "com.thingclips.smart.activator.ThingDeviceActivatorManager",
            "com.tuya.smart.activator.ThingDeviceActivatorManager"
        )
        for (cn in candidates) {
            try {
                val clazz = Class.forName(cn)
                val instance = try { clazz.getDeclaredField("INSTANCE").get(null) } catch (_: Throwable) { null }
                clazz.methods.firstOrNull { m ->
                    m.name == "setHomeId" &&
                        m.parameterTypes.size == 1 &&
                        (m.parameterTypes[0] == java.lang.Long.TYPE || m.parameterTypes[0] == java.lang.Long::class.java)
                }?.invoke(instance, homeId)
                Log.d(TAG, "✅ Activator setHomeId OK via $cn")
                return
            } catch (_: Throwable) {}
        }
    }

    // ---------- Entry point used by MainActivity ----------
    @JvmStatic
    fun handle(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {

                // ==========================================================
                // Core
                // ==========================================================
                "initSdk" -> result.success(true)

                "isLoggedIn" -> result.success(ThingHomeSdk.getUserInstance().isLogin)

                // ==========================================================
                // Auth (Email)
                // ==========================================================
                "loginByEmail" -> {
                    val countryCode = call.argument<String>("countryCode") ?: ""
                    val email = call.argument<String>("email") ?: ""
                    val password = call.argument<String>("password") ?: ""

                    ThingHomeSdk.getUserInstance().loginWithEmail(
                        countryCode,
                        email,
                        password,
                        object : ILoginCallback {
                            override fun onSuccess(user: User?) {
                                result.success(true)
                            }

                            override fun onError(code: String?, error: String?) {
                                result.error(code ?: "LOGIN_FAILED", error ?: "login failed", null)
                            }
                        }
                    )
                }

                "sendEmailCode" -> {
                    val countryCode = call.argument<String>("countryCode") ?: ""
                    val email = call.argument<String>("email") ?: ""
                    val type = call.argument<Int>("type") ?: 1

                    ThingHomeSdk.getUserInstance().sendVerifyCodeWithUserName(
                        email,
                        "",
                        countryCode,
                        type,
                        object : IResultCallback {
                            override fun onSuccess() { result.success(true) }
                            override fun onError(code: String?, error: String?) {
                                result.error(code ?: "SEND_CODE_FAILED", error ?: "send code failed", null)
                            }
                        }
                    )
                }

                "registerEmail" -> {
                    val countryCode = call.argument<String>("countryCode") ?: ""
                    val email = call.argument<String>("email") ?: ""
                    val password = call.argument<String>("password") ?: ""
                    val code = call.argument<String>("code") ?: ""

                    ThingHomeSdk.getUserInstance().registerAccountWithEmail(
                        countryCode,
                        email,
                        password,
                        code,
                        object : IRegisterCallback {
                            override fun onSuccess(user: User?) { result.success(true) }
                            override fun onError(code: String?, error: String?) {
                                result.error(code ?: "REGISTER_FAILED", error ?: "register failed", null)
                            }
                        }
                    )
                }

                "logout" -> {
                    ThingHomeSdk.getUserInstance().logout(object : ILogoutCallback {
                        override fun onSuccess() { result.success(true) }
                        override fun onError(code: String?, error: String?) {
                            result.error(code ?: "LOGOUT_FAILED", error ?: "logout failed", null)
                        }
                    })
                }

                // ==========================================================
                // Home
                // ==========================================================
                "getHomeList" -> {
                    ThingHomeSdk.getHomeManagerInstance().queryHomeList(object : IThingGetHomeListCallback {
                        override fun onSuccess(homeBeans: MutableList<HomeBean>?) {
                            val list = (homeBeans ?: mutableListOf()).map { hb ->
                                hashMapOf<String, Any?>(
                                    "homeId" to hb.homeId,
                                    "name" to hb.name,
                                    "geoName" to hb.geoName
                                )
                            }
                            result.success(list)
                        }

                        override fun onError(errorCode: String?, error: String?) {
                            result.error(errorCode ?: "HOME_LIST_FAILED", error ?: "queryHomeList failed", null)
                        }
                    })
                }

                "ensureHome" -> {
                    val name = call.argument<String>("name") ?: "My Home"
                    val geoName = call.argument<String>("geoName") ?: "Oman"
                    val rooms = call.argument<List<String>>("rooms") ?: listOf("Living Room")

                    ThingHomeSdk.getHomeManagerInstance().queryHomeList(object : IThingGetHomeListCallback {
                        override fun onSuccess(homeBeans: MutableList<HomeBean>?) {
                            val existing = homeBeans?.firstOrNull()
                            if (existing != null) {
                                result.success(hashMapOf<String, Any?>(
                                    "homeId" to existing.homeId,
                                    "name" to existing.name,
                                    "geoName" to existing.geoName
                                ))
                                return
                            }

                            ThingHomeSdk.getHomeManagerInstance().createHome(
                                name,
                                0.0,
                                0.0,
                                geoName,
                                rooms,
                                object : IThingHomeResultCallback {
                                    override fun onSuccess(bean: HomeBean?) {
                                        if (bean == null) {
                                            result.error("CREATE_HOME_FAILED", "HomeBean is null", null)
                                            return
                                        }
                                        result.success(hashMapOf<String, Any?>(
                                            "homeId" to bean.homeId,
                                            "name" to bean.name,
                                            "geoName" to bean.geoName
                                        ))
                                    }

                                    override fun onError(errorCode: String?, errorMsg: String?) {
                                        result.error(errorCode ?: "CREATE_HOME_FAILED", errorMsg ?: "createHome failed", null)
                                    }
                                }
                            )
                        }

                        override fun onError(errorCode: String?, error: String?) {
                            result.error(errorCode ?: "HOME_LIST_FAILED", error ?: "queryHomeList failed", null)
                        }
                    })
                }

                // ==========================================================
                // ✅ NEW: Ensure Biz context
                // ==========================================================
                "ensureBizContext" -> {
                    val homeId = (call.argument<Number>("homeId") ?: 0).toLong()
                    ensureBizContextAsync(
                        homeId = homeId,
                        onOk = { result.success(true) },
                        onErr = { t -> result.error("ENSURE_BIZ_CONTEXT_FAILED", t.message, null) }
                    )
                }

                // ==========================================================
                // BizBundle UI
                // ==========================================================
                "bizOpenQrScan" -> {
                    val activity = currentActivity ?: run {
                        result.error("NO_ACTIVITY", "No foreground Activity available", null)
                        return
                    }
                    val ctx: Context = activity
                    val homeId = (call.argument<Number>("homeId") ?: 0).toLong()

                    ensureBizContextAsync(
                        homeId = homeId,
                        onOk = {
                            mainHandler.post {
                                try {
                                    val scanClazz = Class.forName("com.thingclips.smart.activator.scan.qrcode.ScanManager")
                                    val instance = scanClazz.getDeclaredField("INSTANCE").get(null)

                                    val openScanWithBundle = scanClazz.methods.firstOrNull {
                                        it.name == "openScan" && it.parameterTypes.size == 2
                                    }

                                    if (openScanWithBundle != null) {
                                        val bundle = Bundle().apply {
                                            putLong("homeId", homeId)
                                            putLong("currentHomeId", homeId)
                                            putLong("familyId", homeId)
                                            putLong("gid", homeId)
                                        }
                                        openScanWithBundle.invoke(instance, ctx, bundle)
                                        result.success(true)
                                        return@post
                                    }

                                    val openScan = scanClazz.methods.firstOrNull {
                                        it.name == "openScan" && it.parameterTypes.size == 1
                                    } ?: throw NoSuchMethodException("ScanManager.openScan(Context/*,Bundle*/) not found")

                                    openScan.invoke(instance, ctx)
                                    result.success(true)
                                } catch (t: Throwable) {
                                    Log.e(TAG, "bizOpenQrScan failed", t)
                                    result.error("QR_SCAN_FAILED", t.message, null)
                                }
                            }
                        },
                        onErr = { t ->
                            result.error("ENSURE_BIZ_CONTEXT_FAILED", t.message, null)
                        }
                    )
                }

                "bizOpenAddDevice" -> {
                    val activity = currentActivity ?: run {
                        result.error("NO_ACTIVITY", "No foreground Activity available", null)
                        return
                    }
                    val homeId = (call.argument<Number>("homeId") ?: 0).toLong()

                    ensureBizContextAsync(
                        homeId = homeId,
                        onOk = {
                            BizBundleActivatorUi.openAddDevice(
                                activity = activity,
                                homeId = homeId,
                                onOk = { result.success(true) },
                                onErr = { t -> result.error("ADD_DEVICE_UI_FAILED", t.message, null) }
                            )
                        },
                        onErr = { t ->
                            result.error("ENSURE_BIZ_CONTEXT_FAILED", t.message, null)
                        }
                    )
                }

                else -> result.notImplemented()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Bridge handler crash prevented", t)
            result.error("NATIVE_BRIDGE_ERROR", t.message, null)
        }
    }
}