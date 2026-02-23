import '../tuya_platform.dart';

class TuyaBizbundleRepository {
  const TuyaBizbundleRepository();

  Future<void> openAddDevice({required int homeId}) {
    return TuyaPlatform.bizOpenAddDevice(homeId: homeId);
  }

  Future<void> openQrScan({required int homeId}) {
    return TuyaPlatform.bizOpenQrScan(homeId: homeId);
  }
}