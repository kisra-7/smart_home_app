import 'package:flutter/services.dart';

class TuyaPlatform {
  static const MethodChannel _channel = MethodChannel('tuya_config');

<<<<<<< HEAD
  /// Must be called once before any Tuya call.
=======
>>>>>>> cc30e20 (fixed gradle problems)
  static Future<void> initSdk() async {
    await _channel.invokeMethod('initSdk');
  }

  static Future<bool> isLoggedIn() async {
    final res = await _channel.invokeMethod('isLoggedIn');
    return res == true;
  }

<<<<<<< HEAD
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
  /// If some parts of your UI still call loginEmail(), this keeps them working,
  /// but internally it calls the correct native method name.
=======
>>>>>>> cc30e20 (fixed gradle problems)
  static Future<void> loginEmail({
    required String countryCode,
    required String email,
    required String password,
  }) async {
<<<<<<< HEAD
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
  ///
  /// If your Android side uses a different name/params, match it there too.
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

  /// ✅ Register account inside your appKey user system
  ///
  /// Most Tuya flows require a verification code.
  static Future<void> registerEmail({
    required String countryCode,
    required String email,
    required String password,
  }) async {
    await _channel.invokeMethod('registerEmail', {
      'countryCode': countryCode.trim(),
      'email': email.trim(),
=======
    await _channel.invokeMethod('loginEmail', {
      'countryCode': countryCode,
      'email': email,
>>>>>>> cc30e20 (fixed gradle problems)
      'password': password,
    });
  }

  static Future<void> logout() async {
    await _channel.invokeMethod('logout');
  }

<<<<<<< HEAD
  /// Returns: List<Map> like [{"homeId": 123, "name": "Home"}]
=======
>>>>>>> cc30e20 (fixed gradle problems)
  static Future<List<Map<String, dynamic>>> getHomeList() async {
    final res = await _channel.invokeMethod('getHomeList');
    final list = (res as List).cast<Map>();
    return list.map((e) => e.cast<String, dynamic>()).toList();
  }
<<<<<<< HEAD
=======

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
    return (res as Map).cast<String, dynamic>();
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
>>>>>>> cc30e20 (fixed gradle problems)
}
