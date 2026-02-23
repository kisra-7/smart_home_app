
import 'package:alrawi_app/tuya/models/things_device.dart';

import '../tuya_platform.dart';

class TuyaHomeRepository {
  Future<List<Map<String, dynamic>>> getHomes() => TuyaPlatform.getHomeList();

  Future<int> ensureHomeId() async {
    final home = await TuyaPlatform.ensureHome();
    return (home['homeId'] as num).toInt();
  }

  Future<List<ThingDevice>> getDevices(int homeId) async {
    final res = await TuyaPlatform.getHomeDevices(homeId: homeId);
    final list = (res['devices'] as List?) ?? const [];
    return list.map((e) => ThingDevice.fromMap(Map<String, dynamic>.from(e as Map))).toList();
  }
}