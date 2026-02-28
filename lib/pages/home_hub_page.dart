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
  @override
  void initState() {
    super.initState();
    Future.microtask(() async {
      await ref.read(homeHubControllerProvider.notifier).loadHomes(autoPickFirst: true);

      final st = ref.read(homeHubControllerProvider).value;
      if (st == null || st.homes.isEmpty) {
        await ref.read(homeHubControllerProvider.notifier).ensureHomeIfNeeded();
        await ref.read(homeHubControllerProvider.notifier).loadHomes(autoPickFirst: true);
      }
    });
  }

  Future<void> _logout() async {
    await TuyaPlatform.logout();
    if (!mounted) return;
    Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const AuthPage()));
  }

  @override
  Widget build(BuildContext context) {
    final hub = ref.watch(homeHubControllerProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text("Home Hub"),
        actions: [
          IconButton(
            onPressed: _logout,
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: hub.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text("Error: $e")),
        data: (data) {
          final homes = data.homes;
          final selectedId = data.selectedHomeId;

          return Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                if (homes.isNotEmpty)
                  DropdownButton<int>(
                    value: selectedId,
                    isExpanded: true,
                    items: homes
                        .map(
                          (h) => DropdownMenuItem(
                            value: h.homeId,
                            child: Text("${h.name} (ID: ${h.homeId})"),
                          ),
                        )
                        .toList(),
                    onChanged: (id) async {
                      if (id == null) return;
                      final home = homes.firstWhere((h) => h.homeId == id);
                      await ref.read(homeHubControllerProvider.notifier).selectHome(home);
                    },
                  )
                else
                  const Text("No homes found."),

                const SizedBox(height: 16),

                Row(
                  children: [
                    Expanded(
                      child: ElevatedButton(
                        onPressed: selectedId == null
                            ? null
                            : () async {
                                await ref.read(homeHubControllerProvider.notifier).openBizAddDevice();
                              },
                        child: const Text("Add Device (Tuya UI)"),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: ElevatedButton(
                        onPressed: selectedId == null
                            ? null
                            : () async {
                                await ref.read(homeHubControllerProvider.notifier).openBizQrScan();
                              },
                        child: const Text("Scan QR (Tuya UI)"),
                      ),
                    ),
                  ],
                ),

                const SizedBox(height: 16),

                const Text(
                  "Tip: For gateway QR binding, scan from Tuya UI and follow the pairing flow.",
                  textAlign: TextAlign.center,
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}