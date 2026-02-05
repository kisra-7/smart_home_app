import 'package:flutter/services.dart';

class TuyaPlatform {
  static const MethodChannel _channel = MethodChannel('tuya_config');

  static Future<void> initSdk() async {
    await _channel.invokeMethod('initSdk');
  }

  static Future<bool> isLoggedIn() async {
    final res = await _channel.invokeMethod('isLoggedIn');
    return res == true;
  }

  static Future<Map<String, dynamic>?> loginByEmail({
    required String countryCode,
    required String email,
    required String password,
  }) async {
    final res = await _channel.invokeMethod('loginByEmail', {
      'countryCode': countryCode.trim(),
      'email': email.trim(),
      'password': password,
    });
    if (res == null) return null;
    return Map<String, dynamic>.from(res as Map);
  }

  static Future<Map<String, dynamic>?> loginEmail({
    required String countryCode,
    required String email,
    required String password,
  }) {
    return loginByEmail(
      countryCode: countryCode,
      email: email,
      password: password,
    );
  }

  static Future<void> sendEmailCode({
    required String countryCode,
    required String email,
    int type = 1,
  }) async {
    await _channel.invokeMethod('sendEmailCode', {
      'countryCode': countryCode.trim(),
      'email': email.trim(),
      'type': type,
    });
  }

  static Future<Map<String, dynamic>?> registerEmail({
    required String countryCode,
    required String email,
    required String password,
    required String code,
  }) async {
    final res = await _channel.invokeMethod('registerEmail', {
      'countryCode': countryCode.trim(),
      'email': email.trim(),
      'password': password,
      'code': code.trim(),
    });
    if (res == null) return null;
    return Map<String, dynamic>.from(res as Map);
  }

  static Future<void> logout() async {
    await _channel.invokeMethod('logout');
  }

  static Future<List<Map<String, dynamic>>> getHomeList() async {
    final res = await _channel.invokeMethod('getHomeList');
    final list = (res as List).cast<dynamic>();
    return list.map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  static Future<Map<String, dynamic>> createHome({
    required String name,
    String geoName = "",
    List<String> rooms = const [],
  }) async {
    final res = await _channel.invokeMethod('createHome', {
      'name': name,
      'geoName': geoName,
      'rooms': rooms,
    });
    return Map<String, dynamic>.from(res as Map);
  }

  // ✅ New: Smart Life style “Add Device” flow (no args)
  static Future<void> openAddGateway() async {
    await _channel.invokeMethod('openAddGateway');
  }

  // ✅ Backward compatible alias (so your old code won’t break)
  static Future<void> openAddGatewayWithHome({required int homeId}) async {
    // Android ignores homeId (Smart Life UI handles it itself)
    await _channel.invokeMethod('openAddGateway', {'homeId': homeId});
  }

  // ✅ QR scan UI
  static Future<void> openQrScan() async {
    await _channel.invokeMethod('openQrScan');
  }

  // ✅ Keep your old zigbee method signature to avoid UI errors
  static Future<void> openAddZigbeeSubDevice({
    required int homeId,
    required String gwId,
  }) async {
    // Android will open the same “Add Device” UI flow
    await _channel.invokeMethod('openAddZigbeeSubDevice', {
      'homeId': homeId,
      'gwId': gwId,
    });
  }

  static Future<void> stopActivator() async {
    await _channel.invokeMethod('stopActivator');
  }
}
