import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../tuya_platform.dart';

final homeHubControllerProvider =
    NotifierProvider<HomeHubController, HomeHubState>(HomeHubController.new);

class HomeHubState {
  final bool busy;
  final int currentHomeId;
  final List<HomeSummary> homes;
  final String? error;

  const HomeHubState({
    this.busy = false,
    this.currentHomeId = 0,
    this.homes = const [],
    this.error,
  });

  HomeHubState copyWith({
    bool? busy,
    int? currentHomeId,
    List<HomeSummary>? homes,
    String? error,
  }) {
    return HomeHubState(
      busy: busy ?? this.busy,
      currentHomeId: currentHomeId ?? this.currentHomeId,
      homes: homes ?? this.homes,
      error: error,
    );
  }
}

class HomeSummary {
  final int homeId;
  final String name;
  final String geoName;

  const HomeSummary({
    required this.homeId,
    required this.name,
    required this.geoName,
  });

  factory HomeSummary.fromMap(Map map) {
    return HomeSummary(
      homeId: (map['homeId'] as num?)?.toInt() ?? 0,
      name: (map['name'] ?? '').toString(),
      geoName: (map['geoName'] ?? '').toString(),
    );
  }
}

class HomeHubController extends Notifier<HomeHubState> {
  static const _native = MethodChannel('tuya_bridge');

  @override
  HomeHubState build() => const HomeHubState();

  Future<void> loadHomes({bool autoPickFirst = false}) async {
    state = state.copyWith(busy: true, error: null);
    try {
      final list = await TuyaPlatform.getHomeList();

      final homes = (list as List)
          .whereType<Map>()
          .map(HomeSummary.fromMap)
          .where((h) => h.homeId > 0)
          .toList();

      int homeId = state.currentHomeId;
      if (autoPickFirst && homeId <= 0 && homes.isNotEmpty) {
        homeId = homes.first.homeId;
      }

      state = state.copyWith(
        busy: false,
        homes: homes,
        currentHomeId: homeId,
        error: null,
      );

      // If we now have a homeId, set it on native to bootstrap BizBundle context.
      if (homeId > 0) {
        await setCurrentHome(homeId);
      }
    } catch (e) {
      state = state.copyWith(busy: false, error: e.toString());
    }
  }

  /// Ensures there is a valid homeId:
  /// - if existing currentHomeId is valid => return it
  /// - else try home list
  /// - else ensureHome (create default)
  Future<int> ensureHomeId() async {
    if (state.currentHomeId > 0) return state.currentHomeId;

    // try to load homes
    try {
      final list = await TuyaPlatform.getHomeList();
      final homes = (list as List)
          .whereType<Map>()
          .map(HomeSummary.fromMap)
          .where((h) => h.homeId > 0)
          .toList();

      if (homes.isNotEmpty) {
        final id = homes.first.homeId;
        state = state.copyWith(homes: homes, currentHomeId: id, error: null);
        await setCurrentHome(id);
        return id;
      }
    } catch (_) {
      // ignore, fallback to ensureHome
    }

    // fallback: ensureHome creates or returns a default home
    final home = await TuyaPlatform.ensureHome(
      name: "My Home",
      geoName: "Oman",
      rooms: const ["Living Room"],
    );

    final homeId =
        (home is Map && home['homeId'] != null) ? (home['homeId'] as num).toInt() : 0;

    if (homeId <= 0) {
      throw StateError("ensureHome returned invalid homeId: $home");
    }

    state = state.copyWith(currentHomeId: homeId, error: null);
    await setCurrentHome(homeId);
    return homeId;
  }

  /// ✅ Calls native `setCurrentHome` to fix BizBundle QR context (gid/token/relationId).
  Future<void> setCurrentHome(int homeId) async {
    if (homeId <= 0) return;
    try {
      await _native.invokeMethod('setCurrentHome', {'homeId': homeId});
    } catch (e) {
      // Don’t block the app — but keep the error for debugging.
      state = state.copyWith(error: "setCurrentHome failed: $e");
    }
  }

  /// Use this if you want a manual home switch later.
  Future<void> selectHome(int homeId) async {
    if (homeId <= 0) return;
    state = state.copyWith(currentHomeId: homeId, error: null);
    await setCurrentHome(homeId);
  }
}