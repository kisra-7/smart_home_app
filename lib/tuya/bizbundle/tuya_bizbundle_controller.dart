import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../tuya_platform.dart';

final tuyaBizBundleControllerProvider =
    NotifierProvider<TuyaBizBundleController, TuyaBizBundleState>(
  TuyaBizBundleController.new,
);

class TuyaBizBundleController extends Notifier<TuyaBizBundleState> {
  @override
  TuyaBizBundleState build() => const TuyaBizBundleState();

  Future<void> ensureBizContext(int homeId) async {
    state = state.copyWith(loading: true, error: null);
    try {
      await TuyaPlatform.ensureBizContext(homeId: homeId);
      state = state.copyWith(loading: false);
    } catch (e) {
      state = state.copyWith(loading: false, error: e.toString());
      rethrow;
    }
  }

  Future<void> openAddDevice(int homeId) async {
    await ensureBizContext(homeId);
    await TuyaPlatform.bizOpenAddDevice(homeId: homeId);
  }

  Future<void> openQrScan(int homeId) async {
    await ensureBizContext(homeId);
    await TuyaPlatform.bizOpenQrScan(homeId: homeId);
  }
}

class TuyaBizBundleState {
  final bool loading;
  final String? error;

  const TuyaBizBundleState({
    this.loading = false,
    this.error,
  });

  TuyaBizBundleState copyWith({
    bool? loading,
    String? error,
  }) {
    return TuyaBizBundleState(
      loading: loading ?? this.loading,
      error: error,
    );
  }
}