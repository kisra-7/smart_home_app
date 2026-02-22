import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../tuya/controllers/home_hub_controller.dart';
import '../tuya/tuya_platform.dart';
import 'auth_page.dart';

class HomeHubPage extends ConsumerStatefulWidget {
  const HomeHubPage({super.key});

  @override
  ConsumerState<HomeHubPage> createState() => _HomeHubPageState();
}

class _HomeHubPageState extends ConsumerState<HomeHubPage> {
  // Captured after gateway pairing success (optional future use)
  final TextEditingController _gwDevIdCtrl = TextEditingController();

  bool _gatewayAdded = false;
  String _gatewayName = "";
  bool _gatewayOnline = false;

  // Wait until app returns to RESUMED (helps after camera / native UI)
  Future<void> _waitUntilResumed() async {
    if (!mounted) return;

    final current = WidgetsBinding.instance.lifecycleState;
    if (current == AppLifecycleState.resumed) return;

    final completer = Completer<void>();
    late final _LifeObserver observer;

    observer = _LifeObserver((state) {
      if (state == AppLifecycleState.resumed && !completer.isCompleted) {
        WidgetsBinding.instance.removeObserver(observer);
        completer.complete();
      }
    });

    WidgetsBinding.instance.addObserver(observer);
    await completer.future;
  }

  @override
  void initState() {
    super.initState();
    // Load homes immediately
    Future.microtask(() {
      ref.read(homeHubControllerProvider.notifier).loadHomes(autoPickFirst: true);
    });
  }

  @override
  void dispose() {
    _gwDevIdCtrl.dispose();
    super.dispose();
  }

  // Brand colors extracted from your logo palette
  static const _navy = Color(0xFF1D254B);
  static const _blue = Color(0xFF5796CB);
  static const _green = Color(0xFF7AB863);
  static const _bg = Color(0xFFF5F7FB);

  @override
  Widget build(BuildContext context) {
    final hubState = ref.watch(homeHubControllerProvider);
    final busy = hubState.isLoading;

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

            // Quick actions row (Tuya-like)
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 14, 16, 0),
                child: Row(
                  children: [
                    Expanded(
                      child: _PrimaryActionCard(
                        title: "Add Device",
                        subtitle: "Tuya UI",
                        icon: Icons.add_circle_rounded,
                        color: _blue,
                        busy: busy,
                        onTap: () => _showAddSheet(
                          busy: busy,
                          onBizAddDevice: () => ref
                              .read(homeHubControllerProvider.notifier)
                              .openBizAddDevice(),
                          onBizQrScan: () => ref
                              .read(homeHubControllerProvider.notifier)
                              .openBizQrScan(),
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: _PrimaryActionCard(
                        title: "Scan QR",
                        subtitle: "Tuya Scanner",
                        icon: Icons.qr_code_scanner_rounded,
                        color: _green,
                        busy: busy,
                        onTap: () => ref
                            .read(homeHubControllerProvider.notifier)
                            .openBizQrScan(),
                      ),
                    ),
                  ],
                ),
              ),
            ),

