import 'package:flutter/material.dart';
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

  Future<void> _autoCheck() async {
    try {
      final loggedIn = await TuyaPlatform.isLoggedIn();
      if (!mounted) return;

      if (loggedIn) {
        await TuyaPlatform.ensureHome(
          name: "My Home",
          geoName: "Oman",
          rooms: const ["Living Room"],
        );
        if (!mounted) return;

        _goHome();
      }
    } catch (e) {
      debugPrint("❌ Auto check error: $e");
    }
  }

  void _goHome() {
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(builder: (_) => const HomeHubPage()),
    );
  }

  Future<void> _run(Future<void> Function() fn) async {
    if (_busy) return;
    FocusScope.of(context).unfocus();
    setState(() => _busy = true);
    try {
      await fn();
    } catch (e) {
      debugPrint("❌ Auth error: $e");
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("Error: $e")),
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _afterAuthEnsureHomeAndGo() async {
    final info = await TuyaPlatform.ensureHome(
      name: "My Home",
      geoName: "Oman",
      rooms: const ["Living Room"],
    );

    final homeId = (info["homeId"] as num?)?.toInt();
    debugPrint("🏠 ensureHome => homeId=$homeId");

    if (!mounted) return;
    _goHome();
  }

  Future<void> _login() async {
    if (_country.isEmpty || _email.isEmpty || _pass.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("Please fill country, email, and password.")),
      );
      return;
    }

    await _run(() async {
      // IMPORTANT: your TuyaPlatform.loginByEmail returns Future<void>
      // so we just await it; if it doesn't throw, we treat it as success.
      await TuyaPlatform.loginByEmail(
        countryCode: _country,
        email: _email,
        password: _pass,
      );

      await _afterAuthEnsureHomeAndGo();
    });
  }

  Future<void> _sendCode() async {
    if (_country.isEmpty || _email.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("Please fill country and email.")),
      );
      return;
    }

    await _run(() async {
      await TuyaPlatform.sendEmailCode(
        countryCode: _country,
        email: _email,
        type: 1,
      );

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("Verification code sent ✅")),
      );
    });
  }

  Future<void> _register() async {
    if (_country.isEmpty || _email.isEmpty || _pass.isEmpty || _code.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("Please fill all fields (including code).")),
      );
      return;
    }

    await _run(() async {
      // IMPORTANT: your TuyaPlatform.registerEmail returns Future<void>
      await TuyaPlatform.registerEmail(
        countryCode: _country,
        email: _email,
        password: _pass,
        code: _code,
      );

      // After registering, we login (some SDKs auto-login, but this is safe)
      await TuyaPlatform.loginByEmail(
        countryCode: _country,
        email: _email,
        password: _pass,
      );

      await _afterAuthEnsureHomeAndGo();
    });
  }

  Widget _card({required Widget child}) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.black12),
        boxShadow: const [
          BoxShadow(
            blurRadius: 18,
            color: Color(0x11000000),
            offset: Offset(0, 10),
          ),
        ],
      ),
      child: child,
    );
  }

  @override
  Widget build(BuildContext context) {
    final title = _registerMode ? "Create account" : "Sign in";

    return Scaffold(
      backgroundColor: const Color(0xFFF5F7FB),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: ListView(
              padding: const EdgeInsets.all(20),
              children: [
                const SizedBox(height: 16),
                Row(
                  children: [
                    Container(
                      width: 44,
                      height: 44,
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(12),
                        color: const Color(0xFF0B84FF),
                      ),
                      child: const Icon(Icons.home_rounded, color: Colors.white),
                    ),
                    const SizedBox(width: 12),
                    const Expanded(
                      child: Text(
                        "Alrawi Smart Home",
                        style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900),
                      ),
                    ),
                    if (_busy)
                      const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      ),
                  ],
                ),
                const SizedBox(height: 28),
                Text(
                  title,
                  style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w900),
                ),
                const SizedBox(height: 6),
                Text(
                  _registerMode
                      ? "Register using email verification code"
                      : "Login with your Tuya account email",
                  style: const TextStyle(color: Colors.black54),
                ),
                const SizedBox(height: 18),

                _card(
                  child: Column(
                    children: [
                      TextField(
                        controller: _countryCtrl,
                        keyboardType: TextInputType.number,
                        decoration: const InputDecoration(
                          labelText: "Country code",
                          border: OutlineInputBorder(),
                        ),
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: _emailCtrl,
                        keyboardType: TextInputType.emailAddress,
                        decoration: const InputDecoration(
                          labelText: "Email",
                          border: OutlineInputBorder(),
                        ),
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: _passCtrl,
                        obscureText: _obscure,
                        decoration: InputDecoration(
                          labelText: "Password",
                          border: const OutlineInputBorder(),
                          suffixIcon: IconButton(
                            onPressed: _busy ? null : () => setState(() => _obscure = !_obscure),
                            icon: Icon(_obscure ? Icons.visibility : Icons.visibility_off),
                          ),
                        ),
                      ),

                      if (_registerMode) ...[
                        const SizedBox(height: 12),
                        Row(
                          children: [
                            Expanded(
                              child: TextField(
                                controller: _codeCtrl,
                                keyboardType: TextInputType.number,
                                decoration: const InputDecoration(
                                  labelText: "Email code",
                                  border: OutlineInputBorder(),
                                ),
                              ),
                            ),
                            const SizedBox(width: 10),
                            ElevatedButton(
                              onPressed: _busy ? null : _sendCode,
                              style: ElevatedButton.styleFrom(
                                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 16),
                                backgroundColor: const Color(0xFF0B84FF),
                                foregroundColor: Colors.white,
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(12),
                                ),
                              ),
                              child: const Text("Send"),
                            ),
                          ],
                        ),
                      ],

                      const SizedBox(height: 14),
                      SizedBox(
                        width: double.infinity,
                        child: ElevatedButton(
                          onPressed: _busy ? null : (_registerMode ? _register : _login),
                          style: ElevatedButton.styleFrom(
                            padding: const EdgeInsets.symmetric(vertical: 14),
                            backgroundColor: const Color(0xFF0B84FF),
                            foregroundColor: Colors.white,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                          child: Text(_registerMode ? "Create account" : "Sign in"),
                        ),
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 14),
                Center(
                  child: TextButton(
                    onPressed: _busy ? null : () => setState(() => _registerMode = !_registerMode),
                    child: Text(
                      _registerMode
                          ? "Already have an account? Sign in"
                          : "Don't have an account? Create one",
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}