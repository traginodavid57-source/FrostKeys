// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.SharedPreferences
import androidx.core.content.edit
import helium314.keyboard.latin.settings.Settings

enum class PrefType {
    FLOAT,
    INT,
    LONG,
    BOOLEAN,
    STRING,
    STRING_SET,
    UNKNOWN
}

object PreferenceUtils {

    @JvmStatic
    fun getExpectedPrefType(key: String): PrefType {
        return when {
            // --- FLOATS ---
            key == Settings.PREF_FROSTED_DUST_ALPHA ||
            key == Settings.PREF_FROSTED_DUST_ALPHA_NIGHT ||
            key == Settings.PREF_FONT_SCALE ||
            key == Settings.PREF_EMOJI_FONT_SCALE ||
            key == Settings.PREF_AUTO_CORRECT_THRESHOLD ||
            key == Settings.PREF_KEYPRESS_SOUND_VOLUME ||
            key.startsWith(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX) ||
            key.startsWith(Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX) ||
            key.startsWith(Settings.PREF_BOTTOM_ROW_SCALE_PREFIX) ||
            key.startsWith(Settings.PREF_SIDE_PADDING_SCALE_PREFIX) ||
            key.startsWith(Settings.PREF_SPLIT_SPACER_SCALE_PREFIX) ||
            key.startsWith(Settings.PREF_ONE_HANDED_SCALE_PREFIX) ||
            key.startsWith(Settings.PREF_FLOATING_KEYBOARD_SCALE_PREFIX) -> PrefType.FLOAT

            // --- INTS ---
            key == Settings.PREF_KEYBOARD_CORNER_RADIUS ||
            key == Settings.PREF_FROSTED_BLUR_RADIUS ||
            key == Settings.PREF_FROSTED_KEY_TRANSPARENCY ||
            key == Settings.PREF_FROSTED_COLOR_BLEND ||
            key == Settings.PREF_FROSTED_SATURATION ||
            key == Settings.PREF_FROSTED_BG_TRANSPARENCY ||
            key == Settings.PREF_FROSTED_SPECIAL_VIBRANCY ||
            key == Settings.PREF_FROSTED_ALPHABET_VIBRANCY ||
            key == Settings.PREF_FROSTED_BLUR_RADIUS_NIGHT ||
            key == Settings.PREF_FROSTED_KEY_TRANSPARENCY_NIGHT ||
            key == Settings.PREF_FROSTED_COLOR_BLEND_NIGHT ||
            key == Settings.PREF_FROSTED_SATURATION_NIGHT ||
            key == Settings.PREF_FROSTED_BG_TRANSPARENCY_NIGHT ||
            key == Settings.PREF_FROSTED_SPECIAL_VIBRANCY_NIGHT ||
            key == Settings.PREF_FROSTED_ALPHABET_VIBRANCY_NIGHT ||
            key == Settings.PREF_LIQUID_GLASS_INTENSITY ||
            key == Settings.PREF_LIQUID_GLASS_INTENSITY_NIGHT ||
            key == Settings.PREF_VIBRATION_DURATION_SETTINGS ||
            key == Settings.PREF_KEY_LONGPRESS_TIMEOUT ||
            key == Settings.PREF_GESTURE_FAST_TYPING_COOLDOWN ||
            key == Settings.PREF_GESTURE_TRAIL_FADEOUT_DURATION ||
            key == Settings.PREF_LANGUAGE_SWIPE_DISTANCE ||
            key == Settings.PREF_TOUCHPAD_SENSITIVITY ||
            key == Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME ||
            key == Settings.PREF_LAST_SHOWN_EMOJI_CATEGORY_PAGE_ID ||
            key == Settings.PREF_LAST_SHOWN_EMOJI_CATEGORY_ID ||
            key == Settings.PREF_VERSION_CODE ||
            key.startsWith(Settings.PREF_ONE_HANDED_GRAVITY_PREFIX) ||
            key.startsWith(Settings.PREF_FLOATING_KEYBOARD_X_PREFIX) ||
            key.startsWith(Settings.PREF_FLOATING_KEYBOARD_Y_PREFIX) ||
            key.startsWith("theme_color_show_more_colors") ||
            key.startsWith("theme_dark_color_show_more_colors") ||
            key.endsWith("_show_more_colors") -> PrefType.INT

            // --- BOOLEANS ---
            key == Settings.PREF_FROSTED_DUST_ENABLED ||
            key == Settings.PREF_NATIVE_BACKGROUND_BLUR_ONLY ||
            key == Settings.PREF_THEME_KEY_BORDERS ||
            key == Settings.PREF_THEME_DAY_NIGHT ||
            key == Settings.PREF_AUTO_CAP ||
            key == Settings.PREF_VIBRATE_ON ||
            key == Settings.PREF_VIBRATE_IN_DND_MODE ||
            key == Settings.PREF_SOUND_ON ||
            key == Settings.PREF_SUGGEST_EMOJIS ||
            key == Settings.PREF_INLINE_EMOJI_SEARCH ||
            key == Settings.PREF_SHOW_EMOJI_DESCRIPTIONS ||
            key == Settings.PREF_PERSISTENT_EMOJI_ROW ||
            key == Settings.PREF_POPUP_ON ||
            key == Settings.PREF_AUTO_CORRECTION ||
            key == Settings.PREF_MORE_AUTO_CORRECTION ||
            key == Settings.PREF_AUTOCORRECT_SHORTCUTS ||
            key == Settings.PREF_BACKSPACE_REVERTS_AUTOCORRECT ||
            key == Settings.PREF_CENTER_SUGGESTION_TEXT_TO_ENTER ||
            key == Settings.PREF_SHOW_SUGGESTIONS ||
            key == Settings.PREF_ALWAYS_SHOW_SUGGESTIONS ||
            key == Settings.PREF_ALWAYS_SHOW_SUGGESTIONS_EXCEPT_WEB_TEXT ||
            key == Settings.PREF_KEY_USE_PERSONALIZED_DICTS ||
            key == Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD ||
            key == Settings.PREF_BLOCK_POTENTIALLY_OFFENSIVE ||
            key == Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY ||
            key == Settings.PREF_SHOW_EMOJI_KEY ||
            key == Settings.PREF_VARIABLE_TOOLBAR_DIRECTION ||
            key == Settings.PREF_ENABLE_SPLIT_KEYBOARD ||
            key == Settings.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE ||
            key == Settings.PREF_ENABLE_SPLIT_KEYBOARD_FOLDED ||
            key == Settings.PREF_ENABLE_SPLIT_KEYBOARD_FOLDED_LANDSCAPE ||
            key == Settings.PREF_EMOJI_KEY_FIT ||
            key == Settings.PREF_DELETE_SWIPE ||
            key == Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION ||
            key == Settings.PREF_AUTOSPACE_AFTER_SUGGESTION ||
            key == Settings.PREF_AUTOSPACE_AFTER_GESTURE_TYPING ||
            key == Settings.PREF_AUTOSPACE_BEFORE_GESTURE_TYPING ||
            key == Settings.PREF_SHIFT_REMOVES_AUTOSPACE ||
            key == Settings.PREF_ALWAYS_INCOGNITO_MODE ||
            key == Settings.PREF_BIGRAM_PREDICTIONS ||
            key == Settings.PREF_SUGGEST_PUNCTUATION ||
            key == Settings.PREF_SUGGEST_CLIPBOARD_CONTENT ||
            key == Settings.PREF_GESTURE_INPUT ||
            key == Settings.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY ||
            key == Settings.PREF_GESTURE_PREVIEW_TRAIL ||
            key == Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT ||
            key == Settings.PREF_GESTURE_FLOATING_PREVIEW_DYNAMIC ||
            key == Settings.PREF_GESTURE_DYNAMIC_PREVIEW_FOLLOW_SYSTEM ||
            key == Settings.PREF_GESTURE_SPACE_AWARE ||
            key == Settings.PREF_SHOW_SETUP_WIZARD_ICON ||
            key == Settings.PREF_USE_CONTACTS ||
            key == Settings.PREF_USE_APPS ||
            key == Settings.PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD ||
            key.startsWith(Settings.PREF_ONE_HANDED_MODE_PREFIX) ||
            key.startsWith(Settings.PREF_FLOATING_KEYBOARD_ENABLED_PREFIX) ||
            key == Settings.PREF_SHOW_NUMBER_ROW ||
            key == Settings.PREF_SHOW_NUMBER_ROW_IN_SYMBOLS ||
            key == Settings.PREF_LOCALIZED_NUMBER_ROW ||
            key == Settings.PREF_SHOW_NUMBER_ROW_HINTS ||
            key == Settings.PREF_SHOW_HINTS ||
            key == Settings.PREF_SHOW_POPUP_HINTS ||
            key == Settings.PREF_SHOW_TLD_POPUP_KEYS ||
            key == Settings.PREF_SPACE_TO_CHANGE_LANG ||
            key == Settings.PREF_TOUCHPAD_EDGE_SCROLL ||
            key == Settings.PREF_ENABLE_CLIPBOARD_HISTORY ||
            key == Settings.PREF_SHOW_SCREENSHOTS_IN_CLIPBOARD ||
            key == Settings.PREF_CLIPBOARD_HISTORY_PINNED_FIRST ||
            key == Settings.PREF_ADD_TO_PERSONAL_DICTIONARY ||
            key == Settings.PREF_NAVBAR_COLOR ||
            key == Settings.PREF_NARROW_KEY_GAPS ||
            key == Settings.PREF_URL_DETECTION ||
            key == Settings.PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG ||
            key == Settings.PREF_QUICK_PIN_TOOLBAR_KEYS ||
            key == Settings.PREF_AUTO_SHOW_TOOLBAR ||
            key == Settings.PREF_AUTO_HIDE_TOOLBAR ||
            key == Settings.PREF_ABC_AFTER_EMOJI ||
            key == Settings.PREF_ABC_AFTER_CLIP ||
            key == Settings.PREF_ABC_AFTER_SYMBOL_SPACE ||
            key == Settings.PREF_ABC_AFTER_NUMPAD_SPACE ||
            key == Settings.PREF_REMOVE_REDUNDANT_POPUPS ||
            key == Settings.PREF_TOOLBAR_HIDING_GLOBAL ||
            key == Settings.PREF_TOOLBAR_SWIPE_DOWN_TO_HIDE ||
            key == Settings.PREF_SPELLCHECK_SUGGEST ||
            key == Settings.PREF_SEND_GIFS_AS_STICKERS ||
            key == Settings.PREF_USE_5_WORD_SUGGESTION_CHIPS ||
            key == Settings.PREF_SAVE_SUBTYPE_PER_APP -> PrefType.BOOLEAN

            // --- STRINGS ---
            key == Settings.PREF_THEME_COLORS ||
            key == Settings.PREF_THEME_COLORS_NIGHT ||
            key == Settings.PREF_THEME_STYLE ||
            key == Settings.PREF_ICON_STYLE ||
            key == Settings.PREF_SPACE_BAR_TEXT ||
            key == Settings.PREF_LANGUAGE_SWITCH_KEY ||
            key == Settings.PREF_ADDITIONAL_SUBTYPES ||
            key == Settings.PREF_ENABLED_SUBTYPES ||
            key == Settings.PREF_SELECTED_SUBTYPE ||
            key == Settings.PREF_TOOLBAR_MODE ||
            key == Settings.PREF_TOOLBAR_KEYS ||
            key == Settings.PREF_PINNED_TOOLBAR_KEYS ||
            key == Settings.PREF_PERSISTENT_TOOLBAR_KEY ||
            key == Settings.PREF_TOOLBAR_CUSTOM_KEY_CODES ||
            key == Settings.PREF_POPUP_KEYS_ORDER ||
            key == Settings.PREF_POPUP_KEYS_HINT_ORDER ||
            key == Settings.PREF_MORE_POPUP_KEYS ||
            key == Settings.PREF_CUSTOM_ICON_NAMES ||
            key == Settings.PREF_CUSTOM_CURRENCY_KEY ||
            key == Settings.PREF_EMOJI_SKIN_TONE ||
            key == Settings.PREF_SPACE_HORIZONTAL_SWIPE ||
            key == Settings.PREF_SPACE_VERTICAL_SWIPE ||
            key == Settings.PREF_TIMESTAMP_FORMAT ||
            key == Settings.PREF_AI_TRANSLATE_LANGUAGE ||
            key == Settings.PREF_EMOJI_RECENT_KEYS ||
            key == Settings.PREF_BLUR_RENDER_OVERRIDE ||
            key.startsWith(Settings.PREF_LAYOUT_PREFIX) ||
            key.startsWith(Settings.PREF_SAVED_APP_SUBTYPE_PREFIX) ||
            key.startsWith(Settings.PREF_USER_COLORS_PREFIX) ||
            key.startsWith(Settings.PREF_USER_ALL_COLORS_PREFIX) ||
            key.startsWith("secondary_locales_") -> PrefType.STRING

            else -> PrefType.UNKNOWN
        }
    }

