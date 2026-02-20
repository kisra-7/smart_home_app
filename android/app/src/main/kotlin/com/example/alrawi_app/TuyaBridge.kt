package com.example.alrawi_app

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.home.sdk.builder.ThingGwActivatorBuilder
import com.thingclips.smart.home.sdk.builder.ThingGwSubDevActivatorBuilder
import com.thingclips.smart.home.sdk.builder.ThingQRCodeActivatorBuilder
import com.thingclips.smart.sdk.api.IThingActivator
import com.thingclips.smart.sdk.api.IThingActivatorGetToken
import com.thingclips.smart.sdk.api.IThingDataCallback
import com.thingclips.smart.sdk.api.IThingSmartActivatorListener
import com.thingclips.smart.sdk.bean.DeviceBean
import com.thingclips.smart.sdk.bean.QrScanBean
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class TuyaBridge(
    private val context: Context,
    private val activity: Activity?
) : MethodChannel.MethodCallHandler {

    private lateinit var channel: MethodChannel

    // Activators (Option B)
    private var gwActivator: IThingActivator? = null
    private var subActivator: IThingActivator? = null

    fun attachToEngine(flutterEngine: FlutterEngine) {
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "tuya_config")
        channel.setMethodCallHandler(this)
        Log.d(TAG, "TuyaBridge attached")
    }

    fun detach() {
        if (::channel.isInitialized) channel.setMethodCallHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        Log.e(TAG, "📩 onMethodCall: ${call.method}")

        when (call.method) {

            // ---------- Debug ----------
            "ping" -> result.success("pong")

            // ---------- Core ----------
            "initSdk" -> result.success(true)

            "isLoggedIn" -> runCatching {
                result.success(ThingHomeSdk.getUserInstance().isLogin)
            }.getOrElse { result.error("IS_LOGGED_IN_FAILED", it.message, null) }

            // ==========================================================
            // Option A (BizBundles UI)
            // ==========================================================

            /**
             * Open native QR Scan page from BizBundle:
             * Official entry point is ScanManager.INSTANCE.openScan(activity)
             * We do it via reflection so no "unresolved reference" compile errors.
             */
            "bizOpenQrScan" -> {
                val act = activity
                if (act == null) {
                    result.error("NO_ACTIVITY", "Activity is null", null)
                    return
                }
                try {
                    openBizQrScan(act)
                    result.success(true)
                } catch (t: Throwable) {
                    Log.e(TAG, "bizOpenQrScan failed", t)
                    result.error("BIZ_QR_SCAN_FAILED", t.message, null)
                }
            }

            /**
             * Open full Add Device UI flow from BizBundle:
             * Official entry point is ThingDeviceActivatorManager.INSTANCE.startDeviceActiveAction(activity)
             * Reflection-based to avoid compile-time package mismatch.
             */
            "bizOpenAddDevice" -> {
                val act = activity
                if (act == null) {
                    result.error("NO_ACTIVITY", "Activity is null", null)
                    return
                }
                try {
                    openBizAddDevice(act)
                    result.success(true)
                } catch (t: Throwable) {
                    Log.e(TAG, "bizOpenAddDevice failed", t)
                    result.error("BIZ_ADD_DEVICE_FAILED", t.message, null)
                }
            }

            // ---------- Auth ----------
            "loginByEmail" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                val countryCode = (args["countryCode"] as? String).orEmpty()
                val email = (args["email"] as? String).orEmpty()
                val password = (args["password"] as? String).orEmpty()

                Log.e(TAG, "➡️ loginByEmail called cc=$countryCode email=$email passLen=${password.length}")
                loginByEmail(countryCode, email, password, result)
            }

            "sendEmailCode" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                val countryCode = (args["countryCode"] as? String).orEmpty()
                val email = (args["email"] as? String).orEmpty()
                val type = (args["type"] as? Int) ?: 1
                sendEmailCode(countryCode, email, type, result)
            }

            "registerEmail" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                val countryCode = (args["countryCode"] as? String).orEmpty()
                val email = (args["email"] as? String).orEmpty()
                val password = (args["password"] as? String).orEmpty()
                val code = (args["code"] as? String).orEmpty()
                registerEmail(countryCode, email, password, code, result)
            }

            "logout" -> logout(result)

            // ---------- Homes ----------
            "getHomeList" -> getHomeList(result)

            "createHome" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                val name = (args["name"] as? String) ?: "My Home"
                val geoName = (args["geoName"] as? String) ?: "Oman"
                val rooms = (args["rooms"] as? List<*>)?.mapNotNull { it as? String }
                    ?: listOf("Living Room")
                createHome(name, geoName, rooms, result)
            }

            "ensureHome" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                val name = (args["name"] as? String) ?: "My Home"
                val geoName = (args["geoName"] as? String) ?: "Oman"
                val rooms = (args["rooms"] as? List<*>)?.mapNotNull { it as? String }
                    ?: listOf("Living Room")
                ensureHome(name, geoName, rooms, result)
            }

            // ==========================================================
            // OPTION B: Direct SDK pairing
            // ==========================================================

            "pairDeviceByQr" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                val homeId = (args["homeId"] as? Number)?.toLong() ?: 0L
                val qrUrl = (args["qrUrl"] as? String).orEmpty().trim()
                val timeout = (args["timeout"] as? Number)?.toInt() ?: 100

                if (homeId <= 0) {
                    result.error("BAD_ARGS", "homeId is required", null)
                    return
                }
                if (qrUrl.isBlank()) {
                    result.error("BAD_ARGS", "qrUrl is required", null)
                    return
                }

                pairDeviceByQr(homeId, qrUrl, timeout, result)
            }

            "startZigbeeGatewayPairing" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                val homeId = (args["homeId"] as? Number)?.toLong() ?: 0L
                if (homeId <= 0) {
                    result.error("BAD_ARGS", "homeId is required", null)
                    return
                }
                startZigbeeGatewayPairing(homeId, result)
            }

            "openAddGateway" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                val homeId = (args["homeId"] as? Number)?.toLong() ?: 0L
                if (homeId <= 0) {
                    result.error("BAD_ARGS", "homeId is required", null)
                    return
                }
                startZigbeeGatewayPairing(homeId, result)
            }

            "openQrScan" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                val homeId = (args["homeId"] as? Number)?.toLong() ?: 0L
                if (homeId <= 0) {
                    result.error("BAD_ARGS", "homeId is required", null)
                    return
                }
                // Old behavior fallback
                startZigbeeGatewayPairing(homeId, result)
            }

            "startZigbeeSubDevicePairing" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                val gwDevId = (args["gwDevId"] as? String).orEmpty()
                val timeout = (args["timeout"] as? Int) ?: 100
                if (gwDevId.isBlank()) {
                    result.error("BAD_ARGS", "gwDevId is required", null)
                    return
                }
                startZigbeeSubDevicePairing(gwDevId, timeout, result)
            }

            "stopActivator" -> {
                stopAll()
                result.success(true)
            }

            else -> result.notImplemented()
        }
    }

    // ==========================================================
    // BizBundle reflection helpers
    // ==========================================================

