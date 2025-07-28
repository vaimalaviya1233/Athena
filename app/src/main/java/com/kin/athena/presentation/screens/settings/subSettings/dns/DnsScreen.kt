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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Games
import androidx.compose.material.icons.rounded.Man
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kin.athena.R
import com.kin.athena.core.logging.Logger
import com.kin.athena.core.utils.constants.AppConstants
import com.kin.athena.presentation.config
import com.kin.athena.presentation.screens.settings.components.IconType
import com.kin.athena.presentation.screens.settings.components.ListDialog
import com.kin.athena.presentation.screens.settings.components.SettingType
import com.kin.athena.presentation.screens.settings.components.SettingsBox
import com.kin.athena.presentation.screens.settings.components.SettingsScaffold
import com.kin.athena.presentation.screens.settings.components.settingsContainer
import com.kin.athena.presentation.screens.settings.subSettings.dns.components.CustomBlocklistDialog
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

    fun updateLists() {
        val workRequest = OneTimeWorkRequestBuilder<RuleDatabaseUpdateWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }

    fun reloadDatabase() {
        blockListViewModel.ruleDatabase.initialize()
    }

    fun updateList(enabled: Boolean, list: String) {
        if (list.isBlank()) {
            Logger.error("updateList: List parameter is blank or empty")
            return
        }

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
            try {
                if (enabled) {
                    config.addURL(list, list, HostState.DENY)
                    Logger.info("Added list: $list")
                } else {
                    RuleDatabaseUpdateWorker.doneNames.value =
                        RuleDatabaseUpdateWorker.doneNames.value
                            .filter { it != list }
                            .toMutableList()
                    config.removeURL(list)
                    Logger.info("Removed list: $list")
                }
                updateLists()
                // Reload the database and update DNS rules in background to prevent ANR
                CoroutineScope(Dispatchers.IO).launch {
                    reloadDatabase()
                    blockListViewModel.firewallManager.updateDomains()
                }
            } catch (e: Exception) {
                Logger.error("Failed to update list '$list': ${e.message}", e)
            }
        } else {
            Logger.warn("Skipping list update for '$list': Root mode enabled but hosts not writable")
        }
    }

    SettingsScaffold(
        settings = settings,
        title = stringResource(id = R.string.dns_title),
        onBackNavClicked = { navController.navigateUp() }
    ) {
        settingsContainer {
            SettingsBox(
                title = stringResource(R.string.set_dns),
                description = stringResource(R.string.set_dns_description),
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
                title = stringResource(id = R.string.malware_protection),
                description = stringResource(id = R.string.malware_protection_description),
                icon = IconType.VectorIcon(Icons.Rounded.Security),
                actionType = SettingType.SWITCH,
                variable = AppConstants.DnsBlockLists.MALWARE_PROTECTION in doneNames.value,
                onSwitchEnabled = {
                    updateList(it, AppConstants.DnsBlockLists.MALWARE_PROTECTION)
                }
            )
            SettingsBox(
                title = stringResource(id = R.string.ad_protection),
                description = stringResource(id = R.string.ad_blocker_description),
                icon = IconType.VectorIcon(Icons.Rounded.AdsClick),
                actionType = SettingType.SWITCH,
                variable = AppConstants.DnsBlockLists.AD_PROTECTION in doneNames.value,
                onSwitchEnabled = {
                    updateList(it, AppConstants.DnsBlockLists.AD_PROTECTION)
                }
            )
            SettingsBox(
                title = stringResource(id = R.string.block_trackers),
                description = stringResource(id = R.string.block_trackers_description),
                icon = IconType.VectorIcon(Icons.Rounded.RemoveRedEye),
                actionType = SettingType.SWITCH,
                variable = AppConstants.DnsBlockLists.PRIVACY_PROTECTION in doneNames.value,
                onSwitchEnabled = {
                    updateList(it, AppConstants.DnsBlockLists.PRIVACY_PROTECTION)
                }
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.social_media),
                description = stringResource(id = R.string.social_media_description),
                icon = IconType.VectorIcon(Icons.Rounded.Message),
                actionType = SettingType.SWITCH,
                variable = AppConstants.DnsBlockLists.SOCIAL_PROTECTION in doneNames.value,
                onSwitchEnabled = {
                    updateList(it, AppConstants.DnsBlockLists.SOCIAL_PROTECTION)
                }
            )
            SettingsBox(
                title = stringResource(id = R.string.adult_content),
                description = stringResource(id = R.string.block_adult_content),
                icon = IconType.VectorIcon(Icons.Rounded.Man),
                actionType = SettingType.SWITCH,
                variable = AppConstants.DnsBlockLists.ADULT_PROTECTION in doneNames.value,
                onSwitchEnabled = {
                    updateList(it, AppConstants.DnsBlockLists.ADULT_PROTECTION)
                }
            )
            SettingsBox(
                title = stringResource(id = R.string.gambling),
                description = stringResource(id = R.string.block_gambling),
                icon = IconType.VectorIcon(Icons.Rounded.Games),
                actionType = SettingType.SWITCH,
                variable = AppConstants.DnsBlockLists.GAMBLING_PROTECTION in doneNames.value,
                onSwitchEnabled = {
                    updateList(it, AppConstants.DnsBlockLists.GAMBLING_PROTECTION)
                }
            )
        }

        settingsContainer {
            @Composable
            fun OnBlocklistClicked(blocklists: List<String>, onExit: () -> Unit) {
                val uniqueBlocklists = blocklists.distinct()

                ListDialog(
                    text = stringResource(R.string.custom_blocklist),
                    list = uniqueBlocklists,
                    onExit = onExit,
                    extractDisplayData = { it },
                    customItem = {
                        SettingsBox(
                            size = 8.dp,
                            title = stringResource(R.string.custom_blocklist),
                            actionType = SettingType.CUSTOM,
                            customAction = { onExit ->
                                CustomBlocklistDialog(onExit) { blocklists ->
                                    updateList(true, blocklists)
                                }
                            }
                        )
                    },
                    setting = { displayData ->
                        SettingsBox(
                            size = 8.dp,
                            title = displayData.replace("https://", ""),
                            actionType = SettingType.CUSTOM,
                            customButton = {
                                Icon(Icons.Rounded.Delete, "")
                            },
                            customAction = {
                                LaunchedEffect(true) {
                                    updateList(false, displayData)
                                }
                            }
                        )
                    }
                )
            }


            SettingsBox(
                title = stringResource(id = R.string.custom_blocklist) + " " + stringResource(id = R.string.premium_setting),
                description = stringResource(R.string.custom_blocklist_description),
                icon = IconType.VectorIcon(Icons.Rounded.Add),
                actionType = SettingType.CUSTOM,
                customAction = { onExit ->
                    var isEnabled by remember { mutableStateOf(false) }
                    settings.startBilling("custom_blocklist") {
                        isEnabled = true
                    }
                    if (isEnabled) {
                        OnBlocklistClicked(doneNames.value, onExit)
                    }
                }
            )
            SettingsBox(
                title = stringResource(id = R.string.blocked_domains),
                description = blockList.value.value.count().toString(),
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
        text = stringResource(R.string.dns_title),
        list = list,
        onExit = onExit,
        extractDisplayData = { it },
        customItem = {
            SettingsBox(
                size = 8.dp,
                title = stringResource(R.string.custom_dns),
                description = stringResource(R.string.set_custom_dns),
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
