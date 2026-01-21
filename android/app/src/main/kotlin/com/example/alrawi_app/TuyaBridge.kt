package com.example.alrawi_app

<<<<<<< HEAD
import android.app.Application
import android.content.Context
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.home.sdk.bean.HomeBean
import com.thingclips.smart.home.sdk.callback.IThingGetHomeListCallback
import com.thingclips.smart.android.user.api.ILoginCallback
import com.thingclips.smart.android.user.api.ILogoutCallback
import com.thingclips.smart.android.user.api.IRegisterCallback
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
=======
import android.app.Activity
import android.app.Application
import com.thingclips.smart.android.user.api.ILoginCallback
import com.thingclips.smart.android.user.api.ILogoutCallback
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.home.sdk.bean.HomeBean
import com.thingclips.smart.home.sdk.callback.IThingGetHomeListCallback
import com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.lang.ref.WeakReference
>>>>>>> cc30e20 (fixed gradle problems)

object TuyaBridge {

    private const val CHANNEL = "tuya_config"
<<<<<<< HEAD
    private var sdkInitialized = false

    fun register(flutterEngine: FlutterEngine, context: Context) {
        val channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)

        channel.setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "initSdk" -> {
                        val app = context.applicationContext as Application
                        if (!sdkInitialized) {
                            ThingHomeSdk.init(app)
                            sdkInitialized = true
                        }
                        result.success(true)
                    }

                    "isLoggedIn" -> {
                        // ✅ Correct login state source for Thingclips/Tuya SDK
                        // If user session exists, this returns true.
                        val loggedIn = try {
                            ThingHomeSdk.getUserInstance().isLogin
                        } catch (e: Throwable) {
                            false
                        }
                        result.success(loggedIn)
                    }

                    "registerEmail" -> {
                        ensureInitOrThrow()

                        val args = call.arguments as? Map<*, *>
                        val countryCode = (args?.get("countryCode") as? String).orEmpty().trim()
                        val email = (args?.get("email") as? String).orEmpty().trim()
                        val password = (args?.get("password") as? String).orEmpty()

                        if (countryCode.isEmpty() || email.isEmpty() || password.isEmpty()) {
                            result.error(
                                "Illegal parameter",
                                "countryCode/email/password is empty",
                                null
                            )
                            return@setMethodCallHandler
                        }

                        // ✅ Register account inside your appKey user system
                        ThingHomeSdk.getUserInstance().registerAccountWithEmail(
                            countryCode,
                            email,
                            password,
                            object : IRegisterCallback {
                                override fun onSuccess(user: com.thingclips.smart.android.user.bean.User?) {
                                    result.success(true)
                                }

                                override fun onError(code: String?, error: String?) {
                                    result.error(
                                        code ?: "REGISTER_ERROR",
                                        error ?: "Unknown register error",
                                        null
                                    )
                                }
                            }
                        )
                    }

                    "loginEmail" -> {
                        ensureInitOrThrow()

                        val args = call.arguments as? Map<*, *>
                        val countryCode = (args?.get("countryCode") as? String).orEmpty().trim()
                        val email = (args?.get("email") as? String).orEmpty().trim()
                        val password = (args?.get("password") as? String).orEmpty()

                        if (countryCode.isEmpty() || email.isEmpty() || password.isEmpty()) {
                            result.error(
                                "Illegal parameter",
                                "countryCode/email/password is empty",
                                null
                            )
                            return@setMethodCallHandler
                        }

                        ThingHomeSdk.getUserInstance().loginWithEmail(
                            countryCode,
                            email,
                            password,
                            object : ILoginCallback {
                                override fun onSuccess(user: com.thingclips.smart.android.user.bean.User?) {
                                    result.success(true)
                                }

                                override fun onError(code: String?, error: String?) {
                                    result.error(
                                        code ?: "LOGIN_ERROR",
                                        error ?: "Unknown login error",
                                        null
                                    )
                                }
                            }
                        )
                    }

                    "logout" -> {
                        ensureInitOrThrow()

                        ThingHomeSdk.getUserInstance().logout(object : ILogoutCallback {
                            override fun onSuccess() {
                                result.success(true)
                            }

                            override fun onError(code: String?, error: String?) {
                                result.error(
                                    code ?: "LOGOUT_ERROR",
                                    error ?: "Unknown logout error",
                                    null
                                )
                            }
                        })
                    }

