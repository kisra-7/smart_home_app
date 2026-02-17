import 'package:flutter/services.dart';

class TuyaPlatform {
  static const MethodChannel _channel = MethodChannel('tuya_config');

  /// Native events (tuya_gw_success, tuya_sub_success, tuya_flow_step, etc.)
  static void setEventHandler(
    Future<dynamic> Function(MethodCall call)? handler,
  ) {
    _channel.setMethodCallHandler(handler);
  }

  // ---------- Core ----------
  static Future<void> initSdk() async {
    await _channel.invokeMethod('initSdk');
  }

  static Future<bool> isLoggedIn() async {
    final res = await _channel.invokeMethod('isLoggedIn');
    return res == true;
  }

  // ---------- Auth ----------
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

  // ---------- Home ----------
  static Future<List<Map<String, dynamic>>> getHomeList() async {
    final res = await _channel.invokeMethod('getHomeList');
    final list = (res as List).cast<dynamic>();
    return list.map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

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

  // ==========================================================
  // Option B (Direct SDK) - Gateway + Zigbee sub-devices
  // ==========================================================

  /// Starts pairing the gateway (hub) via token + newGwActivator(...)
  static Future<void> startZigbeeGatewayPairing({required int homeId}) async {
    await _channel.invokeMethod('startZigbeeGatewayPairing', {'homeId': homeId});
  }

  /// ✅ Pair device/gateway using QR URL like: https://m.smart.com/XXXX
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

  /// Starts pairing Zigbee sub-devices through a paired gateway devId.
  static Future<void> startZigbeeSubDevicePairing({
    required String gwDevId,
    int timeout = 100,
  }) async {
    await _channel.invokeMethod('startZigbeeSubDevicePairing', {
      'gwDevId': gwDevId,
      'timeout': timeout,
    });
  }

  static Future<void> stopActivator() async {
    await _channel.invokeMethod('stopActivator');
  }

  // ==========================================================
  // Backwards compatible names (optional)
  // ==========================================================
  static Future<void> openAddGateway({required int homeId}) async {
    await _channel.invokeMethod('openAddGateway', {'homeId': homeId});
  }

  static Future<void> openQrScan({required int homeId}) async {
    await _channel.invokeMethod('openQrScan', {'homeId': homeId});
  }

  // ==========================================================
  // Option A (UI BizBundles) - Production flows
  // ==========================================================

  /// Opens the full pairing UI (Wi-Fi / Zigbee gateway / BLE / QR, etc.)
  static Future<void> bizOpenAddDevice({required int homeId}) async {
    await _channel.invokeMethod('bizOpenAddDevice', {'homeId': homeId});
  }

  /// Opens the native BizBundle QR scanner page.
  static Future<void> bizOpenQrScan() async {
    await _channel.invokeMethod('bizOpenQrScan');
  }
}
