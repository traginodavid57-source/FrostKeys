// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

    // Export state - initialized immediately with all categories checked
    val selectedExportCategories = remember {
        mutableStateMapOf<BackupCategory, Boolean>().apply {
            BackupCategory.entries.forEach { put(it, true) }
        }
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
                // Dialog Header: Title + Close Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.backup_restore_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close_rounded),
                            contentDescription = stringResource(R.string.dialog_cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

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
                        text = {
                            Text(
                                stringResource(R.string.tab_export_customization),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                stringResource(R.string.tab_restore_customization),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        BackupCategory.entries.forEach { selectedExportCategories[it] = true }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.select_all),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        BackupCategory.entries.forEach { selectedExportCategories[it] = false }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.deselect_all),
                                        style = MaterialTheme.typography.labelMedium
                                    )
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
                                        .padding(vertical = 36.dp),
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
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Text(
                                                text = if (result.isLegacyZip) "Backup ZIP detectado" else "Arquivo FrostKeys (.fsk) válido! ✨",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            if (result.createdAt.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(4.dp))
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

                                    // Quick Select All / None for Restore
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                result.availableCategories.forEach { selectedRestoreCategories[it] = true }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(stringResource(R.string.select_all), style = MaterialTheme.typography.labelMedium)
                                        }
                                        TextButton(
                                            onClick = {
                                                result.availableCategories.forEach { selectedRestoreCategories[it] = false }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(stringResource(R.string.deselect_all), style = MaterialTheme.typography.labelMedium)
                                        }
                                    }

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
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(stringResource(R.string.button_choose_another_file))
                                    }
                                } else {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp)
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
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(stringResource(R.string.button_try_again))
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Action Buttons
                if (selectedTab == 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.button_share_friend),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

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
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.button_save_fsk),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                } else if (inspectionResult?.isValid == true && selectedFileUri != null) {
                    // Restore Selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.button_restore_selected),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge
                            )
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
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(14.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = if (checked) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

