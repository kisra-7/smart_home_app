package com.example.alrawi_app

import android.os.Bundle
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterFragmentActivity() {

    private val CHANNEL = "tuya_bridge"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep a reference so BizBundle UI / scanner can open using the current Activity
        TuyaBridge.bindActivity(this)
    }

    override fun onDestroy() {
        TuyaBridge.unbindActivity(this)
        super.onDestroy()
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        val channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)

        // (Optional) allow native -> Flutter event callbacks if you use them later
        TuyaBridge.setChannel(channel)

        channel.setMethodCallHandler { call, result ->
            TuyaBridge.handle(call, result)
        }
    }
}