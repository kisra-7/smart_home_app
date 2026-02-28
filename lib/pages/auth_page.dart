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
  bool _registerMode = false;
  bool _obscure = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _autoRouteIfLoggedIn();
  }

  @override
  void dispose() {
    _countryCtrl.dispose();
    _emailCtrl.dispose();
    _passCtrl.dispose();
    _codeCtrl.dispose();
    super.dispose();
  }

  Future<void> _autoRouteIfLoggedIn() async {
    try {
      final loggedIn = await TuyaPlatform.isLoggedIn();
      if (!mounted) return;

      if (loggedIn) {
        await _goHome();
      }
    } catch (_) {
      // ignore auto route errors
    }
  }

  Future<void> _goHome() async {
    // Make sure homes load and biz context is set for the selected home.
    await ref.read(homeHubControllerProvider.notifier).loadHomes(autoPickFirst: true);

    // If no homes exist, create one and reload
    final st = ref.read(homeHubControllerProvider).value;
    if (st == null || st.homes.isEmpty) {
      await ref.read(homeHubControllerProvider.notifier).ensureHomeIfNeeded();
      await ref.read(homeHubControllerProvider.notifier).loadHomes(autoPickFirst: true);
    }

    if (!mounted) return;
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(builder: (_) => const HomeHubPage()),
    );
  }

  Future<void> _login() async {
    setState(() {
      _busy = true;
      _error = null;
    });

    try {
      final ok = await TuyaPlatform.loginByEmail(
        countryCode: _countryCtrl.text.trim(),
        email: _emailCtrl.text.trim(),
        password: _passCtrl.text,
      );

      if (!mounted) return;

      if (!ok) {
        setState(() => _error = "Login failed (unknown).");
      } else {
        await _goHome();
      }
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _sendCode() async {
    setState(() {
      _busy = true;
      _error = null;
    });

    try {
      await TuyaPlatform.sendEmailCode(
        countryCode: _countryCtrl.text.trim(),
        email: _emailCtrl.text.trim(),
        type: 1,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("Verification code sent.")),
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _register() async {
    setState(() {
      _busy = true;
      _error = null;
    });

    try {
      final ok = await TuyaPlatform.registerEmail(
        countryCode: _countryCtrl.text.trim(),
        email: _emailCtrl.text.trim(),
        password: _passCtrl.text,
        code: _codeCtrl.text.trim(),
      );

      if (!mounted) return;

      if (!ok) {
        setState(() => _error = "Register failed (unknown).");
      } else {
        // After register, login immediately using same credentials
        await _login();
      }
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final title = _registerMode ? "Create Account" : "Sign In";

    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: GestureDetector(
        onTap: () => FocusScope.of(context).unfocus(),
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            if (_error != null) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(),
                ),
                child: Text(_error!),
              ),
              const SizedBox(height: 12),
            ],

            TextField(
              controller: _countryCtrl,
              decoration: const InputDecoration(labelText: "Country Code"),
              keyboardType: TextInputType.number,
              enabled: !_busy,
            ),
            const SizedBox(height: 12),

            TextField(
              controller: _emailCtrl,
              decoration: const InputDecoration(labelText: "Email"),
              keyboardType: TextInputType.emailAddress,
              enabled: !_busy,
            ),
            const SizedBox(height: 12),

            TextField(
              controller: _passCtrl,
              decoration: InputDecoration(
                labelText: "Password",
                suffixIcon: IconButton(
                  onPressed: _busy ? null : () => setState(() => _obscure = !_obscure),
                  icon: Icon(_obscure ? Icons.visibility : Icons.visibility_off),
                ),
              ),
              obscureText: _obscure,
              enabled: !_busy,
            ),
            const SizedBox(height: 12),

            if (_registerMode) ...[
              TextField(
                controller: _codeCtrl,
                decoration: const InputDecoration(labelText: "Verification Code"),
                enabled: !_busy,
              ),
              const SizedBox(height: 12),

              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: _busy ? null : _sendCode,
                      child: const Text("Send Code"),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: ElevatedButton(
                      onPressed: _busy ? null : _register,
                      child: _busy
                          ? const SizedBox(height: 18, width: 18, child: CircularProgressIndicator())
                          : const Text("Register"),
                    ),
                  ),
                ],
              ),
            ] else ...[
              ElevatedButton(
                onPressed: _busy ? null : _login,
                child: _busy
                    ? const SizedBox(height: 18, width: 18, child: CircularProgressIndicator())
                    : const Text("Sign In"),
              ),
            ],

            const SizedBox(height: 12),

            TextButton(
              onPressed: _busy ? null : () => setState(() => _registerMode = !_registerMode),
              child: Text(_registerMode ? "I already have an account" : "Create a new account"),
            ),
          ],
        ),
      ),
    );
  }
}