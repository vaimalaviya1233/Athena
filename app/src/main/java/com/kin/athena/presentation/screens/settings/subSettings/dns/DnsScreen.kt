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

import CustomDnsDialog
import android.annotation.SuppressLint
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Games
import androidx.compose.material.icons.rounded.Man
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.work.Data
import com.kin.athena.R
import com.kin.athena.core.logging.Logger
import com.kin.athena.core.utils.constants.AppConstants
import com.kin.athena.presentation.config
import com.kin.athena.presentation.screens.settings.components.IconType
import com.kin.athena.presentation.screens.settings.components.ListDialog
import com.kin.athena.presentation.screens.settings.components.SettingType
import com.kin.athena.presentation.screens.settings.components.SettingsBox
import com.kin.athena.presentation.screens.settings.components.SettingsScaffold
import com.kin.athena.presentation.components.PremiumFeatureChoiceDialog
import com.kin.athena.presentation.screens.settings.components.settingsContainer
import com.kin.athena.presentation.navigation.routes.SettingRoutes
import com.kin.athena.presentation.screens.settings.subSettings.dns.components.CustomBlocklistDialog
import com.kin.athena.presentation.screens.settings.subSettings.dns.components.DownloadDialog
import com.kin.athena.presentation.screens.settings.subSettings.dns.components.DownloadState
import com.kin.athena.presentation.screens.settings.subSettings.dns.components.MagiskSystemlessHostsDialog
import com.kin.athena.presentation.screens.settings.subSettings.dns.hosts.HostState
import com.kin.athena.presentation.screens.settings.subSettings.dns.hosts.RuleDatabaseUpdateWorker
import com.kin.athena.presentation.screens.settings.subSettings.dns.root.HostsManager
import com.kin.athena.presentation.screens.settings.subSettings.dns.viewModel.BlockListViewModel
import com.kin.athena.presentation.screens.settings.viewModel.SettingsViewModel
import com.kin.athena.service.vpn.network.util.NetworkConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun DnsScreen(
    navController: NavController,
    settings: SettingsViewModel,
    blockListViewModel: BlockListViewModel = hiltViewModel()
) {
    val doneNames = RuleDatabaseUpdateWorker.doneNames.collectAsState(initial = emptyList())
    val blockList = blockListViewModel.ruleDatabase.blockedHosts.collectAsState()
    val context = LocalContext.current
    val showMagiskDialog by blockListViewModel.showMagiskDialog.collectAsState()
    val isDomainsLoading by blockListViewModel.isLoading.collectAsState()
    val isDomainsInitialized by blockListViewModel.isInitialized.collectAsState()
    val showDownloadDialog by blockListViewModel.showDownloadDialog.collectAsState()
    val downloadState by blockListViewModel.downloadState.collectAsState()
    val currentDownloadingRule by blockListViewModel.currentDownloadingRule.collectAsState()
    
    // Local dialog state for premium feature choice
    var showPremiumDialog by remember { mutableStateOf(false) }
    
    // Local state to track immediate switch changes for UI responsiveness
    val localSwitchStates = remember { mutableStateMapOf<String, Boolean>() }
    
    // Track if we're actively updating domains (different from initial load)
    var isDomainUpdateInProgress by remember { mutableStateOf(false) }

    fun updateLists(onComplete: ((Boolean) -> Unit)? = null) {
        val workRequest = OneTimeWorkRequestBuilder<RuleDatabaseUpdateWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
        
        // Monitor work completion
        WorkManager.getInstance(context).getWorkInfoByIdLiveData(workRequest.id)
            .observeForever { workInfo ->
                if (workInfo?.state?.isFinished == true) {
                    onComplete?.invoke(workInfo.state == WorkInfo.State.SUCCEEDED)
                }
            }
    }
    
    fun updateSpecificList(targetList: String, onComplete: ((Boolean) -> Unit)? = null) {
        val inputData = Data.Builder()
            .putStringArray("target_lists", arrayOf(targetList))
            .build()
            
        val workRequest = OneTimeWorkRequestBuilder<RuleDatabaseUpdateWorker>()
            .setInputData(inputData)
            .build()
            
        WorkManager.getInstance(context).enqueue(workRequest)
        
        onComplete?.let { callback ->
            WorkManager.getInstance(context)
                .getWorkInfoByIdLiveData(workRequest.id)
                .observeForever { workInfo ->
                    if (workInfo?.state?.isFinished == true) {
                        callback(workInfo.state == WorkInfo.State.SUCCEEDED)
                    }
                }
        }
    }

    fun reloadDatabase() {
        // Use cache service to reload domains instead of direct database initialization
        blockListViewModel.refreshDomains()
    }

    fun updateList(enabled: Boolean, list: String) {
        if (list.isBlank()) {
            Logger.error("updateList: List parameter is blank or empty")
            return
        }

        // Don't update switch state immediately - only update after successful download/removal

        val useRootMode = settings.settings.value.useRootMode == true
        var canUpdateHosts = true

        if (useRootMode) {
            val hostManager = HostsManager(context, emptyList())
            val (isWritable, isMagiskSystemless) = hostManager.isHostsWritable()
            Logger.info("Hosts writable: $isWritable, Magisk systemless: $isMagiskSystemless")
            canUpdateHosts = isWritable

            if (!isWritable) {
                Logger.error("Cannot update hosts file: Not writable even after remount attempt")
                if (!isMagiskSystemless && enabled) {
                    blockListViewModel.showMagiskDialog()
                    return // Don't proceed with operations when systemless hosts are needed but not available
                }
            }
        }

        if (!useRootMode || canUpdateHosts || !enabled) {
            // Show download dialog for enabling rules
            if (enabled) {
                val ruleName = when (list) {
                    AppConstants.DnsBlockLists.MALWARE_PROTECTION -> "Malware Protection"
                    AppConstants.DnsBlockLists.AD_PROTECTION -> "Ad Protection"
                    AppConstants.DnsBlockLists.PRIVACY_PROTECTION -> "Privacy Protection"
                    AppConstants.DnsBlockLists.SOCIAL_PROTECTION -> "Social Media"
                    AppConstants.DnsBlockLists.ADULT_PROTECTION -> "Adult Content"
                    AppConstants.DnsBlockLists.GAMBLING_PROTECTION -> "Gambling"
                    else -> "Custom Rules"
                }
                blockListViewModel.startDownload(ruleName)
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    withContext(Dispatchers.Main) {
                        isDomainUpdateInProgress = true
                    }
                    
                    if (enabled) {
                        config.addURL(list, list, HostState.DENY)
                        Logger.info("Added list: $list")
                    } else {
                        // For disabling, immediately update switch and config
                        RuleDatabaseUpdateWorker.doneNames.value =
                            RuleDatabaseUpdateWorker.doneNames.value
                                .filter { it != list }
                                .toMutableList()
                        config.removeURL(list)
                        Logger.info("Removed list: $list")
                        withContext(Dispatchers.Main) {
                            localSwitchStates[list] = false
                        }
                    }
                    config.save()  // Persist changes to disk
                    blockListViewModel.invalidateDomainsCache() // Invalidate cache for next load
                    
                    withContext(Dispatchers.Main) {
                        if (enabled) {
                            // Only update this specific list, not all lists
                            updateSpecificList(list) { success ->
                                if (success) {
                                    // Only turn switch ON after successful download
                                    localSwitchStates[list] = true
                                    blockListViewModel.downloadSuccess()
                                } else {
                                    // Download failed, revert the config changes made earlier
                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            // Remove the URL that was added
                                            config.removeURL(list)
                                            config.save()
                                            Logger.info("Reverted list addition: $list")
                                        } catch (e: Exception) {
                                            Logger.error("Failed to revert config for '$list': ${e.message}", e)
                                        }
                                    }
                                    
                                    // Show appropriate error
                                    if (!blockListViewModel.isNetworkAvailable()) {
                                        blockListViewModel.downloadNetworkError()
                                    } else {
                                        blockListViewModel.downloadError()
                                    }
                                }
                                isDomainUpdateInProgress = false
                            }
                        } else {
                            // For disabling, just refresh the domain cache
                            isDomainUpdateInProgress = false
                        }
                    }
                    
                    // Only invalidate cache and update domains without full reload
                    // This avoids downloading all blocklists when only one was changed
                    blockListViewModel.invalidateDomainsCache()
                    
                    // For disabling, no need to download - just update the domain cache
                    if (!enabled) {
                        blockListViewModel.refreshDomains()
                    }
                    // For enabling, the download happens in updateLists() callback
                    
                } catch (e: Exception) {
                    Logger.error("Failed to update list '$list': ${e.message}", e)
                    
                    withContext(Dispatchers.Main) {
                        isDomainUpdateInProgress = false
                        
                        // Don't set switch state on error - leave it in original position
                        
                        // Show appropriate error dialog
                        if (enabled) {
                            if (!blockListViewModel.isNetworkAvailable()) {
                                blockListViewModel.downloadNetworkError()
                            } else if (blockListViewModel.isNetworkError(e)) {
                                blockListViewModel.downloadNetworkError()
                            } else {
                                blockListViewModel.downloadError()
                            }
                        }
                    }
                }
            }
        } else {
            Logger.warn("Skipping list update for '$list': Root mode enabled but hosts not writable")
            // Don't update switch state - leave it in original position
        }
    }

    SettingsScaffold(
        settings = settings,
        title = stringResource(id = R.string.settings_dns),
        onBackNavClicked = { navController.navigateUp() }
    ) {
        settingsContainer {
            SettingsBox(
                title = stringResource(R.string.dns_set_custom),
                description = stringResource(R.string.dns_set_custom_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Dns),
                actionType = SettingType.CUSTOM,
                customAction = { onExit ->
                    OnDNSClicked(settings, onExit)
                }
            )
        }
        settingsContainer {
            LaunchedEffect(doneNames.value) {
                CoroutineScope(Dispatchers.IO).launch {
                    reloadDatabase()
                }
            }

            SettingsBox(
                title = stringResource(id = R.string.dns_malware_protection),
                description = stringResource(id = R.string.dns_malware_protection_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Security),
                actionType = SettingType.SWITCH,
                variable = localSwitchStates[AppConstants.DnsBlockLists.MALWARE_PROTECTION] 
                    ?: (AppConstants.DnsBlockLists.MALWARE_PROTECTION in doneNames.value),
                onSwitchEnabled = {
                    updateList(it, AppConstants.DnsBlockLists.MALWARE_PROTECTION)
                }
            )
            SettingsBox(
                title = stringResource(id = R.string.dns_ad_protection),
                description = stringResource(id = R.string.dns_ad_blocker_desc),
                icon = IconType.VectorIcon(Icons.Rounded.AdsClick),
                actionType = SettingType.SWITCH,
                variable = localSwitchStates[AppConstants.DnsBlockLists.AD_PROTECTION] 
                    ?: (AppConstants.DnsBlockLists.AD_PROTECTION in doneNames.value),
                onSwitchEnabled = {
                    updateList(it, AppConstants.DnsBlockLists.AD_PROTECTION)
                }
            )
            SettingsBox(
                title = stringResource(id = R.string.dns_block_trackers),
                description = stringResource(id = R.string.dns_block_trackers_desc),
                icon = IconType.VectorIcon(Icons.Rounded.RemoveRedEye),
                actionType = SettingType.SWITCH,
                variable = localSwitchStates[AppConstants.DnsBlockLists.PRIVACY_PROTECTION] 
                    ?: (AppConstants.DnsBlockLists.PRIVACY_PROTECTION in doneNames.value),
                onSwitchEnabled = {
                    updateList(it, AppConstants.DnsBlockLists.PRIVACY_PROTECTION)
                }
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.dns_social_media),
                description = stringResource(id = R.string.dns_social_media_desc),
                icon = IconType.VectorIcon(Icons.AutoMirrored.Rounded.Message),
                actionType = SettingType.SWITCH,
                variable = localSwitchStates[AppConstants.DnsBlockLists.SOCIAL_PROTECTION] 
                    ?: (AppConstants.DnsBlockLists.SOCIAL_PROTECTION in doneNames.value),
                onSwitchEnabled = {
                    updateList(it, AppConstants.DnsBlockLists.SOCIAL_PROTECTION)
                }
            )
            SettingsBox(
                title = stringResource(id = R.string.dns_adult_content),
                description = stringResource(id = R.string.dns_block_adult),
                icon = IconType.VectorIcon(Icons.Rounded.Man),
                actionType = SettingType.SWITCH,
                variable = localSwitchStates[AppConstants.DnsBlockLists.ADULT_PROTECTION] 
                    ?: (AppConstants.DnsBlockLists.ADULT_PROTECTION in doneNames.value),
                onSwitchEnabled = {
                    updateList(it, AppConstants.DnsBlockLists.ADULT_PROTECTION)
                }
            )
            SettingsBox(
                title = stringResource(id = R.string.dns_gambling),
                description = stringResource(id = R.string.dns_block_gambling),
                icon = IconType.VectorIcon(Icons.Rounded.Games),
                actionType = SettingType.SWITCH,
                variable = localSwitchStates[AppConstants.DnsBlockLists.GAMBLING_PROTECTION] 
                    ?: (AppConstants.DnsBlockLists.GAMBLING_PROTECTION in doneNames.value),
                onSwitchEnabled = {
                    updateList(it, AppConstants.DnsBlockLists.GAMBLING_PROTECTION)
                }
            )
        }

        settingsContainer {
            val customBlocklistTitle = stringResource(id = R.string.blocklist_custom)
            val customBlocklistDescription = stringResource(R.string.blocklist_custom_desc)
            
            SettingsBox(
                title = customBlocklistTitle + " " + stringResource(id = R.string.premium_feature_indicator),
                description = customBlocklistDescription,
                icon = IconType.VectorIcon(Icons.Rounded.Add),
                actionType = SettingType.CUSTOM,
                customAction = { onExit ->
                    println("DEBUG: Custom action composable called")
                    if (!settings.settings.value.premiumUnlocked) {
                        showPremiumDialog = true
                    } else {
                        navController.navigate(SettingRoutes.CustomBlocklist.route)
                    }
                    onExit()
                },
            )
            SettingsBox(
                title = stringResource(id = R.string.dns_blocked_domains),
                description = run {
                    val currentCount = blockList.value.value.count()
                    when {
                        currentCount > 0 -> {
                            val formatter = java.text.DecimalFormat("#,###")
                            "${formatter.format(currentCount)} domains blocked"
                        }
                        isDomainUpdateInProgress -> "Updating domains..."
                        isDomainsLoading && !isDomainsInitialized -> "Loading domains..."
                        else -> "No domains loaded"
                    }
                },
                icon = IconType.VectorIcon(Icons.Rounded.Numbers),
                actionType = SettingType.TEXT,
            )
        }
    }
    if (showMagiskDialog) {
        MagiskSystemlessHostsDialog(
            onDismiss = { blockListViewModel.hideMagiskDialog() }
        )
    }
    
    // Download Dialog
    DownloadDialog(
        title = currentDownloadingRule ?: "Downloading Rules",
        isVisible = showDownloadDialog,
        downloadState = downloadState ?: DownloadState.Downloading,
        onDismiss = { blockListViewModel.dismissDownloadDialog() },
        onRetry = {
            currentDownloadingRule?.let { ruleName ->
                val listConstant = when (ruleName) {
                    "Malware Protection" -> AppConstants.DnsBlockLists.MALWARE_PROTECTION
                    "Ad Protection" -> AppConstants.DnsBlockLists.AD_PROTECTION
                    "Privacy Protection" -> AppConstants.DnsBlockLists.PRIVACY_PROTECTION
                    "Social Media" -> AppConstants.DnsBlockLists.SOCIAL_PROTECTION
                    "Adult Content" -> AppConstants.DnsBlockLists.ADULT_PROTECTION
                    "Gambling" -> AppConstants.DnsBlockLists.GAMBLING_PROTECTION
                    else -> ""
                }
                if (listConstant.isNotEmpty()) {
                    updateList(true, listConstant)
                }
            }
        }
    )
    
    // Premium Feature Choice Dialog
    if (showPremiumDialog) {
        PremiumFeatureChoiceDialog(
            featureName = "Custom Blocklist",
            featureDescription = stringResource(R.string.blocklist_custom_desc),
            singleFeaturePrice = settings.getProductPrice("custom_blocklist"),
            fullPremiumPrice = settings.getProductPrice("all_features"),
            onSingleFeaturePurchase = { 
                settings.startBilling("custom_blocklist") {
                    navController.navigate(SettingRoutes.CustomBlocklist.route)
                }
                showPremiumDialog = false
            },
            onFullPremiumPurchase = { 
                settings.startBilling("all_features") {
                    settings.update(settings.settings.value.copy(premiumUnlocked = true))
                    navController.navigate(SettingRoutes.CustomBlocklist.route)
                }
                showPremiumDialog = false
            },
            onDismiss = { showPremiumDialog = false }
        )
    }
}

