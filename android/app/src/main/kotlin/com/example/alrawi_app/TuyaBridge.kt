package com.example.alrawi_app

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.thingclips.smart.home.sdk.ThingHomeSdk
import dalvik.system.DexFile
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.lang.reflect.Proxy

class TuyaBridge(
    private val context: Context,
    private val activity: Activity?
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
            "initSdk" -> result.success(true)

            "isLoggedIn" -> runCatching {
                result.success(ThingHomeSdk.getUserInstance().isLogin)
            }.getOrElse { result.error("IS_LOGGED_IN_FAILED", it.message, null) }

            "logout" -> safeLogout(result)

            "loginByEmail" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                loginWithEmail(
                    args["countryCode"] as? String ?: "",
                    args["email"] as? String ?: "",
                    args["password"] as? String ?: "",
                    result
                )
            }

            "sendEmailCode" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                sendEmailCode(
                    args["email"] as? String ?: "",
                    args["countryCode"] as? String ?: "",
                    args["type"] as? Int ?: 1,
                    result
                )
            }

            "registerEmail" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                registerWithEmail(
                    args["countryCode"] as? String ?: "",
                    args["email"] as? String ?: "",
                    args["password"] as? String ?: "",
                    args["code"] as? String ?: "",
                    result
                )
            }

            "getHomeList" -> queryHomeList(result)

            "createHome" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                createHome(
                    args["name"] as? String ?: "My Home",
                    args["geoName"] as? String ?: "Oman",
                    (args["rooms"] as? List<*>)?.mapNotNull { it as? String } ?: listOf("Living Room"),
                    result
                )
            }

            // ✅ returns {homeId, name, created}
            "ensureHome" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                ensureHome(
                    name = args["name"] as? String ?: "My Home",
                    geoName = args["geoName"] as? String ?: "Oman",
                    rooms = (args["rooms"] as? List<*>)?.mapNotNull { it as? String } ?: listOf("Living Room"),
                    result = result
                )
            }

            // ✅ open add device using DeviceActivatorManager (NO BizBundleRouter)
            "openAddGateway" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                val homeId = (args["homeId"] as? Number)?.toLong()
                openDevicePairing(homeId, result)
            }

            "openQrScan" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                val homeId = (args["homeId"] as? Number)?.toLong()
                openDevicePairing(homeId, result)
            }

            "stopActivator" -> {
                stopActivatorSafe()
                result.success(true)
            }

            else -> result.notImplemented()
        }
    }

    // ---------- AUTH ----------

    private fun loginWithEmail(countryCode: String, email: String, password: String, result: MethodChannel.Result) {
        try {
            val user = ThingHomeSdk.getUserInstance()
            val cb = createCallback(
                possibleInterfaces = listOf(
                    "com.thingclips.smart.android.user.api.ILoginCallback",
                    "com.tuya.smart.android.user.api.ILoginCallback"
                ),
                onSuccess = { bizOnLogin(); result.success(true) },
                onError = { code, msg -> result.error(code ?: "LOGIN_FAILED", msg, null) }
            )

            val m = user.javaClass.methods.firstOrNull { it.name == "loginWithEmail" && it.parameterTypes.size == 4 }
                ?: throw NoSuchMethodException("loginWithEmail(countryCode,email,password,cb) not found")

            m.invoke(user, countryCode, email, password, cb)
        } catch (t: Throwable) {
            result.error("LOGIN_EXCEPTION", t.message, null)
        }
    }

    private fun sendEmailCode(email: String, countryCode: String, type: Int, result: MethodChannel.Result) {
        try {
            val user = ThingHomeSdk.getUserInstance()
            val cb = createCallback(
                possibleInterfaces = listOf(
                    "com.thingclips.smart.sdk.api.IResultCallback",
                    "com.tuya.smart.sdk.api.IResultCallback"
                ),
                onSuccess = { result.success(true) },
                onError = { code, msg -> result.error(code ?: "SEND_CODE_FAILED", msg, null) }
            )

            val m = user.javaClass.methods.firstOrNull { it.name == "sendVerifyCodeWithUserName" && it.parameterTypes.size == 5 }
                ?: throw NoSuchMethodException("sendVerifyCodeWithUserName(...) not found")

            m.invoke(user, email, "", countryCode, type, cb)
        } catch (t: Throwable) {
            result.error("SEND_CODE_EXCEPTION", t.message, null)
        }
    }

    private fun registerWithEmail(countryCode: String, email: String, password: String, code: String, result: MethodChannel.Result) {
        try {
            val user = ThingHomeSdk.getUserInstance()
            val cb = createCallback(
                possibleInterfaces = listOf(
                    "com.thingclips.smart.android.user.api.IRegisterCallback",
                    "com.tuya.smart.android.user.api.IRegisterCallback"
                ),
                onSuccess = { bizOnLogin(); result.success(true) },
                onError = { errCode, msg -> result.error(errCode ?: "REGISTER_FAILED", msg, null) }
            )

            val m = user.javaClass.methods.firstOrNull { it.name == "registerAccountWithEmail" && it.parameterTypes.size == 5 }
                ?: throw NoSuchMethodException("registerAccountWithEmail(...) not found")

            m.invoke(user, countryCode, email, password, code, cb)
        } catch (t: Throwable) {
            result.error("REGISTER_EXCEPTION", t.message, null)
        }
    }

    private fun safeLogout(result: MethodChannel.Result) {
        try {
            val user = ThingHomeSdk.getUserInstance()

            val m1 = user.javaClass.methods.firstOrNull { it.name == "logout" && it.parameterTypes.size == 1 }
            if (m1 != null) {
                val cb = createCallback(
                    possibleInterfaces = listOf(
                        "com.thingclips.smart.android.user.api.ILogoutCallback",
                        "com.tuya.smart.android.user.api.ILogoutCallback",
                        "com.thingclips.smart.sdk.api.IResultCallback",
                        "com.tuya.smart.sdk.api.IResultCallback"
                    ),
                    onSuccess = { bizOnLogout(); result.success(true) },
                    onError = { code, msg -> result.error(code ?: "LOGOUT_FAILED", msg, null) }
                )
                m1.invoke(user, cb)
                return
            }

            val m0 = user.javaClass.methods.firstOrNull { it.name == "logout" && it.parameterTypes.isEmpty() }
            if (m0 != null) {
                m0.invoke(user)
                bizOnLogout()
                result.success(true)
                return
            }

            result.error("LOGOUT_NOT_FOUND", "logout method not found", null)
        } catch (t: Throwable) {
            result.error("LOGOUT_EXCEPTION", t.message, null)
        }
    }

    // ---------- HOME ----------

    private fun queryHomeList(result: MethodChannel.Result) {
        try {
            val homeMgr = ThingHomeSdk.getHomeManagerInstance()

            val cb = createCallback(
                possibleInterfaces = listOf(
                    "com.thingclips.smart.home.sdk.callback.IThingGetHomeListCallback",
                    "com.tuya.smart.home.sdk.callback.ITuyaGetHomeListCallback"
                ),
                onSuccess = { arg ->
                    val raw = arg as? List<*> ?: emptyList<Any>()
                    val list = raw.mapNotNull { bean ->
                        if (bean == null) return@mapNotNull null
                        val homeId = (bean.javaClass.methods.firstOrNull { it.name == "getHomeId" }?.invoke(bean) as? Number)?.toLong()
                        val name = bean.javaClass.methods.firstOrNull { it.name == "getName" }?.invoke(bean) as? String
                        mapOf("homeId" to homeId, "name" to name)
                    }
                    result.success(list)
                },
                onError = { code, msg -> result.error(code ?: "HOME_LIST_FAILED", msg, null) }
            )

            val m = homeMgr.javaClass.methods.firstOrNull { it.name == "queryHomeList" && it.parameterTypes.size == 1 }
                ?: throw NoSuchMethodException("queryHomeList(cb) not found")

            m.invoke(homeMgr, cb)
        } catch (t: Throwable) {
            result.error("HOME_LIST_EXCEPTION", t.message, null)
        }
    }

    private fun createHome(name: String, geoName: String, rooms: List<String>, result: MethodChannel.Result) {
        try {
            val homeMgr = ThingHomeSdk.getHomeManagerInstance()

            val cb = createCallback(
                possibleInterfaces = listOf(
                    "com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback",
                    "com.tuya.smart.home.sdk.callback.ITuyaHomeResultCallback"
                ),
                onSuccess = { bean ->
                    val homeId = (bean?.javaClass?.methods?.firstOrNull { it.name == "getHomeId" }?.invoke(bean) as? Number)?.toLong() ?: 0L
                    result.success(mapOf("homeId" to homeId, "name" to name))
                },
                onError = { code, msg -> result.error(code ?: "CREATE_HOME_FAILED", msg, null) }
            )

            val m = homeMgr.javaClass.methods.firstOrNull { it.name == "createHome" && it.parameterTypes.size == 6 }
                ?: throw NoSuchMethodException("createHome(...) not found")

            m.invoke(homeMgr, name, 0.0, 0.0, geoName, rooms, cb)
        } catch (t: Throwable) {
            result.error("CREATE_HOME_EXCEPTION", t.message, null)
        }
    }

    private fun ensureHome(name: String, geoName: String, rooms: List<String>, result: MethodChannel.Result) {
        try {
            val homeMgr = ThingHomeSdk.getHomeManagerInstance()

            val listCb = createCallback(
                possibleInterfaces = listOf(
                    "com.thingclips.smart.home.sdk.callback.IThingGetHomeListCallback",
                    "com.tuya.smart.home.sdk.callback.ITuyaGetHomeListCallback"
                ),
                onSuccess = { arg ->
                    val raw = arg as? List<*> ?: emptyList<Any>()
                    if (raw.isNotEmpty()) {
                        val first = raw.first()
                        val homeId = (first?.javaClass?.methods?.firstOrNull { it.name == "getHomeId" }?.invoke(first) as? Number)?.toLong() ?: 0L
                        val homeName = (first?.javaClass?.methods?.firstOrNull { it.name == "getName" }?.invoke(first) as? String) ?: "Home"
                        result.success(mapOf("homeId" to homeId, "name" to homeName, "created" to false))
                        return@createCallback
                    }

                    val createCb = createCallback(
                        possibleInterfaces = listOf(
                            "com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback",
                            "com.tuya.smart.home.sdk.callback.ITuyaHomeResultCallback"
                        ),
                        onSuccess = { bean ->
                            val homeId = (bean?.javaClass?.methods?.firstOrNull { it.name == "getHomeId" }?.invoke(bean) as? Number)?.toLong() ?: 0L
                            result.success(mapOf("homeId" to homeId, "name" to name, "created" to true))
                        },
                        onError = { code, msg -> result.error(code ?: "ENSURE_HOME_CREATE_FAILED", msg, null) }
                    )

                    val mCreate = homeMgr.javaClass.methods.firstOrNull { it.name == "createHome" && it.parameterTypes.size == 6 }
                        ?: throw NoSuchMethodException("createHome(...) not found")

                    mCreate.invoke(homeMgr, name, 0.0, 0.0, geoName, rooms, createCb)
                },
                onError = { code, msg -> result.error(code ?: "ENSURE_HOME_LIST_FAILED", msg, null) }
            )

            val mList = homeMgr.javaClass.methods.firstOrNull { it.name == "queryHomeList" && it.parameterTypes.size == 1 }
                ?: throw NoSuchMethodException("queryHomeList(cb) not found")

            mList.invoke(homeMgr, listCb)
        } catch (t: Throwable) {
            result.error("ENSURE_HOME_EXCEPTION", t.message, null)
        }
    }

    // ---------- DEVICE ADD (NO BizBundleRouter) ----------

    private fun openDevicePairing(homeId: Long?, result: MethodChannel.Result) {
        if (activity == null) {
            result.error("NO_ACTIVITY", "Activity is null", null)
            return
        }

        fun launchWithHome(id: Long) {
            try {
                val ok = startDeviceActivatorAction(homeId = id)
                if (ok) result.success(true)
                else result.error("ACTIVATOR_OPEN_FAILED", "No compatible DeviceActivatorManager found in classpath.", null)
            } catch (t: Throwable) {
                val root = t.cause ?: t
                result.error("ACTIVATOR_OPEN_FAILED", "${root.javaClass.name}: ${root.message}", null)
            }
        }

        if (homeId != null && homeId > 0) {
            launchWithHome(homeId)
            return
        }

        // If homeId not passed, pick first home
        try {
            val homeMgr = ThingHomeSdk.getHomeManagerInstance()
            val cb = createCallback(
                possibleInterfaces = listOf(
                    "com.thingclips.smart.home.sdk.callback.IThingGetHomeListCallback",
                    "com.tuya.smart.home.sdk.callback.ITuyaGetHomeListCallback"
                ),
                onSuccess = { arg ->
                    val raw = arg as? List<*> ?: emptyList<Any>()
                    val first = raw.firstOrNull()
                    val id = (first?.javaClass?.methods?.firstOrNull { it.name == "getHomeId" }?.invoke(first) as? Number)?.toLong() ?: 0L
                    if (id <= 0L) {
                        result.error("NO_HOME", "No home found. Call ensureHome first.", null)
                        return@createCallback
                    }
                    launchWithHome(id)
                },
                onError = { code, msg -> result.error(code ?: "HOME_LIST_FAILED", msg, null) }
            )

            val m = homeMgr.javaClass.methods.firstOrNull { it.name == "queryHomeList" && it.parameterTypes.size == 1 }
                ?: throw NoSuchMethodException("queryHomeList(cb) not found")

            m.invoke(homeMgr, cb)
        } catch (t: Throwable) {
            result.error("ACTIVATOR_OPEN_FAILED", t.message, null)
        }
    }

    private fun startDeviceActivatorAction(homeId: Long): Boolean {
        val act = activity ?: return false

        val managerCandidates = listOf(
            "com.thingclips.smart.bizbundle.deviceactivator.ThingDeviceActivatorManager",
            "com.tuya.smart.bizbundle.deviceactivator.TuyaDeviceActivatorManager"
        )

        val managerClass = managerCandidates.firstNotNullOfOrNull { name ->
            runCatching { Class.forName(name) }.getOrNull()
        } ?: findClass("ThingDeviceActivatorManager")
        ?: findClass("TuyaDeviceActivatorManager")
        ?: return false

        val manager = runCatching { getObjectInstance(managerClass) }.getOrNull()
        val methods = (manager?.javaClass ?: managerClass).methods

        // Try common overloads
        val m1 = methods.firstOrNull { it.name == "startDeviceActiveAction" && it.parameterTypes.size == 1 && Activity::class.java.isAssignableFrom(it.parameterTypes[0]) }
        if (m1 != null) { if (manager != null) m1.invoke(manager, act) else m1.invoke(null, act); return true }

        val m2 = methods.firstOrNull { it.name == "startDeviceActiveAction" && it.parameterTypes.size == 2 && Activity::class.java.isAssignableFrom(it.parameterTypes[0]) && it.parameterTypes[1] == java.lang.Long.TYPE }
        if (m2 != null) { if (manager != null) m2.invoke(manager, act, homeId) else m2.invoke(null, act, homeId); return true }

        val m3 = methods.firstOrNull { it.name == "startDeviceActiveAction" && it.parameterTypes.size == 3 && Activity::class.java.isAssignableFrom(it.parameterTypes[0]) && it.parameterTypes[1] == java.lang.Long.TYPE && it.parameterTypes[2].name == "android.os.Bundle" }
        if (m3 != null) {
            val bundle = Bundle().apply { putLong("homeId", homeId); putLong("home_id", homeId) }
            if (manager != null) m3.invoke(manager, act, homeId, bundle) else m3.invoke(null, act, homeId, bundle)
            return true
        }

        // Bundle-only fallback
        val mb2 = methods.firstOrNull { it.name == "startDeviceActiveAction" && it.parameterTypes.size == 2 && Activity::class.java.isAssignableFrom(it.parameterTypes[0]) && it.parameterTypes[1].name == "android.os.Bundle" }
        if (mb2 != null) {
            val bundle = Bundle().apply { putLong("homeId", homeId); putLong("home_id", homeId) }
            if (manager != null) mb2.invoke(manager, act, bundle) else mb2.invoke(null, act, bundle)
            return true
        }

        return false
    }

    private fun stopActivatorSafe() {
        // optional, keep no-op
    }

    // ---------- BizBundle hooks (optional) ----------
    private fun bizOnLogin() {
        try {
            val clazz = Class.forName("com.thingclips.smart.bizbundle.initializer.BizBundleInitializer")
            clazz.methods.firstOrNull { it.name == "onLogin" && it.parameterTypes.isEmpty() }?.invoke(null)
        } catch (_: Throwable) {}
    }

    private fun bizOnLogout() {
        try {
            val clazz = Class.forName("com.thingclips.smart.bizbundle.initializer.BizBundleInitializer")
            clazz.methods.firstOrNull { it.name == "onLogout" && it.parameterTypes.size == 1 }?.invoke(null, context)
        } catch (_: Throwable) {}
    }

    // ---------- UTIL ----------
    private fun findClass(simpleName: String): Class<*>? {
        return try {
            val dex = DexFile(context.packageCodePath)
            val entries = dex.entries()
            while (entries.hasMoreElements()) {
                val name = entries.nextElement()
                if (name.endsWith(".$simpleName")) return Class.forName(name)
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun getObjectInstance(clazz: Class<*>): Any {
        return clazz.fields.firstOrNull { it.name == "INSTANCE" }?.get(null)
            ?: throw IllegalStateException("No INSTANCE field on ${clazz.name}")
    }

    private fun createCallback(
        possibleInterfaces: List<String>,
        onSuccess: (Any?) -> Unit,
        onError: (String?, String?) -> Unit
    ): Any {
        val iface = possibleInterfaces.firstNotNullOfOrNull {
            runCatching { Class.forName(it) }.getOrNull()
        } ?: throw ClassNotFoundException("Callback interface not found: $possibleInterfaces")

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

    companion object {
        private const val TAG = "TuyaBridge"
    }
}
