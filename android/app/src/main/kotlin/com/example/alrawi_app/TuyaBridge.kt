package com.example.alrawi_app

import android.app.Activity
import android.content.Context
import android.util.Log
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import com.thingclips.smart.home.sdk.ThingHomeSdk
import java.lang.reflect.Proxy

class TuyaBridge(
    private val context: Context,
    private val activity: Activity
) : MethodChannel.MethodCallHandler {

    private lateinit var channel: MethodChannel

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

            "initSdk" -> {
                // SDK initialized in MainApplication.onCreate()
                Log.d(TAG, "initSdk called from Flutter (noop)")
                result.success(true)
            }

            "isLoggedIn" -> {
                try {
                    val loggedIn = ThingHomeSdk.getUserInstance().isLogin
                    result.success(loggedIn)
                } catch (t: Throwable) {
                    result.error("IS_LOGGED_IN_FAILED", t.message, null)
                }
            }

            "logout" -> {
                try {
                    ThingHomeSdk.getUserInstance().logout(object :Any() {})
                    // Some SDKs have logout(IResultCallback). We'll fallback with reflection:
                    safeLogout(result)
                } catch (t: Throwable) {
                    safeLogout(result)
                }
            }

            "loginByEmail" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                val countryCode = (args["countryCode"] as? String) ?: ""
                val email = (args["email"] as? String) ?: ""
                val password = (args["password"] as? String) ?: ""

                loginWithEmail(countryCode, email, password, result)
            }

            "sendEmailCode" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                val countryCode = (args["countryCode"] as? String) ?: ""
                val email = (args["email"] as? String) ?: ""
                val type = (args["type"] as? Int) ?: 1

                // Tuya doc: sendVerifyCodeWithUserName(userName, region, countryCode, type, cb) :contentReference[oaicite:3]{index=3}
                sendEmailCode(email, countryCode, type, result)
            }

            "registerEmail" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                val countryCode = (args["countryCode"] as? String) ?: ""
                val email = (args["email"] as? String) ?: ""
                val password = (args["password"] as? String) ?: ""
                val code = (args["code"] as? String) ?: ""

                registerWithEmail(countryCode, email, password, code, result)
            }

            "getHomeList" -> queryHomeList(result)

            "createHome" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                val name = (args["name"] as? String) ?: "My Home"
                val geoName = (args["geoName"] as? String) ?: ""
                val rooms = (args["rooms"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

                createHome(name, geoName, rooms, result)
            }

            "openAddGateway" -> {
                val args = call.arguments as? Map<*, *>
                val passedHomeId = (args?.get("homeId") as? Number)?.toLong()

                openDevicePairing(passedHomeId, result)
            }

            "openQrScan" -> {
                // Most SDKs expose QR scan inside the same activator BizBundle entry.
                openDevicePairing(null, result)
            }

            "openAddZigbeeSubDevice" -> {
                // Use same “Add Device” UI flow
                openDevicePairing(null, result)
            }

            "stopActivator" -> {
                // No-op for BizBundle-based flow
                result.success(true)
            }

            else -> result.notImplemented()
        }
    }

    // ---------- User APIs (Reflection-friendly) ----------

    private fun loginWithEmail(countryCode: String, email: String, password: String, result: MethodChannel.Result) {
        try {
            val userInst = ThingHomeSdk.getUserInstance()

            // callback interfaces differ across SDK versions; use reflection + dynamic proxy.
            val cb = createLoginCallback(
                onSuccess = { userObj ->
                    val map = mapOf(
                        "ok" to true,
                        "user" to (userObj?.toString() ?: "User")
                    )
                    result.success(map)
                },
                onError = { code, msg ->
                    result.error(code ?: "LOGIN_FAILED", msg ?: "login failed", null)
                }
            )

            val m = userInst.javaClass.methods.firstOrNull {
                it.name == "loginWithEmail" && it.parameterTypes.size == 4
            } ?: throw NoSuchMethodException("loginWithEmail not found")

            m.invoke(userInst, countryCode, email, password, cb)
        } catch (t: Throwable) {
            result.error("LOGIN_EXCEPTION", t.message, null)
        }
    }

    private fun sendEmailCode(email: String, countryCode: String, type: Int, result: MethodChannel.Result) {
        try {
            val userInst = ThingHomeSdk.getUserInstance()

            val cb = createResultCallback(
                onSuccess = { result.success(true) },
                onError = { code, msg -> result.error(code ?: "SEND_CODE_FAILED", msg ?: "send code failed", null) }
            )

            val m = userInst.javaClass.methods.firstOrNull {
                it.name == "sendVerifyCodeWithUserName" && it.parameterTypes.size == 5
            } ?: throw NoSuchMethodException("sendVerifyCodeWithUserName not found")

            // region is "" per docs :contentReference[oaicite:4]{index=4}
            m.invoke(userInst, email, "", countryCode, type, cb)
        } catch (t: Throwable) {
            result.error("SEND_CODE_EXCEPTION", t.message, null)
        }
    }

    private fun registerWithEmail(countryCode: String, email: String, password: String, code: String, result: MethodChannel.Result) {
        try {
            val userInst = ThingHomeSdk.getUserInstance()

            val cb = createRegisterCallback(
                onSuccess = { userObj ->
                    val map = mapOf(
                        "ok" to true,
                        "user" to (userObj?.toString() ?: "User")
                    )
                    result.success(map)
                },
                onError = { errCode, msg ->
                    result.error(errCode ?: "REGISTER_FAILED", msg ?: "register failed", null)
                }
            )

            val m = userInst.javaClass.methods.firstOrNull {
                it.name == "registerAccountWithEmail" && it.parameterTypes.size == 5
            } ?: throw NoSuchMethodException("registerAccountWithEmail not found")

            m.invoke(userInst, countryCode, email, password, code, cb)
        } catch (t: Throwable) {
            result.error("REGISTER_EXCEPTION", t.message, null)
        }
    }

    private fun safeLogout(result: MethodChannel.Result) {
        try {
            val userInst = ThingHomeSdk.getUserInstance()
            val cb = createResultCallback(
                onSuccess = { result.success(true) },
                onError = { code, msg -> result.error(code ?: "LOGOUT_FAILED", msg ?: "logout failed", null) }
            )

            val m = userInst.javaClass.methods.firstOrNull {
                it.name == "logout" && it.parameterTypes.size == 1
            } ?: run {
                // Some SDKs have logout() without args:
                val m0 = userInst.javaClass.methods.firstOrNull { it.name == "logout" && it.parameterTypes.isEmpty() }
                if (m0 != null) {
                    m0.invoke(userInst)
                    result.success(true)
                    return
                }
                throw NoSuchMethodException("logout not found")
            }

            m.invoke(userInst, cb)
        } catch (t: Throwable) {
            result.error("LOGOUT_EXCEPTION", t.message, null)
        }
    }

    // ---------- Home APIs ----------

    private fun queryHomeList(result: MethodChannel.Result) {
        try {
            val homeMgr = ThingHomeSdk.getHomeManagerInstance()

            val cb = createGetHomeListCallback(
                onSuccess = { listObj ->
                    val list = mutableListOf<Map<String, Any?>>()

                    val raw = listObj as? List<*> ?: emptyList<Any>()
                    for (bean in raw) {
                        if (bean == null) continue
                        val homeId = readLong(bean, "getHomeId") ?: readLongField(bean, "homeId")
                        val name = readString(bean, "getName") ?: readStringField(bean, "name")
                        list.add(mapOf("homeId" to homeId, "name" to name))
                    }
                    result.success(list)
                },
                onError = { code, msg ->
                    result.error(code ?: "HOME_LIST_FAILED", msg ?: "query home list failed", null)
                }
            )

            // Docs: queryHomeList(IThingGetHomeListCallback) :contentReference[oaicite:5]{index=5}
            val m = homeMgr.javaClass.methods.firstOrNull {
                it.name == "queryHomeList" && it.parameterTypes.size == 1
            } ?: throw NoSuchMethodException("queryHomeList not found")

            m.invoke(homeMgr, cb)
        } catch (t: Throwable) {
            result.error("HOME_LIST_EXCEPTION", t.message, null)
        }
    }

    private fun createHome(name: String, geoName: String, rooms: List<String>, result: MethodChannel.Result) {
        try {
            val homeMgr = ThingHomeSdk.getHomeManagerInstance()

            val cb = createHomeResultCallback(
                onSuccess = { homeBean ->
                    val homeId = if (homeBean != null) {
                        readLong(homeBean, "getHomeId") ?: readLongField(homeBean, "homeId")
                    } else null

                    result.success(
                        mapOf(
                            "homeId" to homeId,
                            "name" to name
                        )
                    )
                },
                onError = { code, msg ->
                    result.error(code ?: "CREATE_HOME_FAILED", msg ?: "create home failed", null)
                }
            )

            // createHome(name, lon, lat, geoName, rooms, callback)
            val m = homeMgr.javaClass.methods.firstOrNull {
                it.name == "createHome" && it.parameterTypes.size == 6
            } ?: throw NoSuchMethodException("createHome not found")

            m.invoke(homeMgr, name, 0.0, 0.0, geoName, rooms, cb)
        } catch (t: Throwable) {
            result.error("CREATE_HOME_EXCEPTION", t.message, null)
        }
    }

    // ---------- Device Pairing UI BizBundle ----------

    private fun openDevicePairing(homeIdMaybe: Long?, result: MethodChannel.Result) {
        // BizBundle entry method differs across versions.
        // We'll:
        // 1) Resolve homeId (first home; if none, ask Flutter to create home first)
        // 2) Invoke ThingDeviceActivatorManager / TuyaDeviceActivatorManager via reflection
        queryHomeList(object : MethodChannel.Result {
            override fun success(res: Any?) {
                val homes = (res as? List<*>) ?: emptyList<Any>()
                val homeId = homeIdMaybe ?: (homes.firstOrNull() as? Map<*, *>)?.get("homeId") as? Long

                if (homeId == null) {
                    result.error("NO_HOME", "No home found. Create a home first.", null)
                    return
                }

                try {
                    // Newer docs mention ThingDeviceActivatorManager.INSTANCE... :contentReference[oaicite:6]{index=6}
                    // Older docs mention TuyaDeviceActivatorManager.startDeviceActiveAction(activity, homeId, listener) :contentReference[oaicite:7]{index=7}
                    if (tryStartActivator("com.thingclips.smart.bizbundle.activator.ThingDeviceActivatorManager", homeId)) {
                        result.success(true); return
                    }
                    if (tryStartActivator("com.tuya.smart.bizbundle.activator.TuyaDeviceActivatorManager", homeId)) {
                        result.success(true); return
                    }
                    if (tryStartActivator("com.tuya.smart.android.device.activator.TuyaDeviceActivatorManager", homeId)) {
                        result.success(true); return
                    }

                    result.error(
                        "ACTIVATOR_NOT_FOUND",
                        "Device Pairing BizBundle class not found. Make sure thingsmart-bizbundle-device_activator is added.",
                        null
                    )
                } catch (t: Throwable) {
                    result.error("OPEN_ADD_DEVICE_FAILED", t.message, null)
                }
            }

            override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                result.error(errorCode, errorMessage, errorDetails)
            }

            override fun notImplemented() {
                result.error("HOME_LIST_NOT_IMPL", "Home list not implemented", null)
            }
        })
    }

    private fun tryStartActivator(className: String, homeId: Long): Boolean {
        return try {
            val cls = Class.forName(className)

            // If it’s Kotlin object with INSTANCE:
            val instance = cls.fields.firstOrNull { it.name == "INSTANCE" }?.get(null)

            // Patterns we support:
            // A) startDeviceActiveAction(activity, homeId, listener)
            // B) startDeviceActiveAction(activity, homeId)
            // C) startDeviceActiveAction(activity) then set homeId elsewhere (less common)
            val target = instance ?: cls

            val methods = (instance?.javaClass ?: cls).methods

            // 1) activity + homeId
            methods.firstOrNull { it.name == "startDeviceActiveAction" && it.parameterTypes.size == 2 }?.let { m ->
                m.invoke(target, activity, homeId)
                Log.d(TAG, "Activator started via $className (2 args)")
                return true
            }

            // 2) activity + homeId + listener (we pass a no-op proxy)
            methods.firstOrNull { it.name == "startDeviceActiveAction" && it.parameterTypes.size == 3 }?.let { m ->
                val listenerType = m.parameterTypes[2]
                val listener = Proxy.newProxyInstance(
                    listenerType.classLoader,
                    arrayOf(listenerType)
                ) { _, _, _ -> null }
                m.invoke(target, activity, homeId, listener)
                Log.d(TAG, "Activator started via $className (3 args)")
                return true
            }

            false
        } catch (_: Throwable) {
            false
        }
    }

    // ---------- Dynamic proxy helpers (avoid SDK interface name mismatch) ----------

    private fun createLoginCallback(
        onSuccess: (Any?) -> Unit,
        onError: (String?, String?) -> Unit
    ): Any {
        val names = listOf(
            "com.thingclips.smart.android.user.api.ILoginCallback",
            "com.tuya.smart.android.user.api.ILoginCallback"
        )
        val iface = names.firstNotNullOfOrNull { runCatching { Class.forName(it) }.getOrNull() }
            ?: throw ClassNotFoundException("ILoginCallback not found")

        return Proxy.newProxyInstance(
            iface.classLoader,
            arrayOf(iface)
        ) { _, method, args ->
            when (method.name) {
                "onSuccess" -> onSuccess(args?.getOrNull(0))
                "onError" -> onError(args?.getOrNull(0) as? String, args?.getOrNull(1) as? String)
            }
            null
        }
    }

    private fun createRegisterCallback(
        onSuccess: (Any?) -> Unit,
        onError: (String?, String?) -> Unit
    ): Any {
        val names = listOf(
            "com.thingclips.smart.android.user.api.IRegisterCallback",
            "com.tuya.smart.android.user.api.IRegisterCallback"
        )
        val iface = names.firstNotNullOfOrNull { runCatching { Class.forName(it) }.getOrNull() }
            ?: throw ClassNotFoundException("IRegisterCallback not found")

        return Proxy.newProxyInstance(
            iface.classLoader,
            arrayOf(iface)
        ) { _, method, args ->
            when (method.name) {
                "onSuccess" -> onSuccess(args?.getOrNull(0))
                "onError" -> onError(args?.getOrNull(0) as? String, args?.getOrNull(1) as? String)
            }
            null
        }
    }

    private fun createResultCallback(
        onSuccess: () -> Unit,
        onError: (String?, String?) -> Unit
    ): Any {
        val names = listOf(
            "com.thingclips.smart.sdk.api.IResultCallback",
            "com.tuya.smart.sdk.api.IResultCallback"
        )
        val iface = names.firstNotNullOfOrNull { runCatching { Class.forName(it) }.getOrNull() }
            ?: throw ClassNotFoundException("IResultCallback not found")

        return Proxy.newProxyInstance(
            iface.classLoader,
            arrayOf(iface)
        ) { _, method, args ->
            when (method.name) {
                "onSuccess" -> onSuccess()
                "onError" -> onError(args?.getOrNull(0) as? String, args?.getOrNull(1) as? String)
            }
            null
        }
    }

    private fun createGetHomeListCallback(
        onSuccess: (Any?) -> Unit,
        onError: (String?, String?) -> Unit
    ): Any {
        val names = listOf(
            "com.thingclips.smart.home.sdk.callback.IThingGetHomeListCallback",
            "com.tuya.smart.home.sdk.callback.ITuyaGetHomeListCallback"
        )
        val iface = names.firstNotNullOfOrNull { runCatching { Class.forName(it) }.getOrNull() }
            ?: throw ClassNotFoundException("GetHomeListCallback not found")

        return Proxy.newProxyInstance(
            iface.classLoader,
            arrayOf(iface)
        ) { _, method, args ->
            when (method.name) {
                "onSuccess" -> onSuccess(args?.getOrNull(0))
                "onError" -> onError(args?.getOrNull(0) as? String, args?.getOrNull(1) as? String)
            }
            null
        }
    }

    private fun createHomeResultCallback(
        onSuccess: (Any?) -> Unit,
        onError: (String?, String?) -> Unit
    ): Any {
        val names = listOf(
            "com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback",
            "com.tuya.smart.home.sdk.callback.ITuyaHomeResultCallback"
        )
        val iface = names.firstNotNullOfOrNull { runCatching { Class.forName(it) }.getOrNull() }
            ?: throw ClassNotFoundException("HomeResultCallback not found")

        return Proxy.newProxyInstance(
            iface.classLoader,
            arrayOf(iface)
        ) { _, method, args ->
            when (method.name) {
                "onSuccess" -> onSuccess(args?.getOrNull(0))
                "onError" -> onError(args?.getOrNull(0) as? String, args?.getOrNull(1) as? String)
            }
            null
        }
    }

    // ---------- Reflection field/getter readers ----------
    private fun readLong(bean: Any, getter: String): Long? =
        runCatching { bean.javaClass.methods.firstOrNull { it.name == getter }?.invoke(bean) as? Long }.getOrNull()

    private fun readString(bean: Any, getter: String): String? =
        runCatching { bean.javaClass.methods.firstOrNull { it.name == getter }?.invoke(bean) as? String }.getOrNull()

    private fun readLongField(bean: Any, field: String): Long? =
        runCatching { bean.javaClass.fields.firstOrNull { it.name == field }?.get(bean) as? Long }.getOrNull()

    private fun readStringField(bean: Any, field: String): String? =
        runCatching { bean.javaClass.fields.firstOrNull { it.name == field }?.get(bean) as? String }.getOrNull()

    companion object {
        private const val TAG = "TuyaBridge"
    }
}
