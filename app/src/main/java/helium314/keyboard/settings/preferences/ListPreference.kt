package helium314.keyboard.settings.preferences

import android.content.SharedPreferences
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.ListPickerDialog

@Composable
/** [items] are displayString to value */
fun <T: Any> ListPreference(
    setting: Setting,
    items: List<Pair<String, T>>,
    default: T,
    itemDescriptions: ((T) -> String?)? = null,
    onChanged: (T) -> Unit = { }
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val prefs = LocalContext.current.prefs()
    val selected = items.firstOrNull { it.second == getPrefOfType(prefs, setting.key, default) }
    Preference(
        name = setting.title,
        description = selected?.first,
        onClick = { showDialog = true }
    )
    if (showDialog) {
        ListPickerDialog(
            onDismissRequest = { showDialog = false },
            items = items,
            onItemSelected = {
                if (it == selected) return@ListPickerDialog
                putPrefOfType(prefs, setting.key, it.second)
                onChanged(it.second)
            },
            selectedItem = selected,
            title = { Text(setting.title) },
            getItemName = { it.first },
            getItemDescription = { itemDescriptions?.invoke(it.second) }
        )
    }
}

@Suppress("UNCHECKED_CAST")
fun <T: Any> getPrefOfType(prefs: SharedPreferences, key: String, default: T): T {
    return try {
        when (default) {
            is String -> prefs.getString(key, default)
            is Int -> prefs.getInt(key, default)
            is Long -> prefs.getLong(key, default)
            is Float -> prefs.getFloat(key, default)
            is Boolean -> prefs.getBoolean(key, default)
            else -> throw IllegalArgumentException("unknown type ${default.javaClass}")
        } as T
    } catch (e: ClassCastException) {
        val v = prefs.all[key]
        val converted: Any = when (default) {
            is Float -> when (v) {
                is Number -> v.toFloat()
                is String -> v.toFloatOrNull() ?: default
                else -> default
            }
            is Int -> when (v) {
                is Number -> v.toInt()
                is String -> v.toIntOrNull() ?: default
                else -> default
            }
            is Long -> when (v) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull() ?: default
                else -> default
            }
            is Boolean -> when (v) {
                is String -> v.toBooleanStrictOrNull() ?: default
                is Number -> v.toInt() != 0
                else -> default
            }
            is String -> v?.toString() ?: default
            else -> default
        }
        putPrefOfType(prefs, key, converted)
        converted as T
    }
}

private fun <T: Any> putPrefOfType(prefs: SharedPreferences, key: String, value: T) =
    prefs.edit {
        when (value) {
            is String -> putString(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Boolean -> putBoolean(key, value)
            else -> throw IllegalArgumentException("unknown type ${value.javaClass}")
        }
    }
