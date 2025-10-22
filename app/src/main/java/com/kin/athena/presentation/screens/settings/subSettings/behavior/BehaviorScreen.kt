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

package com.kin.athena.presentation.screens.settings.subSettings.behavior

import android.annotation.SuppressLint
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.SignalCellularOff
import androidx.compose.material.icons.rounded.SignalWifiBad
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kin.athena.R
import com.kin.athena.core.utils.grantRootAccess
import com.kin.athena.core.utils.ShizukuUtils
import androidx.compose.material.icons.rounded.Android
import com.kin.athena.presentation.screens.settings.components.IconType
import com.kin.athena.presentation.screens.settings.components.SettingType
import com.kin.athena.presentation.screens.settings.components.SettingsBox
import com.kin.athena.presentation.screens.settings.components.SettingsScaffold
import com.kin.athena.presentation.screens.settings.components.settingsContainer
import com.kin.athena.presentation.screens.settings.subSettings.behavior.viewModel.BehaviorViewModel
import com.kin.athena.presentation.screens.settings.viewModel.SettingsViewModel
import com.kin.athena.service.firewall.utils.FirewallStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun BehaviorScreen(
    navController: NavController,
    settings: SettingsViewModel,
    behaviorViewModel: BehaviorViewModel = hiltViewModel()
) {
    SettingsScaffold(
        settings = settings,
        title = stringResource(id = R.string.settings_behavior),
        onBackNavClicked = { navController.navigateUp() }
    ) {
        settingsContainer {
            val notificationTitle = stringResource(id = R.string.behavior_notify_install)
            val notificationDescription = stringResource(id = R.string.behavior_notify_install_desc)
            
            SettingsBox(
                title = notificationTitle + " " + stringResource(id = R.string.premium_feature_indicator),
                description = notificationDescription,
                icon = IconType.VectorIcon(Icons.Rounded.InstallMobile),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.sendNotificationOnInstall,
                onSwitchEnabled = {
                    if (!settings.settings.value.premiumUnlocked) {
                        settings.showFeatureChoiceDialog(
                            featureName = notificationTitle,
                            featureDescription = notificationDescription,
                            productId = "notify_on_install"
                        ) {
                            settings.update(settings.settings.value.copy(sendNotificationOnInstall = it))
                        }
                    } else {
                        settings.update(settings.settings.value.copy(sendNotificationOnInstall = it))
                    }
                }
            )
        }
        settingsContainer {
            val firewallStatus by behaviorViewModel.firewallManager.rulesLoaded.collectAsState()
            val isFirewallOnline = firewallStatus.name() == FirewallStatus.ONLINE.name()
            
            SettingsBox(
                title = stringResource(id = R.string.behavior_use_root),
                description = stringResource(id = R.string.behavior_use_root_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Key),
                actionType = SettingType.SWITCH,
                isEnabled = !isFirewallOnline,
                isUsable = !isFirewallOnline,
                onNotUsableClicked = {
                    behaviorViewModel.showRootDisabledMessage()
                },
                variable = settings.settings.value.useRootMode == true,
                onSwitchEnabled = {
                    if (settings.settings.value.useRootMode == null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            if (grantRootAccess()) {
                                settings.update(settings.settings.value.copy(useRootMode = true))
                            }
                        }
                    } else {
                        settings.update(settings.settings.value.copy(useRootMode = null))
                    }
                }
            )
            SettingsBox(
                title = stringResource(id = R.string.behavior_use_shizuku),
                description = stringResource(id = R.string.behavior_use_shizuku_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Android),
                actionType = SettingType.SWITCH,
                isEnabled = !isFirewallOnline && ShizukuUtils.isShizukuAvailable(),
                isUsable = !isFirewallOnline && ShizukuUtils.isShizukuAvailable(),
                onNotUsableClicked = {
                    behaviorViewModel.showRootDisabledMessage()
                },
                variable = settings.settings.value.useShizukuMode == true,
                onSwitchEnabled = {
                    if (settings.settings.value.useShizukuMode == null) {
                        if (ShizukuUtils.isShizukuReady()) {
                            settings.update(settings.settings.value.copy(useShizukuMode = true, useRootMode = null))
                        } else {
                            ShizukuUtils.requestShizukuPermission()
                        }
                    } else {
                        settings.update(settings.settings.value.copy(useShizukuMode = null))
                    }
                }
            )
            val logsTitle = stringResource(id = R.string.common_logs)
            val logsDescription = stringResource(id = R.string.behavior_logs_desc)
            
            SettingsBox(
                title = logsTitle + " " + stringResource(id = R.string.premium_feature_indicator),
                description = logsDescription,
                icon = IconType.VectorIcon(Icons.Rounded.Code),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.logs,
                onSwitchEnabled = {
                    if (!settings.settings.value.premiumUnlocked) {
                        settings.showFeatureChoiceDialog(
                            featureName = logsTitle,
                            featureDescription = logsDescription,
                            productId = "packet_logs"
                        ) {
                            settings.update(settings.settings.value.copy(logs = it))
                            behaviorViewModel.updateLogs(it)
                        }
                    } else {
                        settings.update(settings.settings.value.copy(logs = it))
                        behaviorViewModel.updateLogs(it)
                    }
                }
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.behavior_show_system_apps),
                description = stringResource(id = R.string.behavior_show_system_apps_desc),
                icon = IconType.VectorIcon(Icons.Rounded.SettingsApplications),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.showSystemPackages,
                onSwitchEnabled = {
                    settings.update(settings.settings.value.copy(showSystemPackages = it))
                }
            )
            SettingsBox(
                title = stringResource(id = R.string.behavior_show_offline_apps),
                description = stringResource(id = R.string.behavior_show_offline_apps_desc),
                icon = IconType.VectorIcon(Icons.Rounded.SignalWifiBad),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.showOfflinePackages,
                onSwitchEnabled = {
                    settings.update(settings.settings.value.copy(showOfflinePackages = it))
                }
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.behavior_block_wifi_default),
                description = stringResource(id = R.string.behavior_block_wifi_default_desc),
                icon = IconType.VectorIcon(Icons.Rounded.WifiOff),
                actionType = SettingType.SWITCH,
                variable = !settings.settings.value.wiFiDefault,
                onSwitchEnabled = {
                    settings.update(settings.settings.value.copy(wiFiDefault = !it))
                    behaviorViewModel.updateApp(
                        settings.settings.value.wiFiDefault,
                        settings.settings.value.cellularDefault,
                        settings.settings.value.showSystemPackages
                    )
                }
            )
            SettingsBox(
                title = stringResource(id = R.string.behavior_block_cellular_default),
                description = stringResource(id = R.string.behavior_block_cellular_default_desc),
                icon = IconType.VectorIcon(Icons.Rounded.SignalCellularOff),
                actionType = SettingType.SWITCH,
                variable = !settings.settings.value.cellularDefault,
                onSwitchEnabled = {
                    settings.update(settings.settings.value.copy(cellularDefault = !it))
                    behaviorViewModel.updateApp(
                        settings.settings.value.wiFiDefault,
                        settings.settings.value.cellularDefault,
                        settings.settings.value.showSystemPackages
                    )
                }
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.behavior_start_on_boot),
                description = stringResource(id = R.string.behavior_start_on_boot_desc),
                icon = IconType.VectorIcon(Icons.Rounded.RestartAlt),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.startOnBoot,
                onSwitchEnabled = {
                    settings.update(settings.settings.value.copy(startOnBoot = it))
                }
            )
            SettingsBox(
                title = stringResource(id = R.string.behavior_permanent_notification),
                description = stringResource(id = R.string.behavior_permanent_notification_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Timer),
                actionType = SettingType.SWITCH,
                variable = !settings.settings.value.permanentNotification,
                onSwitchEnabled = {
                    settings.update(settings.settings.value.copy(permanentNotification = !it))
                }
            )
        }
    }
}
