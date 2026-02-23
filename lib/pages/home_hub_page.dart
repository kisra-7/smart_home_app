import 'dart:async';

import 'package:alrawi_app/ui/widgets/device_card.dart';
import 'package:alrawi_app/ui/widgets/section_title.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../tuya/tuya_platform.dart';
import '../tuya/controllers/home_hub_controller.dart';
import 'auth_page.dart';
class HomeHubPage extends ConsumerStatefulWidget {
  const HomeHubPage({super.key});

  @override
  ConsumerState<HomeHubPage> createState() => _HomeHubPageState();
}

class _HomeHubPageState extends ConsumerState<HomeHubPage> with WidgetsBindingObserver {
  static const _bg = Color(0xFFF4F6FA);

  final _gwDevIdCtrl = TextEditingController();
  bool _gatewayAdded = false;
  String _gatewayName = "";
  bool _gatewayOnline = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);

    // Re-load homes once the page is alive.
    Future.microtask(() {
      ref.read(homeHubControllerProvider.notifier).loadHomes(autoPickFirst: true);
    });

    // Optional: listen for native events if your native bridge emits them.
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

      if (call.method == "tuya_gw_error" && call.arguments is Map) {
        final map = Map<String, dynamic>.from(call.arguments as Map);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("Gateway error: ${map["msg"] ?? "unknown"}")),
        );
      }

      return null;
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _gwDevIdCtrl.dispose();
    super.dispose();
  }

  Future<void> _logout(bool busy) async {
    if (busy) return;
    try {
      await TuyaPlatform.logout();
    } catch (_) {}

    if (!mounted) return;
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const AuthPage()),
      (_) => false,
    );
  }

  Future<void> _waitUntilResumed() async {
    // Useful when returning from native UI (BizBundle) back to Flutter.
    if (WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed) return;

    final completer = Completer<void>();
    late final _LifecycleObserver observer;

    observer = _LifecycleObserver(onResumed: () {
      if (!completer.isCompleted) completer.complete();
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

  Future<void> _openBizAddDevice({required bool busy}) async {
    if (busy) return;
    await ref.read(homeHubControllerProvider.notifier).openBizAddDevice();
    await _waitUntilResumed();

    // After returning, refresh homes list (safe).
    ref.read(homeHubControllerProvider.notifier).loadHomes(autoPickFirst: false);
  }

  Future<void> _openBizQrScan({required bool busy}) async {
    if (busy) return;
    await ref.read(homeHubControllerProvider.notifier).openBizQrScan();
    await _waitUntilResumed();

    // After returning, refresh homes list (safe).
    ref.read(homeHubControllerProvider.notifier).loadHomes(autoPickFirst: false);
  }

  @override
  Widget build(BuildContext context) {
    final hubState = ref.watch(homeHubControllerProvider);
    final busy = hubState.isLoading;

    ref.listen(homeHubControllerProvider, (prev, next) {
      next.whenOrNull(
        error: (e, _) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text("Error: $e")),
          );
        },
      );
    });

    final data = hubState.value ?? HomeHubState.empty;
    final homes = data.homes;
    final homeId = data.selectedHomeId;
    final homeName = data.selectedHomeName;

    final title = homeId == null ? "AL RAWI" : homeName;

    final devId = _gwDevIdCtrl.text.trim();
    final gatewaySubtitle = devId.isEmpty
        ? "Not added yet"
        : "Added • ${_gatewayOnline ? "Online" : "Offline"}"
            "${_gatewayName.isNotEmpty ? " • $_gatewayName" : ""}"
            " • devId: $devId";

    return Scaffold(
      backgroundColor: _bg,
      body: SafeArea(
        child: CustomScrollView(
          slivers: [
            SliverToBoxAdapter(
              child: _TopHeader(
                title: title,
                homes: homes,
                busy: busy,
                onRefresh: () => ref
                    .read(homeHubControllerProvider.notifier)
                    .loadHomes(autoPickFirst: false),
                onSelectHome: (id) {
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
                onLogout: () => _logout(busy),
              ),
            ),

            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 14, 16, 8),
              sliver: SliverToBoxAdapter(
                child: Row(
                  children: [
                    Expanded(
                      child: ElevatedButton.icon(
                        onPressed: busy ? null : () => _openBizAddDevice(busy: busy),
                        icon: const Icon(Icons.add),
                        label: const Text("Add Device"),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xFF0B84FF),
                          foregroundColor: Colors.white,
                          padding: const EdgeInsets.symmetric(vertical: 14),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(14),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: busy ? null : () => _openBizQrScan(busy: busy),
                        icon: const Icon(Icons.qr_code_scanner),
                        label: const Text("Scan QR"),
                        style: OutlinedButton.styleFrom(
                          padding: const EdgeInsets.symmetric(vertical: 14),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(14),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),

            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
              sliver: SliverToBoxAdapter(
                child: SectionTitle(
                  title: "Gateway / Hub",
                  actionText: "Options",
                  onAction: busy ? null : () => _showGatewaySheet(busy: busy),
                ),
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
              sliver: SliverToBoxAdapter(
                child: DeviceCard(
                  icon: Icons.hub,
                  title: "Gateway",
                  subtitle: gatewaySubtitle,
                  badgeText: devId.isEmpty ? "Not added" : (_gatewayOnline ? "Online" : "Offline"),
                  badgeColor: devId.isEmpty
                      ? Colors.grey
                      : (_gatewayOnline ? Colors.green : Colors.orange),
                  onTap: busy ? null : () => _showGatewaySheet(busy: busy),
                  trailing: busy
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : null,
                ),
              ),
            ),

            if (_gatewayAdded && devId.isNotEmpty)
              SliverPadding(
                padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
                sliver: SliverToBoxAdapter(
                  child: _statusPill(
                    icon: Icons.check_circle,
                    text: "Gateway added successfully",
                  ),
                ),
              ),

            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 18, 16, 0),
              sliver: SliverToBoxAdapter(
                child: const SectionTitle(title: "Devices"),
              ),
            ),

            // For now your platform layer doesn’t expose “device list”, so we show placeholder.
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 24),
              sliver: SliverToBoxAdapter(
                child: _emptyDevicesCard(),
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _showGatewaySheet({required bool busy}) {
    showModalBottomSheet(
      context: context,
      showDragHandle: true,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(18)),
      ),
      builder: (_) {
        return Padding(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 18),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const SizedBox(height: 6),
              const Text(
                "Gateway Actions",
                style: TextStyle(fontWeight: FontWeight.w900, fontSize: 16),
              ),
              const SizedBox(height: 14),
              ListTile(
                leading: const Icon(Icons.add),
                title: const Text("Add Device (Tuya UI)"),
                subtitle: const Text("Recommended: full Tuya pairing flow"),
                onTap: busy
                    ? null
                    : () async {
                        Navigator.pop(context);
                        await _openBizAddDevice(busy: false);
                      },
              ),
              ListTile(
                leading: const Icon(Icons.qr_code_scanner),
                title: const Text("Scan QR (Tuya UI)"),
                subtitle: const Text("Opens native QR scanner"),
                onTap: busy
                    ? null
                    : () async {
                        Navigator.pop(context);
                        await _openBizQrScan(busy: false);
                      },
              ),

              // If you later re-enable Direct SDK QR pairing (native side),
              // you can add a stable QR fallback button here again.

              const SizedBox(height: 10),
            ],
          ),
        );
      },
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
          const Icon(Icons.check_circle, color: Colors.green),
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

  Widget _emptyDevicesCard() {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.black12),
      ),
      child: const Row(
        children: [
          Icon(Icons.devices_other, color: Colors.black54),
          SizedBox(width: 10),
          Expanded(
            child: Text(
              "Devices list will appear here.\nNext step: expose device list from native (ThingHomeSdk) to Flutter.",
              style: TextStyle(color: Colors.black54),
            ),
          ),
        ],
      ),
    );
  }
}

