import 'package:flutter/material.dart';

abstract final class AppColors {
  static const primary = Color(0xFF006C4C);
  static const secondary = Color(0xFF7A5900);
  static const surface = Color(0xFFFFF8EE);
  static const text = Color(0xFF1E1B16);
}

ThemeData buildAppTheme() {
  const scheme = ColorScheme(
    brightness: Brightness.light,
    primary: AppColors.primary,
    onPrimary: Colors.white,
    secondary: AppColors.secondary,
    onSecondary: Colors.white,
    error: Color(0xFFBA1A1A),
    onError: Colors.white,
    surface: AppColors.surface,
    onSurface: AppColors.text,
  );
  return ThemeData(
    colorScheme: scheme,
    scaffoldBackgroundColor: AppColors.surface,
    useMaterial3: true,
    appBarTheme: const AppBarTheme(
      backgroundColor: AppColors.surface,
      foregroundColor: AppColors.text,
      elevation: 0,
    ),
    cardTheme: CardThemeData(
      color: Colors.white,
      elevation: 0,
      margin: EdgeInsets.zero,
    ),
  );
}
