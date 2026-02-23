import 'package:alrawi_app/tuya/bizbundle/tuya_bizbundle_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../tuya_platform.dart';

final homeHubControllerProvider =
    AsyncNotifierProvider<HomeHubController, HomeHubState>(
  HomeHubController.new,
);

class HomeHubController extends AsyncNotifier<HomeHubState> {
  late final TuyaBizbundleRepository _bizRepo;

  @override
  Future<HomeHubState> build() async {
    _bizRepo = const TuyaBizbundleRepository();
    // Load homes on first build.
    return _loadHomes(autoPickFirst: true);
  }

  Future<HomeHubState> _loadHomes({required bool autoPickFirst}) async {
    final homes = await TuyaPlatform.getHomeList();

    int? selectedId;
    String selectedName = "AL RAWI";

    if (homes.isNotEmpty) {
      final first = homes.first;
      selectedId = (first["homeId"] as num?)?.toInt();
      selectedName = (first["name"] ?? "Home").toString();
    }

    if (autoPickFirst && selectedId == null) {
      final ensured = await TuyaPlatform.ensureHome(
        name: "My Home",
        geoName: "Oman",
        rooms: const ["Living Room"],
      );
      selectedId = (ensured["homeId"] as num?)?.toInt();
      selectedName = (ensured["name"] ?? "My Home").toString();
    }

    return HomeHubState(
      homes: homes,
      selectedHomeId: selectedId,
      selectedHomeName: selectedName,
    );
  }

  Future<void> loadHomes({required bool autoPickFirst}) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      return _loadHomes(autoPickFirst: autoPickFirst);
    });
  }

  void selectHome(int id, String name) {
    final current = state.value ?? HomeHubState.empty;
    state = AsyncData(
      current.copyWith(selectedHomeId: id, selectedHomeName: name),
    );
  }

  Future<int> ensureHomeId() async {
    final current = state.value;
    final cached = current?.selectedHomeId;
    if (cached != null && cached > 0) return cached;

    final homes = await TuyaPlatform.getHomeList();
    if (homes.isNotEmpty) {
      final hid = (homes.first["homeId"] as num?)?.toInt() ?? 0;
      if (hid > 0) return hid;
    }

    final ensured = await TuyaPlatform.ensureHome(
      name: "My Home",
      geoName: "Oman",
      rooms: const ["Living Room"],
    );

    final hid = (ensured["homeId"] as num?)?.toInt() ?? 0;
    if (hid <= 0) throw Exception("Failed to ensure homeId");
    return hid;
  }

  Future<void> openBizAddDevice({int? homeId}) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final hid = homeId ?? await ensureHomeId();
      await _bizRepo.openAddDevice(homeId: hid);
      // Keep state (don’t lose selected home)
      return this.state.value ?? HomeHubState.empty;
    });
  }

  Future<void> openBizQrScan({int? homeId}) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final hid = homeId ?? await ensureHomeId();
      await _bizRepo.openQrScan(homeId: hid);
      return this.state.value ?? HomeHubState.empty;
    });
  }
}

class HomeHubState {
  final List<Map<String, dynamic>> homes;
  final int? selectedHomeId;
  final String selectedHomeName;

  const HomeHubState({
    required this.homes,
    required this.selectedHomeId,
    required this.selectedHomeName,
  });

  static const empty = HomeHubState(
    homes: [],
    selectedHomeId: null,
    selectedHomeName: "AL RAWI",
  );

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
}