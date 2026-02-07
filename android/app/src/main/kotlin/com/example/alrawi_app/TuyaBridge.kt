package com.example.alrawi_app

import android.app.Activity
import android.content.Context
import android.util.Log
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.sdk.api.IResultCallback
import com.thingclips.smart.android.user.api.ILoginCallback
import com.thingclips.smart.android.user.api.ILogoutCallback
import com.thingclips.smart.android.user.api.IRegisterCallback
import com.thingclips.smart.android.user.bean.User
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class TuyaBridge(
    private val context: Context,
    private val activity: Activity?
) : MethodChannel.MethodCallHandler {

    private lateinit var channel: MethodChannel

    fun attachToEngine(flutterEngine: FlutterEngine) {
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "tuya_config")
        channel.setMethodCallHandler(this)
        Log.d(TAG, "TuyaBridge attached to engine")
    }

    fun detach() {
        if (::channel.isInitialized) {
            channel.setMethodCallHandler(null)
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {

                "initSdk" -> {
                    // SDK init should be done in Application.onCreate()
                    result.success(true)
                }

                "isloggedIn" -> {
                    result.success(ThingHomeSdk.getUserInstance().isLogin)
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

                "sendEmailCode" -> {
                    val countryCode = call.argument<String>("countryCode") ?: ""
                    val email = call.argument<String>("email") ?: ""
                    val region = call.argument<String>("region") ?: "EU"
                    val type = call.argument<Int>("type") ?: 1

                    if (countryCode.isBlank() || email.isBlank()) {
                        result.error("ARGUMENTS", "countryCode/email cannot be blank", null)
                        return
                    }

                    // ✅ Correct callback type for your SDK: com.thingclips.smart.sdk.api.IResultCallback
                    ThingHomeSdk.getUserInstance().sendVerifyCodeWithUserName(
                        countryCode,
                        email,
                        region,
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

                    if (countryCode.isBlank() || email.isBlank() || password.isBlank()) {
                        result.error("ARGUMENTS", "countryCode/email/password cannot be blank", null)
                        return
                    }

                    ThingHomeSdk.getUserInstance().registerAccountWithEmail(
                        countryCode,
                        email,
                        password,
                        code,
                        object : IRegisterCallback {
                            override fun onSuccess(user: User) {
                                result.success(
                                    mapOf(
                                        "uid" to user.uid,
                                        "nickname" to user.nickName
                                    )
                                )
                            }

                            override fun onError(code: String, error: String) {
                                result.error(code, error, null)
                            }
                        }
                    )
                }

                "loginByEmail" -> {
                    val countryCode = call.argument<String>("countryCode") ?: ""
                    val email = call.argument<String>("email") ?: ""
                    val password = call.argument<String>("password") ?: ""

                    if (countryCode.isBlank() || email.isBlank() || password.isBlank()) {
                        result.error("ARGUMENTS", "countryCode/email/password cannot be blank", null)
                        return
                    }

                    ThingHomeSdk.getUserInstance().loginWithEmail(
                        countryCode,
                        email,
                        password,
                        object : ILoginCallback {
                            override fun onSuccess(user: User) {
                                result.success(
                                    mapOf(
                                        "uid" to user.uid,
                                        "nickname" to user.nickName
                                    )
                                )
                            }

                            override fun onError(code: String, error: String) {
                                result.error(code, error, null)
                            }
                        }
                    )
                }

                else -> result.notImplemented()
            }
        } catch (e: Exception) {
            result.error("NATIVE_EXCEPTION", e.message, Log.getStackTraceString(e))
        }
    }

    companion object {
        private const val TAG = "TuyaBridge"
    }
}
