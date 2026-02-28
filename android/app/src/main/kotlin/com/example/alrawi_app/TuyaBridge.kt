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
import java.util.concurrent.atomic.AtomicBoolean

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
    private const val PREFS = "alrawi_tuya_prefs"
    private const val KEY_HOME_ID = "current_home_id"

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

    private fun getActivity(): Activity? = activityRef?.get()

    // ---------- Channel holder ----------
    private var channel: MethodChannel? = null

    @JvmStatic
    fun setChannel(ch: MethodChannel) {
        channel = ch
    }

    // ---------- Preferences ----------
    private fun saveHomeId(ctx: Context, homeId: Long) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_HOME_ID, homeId)
            .apply()
    }

    fun getSavedHomeId(ctx: Context): Long {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_HOME_ID, 0L)
    }

    // ==========================================================
    // ✅ THE FIX: Force BizBundle "current family/home" context
    // so token + relationId are NOT zero in QR activation.
    // ==========================================================
    fun bootstrapBizContext(
        ctx: Context,
        homeId: Long,
        onOk: () -> Unit,
        onErr: (Throwable) -> Unit
    ) {
        if (homeId <= 0L) {
            onErr(IllegalArgumentException("homeId must be > 0"))
            return
        }

        saveHomeId(ctx, homeId)

        // If not logged in yet, still save homeId and return OK.
        // We’ll retry again after login / when opening Biz UI.
        if (!ThingHomeSdk.getUserInstance().isLogin) {
            Log.w(TAG, "bootstrapBizContext: not logged in yet. Saved homeId=$homeId (will retry later).")
            onOk()
            return
        }

        // 1) Shift current family/home (gid) using FamilyManagerCoreKit
        shiftCurrentFamilyReflectively(
            homeId = homeId,
            onShifted = {
                // 2) Warm home detail cache
                warmHomeDetail(homeId = homeId) {
                    Log.d(TAG, "✅ bootstrapBizContext done homeId=$homeId")
                    onOk()
                }
            },
            onErr = { t ->
                Log.e(TAG, "bootstrapBizContext shift failed: ${t.message}", t)
                // Still warm & continue; sometimes shift is async internally
                warmHomeDetail(homeId = homeId) {
                    onOk()
                }
            }
        )
    }

    private fun warmHomeDetail(homeId: Long, done: () -> Unit) {
        try {
            ThingHomeSdk.newHomeInstance(homeId).getHomeDetail(object : IThingHomeResultCallback {
                override fun onSuccess(bean: HomeBean?) {
                    Log.d(TAG, "warmHomeDetail OK homeId=$homeId name=${bean?.name}")
                    done()
                }

                override fun onError(errorCode: String?, errorMsg: String?) {
                    Log.w(TAG, "warmHomeDetail failed: $errorCode $errorMsg (continuing)")
                    done()
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "warmHomeDetail exception (continuing): ${t.message}")
            done()
        }
    }

    private fun shiftCurrentFamilyReflectively(
        homeId: Long,
        onShifted: () -> Unit,
        onErr: (Throwable) -> Unit
    ) {
        val coreKitCandidates = listOf(
            "com.thingclips.smart.family.core.FamilyManagerCoreKit",
            "com.tuya.smart.family.core.FamilyManagerCoreKit",
            "com.thingclips.smart.family.FamilyManagerCoreKit",
            "com.tuya.smart.family.FamilyManagerCoreKit"
        )

        val coreKitClass = coreKitCandidates.firstNotNullOfOrNull { name ->
            try { Class.forName(name) } catch (_: Throwable) { null }
        } ?: run {
            onErr(ClassNotFoundException("FamilyManagerCoreKit not found. Tried: $coreKitCandidates"))
            return
        }

        try {
            val getUseCase = coreKitClass.methods.firstOrNull { it.name == "getFamilyUseCase" && it.parameterTypes.isEmpty() }
                ?: throw NoSuchMethodException("${coreKitClass.name}.getFamilyUseCase() not found")

            val useCase = getUseCase.invoke(null)
                ?: throw IllegalStateException("getFamilyUseCase() returned null")

            // ✅ More robust: accept shift/switch/set methods that take (Long, callback?) or (Long)
            val method = useCase.javaClass.methods.firstOrNull { m ->
                val n = m.name.lowercase()
                val okName = (n.contains("shift") || n.contains("switch") || (n.contains("set") && n.contains("current")))
                val okFirstParam = m.parameterTypes.isNotEmpty() &&
                    (m.parameterTypes[0] == java.lang.Long.TYPE || m.parameterTypes[0] == java.lang.Long::class.java)
                okName && okFirstParam
            } ?: throw NoSuchMethodException("No shift/switch/setCurrentFamily(Long, ...) method found on ${useCase.javaClass.name}")

            val fired = AtomicBoolean(false)
            fun fireOnce() {
                if (fired.compareAndSet(false, true)) {
                    mainHandler.post {
                        Log.d(TAG, "✅ shiftCurrentFamily done (method=${method.name}) homeId=$homeId")
                        onShifted()
                    }
                }
            }

            if (method.parameterTypes.size >= 2) {
                val cbType = method.parameterTypes[1]
                val cbProxy = Proxy.newProxyInstance(cbType.classLoader, arrayOf(cbType)) { _, cbMethod, args ->
                    val n = cbMethod.name.lowercase()
                    if (n.contains("success") || n.contains("complete") || n.contains("finish") || n.contains("shift") || n.contains("switch")) {
                        Log.d(TAG, "shift callback: ${cbMethod.name} args=${args?.toList()}")
                        fireOnce()
                    } else if (n.contains("error") || n.contains("fail")) {
                        Log.e(TAG, "shift callback error: ${cbMethod.name} args=${args?.toList()}")
                        fireOnce()
                    }
                    null
                }

                method.invoke(useCase, homeId, cbProxy)
                // Safety timeout
                mainHandler.postDelayed({ fireOnce() }, 1000)
            } else {
                method.invoke(useCase, homeId)
                mainHandler.postDelayed({ fireOnce() }, 600)
            }
        } catch (t: Throwable) {
            onErr(t)
        }
    }

    // ---------- Entry point ----------
    @JvmStatic
    fun handle(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {

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
                        countryCode, email, password,
                        object : ILoginCallback {
                            override fun onSuccess(user: User?) {
                                // ✅ After login, retry bootstrap with saved homeId
                                val ctx = getActivity()?.applicationContext
                                if (ctx != null) {
                                    val saved = getSavedHomeId(ctx)
                                    if (saved > 0L) {
                                        bootstrapBizContext(
                                            ctx = ctx,
                                            homeId = saved,
                                            onOk = { result.success(true) },
                                            onErr = { _ -> result.success(true) }
                                        )
                                        return
                                    }
                                }
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
                        email, "", countryCode, type,
                        object : IResultCallback {
                            override fun onSuccess() = result.success(true)
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
                        countryCode, email, password, code,
                        object : IRegisterCallback {
                            override fun onSuccess(user: User?) = result.success(true)
                            override fun onError(code: String?, error: String?) {
                                result.error(code ?: "REGISTER_FAILED", error ?: "register failed", null)
                            }
                        }
                    )
                }

                "logout" -> {
                    ThingHomeSdk.getUserInstance().logout(object : ILogoutCallback {
                        override fun onSuccess() = result.success(true)
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
                                val ctx = getActivity()?.applicationContext
                                if (ctx != null) {
                                    bootstrapBizContext(ctx, existing.homeId, onOk = {}, onErr = {})
                                }
                                result.success(
                                    hashMapOf<String, Any?>(
                                        "homeId" to existing.homeId,
                                        "name" to existing.name,
                                        "geoName" to existing.geoName
                                    )
                                )
                                return
                            }

                            ThingHomeSdk.getHomeManagerInstance().createHome(
                                name, 0.0, 0.0, geoName, rooms,
                                object : IThingHomeResultCallback {
                                    override fun onSuccess(bean: HomeBean?) {
                                        if (bean == null) {
                                            result.error("CREATE_HOME_FAILED", "HomeBean is null", null)
                                            return
                                        }
                                        val ctx = getActivity()?.applicationContext
                                        if (ctx != null) {
                                            bootstrapBizContext(ctx, bean.homeId, onOk = {}, onErr = {})
                                        }
                                        result.success(
                                            hashMapOf<String, Any?>(
                                                "homeId" to bean.homeId,
                                                "name" to bean.name,
                                                "geoName" to bean.geoName
                                            )
                                        )
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

                // ✅ Call this from Dart right after you pick a home (important!)
                "setCurrentHome" -> {
                    val activity = getActivity()
                    if (activity == null) {
                        result.error("NO_ACTIVITY", "No foreground Activity available", null)
                        return
                    }
                    val homeId = (call.argument<Number>("homeId") ?: 0).toLong()
                    bootstrapBizContext(
                        ctx = activity.applicationContext,
                        homeId = homeId,
                        onOk = { result.success(true) },
                        onErr = { t -> result.error("BIZ_CONTEXT_FAILED", t.message, null) }
                    )
                }

                // ==========================================================
                // BizBundle UI
                // ==========================================================
                "bizOpenAddDevice" -> {
                    val activity = getActivity()
                    if (activity == null) {
                        result.error("NO_ACTIVITY", "No foreground Activity available", null)
                        return
                    }
                    val homeId = (call.argument<Number>("homeId") ?: 0).toLong()

                    // ✅ ALWAYS bootstrap before opening Biz UI
                    bootstrapBizContext(
                        ctx = activity.applicationContext,
                        homeId = homeId,
                        onOk = {
                            BizBundleActivatorUi.openAddDevice(
                                activity = activity,
                                homeId = homeId,
                                onOk = { result.success(true) },
                                onErr = { t -> result.error("ADD_DEVICE_UI_FAILED", t.message, null) }
                            )
                        },
                        onErr = { t -> result.error("BIZ_CONTEXT_FAILED", t.message, null) }
                    )
                }

                "bizOpenQrScan" -> {
                    val activity = getActivity()
                    if (activity == null) {
                        result.error("NO_ACTIVITY", "No foreground Activity available", null)
                        return
                    }
                    val homeId = (call.argument<Number>("homeId") ?: 0).toLong()

                    // ✅ ALWAYS bootstrap before opening native QR
                    bootstrapBizContext(
                        ctx = activity.applicationContext,
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
                                        val b = Bundle().apply {
                                            putLong("homeId", homeId)
                                            putLong("familyId", homeId)
                                        }
                                        openScanWithBundle.invoke(instance, activity, b)
                                    } else {
                                        val openScan = scanClazz.methods.firstOrNull {
                                            it.name == "openScan" && it.parameterTypes.size == 1
                                        } ?: throw NoSuchMethodException("ScanManager.openScan(Context/*,Bundle*/) not found")

                                        openScan.invoke(instance, activity)
                                    }

                                    result.success(true)
                                } catch (t: Throwable) {
                                    Log.e(TAG, "bizOpenQrScan failed", t)
                                    result.error("QR_SCAN_FAILED", t.message, null)
                                }
                            }
                        },
                        onErr = { t -> result.error("BIZ_CONTEXT_FAILED", t.message, null) }
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