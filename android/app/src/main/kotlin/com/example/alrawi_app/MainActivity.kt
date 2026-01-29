package com.example.alrawi_app

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {

    private var tuyaBridge: TuyaBridge? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        tuyaBridge = TuyaBridge(this, this).also { it.attachToEngine(flutterEngine) }
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        tuyaBridge?.detach()
        tuyaBridge = null
        super.cleanUpFlutterEngine(flutterEngine)
    }
}