            // My Home section
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 18, 16, 8),
                child: _sectionTitle("My Home"),
              ),
            ),

            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 10),
                child: _HubCard(
                  title: "Gateway / Hub",
                  subtitle: gatewaySubtitle,
                  busy: busy,
                  highlight: devId.isNotEmpty,
                  onTap: () => _showAddSheet(
                    busy: busy,
                    onBizAddDevice: () =>
                        ref.read(homeHubControllerProvider.notifier).openBizAddDevice(),
                    onBizQrScan: () =>
                        ref.read(homeHubControllerProvider.notifier).openBizQrScan(),
                  ),
                ),
              ),
            ),

            if (_gatewayAdded && devId.isNotEmpty)
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 10),
                  child: _StatusPill(
                    icon: Icons.check_circle,
                    text: "Gateway added successfully",
                    color: _green,
                  ),
                ),
              ),

            // Devices section (Tuya-like empty state)
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 10, 16, 8),
                child: _sectionTitle("Devices"),
              ),
            ),

            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
                child: _EmptyDevicesCard(
                  onAdd: busy
                      ? null
                      : () => _showAddSheet(
                            busy: busy,
                            onBizAddDevice: () => ref
                                .read(homeHubControllerProvider.notifier)
                                .openBizAddDevice(),
                            onBizQrScan: () => ref
                                .read(homeHubControllerProvider.notifier)
                                .openBizQrScan(),
                          ),
                ),
              ),
            ),

            const SliverToBoxAdapter(child: SizedBox(height: 24)),
          ],
        ),
      ),

      // Floating action similar to Tuya “+”
      floatingActionButton: FloatingActionButton(
        onPressed: busy
            ? null
            : () => _showAddSheet(
                  busy: busy,
                  onBizAddDevice: () =>
                      ref.read(homeHubControllerProvider.notifier).openBizAddDevice(),
                  onBizQrScan: () =>
                      ref.read(homeHubControllerProvider.notifier).openBizQrScan(),
                ),
        backgroundColor: _navy,
        foregroundColor: Colors.white,
        child: const Icon(Icons.add),
      ),
    );
  }

  Widget _sectionTitle(String text) {
    return Text(
      text,
      style: const TextStyle(
        fontWeight: FontWeight.w900,
        fontSize: 16,
        color: _navy,
      ),
    );
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

  Future<void> _showAddSheet({
    required bool busy,
    required Future<void> Function() onBizAddDevice,
    required Future<void> Function() onBizQrScan,
  }) async {
    if (busy) return;

    await showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: false,
      builder: (_) {
        return _AddDeviceSheet(
          navy: _navy,
          blue: _blue,
          green: _green,
          onAddDevice: () async {
            Navigator.of(context).pop();
            await onBizAddDevice();
            await _waitUntilResumed();
          },
          onScanQr: () async {
            Navigator.of(context).pop();
            await onBizQrScan();
            await _waitUntilResumed();
          },
        );
      },
    );
  }
}

class _TopHeader extends StatelessWidget {
  const _TopHeader({
    required this.title,
    required this.homes,
    required this.busy,
    required this.onRefresh,
    required this.onSelectHome,
    required this.onLogout,
  });

  final String title;
  final List<Map<String, dynamic>> homes;
  final bool busy;
  final VoidCallback onRefresh;
  final ValueChanged<int> onSelectHome;
  final VoidCallback onLogout;

