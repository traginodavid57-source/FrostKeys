// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.backup

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.edit
import helium314.keyboard.dictionarypack.DictionaryPackConstants
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.emoji.SupportedEmojis
import helium314.keyboard.latin.R
import helium314.keyboard.latin.checkVersionUpgrade
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.database.Database
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.transferOldPinnedClips
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.PreferenceUtils
import helium314.keyboard.latin.utils.PrefType
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.protectedPrefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class BackupCategory(
    val id: String,
    val titleResId: Int,
    val descriptionResId: Int
) {
    THEME_APPEARANCE(
        "theme_appearance",
        R.string.backup_cat_theme_title,
        R.string.backup_cat_theme_desc
    ),
    TOOLBAR_SHORTCUTS(
        "toolbar_shortcuts",
        R.string.backup_cat_toolbar_title,
        R.string.backup_cat_toolbar_desc
    ),
    LAYOUT_FLOATING(
        "layout_floating",
        R.string.backup_cat_layout_title,
        R.string.backup_cat_layout_desc
    ),
    TYPING_PREFERENCES(
        "typing_preferences",
        R.string.backup_cat_typing_title,
        R.string.backup_cat_typing_desc
    ),
    DICTIONARIES_DATA(
        "dictionaries_data",
        R.string.backup_cat_dictionaries_title,
        R.string.backup_cat_dictionaries_desc
    )
}

data class BackupInspectionResult(
    val isValid: Boolean,
    val isLegacyZip: Boolean = false,
    val appVersion: String = "",
    val createdAt: String = "",
    val availableCategories: Set<BackupCategory> = emptySet(),
    val errorMessage: String? = null
)

object FrostKeysBackupManager {

    const val FILE_EXTENSION = ".fsk"
    const val MANIFEST_FILE_NAME = "frostkeys_manifest.json"
    const val PREFS_FILE_NAME = "preferences.json"
    const val PROTECTED_PREFS_FILE_NAME = "protected_preferences.json"
    private const val TAG = "FrostKeysBackupManager"

    private val themePrefKeys = setOf(
        Settings.PREF_THEME_COLORS,
        Settings.PREF_THEME_COLORS_NIGHT,
        Settings.PREF_THEME_STYLE,
        Settings.PREF_THEME_DAY_NIGHT,
        Settings.PREF_THEME_KEY_BORDERS,
        Settings.PREF_NAVBAR_COLOR,
        Settings.PREF_KEYBOARD_CORNER_RADIUS,
        Settings.PREF_NARROW_KEY_GAPS,
        Settings.PREF_FROSTED_BLUR_RADIUS,
        Settings.PREF_FROSTED_KEY_TRANSPARENCY,
        Settings.PREF_FROSTED_COLOR_BLEND,
        Settings.PREF_FROSTED_SATURATION,
        Settings.PREF_FROSTED_BG_TRANSPARENCY,
        Settings.PREF_FROSTED_SPECIAL_VIBRANCY,
        Settings.PREF_FROSTED_ALPHABET_VIBRANCY,
        Settings.PREF_FROSTED_BLUR_RADIUS_NIGHT,
        Settings.PREF_FROSTED_KEY_TRANSPARENCY_NIGHT,
        Settings.PREF_FROSTED_COLOR_BLEND_NIGHT,
        Settings.PREF_FROSTED_SATURATION_NIGHT,
        Settings.PREF_FROSTED_BG_TRANSPARENCY_NIGHT,
        Settings.PREF_FROSTED_SPECIAL_VIBRANCY_NIGHT,
        Settings.PREF_FROSTED_ALPHABET_VIBRANCY_NIGHT,
        Settings.PREF_FROSTED_DUST_ENABLED,
        Settings.PREF_FROSTED_DUST_ALPHA,
        Settings.PREF_FROSTED_DUST_ALPHA_NIGHT,
        Settings.PREF_LIQUID_GLASS_INTENSITY,
        Settings.PREF_LIQUID_GLASS_INTENSITY_NIGHT,
        Settings.PREF_CUSTOM_ICON_NAMES,
        Settings.PREF_ICON_STYLE,
        Settings.PREF_SPACE_BAR_TEXT,
        Settings.PREF_FONT_SCALE,
        Settings.PREF_EMOJI_FONT_SCALE
    )

