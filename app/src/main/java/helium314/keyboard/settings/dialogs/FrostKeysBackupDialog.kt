// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import helium314.keyboard.latin.R
import helium314.keyboard.latin.backup.BackupCategory
import helium314.keyboard.latin.backup.BackupInspectionResult
import helium314.keyboard.latin.backup.FrostKeysBackupManager
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.settings.filePicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrostKeysBackupDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Export, 1 = Restore

    // Export state
    val selectedExportCategories = remember { mutableStateMapOf<BackupCategory, Boolean>() }
    LaunchedEffect(Unit) {
        BackupCategory.entries.forEach { selectedExportCategories[it] = true }
    }

    // Restore state
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var inspectionResult by remember { mutableStateOf<BackupInspectionResult?>(null) }
    val selectedRestoreCategories = remember { mutableStateMapOf<BackupCategory, Boolean>() }
    var isProcessing by remember { mutableStateOf(false) }

    val fileSaveLauncher = filePicker { uri ->
        isProcessing = true
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    val activeCats = selectedExportCategories.filter { it.value }.keys.toSet()
                    FrostKeysBackupManager.exportBackup(context, os, activeCats)
                }
                context.getActivity()?.runOnUiThread {
                    Toast.makeText(context, context.getString(R.string.backup_created_success), Toast.LENGTH_LONG).show()
                    isProcessing = false
                    onDismissRequest()
                }
            } catch (t: Throwable) {
                context.getActivity()?.runOnUiThread {
                    Toast.makeText(context, context.getString(R.string.backup_error, t.localizedMessage), Toast.LENGTH_LONG).show()
                    isProcessing = false
                }
            }
        }
    }

    val fileOpenLauncher = filePicker { uri ->
        selectedFileUri = uri
        isProcessing = true
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            val result = FrostKeysBackupManager.inspectBackup(context, uri)
            context.getActivity()?.runOnUiThread {
                inspectionResult = result
                selectedRestoreCategories.clear()
                result.availableCategories.forEach { selectedRestoreCategories[it] = true }
                isProcessing = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Dialog Title
                Text(
                    text = stringResource(R.string.backup_restore_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Tabs: Export vs Restore
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.tab_export_customization), fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.tab_restore_customization), fontWeight = FontWeight.SemiBold) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Body content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (selectedTab == 0) {
                        // EXPORT TAB
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = stringResource(R.string.export_categories_prompt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Quick Select All / None
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    BackupCategory.entries.forEach { selectedExportCategories[it] = true }
                                }) {
                                    Text(stringResource(R.string.select_all))
                                }
                                TextButton(onClick = {
                                    BackupCategory.entries.forEach { selectedExportCategories[it] = false }
                                }) {
                                    Text(stringResource(R.string.deselect_all))
                                }
                            }

                            // Category Checkboxes
                            BackupCategory.entries.forEach { category ->
                                val isChecked = selectedExportCategories[category] ?: true
                                CategoryCheckboxItem(
                                    title = stringResource(category.titleResId),
                                    description = stringResource(category.descriptionResId),
                                    checked = isChecked,
                                    onCheckedChange = { selectedExportCategories[category] = it }
                                )
                            }
                        }
                    } else {
                        // RESTORE TAB
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (inspectionResult == null) {
                                // Prompt to select file
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Button(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                                addCategory(Intent.CATEGORY_OPENABLE)
                                                type = "*/*"
                                                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "application/zip", "*/*"))
                                            }
                                            fileOpenLauncher.launch(intent)
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                                    ) {
                                        Text(stringResource(R.string.button_select_fsk_file))
                                    }
                                }
                            } else {
                                val result = inspectionResult!!
                                if (result.isValid) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = if (result.isLegacyZip) "Backup ZIP detectado" else "Arquivo FrostKeys (.fsk) válido! ✨",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            if (result.createdAt.isNotEmpty()) {
                                                Text(
                                                    text = "Criado em: ${result.createdAt}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = stringResource(R.string.restore_categories_prompt),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    result.availableCategories.forEach { category ->
                                        val isChecked = selectedRestoreCategories[category] ?: true
                                        CategoryCheckboxItem(
                                            title = stringResource(category.titleResId),
                                            description = stringResource(category.descriptionResId),
                                            checked = isChecked,
                                            onCheckedChange = { selectedRestoreCategories[category] = it }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedButton(
                                        onClick = {
                                            selectedFileUri = null
                                            inspectionResult = null
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.button_choose_another_file))
                                    }
                                } else {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                    ) {
                                        Text(
                                            text = result.errorMessage ?: stringResource(R.string.backup_invalid_format),
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(16.dp),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            selectedFileUri = null
                                            inspectionResult = null
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.button_try_again))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.dialog_cancel))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (selectedTab == 0) {
                        // Share with Friend
                        FilledTonalButton(
                            onClick = {
                                val activeCats = selectedExportCategories.filter { it.value }.keys.toSet()
                                if (activeCats.isEmpty()) {
                                    Toast.makeText(context, context.getString(R.string.backup_select_at_least_one), Toast.LENGTH_SHORT).show()
                                    return@FilledTonalButton
                                }
                                val shareIntent = FrostKeysBackupManager.createShareIntent(context, activeCats)
                                if (shareIntent != null) {
                                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_backup_title)))
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.button_share_friend))
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Save .fsk File
                        Button(
                            onClick = {
                                val activeCats = selectedExportCategories.filter { it.value }.keys.toSet()
                                if (activeCats.isEmpty()) {
                                    Toast.makeText(context, context.getString(R.string.backup_select_at_least_one), Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val defaultName = FrostKeysBackupManager.generateDefaultFileName()
                                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_TITLE, defaultName)
                                }
                                fileSaveLauncher.launch(intent)
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.button_save_fsk))
                        }
                    } else if (inspectionResult?.isValid == true && selectedFileUri != null) {
                        // Restore Selected
                        Button(
                            onClick = {
                                val activeCats = selectedRestoreCategories.filter { it.value }.keys.toSet()
                                if (activeCats.isEmpty()) {
                                    Toast.makeText(context, context.getString(R.string.backup_select_at_least_one), Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isProcessing = true
                                ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
                                    try {
                                        FrostKeysBackupManager.restoreBackup(context, selectedFileUri!!, activeCats)
                                        context.getActivity()?.runOnUiThread {
                                            Toast.makeText(context, context.getString(R.string.backup_restored), Toast.LENGTH_LONG).show()
                                            isProcessing = false
                                            onDismissRequest()
                                        }
                                    } catch (t: Throwable) {
                                        context.getActivity()?.runOnUiThread {
                                            Toast.makeText(context, context.getString(R.string.restore_error, t.localizedMessage), Toast.LENGTH_LONG).show()
                                            isProcessing = false
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.button_restore_selected))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCheckboxItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) },
        color = if (checked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
