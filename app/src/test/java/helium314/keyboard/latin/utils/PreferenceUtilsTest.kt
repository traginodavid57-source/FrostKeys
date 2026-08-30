// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.settings.Settings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferenceUtilsTest {
    private lateinit var prefs: SharedPreferences

    @BeforeTest
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = context.getSharedPreferences("pref-utils-test", Context.MODE_PRIVATE)
        prefs.edit { clear() }
    }

    @Test
    fun testGetFloatSafeWithCorruptedInt() {
        // Simulate corrupted preference where Int was stored instead of Float
        prefs.edit { putInt(Settings.PREF_FROSTED_DUST_ALPHA, 5) }

        // Normal getFloat would throw ClassCastException: Integer cannot be cast to Float
        val value = prefs.getFloatSafe(Settings.PREF_FROSTED_DUST_ALPHA, 1f)
        assertEquals(5f, value)

        // After safe get, it should be repaired in prefs as Float
        assertEquals(5f, prefs.getFloat(Settings.PREF_FROSTED_DUST_ALPHA, 0f))
    }

    @Test
    fun testGetIntSafeWithCorruptedFloat() {
        // Simulate corrupted preference where Float was stored instead of Int
        prefs.edit { putFloat(Settings.PREF_KEYBOARD_CORNER_RADIUS, 16f) }

        val value = prefs.getIntSafe(Settings.PREF_KEYBOARD_CORNER_RADIUS, 0)
        assertEquals(16, value)

        // After safe get, it should be repaired in prefs as Int
        assertEquals(16, prefs.getInt(Settings.PREF_KEYBOARD_CORNER_RADIUS, 0))
    }

    @Test
    fun testGetBooleanSafeWithCorruptedInt() {
        prefs.edit { putInt(Settings.PREF_FROSTED_DUST_ENABLED, 1) }

        val value = prefs.getBooleanSafe(Settings.PREF_FROSTED_DUST_ENABLED, false)
        assertTrue(value)

        assertTrue(prefs.getBoolean(Settings.PREF_FROSTED_DUST_ENABLED, false))
    }

    @Test
    fun testSanitizePreferencesRepairsAllCorruptedTypes() {
        // Put corrupted types in SharedPreferences
        prefs.edit {
            putInt(Settings.PREF_FROSTED_DUST_ALPHA, 3)
            putInt(Settings.PREF_FROSTED_DUST_ALPHA_NIGHT, 7)
            putFloat(Settings.PREF_KEYBOARD_CORNER_RADIUS, 12f)
            putFloat(Settings.PREF_FROSTED_BLUR_RADIUS, 25f)
            putFloat(Settings.PREF_FROSTED_KEY_TRANSPARENCY, 80f)
            putString(Settings.PREF_FROSTED_DUST_ENABLED, "true")
        }

        // Run batch sanitization
        PreferenceUtils.sanitizePreferences(prefs)

        // Verify that standard SharedPreferences getters now work without ClassCastException
        assertEquals(3f, prefs.getFloat(Settings.PREF_FROSTED_DUST_ALPHA, 0f))
        assertEquals(7f, prefs.getFloat(Settings.PREF_FROSTED_DUST_ALPHA_NIGHT, 0f))
        assertEquals(12, prefs.getInt(Settings.PREF_KEYBOARD_CORNER_RADIUS, 0))
        assertEquals(25, prefs.getInt(Settings.PREF_FROSTED_BLUR_RADIUS, 0))
        assertEquals(80, prefs.getInt(Settings.PREF_FROSTED_KEY_TRANSPARENCY, 0))
        assertTrue(prefs.getBoolean(Settings.PREF_FROSTED_DUST_ENABLED, false))
    }
}
