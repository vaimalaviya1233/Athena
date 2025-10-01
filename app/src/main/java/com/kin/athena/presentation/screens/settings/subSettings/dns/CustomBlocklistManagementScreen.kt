/*
 * Copyright (C) 2025 Vexzure
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.kin.athena.presentation.screens.settings.subSettings.dns

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kin.athena.R
import com.kin.athena.presentation.screens.settings.components.SettingType
import com.kin.athena.presentation.screens.settings.components.SettingsBox
import com.kin.athena.presentation.components.material.MaterialBar
import com.kin.athena.presentation.components.material.MaterialScaffold
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.vector.ImageVector
import com.kin.athena.presentation.screens.settings.components.settingsContainer
import com.kin.athena.presentation.screens.settings.subSettings.dns.components.AdvancedCustomBlocklistDialog
import com.kin.athena.presentation.screens.settings.subSettings.dns.components.DownloadDialog
import com.kin.athena.presentation.screens.settings.subSettings.dns.components.DownloadState
import com.kin.athena.presentation.screens.settings.subSettings.dns.viewModel.BlocklistEntry
import com.kin.athena.presentation.screens.settings.subSettings.dns.viewModel.BlockListViewModel
import com.kin.athena.presentation.screens.settings.subSettings.dns.viewModel.CustomBlocklistViewModel
import com.kin.athena.presentation.screens.settings.viewModel.SettingsViewModel
import com.kin.athena.core.utils.constants.AppConstants
import com.kin.athena.presentation.config
import com.kin.athena.presentation.screens.settings.subSettings.dns.hosts.HostState
import com.kin.athena.presentation.screens.settings.subSettings.dns.hosts.HostFile
import com.kin.athena.presentation.screens.settings.subSettings.dns.hosts.RuleDatabaseUpdateWorker
import com.kin.athena.core.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun CustomBlocklistManagementScreen(
    navController: NavController,
    settings: SettingsViewModel,
    blockListViewModel: BlockListViewModel = hiltViewModel(),
    customBlocklistViewModel: CustomBlocklistViewModel = hiltViewModel()
) {
    val showDownloadDialog by blockListViewModel.showDownloadDialog.collectAsState()
    val downloadState by blockListViewModel.downloadState.collectAsState()
    val deletingItems by customBlocklistViewModel.deletingItems.collectAsState()
    
    // Validation state for live updates
    val validationState by customBlocklistViewModel.validationState.collectAsState()
    val currentValidatingEntry by customBlocklistViewModel.currentEntry.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var editingHostFile by remember { mutableStateOf<HostFile?>(null) }
    
    val predefinedLists = setOf(
        AppConstants.DnsBlockLists.MALWARE_PROTECTION,
        AppConstants.DnsBlockLists.AD_PROTECTION,
        AppConstants.DnsBlockLists.PRIVACY_PROTECTION,
        AppConstants.DnsBlockLists.SOCIAL_PROTECTION,
        AppConstants.DnsBlockLists.ADULT_PROTECTION,
        AppConstants.DnsBlockLists.GAMBLING_PROTECTION
    )
    
    // Use a state that triggers recomposition when the config is modified
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val customBlocklists = remember(refreshTrigger) {
        config.hosts.items.filter { hostFile ->
            !predefinedLists.contains(hostFile.data)
        }
    }
    
    // Always trigger fresh validation when screen loads or data changes
    LaunchedEffect(customBlocklists) {
        customBlocklists.forEach { hostFile ->
            // Force fresh validation every time - no caching, always get latest data
            customBlocklistViewModel.validateBlocklist(hostFile.data)
        }
    }

    fun updateList(enabled: Boolean, list: String, title: String = list) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (enabled) {
                    blockListViewModel.startDownload("Custom Blocklist")
                    config.addURL(title, list, HostState.DENY)
                    val currentList = RuleDatabaseUpdateWorker.doneNames.value.toMutableList()
                    currentList.add(list)
                    RuleDatabaseUpdateWorker.doneNames.value = currentList
                } else {
                    RuleDatabaseUpdateWorker.doneNames.value =
                        RuleDatabaseUpdateWorker.doneNames.value
                            .filter { it != list }
                            .toMutableList()
                    config.removeURL(list)
                }
                config.save()
                blockListViewModel.invalidateDomainsCache()
                blockListViewModel.refreshDomains()
                
                // Trigger recomposition by updating the refresh trigger on the Main thread
                CoroutineScope(Dispatchers.Main).launch {
                    refreshTrigger++
                }
                
                if (enabled) {
                    blockListViewModel.downloadSuccess()
                }
            } catch (e: Exception) {
                Logger.error("Failed to update custom blocklist '$list': ${e.message}", e)
                if (enabled) {
                    blockListViewModel.downloadError()
                }
            }
        }
    }

    MaterialScaffold(
        topBar = {
            MaterialBar(
                title = stringResource(R.string.custom_blocklist),
                onBackNavClicked = { navController.navigateUp() }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.custom_blocklist)
                )
            }
        }
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (customBlocklists.isEmpty()) {
                settingsContainer {
                    Text(
                        text = stringResource(R.string.no_custom_blocklists_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    )
                }
            } else {                
                val blocklistEntries = customBlocklists.map { hostFile ->
                    val baseEntry = customBlocklistViewModel.createBlocklistEntry(
                        hostFile.data, 
                        hostFile.title,
                        if (hostFile.title.contains("AdBlock", ignoreCase = true)) "Ad Blocking" 
                        else if (hostFile.title.contains("Privacy", ignoreCase = true)) "Privacy"
                        else if (hostFile.title.contains("Security", ignoreCase = true)) "Security"
                        else "Custom"
                    )
                    // Use validation state if this is the entry being validated
                    val updatedEntry = if (currentValidatingEntry?.url == hostFile.data) {
                        baseEntry.copy(validationState = validationState)
                    } else baseEntry
                    
                    updatedEntry to hostFile
                }
                
                val groupedEntries = blocklistEntries.groupBy { it.first.category }
                
                groupedEntries.forEach { (category, entries) ->
                    // Category header
                    item(key = "header_$category") {
                        CategoryHeader(
                            category = category,
                            count = entries.size
                        )
                    }
                    
                    entries.forEachIndexed { index, (entry, hostFile) ->
                        item(key = "${category}_${hostFile.data}_$index") {
                            AnimatedVisibility(
                                visible = !deletingItems.contains(hostFile.data),
                                exit = slideOutHorizontally(
                                    targetOffsetX = { -it },
                                    animationSpec = tween(300)
                                ) + fadeOut(animationSpec = tween(300))
                            ) {
                                ImprovedBlocklistTile(
                                    entry = entry,
                                    onEdit = {
                                        editingHostFile = hostFile
                                    },
                                    onDelete = {
                                        customBlocklistViewModel.startDeletion(hostFile.data)
                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                            kotlinx.coroutines.delay(300) // Wait for animation
                                            updateList(false, hostFile.data)
                                            customBlocklistViewModel.finishDeletion(hostFile.data)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    
                    // Add spacing between categories
                    item(key = "spacer_$category") {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        LaunchedEffect(Unit) {
            customBlocklistViewModel.showAddDialog()
        }
        AdvancedCustomBlocklistDialog(
            onExit = { 
                showAddDialog = false
                customBlocklistViewModel.hideDialog()
            },
            onDone = { entry ->
                showAddDialog = false
                updateList(true, entry.url, entry.name)
                customBlocklistViewModel.hideDialog()
            }
        )
    }
    
    // Edit Dialog
    editingHostFile?.let { hostFile ->
        LaunchedEffect(hostFile) {
            val blocklistEntry = customBlocklistViewModel.createBlocklistEntry(
                hostFile.data, 
                hostFile.title,
                if (hostFile.title.contains("AdBlock", ignoreCase = true)) "Ad Blocking" 
                else if (hostFile.title.contains("Privacy", ignoreCase = true)) "Privacy"
                else if (hostFile.title.contains("Security", ignoreCase = true)) "Security"
                else "Custom"
            )
            customBlocklistViewModel.showEditDialog(blocklistEntry)
        }
        
        AdvancedCustomBlocklistDialog(
            onExit = { 
                editingHostFile = null
                customBlocklistViewModel.hideDialog()
            },
            onDone = { entry ->
                // First remove the old blocklist
                updateList(false, hostFile.data)
                // Then add the updated one
                updateList(true, entry.url, entry.name)
                editingHostFile = null
                customBlocklistViewModel.hideDialog()
            }
        )
    }
    
    // Download Dialog
    DownloadDialog(
        title = stringResource(R.string.custom_blocklist),
        isVisible = showDownloadDialog,
        downloadState = downloadState ?: DownloadState.Downloading,
        onDismiss = { blockListViewModel.dismissDownloadDialog() },
        onRetry = { /* Retry logic if needed */ }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImprovedBlocklistTile(
    entry: BlocklistEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.toInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX < -200) {
                            showDeleteDialog = true
                        }
                        offsetX = 0f
                    }
                ) { _, dragAmount ->
                    val newOffset = offsetX + dragAmount
                    offsetX = newOffset.coerceAtMost(0f) // Only allow left swipe
                }
            }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEdit() },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp,
                pressedElevation = 6.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(modifier = Modifier.height(2.dp))
                        
                        // Category chip
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (entry.category) {
                                "Ad Blocking" -> MaterialTheme.colorScheme.primaryContainer
                                "Privacy" -> MaterialTheme.colorScheme.secondaryContainer
                                "Security" -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = entry.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = when (entry.category) {
                                    "Ad Blocking" -> MaterialTheme.colorScheme.onPrimaryContainer
                                    "Privacy" -> MaterialTheme.colorScheme.onSecondaryContainer
                                    "Security" -> MaterialTheme.colorScheme.onErrorContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Delete button (always visible)
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.delete_description),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // URL/Link
                Text(
                    text = entry.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
            }
        }
        
        // Swipe indicator
        if (offsetX < -50) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .graphicsLayer { alpha = (-offsetX / 200f).coerceIn(0f, 1f) },
                contentAlignment = Alignment.CenterEnd
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.delete_description),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
    
    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_blocklist_title)) },
            text = { 
                Text(stringResource(R.string.delete_blocklist_confirmation, entry.name)) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel_button_text))
                }
            }
        )
    }
}

