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

  static Future<bool> loginByEmail({
    required String countryCode,
    required String email,
    required String password,
  }) async {
    final res = await _channel.invokeMethod('loginByEmail', {
      'countryCode': countryCode.trim(),
      'email': email.trim(),
      'password': password,
    });
    return res == true;
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

  static Future<bool> registerEmail({
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
    return res == true;
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
    String geoName = "Oman",
    List<String> rooms = const ["Living Room"],
  }) async {
    final res = await _channel.invokeMethod('createHome', {
      'name': name,
      'geoName': geoName,
      'rooms': rooms,
    });
    return Map<String, dynamic>.from(res as Map);
  }

  /// ✅ Ensure the logged-in user always has at least one home.
  /// Returns: { homeId, name, created: true/false }
  static Future<Map<String, dynamic>> ensureHome({
    String name = "My Home",
    String geoName = "Oman",
    List<String> rooms = const ["Living Room"],
  }) async {
    final res = await _channel.invokeMethod('ensureHome', {
      'name': name,
      'geoName': geoName,
      'rooms': rooms,
    });
    return Map<String, dynamic>.from(res as Map);
  }

  /// ✅ Open Add Device / Gateway flow (requires homeId).
  static Future<void> openAddGateway({required int homeId}) async {
    await _channel.invokeMethod('openAddGateway', {'homeId': homeId});
  }

  /// ✅ Open QR scan flow (also tied to a home).
  static Future<void> openQrScan({required int homeId}) async {
    await _channel.invokeMethod('openQrScan', {'homeId': homeId});
  }

  static Future<void> stopActivator() async {
    await _channel.invokeMethod('stopActivator');
  }
}
