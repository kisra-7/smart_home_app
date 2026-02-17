import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import '../tuya/tuya_platform.dart';
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

  // Captured after gateway pairing success
  final TextEditingController _gwDevIdCtrl = TextEditingController();

  // ✅ Persistent gateway state indicator (not only snackbar)
  bool _gatewayAdded = false;
  String _gatewayName = "";
  bool _gatewayOnline = false;

  Future<void> _run(Future<void> Function() fn) async {
    if (!mounted) return;
    setState(() => _busy = true);
    try {
      await fn();
    } catch (e) {
      debugPrint("❌ HomeHub error: $e");
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("Error: $e")),
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _loadHomes({bool autoPickFirst = true}) => _run(() async {
        final homes = await TuyaPlatform.getHomeList();

        if (!mounted) return;
        setState(() {
          _homes = homes;
          if (autoPickFirst && homes.isNotEmpty) {
            final first = homes.first;
            _homeId = (first["homeId"] as num?)?.toInt();
            _homeName = (first["name"] ?? "Home").toString();
          }
        });
      });

  Future<void> _ensureHomeIfNeeded() async {
    final hid = _homeId;
    if (hid != null && hid > 0) return;

    final info = await TuyaPlatform.ensureHome();
    final ensured = (info["homeId"] as num?)?.toInt() ?? 0;
    if (ensured <= 0) throw Exception("Failed to ensure home.");

    if (!mounted) return;
    setState(() {
      _homeId = ensured;
      _homeName = (info["name"] ?? "Home").toString();
    });
  }

  Future<void> _logout() => _run(() async {
        await TuyaPlatform.logout();
        if (!mounted) return;
        Navigator.pushAndRemoveUntil(
          context,
          MaterialPageRoute(builder: (_) => const AuthPage()),
          (_) => false,
        );
      });

  Future<void> _startGatewayPairing() => _run(() async {
        await _ensureHomeIfNeeded();
        final hid = _homeId!;
        await TuyaPlatform.startZigbeeGatewayPairing(homeId: hid);

        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text("Gateway pairing started. Put hub in pairing mode."),
          ),
        );
      });

  Future<void> _startSubPairing() => _run(() async {
        final gwDevId = _gwDevIdCtrl.text.trim();
        if (gwDevId.isEmpty) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text("Gateway devId is empty. Pair gateway first.")),
          );
          return;
        }
        await TuyaPlatform.startZigbeeSubDevicePairing(gwDevId: gwDevId);
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text("Zigbee pairing started. Reset Zigbee device now.")),
        );
      });

  // ✅ IMPORTANT: wait until app returns to RESUMED after closing the camera
  Future<void> _waitUntilResumed() async {
    if (!mounted) return;

    final current = WidgetsBinding.instance.lifecycleState;
    if (current == AppLifecycleState.resumed) return;

    final completer = Completer<void>();

    late final _LifeObserver observer;
    observer = _LifeObserver((state) {
      if (state == AppLifecycleState.resumed && !completer.isCompleted) {
        completer.complete();
      }
    });

    WidgetsBinding.instance.addObserver(observer);

    try {
      // Give it a short window; if it doesn't resume, we still proceed.
      await completer.future.timeout(const Duration(seconds: 3));
    } catch (_) {
      // ignore timeout
    } finally {
      WidgetsBinding.instance.removeObserver(observer);
    }

    // One extra small delay helps some devices settle UI transitions
    await Future.delayed(const Duration(milliseconds: 250));
  }

  Future<void> _scanQrAndStartGateway() => _run(() async {
        await _ensureHomeIfNeeded();
        final hid = _homeId!;

        final qr = await Navigator.push<String?>(
          context,
          MaterialPageRoute(builder: (_) => const _QrScanPage()),
        );

        if (qr == null || qr.trim().isEmpty) return;

        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("QR scanned: ${qr.trim()}")),
        );

        // ✅ FIX: do not start Tuya pairing until app is RESUMED (foreground=true)
        await _waitUntilResumed();

        await TuyaPlatform.pairDeviceByQr(
          homeId: hid,
          qrUrl: qr.trim(),
          timeout: 100,
        );

        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text("QR pairing started...")),
        );
      });

  void _showAddSheet() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (_) => _AddDeviceSheet(
        busy: _busy,
        onScanQr: _scanQrAndStartGateway,
        onAddGateway: _startGatewayPairing,
        onAddZigbee: _startSubPairing,
      ),
    );
  }

  @override
  void initState() {
    super.initState();

    // Listen to native events and capture gateway devId automatically
    TuyaPlatform.setEventHandler((MethodCall call) async {
      if (!mounted) return null;

      if (call.method == "tuya_gw_success" && call.arguments is Map) {
        final map = Map<String, dynamic>.from(call.arguments as Map);
        final devId = (map["devId"] ?? "").toString();
        final name = (map["name"] ?? "").toString();
        final online = map["isOnline"] == true;

        if (devId.isNotEmpty) {
          setState(() {
            _gwDevIdCtrl.text = devId;
            _gatewayAdded = true;
            _gatewayName = name;
            _gatewayOnline = online;
          });

          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text("Gateway added ✅ devId: $devId")),
          );
        }
      }

      if (call.method == "tuya_sub_success" && call.arguments is Map) {
        final map = Map<String, dynamic>.from(call.arguments as Map);
        final name = (map["name"] ?? "Zigbee device").toString();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("$name added ✅")),
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

    _loadHomes();
  }

  @override
  void dispose() {
    _gwDevIdCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final title = _homeId == null ? "Alrawi" : _homeName;

    final devId = _gwDevIdCtrl.text.trim();

    final gatewaySubtitle = devId.isEmpty
        ? "Not added yet"
        : "Added ✅ • ${_gatewayOnline ? "Online" : "Offline"}"
            "${_gatewayName.isNotEmpty ? " • $_gatewayName" : ""}"
            " • devId: $devId";

    return Scaffold(
      backgroundColor: const Color(0xFFF4F6FA),
      appBar: AppBar(
        backgroundColor: Colors.white,
        foregroundColor: Colors.black,
        elevation: 0,
        title: Row(
          children: [
            Expanded(
              child: Text(
                title,
                style: const TextStyle(fontWeight: FontWeight.w900),
                overflow: TextOverflow.ellipsis,
              ),
            ),
            const SizedBox(width: 8),
            if (_homes.isNotEmpty)
              PopupMenuButton<int>(
                tooltip: "Switch Home",
                onSelected: (id) {
                  final match = _homes.firstWhere((h) => (h["homeId"] as num).toInt() == id);
                  setState(() {
                    _homeId = id;
                    _homeName = (match["name"] ?? "Home").toString();

                    _gwDevIdCtrl.clear();
                    _gatewayAdded = false;
                    _gatewayName = "";
                    _gatewayOnline = false;
                  });
                },
                itemBuilder: (_) => _homes
                    .map(
                      (h) => PopupMenuItem<int>(
                        value: (h["homeId"] as num).toInt(),
                        child: Text((h["name"] ?? "Home").toString()),
                      ),
                    )
                    .toList(),
                child: const Icon(Icons.expand_more),
              ),
          ],
        ),
        actions: [
          IconButton(
            onPressed: _busy ? null : () => _loadHomes(autoPickFirst: false),
            icon: const Icon(Icons.refresh),
          ),
          IconButton(
            onPressed: _busy ? null : _logout,
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 110),
        children: [
          _sectionHeader("My Home"),
          const SizedBox(height: 10),
          _tileCard(
            icon: Icons.hub,
            title: "Gateway / Hub",
            subtitle: gatewaySubtitle,
            trailing: _busy
                ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                : const Icon(Icons.chevron_right),
            onTap: _busy ? null : _showAddSheet,
            borderColor: devId.isEmpty ? Colors.black12 : Colors.green.withOpacity(0.35),
          ),
          if (_gatewayAdded && devId.isNotEmpty) ...[
            const SizedBox(height: 10),
            _statusPill(
              icon: Icons.check_circle,
              text: "Gateway added successfully",
            ),
          ],
          const SizedBox(height: 12),
          _sectionHeader("Devices"),
          const SizedBox(height: 10),
          _emptyDevicesCard(),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _busy ? null : _showAddSheet,
        backgroundColor: Colors.blueAccent,
        icon: const Icon(Icons.add),
        label: const Text("Add Device"),
      ),
    );
  }

  Widget _statusPill({required IconData icon, required String text}) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: const Color(0xFFEAF7EF),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.green.withOpacity(0.25)),
      ),
      child: Row(
        children: [
          Icon(icon, color: Colors.green),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              text,
              style: const TextStyle(fontWeight: FontWeight.w800),
            ),
          ),
        ],
      ),
    );
  }

  Widget _sectionHeader(String text) {
    return Text(
      text,
      style: const TextStyle(
        fontWeight: FontWeight.w900,
        fontSize: 16,
        color: Colors.black87,
      ),
    );
  }

  Widget _tileCard({
    required IconData icon,
    required String title,
    required String subtitle,
    required Widget trailing,
    VoidCallback? onTap,
    Color borderColor = Colors.black12,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: borderColor),
          boxShadow: const [
            BoxShadow(
              blurRadius: 14,
              offset: Offset(0, 6),
              color: Color(0x11000000),
            ),
          ],
        ),
        child: Row(
          children: [
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: const Color(0xFFF1F4FF),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(icon, color: Colors.blueAccent),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: const TextStyle(fontWeight: FontWeight.w900)),
                  const SizedBox(height: 4),
                  Text(subtitle, style: const TextStyle(color: Colors.black54)),
                ],
              ),
            ),
            trailing,
          ],
        ),
      ),
    );
  }

  Widget _emptyDevicesCard() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.black12),
      ),
      child: Column(
        children: const [
          Icon(Icons.devices_other, size: 36, color: Colors.black38),
          SizedBox(height: 10),
          Text(
            "No devices yet",
            style: TextStyle(fontWeight: FontWeight.w900),
          ),
          SizedBox(height: 6),
          Text(
            "Tap “Add Device” to add your hub then pair Zigbee devices.",
            textAlign: TextAlign.center,
            style: TextStyle(color: Colors.black54),
          ),
        ],
      ),
    );
  }
}

