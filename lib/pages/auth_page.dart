// lib/pages/auth_page.dart
//
// A clean "Smart Life-ish" auth screen that supports:
// - Login by email
// - Register by email (send code -> register)
// - Auto-check logged-in status
// - Navigate to HomePage after login
//
// Uses: TuyaPlatform (MethodChannel -> Android Tuya SDK)

import 'package:alrawi_app/pages/home_page.dart';
import 'package:alrawi_app/tuya/tuya_platform.dart';
import 'package:flutter/material.dart';

class AuthPage extends StatefulWidget {
  const AuthPage({super.key});

  @override
  State<AuthPage> createState() => _AuthPageState();
}

class _AuthPageState extends State<AuthPage>
    with SingleTickerProviderStateMixin {
  final _countryCtrl = TextEditingController(text: "968");
  final _emailCtrl = TextEditingController();
  final _passCtrl = TextEditingController();
  final _codeCtrl = TextEditingController();

  bool _busy = false;
  bool _obscure = true;
  bool _isRegisterMode = false;

  final List<String> _logs = [];

  void _log(String msg) {
    final now = TimeOfDay.now();
    final line =
        "[${now.hour.toString().padLeft(2, '0')}:${now.minute.toString().padLeft(2, '0')}] $msg";
    if (!mounted) return;
    setState(() => _logs.insert(0, line));
  }

  String get _countryCode => _countryCtrl.text.trim();
  String get _email => _emailCtrl.text.trim();
  String get _password => _passCtrl.text;
  String get _code => _codeCtrl.text.trim();

  bool get _canLogin =>
      !_busy &&
      _countryCode.isNotEmpty &&
      _email.isNotEmpty &&
      _password.isNotEmpty;

  bool get _canSendCode =>
      !_busy && _countryCode.isNotEmpty && _email.isNotEmpty;

  bool get _canRegister =>
      !_busy &&
      _countryCode.isNotEmpty &&
      _email.isNotEmpty &&
      _password.isNotEmpty &&
      _code.isNotEmpty;

  Future<void> _run(Future<void> Function() action) async {
    if (!mounted) return;
    setState(() => _busy = true);
    FocusScope.of(context).unfocus();
    try {
      await action();
    } catch (e) {
      _log("❌ Error: $e");
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text("Error: $e")));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _checkAutoLogin() async {
    try {
      final loggedIn = await TuyaPlatform.isLoggedIn();
      _log("Auto check: ${loggedIn ? "LOGGED IN" : "LOGGED OUT"}");
      if (!mounted) return;
      if (loggedIn) {
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(builder: (_) => const HomePage()),
        );
      }
    } catch (e) {
      _log("Auto check error: $e");
    }
  }

  Future<void> _login() => _run(() async {
    _log("Login started...");
    await TuyaPlatform.loginByEmail(
      countryCode: _countryCode,
      email: _email,
      password: _password,
    );
    _log("✅ Login success");
    if (!mounted) return;
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(builder: (_) => const HomePage()),
    );
  });

  Future<void> _sendCode() => _run(() async {
    _log("Sending verification code...");
    await TuyaPlatform.sendEmailCode(
      countryCode: _countryCode,
      email: _email,
      type: 1, // register
    );
    _log("✅ Code sent (check your email)");
  });

  Future<void> _register() => _run(() async {
    _log("Register started...");
    await TuyaPlatform.registerEmail(
      countryCode: _countryCode,
      email: _email,
      password: _password,
      code: _code,
    );
    _log("✅ Register success - logging in...");
    await TuyaPlatform.loginByEmail(
      countryCode: _countryCode,
      email: _email,
      password: _password,
    );
    if (!mounted) return;
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(builder: (_) => const HomePage()),
    );
  });

  @override
  void initState() {
    super.initState();
    // SDK is initialized in main(), so we just check login state.
    WidgetsBinding.instance.addPostFrameCallback((_) => _checkAutoLogin());
  }

  @override
  void dispose() {
    _countryCtrl.dispose();
    _emailCtrl.dispose();
    _passCtrl.dispose();
    _codeCtrl.dispose();
    super.dispose();
  }

  Widget _card({required Widget child}) {
    return Container(
      padding: const EdgeInsets.all(14),
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: Colors.black12),
        color: Colors.white,
        boxShadow: const [
          BoxShadow(
            blurRadius: 14,
            offset: Offset(0, 6),
            color: Color(0x11000000),
          ),
        ],
      ),
      child: child,
    );
  }

  InputDecoration _dec(String label, {String? hint, Widget? suffix}) {
    return InputDecoration(
      labelText: label,
      hintText: hint,
      border: const OutlineInputBorder(),
      suffixIcon: suffix,
    );
  }

  @override
  Widget build(BuildContext context) {
    final title = _isRegisterMode ? "Create account" : "Sign in";
    final subtitle = _isRegisterMode
        ? "Register with email verification code"
        : "Sign in with your Tuya account";

    return Scaffold(
      backgroundColor: const Color(0xFFF6F7FB),
      appBar: AppBar(
        title: const Text("Alrawi Smart Home"),
        centerTitle: true,
        backgroundColor: Colors.white,
        foregroundColor: Colors.black,
        elevation: 0,
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _card(
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    title,
                    style: const TextStyle(
                      fontSize: 18,
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
          ),

          _card(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(subtitle, style: const TextStyle(color: Colors.black54)),
                const SizedBox(height: 12),

                TextField(
                  controller: _countryCtrl,
                  keyboardType: TextInputType.number,
                  decoration: _dec("Country Code", hint: "968"),
                ),
                const SizedBox(height: 10),

                TextField(
                  controller: _emailCtrl,
                  keyboardType: TextInputType.emailAddress,
                  decoration: _dec("Email", hint: "name@example.com"),
                ),
                const SizedBox(height: 10),

                TextField(
                  controller: _passCtrl,
                  obscureText: _obscure,
                  decoration: _dec(
                    "Password",
                    suffix: IconButton(
                      onPressed: () => setState(() => _obscure = !_obscure),
                      icon: Icon(
                        _obscure ? Icons.visibility_off : Icons.visibility,
                      ),
                    ),
                  ),
                ),

                if (_isRegisterMode) ...[
                  const SizedBox(height: 10),
                  TextField(
                    controller: _codeCtrl,
                    keyboardType: TextInputType.number,
                    decoration: _dec(
                      "Verification Code",
                      hint: "Enter code from email",
                    ),
                  ),
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: _canSendCode ? _sendCode : null,
                          icon: const Icon(Icons.mark_email_read_outlined),
                          label: const Text("Send Code"),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: ElevatedButton.icon(
                          onPressed: _canRegister ? _register : null,
                          icon: const Icon(Icons.person_add_alt_1),
                          label: const Text("Register"),
                        ),
                      ),
                    ],
                  ),
                ] else ...[
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
                      onPressed: _canLogin ? _login : null,
                      icon: const Icon(Icons.login),
                      label: const Text("Login"),
                    ),
                  ),
                ],

                const SizedBox(height: 12),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(
                      _isRegisterMode
                          ? "Already have an account? "
                          : "Don't have an account? ",
                    ),
                    GestureDetector(
                      onTap: _busy
                          ? null
                          : () => setState(
                              () => _isRegisterMode = !_isRegisterMode,
                            ),
                      child: Text(
                        _isRegisterMode ? "Sign in" : "Create one",
                        style: const TextStyle(fontWeight: FontWeight.w900),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),

          _card(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  "Logs",
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.w900),
                ),
                const SizedBox(height: 10),
                if (_logs.isEmpty)
                  const Text("No logs yet.")
                else
                  ListView.separated(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: _logs.length,
                    separatorBuilder: (_, __) => const Divider(height: 1),
                    itemBuilder: (context, i) => Padding(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      child: Text(
                        _logs[i],
                        style: const TextStyle(
                          fontFamily: "monospace",
                          fontSize: 12,
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