    private val toolbarPrefKeys = setOf(
        Settings.PREF_TOOLBAR_KEYS,
        Settings.PREF_PINNED_TOOLBAR_KEYS,
        Settings.PREF_QUICK_PIN_TOOLBAR_KEYS,
        Settings.PREF_PERSISTENT_TOOLBAR_KEY,
        Settings.PREF_TOOLBAR_MODE,
        Settings.PREF_TOOLBAR_CUSTOM_KEY_CODES,
        Settings.PREF_POPUP_KEYS_ORDER,
        Settings.PREF_POPUP_KEYS_HINT_ORDER,
        Settings.PREF_SHOW_POPUP_HINTS,
        Settings.PREF_SHOW_HINTS,
        Settings.PREF_MORE_POPUP_KEYS,
        Settings.PREF_TOOLBAR_SWIPE_DOWN_TO_HIDE
    )

    private val layoutPrefPrefixes = listOf(
        "floating_keyboard_",
        "one_handed_mode_",
        "enable_split_keyboard",
        "split_spacer_scale",
        "keyboard_height_scale",
        "bottom_row_scale",
        "bottom_padding_scale",
        "side_padding_scale"
    )

    private val customLayoutFilePatterns by lazy {
        listOf(
            "layouts${File.separator}.*${LayoutUtilsCustom.CUSTOM_LAYOUT_PREFIX}+\\..{0,4}".toRegex()
        )
    }

    private fun isKeyMatchingCategory(key: String, category: BackupCategory): Boolean {
        return when (category) {
            BackupCategory.THEME_APPEARANCE -> themePrefKeys.contains(key) ||
                key.startsWith("pref_frosted_") ||
                key.startsWith("pref_liquid_") ||
                key.startsWith(Settings.PREF_USER_COLORS_PREFIX) ||
                key.startsWith(Settings.PREF_USER_ALL_COLORS_PREFIX) ||
                key.startsWith(Settings.PREF_USER_MORE_COLORS_PREFIX)
            BackupCategory.TOOLBAR_SHORTCUTS -> toolbarPrefKeys.contains(key)
            BackupCategory.LAYOUT_FLOATING -> layoutPrefPrefixes.any { key.startsWith(it) }
            BackupCategory.TYPING_PREFERENCES -> {
                !isKeyMatchingCategory(key, BackupCategory.THEME_APPEARANCE) &&
                !isKeyMatchingCategory(key, BackupCategory.TOOLBAR_SHORTCUTS) &&
                !isKeyMatchingCategory(key, BackupCategory.LAYOUT_FLOATING)
            }
            BackupCategory.DICTIONARIES_DATA -> false
        }
    }

