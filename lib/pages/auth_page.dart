import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../tuya/tuya_platform.dart';
import '../tuya/controllers/home_hub_controller.dart';
import 'home_hub_page.dart';

class AuthPage extends ConsumerStatefulWidget {
  const AuthPage({super.key});

  @override
  ConsumerState<AuthPage> createState() => _AuthPageState();
}

class _AuthPageState extends ConsumerState<AuthPage> {
  final _countryCtrl = TextEditingController(text: "968");
  final _emailCtrl = TextEditingController();
  final _passCtrl = TextEditingController();
  final _codeCtrl = TextEditingController();

  bool _busy = false;
  bool _obscure = true;
  bool _registerMode = false;

  String get _country => _countryCtrl.text.trim();
  String get _email => _emailCtrl.text.trim();
  String get _pass => _passCtrl.text;
  String get _code => _codeCtrl.text.trim();

  Future<void> _goHome() async {
    // ✅ Ensure controller loads + sets Biz current home early
    await ref.read(homeHubControllerProvider.notifier).loadHomes(autoPickFirst: true);

    if (!mounted) return;
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(builder: (_) => const HomeHubPage()),
    );
  }

  @override
  void initState() {
    super.initState();
    _autoRoute();
  }

  Future<void> _autoRoute() async {
    try {
      final loggedIn = await TuyaPlatform.isLoggedIn();
      if (!mounted) return;
      if (loggedIn) {
        await _goHome();
      }
    } catch (_) {}
  }

  Future<void> _ensureHomeThenGo() async {
    await TuyaPlatform.ensureHome(
      name: "My Home",
      geoName: "Oman",
      rooms: const ["Living Room", "Bedroom"],
    );

    await _goHome();
  }

  // ✅ Keep the rest of your existing UI/buttons exactly as they are.
  @override
  Widget build(BuildContext context) {
    // ... keep your exact existing build UI ...
    return const Scaffold(
      body: Center(child: Text("Keep your existing AuthPage UI here (unchanged)")),
    );
  }
}