  static const _navy = Color(0xFF1D254B);
  static const _blue = Color(0xFF5796CB);

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [_navy, _blue],
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 16),
        child: Column(
          children: [
            Row(
              children: [
                // Logo (safe: does not crash if asset missing)
                ClipRRect(
                  borderRadius: BorderRadius.circular(10),
                  child: Container(
                    width: 44,
                    height: 44,
                    color: Colors.white.withOpacity(0.12),
                    alignment: Alignment.center,
                    child: Image.asset(
                      "assets/images/alrawi_black.jpeg", // <-- Put your logo here (recommended)
                      fit: BoxFit.contain,
                      errorBuilder: (_, __, ___) {
                        return const Icon(Icons.home_rounded, color: Colors.white);
                      },
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontWeight: FontWeight.w900,
                      fontSize: 18,
                      color: Colors.white,
                      letterSpacing: 0.3,
                    ),
                  ),
                ),
                IconButton(
                  onPressed: busy ? null : onRefresh,
                  icon: const Icon(Icons.refresh_rounded),
                  color: Colors.white,
                ),
                IconButton(
                  onPressed: busy ? null : onLogout,
                  icon: const Icon(Icons.logout_rounded),
                  color: Colors.white,
                ),
              ],
            ),
            const SizedBox(height: 12),

            // Home selector pill (Tuya-like)
            Row(
              children: [
                Expanded(
                  child: _HomePickerPill(
                    homes: homes,
                    busy: busy,
                    onSelectHome: onSelectHome,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _HomePickerPill extends StatelessWidget {
  const _HomePickerPill({
    required this.homes,
    required this.busy,
    required this.onSelectHome,
  });

  final List<Map<String, dynamic>> homes;
  final bool busy;
  final ValueChanged<int> onSelectHome;

  @override
  Widget build(BuildContext context) {
    final canPick = homes.isNotEmpty && !busy;

    return Container(
      height: 46,
      padding: const EdgeInsets.symmetric(horizontal: 14),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.14),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.white.withOpacity(0.20)),
      ),
      child: Row(
        children: [
          const Icon(Icons.location_on_rounded, color: Colors.white),
          const SizedBox(width: 10),
          const Expanded(
            child: Text(
              "Homes",
              style: TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          if (homes.isNotEmpty)
            PopupMenuButton<int>(
              enabled: canPick,
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
              child: Row(
                children: [
                  Text(
                    canPick ? "Switch" : "Loading…",
                    style: const TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(width: 6),
                  const Icon(Icons.expand_more_rounded, color: Colors.white),
                ],
              ),
            )
          else
            const Text(
              "No homes",
              style: TextStyle(
                color: Colors.white70,
                fontWeight: FontWeight.w700,
              ),
            ),
        ],
      ),
    );
  }
}

class _PrimaryActionCard extends StatelessWidget {
  const _PrimaryActionCard({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.color,
    required this.onTap,
    required this.busy,
  });

  final String title;
  final String subtitle;
  final IconData icon;
  final Color color;
  final VoidCallback onTap;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: busy ? null : onTap,
      borderRadius: BorderRadius.circular(16),
      child: Ink(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: Colors.black12.withOpacity(0.06)),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.05),
              blurRadius: 18,
              offset: const Offset(0, 10),
            ),
          ],
        ),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 14, 14, 14),
          child: Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: color.withOpacity(0.12),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Icon(icon, color: color),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        fontWeight: FontWeight.w900,
                        fontSize: 14,
                        color: Color(0xFF1D254B),
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: TextStyle(
                        fontWeight: FontWeight.w700,
                        fontSize: 12,
                        color: Colors.black.withOpacity(0.55),
                      ),
                    ),
                  ],
                ),
              ),
              if (busy)
                const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              else
                const Icon(Icons.chevron_right_rounded, color: Colors.black38),
            ],
          ),
        ),
      ),
    );
  }
}

class _HubCard extends StatelessWidget {
  const _HubCard({
    required this.title,
    required this.subtitle,
    required this.busy,
    required this.onTap,
    required this.highlight,
  });

  final String title;
  final String subtitle;
  final bool busy;
  final VoidCallback onTap;
  final bool highlight;

  static const _navy = Color(0xFF1D254B);

  @override
  Widget build(BuildContext context) {
    final border = highlight ? const Color(0x337AB863) : Colors.black12.withOpacity(0.08);

    return InkWell(
      onTap: busy ? null : onTap,
      borderRadius: BorderRadius.circular(18),
      child: Ink(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: border),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.05),
              blurRadius: 18,
              offset: const Offset(0, 10),
            ),
          ],
        ),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 14, 14, 14),
          child: Row(
            children: [
              Container(
                width: 46,
                height: 46,
                decoration: BoxDecoration(
                  color: _navy.withOpacity(0.08),
                  borderRadius: BorderRadius.circular(16),
                ),
                child: const Icon(Icons.hub_rounded, color: _navy),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        fontWeight: FontWeight.w900,
                        fontSize: 14,
                        color: _navy,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      subtitle,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontWeight: FontWeight.w700,
                        fontSize: 12,
                        color: Colors.black.withOpacity(0.55),
                        height: 1.2,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 10),
              busy
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.chevron_right_rounded, color: Colors.black38),
            ],
          ),
        ),
      ),
    );
  }
}

class _EmptyDevicesCard extends StatelessWidget {
  const _EmptyDevicesCard({required this.onAdd});
  final VoidCallback? onAdd;

