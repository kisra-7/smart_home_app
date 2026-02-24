package com.example.alrawi_app

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.lang.ref.WeakReference

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

    private val currentActivity: Activity?
        get() = activityRef?.get()

    // ---------- Channel holder (optional events) ----------
    private var channel: MethodChannel? = null

    @JvmStatic
    fun setChannel(ch: MethodChannel) {
        channel = ch
    }

    @Suppress("unused")
    private fun emit(event: String, args: Any? = null) {
        try {
            channel?.invokeMethod(event, args)
        } catch (t: Throwable) {
            Log.w(TAG, "emit($event) failed: ${t.message}")
        }
    }

    // ==========================================================
    // BizBundle "current family/home" context
    //
    // Why:
    // Native BizBundle QR / AddDevice UI expects a "current family" to be selected.
    // If not shifted, BizBundle token fetching can fail with USER_GROUP_ID_IS_BLANK,
    // leading to gateway QR bind failures (errCode=10002, relationId=0).
    // ==========================================================
    private fun ensureBizCurrentFamily(homeId: Long, onDone: () -> Unit) {
        if (homeId <= 0L) {
            onDone()
            return
        }

        fun warmAndContinue() {
            try {
                // Warm SDK caches (and also makes sure home context exists)
                ThingHomeSdk.newHomeInstance(homeId).getHomeDetail(object : IThingHomeResultCallback {
                    override fun onSuccess(bean: HomeBean) {
                        onDone()
                    }

                    override fun onError(errorCode: String, errorMsg: String) {
                        Log.w(TAG, "ensureBizCurrentFamily: getHomeDetail failed code=$errorCode msg=$errorMsg")
                        onDone()
                    }
                })
            } catch (t: Throwable) {
                Log.w(TAG, "ensureBizCurrentFamily: getHomeDetail crash (continuing): ${t.message}")
                onDone()
            }
        }

        // Step 1: Try shifting BizBundle's current family (best effort)
        try {
            val kitCandidates = listOf(
                // ThingClips
                "com.thingclips.smart.android.family.api.FamilyManagerCoreKit",
                "com.thingclips.smart.family.api.FamilyManagerCoreKit",

                // Tuya (older)
                "com.tuya.smart.android.family.api.FamilyManagerCoreKit",
                "com.tuya.smart.family.api.FamilyManagerCoreKit"
            )

            var shifted = false
            for (kitName in kitCandidates) {
                try {
                    val kitClazz = Class.forName(kitName)
                    val getUseCase = kitClazz.methods.firstOrNull { m ->
                        m.name == "getFamilyUseCase" && m.parameterTypes.isEmpty()
                    } ?: continue

                    val useCase = getUseCase.invoke(null) ?: continue
                    val useCaseClazz = useCase.javaClass

                    // Docs show: shiftCurrentFamily(homeId, null)
                    val shift = useCaseClazz.methods.firstOrNull { m ->
                        m.name == "shiftCurrentFamily" &&
                                m.parameterTypes.size == 2 &&
                                (m.parameterTypes[0] == java.lang.Long.TYPE || m.parameterTypes[0] == java.lang.Long::class.java)
                    } ?: continue

                    shift.invoke(useCase, homeId, null)
                    Log.d(TAG, "✅ ensureBizCurrentFamily: shifted current family via $kitName")
                    shifted = true
                    break
                } catch (t: Throwable) {
                    Log.w(TAG, "ensureBizCurrentFamily: shift failed for $kitName: ${t.javaClass.simpleName}: ${t.message}")
                }
            }

            if (!shifted) {
                Log.w(TAG, "ensureBizCurrentFamily: FamilyManagerCoreKit not available or shiftCurrentFamily not found (continuing)")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "ensureBizCurrentFamily: shifting step crashed (continuing): ${t.message}")
        }

        // Step 2: Always warm caches, then continue
        warmAndContinue()
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
                            override fun onSuccess(user: User) {
                                result.success(true)
                            }

                            override fun onError(code: String, error: String) {
                                result.error(code, error, null)
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
                            override fun onSuccess() {
                                result.success(true)
                            }

                            override fun onError(code: String, error: String) {
                                result.error(code, error, null)
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
                            override fun onSuccess(user: User) {
                                result.success(true)
                            }

                            override fun onError(code: String, error: String) {
                                result.error(code, error, null)
                            }
                        }
                    )
                }

                "logout" -> {
                    ThingHomeSdk.getUserInstance().logout(object : ILogoutCallback {
                        override fun onSuccess() {
                            result.success(true)
                        }

                        override fun onError(code: String, error: String) {
                            result.error(code, error, null)
                        }
                    })
                }

                // ==========================================================
                // Home
                // ==========================================================
                "getHomeList" -> {
                    ThingHomeSdk.getHomeManagerInstance().queryHomeList(object : IThingGetHomeListCallback {
                        override fun onSuccess(homeBeans: MutableList<HomeBean>) {
                            val list = homeBeans.map { hb ->
                                hashMapOf<String, Any?>(
                                    "homeId" to hb.homeId,
                                    "name" to hb.name,
                                    "geoName" to hb.geoName
                                )
                            }
                            result.success(list)
                        }

                        override fun onError(errorCode: String, error: String) {
                            result.error(errorCode, error, null)
                        }
                    })
                }

                "ensureHome" -> {
                    val name = call.argument<String>("name") ?: "My Home"
                    val geoName = call.argument<String>("geoName") ?: "Oman"
                    val rooms = call.argument<List<String>>("rooms") ?: listOf("Living Room")

                    ThingHomeSdk.getHomeManagerInstance().queryHomeList(object : IThingGetHomeListCallback {
                        override fun onSuccess(homeBeans: MutableList<HomeBean>) {
                            val existing = homeBeans.firstOrNull()
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
                                    override fun onSuccess(bean: HomeBean) {
                                        result.success(
                                            hashMapOf<String, Any?>(
                                                "homeId" to bean.homeId,
                                                "name" to bean.name,
                                                "geoName" to bean.geoName
                                            )
                                        )
                                    }

                                    override fun onError(errorCode: String, errorMsg: String) {
                                        result.error(errorCode, errorMsg, null)
                                    }
                                }
                            )
                        }

                        override fun onError(errorCode: String, error: String) {
                            result.error(errorCode, error, null)
                        }
                    })
                }

                // ==========================================================
                // Option A (BizBundle UI)
                // ==========================================================
                "bizOpenQrScan" -> {
                    val activity = currentActivity ?: run {
                        result.error("NO_ACTIVITY", "No foreground Activity available", null)
                        return
                    }
                    val homeId = (call.argument<Number>("homeId") ?: 0).toLong()

                    mainHandler.post {
                        ensureBizCurrentFamily(homeId) {
                            try {
                                val scanClazz = Class.forName("com.thingclips.smart.activator.scan.qrcode.ScanManager")
                                val instance = scanClazz.getDeclaredField("INSTANCE").get(null)

                                // Prefer openScan(Context, Bundle)
                                val openScanWithBundle = scanClazz.methods.firstOrNull {
                                    it.name == "openScan" && it.parameterTypes.size == 2
                                }

                                if (openScanWithBundle != null) {
                                    val bundle = android.os.Bundle().apply {
                                        putLong("homeId", homeId)
                                        putInt("homeId_int", homeId.toInt())
                                    }
                                    openScanWithBundle.invoke(instance, activity, bundle)
                                    result.success(true)
                                    return@ensureBizCurrentFamily
                                }

                                // Fallback openScan(Context)
                                val openScan = scanClazz.methods.firstOrNull {
                                    it.name == "openScan" && it.parameterTypes.size == 1
                                } ?: throw NoSuchMethodException("ScanManager.openScan(Context/*,Bundle*/) not found")

                                openScan.invoke(instance, activity)
                                result.success(true)
                            } catch (t: Throwable) {
                                Log.e(TAG, "bizOpenQrScan failed", t)
                                result.error("QR_SCAN_FAILED", t.message, null)
                            }
                        }
                    }
                }

                "bizOpenAddDevice" -> {
                    val activity = currentActivity ?: run {
                        result.error("NO_ACTIVITY", "No foreground Activity available", null)
                        return
                    }
                    val homeId = (call.argument<Number>("homeId") ?: 0).toLong()

                    mainHandler.post {
                        ensureBizCurrentFamily(homeId) {
                            try {
                                BizBundleActivatorUi.openAddDevice(
                                    activity = activity,
                                    homeId = homeId,
                                    onOk = { result.success(true) },
                                    onErr = { t -> result.error("ADD_DEVICE_UI_FAILED", t.message, null) }
                                )
                            } catch (t: Throwable) {
                                result.error("ADD_DEVICE_UI_FAILED", t.message, null)
                            }
                        }
                    }
                }

                // ==========================================================
                // Keep Dart stable (existing methods)
                // ==========================================================
                "startZigbeeGatewayPairing" -> {
                    val activity = currentActivity ?: run {
                        result.error("NO_ACTIVITY", "No foreground Activity available", null)
                        return
                    }
                    val homeId = (call.argument<Number>("homeId") ?: 0).toLong()

                    mainHandler.post {
                        ensureBizCurrentFamily(homeId) {
                            try {
                                BizBundleActivatorUi.openAddDevice(
                                    activity = activity,
                                    homeId = homeId,
                                    onOk = { result.success(true) },
                                    onErr = { t -> result.error("GW_PAIRING_FAILED", t.message, null) }
                                )
                            } catch (t: Throwable) {
                                result.error("GW_PAIRING_FAILED", t.message, null)
                            }
                        }
                    }
                }

                "openAddGateway" -> {
                    val activity = currentActivity ?: run {
                        result.error("NO_ACTIVITY", "No foreground Activity available", null)
                        return
                    }
                    val homeId = (call.argument<Number>("homeId") ?: 0).toLong()

                    mainHandler.post {
                        ensureBizCurrentFamily(homeId) {
                            try {
                                BizBundleActivatorUi.openAddDevice(
                                    activity = activity,
                                    homeId = homeId,
                                    onOk = { result.success(true) },
                                    onErr = { t -> result.error("ADD_GATEWAY_FAILED", t.message, null) }
                                )
                            } catch (t: Throwable) {
                                result.error("ADD_GATEWAY_FAILED", t.message, null)
                            }
                        }
                    }
                }

                // Legacy / optional
                "openQrScan" -> {
                    val activity = currentActivity ?: run {
                        result.error("NO_ACTIVITY", "No foreground Activity available", null)
                        return
                    }

                    mainHandler.post {
                        try {
                            val scanClazz = Class.forName("com.thingclips.smart.activator.scan.qrcode.ScanManager")
                            val instance = scanClazz.getDeclaredField("INSTANCE").get(null)

                            val openScan = scanClazz.methods.firstOrNull {
                                it.name == "openScan" && it.parameterTypes.size == 1
                            } ?: throw NoSuchMethodException("ScanManager.openScan(Context) not found")

                            openScan.invoke(instance, activity)
                            result.success(true)
                        } catch (t: Throwable) {
                            Log.e(TAG, "openQrScan failed", t)
                            result.error("QR_SCAN_FAILED", t.message, null)
                        }
                    }
                }

                "pairDeviceByQr" ->
                    result.error("NOT_SUPPORTED", "Use BizBundle QR scan + Add Device UI flow", null)

                "startZigbeeSubDevicePairing" ->
                    result.error("NOT_SUPPORTED", "Use BizBundle Add Device UI flow for sub-devices", null)

                "stopActivator" -> result.success(true)

                else -> result.notImplemented()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Bridge handler crash prevented", t)
            result.error("NATIVE_BRIDGE_ERROR", t.message, null)
        }
    }
}