@Composable
private fun CategoryHeader(
    category: String,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = when (category) {
                "Ad Blocking" -> MaterialTheme.colorScheme.primaryContainer
                "Privacy" -> MaterialTheme.colorScheme.secondaryContainer
                "Security" -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = when (category) {
                        "Ad Blocking" -> Icons.Rounded.Block
                        "Privacy" -> Icons.Rounded.PrivacyTip
                        "Security" -> Icons.Rounded.Security
                        else -> Icons.Rounded.Category
                    },
                    contentDescription = category,
                    modifier = Modifier.size(16.dp),
                    tint = when (category) {
                        "Ad Blocking" -> MaterialTheme.colorScheme.onPrimaryContainer
                        "Privacy" -> MaterialTheme.colorScheme.onSecondaryContainer
                        "Security" -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                Text(
                    text = category,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = when (category) {
                        "Ad Blocking" -> MaterialTheme.colorScheme.onPrimaryContainer
                        "Privacy" -> MaterialTheme.colorScheme.onSecondaryContainer
                        "Security" -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                Surface(
                    shape = RoundedCornerShape(50),
                    color = when (category) {
                        "Ad Blocking" -> MaterialTheme.colorScheme.primary
                        "Privacy" -> MaterialTheme.colorScheme.secondary
                        "Security" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (category) {
                            "Ad Blocking" -> MaterialTheme.colorScheme.onPrimary
                            "Privacy" -> MaterialTheme.colorScheme.onSecondary
                            "Security" -> MaterialTheme.colorScheme.onError
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