class _LifeObserver extends WidgetsBindingObserver {
  _LifeObserver(this.onState);
  final void Function(AppLifecycleState state) onState;

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    onState(state);
  }
}

class _AddDeviceSheet extends StatelessWidget {
  final bool busy;
  final Future<void> Function() onScanQr;
  final Future<void> Function() onAddGateway;
  final Future<void> Function() onAddZigbee;

  const _AddDeviceSheet({
    required this.busy,
    required this.onScanQr,
    required this.onAddGateway,
    required this.onAddZigbee,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(18)),
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 42,
              height: 5,
              decoration: BoxDecoration(
                color: Colors.black12,
                borderRadius: BorderRadius.circular(99),
              ),
            ),
            const SizedBox(height: 12),
            const Align(
              alignment: Alignment.centerLeft,
              child: Text("Add Device", style: TextStyle(fontWeight: FontWeight.w900, fontSize: 16)),
            ),
            const SizedBox(height: 12),
            _sheetBtn(
              context,
              icon: Icons.qr_code_scanner,
              title: "Scan QR Code (recommended)",
              subtitle: "Use the hub QR sticker to add it quickly",
              onTap: busy
                  ? null
                  : () async {
                      Navigator.pop(context);
                      await onScanQr();
                    },
            ),
            const SizedBox(height: 10),
            _sheetBtn(
              context,
              icon: Icons.hub,
              title: "Add Gateway / Hub",
              subtitle: "Manual pairing (no QR scan)",
              onTap: busy
                  ? null
                  : () async {
                      Navigator.pop(context);
                      await onAddGateway();
                    },
            ),
            const SizedBox(height: 10),
            _sheetBtn(
              context,
              icon: Icons.sensors,
              title: "Add Zigbee Device",
              subtitle: "Pair sensors/switches through the hub",
              onTap: busy
                  ? null
                  : () async {
                      Navigator.pop(context);
                      await onAddZigbee();
                    },
            ),
            const SizedBox(height: 6),
          ],
        ),
      ),
    );
  }

  Widget _sheetBtn(
    BuildContext context, {
    required IconData icon,
    required String title,
    required String subtitle,
    required VoidCallback? onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(14),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: Colors.black12),
          color: const Color(0xFFF8FAFF),
        ),
        child: Row(
          children: [
            Icon(icon, color: Colors.blueAccent),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: const TextStyle(fontWeight: FontWeight.w900)),
                  const SizedBox(height: 4),
                  Text(subtitle, style: const TextStyle(color: Colors.black54)),
                ],
              ),
            ),
            const Icon(Icons.chevron_right, color: Colors.black45),
          ],
        ),
      ),
    );
  }
}

/// Simple QR scan page (returns scanned string)
class _QrScanPage extends StatefulWidget {
  const _QrScanPage();

  @override
  State<_QrScanPage> createState() => _QrScanPageState();
}

class _QrScanPageState extends State<_QrScanPage> {
  bool _handled = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
        title: const Text("Scan QR Code"),
      ),
      body: Stack(
        children: [
          MobileScanner(
            onDetect: (capture) {
              if (_handled) return;
              final codes = capture.barcodes;
              if (codes.isEmpty) return;

              final raw = codes.first.rawValue;
              if (raw == null || raw.trim().isEmpty) return;

              _handled = true;
              Navigator.pop(context, raw.trim());
            },
          ),
          Center(
            child: Container(
              width: 260,
              height: 260,
              decoration: BoxDecoration(
                border: Border.all(color: Colors.white70, width: 2),
                borderRadius: BorderRadius.circular(18),
              ),
            ),
          ),
          const Positioned(
            left: 24,
            right: 24,
            bottom: 30,
            child: Text(
              "Align the QR within the frame.\nScanning will start pairing automatically.",
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.white70),
            ),
          )
        ],
      ),
    );
  }
}
