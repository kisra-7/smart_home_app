class ThingDevice {
  final String devId;
  final String name;
  final String? iconUrl;
  final bool isOnline;
  final bool isGateway;
  final String? parentId;

  const ThingDevice({
    required this.devId,
    required this.name,
    required this.iconUrl,
    required this.isOnline,
    required this.isGateway,
    required this.parentId,
  });

  factory ThingDevice.fromMap(Map<String, dynamic> map) {
    return ThingDevice(
      devId: (map['devId'] ?? '').toString(),
      name: (map['name'] ?? '').toString(),
      iconUrl: (map['iconUrl'] as String?)?.trim().isEmpty == true ? null : map['iconUrl'] as String?,
      isOnline: map['isOnline'] == true,
      isGateway: map['isGateway'] == true,
      parentId: (map['parentId'] as String?)?.trim().isEmpty == true ? null : map['parentId'] as String?,
    );
  }
}