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
        when (call.method) {

            "initSdk" -> result.success(true)

            "isLoggedIn" -> runCatching {
                result.success(ThingHomeSdk.getUserInstance().isLogin)
            }.getOrElse { result.error("IS_LOGGED_IN_FAILED", it.message, null) }

            "loginByEmail" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                loginByEmail(
                    (args["countryCode"] as? String).orEmpty(),
                    (args["email"] as? String).orEmpty(),
                    (args["password"] as? String).orEmpty(),
                    result
                )
            }

            "sendEmailCode" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                sendEmailCode(
                    (args["countryCode"] as? String).orEmpty(),
                    (args["email"] as? String).orEmpty(),
                    (args["type"] as? Int) ?: 1,
                    result
                )
            }

            "registerEmail" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                registerEmail(
                    (args["countryCode"] as? String).orEmpty(),
                    (args["email"] as? String).orEmpty(),
                    (args["password"] as? String).orEmpty(),
                    (args["code"] as? String).orEmpty(),
                    result
                )
            }

            "logout" -> logout(result)

            "getHomeList" -> getHomeList(result)

            "ensureHome" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                ensureHome(
                    (args["name"] as? String) ?: "My Home",
                    (args["geoName"] as? String) ?: "Oman",
                    (args["rooms"] as? List<*>)?.mapNotNull { it as? String } ?: listOf("Living Room"),
                    result
                )
            }

            // ==========================================================
            // OPTION A: BizBundles UI (Production)
            // ==========================================================

            "bizOpenQrScan" -> {
                val act = activity
                if (act == null) {
                    result.error("NO_ACTIVITY", "Activity is null", null)
                    return
                }
                try {
                    // ✅ Doc-aligned navigation: UrlBuilder + UrlRouter.execute
                    bizOpenQrScanViaUrlRouter(act)
                    result.success(true)
                } catch (t: Throwable) {
                    Log.e(TAG, "bizOpenQrScan failed", t)
                    result.error("BIZ_QR_SCAN_FAILED", t.message, null)
                }
            }

            "bizOpenAddDevice" -> {
                val act = activity
                if (act == null) {
                    result.error("NO_ACTIVITY", "Activity is null", null)
                    return
                }
                val homeId = call.argument<Number>("homeId")?.toLong()
                if (homeId == null || homeId <= 0L) {
                    result.error("BAD_ARGS", "homeId is required", null)
                    return
                }
                try {
                    // target-based (from your module_app.json mapping)
                    bizOpenTargetViaUrlRouter(act, "config_device", Bundle().apply {
                        putLong("homeId", homeId)
                    })
                    result.success(true)
                } catch (t: Throwable) {
                    Log.e(TAG, "bizOpenAddDevice failed", t)
                    result.error("BIZ_ADD_DEVICE_FAILED", t.message, null)
                }
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
    // BizBundle navigation (UrlBuilder + UrlRouter.execute)
    // ==========================================================

    private fun bizOpenQrScanViaUrlRouter(act: Activity) {
        // Your module_app.json maps scan: "scan_qrcode"
        val targets = listOf(
            "scan_qrcode",   // ✅ your mapping
            "qrcode_scan",
            "qr_scan",
            "scan"
        )

        var lastErr: Throwable? = null
        for (t in targets) {
            try {
                bizOpenTargetViaUrlRouter(act, t, null)
                return
            } catch (e: Throwable) {
                lastErr = e
            }
        }

        throw IllegalStateException(
            "QR Scan route not found. Tried targets:\n$targets\n" +
                "Last error: ${lastErr?.message}"
        )
    }

    private fun bizOpenTargetViaUrlRouter(ctx: Context, target: String, extras: Bundle?) {
        val urlBuilderClazz = findFirstClass(
            listOf(
                "com.thingclips.smart.android.router.UrlBuilder",
                "com.tuya.smart.android.router.UrlBuilder"
            )
        ) ?: throw ClassNotFoundException("UrlBuilder not found")

        val urlRouterClazz = findFirstClass(
            listOf(
                "com.thingclips.smart.android.router.UrlRouter",
                "com.tuya.smart.android.router.UrlRouter"
            )
        ) ?: throw ClassNotFoundException("UrlRouter not found")

        // UrlBuilder(Context, String)
        val ctor = urlBuilderClazz.constructors.firstOrNull { c ->
            val p = c.parameterTypes
            p.size == 2 && Context::class.java.isAssignableFrom(p[0]) && p[1] == String::class.java
        } ?: throw NoSuchMethodException("UrlBuilder(Context, String) ctor not found")

        val builder = ctor.newInstance(ctx, target)

        // optional: putExtras(bundle)
        if (extras != null) {
            val putExtras = urlBuilderClazz.methods.firstOrNull { m ->
                m.name == "putExtras" && m.parameterTypes.size == 1 && m.parameterTypes[0] == Bundle::class.java
            }
            putExtras?.invoke(builder, extras)
        }

        // UrlRouter.execute(UrlBuilder)
        val execute = urlRouterClazz.methods.firstOrNull { m ->
            m.name == "execute" && m.parameterTypes.size == 1 && m.parameterTypes[0].isAssignableFrom(urlBuilderClazz)
        } ?: urlRouterClazz.methods.firstOrNull { m ->
            // some builds use execute(Object)
            m.name == "execute" && m.parameterTypes.size == 1
        } ?: throw NoSuchMethodException("UrlRouter.execute(...) not found")

        // static execute
        execute.invoke(null, builder)
    }

    private fun findFirstClass(names: List<String>): Class<*>? {
        for (n in names) {
            try {
                return Class.forName(n)
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
                        result.success(true)
                    }
                    override fun onError(code: String, error: String) {
                        result.error(code.ifBlank { "LOGIN_FAILED" }, error, null)
                    }
                }
            )
        } catch (t: Throwable) {
            result.error("LOGIN_EXCEPTION", t.message, null)
        }
    }

    private fun sendEmailCode(countryCode: String, email: String, type: Int, result: MethodChannel.Result) {
        try {
            ThingHomeSdk.getUserInstance().sendVerifyCodeWithUserName(
                email, "", countryCode, type,
                object : com.thingclips.smart.sdk.api.IResultCallback {
                    override fun onSuccess() { result.success(true) }
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
                countryCode, email, password, code,
                object : com.thingclips.smart.android.user.api.IRegisterCallback {
                    override fun onSuccess(user: com.thingclips.smart.android.user.bean.User) { result.success(true) }
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
                        val list = homeBeans.map { mapOf("homeId" to it.homeId, "name" to it.name) }
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
                        name, 0.0, 0.0, geoName, rooms,
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

    // -------------------- OPTION B: Pair device by QR URL --------------------

    private fun pairDeviceByQr(homeId: Long, qrUrl: String, timeout: Int, result: MethodChannel.Result) {
        val act = activity ?: run {
            result.error("NO_ACTIVITY", "Activity is null", null)
            return
        }

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
                            emit("tuya_gw_success", mapOf("devId" to devResp.devId, "name" to devResp.name, "isOnline" to devResp.isOnline))
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
                is Map<*, *> -> (actionData["uuid"] ?: actionData["UUID"] ?: actionData["deviceUuid"] ?: actionData["device_uuid"] ?: "").toString().trim()
                else -> actionData?.toString()?.trim().orEmpty()
            }
        } catch (_: Throwable) {
            ""
        }
    }

    private fun startZigbeeGatewayPairing(homeId: Long, result: MethodChannel.Result) {
        val act = activity ?: run {
            result.error("NO_ACTIVITY", "Activity is null", null)
            return
        }

        ThingHomeSdk.getActivatorInstance().getActivatorToken(homeId, object : IThingActivatorGetToken {
            override fun onSuccess(token: String) {
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
                            emit("tuya_gw_success", mapOf("devId" to devResp.devId, "name" to devResp.name, "isOnline" to devResp.isOnline))
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

    private fun startZigbeeSubDevicePairing(gwDevId: String, timeout: Int, result: MethodChannel.Result) {
        stopSubOnly()

        val builder = ThingGwSubDevActivatorBuilder()
            .setDevId(gwDevId)
            .setTimeOut(timeout.toLong())
            .setListener(object : IThingSmartActivatorListener {
                override fun onError(errorCode: String, errorMsg: String) {
                    emit("tuya_sub_error", mapOf("code" to errorCode, "msg" to errorMsg))
                }
                override fun onActiveSuccess(devResp: DeviceBean) {
                    emit("tuya_sub_success", mapOf("devId" to devResp.devId, "name" to devResp.name, "isOnline" to devResp.isOnline))
                }
                override fun onStep(step: String, data: Any?) {
                    emit("tuya_flow_step", mapOf("flow" to "sub", "step" to step, "data" to (data?.toString() ?: "")))
                }
            })

        subActivator = ThingHomeSdk.getActivatorInstance().newGwSubDevActivator(builder)
        subActivator?.start()

        result.success(true)
    }

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

    private fun emit(method: String, args: Any?) {
        try {
            if (!::channel.isInitialized) return
            activity?.runOnUiThread { channel.invokeMethod(method, args) } ?: channel.invokeMethod(method, args)
        } catch (t: Throwable) {
            Log.w(TAG, "emit failed: $method -> ${t.message}")
        }
    }

    companion object {
        private const val TAG = "TuyaBridge"
    }
}
