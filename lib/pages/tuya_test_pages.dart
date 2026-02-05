import 'package:alrawi_app/tuya/tuya_platform.dart';
import 'package:flutter/material.dart';

class TuyaTestPage extends StatefulWidget {
  const TuyaTestPage({super.key});

  @override
  State<TuyaTestPage> createState() => _TuyaTestPageState();
}

class _TuyaTestPageState extends State<TuyaTestPage> {
  final _countryCtrl = TextEditingController(text: "968");
  final _emailCtrl = TextEditingController();
  final _passCtrl = TextEditingController();
  final _codeCtrl = TextEditingController();

  final _homeNameCtrl = TextEditingController(text: "My Home");
  final _geoNameCtrl = TextEditingController(text: "Oman");
  final _roomsCtrl = TextEditingController(text: "Living Room,Bedroom");

  bool _sdkReady = true; // init happens in main()
  bool _loggedIn = false;

  bool _busy = false;
  int? _selectedHomeId;
  String? _selectedHomeName;
  List<Map<String, dynamic>> _homes = [];
  final List<String> _logs = [];

  String get _countryCode => _countryCtrl.text.trim();
  String get _email => _emailCtrl.text.trim();
  String get _password => _passCtrl.text;
  String get _code => _codeCtrl.text.trim();

  void _log(String msg) {
    final now = TimeOfDay.now();
    final line =
        "[${now.hour.toString().padLeft(2, '0')}:${now.minute.toString().padLeft(2, '0')}] $msg";
    if (!mounted) return;
    setState(() => _logs.insert(0, line));
  }

  Future<void> _refreshLoginStatus() async {
    try {
      final status = await TuyaPlatform.isLoggedIn();
      if (!mounted) return;
      setState(() => _loggedIn = status);
      _log("Login status: ${status ? "LOGGED IN" : "LOGGED OUT"}");
    } catch (e) {
      _log("Login status error: $e");
    }
  }

  @override
  void initState() {
    super.initState();
    _refreshLoginStatus();
  }

