package io.github.hatake716.ohagi.data

/** Stable preference values, independent of enum ordering or translated labels. */
enum class ThemeMode(val preferenceValue: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }

    companion object {
        fun fromPreference(value: String?): ThemeMode =
            entries.firstOrNull { it.preferenceValue == value } ?: System
    }
}
