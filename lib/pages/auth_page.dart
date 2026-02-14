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
        await TuyaPlatform.ensureHome(
          name: "My Home",
          geoName: "Oman",
          rooms: const ["Living Room"],
        );
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

  Future<void> _afterLoginEnsureHomeAndGo() async {
    final info = await TuyaPlatform.ensureHome(
      name: "My Home",
      geoName: "Oman",
      rooms: const ["Living Room"],
    );

    final created = info["created"] == true;
    final homeId = info["homeId"];
    debugPrint("🏠 ensureHome => created=$created homeId=$homeId");

    if (!mounted) return;

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          created ? "Home created (ID: $homeId)" : "Home found (ID: $homeId)",
        ),
      ),
    );

    Navigator.pushReplacement(
      context,
      MaterialPageRoute(builder: (_) => const HomeHubPage()),
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
                      child: const Icon(
                        Icons.home_rounded,
                        color: Colors.white,
                      ),
                    ),
                    const SizedBox(width: 12),
                    const Expanded(
                      child: Text(
                        "Alrawi Smart Home",
                        style: TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.w900,
                        ),
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
                  style: const TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.w900,
                  ),
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
                            onPressed: _busy
                                ? null
                                : () => setState(() => _obscure = !_obscure),
                            icon: Icon(
                              _obscure
                                  ? Icons.visibility_off
                                  : Icons.visibility,
                            ),
                          ),
                        ),
                      ),
                      if (_registerMode) ...[
                        const SizedBox(height: 12),
                        TextField(
                          controller: _codeCtrl,
                          keyboardType: TextInputType.number,
                          decoration: const InputDecoration(
                            labelText: "Verification code",
                            border: OutlineInputBorder(),
                          ),
                        ),
                        const SizedBox(height: 12),
                        Row(
                          children: [
                            Expanded(
                              child: OutlinedButton(
                                onPressed:
                                    _busy || _country.isEmpty || _email.isEmpty
                                    ? null
                                    : () => _run(() async {
                                        await TuyaPlatform.sendEmailCode(
                                          countryCode: _country,
                                          email: _email,
                                          type: 1,
                                        );
                                        if (!mounted) return;
                                        ScaffoldMessenger.of(
                                          context,
                                        ).showSnackBar(
                                          const SnackBar(
                                            content: Text(
                                              "Code sent. Check your email.",
                                            ),
                                          ),
                                        );
                                      }),
                                child: const Text("Send code"),
                              ),
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: ElevatedButton(
                                onPressed:
                                    _busy ||
                                        _country.isEmpty ||
                                        _email.isEmpty ||
                                        _pass.isEmpty ||
                                        _code.isEmpty
                                    ? null
                                    : () => _run(() async {
                                        final ok =
                                            await TuyaPlatform.registerEmail(
                                              countryCode: _country,
                                              email: _email,
                                              password: _pass,
                                              code: _code,
                                            );
                                        if (!ok)
                                          throw Exception("Register failed");

                                        final logged =
                                            await TuyaPlatform.loginByEmail(
                                              countryCode: _country,
                                              email: _email,
                                              password: _pass,
                                            );
                                        if (!logged)
                                          throw Exception(
                                            "Login failed after register",
                                          );

                                        await _afterLoginEnsureHomeAndGo();
                                      }),
                                child: const Text("Register"),
                              ),
                            ),
                          ],
                        ),
                      ] else ...[
                        const SizedBox(height: 14),
                        SizedBox(
                          width: double.infinity,
                          height: 48,
                          child: ElevatedButton(
                            onPressed:
                                _busy ||
                                    _country.isEmpty ||
                                    _email.isEmpty ||
                                    _pass.isEmpty
                                ? null
                                : () => _run(() async {
                                    final ok = await TuyaPlatform.loginByEmail(
                                      countryCode: _country,
                                      email: _email,
                                      password: _pass,
                                    );
                                    if (!ok) throw Exception("Login failed");
                                    await _afterLoginEnsureHomeAndGo();
                                  }),
                            child: const Text("Sign in"),
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
                const SizedBox(height: 14),
                Center(
                  child: GestureDetector(
                    onTap: _busy
                        ? null
                        : () => setState(() => _registerMode = !_registerMode),
                    child: Text(
                      _registerMode
                          ? "Already have an account? Sign in"
                          : "Don't have an account? Create one",
                      style: const TextStyle(fontWeight: FontWeight.w800),
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

  Widget _card({required Widget child}) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: Colors.black12),
        boxShadow: const [
          BoxShadow(
            blurRadius: 16,
            offset: Offset(0, 8),
            color: Color(0x11000000),
          ),
        ],
      ),
      child: child,
    );
  }
}
