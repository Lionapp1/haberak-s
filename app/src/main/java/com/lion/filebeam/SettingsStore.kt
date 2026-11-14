package com.lion.filebeam

import android.content.Context

object SettingsStore {
    private const val PREFS = "filebeam_preferences"
    private const val THEME = "theme_mode"
    private const val MIRROR_PROFILE = "mirror_profile"
    private const val TRANSFER_PIN = "transfer_pin"
    private const val PAIRED_TRANSFER_IP = "paired_transfer_ip"
    private const val PAIRED_TRANSFER_PIN = "paired_transfer_pin"
    private const val MIRROR_PIN = "mirror_pin"
    private const val PAIRED_MIRROR_IP = "paired_mirror_ip"
    private const val PAIRED_MIRROR_PIN = "paired_mirror_pin"

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

    @Synchronized
    fun transferPin(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(TRANSFER_PIN, null)?.let { return it }
        val pin = (100000..999999).random().toString()
        prefs.edit().putString(TRANSFER_PIN, pin).apply()
        return pin
    }

    @Synchronized
    fun mirrorPin(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(MIRROR_PIN, null)?.let { return it }
        val pin = (100000..999999).random().toString()
        prefs.edit().putString(MIRROR_PIN, pin).apply()
        return pin
    }

    fun saveTransferPair(context: Context, ip: String, pin: String) {
        if (ip.isBlank() || pin.length != 6 || !pin.all(Char::isDigit)) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PAIRED_TRANSFER_IP, ip).putString(PAIRED_TRANSFER_PIN, pin).apply()
    }

    fun transferPinFor(context: Context, ip: String): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(PAIRED_TRANSFER_PIN, null)
            ?.takeIf { prefs.getString(PAIRED_TRANSFER_IP, null) == ip }
    }

    fun saveMirrorPair(context: Context, ip: String, pin: String) {
        if (ip.isBlank() || pin.length != 6 || !pin.all(Char::isDigit)) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PAIRED_MIRROR_IP, ip).putString(PAIRED_MIRROR_PIN, pin).apply()
    }

    fun mirrorPinFor(context: Context, ip: String): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(PAIRED_MIRROR_PIN, null)
            ?.takeIf { prefs.getString(PAIRED_MIRROR_IP, null) == ip }
    }
}