@Composable
private fun OnDNSClicked(settings: SettingsViewModel, onExit: () -> Unit) {
    val list = NetworkConstants.DNS_SERVERS
    var useCustom = true

    list.forEach { dns ->
        if (
            settings.settings.value.dnsServer1 == dns.second.first ||
            settings.settings.value.dnsServer1 == dns.second.first &&
            settings.settings.value.dnsServer2 == dns.second.first ||
            settings.settings.value.dnsServer2 == dns.second.first
        ) {
            useCustom = false
        }
    }

    ListDialog(
        text = stringResource(R.string.settings_dns),
        list = list,
        onExit = onExit,
        extractDisplayData = { it },
        customItem = {
            SettingsBox(
                size = 8.dp,
                title = stringResource(R.string.dns_custom),
                description = stringResource(R.string.dns_set_custom),
                actionType = SettingType.CUSTOM,
                customAction = { onExit ->
                    CustomDnsDialog(
                        onExit = onExit,
                        dns1Key = if (useCustom) settings.settings.value.dnsServer1 else null,
                        dns2Key = if (useCustom) settings.settings.value.dnsServer2 else null,
                        onDone = { dns1, dns2 ->
                            settings.update(settings.settings.value.copy(
                                dnsServer1 = dns1,
                                dnsServer2 = dns2
                            ))
                        }
                    )
                }
            )
        },
        setting = { displayData ->
            SettingsBox(
                size = 8.dp,
                title = displayData.first,
                description = displayData.second.first,
                actionType = SettingType.RADIOBUTTON,
                variable = settings.settings.value.dnsServer1 == displayData.second.first,
                onSwitchEnabled = {
                    settings.update(settings.settings.value.copy(
                        dnsServer1 = displayData.second.first,
                        dnsServer2 = displayData.second.second,
                    ))
                }
            )
        }
    )
}
