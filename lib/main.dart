import 'package:alrawi_app/pages/tuya_test_pages.dart';
import 'package:flutter/material.dart';
import 'tuya/tuya_platform.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await TuyaPlatform.initSdk(); // must exist + must be awaited
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      debugShowCheckedModeBanner: false,
      home: TuyaTestPage(),
    );
  }
}
