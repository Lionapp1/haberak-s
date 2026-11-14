package com.lion.filebeam

import android.content.Context

object SettingsStore {
    private const val PREFS = "filebeam_preferences"
    private const val THEME = "theme_mode"
    private const val MIRROR_PROFILE = "mirror_profile"

    fun themeMode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(THEME, "system") ?: "system"

    fun setThemeMode(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(THEME, value).apply()
    }

    fun mirrorProfile(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(MIRROR_PROFILE, "balanced") ?: "balanced"

    fun setMirrorProfile(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(MIRROR_PROFILE, value).apply()
    }
}
