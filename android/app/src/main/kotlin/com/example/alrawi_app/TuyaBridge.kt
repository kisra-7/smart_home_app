package com.example.alrawi_app

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

object TuyaBridge {

    private const val CHANNEL = "tuya_config"
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
                    }

                    else -> result.notImplemented()
                }
            } catch (e: Throwable) {
                result.error("NATIVE_EXCEPTION", e.message ?: "Unknown native error", null)
            }
        }
    }

    private fun ensureInitOrThrow() {
        if (!sdkInitialized) {
            throw IllegalStateException("SDK not initialized yet. Running initSdk first...")
        }
    }
}