private fun openBizQrScan(act: Activity) {
    val candidates = listOf(
        // ✅ confirmed in your APK
        "com.thingclips.smart.activator.scan.qrcode.ScanManager",

        // fallback candidates (harmless)
        "com.thingclips.smart.grcode.api.ScanManager",
        "com.thingclips.smart.grcode.ScanManager",
        "com.thingclips.smart.bizbundle.qrcode.api.ScanManager",
        "com.thingclips.smart.bizbundle.qrcode_mlkit.api.ScanManager",
        "com.thingclips.smart.android.device.config.api.ScanManager",
        "com.tuya.smart.android.device.config.api.ScanManager"
    )

    val clazz = findFirstClass(candidates)
        ?: throw ClassNotFoundException("ScanManager not found. Tried:\n$candidates")

    val instance = clazz.getDeclaredField("INSTANCE").get(null)

    // ✅ Your SDK exposes: openScan(Context) and openScan(Context, Bundle)
    val m1 = clazz.methods.firstOrNull { m ->
        m.name == "openScan" &&
            m.parameterTypes.size == 1 &&
            android.content.Context::class.java.isAssignableFrom(m.parameterTypes[0])
    }
    if (m1 != null) {
        m1.invoke(instance, act as android.content.Context)
        return
    }

    val m2 = clazz.methods.firstOrNull { m ->
        m.name == "openScan" &&
            m.parameterTypes.size == 2 &&
            android.content.Context::class.java.isAssignableFrom(m.parameterTypes[0]) &&
            android.os.Bundle::class.java.isAssignableFrom(m.parameterTypes[1])
    }
    if (m2 != null) {
        m2.invoke(instance, act as android.content.Context, android.os.Bundle())
        return
    }

    throw NoSuchMethodException("openScan(Context) not found on ${clazz.name}")
}

