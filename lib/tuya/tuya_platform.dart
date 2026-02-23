import 'dart:async';
import 'package:flutter/services.dart';

typedef TuyaEventHandler = Future<dynamic> Function(MethodCall call);

class TuyaPlatform {
  static const _channel = MethodChannel('tuya_bridge');

  static TuyaEventHandler? _eventHandler;
  static bool _handlerBound = false;

  static void setEventHandler(TuyaEventHandler? handler) {
    _eventHandler = handler;
    if (_handlerBound) return;

    _channel.setMethodCallHandler((call) async {
      final h = _eventHandler;
      if (h == null) return null;
      return h(call);
    });

    _handlerBound = true;
  }

  static Future<void> initSdk() async {
    await _channel.invokeMethod('initSdk');
  }

  static Future<bool> isLoggedIn() async {
    final v = await _channel.invokeMethod('isLoggedIn');
    return v == true;
  }

  static Future<void> loginByEmail({
    required String countryCode,
    required String email,
    required String password,
  }) async {
    await _channel.invokeMethod('loginByEmail', {
      'countryCode': countryCode,
      'email': email,
      'password': password,
    });
  }

  static Future<void> sendEmailCode({
    required String countryCode,
    required String email,
    int type = 1,
  }) async {
    await _channel.invokeMethod('sendEmailCode', {
      'countryCode': countryCode,
      'email': email,
      'type': type,
    });
  }

  static Future<void> registerEmail({
    required String countryCode,
    required String email,
    required String password,
    required String code,
  }) async {
    await _channel.invokeMethod('registerEmail', {
      'countryCode': countryCode,
      'email': email,
      'password': password,
      'code': code,
    });
  }

  static Future<void> logout() async {
    await _channel.invokeMethod('logout');
  }

  static Future<List<Map<String, dynamic>>> getHomeList() async {
    final res = await _channel.invokeMethod('getHomeList');
    final list = (res as List?) ?? const [];
    return list.map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

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
    return Map<String, dynamic>.from(res as Map);
  }

  static Future<Map<String, dynamic>> getHomeDevices({
    required int homeId,
  }) async {
    final res = await _channel.invokeMethod('getHomeDevices', {
      'homeId': homeId,
    });
    return Map<String, dynamic>.from(res as Map);
  }

  /// BizBundle: opens full Add Device UI
  static Future<void> bizOpenAddDevice({
    required int homeId,
  }) async {
    await _channel.invokeMethod('bizOpenAddDevice', {
      'homeId': homeId,
    });
  }

  /// BizBundle: opens scan+bind route (native Tuya QR UI)
  static Future<void> bizOpenQrScan({
    required int homeId,
  }) async {
    await _channel.invokeMethod('bizOpenQrScan', {
      'homeId': homeId,
    });
  }

  /// BizBundle: sub-device pairing via a gateway card
  static Future<void> bizOpenGatewaySubPairing({
    required int homeId,
    required String gwDevId,
  }) async {
    await _channel.invokeMethod('bizOpenGatewaySubPairing', {
      'homeId': homeId,
      'gwDevId': gwDevId,
    });
  }

  /// Stable QR pairing (SDK activator): call this if BizBundle standalone scan is flaky.
  static Future<void> pairDeviceByQr({
    required int homeId,
    required String qrUrl,
    int timeout = 100,
  }) async {
    await _channel.invokeMethod('pairDeviceByQr', {
      'homeId': homeId,
      'qrUrl': qrUrl,
      'timeout': timeout,
    });
  }
}