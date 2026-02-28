import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../tuya/tuya_platform.dart';
import 'home_hub_page.dart';

class AuthPage extends StatefulWidget {
  const AuthPage({super.key});

  @override
  State<AuthPage> createState() => _AuthPageState();
}

class _AuthPageState extends State<AuthPage> {
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

  Future<void> _setCurrentHome(int homeId) async {
    if (homeId <= 0) return;

    // Call native: setCurrentHome -> bootstraps BizBundle current family/home context
    try {
      const ch = MethodChannel('tuya_bridge');
      await ch.invokeMethod('setCurrentHome', {'homeId': homeId});
      debugPrint("✅ setCurrentHome($homeId) sent to native");
    } catch (e) {
      debugPrint("⚠️ setCurrentHome failed (continuing): $e");
    }
  }

  Future<void> _run(Future<void> Function() fn) async {
    if (!mounted) return;
    FocusScope.of(context).unfocus();
    setState(() => _busy = true);
    try {
      await fn();
    } catch (e) {
      debugPrint("❌ Auth error: $e");
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text("Error: $e")));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _autoCheck() async {
    try {
      final loggedIn = await TuyaPlatform.isLoggedIn();
      debugPrint("🔎 Auto login check => $loggedIn");
      if (!mounted) return;

      if (loggedIn) {
        final home = await TuyaPlatform.ensureHome(
          name: "My Home",
          geoName: "Oman",
          rooms: const ["Living Room"],
        );
        final homeId =
            (home is Map && home['homeId'] != null) ? (home['homeId'] as num).toInt() : 0;
        await _setCurrentHome(homeId);

        if (!mounted) return;
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(builder: (_) => const HomeHubPage()),
        );
      }
    } catch (e) {
      debugPrint("❌ Auto check error: $e");
    }
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _autoCheck());
  }

  @override
  void dispose() {
    _countryCtrl.dispose();
    _emailCtrl.dispose();
    _passCtrl.dispose();
    _codeCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final title = _registerMode ? "Create Account" : "Login";
    final btn = _registerMode ? "Register" : "Login";

    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            children: [
              TextField(
                controller: _countryCtrl,
                decoration: const InputDecoration(labelText: "Country code"),
              ),
              TextField(
                controller: _emailCtrl,
                decoration: const InputDecoration(labelText: "Email"),
              ),
              TextField(
                controller: _passCtrl,
                obscureText: _obscure,
                decoration: InputDecoration(
                  labelText: "Password",
                  suffixIcon: IconButton(
                    onPressed: () => setState(() => _obscure = !_obscure),
                    icon: Icon(_obscure ? Icons.visibility : Icons.visibility_off),
                  ),
                ),
              ),
              if (_registerMode) ...[
                const SizedBox(height: 8),
                TextField(
                  controller: _codeCtrl,
                  decoration: const InputDecoration(labelText: "Verification code"),
                ),
              ],
              const SizedBox(height: 16),
              Row(
                children: [
                  Expanded(
                    child: ElevatedButton(
                      onPressed: _busy
                          ? null
                          : () => _run(() async {
                                if (_registerMode) {
                                  // Send code first if needed
                                  if (_code.isEmpty) {
                                    await TuyaPlatform.sendEmailCode(
                                      countryCode: _country,
                                      email: _email,
                                      type: 1,
                                    );
                                    if (!mounted) return;
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      const SnackBar(content: Text("Code sent. Enter it then register.")),
                                    );
                                    return;
                                  }

                                  await TuyaPlatform.registerEmail(
                                    countryCode: _country,
                                    email: _email,
                                    password: _pass,
                                    code: _code,
                                  );

                                  // After register, login
                                  await TuyaPlatform.loginByEmail(
                                    countryCode: _country,
                                    email: _email,
                                    password: _pass,
                                  );
                                } else {
                                  await TuyaPlatform.loginByEmail(
                                    countryCode: _country,
                                    email: _email,
                                    password: _pass,
                                  );
                                }

                                // Ensure home and set current home (CRITICAL for BizBundle QR token)
                                final home = await TuyaPlatform.ensureHome(
                                  name: "My Home",
                                  geoName: "Oman",
                                  rooms: const ["Living Room"],
                                );
                                final homeId = (home is Map && home['homeId'] != null)
                                    ? (home['homeId'] as num).toInt()
                                    : 0;
                                await _setCurrentHome(homeId);

                                if (!mounted) return;
                                Navigator.pushReplacement(
                                  context,
                                  MaterialPageRoute(builder: (_) => const HomeHubPage()),
                                );
                              }),
                      child: Text(btn),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  TextButton(
                    onPressed: _busy ? null : () => setState(() => _registerMode = !_registerMode),
                    child: Text(_registerMode ? "Have an account? Login" : "No account? Register"),
                  ),
                  const Spacer(),
                  TextButton(
                    onPressed: _busy
                        ? null
                        : () => _run(() async {
                              await TuyaPlatform.logout();
                              if (!mounted) return;
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(content: Text("Logged out")),
                              );
                            }),
                    child: const Text("Logout"),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}