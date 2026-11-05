import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class SecureTokenStore {
  static const _key = 'model_bearer_token';
  final FlutterSecureStorage _storage = const FlutterSecureStorage();

  Future<String> read() async => await _storage.read(key: _key) ?? '';
  Future<void> save(String value) async {
    if (value.trim().isEmpty) {
      await clear();
    } else {
      await _storage.write(key: _key, value: value.trim());
    }
  }

  Future<void> clear() => _storage.delete(key: _key);
}