    @JvmStatic
    fun sanitizePreferences(prefs: SharedPreferences) {
        val all = try {
            prefs.all
        } catch (t: Throwable) {
            Log.e("PreferenceUtils", "Failed to read prefs.all for sanitization", t)
            return
        }
        if (all.isEmpty()) return

        var hasFixes = false
        val editor = prefs.edit()

        all.forEach { (key, value) ->
            if (key == null || value == null) return@forEach
            val expected = getExpectedPrefType(key)
            when (expected) {
                PrefType.FLOAT -> {
                    if (value !is Float) {
                        val converted = when (value) {
                            is Number -> value.toFloat()
                            is String -> value.toFloatOrNull()
                            else -> null
                        }
                        if (converted != null) {
                            editor.putFloat(key, converted)
                            hasFixes = true
                            Log.w("PreferenceUtils", "Repaired float pref '$key': was ${value.javaClass.simpleName}($value) -> Float($converted)")
                        }
                    }
                }
                PrefType.INT -> {
                    if (value !is Int) {
                        val converted = when (value) {
                            is Number -> value.toInt()
                            is String -> value.toIntOrNull()
                            else -> null
                        }
                        if (converted != null) {
                            editor.putInt(key, converted)
                            hasFixes = true
                            Log.w("PreferenceUtils", "Repaired int pref '$key': was ${value.javaClass.simpleName}($value) -> Int($converted)")
                        }
                    }
                }
                PrefType.LONG -> {
                    if (value !is Long) {
                        val converted = when (value) {
                            is Number -> value.toLong()
                            is String -> value.toLongOrNull()
                            else -> null
                        }
                        if (converted != null) {
                            editor.putLong(key, converted)
                            hasFixes = true
                            Log.w("PreferenceUtils", "Repaired long pref '$key': was ${value.javaClass.simpleName}($value) -> Long($converted)")
                        }
                    }
                }
                PrefType.BOOLEAN -> {
                    if (value !is Boolean) {
                        val converted = when (value) {
                            is String -> value.toBooleanStrictOrNull()
                            is Number -> value.toInt() != 0
                            else -> null
                        }
                        if (converted != null) {
                            editor.putBoolean(key, converted)
                            hasFixes = true
                            Log.w("PreferenceUtils", "Repaired boolean pref '$key': was ${value.javaClass.simpleName}($value) -> Boolean($converted)")
                        }
                    }
                }
                PrefType.STRING -> {
                    if (value !is String && value !is Set<*>) {
                        val converted = value.toString()
                        editor.putString(key, converted)
                        hasFixes = true
                        Log.w("PreferenceUtils", "Repaired string pref '$key': was ${value.javaClass.simpleName}($value) -> String($converted)")
                    }
                }
                else -> {
                    // UNKNOWN or STRING_SET - leave as is
                }
            }
        }

        if (hasFixes) {
            editor.apply()
            Log.i("PreferenceUtils", "Preference sanitization finished and changes applied.")
        }
    }

