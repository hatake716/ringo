package io.github.hatake716.ohagi.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeModeTest {
    @Test
    fun missingOrUnknownPreferenceFollowsSystem() {
        listOf(null, "", "invalid", "LIGHT").forEach {
            assertEquals(ThemeMode.System, ThemeMode.fromPreference(it))
        }
    }

    @Test
    fun storedModeSurvivesReload() {
        mapOf("system" to ThemeMode.System, "light" to ThemeMode.Light, "dark" to ThemeMode.Dark)
            .forEach { (stored, expected) ->
                assertEquals(expected, ThemeMode.fromPreference(stored))
                assertEquals(stored, expected.preferenceValue)
            }
    }

    @Test
    fun explicitModesOverrideSystemButAutomaticFollowsChanges() {
        for (systemDark in listOf(false, true)) {
            assertFalse(ThemeMode.Light.isDark(systemDark))
            assertTrue(ThemeMode.Dark.isDark(systemDark))
            assertEquals(systemDark, ThemeMode.System.isDark(systemDark))
        }
    }
}