  Future<void> _run(Future<void> Function() action) async {
    setState(() => _busy = true);
    try {
      await action();
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _sendEmailCode() => _run(() async {
    _log("Send code...");
    await TuyaPlatform.sendEmailCode(
      countryCode: _countryCode,
      email: _email,
      type: 1,
    );
    _log("✅ Code sent");
  });

  Future<void> _register() => _run(() async {
    _log("Register...");
    await TuyaPlatform.registerEmail(
      countryCode: _countryCode,
      email: _email,
      password: _password,
      code: _code,
    );
    _log("✅ Register success (now login)");
  });

  Future<void> _login() => _run(() async {
    _log("Login...");
    await TuyaPlatform.loginByEmail(
      countryCode: _countryCode,
      email: _email,
      password: _password,
    );
    _log("✅ Login success");
    await _refreshLoginStatus();
    await _loadHomes(autoSelectFirst: true);
  });

  Future<void> _logout() => _run(() async {
    _log("Logout...");
    await TuyaPlatform.logout();
    setState(() {
      _loggedIn = false;
      _homes = [];
      _selectedHomeId = null;
      _selectedHomeName = null;
    });
    _log("✅ Logout success");
    await _refreshLoginStatus();
  });

  Future<void> _loadHomes({bool autoSelectFirst = true}) => _run(() async {
    _log("Load homes...");
    final homes = await TuyaPlatform.getHomeList();
    setState(() => _homes = homes);
    _log("✅ Homes loaded: ${homes.length}");

    if (autoSelectFirst && homes.isNotEmpty) {
      final first = homes.first;
      setState(() {
        _selectedHomeId = (first["homeId"] as num).toInt();
        _selectedHomeName = (first["name"] ?? "Home").toString();
      });
      _log("Selected: $_selectedHomeName ($_selectedHomeId)");
    }
  });

  Future<void> _createHome() => _run(() async {
    _log("Create home...");
    final rooms = _roomsCtrl.text
        .split(",")
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toList();

    final created = await TuyaPlatform.createHome(
      name: _homeNameCtrl.text.trim(),
      geoName: _geoNameCtrl.text.trim(),
      rooms: rooms,
    );

    final newHomeId = (created["homeId"] as num).toInt();
    _log("✅ Home created: ${created["name"]} (ID: $newHomeId)");

    await _loadHomes(autoSelectFirst: false);

    setState(() {
      _selectedHomeId = newHomeId;
      _selectedHomeName = (created["name"] ?? "Home").toString();
    });
  });

  Future<void> _openAddDevice() => _run(() async {
    _log("Open Add Device (Smart Life UI)...");
    await TuyaPlatform.openAddGateway();
    _log("✅ Add Device UI opened");
  });

  Future<void> _openQrScan() => _run(() async {
    _log("Open QR scan...");
    await TuyaPlatform.openQrScan();
    _log("✅ QR scan opened");
  });

  Widget _card({required Widget child}) {
    return Container(
      padding: const EdgeInsets.all(14),
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
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

  @override
  void dispose() {
    _countryCtrl.dispose();
    _emailCtrl.dispose();
    _passCtrl.dispose();
    _codeCtrl.dispose();
    _homeNameCtrl.dispose();
    _geoNameCtrl.dispose();
    _roomsCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final statusColor = !_sdkReady
        ? Colors.red
        : (_loggedIn ? Colors.green : Colors.orange);
    final statusText = !_sdkReady
        ? "SDK NOT READY"
        : (_loggedIn ? "LOGGED IN" : "LOGGED OUT");

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
                Icon(Icons.circle, size: 12, color: statusColor),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    statusText,
                    style: TextStyle(
                      fontWeight: FontWeight.w900,
                      color: statusColor,
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
                const Text(
                  "Account",
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.w900),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _countryCtrl,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(
                    labelText: "Country Code",
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _emailCtrl,
                  keyboardType: TextInputType.emailAddress,
                  decoration: const InputDecoration(
                    labelText: "Email",
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _passCtrl,
                  obscureText: true,
                  decoration: const InputDecoration(
                    labelText: "Password",
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: ElevatedButton(
                        onPressed: (_busy || !_sdkReady) ? null : _login,
                        child: const Text("Login"),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: OutlinedButton(
                        onPressed: (_busy || !_loggedIn) ? null : _logout,
                        child: const Text("Logout"),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                const Divider(),
                const SizedBox(height: 10),
                const Text(
                  "Register (Email Code)",
                  style: TextStyle(fontSize: 14, fontWeight: FontWeight.w800),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _codeCtrl,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(
                    labelText: "Verification Code",
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: (_busy || !_sdkReady)
                            ? null
                            : _sendEmailCode,
                        child: const Text("Send Code"),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: ElevatedButton(
                        onPressed: (_busy || !_sdkReady) ? null : _register,
                        child: const Text("Register"),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),

          if (_loggedIn) ...[
            _card(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    "Homes",
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.w900),
                  ),
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: _busy
                              ? null
                              : () => _loadHomes(autoSelectFirst: false),
                          icon: const Icon(Icons.refresh),
                          label: const Text("Refresh"),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: ElevatedButton.icon(
                          onPressed: _busy ? null : _createHome,
                          icon: const Icon(Icons.add_home),
                          label: const Text("Create"),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  if (_homes.isNotEmpty)
                    DropdownButtonFormField<int>(
                      value: _selectedHomeId,
                      items: _homes
                          .map(
                            (h) => DropdownMenuItem<int>(
                              value: (h["homeId"] as num).toInt(),
                              child: Text("${h["name"]} (${h["homeId"]})"),
                            ),
                          )
                          .toList(),
                      onChanged: (v) {
                        if (v == null) return;
                        final match = _homes.firstWhere(
                          (h) => (h["homeId"] as num).toInt() == v,
                        );
                        setState(() {
                          _selectedHomeId = v;
                          _selectedHomeName = (match["name"] ?? "Home")
                              .toString();
                        });
                        _log("Selected: $_selectedHomeName ($_selectedHomeId)");
                      },
                      decoration: const InputDecoration(
                        labelText: "Selected Home",
                        border: OutlineInputBorder(),
                      ),
                    ),
                  const SizedBox(height: 10),
                  TextField(
                    controller: _homeNameCtrl,
                    decoration: const InputDecoration(
                      labelText: "Home Name",
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 10),
                  TextField(
                    controller: _geoNameCtrl,
                    decoration: const InputDecoration(
                      labelText: "City / Geo Name",
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 10),
                  TextField(
                    controller: _roomsCtrl,
                    decoration: const InputDecoration(
                      labelText: "Rooms (comma separated)",
                      border: OutlineInputBorder(),
                    ),
                  ),
                ],
              ),
            ),

            _card(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    "Add Devices (Smart Life UI)",
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.w900),
                  ),
                  const SizedBox(height: 10),
                  ElevatedButton.icon(
                    onPressed: _busy ? null : _openAddDevice,
                    icon: const Icon(Icons.add),
                    label: const Text("Open Add Device"),
                  ),
                  const SizedBox(height: 10),
                  OutlinedButton.icon(
                    onPressed: _busy ? null : _openQrScan,
                    icon: const Icon(Icons.qr_code_scanner),
                    label: const Text("Scan QR Code"),
                  ),
                  const SizedBox(height: 6),
                  const Text(
                    "Tip: The Add Device flow also supports Zigbee gateway + sub-devices like Smart Life.",
                    style: TextStyle(color: Colors.black54),
                  ),
                ],
              ),
            ),
          ],

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
