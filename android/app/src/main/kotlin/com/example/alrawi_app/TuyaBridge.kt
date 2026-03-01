package com.example.alrawi_app

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.thingclips.smart.android.user.api.ILoginCallback
import com.thingclips.smart.android.user.api.ILogoutCallback
import com.thingclips.smart.android.user.api.IRegisterCallback
import com.thingclips.smart.android.user.bean.User
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.home.sdk.bean.HomeBean
import com.thingclips.smart.home.sdk.callback.IThingGetHomeListCallback
import com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback
import com.thingclips.smart.sdk.api.IResultCallback
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    private val currentActivity: Activity?
        get() = activityRef?.get()

    // ---------- Channel holder (optional events) ----------
    private var channel: MethodChannel? = null

    @JvmStatic
    fun setChannel(ch: MethodChannel) {
        channel = ch
    }

    // ==========================================================
    // Public entry point from MainActivity
    // ==========================================================
    @JvmStatic
    fun handle(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {

                // ---------------- Core ----------------
                "initSdk" -> result.success(true)

                "isLoggedIn" -> result.success(ThingHomeSdk.getUserInstance().isLogin)

                // ---------------- Auth (Email) ----------------
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
                                // IMPORTANT for BizBundle:
                                // Some builds require calling wrapper onLogin AFTER login.
                                tryCallWrapperOnLogin()
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
                        countryCode,
                        email,
                        password,
                        code,
                        object : IRegisterCallback {
                            override fun onSuccess(user: User?) {
                                tryCallWrapperOnLogin()
                                result.success(true)
                            }

                            override fun onError(code: String?, error: String?) {
                                result.error(code ?: "REGISTER_FAILED", error ?: "register failed", null)
                            }
                        }
                    )
                }

                "logout" -> {
                    ThingHomeSdk.getUserInstance().logout(object : ILogoutCallback {
                        override fun onSuccess() {
                            tryCallWrapperOnLogout()
                            result.success(true)
                        }

                        override fun onError(code: String?, error: String?) {
                            result.error(code ?: "LOGOUT_FAILED", error ?: "logout failed", null)
                        }
                    })
                }

                // ---------------- Home ----------------
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

                // ---------------- BizBundle context (CRITICAL) ----------------
                "ensureBizContext" -> {
                    val homeId = (call.argument<Number>("homeId") ?: 0).toLong()
                    ensureBizContext(homeId) { ok, err ->
                        if (ok) result.success(true)
                        else result.error("ENSURE_BIZ_CONTEXT_FAILED", err ?: "unknown", null)
                    }
                }

                // ---------------- BizBundle UI ----------------
                "bizOpenAddDevice" -> {
                    val activity = currentActivity ?: run {
                        result.error("NO_ACTIVITY", "No foreground Activity available", null)
                        return
                    }
                    val homeId = (call.argument<Number>("homeId") ?: 0).toLong()

                    ensureBizContext(homeId) { ok, err ->
                        if (!ok) {
                            result.error("ENSURE_BIZ_CONTEXT_FAILED", err ?: "unknown", null)
                            return@ensureBizContext
                        }
                        BizBundleActivatorUi.openAddDevice(
                            activity = activity,
                            homeId = homeId,
                            onOk = { result.success(true) },
                            onErr = { t -> result.error("ADD_DEVICE_UI_FAILED", t.message, null) }
                        )
                    }
                }

                "bizOpenQrScan" -> {
                    val activity = currentActivity ?: run {
                        result.error("NO_ACTIVITY", "No foreground Activity available", null)
                        return
                    }
                    val homeId = (call.argument<Number>("homeId") ?: 0).toLong()

                    ensureBizContext(homeId) { ok, err ->
                        if (!ok) {
                            result.error("ENSURE_BIZ_CONTEXT_FAILED", err ?: "unknown", null)
                            return@ensureBizContext
                        }
                        mainHandler.post {
                            try {
                                // Official working class in your build:
                                val scanClazz = Class.forName("com.thingclips.smart.activator.scan.qrcode.ScanManager")
                                val instance = scanClazz.getDeclaredField("INSTANCE").get(null)

                                // Prefer openScan(Context, Bundle)
                                val m2 = scanClazz.methods.firstOrNull {
                                    it.name == "openScan" && it.parameterTypes.size == 2
                                }
                                if (m2 != null) {
                                    val bundle = Bundle().apply {
                                        putLong("homeId", homeId)
                                        putString("homeName", lastHomeName ?: "")
                                    }
                                    m2.invoke(instance, activity /* Context */, bundle)
                                    result.success(true)
                                    return@post
                                }

                                // Fallback openScan(Context)
                                val m1 = scanClazz.methods.firstOrNull {
                                    it.name == "openScan" && it.parameterTypes.size == 1
                                } ?: throw NoSuchMethodException("ScanManager.openScan(Context/*,Bundle*/) not found")

                                m1.invoke(instance, activity /* Context */)
                                result.success(true)
                            } catch (t: Throwable) {
                                Log.e(TAG, "bizOpenQrScan failed", t)
                                result.error("QR_SCAN_FAILED", t.message, null)
                            }
                        }
                    }
                }

                else -> result.notImplemented()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Bridge handler crash prevented", t)
            result.error("NATIVE_BRIDGE_ERROR", t.message, null)
        }
    }

    // ==========================================================
    // Internal: ensure BizBundle has CURRENT HOME (official logic)
    // ==========================================================
    private var lastHomeName: String? = null

    private fun ensureBizContext(homeId: Long, done: (Boolean, String?) -> Unit) {
        Log.d(TAG, "➡️ ensureBizContext(homeId=$homeId)")

        // 1) Warm home detail (your logs show this works)
        warmHomeDetail(homeId) { ok, nameOrErr ->
            if (!ok) {
                done(false, nameOrErr)
                return@warmHomeDetail
            }
            val homeName = nameOrErr ?: ""
            lastHomeName = homeName
            Log.d(TAG, "✅ warmHomeDetail OK homeId=$homeId name=$homeName")

            // 2) Official: MicroServiceManager + AbsBizBundleFamilyService shiftCurrentFamily
            val shifted = tryShiftCurrentFamily(homeId, homeName)
            if (!shifted) {
                // If you don’t add the family dependency, this will stay false and QR will keep failing.
                done(false, "Family service not available. Ensure you added thingsmart-bizbundle-family dependency.")
                return@warmHomeDetail
            }

            done(true, null)
        }
    }

    private fun warmHomeDetail(homeId: Long, cb: (Boolean, String?) -> Unit) {
        try {
            val latch = CountDownLatch(1)
            var ok = false
            var name: String? = null
            var err: String? = null

            ThingHomeSdk.newHomeInstance(homeId).getHomeDetail(object : IThingHomeResultCallback {
                override fun onSuccess(bean: HomeBean?) {
                    ok = true
                    name = bean?.name ?: ""
                    latch.countDown()
                }

                override fun onError(errorCode: String?, errorMsg: String?) {
                    ok = false
                    err = "${errorCode ?: "HOME_DETAIL_FAILED"}: ${errorMsg ?: "getHomeDetail failed"}"
                    latch.countDown()
                }
            })

            // Small wait so we don't race opening BizBundle UI immediately.
            val completed = latch.await(2500, TimeUnit.MILLISECONDS)
            if (!completed) {
                cb(false, "HOME_DETAIL_TIMEOUT")
            } else {
                cb(ok, if (ok) name else err)
            }
        } catch (t: Throwable) {
            cb(false, t.message)
        }
    }

    /**
     * Official docs:
     * AbsBizBundleFamilyService service = MicroServiceManager.getInstance()
     *   .findServiceByInterface(AbsBizBundleFamilyService.class.getName());
     * service.shiftCurrentFamily(homeId, homeName);
     *
     * We do it via reflection to avoid compile errors if dependency isn't added yet.
     */
    private fun tryShiftCurrentFamily(homeId: Long, homeName: String): Boolean {
        try {
            val microServiceManagerClazzNames = listOf(
                // Tuya older
                "com.tuya.smart.android.common.utils.MicroServiceManager",
                // Thingclips builds often expose this:
                "com.thingclips.smart.android.common.utils.MicroServiceManager",
                // Framework (your logs show MicroServiceManagerImpl; facade is often here)
                "com.thingclips.smart.framework.service.MicroServiceManager"
            )

            val microClazz = microServiceManagerClazzNames.firstNotNullOfOrNull { name ->
                runCatching { Class.forName(name) }.getOrNull()
            } ?: run {
                Log.w(TAG, "⚠️ MicroServiceManager not found in app classpath.")
                return false
            }

            val getInstance = microClazz.methods.firstOrNull { it.name == "getInstance" && it.parameterTypes.isEmpty() }
                ?: run {
                    Log.w(TAG, "⚠️ MicroServiceManager.getInstance() not found.")
                    return false
                }

            val msm = getInstance.invoke(null) ?: run {
                Log.w(TAG, "⚠️ MicroServiceManager.getInstance() returned null.")
                return false
            }

            // We need the AbsBizBundleFamilyService class name string
            // In most BizBundle builds it is resolvable by simple name via the service manager.
            val absFamilyCandidates = listOf(
                "com.thingclips.smart.bizbundle.family.api.AbsBizBundleFamilyService",
                "com.tuya.smart.bizbundle.family.api.AbsBizBundleFamilyService",
                // Some older docs omit package; manager usually uses full name, but keep candidates.
                "AbsBizBundleFamilyService"
            )

            // Prefer: use Class.forName to get canonical name
            val absFamilyClazz = absFamilyCandidates.firstNotNullOfOrNull { n ->
                runCatching { Class.forName(n) }.getOrNull()
            }

            val familyInterfaceName = absFamilyClazz?.name ?: run {
                // If the class can't be loaded, we still can try by known interface name strings
                // but in practice: you must add thingsmart-bizbundle-family.
                Log.w(TAG, "⚠️ AbsBizBundleFamilyService class not found. Add thingsmart-bizbundle-family.")
                return false
            }

            val findSvc = msm.javaClass.methods.firstOrNull {
                it.name == "findServiceByInterface" && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
            } ?: run {
                Log.w(TAG, "⚠️ MicroServiceManager.findServiceByInterface(String) not found.")
                return false
            }

            val service = findSvc.invoke(msm, familyInterfaceName)
            if (service == null) {
                Log.w(TAG, "⚠️ Family service not registered yet (service=null).")
                return false
            }

            // Call shiftCurrentFamily(long,String) if exists (preferred)
            val shift = service.javaClass.methods.firstOrNull {
                it.name == "shiftCurrentFamily" && it.parameterTypes.size == 2 &&
                        (it.parameterTypes[0] == java.lang.Long.TYPE || it.parameterTypes[0] == java.lang.Long::class.java) &&
                        it.parameterTypes[1] == String::class.java
            }
            if (shift != null) {
                shift.invoke(service, homeId, homeName)
                Log.d(TAG, "✅ shiftCurrentFamily($homeId, $homeName) OK")
            }

            // Also call setCurrentHomeId(long) if exists (some builds rely on it)
            val setHomeId = service.javaClass.methods.firstOrNull {
                it.name == "setCurrentHomeId" && it.parameterTypes.size == 1 &&
                        (it.parameterTypes[0] == java.lang.Long.TYPE || it.parameterTypes[0] == java.lang.Long::class.java)
            }
            if (setHomeId != null) {
                setHomeId.invoke(service, homeId)
                Log.d(TAG, "✅ setCurrentHomeId($homeId) OK")
            }

            return true
        } catch (t: Throwable) {
            Log.e(TAG, "tryShiftCurrentFamily failed", t)
            return false
        }
    }

    private fun tryCallWrapperOnLogin() {
        // Official docs: TuyaWrapper.onLogin() after login
        val wrappers = listOf(
            "com.tuya.smart.wrapper.TuyaWrapper",
            "com.thingclips.smart.wrapper.ThingWrapper",
            "com.thingclips.smart.wrapper.TuyaWrapper"
        )
        try {
            val w = wrappers.firstNotNullOfOrNull { runCatching { Class.forName(it) }.getOrNull() } ?: return
            val m = w.methods.firstOrNull { it.name == "onLogin" && it.parameterTypes.isEmpty() } ?: return
            m.invoke(null)
            Log.d(TAG, "✅ Wrapper.onLogin() called")
        } catch (_: Throwable) {
        }
    }

    private fun tryCallWrapperOnLogout() {
        // Official docs: TuyaWrapper.onLogout(context)
        val wrappers = listOf(
            "com.tuya.smart.wrapper.TuyaWrapper",
            "com.thingclips.smart.wrapper.ThingWrapper",
            "com.thingclips.smart.wrapper.TuyaWrapper"
        )
        try {
            val w = wrappers.firstNotNullOfOrNull { runCatching { Class.forName(it) }.getOrNull() } ?: return
            val m = w.methods.firstOrNull { it.name == "onLogout" && it.parameterTypes.size == 1 } ?: return
            val ctx = currentActivity?.applicationContext ?: return
            m.invoke(null, ctx)
            Log.d(TAG, "✅ Wrapper.onLogout(ctx) called")
        } catch (_: Throwable) {
        }
    }
}