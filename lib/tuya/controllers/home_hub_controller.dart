import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../repositories/tuya_home_repository.dart';
import 'tuya_bizbundle_controller.dart';

final homeHubControllerProvider =
    AsyncNotifierProvider<HomeHubController, HomeHubState>(
  HomeHubController.new,
);

class HomeHubController extends AsyncNotifier<HomeHubState> {
  final TuyaHomeRepository _homeRepo = TuyaHomeRepository();

  @override
  Future<HomeHubState> build() async {
    return const HomeHubState(
      homes: [],
      selectedHomeId: null,
      selectedHomeName: '',
    );
  }

  Future<void> ensureHomeIfNeeded() async {
    // Use your real defaults (same as your old flow)
    final ensured = await _homeRepo.ensureHome(
      name: "My Home",
      geoName: "Oman",
      rooms: const ["Living Room", "Bedroom"],
    );

    final prev = state.value ?? const HomeHubState(homes: [], selectedHomeId: null, selectedHomeName: '');
    state = AsyncData(
      prev.copyWith(
        selectedHomeId: ensured.homeId,
        selectedHomeName: ensured.name,
      ),
    );

    // Enforce Biz context immediately
    await ref.read(tuyaBizBundleControllerProvider.notifier).ensureBizContext(ensured.homeId);
  }

  Future<void> loadHomes({bool autoPickFirst = true}) async {
    state = const AsyncLoading();

    state = await AsyncValue.guard(() async {
      final homes = await _homeRepo.getHomes();
      HomeHubState next = (state.value ?? const HomeHubState(homes: [], selectedHomeId: null, selectedHomeName: ''))
          .copyWith(homes: homes);

      if (homes.isEmpty) {
        // No homes yet: keep state and let caller ensureHomeIfNeeded()
        return next.copyWith(selectedHomeId: null, selectedHomeName: '');
      }

      // Keep previous selection if exists
      final prevSelected = state.value?.selectedHomeId;
      final hasPrev = prevSelected != null && homes.any((h) => h.homeId == prevSelected);

      if (hasPrev) {
        final chosen = homes.firstWhere((h) => h.homeId == prevSelected);
        next = next.copyWith(selectedHomeId: chosen.homeId, selectedHomeName: chosen.name);
      } else if (autoPickFirst) {
        final first = homes.first;
        next = next.copyWith(selectedHomeId: first.homeId, selectedHomeName: first.name);
      }

      // Enforce Biz current home context if we have one
      final homeId = next.selectedHomeId;
      if (homeId != null) {
        await ref.read(tuyaBizBundleControllerProvider.notifier).ensureBizContext(homeId);
      }

      return next;
    });
  }

  Future<void> selectHome(TuyaHome home) async {
    final prev = state.value ?? const HomeHubState(homes: [], selectedHomeId: null, selectedHomeName: '');
    state = AsyncData(
      prev.copyWith(
        selectedHomeId: home.homeId,
        selectedHomeName: home.name,
      ),
    );

    await ref.read(tuyaBizBundleControllerProvider.notifier).ensureBizContext(home.homeId);
  }

  Future<void> openBizAddDevice() async {
    final homeId = state.value?.selectedHomeId;
    if (homeId == null) return;
    await ref.read(tuyaBizBundleControllerProvider.notifier).openAddDevice(homeId);
  }

  Future<void> openBizQrScan() async {
    final homeId = state.value?.selectedHomeId;
    if (homeId == null) return;
    await ref.read(tuyaBizBundleControllerProvider.notifier).openQrScan(homeId);
  }
}

class HomeHubState {
  final List<TuyaHome> homes;
  final int? selectedHomeId;
  final String selectedHomeName;

  const HomeHubState({
    required this.homes,
    required this.selectedHomeId,
    required this.selectedHomeName,
  });

  HomeHubState copyWith({
    List<TuyaHome>? homes,
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