  static const _navy = Color(0xFF1D254B);

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: Colors.black12.withOpacity(0.08)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 18,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: Column(
        children: [
          Icon(Icons.devices_other_rounded, size: 44, color: _navy.withOpacity(0.50)),
          const SizedBox(height: 10),
          const Text(
            "No devices yet",
            style: TextStyle(
              fontWeight: FontWeight.w900,
              fontSize: 14,
              color: _navy,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            "Add your gateway, then pair Zigbee devices from the same Tuya UI.",
            textAlign: TextAlign.center,
            style: TextStyle(
              fontWeight: FontWeight.w700,
              fontSize: 12,
              color: Colors.black.withOpacity(0.55),
              height: 1.25,
            ),
          ),
          const SizedBox(height: 14),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: onAdd,
              icon: const Icon(Icons.add),
              label: const Text("Add Device"),
              style: ElevatedButton.styleFrom(
                backgroundColor: _navy,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 12),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _StatusPill extends StatelessWidget {
  const _StatusPill({
    required this.icon,
    required this.text,
    required this.color,
  });

  final IconData icon;
  final String text;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: color.withOpacity(0.12),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withOpacity(0.22)),
      ),
      child: Row(
        children: [
          Icon(icon, color: color),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              text,
              style: TextStyle(
                fontWeight: FontWeight.w900,
                color: Colors.black.withOpacity(0.75),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _AddDeviceSheet extends StatelessWidget {
  const _AddDeviceSheet({
    required this.navy,
    required this.blue,
    required this.green,
    required this.onAddDevice,
    required this.onScanQr,
  });

  final Color navy;
  final Color blue;
  final Color green;
  final Future<void> Function() onAddDevice;
  final Future<void> Function() onScanQr;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(22),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.18),
              blurRadius: 30,
              offset: const Offset(0, 18),
            ),
          ],
        ),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 42,
                height: 4,
                decoration: BoxDecoration(
                  color: Colors.black12,
                  borderRadius: BorderRadius.circular(999),
                ),
              ),
              const SizedBox(height: 12),
              const Text(
                "Add device",
                style: TextStyle(
                  fontWeight: FontWeight.w900,
                  fontSize: 16,
                  color: Color(0xFF1D254B),
                ),
              ),
              const SizedBox(height: 6),
              Text(
                "Choose how you want to pair.",
                style: TextStyle(
                  fontWeight: FontWeight.w700,
                  fontSize: 12,
                  color: Colors.black.withOpacity(0.55),
                ),
              ),
              const SizedBox(height: 14),
              _sheetButton(
                context,
                icon: Icons.add_circle_rounded,
                title: "Add Device (Tuya UI)",
                subtitle: "Wi-Fi / Zigbee / BLE flows",
                color: blue,
                onTap: onAddDevice,
              ),
              const SizedBox(height: 10),
              _sheetButton(
                context,
                icon: Icons.qr_code_scanner_rounded,
                title: "Scan QR (Tuya UI)",
                subtitle: "Use the native QR scanner",
                color: green,
                onTap: onScanQr,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _sheetButton(
    BuildContext context, {
    required IconData icon,
    required String title,
    required String subtitle,
    required Color color,
    required Future<void> Function() onTap,
  }) {
    return InkWell(
      onTap: () => onTap(),
      borderRadius: BorderRadius.circular(16),
      child: Ink(
        decoration: BoxDecoration(
          color: color.withOpacity(0.10),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: color.withOpacity(0.20)),
        ),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 14, 14, 14),
          child: Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: Colors.white.withOpacity(0.85),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Icon(icon, color: color),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        fontWeight: FontWeight.w900,
                        fontSize: 14,
                        color: Color(0xFF1D254B),
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: TextStyle(
                        fontWeight: FontWeight.w700,
                        fontSize: 12,
                        color: Colors.black.withOpacity(0.55),
                      ),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right_rounded, color: Colors.black38),
            ],
          ),
        ),
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