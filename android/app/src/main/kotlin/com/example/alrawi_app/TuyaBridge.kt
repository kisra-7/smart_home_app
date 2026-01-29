package com.example.alrawi_app

import android.app.Activity
import android.content.Context
import android.util.Log
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
        when (call.method) {
            "initSdk" -> {
                try {
                    Log.d(TAG, "initSdk called")
                    result.success(null)
                } catch (e: Exception) {
                    Log.e(TAG, "initSdk error", e)
                    result.error("INIT_SDK_ERROR", e.message, null)
                }
            }

            "isLoggedIn" -> {
                try {
                    val loggedIn = false
                    result.success(loggedIn)
                } catch (e: Exception) {
                    Log.e(TAG, "isLoggedIn error", e)
                    result.error("IS_LOGGED_IN_ERROR", e.message, null)
                }
            }

            "logout" -> {
                try {
                    result.success(null)
                } catch (e: Exception) {
                    Log.e(TAG, "logout error", e)
                    result.error("LOGOUT_ERROR", e.message, null)
                }
            }

            "loginByEmail" -> {
                val countryCode = call.argument<String>("countryCode") ?: ""
                val email = call.argument<String>("email") ?: ""
                val password = call.argument<String>("password") ?: ""

                try {
                    Log.d(TAG, "loginByEmail(country=$countryCode, email=$email)")
                    result.success(null)
                } catch (e: Exception) {
                    Log.e(TAG, "loginByEmail error", e)
                    result.error("LOGIN_ERROR", e.message, null)
                }
            }

            "sendEmailCode" -> {
                val countryCode = call.argument<String>("countryCode") ?: ""
                val email = call.argument<String>("email") ?: ""
                val type = call.argument<Int>("type") ?: 1

                try {
                    Log.d(TAG, "sendEmailCode(country=$countryCode, email=$email, type=$type)")
                    result.success(null)
                } catch (e: Exception) {
                    Log.e(TAG, "sendEmailCode error", e)
                    result.error("SEND_CODE_ERROR", e.message, null)
                }
            }

            "registerEmail" -> {
                val countryCode = call.argument<String>("countryCode") ?: ""
                val email = call.argument<String>("email") ?: ""
                val password = call.argument<String>("password") ?: ""
                val code = call.argument<String>("code") ?: ""

                try {
                    Log.d(TAG, "registerEmail(country=$countryCode, email=$email, code=$code)")
                    result.success(null)
                } catch (e: Exception) {
                    Log.e(TAG, "registerEmail error", e)
                    result.error("REGISTER_ERROR", e.message, null)
                }
            }

            "getHomeList" -> {
                try {
                    val homes: List<Map<String, Any>> = emptyList()
                    result.success(homes)
                } catch (e: Exception) {
                    Log.e(TAG, "getHomeList error", e)
                    result.error("HOME_LIST_ERROR", e.message, null)
                }
            }

            "createHome" -> {
                val name = call.argument<String>("name") ?: ""
                val geoName = call.argument<String>("geoName") ?: ""
                val rooms = call.argument<List<String>>("rooms") ?: emptyList()

                try {
                    val created: Map<String, Any> = mapOf(
                        "homeId" to 0,
                        "name" to name,
                        "geoName" to geoName,
                        "rooms" to rooms
                    )
                    result.success(created)
                } catch (e: Exception) {
                    Log.e(TAG, "createHome error", e)
                    result.error("CREATE_HOME_ERROR", e.message, null)
                }
            }

            "openAddGateway" -> {
                result.error(
                    "BIZBUNDLE_NOT_ENABLED",
                    "openAddGateway requires Tuya BizBundle integration on Android.",
                    null
                )
            }

            "openAddZigbeeSubDevice" -> {
                result.error(
                    "BIZBUNDLE_NOT_ENABLED",
                    "openAddZigbeeSubDevice requires Tuya BizBundle integration on Android.",
                    null
                )
            }

            "stopActivator" -> {
                result.success(null)
            }

            else -> result.notImplemented()
        }
    }

    companion object {
        private const val TAG = "TuyaBridge"
    }
}
