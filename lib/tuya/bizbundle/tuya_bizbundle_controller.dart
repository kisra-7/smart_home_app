import 'package:alrawi_app/tuya/tuya_platform.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'tuya_bizbundle_repository.dart';

final tuyaBizbundleRepositoryProvider = Provider<TuyaBizbundleRepository>(
  (ref) => const TuyaBizbundleRepository(),
);

final tuyaBizbundleControllerProvider =
    NotifierProvider<TuyaBizbundleController, AsyncValue<void>>(
  TuyaBizbundleController.new,
);

class TuyaBizbundleController extends Notifier<AsyncValue<void>> {
  late final TuyaBizbundleRepository _repo;

  @override
  AsyncValue<void> build() {
    _repo = ref.read(tuyaBizbundleRepositoryProvider);
    return const AsyncData(null);
  }

  Future<int> _ensureHomeId() async {
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

  Future<void> openAddDevice({int? homeId}) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final hid = homeId ?? await _ensureHomeId();
      await _repo.openAddDevice(homeId: hid);
    });
  }

  Future<void> openQrScan({int? homeId}) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final hid = homeId ?? await _ensureHomeId();
      await _repo.openQrScan(homeId: hid);
    });
  }
}