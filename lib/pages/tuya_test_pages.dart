import 'package:alrawi_app/tuya/tuya_platform.dart';
import 'package:flutter/material.dart';

class TuyaTestPage extends StatefulWidget {
  const TuyaTestPage({super.key});

  @override
  State<TuyaTestPage> createState() => _TuyaTestPageState();
}

class _TuyaTestPageState extends State<TuyaTestPage> {
  // Login / Register
  final _countryCtrl = TextEditingController(text: "968"); // Oman default
  final _emailCtrl = TextEditingController();
  final _passCtrl = TextEditingController();
  final _codeCtrl = TextEditingController(); // verification code for register

  // Create home
  final _homeNameCtrl = TextEditingController(text: "My Home");
  final _geoNameCtrl = TextEditingController(text: "Oman");
  final _roomsCtrl = TextEditingController(text: "Living Room,Bedroom");

  // Activator UI (BizBundle)
  final _gwIdCtrl = TextEditingController();

  bool _sdkReady = false;
  bool _loggedIn = false;

  bool _busyInit = false;
  bool _busySendCode = false;
  bool _busyRegister = false;
  bool _busyLogin = false;
  bool _busyLogout = false;

  bool _busyHomes = false;
  bool _busyCreateHome = false;
  bool _busyOpenGw = false;
  bool _busyOpenZigbee = false;
  bool _busyStop = false;

  int? _selectedHomeId;
  String? _selectedHomeName;

  List<Map<String, dynamic>> _homes = [];
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

  bool get _canInit => !_busyInit;

  bool get _canLogin =>
      _sdkReady &&
      !_busyLogin &&
      _countryCode.isNotEmpty &&
      _email.isNotEmpty &&
      _password.isNotEmpty;

  bool get _canLogout => _sdkReady && _loggedIn && !_busyLogout;

  bool get _canSendCode =>
      _sdkReady &&
      !_busySendCode &&
      _countryCode.isNotEmpty &&
      _email.isNotEmpty;

  bool get _canRegister =>
      _sdkReady &&
      !_busyRegister &&
      _countryCode.isNotEmpty &&
      _email.isNotEmpty &&
      _password.isNotEmpty &&
      _code.isNotEmpty;

  bool get _canLoadHomes => _sdkReady && _loggedIn && !_busyHomes;

  bool get _canCreateHome =>
      _sdkReady &&
      _loggedIn &&
      !_busyCreateHome &&
      _homeNameCtrl.text.trim().isNotEmpty;

  bool get _canOpenAddGateway =>
      _sdkReady && _loggedIn && !_busyOpenGw && _selectedHomeId != null;

  bool get _canOpenZigbee =>
      _sdkReady &&
      _loggedIn &&
      !_busyOpenZigbee &&
      _selectedHomeId != null &&
      _gwIdCtrl.text.trim().isNotEmpty;

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

  Future<void> _initSdk() async {
    setState(() => _busyInit = true);
    _log("Init SDK started");
    try {
      await TuyaPlatform.initSdk();
      if (!mounted) return;
      setState(() => _sdkReady = true);
      _log("✅ Init SDK success");
      await _refreshLoginStatus();
    } catch (e) {
      _log("❌ Init SDK error: $e");
      if (!mounted) return;
      setState(() => _sdkReady = false);
    } finally {
      if (!mounted) return;
      setState(() => _busyInit = false);
    }
  }

