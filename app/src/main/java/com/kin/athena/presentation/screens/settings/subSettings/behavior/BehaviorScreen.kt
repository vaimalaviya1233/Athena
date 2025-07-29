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
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.SignalCellularOff
import androidx.compose.material.icons.rounded.SignalWifiBad
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kin.athena.R
import com.kin.athena.core.utils.grantRootAccess
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
        title = stringResource(id = R.string.behavior_title),
        onBackNavClicked = { navController.navigateUp() }
    ) {
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.notification_on_install) + " " + stringResource(id = R.string.premium_setting),
                description = stringResource(id = R.string.notification_on_install_description),
                icon = IconType.VectorIcon(Icons.Rounded.InstallMobile),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.sendNotificationOnInstall,
                onSwitchEnabled = {
                    settings.startBilling("notify_on_install") {
                        settings.update(settings.settings.value.copy(sendNotificationOnInstall = it))                    }
                }
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.use_root),
                description = stringResource(id = R.string.use_root_description),
                icon = IconType.VectorIcon(Icons.Rounded.Key),
                actionType = SettingType.SWITCH,
                isEnabled = behaviorViewModel.firewallManager.rulesLoaded.value.name() == FirewallStatus.OFFLINE.name(),
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
                title = stringResource(id = R.string.logs) + " " + stringResource(id = R.string.premium_setting),
                description = stringResource(id = R.string.logs_description),
                icon = IconType.VectorIcon(Icons.Rounded.Code),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.logs,
                onSwitchEnabled = {
                    settings.startBilling("packet_logs") {
                        settings.update(settings.settings.value.copy(logs = it))
                        behaviorViewModel.updateLogs(it)
                    }
                }
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.manage_system_apps),
                description = stringResource(id = R.string.manage_system_apps_description),
                icon = IconType.VectorIcon(Icons.Rounded.SettingsApplications),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.showSystemPackages,
                onSwitchEnabled = {
                    settings.update(settings.settings.value.copy(showSystemPackages = it))
                    behaviorViewModel.updateApp(
                        settings.settings.value.wiFiDefault,
                        settings.settings.value.cellularDefault,
                        settings.settings.value.showSystemPackages
                    )
                }
            )
            SettingsBox(
                title = stringResource(id = R.string.network_permissions_setting),
                description = stringResource(id = R.string.network_permissions_setting_description),
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
                title = stringResource(id = R.string.block_wifi),
                description = stringResource(id = R.string.block_wifi_description),
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
                title = stringResource(id = R.string.block_cellular),
                description = stringResource(id = R.string.block_cellular_description),
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
                title = stringResource(id = R.string.start_on_boot),
                description = stringResource(id = R.string.start_on_boot_description),
                icon = IconType.VectorIcon(Icons.Rounded.RestartAlt),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.startOnBoot,
                onSwitchEnabled = {
                    settings.update(settings.settings.value.copy(startOnBoot = it))
                }
            )
            SettingsBox(
                title = stringResource(id = R.string.permanent_notification),
                description = stringResource(id = R.string.permanent_notification_description),
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