class _TopHeader extends StatelessWidget {
  final String title;
  final List<Map<String, dynamic>> homes;
  final bool busy;
  final VoidCallback onRefresh;
  final ValueChanged<int> onSelectHome;
  final VoidCallback onLogout;

  const _TopHeader({
    required this.title,
    required this.homes,
    required this.busy,
    required this.onRefresh,
    required this.onSelectHome,
    required this.onLogout,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 10),
      child: Row(
        children: [
          Expanded(
            child: Row(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: const Color(0xFF0B84FF),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(Icons.home_rounded, color: Colors.white),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    title,
                    style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 18),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                if (homes.isNotEmpty)
                  PopupMenuButton<int>(
                    tooltip: "Switch Home",
                    onSelected: onSelectHome,
                    itemBuilder: (_) => homes
                        .map(
                          (h) => PopupMenuItem<int>(
                            value: (h["homeId"] as num).toInt(),
                            child: Text((h["name"] ?? "Home").toString()),
                          ),
                        )
                        .toList(),
                    child: const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 6),
                      child: Icon(Icons.expand_more),
                    ),
                  ),
              ],
            ),
          ),
          IconButton(
            onPressed: busy ? null : onRefresh,
            icon: const Icon(Icons.refresh),
          ),
          IconButton(
            onPressed: onLogout,
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
    );
  }
}

class _LifecycleObserver extends WidgetsBindingObserver {
  final VoidCallback onResumed;

  _LifecycleObserver({required this.onResumed});

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) onResumed();
  }
}