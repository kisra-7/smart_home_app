import 'package:flutter/material.dart';
import 'pages/auth_page.dart';
import 'tuya/tuya_platform.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // No UI snackbar. Only debug logs.
  try {
    await TuyaPlatform.initSdk();
    debugPrint("✅ Tuya initSdk OK (from Dart)");
  } catch (e) {
    debugPrint("❌ Tuya initSdk failed (from Dart): $e");
  }

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: "Alrawi Smart Home",
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF0B84FF)),
      ),
      home: const AuthPage(),
    );
  }
}