    fun generateDefaultFileName(): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
        return "FrostKeys_Customization_$date$FILE_EXTENSION"
    }

    fun exportBackup(context: Context, outputStream: OutputStream, selectedCategories: Set<BackupCategory>) {
        ZipOutputStream(outputStream).use { zip ->
            // 1. Write Manifest
            val manifestJson = JSONObject().apply {
                put("app", "com.orion.frostkeys")
                put("format_version", 1)
                put("extension", FILE_EXTENSION)
                put("created_at", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Calendar.getInstance().time))
                put("app_version", context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0")
                val catArray = JSONArray()
                selectedCategories.forEach { catArray.put(it.id) }
                put("categories", catArray)
            }
            zip.putNextEntry(ZipEntry(MANIFEST_FILE_NAME))
            zip.write(manifestJson.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 2. Filter and write Main Preferences
            val allPrefs = context.prefs().all
            val filteredPrefs = allPrefs.filter { entry ->
                val key = entry.key ?: return@filter false
                selectedCategories.any { cat -> isKeyMatchingCategory(key, cat) }
            }
            val prefsJson = mapToTypedJsonObject(filteredPrefs)
            zip.putNextEntry(ZipEntry(PREFS_FILE_NAME))
            zip.write(prefsJson.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 3. Filter and write Protected Preferences
            val allProtected = context.protectedPrefs().all
            val filteredProtected = allProtected.filter { entry ->
                val key = entry.key ?: return@filter false
                selectedCategories.any { cat -> isKeyMatchingCategory(key, cat) }
            }
            val protectedJson = mapToTypedJsonObject(filteredProtected)
            zip.putNextEntry(ZipEntry(PROTECTED_PREFS_FILE_NAME))
            zip.write(protectedJson.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 4. If DICTIONARIES_DATA selected, export database and layout files
            if (selectedCategories.contains(BackupCategory.DICTIONARIES_DATA)) {
                val dbFile = context.getDatabasePath(Database.NAME)
                if (dbFile.exists()) {
                    FileInputStream(dbFile).buffered().use { fis ->
                        zip.putNextEntry(ZipEntry(Database.NAME))
                        fis.copyTo(zip, 1024)
                        zip.closeEntry()
                    }
                }

                val filesDir = context.filesDir
                if (filesDir != null) {
                    val filesPath = filesDir.path + File.separator
                    filesDir.walk().forEach { file ->
                        val relPath = file.path.replace(filesPath, "")
                        if (file.isFile && customLayoutFilePatterns.any { relPath.matches(it) }) {
                            FileInputStream(file).buffered().use { fis ->
                                zip.putNextEntry(ZipEntry(relPath))
                                fis.copyTo(zip, 1024)
                                zip.closeEntry()
                            }
                        }
                    }
                }
            }
        }
    }

    fun createShareIntent(context: Context, selectedCategories: Set<BackupCategory>): Intent? {
        return try {
            val fileName = generateDefaultFileName()
            val cacheFile = File(context.cacheDir, fileName)
            FileOutputStream(cacheFile).use { fos ->
                exportBackup(context, fos, selectedCategories)
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )

            Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_backup_subject))
                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_backup_text))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create share intent", t)
            null
        }
    }

    fun inspectBackup(context: Context, uri: Uri): BackupInspectionResult {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zip ->
                    var entry = zip.nextEntry
                    var hasManifest = false
                    var manifestJson: JSONObject? = null
                    var hasLegacyPrefs = false

                    while (entry != null) {
                        if (entry.name == MANIFEST_FILE_NAME) {
                            val content = String(zip.readBytes(), Charsets.UTF_8)
                            manifestJson = JSONObject(content)
                            hasManifest = true
                        } else if (entry.name == "settings.json" || entry.name == PREFS_FILE_NAME) {
                            hasLegacyPrefs = true
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }

                    if (hasManifest && manifestJson != null) {
                        val app = manifestJson.optString("app", "")
                        val appVersion = manifestJson.optString("app_version", "1.0")
                        val createdAt = manifestJson.optString("created_at", "")
                        val categoriesArray = manifestJson.optJSONArray("categories")
                        val availableCategories = mutableSetOf<BackupCategory>()

                        if (categoriesArray != null) {
                            for (i in 0 until categoriesArray.length()) {
                                val catId = categoriesArray.getString(i)
                                BackupCategory.entries.find { it.id == catId }?.let { availableCategories.add(it) }
                            }
                        }
                        BackupInspectionResult(
                            isValid = true,
                            isLegacyZip = false,
                            appVersion = appVersion,
                            createdAt = createdAt,
                            availableCategories = if (availableCategories.isEmpty()) BackupCategory.entries.toSet() else availableCategories
                        )
                    } else if (hasLegacyPrefs) {
                        // Legacy backup detected
                        BackupInspectionResult(
                            isValid = true,
                            isLegacyZip = true,
                            appVersion = "Legacy",
                            createdAt = "Unknown",
                            availableCategories = BackupCategory.entries.toSet()
                        )
                    } else {
                        BackupInspectionResult(
                            isValid = false,
                            errorMessage = context.getString(R.string.backup_invalid_format)
                        )
                    }
                }
            } ?: BackupInspectionResult(isValid = false, errorMessage = "Cannot open file")
        } catch (t: Throwable) {
            Log.e(TAG, "Inspection error", t)
            BackupInspectionResult(isValid = false, errorMessage = t.localizedMessage)
        }
    }

    fun restoreBackup(context: Context, uri: Uri, categoriesToRestore: Set<BackupCategory>) {
        val restoredDb = context.getDatabasePath(Database.NAME + "_restored")
        val filesDir = context.filesDir ?: return
        val protectedFilesDir = DeviceProtectedUtils.getFilesDir(context)

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        PREFS_FILE_NAME, "settings.json" -> {
                            val content = String(zip.readBytes(), Charsets.UTF_8)
                            restorePreferencesJson(context.prefs(), content, categoriesToRestore)
                        }
                        PROTECTED_PREFS_FILE_NAME, "protected_settings.json" -> {
                            val content = String(zip.readBytes(), Charsets.UTF_8)
                            restorePreferencesJson(context.protectedPrefs(), content, categoriesToRestore)
                        }
                        Database.NAME -> {
                            if (categoriesToRestore.contains(BackupCategory.DICTIONARIES_DATA)) {
                                FileUtils.copyStreamToNewFile(zip, restoredDb)
                            }
                        }
                        else -> {
                            if (categoriesToRestore.contains(BackupCategory.DICTIONARIES_DATA)) {
                                if (entry.name.startsWith("unprotected${File.separator}")) {
                                    val adj = entry.name.substringAfter("unprotected${File.separator}")
                                    if (customLayoutFilePatterns.any { adj.matches(it) }) {
                                        restoreEntryToDir(zip, protectedFilesDir, adj)
                                    }
                                } else if (customLayoutFilePatterns.any { entry.name.matches(it) }) {
                                    restoreEntryToDir(zip, filesDir, entry.name)
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        if (categoriesToRestore.contains(BackupCategory.DICTIONARIES_DATA) && restoredDb.exists()) {
            Database.copyFromDb(restoredDb, context)
        }

        PreferenceUtils.sanitizePreferences(context.prefs())
        PreferenceUtils.sanitizePreferences(context.protectedPrefs())

        // Post-restore refresh
        checkVersionUpgrade(context)
        transferOldPinnedClips(context)
        SubtypeSettings.reloadEnabledSubtypes(context)
        val newDictBroadcast = Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION)
        context.sendBroadcast(newDictBroadcast)
        LayoutUtilsCustom.onLayoutFileChanged()
        LayoutUtilsCustom.removeMissingLayouts(context)
        SupportedEmojis.load(context)
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
        KeyboardSwitcher.getInstance().reloadKeyboard()
    }

    private fun restorePreferencesJson(
        prefs: SharedPreferences,
        jsonString: String,
        categoriesToRestore: Set<BackupCategory>
    ) {
        try {
            val json = JSONObject(jsonString)
            val isTypedV2 = json.optString("__format__") == "typed_v2" && json.has("entries")
            val targetObject = if (isTypedV2) json.getJSONObject("entries") else json

            prefs.edit {
                val keys = targetObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key == "__format__" || key == "entries") continue
                    if (categoriesToRestore.any { isKeyMatchingCategory(key, it) }) {
                        val rawValue = targetObject.get(key)
                        if (rawValue is JSONObject && rawValue.has("type") && rawValue.has("value")) {
                            val type = rawValue.getString("type")
                            when (type) {
                                "boolean" -> putBoolean(key, rawValue.getBoolean("value"))
                                "int" -> putInt(key, rawValue.getInt("value"))
                                "long" -> putLong(key, rawValue.getLong("value"))
                                "float" -> putFloat(key, rawValue.getDouble("value").toFloat())
                                "string" -> putString(key, rawValue.getString("value"))
                                "string_set", "stringSet" -> {
                                    val arr = rawValue.getJSONArray("value")
                                    val set = mutableSetOf<String>()
                                    for (i in 0 until arr.length()) set.add(arr.getString(i))
                                    putStringSet(key, set)
                                }
                            }
                        } else {
                            // Flat JSON without explicit wrapper (e.g. from older .fsk backups)
                            val expectedType = PreferenceUtils.getExpectedPrefType(key)
                            when (expectedType) {
                                PrefType.FLOAT -> {
                                    val f = when (rawValue) {
                                        is Number -> rawValue.toFloat()
                                        is String -> rawValue.toFloatOrNull()
                                        else -> null
                                    }
                                    if (f != null) putFloat(key, f)
                                }
                                PrefType.INT -> {
                                    val i = when (rawValue) {
                                        is Number -> rawValue.toInt()
                                        is String -> rawValue.toIntOrNull()
                                        else -> null
                                    }
                                    if (i != null) putInt(key, i)
                                }
                                PrefType.LONG -> {
                                    val l = when (rawValue) {
                                        is Number -> rawValue.toLong()
                                        is String -> rawValue.toLongOrNull()
                                        else -> null
                                    }
                                    if (l != null) putLong(key, l)
                                }
                                PrefType.BOOLEAN -> {
                                    val b = when (rawValue) {
                                        is Boolean -> rawValue
                                        is String -> rawValue.toBooleanStrictOrNull()
                                        is Number -> rawValue.toInt() != 0
                                        else -> null
                                    }
                                    if (b != null) putBoolean(key, b)
                                }
                                PrefType.STRING -> {
                                    if (rawValue !is JSONArray && rawValue !is JSONObject) {
                                        putString(key, rawValue.toString())
                                    }
                                }
                                PrefType.STRING_SET -> {
                                    if (rawValue is JSONArray) {
                                        val set = mutableSetOf<String>()
                                        for (i in 0 until rawValue.length()) {
                                            set.add(rawValue.getString(i))
                                        }
                                        putStringSet(key, set)
                                    }
                                }
                                PrefType.UNKNOWN -> {
                                    when (rawValue) {
                                        is Boolean -> putBoolean(key, rawValue)
                                        is Int -> putInt(key, rawValue)
                                        is Long -> putLong(key, rawValue)
                                        is Double -> putFloat(key, rawValue.toFloat())
                                        is Float -> putFloat(key, rawValue)
                                        is String -> putString(key, rawValue)
                                        is JSONArray -> {
                                            val set = mutableSetOf<String>()
                                            for (i in 0 until rawValue.length()) {
                                                set.add(rawValue.getString(i))
                                            }
                                            putStringSet(key, set)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            PreferenceUtils.sanitizePreferences(prefs)
        } catch (t: Throwable) {
            Log.w(TAG, "Error restoring preferences JSON, trying line fallback", t)
            // Fallback for older line-based format
            restoreLegacyPrefLines(prefs, jsonString.split("\n"), categoriesToRestore)
            PreferenceUtils.sanitizePreferences(prefs)
        }
    }

    private fun restoreLegacyPrefLines(
        prefs: SharedPreferences,
        lines: List<String>,
        categoriesToRestore: Set<BackupCategory>
    ) {
        prefs.edit {
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEach
                try {
                    val json = JSONObject(trimmed)
                    val key = json.optString("key", "")
                    if (key.isNotEmpty() && categoriesToRestore.any { isKeyMatchingCategory(key, it) }) {
                        val type = json.optString("type", "")
                        when (type) {
                            "boolean" -> putBoolean(key, json.getBoolean("value"))
                            "int" -> putInt(key, json.getInt("value"))
                            "long" -> putLong(key, json.getLong("value"))
                            "float" -> putFloat(key, json.getDouble("value").toFloat())
                            "string" -> putString(key, json.getString("value"))
                            "stringSet", "string_set" -> {
                                val arr = json.getJSONArray("value")
                                val set = mutableSetOf<String>()
                                for (i in 0 until arr.length()) set.add(arr.getString(i))
                                putStringSet(key, set)
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    private fun mapToTypedJsonObject(map: Map<String, Any?>): JSONObject {
        val root = JSONObject()
        val entries = JSONObject()
        map.forEach { (k, v) ->
            if (k != null && v != null) {
                val item = JSONObject()
                when (v) {
                    is Boolean -> {
                        item.put("type", "boolean")
                        item.put("value", v)
                    }
                    is Float -> {
                        item.put("type", "float")
                        item.put("value", v.toDouble())
                    }
                    is Int -> {
                        item.put("type", "int")
                        item.put("value", v)
                    }
                    is Long -> {
                        item.put("type", "long")
                        item.put("value", v)
                    }
                    is String -> {
                        item.put("type", "string")
                        item.put("value", v)
                    }
                    is Set<*> -> {
                        item.put("type", "string_set")
                        val arr = JSONArray()
                        v.forEach { if (it is String) arr.put(it) }
                        item.put("value", arr)
                    }
                    else -> {
                        item.put("type", "string")
                        item.put("value", v.toString())
                    }
                }
                entries.put(k, item)
            }
        }
        root.put("__format__", "typed_v2")
        root.put("entries", entries)
        return root
    }

    private fun restoreEntryToDir(zip: ZipInputStream, targetDir: File, relativePath: String): Boolean {
        val targetFile = File(targetDir, relativePath)
        val canonicalDir = targetDir.canonicalPath
        val canonicalFile = targetFile.canonicalPath
        if (!canonicalFile.startsWith(canonicalDir + File.separator)) {
            return false
        }
        targetFile.parentFile?.mkdirs()
        FileUtils.copyStreamToNewFile(zip, targetFile)
        return true
    }
}
