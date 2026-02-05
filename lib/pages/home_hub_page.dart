// lib/pages/home_page.dart
import 'package:alrawi_app/tuya/tuya_platform.dart';
import 'package:flutter/material.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  bool _busy = false;

  List<Map<String, dynamic>> _homes = [];
  int? _selectedHomeId;
  String? _selectedHomeName;

  // Create home inputs
  final _homeNameCtrl = TextEditingController(text: "My Home");
  final _geoNameCtrl = TextEditingController(text: "Oman");
  final _roomsCtrl = TextEditingController(text: "Living Room,Bedroom");

  final List<String> _logs = [];

  void _log(String msg) {
    final now = TimeOfDay.now();
    final line =
        "[${now.hour.toString().padLeft(2, '0')}:${now.minute.toString().padLeft(2, '0')}] $msg";
    if (!mounted) return;
    setState(() => _logs.insert(0, line));
  }

  Future<void> _run(Future<void> Function() action) async {
    if (!mounted) return;
    setState(() => _busy = true);
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

  Future<void> _loadHomes({bool autoSelectFirst = true}) => _run(() async {
    _log("Loading homes...");
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
  });

  Future<void> _createHome() => _run(() async {
    final name = _homeNameCtrl.text.trim();
    if (name.isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text("Please enter a home name")));
      return;
    }

    final rooms = _roomsCtrl.text
        .split(",")
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toList();

    _log("Creating home...");
    final created = await TuyaPlatform.createHome(
      name: name,
      geoName: _geoNameCtrl.text.trim(),
      rooms: rooms,
    );

    final newHomeId = (created["homeId"] as num).toInt();
    final newHomeName = (created["name"] ?? name).toString();

    _log("✅ Home created: $newHomeName (ID: $newHomeId)");

    // Refresh list and select created home
    await _loadHomes(autoSelectFirst: false);
    if (!mounted) return;
    setState(() {
      _selectedHomeId = newHomeId;
      _selectedHomeName = newHomeName;
    });
  });

  Future<void> _openAddDevice() => _run(() async {
    _log("Opening Add Device UI...");
    // Smart Life style flow (Android handles UI)
    await TuyaPlatform.openAddGateway();
    _log("✅ Add Device UI opened");
  });

  Future<void> _openQrScan() => _run(() async {
    _log("Opening QR scan...");
    await TuyaPlatform.openQrScan();
    _log("✅ QR scan opened");
  });

  Future<void> _logout() => _run(() async {
    _log("Logging out...");
    await TuyaPlatform.logout();
    _log("✅ Logged out");
    if (!mounted) return;
    Navigator.of(context).pop(); // back to login page
  });

  @override
  void initState() {
    super.initState();
    _loadHomes();
  }

  @override
  void dispose() {
    _homeNameCtrl.dispose();
    _geoNameCtrl.dispose();
    _roomsCtrl.dispose();
    super.dispose();
  }

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
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF6F7FB),
      appBar: AppBar(
        title: const Text("Home"),
        centerTitle: true,
        backgroundColor: Colors.white,
        foregroundColor: Colors.black,
        elevation: 0,
        actions: [
          IconButton(
            onPressed: _busy ? null : () => _loadHomes(autoSelectFirst: false),
            icon: const Icon(Icons.refresh),
            tooltip: "Refresh homes",
          ),
          IconButton(
            onPressed: _busy ? null : _logout,
            icon: const Icon(Icons.logout),
            tooltip: "Logout",
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _card(
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    _selectedHomeId == null
                        ? "No home selected"
                        : "Home: ${_selectedHomeName ?? "Home"} (ID: $_selectedHomeId)",
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

          _card(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  "Homes",
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.w900),
                ),
                const SizedBox(height: 10),

                if (_homes.isEmpty)
                  const Text("No homes found. Create one below.")
                else
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
                    onChanged: _busy
                        ? null
                        : (v) {
                            if (v == null) return;
                            final match = _homes.firstWhere(
                              (h) => (h["homeId"] as num).toInt() == v,
                            );
                            setState(() {
                              _selectedHomeId = v;
                              _selectedHomeName = (match["name"] ?? "Home")
                                  .toString();
                            });
                            _log(
                              "Selected home: $_selectedHomeName ($_selectedHomeId)",
                            );
                          },
                    decoration: const InputDecoration(
                      labelText: "Selected Home",
                      border: OutlineInputBorder(),
                    ),
                  ),

                const SizedBox(height: 12),
                const Divider(),
                const SizedBox(height: 12),

                const Text(
                  "Create Home",
                  style: TextStyle(fontSize: 14, fontWeight: FontWeight.w800),
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
                  onPressed: _busy ? null : _createHome,
                  icon: const Icon(Icons.add_home),
                  label: const Text("Create Home"),
                ),
              ],
            ),
          ),

          _card(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  "Add Devices",
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
                const SizedBox(height: 8),
                const Text(
                  "Use the Tuya/Smart Life pairing flow to add gateways and Zigbee sub-devices.",
                  style: TextStyle(color: Colors.black54),
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