    @JvmStatic
    fun getFloatSafe(prefs: SharedPreferences, key: String, defValue: Float): Float {
        return try {
            prefs.getFloat(key, defValue)
        } catch (e: ClassCastException) {
            val v = prefs.all[key]
            val result = when (v) {
                is Number -> v.toFloat()
                is String -> v.toFloatOrNull() ?: defValue
                else -> defValue
            }
            prefs.edit { putFloat(key, result) }
            result
        }
    }

    @JvmStatic
    fun getIntSafe(prefs: SharedPreferences, key: String, defValue: Int): Int {
        return try {
            prefs.getInt(key, defValue)
        } catch (e: ClassCastException) {
            val v = prefs.all[key]
            val result = when (v) {
                is Number -> v.toInt()
                is String -> v.toIntOrNull() ?: defValue
                else -> defValue
            }
            prefs.edit { putInt(key, result) }
            result
        }
    }

    @JvmStatic
    fun getBooleanSafe(prefs: SharedPreferences, key: String, defValue: Boolean): Boolean {
        return try {
            prefs.getBoolean(key, defValue)
        } catch (e: ClassCastException) {
            val v = prefs.all[key]
            val result = when (v) {
                is String -> v.toBooleanStrictOrNull() ?: defValue
                is Number -> valueIsNonZero(v)
                else -> defValue
            }
            prefs.edit { putBoolean(key, result) }
            result
        }
    }

