import 'package:flutter/services.dart';

class TuyaPlatform {
  static const MethodChannel _channel = MethodChannel('tuya_config');

  /// Must be called once before any Tuya call.
  static Future<void> initSdk() async {
    await _channel.invokeMethod('initSdk');
  }

  static Future<bool> isLoggedIn() async {
    final res = await _channel.invokeMethod('isLoggedIn');
    return res == true;
  }

  /// ✅ Login with email (Tuya)
  ///
  /// IMPORTANT:
  /// - method name MUST match Android: "loginByEmail"
  /// - countryCode is numeric without '+' (e.g. "968")
  static Future<void> loginByEmail({
    required String countryCode,
    required String email,
    required String password,
  }) async {
    await _channel.invokeMethod('loginByEmail', {
      'countryCode': countryCode.trim(),
      'email': email.trim(),
      'password': password,
    });
  }

  /// Backward compatible alias:
  /// If some parts of your UI still call loginEmail(), this keeps them working.
  static Future<void> loginEmail({
    required String countryCode,
    required String email,
    required String password,
  }) async {
    return loginByEmail(
      countryCode: countryCode,
      email: email,
      password: password,
    );
  }

  /// ✅ Request email verification code (needed before registerEmail)
  ///
  /// type values vary by Tuya SDK, common patterns:
  /// - 1 = register
  /// - 2 = login / reset (depends on SDK)
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

  /// ✅ Register account (Tuya / ThingClips)
  static Future<void> registerEmail({
    required String countryCode,
    required String email,
    required String password,
    required String code,
  }) async {
    await _channel.invokeMethod('registerEmail', {
      'countryCode': countryCode.trim(),
      'email': email.trim(),
      'password': password,
      'code': code.trim(),
    });
  }

  static Future<void> logout() async {
    await _channel.invokeMethod('logout');
  }

  /// Returns: List<Map> like [{"homeId": 123, "name": "Home"}]
  static Future<List<Map<String, dynamic>>> getHomeList() async {
    final res = await _channel.invokeMethod('getHomeList');
    final list = (res as List).cast<dynamic>();

    return list.map((e) {
      final map = Map<String, dynamic>.from(e as Map);
      return map;
    }).toList();
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

  /// Opens Tuya Activator UI (BizBundle) to add a gateway/device
  static Future<void> openAddGateway({required int homeId}) async {
    await _channel.invokeMethod('openAddGateway', {'homeId': homeId});
  }

  /// Opens activator UI for zigbee sub-devices (many bundles use same UI flow)
  static Future<void> openAddZigbeeSubDevice({
    required int homeId,
    required String gwId,
  }) async {
    await _channel.invokeMethod('openAddZigbeeSubDevice', {
      'homeId': homeId,
      'gwId': gwId,
    });
  }

  static Future<void> stopActivator() async {
    await _channel.invokeMethod('stopActivator');
  }
}
