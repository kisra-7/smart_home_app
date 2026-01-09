import 'package:alrawi_app/tuya/tuya_platform.dart';
import 'package:flutter/material.dart';

class TuyaTestPage extends StatefulWidget {
  const TuyaTestPage({super.key});

  @override
  State<TuyaTestPage> createState() => _TuyaTestPageState();
}

class _TuyaTestPageState extends State<TuyaTestPage> {
  final _countryCtrl = TextEditingController(text: "968"); // Oman default
  final _emailCtrl = TextEditingController();
  final _passCtrl = TextEditingController();

  bool _sdkReady = false;
  bool _loggedIn = false;

  bool _busyInit = false;
  bool _busyRegister = false;
  bool _busyLogin = false;
  bool _busyLogout = false;
  bool _busyHomes = false;

  List<Map<String, dynamic>> _homes = [];

  final List<String> _logs = [];

  void _log(String msg) {
    final now = TimeOfDay.now();
    final line =
        "[${now.hour.toString().padLeft(2, '0')}:${now.minute.toString().padLeft(2, '0')}] $msg";
    setState(() => _logs.insert(0, line));
  }

  String get _countryCode => _countryCtrl.text.trim();
  String get _email => _emailCtrl.text.trim();
  String get _password => _passCtrl.text;

  bool get _canRegister =>
      _sdkReady &&
      !_busyRegister &&
      _countryCode.isNotEmpty &&
      _email.isNotEmpty &&
      _password.isNotEmpty;

  bool get _canLogin =>
      _sdkReady &&
      !_busyLogin &&
      _countryCode.isNotEmpty &&
      _email.isNotEmpty &&
      _password.isNotEmpty;

  bool get _canLogout => _sdkReady && _loggedIn && !_busyLogout;
  bool get _canGetHomes => _sdkReady && _loggedIn && !_busyHomes;

  Future<void> _refreshLoginStatus() async {
    try {
      final status = await TuyaPlatform.isLoggedIn();
      setState(() => _loggedIn = status);
      _log("Login status updated: ${status ? "LOGGED IN" : "LOGGED OUT"}");
    } catch (e) {
      _log("Login status check error: $e");
    }
  }

  Future<void> _initSdk() async {
    setState(() => _busyInit = true);
    _log("Init SDK started");
    try {
      await TuyaPlatform.initSdk();
      setState(() => _sdkReady = true);
      _log("✅ Init SDK success");
      await _refreshLoginStatus();
    } catch (e) {
      _log("❌ Init SDK error: $e");
      setState(() => _sdkReady = false);
    } finally {
      setState(() => _busyInit = false);
    }
  }

  Future<void> _register() async {
    setState(() => _busyRegister = true);
    _log("Register started");
    try {
      await TuyaPlatform.registerEmail(
        countryCode: _countryCode,
        email: _email,
        password: _password,
      );
      _log("✅ Register success");
    } catch (e) {
      _log("❌ Register error: $e");
    } finally {
      setState(() => _busyRegister = false);
    }
  }

  Future<void> _login() async {
    setState(() => _busyLogin = true);
    _log("Login started");

    try {
      await TuyaPlatform.loginEmail(
        countryCode: _countryCode,
        email: _email,
        password: _password,
      );

      // ✅ IMPORTANT:
      // Even if native login state updates slightly later,
      // we enable the UI immediately after success.
      setState(() => _loggedIn = true);

      _log("✅ Login success");

      // Re-check shortly after so UI matches native state.
      await Future.delayed(const Duration(milliseconds: 400));
      await _refreshLoginStatus();
    } catch (e) {
      _log("❌ Login error: $e");
      await _refreshLoginStatus();
    } finally {
      setState(() => _busyLogin = false);
    }
  }

  Future<void> _logout() async {
    setState(() => _busyLogout = true);
    _log("Logout started");
    try {
      await TuyaPlatform.logout();
      setState(() {
        _loggedIn = false;
        _homes = [];
      });
      _log("✅ Logout success");
      await _refreshLoginStatus();
    } catch (e) {
      _log("❌ Logout error: $e");
    } finally {
      setState(() => _busyLogout = false);
    }
  }

