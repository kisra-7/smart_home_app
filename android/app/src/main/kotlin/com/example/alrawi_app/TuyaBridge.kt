package com.example.alrawi_app

import android.app.Activity
import android.os.Bundle
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

// ✅ Typed QR APIs (fix compile errors)
import com.thingclips.smart.sdk.bean.QrScanBean
import com.thingclips.smart.sdk.bean.DeviceBean
import com.thingclips.smart.sdk.api.IThingDataCallback
import com.thingclips.smart.sdk.api.IThingSmartActivatorListener
import com.thingclips.smart.home.sdk.builder.ThingQRCodeActivatorBuilder

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

    // ---------- Entry point used by MainActivity ----------
    @JvmStatic
    fun handle(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {

                // ==========================================================
                // Core
                // ==========================================================
                "initSdk" -> {
                    result.success(true)
                }

                "isLoggedIn" -> {
                    result.success(ThingHomeSdk.getUserInstance().isLogin)
                }

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
                            override fun onSuccess() {
                                result.success(true)
                            }

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
                            result.success(true)
                        }

                        override fun onError(code: String?, error: String?) {
                            result.error(code ?: "LOGOUT_FAILED", error ?: "logout failed", null)
                        }
                    })
                }

                // ==========================================================
                // Home
                // ==========================================================
                "getHomeList" -> {
                    ThingHomeSdk.getHomeManagerInstance()
                        .queryHomeList(object : IThingGetHomeListCallback {
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
                                result.error(
                                    errorCode ?: "HOME_LIST_FAILED",
                                    error ?: "queryHomeList failed",
                                    null
                                )
                            }
                        })
                }

                "ensureHome" -> {
                    val name = call.argument<String>("name") ?: "My Home"
                    val geoName = call.argument<String>("geoName") ?: "Oman"
                    val rooms = call.argument<List<String>>("rooms") ?: listOf("Living Room")

                    ThingHomeSdk.getHomeManagerInstance()
                        .queryHomeList(object : IThingGetHomeListCallback {
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
                                            result.error(
                                                errorCode ?: "CREATE_HOME_FAILED",
                                                errorMsg ?: "createHome failed",
                                                null
                                            )
                                        }
                                    }
                                )
                            }

                            override fun onError(errorCode: String?, error: String?) {
                                result.error(
                                    errorCode ?: "HOME_LIST_FAILED",
                                    error ?: "queryHomeList failed",
                                    null
                                )
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
                        try {
                            val scanClazz =
                                Class.forName("com.thingclips.smart.activator.scan.qrcode.ScanManager")
                            val instance = scanClazz.getDeclaredField("INSTANCE").get(null)

                            // Prefer openScan(Context, Bundle)
                            val openScanWithBundle = scanClazz.methods.firstOrNull {
                                it.name == "openScan" && it.parameterTypes.size == 2
                            }

                            if (openScanWithBundle != null) {
                                val bundle = Bundle().apply {
                                    putLong("homeId", homeId)
                                    putInt("homeId_int", homeId.toInt())
                                }
                                openScanWithBundle.invoke(instance, activity, bundle)
                                result.success(true)
                                return@post
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

                "bizOpenAddDevice" -> {
                    val activity = currentActivity ?: run {
                        result.error("NO_ACTIVITY", "No foreground Activity available", null)
                        return
                    }
                    val homeId = (call.argument<Number>("homeId") ?: 0).toLong()

                    BizBundleActivatorUi.openAddDevice(
                        activity = activity,
                        homeId = homeId,
                        onOk = { result.success(true) },
                        onErr = { t -> result.error("ADD_DEVICE_UI_FAILED", t.message, null) }
                    )
                }

                // ==========================================================
                // ✅ Stable QR pairing (Direct SDK) — FIXED TYPES
                // ==========================================================
                "pairDeviceByQr" -> {
                    val activity = currentActivity ?: run {
                        result.error("NO_ACTIVITY", "No foreground Activity available", null)
                        return
                    }

                    val homeId = (call.argument<Number>("homeId") ?: 0).toLong()
                    val qrUrl = (call.argument<String>("qrUrl") ?: "").trim()
                    val timeout = (call.argument<Number>("timeout") ?: 100).toInt()

                    if (homeId <= 0 || qrUrl.isEmpty()) {
                        result.error("BAD_ARGS", "homeId and qrUrl are required", null)
                        return
                    }

                    mainHandler.post {
                        try {
                            ThingHomeSdk.getActivatorInstance().deviceQrCodeParse(
                                qrUrl,
                                object : IThingDataCallback<QrScanBean> {
                                    override fun onSuccess(data: QrScanBean?) {
                                        try {
                                            val uuid = data?.actionData?.uuid
                                            if (uuid.isNullOrBlank()) {
                                                emit(
                                                    "tuya_gw_error",
                                                    hashMapOf("code" to "QR_PARSE_FAILED", "msg" to "UUID not found")
                                                )
                                                result.error("QR_PARSE_FAILED", "UUID not found in QrScanBean", null)
                                                return
                                            }

                                            val builder = ThingQRCodeActivatorBuilder()
                                                .setContext(activity)
                                                .setHomeId(homeId)
                                                .setUuid(uuid)
                                                .setTimeOut(timeout)
                                                .setListener(object : IThingSmartActivatorListener {
                                                    override fun onError(errorCode: String?, errorMsg: String?) {
                                                        emit(
                                                            "tuya_gw_error",
                                                            hashMapOf(
                                                                "code" to (errorCode ?: "QR_ACTIVE_ERROR"),
                                                                "msg" to (errorMsg ?: "activation failed")
                                                            )
                                                        )
                                                    }

                                                    override fun onActiveSuccess(devResp: DeviceBean?) {
                                                        if (devResp != null) {
                                                            emit(
                                                                "tuya_gw_success",
                                                                hashMapOf(
                                                                    "devId" to devResp.devId,
                                                                    "name" to devResp.name,
                                                                    "isOnline" to devResp.isOnline
                                                                )
                                                            )
                                                        } else {
                                                            emit("tuya_gw_success", hashMapOf("devId" to "", "name" to "", "isOnline" to false))
                                                        }
                                                    }

                                                    override fun onStep(step: String?, data: Any?) {
                                                        // Optional: emit progress
                                                    }
                                                })

                                            val activator =
                                                ThingHomeSdk.getActivatorInstance().newQRCodeDevActivator(builder)
                                            activator.start()

                                            // start() is async; success comes via listener
                                            result.success(true)
                                        } catch (t: Throwable) {
                                            Log.e(TAG, "pairDeviceByQr start failed", t)
                                            emit(
                                                "tuya_gw_error",
                                                hashMapOf("code" to "QR_ACTIVE_ERROR", "msg" to (t.message ?: "unknown"))
                                            )
                                            result.error("QR_ACTIVE_ERROR", t.message, null)
                                        }
                                    }

                                    override fun onError(errorCode: String?, errorMessage: String?) {
                                        emit(
                                            "tuya_gw_error",
                                            hashMapOf(
                                                "code" to (errorCode ?: "QR_PARSE_FAILED"),
                                                "msg" to (errorMessage ?: "parse failed")
                                            )
                                        )
                                        result.error(errorCode ?: "QR_PARSE_FAILED", errorMessage ?: "parse failed", null)
                                    }
                                }
                            )
                        } catch (t: Throwable) {
                            Log.e(TAG, "pairDeviceByQr failed", t)
                            emit(
                                "tuya_gw_error",
                                hashMapOf("code" to "QR_PAIR_FAILED", "msg" to (t.message ?: "unknown"))
                            )
                            result.error("QR_PAIR_FAILED", t.message, null)
                        }
                    }
                }

                // ==========================================================
                // Compatibility / placeholders (keep Dart stable)
                // ==========================================================
                "startZigbeeGatewayPairing" -> {
                    val activity = currentActivity ?: run {
                        result.error("NO_ACTIVITY", "No foreground Activity available", null)
                        return
                    }
                    val homeId = (call.argument<Number>("homeId") ?: 0).toLong()

                    BizBundleActivatorUi.openAddDevice(
                        activity = activity,
                        homeId = homeId,
                        onOk = { result.success(true) },
                        onErr = { t -> result.error("GW_PAIRING_FAILED", t.message, null) }
                    )
                }

                "startZigbeeSubDevicePairing" -> {
                    result.error("NOT_SUPPORTED", "Use BizBundle Add Device UI flow for sub-devices", null)
                }

                "stopActivator" -> {
                    result.success(true)
                }

                "openAddGateway" -> {
                    val activity = currentActivity ?: run {
                        result.error("NO_ACTIVITY", "No foreground Activity available", null)
                        return
                    }
                    val homeId = (call.argument<Number>("homeId") ?: 0).toLong()
                    BizBundleActivatorUi.openAddDevice(
                        activity = activity,
                        homeId = homeId,
                        onOk = { result.success(true) },
                        onErr = { t -> result.error("ADD_GATEWAY_FAILED", t.message, null) }
                    )
                }

                "openQrScan" -> {
                    val activity = currentActivity ?: run {
                        result.error("NO_ACTIVITY", "No foreground Activity available", null)
                        return
                    }
                    mainHandler.post {
                        try {
                            val scanClazz =
                                Class.forName("com.thingclips.smart.activator.scan.qrcode.ScanManager")
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

                else -> result.notImplemented()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Bridge handler crash prevented", t)
            result.error("NATIVE_BRIDGE_ERROR", t.message, null)
        }
    }
}