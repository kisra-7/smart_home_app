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

  Future<void> openAddDevice({required int homeId}) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() => _repo.openAddDevice(homeId: homeId));
  }

  Future<void> openQrScan() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(_repo.openQrScan);
  }
}
