import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../tuya_platform.dart';

/// Holds selected home state + list for the HomeHub screen.
class HomeHubState {
  final List<Map<String, dynamic>> homes;
  final int? selectedHomeId;
  final String selectedHomeName;

  const HomeHubState({
    required this.homes,
    required this.selectedHomeId,
    required this.selectedHomeName,
  });

  HomeHubState copyWith({
    List<Map<String, dynamic>>? homes,
    int? selectedHomeId,
    String? selectedHomeName,
  }) {
    return HomeHubState(
      homes: homes ?? this.homes,
      selectedHomeId: selectedHomeId ?? this.selectedHomeId,
      selectedHomeName: selectedHomeName ?? this.selectedHomeName,
    );
  }

  static const empty = HomeHubState(
    homes: [],
    selectedHomeId: null,
    selectedHomeName: "Home",
  );
}

final homeHubControllerProvider =
    NotifierProvider<HomeHubController, AsyncValue<HomeHubState>>(
  HomeHubController.new,
);

class HomeHubController extends Notifier<AsyncValue<HomeHubState>> {
  @override
  AsyncValue<HomeHubState> build() {
    // Start empty; UI can call loadHomes() in initState
    return const AsyncData(HomeHubState.empty);
  }

  HomeHubState get _value => state.value ?? HomeHubState.empty;

  Future<void> loadHomes({bool autoPickFirst = true}) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final homes = await TuyaPlatform.getHomeList();

      var selectedId = _value.selectedHomeId;
      var selectedName = _value.selectedHomeName;

      if (autoPickFirst && (selectedId == null || selectedId <= 0) && homes.isNotEmpty) {
        final first = homes.first;
        selectedId = (first["homeId"] as num?)?.toInt();
        selectedName = (first["name"] ?? "Home").toString();
      } else if (selectedId != null && homes.isNotEmpty) {
        // Keep selection if still exists
        final match = homes.where((h) => (h["homeId"] as num).toInt() == selectedId).toList();
        if (match.isEmpty && autoPickFirst) {
          final first = homes.first;
          selectedId = (first["homeId"] as num?)?.toInt();
          selectedName = (first["name"] ?? "Home").toString();
        }
      }

      return HomeHubState(
        homes: homes,
        selectedHomeId: selectedId,
        selectedHomeName: selectedName,
      );
    });
  }

  void selectHome(int homeId, String homeName) {
    final current = _value;
    state = AsyncData(
      current.copyWith(
        selectedHomeId: homeId,
        selectedHomeName: homeName,
      ),
    );
  }

  Future<int> ensureHomeId() async {
    final current = _value;

    final hid = current.selectedHomeId;
    if (hid != null && hid > 0) return hid;

    // Ensure home if missing
    final info = await TuyaPlatform.ensureHome(
      name: "My Home",
      geoName: "Oman",
      rooms: const ["Living Room"],
    );

    final ensured = (info["homeId"] as num?)?.toInt() ?? 0;
    if (ensured <= 0) {
      throw Exception("Failed to ensure homeId.");
    }

    final name = (info["name"] ?? "Home").toString();
    state = AsyncData(
      current.copyWith(selectedHomeId: ensured, selectedHomeName: name),
    );

    return ensured;
  }

  /// ✅ Production: open full Tuya BizBundle Add Device UI
  Future<void> openBizAddDevice() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final hid = await ensureHomeId();
      await TuyaPlatform.bizOpenAddDevice(homeId: hid);
      return _value;
    });
  }

  /// ✅ Production: open native Tuya QR scan UI (BizBundle)
  Future<void> openBizQrScan() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      await TuyaPlatform.bizOpenQrScan();
      return _value;
    });
  }
}
