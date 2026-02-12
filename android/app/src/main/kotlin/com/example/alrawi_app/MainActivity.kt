package com.example.alrawi_app

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {

    private var tuyaBridge: TuyaBridge? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // ✅ Register our MethodChannel here
        tuyaBridge = TuyaBridge(applicationContext, this).apply {
            attachToEngine(flutterEngine)
        }
    }

    override fun onDestroy() {
        tuyaBridge?.detach()
        tuyaBridge = null
        super.onDestroy()
    }
}
