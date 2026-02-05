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
        Log.d(TAG, "TuyaBridge attached")
    }

    fun detach() {
        if (::channel.isInitialized) channel.setMethodCallHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        // ✅ Always compile / always respond (no Tuya SDK imports)
        when (call.method) {
            "initSdk" -> result.success(null)
            "isLoggedIn" -> result.success(false)
            "logout" -> result.success(null)

            "loginByEmail" -> result.error("STUB", "TuyaBridge is stubbed (Kotlin compile debug step).", null)
            "sendEmailCode" -> result.error("STUB", "TuyaBridge is stubbed (Kotlin compile debug step).", null)
            "registerEmail" -> result.error("STUB", "TuyaBridge is stubbed (Kotlin compile debug step).", null)

            "getHomeList" -> result.success(emptyList<Map<String, Any>>())
            "createHome" -> result.success(mapOf("homeId" to 0, "name" to "Home"))

            "openAddGateway" -> result.error("STUB", "TuyaBridge is stubbed.", null)
            "openQrScan" -> result.error("STUB", "TuyaBridge is stubbed.", null)
            "openAddZigbeeSubDevice" -> result.error("STUB", "TuyaBridge is stubbed.", null)

            "stopActivator" -> result.success(null)
            else -> result.notImplemented()
        }
    }

    companion object {
        private const val TAG = "TuyaBridge"
    }
}
