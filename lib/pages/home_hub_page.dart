import '../tuya/tuya_platform.dart';
import 'package:flutter/material.dart';
import 'auth_page.dart';

class HomeHubPage extends StatefulWidget {
  const HomeHubPage({super.key});

  @override
  State<HomeHubPage> createState() => _HomeHubPageState();
}

class _HomeHubPageState extends State<HomeHubPage> {
  bool _busy = false;
  List<Map<String, dynamic>> _homes = [];
  int? _homeId;
  String _homeName = "Home";

  Future<void> _run(Future<void> Function() fn) async {
    if (!mounted) return;
    setState(() => _busy = true);
    try {
      await fn();
    } catch (e) {
      debugPrint("❌ HomeHub error: $e");
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text("Error: $e")));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _loadHomes() => _run(() async {
    debugPrint("🏠 loading homes...");
    final homes = await TuyaPlatform.getHomeList();
    debugPrint("✅ homes: ${homes.length}");

    if (!mounted) return;
    setState(() {
      _homes = homes;
      if (_homes.isNotEmpty) {
        final first = _homes.first;
        _homeId = (first["homeId"] as num?)?.toInt();
        _homeName = (first["name"] ?? "Home").toString();
      }
    });
  });

  Future<void> _logout() => _run(() async {
    await TuyaPlatform.logout();
    if (!mounted) return;
    Navigator.pushAndRemoveUntil(
      context,
      MaterialPageRoute(builder: (_) => const AuthPage()),
      (_) => false,
    );
  });

  Future<void> _addDevice() => _run(() async {
    final hid = _homeId;
    if (hid == null || hid <= 0) {
      // Safety: if for any reason we have no home, create/ensure one.
      final info = await TuyaPlatform.ensureHome();
      final ensured = (info["homeId"] as num?)?.toInt() ?? 0;
      if (ensured <= 0) throw Exception("Failed to ensure home.");
      setState(() => _homeId = ensured);
      await TuyaPlatform.openAddGateway(homeId: ensured);
      return;
    }

    debugPrint("➕ open add device for homeId=$hid");
    await TuyaPlatform.openAddGateway(homeId: hid);
  });

  Future<void> _scanQr() => _run(() async {
    final hid = _homeId;
    if (hid == null || hid <= 0) {
      final info = await TuyaPlatform.ensureHome();
      final ensured = (info["homeId"] as num?)?.toInt() ?? 0;
      if (ensured <= 0) throw Exception("Failed to ensure home.");
      setState(() => _homeId = ensured);
      await TuyaPlatform.openQrScan(homeId: ensured);
      return;
    }

    debugPrint("📷 open qr scan for homeId=$hid");
    await TuyaPlatform.openQrScan(homeId: hid);
  });

  @override
  void initState() {
    super.initState();
    _loadHomes();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF5F7FB),
      appBar: AppBar(
        title: Text(_homeId == null ? "Alrawi" : "Alrawi • $_homeName"),
        backgroundColor: Colors.white,
        foregroundColor: Colors.black,
        elevation: 0,
        actions: [
          IconButton(
            onPressed: _busy ? null : _loadHomes,
            icon: const Icon(Icons.refresh),
          ),
          IconButton(
            onPressed: _busy ? null : _logout,
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _busy ? null : _addDevice,
        child: const Icon(Icons.add),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _card(
            child: Row(
              children: [
                const Icon(Icons.home_rounded),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    _homeId == null ? "No home selected" : "Home ID: $_homeId",
                    style: const TextStyle(fontWeight: FontWeight.w900),
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
          const SizedBox(height: 12),
          _card(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  "Homes",
                  style: TextStyle(fontWeight: FontWeight.w900, fontSize: 16),
                ),
                const SizedBox(height: 10),
                if (_homes.isEmpty)
                  const Text(
                    "No homes found (should not happen after ensureHome).",
                  )
                else
                  DropdownButtonFormField<int>(
                    value: _homeId,
                    items: _homes
                        .map(
                          (h) => DropdownMenuItem<int>(
                            value: (h["homeId"] as num).toInt(),
                            child: Text("${h["name"]}"),
                          ),
                        )
                        .toList(),
                    onChanged: _busy
                        ? null
                        : (v) {
                            if (v == null) return;
                            final match = _homes.firstWhere(
                              (h) => (h["homeId"] as num).toInt() == v,
                            );
                            setState(() {
                              _homeId = v;
                              _homeName = (match["name"] ?? "Home").toString();
                            });
                          },
                    decoration: const InputDecoration(
                      border: OutlineInputBorder(),
                      labelText: "Select home",
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
                const Text(
                  "Add device (Tuya flow)",
                  style: TextStyle(fontWeight: FontWeight.w900, fontSize: 16),
                ),
                const SizedBox(height: 10),
                SizedBox(
                  width: double.infinity,
                  height: 48,
                  child: ElevatedButton.icon(
                    onPressed: _busy ? null : _addDevice,
                    icon: const Icon(Icons.add),
                    label: const Text("Add Device / Gateway"),
                  ),
                ),
                const SizedBox(height: 10),
                SizedBox(
                  width: double.infinity,
                  height: 48,
                  child: OutlinedButton.icon(
                    onPressed: _busy ? null : _scanQr,
                    icon: const Icon(Icons.qr_code_scanner),
                    label: const Text("Scan QR Code"),
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
