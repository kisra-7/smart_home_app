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
  final TextEditingController _gwDevIdCtrl = TextEditingController();

  bool _gatewayAdded = false;
  String _gatewayName = "";
  bool _gatewayOnline = false;

  static const MethodChannel _native = MethodChannel('tuya_bridge');

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
      // ignore
    } finally {
      WidgetsBinding.instance.removeObserver(observer);
    }

    await Future.delayed(const Duration(milliseconds: 250));
  }

  @override
  void initState() {
    super.initState();

    Future.microtask(() {
      ref.read(homeHubControllerProvider.notifier).loadHomes(autoPickFirst: true);
    });

    // Native -> Flutter events
    TuyaPlatform.setEventHandler((MethodCall call) async {
      if (!mounted) return null;

      if (call.method == "onGatewayAdded") {
        final gwName = (call.arguments as Map?)?["name"]?.toString() ?? "Gateway";
        setState(() {
          _gatewayAdded = true;
          _gatewayName = gwName;
        });
      }

      if (call.method == "onGatewayOnline") {
        final online = (call.arguments as Map?)?["online"] == true;
        setState(() => _gatewayOnline = online);
      }

      return null;
    });
  }

  @override
  void dispose() {
    _gwDevIdCtrl.dispose();
    super.dispose();
  }

  Future<void> _logout() async {
    await TuyaPlatform.logout();
    if (!mounted) return;
    Navigator.pushAndRemoveUntil(
      context,
      MaterialPageRoute(builder: (_) => const AuthPage()),
      (_) => false,
    );
  }

  /// ✅ Stable flow:
  /// Scan QR (Flutter) -> pairDeviceByQr(homeId, qrUrl)
  Future<void> _addGatewayViaQrStable({required bool busy}) async {
    if (busy) return;

    try {
      final ctrl = ref.read(homeHubControllerProvider.notifier);
      final homeId = await ctrl.ensureHomeId(); // also calls setCurrentHome native

      final qr = await Navigator.push<String?>(
        context,
        MaterialPageRoute(builder: (_) => const _QrScanPage()),
      );

      if (qr == null || qr.trim().isEmpty) return;

      await _waitUntilResumed();

      await TuyaPlatform.pairDeviceByQr(
        homeId: homeId,
        qrUrl: qr.trim(),
        timeout: 100,
      );

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("Pairing started…")),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text("Error: $e")));
    }
  }

  /// ✅ FIXED: no longer depends on TuyaPlatform.getGatewayInfo (which doesn't exist)
  /// Instead calls native directly. If native method isn't implemented, you’ll see the real error.
  Future<void> _checkGatewayInfo({required bool busy}) async {
    if (busy) return;

    try {
      final ctrl = ref.read(homeHubControllerProvider.notifier);
      await ctrl.ensureHomeId(); // ensure + setCurrentHome native

      final devId = _gwDevIdCtrl.text.trim();
      if (devId.isEmpty) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text("Enter gateway devId first")),
        );
        return;
      }

      // Native call (compile-safe)
      final info = await _native.invokeMethod<dynamic>(
        "getGatewayInfo",
        {"devId": devId},
      );

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text("Gateway info: $info")),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text("Error: $e")));
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(homeHubControllerProvider);
    final ctrl = ref.read(homeHubControllerProvider.notifier);

    final busy = state.busy;

    return Scaffold(
      appBar: AppBar(
        title: const Text("Home Hub"),
        actions: [
          IconButton(
            onPressed: busy ? null : _logout,
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: ListView(
            children: [
              Text("Current homeId: ${state.currentHomeId}"),
              if (state.error != null) ...[
                const SizedBox(height: 6),
                Text(
                  "Error: ${state.error}",
                  style: const TextStyle(color: Colors.red),
                ),
              ],
              const SizedBox(height: 12),

              ElevatedButton(
                onPressed: busy ? null : () => ctrl.loadHomes(autoPickFirst: true),
                child: const Text("Reload homes"),
              ),

              const SizedBox(height: 16),
              const Divider(),
              const SizedBox(height: 16),

              Text("Gateway status: ${_gatewayAdded ? 'Added' : 'Not added'}"),
              if (_gatewayAdded) ...[
                const SizedBox(height: 6),
                Text("Name: $_gatewayName"),
                Text("Online: ${_gatewayOnline ? 'Yes' : 'No'}"),
              ],

              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: busy ? null : () => _addGatewayViaQrStable(busy: busy),
                child: const Text("Add Gateway (QR Stable)"),
              ),

              const SizedBox(height: 16),
              const Divider(),
              const SizedBox(height: 16),

              TextField(
                controller: _gwDevIdCtrl,
                decoration: const InputDecoration(
                  labelText: "Gateway devId",
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),

              ElevatedButton(
                onPressed: busy ? null : () => _checkGatewayInfo(busy: busy),
                child: const Text("Check Gateway Info"),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _LifeObserver with WidgetsBindingObserver {
  final void Function(AppLifecycleState) onChange;

  _LifeObserver(this.onChange);

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) => onChange(state);
}

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
      appBar: AppBar(title: const Text("Scan QR")),
      body: MobileScanner(
        onDetect: (capture) {
          if (_handled) return;
          final barcodes = capture.barcodes;
          if (barcodes.isEmpty) return;

          final raw = barcodes.first.rawValue;
          if (raw == null || raw.trim().isEmpty) return;

          _handled = true;
          Navigator.pop(context, raw.trim());
        },
      ),
    );
  }
}