    private fun valueIsNonZero(num: Number): Boolean {
        return num.toInt() != 0
    }

    @JvmStatic
    fun getLongSafe(prefs: SharedPreferences, key: String, defValue: Long): Long {
        return try {
            prefs.getLong(key, defValue)
        } catch (e: ClassCastException) {
            val v = prefs.all[key]
            val result = when (v) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull() ?: defValue
                else -> defValue
            }
            prefs.edit { putLong(key, result) }
            result
        }
    }

    @JvmStatic
    fun getStringSafe(prefs: SharedPreferences, key: String, defValue: String?): String? {
        return try {
            prefs.getString(key, defValue)
        } catch (e: ClassCastException) {
            val v = prefs.all[key]
            val result = v?.toString() ?: defValue
            prefs.edit { if (result != null) putString(key, result) else remove(key) }
            result
        }
    }
}

// Kotlin extension functions on SharedPreferences
fun SharedPreferences.getFloatSafe(key: String, defValue: Float): Float =
    PreferenceUtils.getFloatSafe(this, key, defValue)

fun SharedPreferences.getIntSafe(key: String, defValue: Int): Int =
    PreferenceUtils.getIntSafe(this, key, defValue)

fun SharedPreferences.getBooleanSafe(key: String, defValue: Boolean): Boolean =
    PreferenceUtils.getBooleanSafe(this, key, defValue)

fun SharedPreferences.getLongSafe(key: String, defValue: Long): Long =
    PreferenceUtils.getLongSafe(this, key, defValue)

fun SharedPreferences.getStringSafe(key: String, defValue: String?): String? =
    PreferenceUtils.getStringSafe(this, key, defValue)
