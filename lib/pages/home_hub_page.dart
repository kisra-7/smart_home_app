import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import '../tuya/controllers/home_hub_controller.dart';
import '../tuya/tuya_platform.dart';
import 'auth_page.dart';

class HomeHubPage extends ConsumerStatefulWidget {
  const HomeHubPage({super.key});

  @override
  ConsumerState<HomeHubPage> createState() => _HomeHubPageState();
}

class _HomeHubPageState extends ConsumerState<HomeHubPage> {
  // Captured after gateway pairing success
  final TextEditingController _gwDevIdCtrl = TextEditingController();

  // ✅ Persistent gateway state indicator (not only snackbar)
  bool _gatewayAdded = false;
  String _gatewayName = "";
  bool _gatewayOnline = false;

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
      await completer.future.timeout(const Duration(seconds: 3));
    } catch (_) {
      // ignore timeout
    } finally {
      WidgetsBinding.instance.removeObserver(observer);
    }

    await Future.delayed(const Duration(milliseconds: 250));
  }

  Future<void> _logout(bool busy) async {
    if (busy) return;
    try {
      await TuyaPlatform.logout();
      if (!mounted) return;
      Navigator.pushAndRemoveUntil(
        context,
        MaterialPageRoute(builder: (_) => const AuthPage()),
        (_) => false,
      );
    } catch (e) {
      debugPrint("❌ Logout error: $e");
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text("Error: $e")),
      );
    }
  }

  // Direct SDK QR flow (kept for testing fallback)
  Future<void> _scanQrAndStartGateway(bool busy) async {
    if (busy) return;

    try {
      final controller = ref.read(homeHubControllerProvider.notifier);
      final hid = await controller.ensureHomeId();

      final qr = await Navigator.push<String?>(
        context,
        MaterialPageRoute(builder: (_) => const _QrScanPage()),
      );

      if (qr == null || qr.trim().isEmpty) return;

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text("QR scanned: ${qr.trim()}")),
      );

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
    } catch (e) {
      debugPrint("❌ QR flow error: $e");
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text("Error: $e")),
      );
    }
  }

  void _showAddSheet({
    required bool busy,
    required Future<void> Function() onBizAddDevice,
    required Future<void> Function() onBizQrScan,
  }) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (_) => _AddDeviceSheet(
        busy: busy,
        onBizAddDevice: onBizAddDevice,
        onBizQrScan: onBizQrScan,
        onScanQr: () => _scanQrAndStartGateway(busy),
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

    // Load homes once after first frame
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(homeHubControllerProvider.notifier).loadHomes();
    });
  }

  @override
  void dispose() {
    _gwDevIdCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final hubState = ref.watch(homeHubControllerProvider);
    final busy = hubState.isLoading;

    final data = hubState.value ?? HomeHubState.empty;
    final homes = data.homes;
    final homeId = data.selectedHomeId;
    final homeName = data.selectedHomeName;

    final title = homeId == null ? "Alrawi" : homeName;

    final devId = _gwDevIdCtrl.text.trim();
    final gatewaySubtitle = devId.isEmpty
        ? "Not added yet"
        : "Added ✅ • ${_gatewayOnline ? "Online" : "Offline"}"
            "${_gatewayName.isNotEmpty ? " • $_gatewayName" : ""}"
            " • devId: $devId";

    ref.listen(homeHubControllerProvider, (prev, next) {
      next.whenOrNull(
        error: (e, _) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text("Error: $e")),
          );
        },
      );
    });

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
            if (homes.isNotEmpty)
              PopupMenuButton<int>(
                tooltip: "Switch Home",
                onSelected: (id) {
                  final match = homes.firstWhere(
                    (h) => (h["homeId"] as num).toInt() == id,
                  );
                  ref.read(homeHubControllerProvider.notifier).selectHome(
                        id,
                        (match["name"] ?? "Home").toString(),
                      );

                  setState(() {
                    _gwDevIdCtrl.clear();
                    _gatewayAdded = false;
                    _gatewayName = "";
                    _gatewayOnline = false;
                  });
                },
                itemBuilder: (_) => homes
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
            onPressed: busy
                ? null
                : () => ref
                    .read(homeHubControllerProvider.notifier)
                    .loadHomes(autoPickFirst: false),
            icon: const Icon(Icons.refresh),
          ),
          IconButton(
            onPressed: () => _logout(busy),
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
            trailing: busy
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.chevron_right),
            onTap: busy
                ? null
                : () => _showAddSheet(
                      busy: busy,
                      onBizAddDevice: () =>
                          ref.read(homeHubControllerProvider.notifier).openBizAddDevice(),
                      onBizQrScan: () =>
                          ref.read(homeHubControllerProvider.notifier).openBizQrScan(),
                    ),
            borderColor: devId.isEmpty
                ? Colors.black12
                : Colors.green.withOpacity(0.35),
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
        onPressed: busy
            ? null
            : () => _showAddSheet(
                  busy: busy,
                  onBizAddDevice: () =>
                      ref.read(homeHubControllerProvider.notifier).openBizAddDevice(),
                  onBizQrScan: () =>
                      ref.read(homeHubControllerProvider.notifier).openBizQrScan(),
                ),
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
                  Text(
                    title,
                    style: const TextStyle(fontWeight: FontWeight.w900),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    subtitle,
                    style: const TextStyle(color: Colors.black54),
                  ),
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
  final Future<void> Function() onBizAddDevice;
  final Future<void> Function() onBizQrScan;
  final Future<void> Function() onScanQr;

  const _AddDeviceSheet({
    required this.busy,
    required this.onBizAddDevice,
    required this.onBizQrScan,
    required this.onScanQr,
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
              child: Text(
                "Add Device",
                style: TextStyle(fontWeight: FontWeight.w900, fontSize: 16),
              ),
            ),
            const SizedBox(height: 12),

            _sheetBtn(
              context,
              icon: Icons.add_circle_outline,
              title: "Add Device (Tuya UI)",
              subtitle: "Official Tuya pairing flow (Wi-Fi / Zigbee / BLE / QR)",
              onTap: busy
                  ? null
                  : () async {
                      Navigator.pop(context);
                      await onBizAddDevice();
                    },
            ),
            const SizedBox(height: 10),
            _sheetBtn(
              context,
              icon: Icons.qr_code,
              title: "Open Native QR Scan (Tuya UI)",
              subtitle: "Use Tuya’s built-in QR scanner screen",
              onTap: busy
                  ? null
                  : () async {
                      Navigator.pop(context);
                      await onBizQrScan();
                    },
            ),

            const SizedBox(height: 10),
            _sheetBtn(
              context,
              icon: Icons.qr_code_scanner,
              title: "QR Scan (Direct SDK fallback)",
              subtitle: "Manual QR scan (mobile_scanner) then pair",
              onTap: busy
                  ? null
                  : () async {
                      Navigator.pop(context);
                      await onScanQr();
                    },
            ),

            const SizedBox(height: 8),
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
    required Future<void> Function()? onTap,
  }) {
    return InkWell(
      onTap: onTap == null ? null : () => onTap(),
      borderRadius: BorderRadius.circular(14),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: Colors.black12),
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
            const Icon(Icons.chevron_right),
          ],
        ),
      ),
    );
  }
}

// ---------------- QR page (unchanged) ----------------
class _QrScanPage extends StatefulWidget {
  const _QrScanPage();

  @override
  State<_QrScanPage> createState() => _QrScanPageState();
}

class _QrScanPageState extends State<_QrScanPage> {
  final MobileScannerController _controller = MobileScannerController();
  bool _handled = false;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("Scan QR")),
      body: MobileScanner(
        controller: _controller,
        onDetect: (capture) {
          if (_handled) return;
          final barcode = capture.barcodes.firstOrNull;
          final raw = barcode?.rawValue;
          if (raw == null || raw.trim().isEmpty) return;
          _handled = true;
          Navigator.pop(context, raw.trim());
        },
      ),
    );
  }
}

extension _FirstOrNull<T> on List<T> {
  T? get firstOrNull => isEmpty ? null : first;
}