  Future<void> _sendEmailCode() async {
    setState(() => _busySendCode = true);
    _log("Send email code started");
    try {
      await TuyaPlatform.sendEmailCode(
        countryCode: _countryCode,
        email: _email,
        type: 1, // usually 1 = register
      );
      _log("✅ Code sent (check your email)");
    } catch (e) {
      _log("❌ Send code error: $e");
    } finally {
      if (!mounted) return;
      setState(() => _busySendCode = false);
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
        code: _code,
      );
      _log("✅ Register success (now try login)");
    } catch (e) {
      _log("❌ Register error: $e");
    } finally {
      if (!mounted) return;
      setState(() => _busyRegister = false);
    }
  }

  Future<void> _login() async {
    setState(() => _busyLogin = true);
    _log("Login started");
    try {
      // loginEmail() is a backward-compatible alias that calls loginByEmail internally
      await TuyaPlatform.loginEmail(
        countryCode: _countryCode,
        email: _email,
        password: _password,
      );
      _log("✅ Login success");
      await _refreshLoginStatus();
    } catch (e) {
      _log("❌ Login error: $e");
      await _refreshLoginStatus();
    } finally {
      if (!mounted) return;
      setState(() => _busyLogin = false);
    }
  }

  Future<void> _logout() async {
    setState(() => _busyLogout = true);
    _log("Logout started");
    try {
      await TuyaPlatform.logout();
      if (!mounted) return;
      setState(() {
        _loggedIn = false;
        _homes = [];
        _selectedHomeId = null;
        _selectedHomeName = null;
        _gwIdCtrl.clear();
      });
      _log("✅ Logout success");
      await _refreshLoginStatus();
    } catch (e) {
      _log("❌ Logout error: $e");
    } finally {
      if (!mounted) return;
      setState(() => _busyLogout = false);
    }
  }

  Future<void> _loadHomes({bool autoSelectFirst = true}) async {
    setState(() => _busyHomes = true);
    _log("Load homes started");
    try {
      final homes = await TuyaPlatform.getHomeList();
      if (!mounted) return;
      setState(() => _homes = homes);
      _log("✅ Homes loaded: ${homes.length}");

      if (autoSelectFirst && homes.isNotEmpty) {
        final first = homes.first;
        setState(() {
          _selectedHomeId = (first["homeId"] as num).toInt();
          _selectedHomeName = (first["name"] ?? "Home").toString();
        });
        _log("Selected home: $_selectedHomeName ($_selectedHomeId)");
      }
    } catch (e) {
      _log("❌ Load homes error: $e");
    } finally {
      if (!mounted) return;
      setState(() => _busyHomes = false);
    }
  }

  Future<void> _createHome() async {
    setState(() => _busyCreateHome = true);
    _log("Create home started");
    try {
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
      final newHomeName = (created["name"] ?? "Home").toString();

      _log("✅ Home created: $newHomeName (homeId=$newHomeId)");

      await _loadHomes(autoSelectFirst: false);

      final match = _homes.where(
        (h) => (h["homeId"] as num).toInt() == newHomeId,
      );
      if (!mounted) return;
      setState(() {
        _selectedHomeId = newHomeId;
        _selectedHomeName = match.isNotEmpty
            ? (match.first["name"] ?? "Home").toString()
            : newHomeName;
      });
    } catch (e) {
      _log("❌ Create home error: $e");
    } finally {
      if (!mounted) return;
      setState(() => _busyCreateHome = false);
    }
  }

  Future<void> _openAddGatewayUi() async {
    final homeId = _selectedHomeId;
    if (homeId == null) return;

    setState(() => _busyOpenGw = true);
    _log("Open Add Gateway UI started");

    try {
      await TuyaPlatform.openAddGateway(homeId: homeId);
      _log("✅ Add Gateway UI opened (follow Tuya UI steps)");
      _log("Tip: After gateway is added, copy its devId and paste it below.");
    } catch (e) {
      _log("❌ Open Add Gateway UI error: $e");
      _log(
        "If MissingPluginException: BizBundle not connected on Android side.",
      );
    } finally {
      if (!mounted) return;
      setState(() => _busyOpenGw = false);
    }
  }

  Future<void> _openZigbeeSubDeviceUi() async {
    final homeId = _selectedHomeId;
    if (homeId == null) return;

    final gwId = _gwIdCtrl.text.trim();
    if (gwId.isEmpty) return;

    setState(() => _busyOpenZigbee = true);
    _log("Open Zigbee pairing UI started (gwId=$gwId)");

    try {
      await TuyaPlatform.openAddZigbeeSubDevice(homeId: homeId, gwId: gwId);
      _log("✅ Zigbee pairing UI opened (put device in pairing mode)");
    } catch (e) {
      _log("❌ Open Zigbee UI error: $e");
      _log(
        "If MissingPluginException: BizBundle not connected on Android side.",
      );
    } finally {
      if (!mounted) return;
      setState(() => _busyOpenZigbee = false);
    }
  }

  Future<void> _stopActivator() async {
    setState(() => _busyStop = true);
    _log("Stop activator started");
    try {
      await TuyaPlatform.stopActivator();
      _log("✅ Stop requested (best effort)");
    } catch (e) {
      _log("❌ Stop activator error: $e");
    } finally {
      if (!mounted) return;
      setState(() => _busyStop = false);
    }
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

    _gwIdCtrl.dispose();
    super.dispose();
  }

  Widget _sectionTitle(String t) => Padding(
    padding: const EdgeInsets.only(top: 16, bottom: 8),
    child: Text(
      t,
      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
    ),
  );

  @override
  Widget build(BuildContext context) {
    final statusColor = !_sdkReady
        ? Colors.red
        : (_loggedIn ? Colors.green : Colors.orange);
    final statusText = !_sdkReady
        ? "SDK NOT READY"
        : (_loggedIn ? "LOGGED IN" : "LOGGED OUT");

    return Scaffold(
      appBar: AppBar(
        title: const Text("Tuya Console"),
        centerTitle: true,
        actions: [
          IconButton(
            onPressed: _busyHomes
                ? null
                : () => _loadHomes(autoSelectFirst: false),
            icon: const Icon(Icons.refresh),
            tooltip: "Refresh Homes",
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: statusColor.withOpacity(0.35)),
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
                      fontWeight: FontWeight.w800,
                      color: statusColor,
                    ),
                  ),
                ),
                ElevatedButton.icon(
                  onPressed: _canInit ? _initSdk : null,
                  icon: _busyInit
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.power),
                  label: const Text("Init"),
                ),
              ],
            ),
          ),

          _sectionTitle("1) Login"),
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
          const SizedBox(height: 10),
          Row(
            children: [
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
              const SizedBox(width: 10),
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
            ],
          ),

          _sectionTitle("1.5) Register (Email Code)"),
          TextField(
            controller: _codeCtrl,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(
              labelText: "Verification Code",
              hintText: "Paste the code you received by email",
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: _canSendCode ? _sendEmailCode : null,
                  child: _busySendCode
                      ? const SizedBox(
                          height: 18,
                          width: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text("Send Code"),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: ElevatedButton(
                  onPressed: _canRegister ? _register : null,
                  child: _busyRegister
                      ? const SizedBox(
                          height: 18,
                          width: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text("Register"),
                ),
              ),
            ],
          ),

          _sectionTitle("2) Homes"),
          OutlinedButton.icon(
            onPressed: _canLoadHomes ? _loadHomes : null,
            icon: _busyHomes
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.home_work),
            label: const Text("Load Homes"),
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
                  _selectedHomeName = (match["name"] ?? "Home").toString();
                });
                _log("Selected home: $_selectedHomeName ($_selectedHomeId)");
              },
              decoration: const InputDecoration(
                labelText: "Selected Home",
                border: OutlineInputBorder(),
              ),
            ),

          _sectionTitle("3) Create Home"),
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
              labelText: "City / Geo Name (optional)",
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
          const SizedBox(height: 10),
          ElevatedButton.icon(
            onPressed: _canCreateHome ? _createHome : null,
            icon: _busyCreateHome
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.add_home),
            label: const Text("Create Home"),
          ),

          _sectionTitle("4) Add Zigbee Gateway (Tuya Activator UI)"),
          Text(
            _selectedHomeId == null
                ? "Select a home first."
                : "Home: ${_selectedHomeName ?? "Home"} (ID: $_selectedHomeId)",
          ),
          const SizedBox(height: 10),
          ElevatedButton.icon(
            onPressed: _canOpenAddGateway ? _openAddGatewayUi : null,
            icon: _busyOpenGw
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.router),
            label: const Text("Open Add Gateway UI"),
          ),

          _sectionTitle("5) Add Zigbee Device (Sub-device scan)"),
          TextField(
            controller: _gwIdCtrl,
            decoration: const InputDecoration(
              labelText: "Gateway devId (gwId)",
              hintText: "Paste gateway devId after it is added",
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 10),
          ElevatedButton.icon(
            onPressed: _canOpenZigbee ? _openZigbeeSubDeviceUi : null,
            icon: _busyOpenZigbee
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.sensors),
            label: const Text("Open Zigbee Pairing UI"),
          ),
          const SizedBox(height: 10),
          OutlinedButton.icon(
            onPressed: (!_sdkReady || _busyStop) ? null : _stopActivator,
            icon: _busyStop
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.stop_circle_outlined),
            label: const Text("Stop (Back)"),
          ),

          _sectionTitle("Logs"),
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
              itemBuilder: (context, i) => Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 10,
                ),
                child: Text(
                  _logs[i],
                  style: const TextStyle(fontFamily: "monospace", fontSize: 12),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
