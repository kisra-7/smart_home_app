import '../tuya_platform.dart';

class TuyaHome {
  final int homeId;
  final String name;
  final String geoName;

  const TuyaHome({
    required this.homeId,
    required this.name,
    required this.geoName,
  });

  factory TuyaHome.fromMap(Map<dynamic, dynamic> map) {
    return TuyaHome(
      homeId: (map['homeId'] as num).toInt(),
      name: (map['name'] ?? '').toString(),
      geoName: (map['geoName'] ?? '').toString(),
    );
  }
}

class TuyaHomeRepository {
  Future<List<TuyaHome>> getHomes() async {
    final list = await TuyaPlatform.getHomeList();
    return list.map((e) => TuyaHome.fromMap(e)).toList();
    // TuyaPlatform.getHomeList() must return List<Map>
  }

  /// Ensures there is at least one home, and returns the ensured home.
  /// NOTE: Native ensureHome returns a Map {homeId, name, geoName}
  Future<TuyaHome> ensureHome({
    required String name,
    required String geoName,
    required List<String> rooms,
  }) async {
    final map = await TuyaPlatform.ensureHome(
      name: name,
      geoName: geoName,
      rooms: rooms,
    );
    return TuyaHome.fromMap(map);
  }
}