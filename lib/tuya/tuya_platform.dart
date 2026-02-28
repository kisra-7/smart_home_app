import 'package:flutter/services.dart';

class TuyaPlatform {
  // ✅ Must match MainActivity.kt channel name
  static const MethodChannel _channel = MethodChannel('tuya_bridge');

  // -------------------------
  // Core
  // -------------------------
  static Future<bool> initSdk() async {
    final ok = await _channel.invokeMethod<bool>('initSdk');
    return ok ?? false;
  }

  static Future<bool> isLoggedIn() async {
    final ok = await _channel.invokeMethod<bool>('isLoggedIn');
    return ok ?? false;
  }

  // -------------------------
  // Auth
  // -------------------------
  static Future<bool> loginByEmail({
    required String countryCode,
    required String email,
    required String password,
  }) async {
    final ok = await _channel.invokeMethod<bool>('loginByEmail', {
      'countryCode': countryCode,
      'email': email,
      'password': password,
    });
    return ok ?? false;
  }

  static Future<bool> sendEmailCode({
    required String countryCode,
    required String email,
    int type = 1,
  }) async {
    final ok = await _channel.invokeMethod<bool>('sendEmailCode', {
      'countryCode': countryCode,
      'email': email,
      'type': type,
    });
    return ok ?? false;
  }

  static Future<bool> registerEmail({
    required String countryCode,
    required String email,
    required String password,
    required String code,
  }) async {
    final ok = await _channel.invokeMethod<bool>('registerEmail', {
      'countryCode': countryCode,
      'email': email,
      'password': password,
      'code': code,
    });
    return ok ?? false;
  }

  static Future<bool> logout() async {
    final ok = await _channel.invokeMethod<bool>('logout');
    return ok ?? false;
  }

  // -------------------------
  // Home
  // -------------------------
  /// Native returns: List<Map> [{homeId, name, geoName}, ...]
  static Future<List<Map<dynamic, dynamic>>> getHomeList() async {
    final res = await _channel.invokeMethod<dynamic>('getHomeList');
    if (res is List) {
      return res.map((e) => Map<dynamic, dynamic>.from(e as Map)).toList();
    }
    return <Map<dynamic, dynamic>>[];
  }

  /// Native returns: Map {homeId, name, geoName}
  static Future<Map<dynamic, dynamic>> ensureHome({
    required String name,
    required String geoName,
    required List<String> rooms,
  }) async {
    final res = await _channel.invokeMethod<dynamic>('ensureHome', {
      'name': name,
      'geoName': geoName,
      'rooms': rooms,
    });

    if (res is Map) return Map<dynamic, dynamic>.from(res);
    throw PlatformException(
      code: 'ENSURE_HOME_FAILED',
      message: 'ensureHome returned invalid result',
    );
  }

  // -------------------------
  // BizBundle Context + UI
  // -------------------------
  static Future<void> ensureBizContext({required int homeId}) async {
    await _channel.invokeMethod('ensureBizContext', {
      'homeId': homeId,
    });
  }

  static Future<void> bizOpenAddDevice({required int homeId}) async {
    await _channel.invokeMethod('bizOpenAddDevice', {
      'homeId': homeId,
    });
  }

  static Future<void> bizOpenQrScan({required int homeId}) async {
    await _channel.invokeMethod('bizOpenQrScan', {
      'homeId': homeId,
    });
  }
}