  Future<void> _getHomes() async {
    setState(() => _busyHomes = true);
    _log("Get homes started");
    try {
      final homes = await TuyaPlatform.getHomeList();
      setState(() => _homes = homes);
      _log("✅ Homes loaded: ${homes.length}");
    } catch (e) {
      _log("❌ Get homes error: $e");
    } finally {
      setState(() => _busyHomes = false);
    }
  }

  @override
  void dispose() {
    _countryCtrl.dispose();
    _emailCtrl.dispose();
    _passCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final statusColor = _sdkReady
        ? (_loggedIn ? Colors.green : Colors.orange)
        : Colors.red;

    final statusText = !_sdkReady
        ? "SDK NOT READY"
        : (_loggedIn ? "LOGGED IN" : "LOGGED OUT");

    return Scaffold(
      appBar: AppBar(title: const Text("Tuya Test Console"), centerTitle: true),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // Status Card
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: statusColor.withOpacity(0.4)),
                color: statusColor.withOpacity(0.08),
              ),
              child: Row(
                children: [
                  Icon(Icons.circle, color: statusColor, size: 14),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      statusText,
                      style: TextStyle(
                        fontWeight: FontWeight.w700,
                        color: statusColor,
                      ),
                    ),
                  ),
                  ElevatedButton.icon(
                    onPressed: _busyInit ? null : _initSdk,
                    icon: _busyInit
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.power),
                    label: const Text("Init SDK"),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 16),

            // Inputs
            TextField(
              controller: _countryCtrl,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: "Country Code",
                hintText: "Example: 968 (Oman), 971 (UAE), 966 (SA)",
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _emailCtrl,
              keyboardType: TextInputType.emailAddress,
              decoration: const InputDecoration(
                labelText: "Email",
                hintText: "example@email.com",
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _passCtrl,
              obscureText: true,
              decoration: const InputDecoration(
                labelText: "Password",
                border: OutlineInputBorder(),
              ),
            ),

            const SizedBox(height: 16),

            // Actions
            Row(
              children: [
                Expanded(
                  child: ElevatedButton(
                    onPressed: _canRegister ? _register : null,
                    child: _busyRegister
                        ? const SizedBox(
                            height: 18,
                            width: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Text("Register Email"),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: ElevatedButton(
                    onPressed: _canLogin ? _login : null,
                    child: _busyLogin
                        ? const SizedBox(
                            height: 18,
                            width: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Text("Login"),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),

            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: _canLogout ? _logout : null,
                    child: _busyLogout
                        ? const SizedBox(
                            height: 18,
                            width: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Text("Logout"),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: OutlinedButton(
                    onPressed: _canGetHomes ? _getHomes : null,
                    child: _busyHomes
                        ? const SizedBox(
                            height: 18,
                            width: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Text("Get Homes"),
                  ),
                ),
              ],
            ),

            const SizedBox(height: 16),

            // Homes
            if (_homes.isNotEmpty) ...[
              Text(
                "Homes (${_homes.length})",
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 8),
              ..._homes.map((h) {
                return Card(
                  child: ListTile(
                    leading: const Icon(Icons.home),
                    title: Text("${h["name"] ?? "Home"}"),
                    subtitle: Text("homeId: ${h["homeId"]}"),
                  ),
                );
              }),
              const SizedBox(height: 8),
            ],

            // Logs
            Text(
              "Logs",
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 8),
            Container(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: Colors.black12),
              ),
              child: ListView.separated(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: _logs.length,
                separatorBuilder: (_, __) => const Divider(height: 1),
                itemBuilder: (context, index) {
                  final line = _logs[index];
                  return Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 10,
                    ),
                    child: Text(
                      line,
                      style: const TextStyle(
                        fontFamily: "monospace",
                        fontSize: 12,
                      ),
                    ),
                  );
                },
              ),
            ),

            const SizedBox(height: 20),
          ],
        ),
      ),
    );
  }
}
