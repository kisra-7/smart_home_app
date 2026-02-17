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
  final _homeNameCtrl = TextEditingController(text: "My Home");

  bool _busy = false;
  bool _loggedIn = false;

  int? _homeId;
  final _gwDevIdCtrl = TextEditingController();

  Future<void> _run(Future<void> Function() fn) async {
    if (!mounted) return;
    setState(() => _busy = true);
    try {
      await fn();
    } catch (e) {
      debugPrint("❌ TuyaTest error: $e");
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text("Error: $e")),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _refreshLogin() => _run(() async {
        final ok = await TuyaPlatform.isLoggedIn();
        if (!mounted) return;
        setState(() => _loggedIn = ok);
      });

  Future<void> _login() => _run(() async {
        await TuyaPlatform.loginByEmail(
          countryCode: _countryCtrl.text.trim(),
          email: _emailCtrl.text.trim(),
          password: _passCtrl.text,
        );
        await _refreshLogin();
      });

  Future<void> _logout() => _run(() async {
        await TuyaPlatform.logout();
        _homeId = null;
        _gwDevIdCtrl.clear();
        await _refreshLogin();
      });

  Future<void> _ensureHome() => _run(() async {
        final info = await TuyaPlatform.ensureHome(name: _homeNameCtrl.text.trim());
        final hid = (info["homeId"] as num?)?.toInt() ?? 0;
        if (hid <= 0) throw Exception("ensureHome returned invalid homeId");
        setState(() => _homeId = hid);
      });

  Future<void> _pairGateway() => _run(() async {
        final hid = _homeId;
        if (hid == null || hid <= 0) throw Exception("No homeId. Tap Ensure Home first.");
        await TuyaPlatform.startZigbeeGatewayPairing(homeId: hid);
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text("Gateway pairing started.")),
        );
      });

  Future<void> _pairZigbee() => _run(() async {
        final gw = _gwDevIdCtrl.text.trim();
        if (gw.isEmpty) throw Exception("Enter gateway devId first.");
        await TuyaPlatform.startZigbeeSubDevicePairing(gwDevId: gw);
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text("Zigbee pairing started.")),
        );
      });

  @override
  void initState() {
    super.initState();
    _refreshLogin();
    TuyaPlatform.setEventHandler((call) async {
      if (!mounted) return null;

      if (call.method == "tuya_gw_success" && call.arguments is Map) {
        final map = Map<String, dynamic>.from(call.arguments as Map);
        final devId = (map["devId"] ?? "").toString();
        if (devId.isNotEmpty) _gwDevIdCtrl.text = devId;

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("Gateway added ✅ devId: $devId")),
        );
      }

      if (call.method == "tuya_sub_success" && call.arguments is Map) {
        final map = Map<String, dynamic>.from(call.arguments as Map);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("Zigbee device added ✅ ${map["name"] ?? ""}")),
        );
      }

      if (call.method == "tuya_gw_error" && call.arguments is Map) {
        final map = Map<String, dynamic>.from(call.arguments as Map);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("Gateway error: ${map["msg"]}")),
        );
      }

      if (call.method == "tuya_sub_error" && call.arguments is Map) {
        final map = Map<String, dynamic>.from(call.arguments as Map);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("Zigbee error: ${map["msg"]}")),
        );
      }

      return null;
    });
  }

  @override
  void dispose() {
    _countryCtrl.dispose();
    _emailCtrl.dispose();
    _passCtrl.dispose();
    _homeNameCtrl.dispose();
    _gwDevIdCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final status = _loggedIn ? "LOGGED IN" : "LOGGED OUT";

    return Scaffold(
      backgroundColor: const Color(0xFFF4F6FA),
      appBar: AppBar(
        title: const Text("Tuya Test"),
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
                Icon(Icons.circle, size: 12, color: _loggedIn ? Colors.green : Colors.orange),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    status,
                    style: const TextStyle(fontWeight: FontWeight.w900),
                  ),
                ),
                if (_busy)
                  const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)),
              ],
            ),
          ),
          const SizedBox(height: 12),

          _card(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text("Auth", style: TextStyle(fontWeight: FontWeight.w900, fontSize: 16)),
                const SizedBox(height: 12),
                TextField(
                  controller: _countryCtrl,
                  decoration: const InputDecoration(border: OutlineInputBorder(), labelText: "Country Code"),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _emailCtrl,
                  decoration: const InputDecoration(border: OutlineInputBorder(), labelText: "Email"),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _passCtrl,
                  obscureText: true,
                  decoration: const InputDecoration(border: OutlineInputBorder(), labelText: "Password"),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: ElevatedButton(
                        onPressed: _busy ? null : _login,
                        child: const Text("Login"),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: OutlinedButton(
                        onPressed: _busy ? null : _logout,
                        child: const Text("Logout"),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),

          const SizedBox(height: 12),

          _card(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text("Home", style: TextStyle(fontWeight: FontWeight.w900, fontSize: 16)),
                const SizedBox(height: 12),
                TextField(
                  controller: _homeNameCtrl,
                  decoration: const InputDecoration(border: OutlineInputBorder(), labelText: "Home Name"),
                ),
                const SizedBox(height: 10),
                SizedBox(
                  width: double.infinity,
                  height: 46,
                  child: ElevatedButton(
                    onPressed: _busy ? null : _ensureHome,
                    child: Text(_homeId == null ? "Ensure Home" : "Home Ready • homeId=$_homeId"),
                  ),
                ),
              ],
            ),
          ),

          const SizedBox(height: 12),

          _card(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text("Pairing", style: TextStyle(fontWeight: FontWeight.w900, fontSize: 16)),
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  height: 46,
                  child: ElevatedButton.icon(
                    onPressed: _busy ? null : _pairGateway,
                    icon: const Icon(Icons.hub),
                    label: const Text("Pair Gateway"),
                  ),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _gwDevIdCtrl,
                  decoration: const InputDecoration(
                    border: OutlineInputBorder(),
                    labelText: "Gateway devId (auto-filled on success)",
                  ),
                ),
                const SizedBox(height: 10),
                SizedBox(
                  width: double.infinity,
                  height: 46,
                  child: ElevatedButton.icon(
                    onPressed: _busy ? null : _pairZigbee,
                    icon: const Icon(Icons.sensors),
                    label: const Text("Pair Zigbee Device"),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
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
            blurRadius: 14,
            offset: Offset(0, 6),
            color: Color(0x11000000),
          ),
        ],
      ),
      child: child,
    );
  }
}
