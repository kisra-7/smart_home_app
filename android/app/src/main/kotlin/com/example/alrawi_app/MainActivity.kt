package com.example.alrawi_app

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
<<<<<<< HEAD
        TuyaBridge.register(flutterEngine, applicationContext)
=======
        TuyaBridge.register(flutterEngine, this)
>>>>>>> cc30e20 (fixed gradle problems)
    }
}