                    "getHomeList" -> {
                        ensureInitOrThrow()

                        val loggedIn = try {
                            ThingHomeSdk.getUserInstance().isLogin
                        } catch (e: Throwable) {
                            false
                        }

                        if (!loggedIn) {
                            result.error("NOT_LOGGED_IN", "Please login first", null)
                            return@setMethodCallHandler
                        }

                        ThingHomeSdk.getHomeManagerInstance()
                            .queryHomeList(object : IThingGetHomeListCallback {
                                override fun onSuccess(homeList: MutableList<HomeBean>?) {
                                    val mapped = (homeList ?: mutableListOf()).map {
                                        mapOf(
                                            "homeId" to it.homeId,
                                            "name" to (it.name ?: "Home")
                                        )
                                    }
                                    result.success(mapped)
                                }

                                override fun onError(code: String?, error: String?) {
                                    result.error(
                                        code ?: "HOME_LIST_ERROR",
                                        error ?: "Unknown home list error",
                                        null
                                    )
                                }
                            })
=======

    private var activityRef: WeakReference<Activity>? = null
    private var sdkInited = false

    fun register(flutterEngine: FlutterEngine, activity: Activity) {
        activityRef = WeakReference(activity)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {

                    // ---------------- SDK ----------------
                    "initSdk" -> {
                        try {
                            val app = activity.application as Application
                            if (!sdkInited) {
                                ThingHomeSdk.init(app)
                                sdkInited = true
                            }
                            result.success(true)
                        } catch (e: Throwable) {
                            result.error("INIT_FAILED", e.message, null)
                        }
                    }

                    "isLoggedIn" -> {
                        try {
                            val isLogin = ThingHomeSdk.getUserInstance().isLogin
                            result.success(isLogin)
                        } catch (e: Throwable) {
                            result.error("IS_LOGIN_FAILED", e.message, null)
                        }
                    }

                    "loginEmail" -> {
                        val args = call.arguments as? Map<*, *>
                        val countryCode = (args?.get("countryCode") as? String)?.trim().orEmpty()
                        val email = (args?.get("email") as? String)?.trim().orEmpty()
                        val password = (args?.get("password") as? String).orEmpty()

                        if (countryCode.isEmpty() || email.isEmpty() || password.isEmpty()) {
                            result.error("ILLEGAL_PARAM", "countryCode/email/password is empty", null)
                            return@setMethodCallHandler
                        }

                        try {
                            ThingHomeSdk.getUserInstance().loginWithEmail(
                                countryCode,
                                email,
                                password,
                                object : ILoginCallback {
                                    override fun onSuccess(user: com.thingclips.smart.android.user.bean.User) {
                                        result.success(true)
                                    }

                                    override fun onError(code: String, error: String) {
                                        result.error(code, error, null)
                                    }
                                }
                            )
                        } catch (e: Throwable) {
                            result.error("LOGIN_FAILED", e.message, null)
                        }
                    }

                    "logout" -> {
                        try {
                            ThingHomeSdk.getUserInstance().logout(object : ILogoutCallback {
                                override fun onSuccess() {
                                    result.success(true)
                                }

                                override fun onError(code: String, error: String) {
                                    result.error(code, error, null)
                                }
                            })
                        } catch (e: Throwable) {
                            result.error("LOGOUT_FAILED", e.message, null)
                        }
                    }

                    // ---------------- HOMES ----------------
                    "getHomeList" -> {
                        try {
                            ThingHomeSdk.getHomeManagerInstance()
                                .queryHomeList(object : IThingGetHomeListCallback {
                                    override fun onSuccess(homeList: MutableList<HomeBean>?) {
                                        val list = (homeList ?: mutableListOf()).map {
                                            mapOf(
                                                "homeId" to it.homeId,
                                                "name" to it.name
                                            )
                                        }
                                        result.success(list)
                                    }

                                    override fun onError(code: String, error: String) {
                                        result.error(code, error, null)
                                    }
                                })
                        } catch (e: Throwable) {
                            result.error("GET_HOMES_FAILED", e.message, null)
                        }
                    }

                    "createHome" -> {
    val args = call.arguments as? Map<*, *>
    val name = (args?.get("name") as? String)?.trim().orEmpty()
    val geoName = (args?.get("geoName") as? String)?.trim().orEmpty()
    val rooms = (args?.get("rooms") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

    if (name.isEmpty()) {
        result.error("ILLEGAL_PARAM", "Home name is empty", null)
        return@setMethodCallHandler
    }

    try {
        val city = if (geoName.isNotEmpty()) geoName else "Unknown"
        val lon = 0.0
        val lat = 0.0

        // ✅ Correct order for ThingHomeSdk createHome in most 6.x SDKs:
        // (name, lon, lat, geoName/city, rooms, callback)
        ThingHomeSdk.getHomeManagerInstance().createHome(
            name,
            lon,
            lat,
            city,
            rooms,
            object : IThingHomeResultCallback {
                override fun onSuccess(bean: HomeBean) {
                    result.success(
                        mapOf(
                            "homeId" to bean.homeId,
                            "name" to bean.name
                        )
                    )
                }

                override fun onError(code: String, error: String) {
                    result.error(code, error, null)
                }
            }
        )
    } catch (e: Throwable) {
        result.error("CREATE_HOME_FAILED", e.message, null)
    }
}


                    // ---------------- ACTIVATOR UI (BizBundle) ----------------
                    // We call BizBundle through reflection so the project COMPILES even if the dependency is missing.
                    "openAddGateway" -> {
                        val act = activityRef?.get()
                        if (act == null) {
                            result.error("NO_ACTIVITY", "Activity is null", null)
                            return@setMethodCallHandler
                        }

                        val args = call.arguments as? Map<*, *>
                        val homeId = (args?.get("homeId") as? Number)?.toLong() ?: 0L
                        if (homeId <= 0L) {
                            result.error("ILLEGAL_PARAM", "homeId is invalid", null)
                            return@setMethodCallHandler
                        }

                        try {
                            startActivatorUi(act, homeId)
                            result.success(true)
                        } catch (e: Throwable) {
                            result.error("OPEN_ACTIVATOR_FAILED", e.message, null)
                        }
                    }

                    "openAddZigbeeSubDevice" -> {
                        val act = activityRef?.get()
                        if (act == null) {
                            result.error("NO_ACTIVITY", "Activity is null", null)
                            return@setMethodCallHandler
                        }

                        val args = call.arguments as? Map<*, *>
                        val homeId = (args?.get("homeId") as? Number)?.toLong() ?: 0L
                        val gwId = (args?.get("gwId") as? String)?.trim().orEmpty()

                        if (homeId <= 0L || gwId.isEmpty()) {
                            result.error("ILLEGAL_PARAM", "homeId/gwId is invalid", null)
                            return@setMethodCallHandler
                        }

                        try {
                            // Many Tuya activator UIs handle sub-devices from inside the same flow.
                            startActivatorUi(act, homeId)
                            result.success(true)
                        } catch (e: Throwable) {
                            result.error("OPEN_ZIGBEE_FAILED", e.message, null)
                        }
                    }

                    "stopActivator" -> {
                        try {
                            activityRef?.get()?.onBackPressed()
                            result.success(true)
                        } catch (e: Throwable) {
                            result.error("STOP_FAILED", e.message, null)
                        }
>>>>>>> cc30e20 (fixed gradle problems)
                    }

                    else -> result.notImplemented()
                }
<<<<<<< HEAD
            } catch (e: Throwable) {
                result.error("NATIVE_EXCEPTION", e.message ?: "Unknown native error", null)
            }
        }
    }

    private fun ensureInitOrThrow() {
        if (!sdkInitialized) {
            throw IllegalStateException("SDK not initialized yet. Running initSdk first...")
        }
=======
            }
    }

    // Reflection wrapper:
    // com.thingclips.smart.bizbundle.deviceactivator.ThingDeviceActivatorManager.INSTANCE.startDeviceActiveAction(activity, homeId)
    private fun startActivatorUi(activity: Activity, homeId: Long) {
        val cls = Class.forName("com.thingclips.smart.bizbundle.deviceactivator.ThingDeviceActivatorManager")
        val instance = cls.getField("INSTANCE").get(null)
        val method = cls.getMethod("startDeviceActiveAction", Activity::class.java, Long::class.javaPrimitiveType)
        method.invoke(instance, activity, homeId)
>>>>>>> cc30e20 (fixed gradle problems)
    }
}
