import 'dart:async';
import 'package:flutter/services.dart';

typedef TuyaEventHandler = Future<dynamic> Function(MethodCall call);

class TuyaPlatform {
  /// IMPORTANT:
  /// This MUST match the Android MethodChannel name in MainActivity.
  /// Based on your project history, the correct one is "tuya_bridge".
  static const MethodChannel _channel = MethodChannel('tuya_bridge');

  static TuyaEventHandler? _eventHandler;
  static bool _handlerBound = false;

  /// Optional: receive native events (pairing progress, callbacks, etc.)
  static void setEventHandler(TuyaEventHandler? handler) {
    _eventHandler = handler;

    if (_handlerBound) return;
    _handlerBound = true;

    _channel.setMethodCallHandler((call) async {
      final h = _eventHandler;
      if (h == null) return null;
      return h(call);
    });
  }

  // =========================
  // Core
  // =========================

  static Future<void> initSdk() async {
    await _channel.invokeMethod('initSdk');
  }

  static Future<bool> isLoggedIn() async {
    final res = await _channel.invokeMethod('isLoggedIn');
    return res == true;
  }

  // =========================
  // Auth
  // =========================

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

  // =========================
  // Home
  // =========================

  static Future<List<Map<String, dynamic>>> getHomeList() async {
    final res = await _channel.invokeMethod('getHomeList');
    final list = (res as List?) ?? const [];
    return list.map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  /// Returns a Map. Depending on your native implementation it may include:
  /// { "homeId": <int>, "created": <bool> }
  ///
  /// We keep it flexible + safe so UI doesn't crash if "created" isn't returned.
  static Future<Map<String, dynamic>> ensureHome({
    String name = 'My Home',
    String geoName = 'Oman',
    List<String> rooms = const ['Living Room'],
  }) async {
    final res = await _channel.invokeMethod('ensureHome', {
      'name': name,
      'geoName': geoName,
      'rooms': rooms,
    });

    if (res is Map) {
      return Map<String, dynamic>.from(res);
    }
    throw PlatformException(
      code: 'BAD_NATIVE_RESPONSE',
      message: 'ensureHome expected Map but got: ${res.runtimeType}',
    );
  }

  static Future<Map<String, dynamic>> getHomeDevices({
    required int homeId,
  }) async {
    final res = await _channel.invokeMethod('getHomeDevices', {'homeId': homeId});
    if (res is Map) return Map<String, dynamic>.from(res);
    throw PlatformException(
      code: 'BAD_NATIVE_RESPONSE',
      message: 'getHomeDevices expected Map but got: ${res.runtimeType}',
    );
  }

  // =========================
  // BizBundle UI (Option A)
  // =========================

  static Future<void> bizOpenAddDevice({required int homeId}) async {
    await _channel.invokeMethod('bizOpenAddDevice', {'homeId': homeId});
  }

  static Future<void> bizOpenQrScan({required int homeId}) async {
    await _channel.invokeMethod('bizOpenQrScan', {'homeId': homeId});
  }

  static Future<void> bizOpenGatewaySubPairing({
    required int homeId,
    required String gwDevId,
  }) async {
    await _channel.invokeMethod('bizOpenGatewaySubPairing', {
      'homeId': homeId,
      'gwDevId': gwDevId,
    });
  }

  // =========================
  // Stable QR pairing (Direct SDK fallback)
  // =========================

  static Future<void> pairDeviceByQr({
    required int homeId,
    required String qrUrl,
    int timeout = 100,
  }) async {
    await _channel.invokeMethod('pairDeviceByQr', {
      'homeId': homeId,
      'qrUrl': qrUrl.trim(),
      'timeout': timeout,
    });
  }
}