private fun defaultArg(type: Class<*>): Any? {
    return when {
        android.os.Bundle::class.java.isAssignableFrom(type) -> android.os.Bundle()
        java.lang.Boolean.TYPE == type || java.lang.Boolean::class.java.isAssignableFrom(type) -> false
        java.lang.Integer.TYPE == type || java.lang.Integer::class.java.isAssignableFrom(type) -> 0
        java.lang.Long.TYPE == type || java.lang.Long::class.java.isAssignableFrom(type) -> 0L
        java.lang.String::class.java.isAssignableFrom(type) -> ""
        else -> null
    }
}

private fun openBizAddDevice(act: Activity) {
    // ✅ REAL class found in your APK:
    // LLcom/thingclips/smart/activator/plug/mesosphere/ThingDeviceActivatorManager;
    val candidates = listOf(
        "com.thingclips.smart.activator.plug.mesosphere.ThingDeviceActivatorManager",

        // keep old candidates as fallback
        "com.thingclips.smart.android.device.config.api.ThingDeviceActivatorManager",
        "com.thingclips.smart.android.device.config.ThingDeviceActivatorManager",
        "com.tuya.smart.android.device.config.api.ThingDeviceActivatorManager",
        "com.tuya.smart.android.device.config.ThingDeviceActivatorManager"
    )

    val clazz = findFirstClass(candidates)
        ?: throw ClassNotFoundException("ThingDeviceActivatorManager not found. Tried:\n$candidates")

    val instance = clazz.getDeclaredField("INSTANCE").get(null)

    val start = clazz.methods.firstOrNull { m ->
        m.name == "startDeviceActiveAction" &&
            m.parameterTypes.size == 1 &&
            Activity::class.java.isAssignableFrom(m.parameterTypes[0])
    } ?: throw NoSuchMethodException("startDeviceActiveAction(Activity) not found on ${clazz.name}")

    start.invoke(instance, act)
}



    private fun findFirstClass(names: List<String>): Class<*>? {
        val cl = context.classLoader
        for (n in names) {
            try {
                return Class.forName(n, false, cl)
            } catch (_: Throwable) {}
        }
        return null
    }

    // -------------------- AUTH --------------------

    private fun loginByEmail(countryCode: String, email: String, password: String, result: MethodChannel.Result) {
        try {
            ThingHomeSdk.getUserInstance().loginWithEmail(
                countryCode,
                email,
                password,
                object : com.thingclips.smart.android.user.api.ILoginCallback {
                    override fun onSuccess(user: com.thingclips.smart.android.user.bean.User) {
                        Log.e(TAG, "✅ login success uid=${user.uid}")
                        result.success(true)
                    }

                    override fun onError(code: String, error: String) {
                        Log.e(TAG, "❌ login error code=$code msg=$error")
                        result.error(code.ifBlank { "LOGIN_FAILED" }, error, null)
                    }
                }
            )
        } catch (t: Throwable) {
            Log.e(TAG, "💥 login exception", t)
            result.error("LOGIN_EXCEPTION", t.message, null)
        }
    }

    private fun sendEmailCode(countryCode: String, email: String, type: Int, result: MethodChannel.Result) {
        try {
            ThingHomeSdk.getUserInstance().sendVerifyCodeWithUserName(
                email,
                "",
                countryCode,
                type,
                object : com.thingclips.smart.sdk.api.IResultCallback {
                    override fun onSuccess() {
                        result.success(true)
                    }

                    override fun onError(code: String, error: String) {
                        result.error(code.ifBlank { "SEND_CODE_FAILED" }, error, null)
                    }
                }
            )
        } catch (t: Throwable) {
            result.error("SEND_CODE_EXCEPTION", t.message, null)
        }
    }

    private fun registerEmail(countryCode: String, email: String, password: String, code: String, result: MethodChannel.Result) {
        try {
            ThingHomeSdk.getUserInstance().registerAccountWithEmail(
                countryCode,
                email,
                password,
                code,
                object : com.thingclips.smart.android.user.api.IRegisterCallback {
                    override fun onSuccess(user: com.thingclips.smart.android.user.bean.User) {
                        result.success(true)
                    }

                    override fun onError(code: String, error: String) {
                        result.error(code.ifBlank { "REGISTER_FAILED" }, error, null)
                    }
                }
            )
        } catch (t: Throwable) {
            result.error("REGISTER_EXCEPTION", t.message, null)
        }
    }

    private fun logout(result: MethodChannel.Result) {
        try {
            ThingHomeSdk.getUserInstance().logout(object : com.thingclips.smart.android.user.api.ILogoutCallback {
                override fun onSuccess() {
                    stopAll()
                    result.success(true)
                }

                override fun onError(code: String, error: String) {
                    result.error(code.ifBlank { "LOGOUT_FAILED" }, error, null)
                }
            })
        } catch (t: Throwable) {
            result.error("LOGOUT_EXCEPTION", t.message, null)
        }
    }

    // -------------------- HOMES --------------------

    private fun getHomeList(result: MethodChannel.Result) {
        try {
            ThingHomeSdk.getHomeManagerInstance().queryHomeList(
                object : com.thingclips.smart.home.sdk.callback.IThingGetHomeListCallback {
                    override fun onSuccess(homeBeans: MutableList<com.thingclips.smart.home.sdk.bean.HomeBean>) {
                        val list = homeBeans.map {
                            mapOf(
                                "homeId" to it.homeId,
                                "name" to it.name
                            )
                        }
                        result.success(list)
                    }

                    override fun onError(code: String, error: String) {
                        result.error(code.ifBlank { "HOME_LIST_FAILED" }, error, null)
                    }
                }
            )
        } catch (t: Throwable) {
            result.error("HOME_LIST_EXCEPTION", t.message, null)
        }
    }

    private fun createHome(name: String, geoName: String, rooms: List<String>, result: MethodChannel.Result) {
        try {
            ThingHomeSdk.getHomeManagerInstance().createHome(
                name,
                0.0,
                0.0,
                geoName,
                rooms,
                object : com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback {
                    override fun onSuccess(bean: com.thingclips.smart.home.sdk.bean.HomeBean) {
                        result.success(mapOf("homeId" to bean.homeId, "name" to bean.name))
                    }

                    override fun onError(code: String, msg: String) {
                        result.error(code.ifBlank { "CREATE_HOME_FAILED" }, msg, null)
                    }
                }
            )
        } catch (t: Throwable) {
            result.error("CREATE_HOME_EXCEPTION", t.message, null)
        }
    }

    private fun ensureHome(name: String, geoName: String, rooms: List<String>, result: MethodChannel.Result) {
        ThingHomeSdk.getHomeManagerInstance().queryHomeList(
            object : com.thingclips.smart.home.sdk.callback.IThingGetHomeListCallback {
                override fun onSuccess(homeBeans: MutableList<com.thingclips.smart.home.sdk.bean.HomeBean>) {
                    if (homeBeans.isNotEmpty()) {
                        val first = homeBeans.first()
                        result.success(mapOf("homeId" to first.homeId, "name" to first.name, "created" to false))
                        return
                    }
                    ThingHomeSdk.getHomeManagerInstance().createHome(
                        name,
                        0.0,
                        0.0,
                        geoName,
                        rooms,
                        object : com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback {
                            override fun onSuccess(bean: com.thingclips.smart.home.sdk.bean.HomeBean) {
                                result.success(mapOf("homeId" to bean.homeId, "name" to bean.name, "created" to true))
                            }

                            override fun onError(code: String, msg: String) {
                                result.error(code.ifBlank { "ENSURE_HOME_CREATE_FAILED" }, msg, null)
                            }
                        }
                    )
                }

                override fun onError(code: String, error: String) {
                    result.error(code.ifBlank { "ENSURE_HOME_LIST_FAILED" }, error, null)
                }
            }
        )
    }

    // -------------------- OPTION B: Pair Gateway by QR URL --------------------

    private fun pairDeviceByQr(homeId: Long, qrUrl: String, timeout: Int, result: MethodChannel.Result) {
        val act = activity
        if (act == null) {
            result.error("NO_ACTIVITY", "Activity is null", null)
            return
        }

        emit("tuya_flow_step", mapOf("flow" to "qr", "step" to "parse_qr", "homeId" to homeId, "qr" to qrUrl))

        stopGwOnly()

        ThingHomeSdk.getActivatorInstance().deviceQrCodeParse(qrUrl, object : IThingDataCallback<QrScanBean> {
            override fun onSuccess(scan: QrScanBean?) {
                if (scan == null) {
                    result.error("QR_PARSE_FAIL", "QrScanBean is null", null)
                    return
                }

                val uuid = extractUuid(scan)
                if (uuid.isBlank()) {
                    result.error("NO_UUID", "Could not extract uuid from QR", null)
                    return
                }

                emit("tuya_flow_step", mapOf("flow" to "qr", "step" to "start_qr_activator", "uuid" to uuid))

                val builder = ThingQRCodeActivatorBuilder()
                    .setContext(act)
                    .setHomeId(homeId)
                    .setUuid(uuid)
                    .setTimeOut(timeout.toLong())
                    .setListener(object : IThingSmartActivatorListener {
                        override fun onError(errorCode: String, errorMsg: String) {
                            emit("tuya_gw_error", mapOf("code" to errorCode, "msg" to errorMsg))
                        }

                        override fun onActiveSuccess(devResp: DeviceBean) {
                            emit(
                                "tuya_gw_success",
                                mapOf(
                                    "devId" to devResp.devId,
                                    "name" to devResp.name,
                                    "isOnline" to devResp.isOnline
                                )
                            )
                        }

                        override fun onStep(step: String, data: Any?) {
                            emit("tuya_flow_step", mapOf("flow" to "qr", "step" to step, "data" to (data?.toString() ?: "")))
                        }
                    })

                gwActivator = ThingHomeSdk.getActivatorInstance().newQRCodeDevActivator(builder)
                gwActivator?.start()

                result.success(true)
            }

            override fun onError(errorCode: String?, errorMessage: String?) {
                result.error(errorCode ?: "QR_PARSE_ERROR", errorMessage ?: "QR parse error", null)
            }
        })
    }

    private fun extractUuid(scan: QrScanBean): String {
        return try {
            val actionData = scan.actionData
            when (actionData) {
                is Map<*, *> -> {
                    (actionData["uuid"]
                        ?: actionData["UUID"]
                        ?: actionData["deviceUuid"]
                        ?: actionData["device_uuid"]
                        ?: "").toString().trim()
                }
                else -> actionData?.toString()?.trim().orEmpty()
            }
        } catch (_: Throwable) {
            ""
        }
    }

    // -------------------- OPTION B: Gateway pairing (WiFi token flow) --------------------

    private fun startZigbeeGatewayPairing(homeId: Long, result: MethodChannel.Result) {
        val act = activity
        if (act == null) {
            result.error("NO_ACTIVITY", "Activity is null", null)
            return
        }

        emit("tuya_flow_step", mapOf("flow" to "gw", "step" to "get_token", "homeId" to homeId))

        ThingHomeSdk.getActivatorInstance().getActivatorToken(homeId, object : IThingActivatorGetToken {
            override fun onSuccess(token: String) {
                emit("tuya_flow_step", mapOf("flow" to "gw", "step" to "start_activator"))

                stopGwOnly()

                val builder = ThingGwActivatorBuilder()
                    .setContext(act)
                    .setToken(token)
                    .setTimeOut(100L)
                    .setListener(object : IThingSmartActivatorListener {
                        override fun onError(errorCode: String, errorMsg: String) {
                            emit("tuya_gw_error", mapOf("code" to errorCode, "msg" to errorMsg))
                        }

                        override fun onActiveSuccess(devResp: DeviceBean) {
                            emit(
                                "tuya_gw_success",
                                mapOf(
                                    "devId" to devResp.devId,
                                    "name" to devResp.name,
                                    "isOnline" to devResp.isOnline
                                )
                            )
                        }

                        override fun onStep(step: String, data: Any?) {
                            emit("tuya_flow_step", mapOf("flow" to "gw", "step" to step, "data" to (data?.toString() ?: "")))
                        }
                    })

                gwActivator = ThingHomeSdk.getActivatorInstance().newGwActivator(builder)
                gwActivator?.start()

                result.success(true)
            }

            override fun onFailure(errorCode: String, errorMsg: String) {
                result.error(errorCode.ifBlank { "TOKEN_FAILED" }, errorMsg, null)
            }
        })
    }

    // -------------------- OPTION B: Zigbee sub-device pairing --------------------

    private fun startZigbeeSubDevicePairing(gwDevId: String, timeout: Int, result: MethodChannel.Result) {
        stopSubOnly()

        emit("tuya_flow_step", mapOf("flow" to "sub", "step" to "start_sub_pairing", "gwDevId" to gwDevId))

        val builder = ThingGwSubDevActivatorBuilder()
            .setDevId(gwDevId)
            .setTimeOut(timeout.toLong())
            .setListener(object : IThingSmartActivatorListener {
                override fun onError(errorCode: String, errorMsg: String) {
                    emit("tuya_sub_error", mapOf("code" to errorCode, "msg" to errorMsg))
                }

                override fun onActiveSuccess(devResp: DeviceBean) {
                    emit(
                        "tuya_sub_success",
                        mapOf(
                            "devId" to devResp.devId,
                            "name" to devResp.name,
                            "isOnline" to devResp.isOnline
                        )
                    )
                }

                override fun onStep(step: String, data: Any?) {
                    emit("tuya_flow_step", mapOf("flow" to "sub", "step" to step, "data" to (data?.toString() ?: "")))
                }
            })

        subActivator = ThingHomeSdk.getActivatorInstance().newGwSubDevActivator(builder)
        subActivator?.start()

        result.success(true)
    }

    // -------------------- Stop / cleanup --------------------

    private fun stopGwOnly() {
        try { gwActivator?.stop() } catch (_: Throwable) {}
        try { gwActivator?.onDestroy() } catch (_: Throwable) {}
        gwActivator = null
    }

    private fun stopSubOnly() {
        try { subActivator?.stop() } catch (_: Throwable) {}
        try { subActivator?.onDestroy() } catch (_: Throwable) {}
        subActivator = null
    }

    private fun stopAll() {
        stopGwOnly()
        stopSubOnly()
    }

    // -------------------- Flutter event helper --------------------

    private fun emit(method: String, args: Any?) {
        try {
            if (!::channel.isInitialized) return
            activity?.runOnUiThread {
                try {
                    channel.invokeMethod(method, args)
                } catch (t: Throwable) {
                    Log.w(TAG, "emit failed: $method -> ${t.message}")
                }
            } ?: run {
                channel.invokeMethod(method, args)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "emit wrapper failed: $method -> ${t.message}")
        }
    }

    companion object {
        private const val TAG = "TuyaBridge"
    }
}
