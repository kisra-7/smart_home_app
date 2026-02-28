package com.example.alrawi_app

import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    // ✅ MUST match Dart exactly: MethodChannel("tuya_bridge")
    private val channelName = "tuya_bridge"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TuyaBridge.bindActivity(this)
    }

    override fun onDestroy() {
        TuyaBridge.unbindActivity(this)
        super.onDestroy()
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        val channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
        TuyaBridge.setChannel(channel)

        channel.setMethodCallHandler { call, result ->
            TuyaBridge.handle(call, result)
        }
    }
}