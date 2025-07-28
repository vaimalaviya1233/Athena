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

package com.kin.athena.presentation.screens.settings.subSettings.privacy

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.filled.Carpenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.rounded.DoDisturbAlt
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.kin.athena.R
import com.kin.athena.core.utils.extensions.isFingerprintSupported
import com.kin.athena.core.utils.extensions.safeNavigate
import com.kin.athena.presentation.navigation.routes.HomeRoutes
import com.kin.athena.presentation.navigation.routes.SettingRoutes
import com.kin.athena.presentation.screens.settings.components.CustomListDialog
import com.kin.athena.presentation.screens.settings.components.IconType
import com.kin.athena.presentation.screens.settings.components.SettingType
import com.kin.athena.presentation.screens.settings.components.SettingsBox
import com.kin.athena.presentation.screens.settings.components.SettingsScaffold
import com.kin.athena.presentation.screens.settings.components.settingsContainer
import com.kin.athena.presentation.screens.settings.subSettings.lock.components.ActionType
import com.kin.athena.presentation.screens.settings.viewModel.SettingsViewModel

@Composable
fun PrivacyScreen(
    navController: NavController,
    settings: SettingsViewModel,
) {
    SettingsScaffold(
        settings = settings,
        title = stringResource(id = R.string.privacy_title),
        onBackNavClicked = { navController.navigateUp() }
    ) {
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.screen_protection),
                description = stringResource(id = R.string.screen_protection_description),
                icon = IconType.VectorIcon(Icons.Filled.RemoveRedEye),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.screenProtection,
                onSwitchEnabled = { settings.update(settings.settings.value.copy(screenProtection = it)) },
            )
            SettingsBox(
                title = stringResource(id = R.string.app_lock),
                description = stringResource(id = R.string.app_lock_description),
                icon = IconType.VectorIcon(Icons.Filled.Lock),
                actionType = SettingType.CUSTOM,
                customAction = { onExit ->
                    OnLockClicked(onExit = { onExit() }, navController = navController, settings = settings)
                },
            )
            SettingsBox(
                isEnabled = settings.settings.value.passcode != null || settings.settings.value.pattern != null || settings.settings.value.fingerprint,
                title = stringResource(id = R.string.lock_on_resume),
                description = stringResource(id = R.string.lock_on_resume_description),
                icon = IconType.VectorIcon(Icons.Rounded.LockReset),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.lockImmediately,
                onSwitchEnabled = { settings.update(settings.settings.value.copy(lockImmediately = it)) },
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.block_port_80),
                description = stringResource(id = R.string.block_port_80_description),
                icon = IconType.VectorIcon(Icons.Filled.Report),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.blockPort80,
                onSwitchEnabled = { settings.update(settings.settings.value.copy(blockPort80 = it)) },
            )
            SettingsBox(
                title = stringResource(id = R.string.arp_spoofing_protection),
                description = stringResource(id = R.string.in_development),
                icon = IconType.VectorIcon(Icons.Filled.Carpenter),
                actionType = SettingType.TEXT,
            )
            SettingsBox(
                title = stringResource(id = R.string.dns_rebind_protection),
                description = stringResource(id = R.string.in_development),
                icon = IconType.VectorIcon(Icons.Filled.Security),
                actionType = SettingType.TEXT,
            )
        }
    }
}

@Composable
private fun OnLockClicked(
    settings: SettingsViewModel,
    onExit: () -> Unit,
    navController: NavController
) {
    val context = LocalContext.current

    CustomListDialog(
        text = stringResource(id = R.string.app_lock),
        onExit = onExit
    ) {
        item {
            CreateSettingBox(
                title = stringResource(id = R.string.passcode),
                description = stringResource(id = R.string.authorize_passcode),
                isEnabled = settings.settings.value.passcode.isNullOrBlank(),
                onAction = {
                    onExit()
                    if (settings.settings.value.passcode.isNullOrBlank()) {
                        navController.safeNavigate(SettingRoutes.LockScreen.createRoute(ActionType.PASSCODE))
                    } else {
                        settings.update(
                            settings.settings.value.copy(
                                passcode = null,
                                defaultRoute = HomeRoutes.Home.route
                            )
                        )
                        settings.loadDefaultRoute()
                    }
                }
            )
            if (context.isFingerprintSupported()) {
                CreateSettingBox(
                    title = stringResource(id = R.string.fingerprint),
                    description = stringResource(id = R.string.authorize_fingerprint),
                    isEnabled = !settings.settings.value.fingerprint,
                    onAction = {
                        onExit()
                        if (!settings.settings.value.fingerprint) {
                            navController.safeNavigate(SettingRoutes.LockScreen.createRoute(ActionType.FINGERPRINT))
                        } else {
                            settings.update(
                                settings.settings.value.copy(
                                    fingerprint = false,
                                    defaultRoute = HomeRoutes.Home.route
                                )
                            )
                            settings.loadDefaultRoute()
                        }
                    }
                )
            }
            CreateSettingBox(
                title = stringResource(id = R.string.pattern),
                description = stringResource(id = R.string.authorize_pattern),
                isEnabled = settings.settings.value.pattern.isNullOrBlank(),
                onAction = {
                    onExit()
                    if (settings.settings.value.pattern.isNullOrBlank()) {
                        navController.safeNavigate(SettingRoutes.LockScreen.createRoute(ActionType.PATTERN))
                    } else {
                        settings.update(
                            settings.settings.value.copy(
                                pattern = null,
                                defaultRoute = HomeRoutes.Home.route
                            )
                        )
                        settings.loadDefaultRoute()
                    }
                }
            )
        }
    }
}

@Composable
private fun CreateSettingBox(
    title: String,
    description: String,
    isEnabled: Boolean,
    onAction: () -> Unit
) {
    SettingsBox(
        title = title,
        description = description,
        actionType = SettingType.CUSTOM,
        customAction = {
            LaunchedEffect(Unit) {
                onAction()
            }
        },
        customButton = {
            Icon(
                imageVector = if (isEnabled) {
                    Icons.AutoMirrored.Rounded.ArrowForwardIos
                } else {
                    Icons.Rounded.DoDisturbAlt
                },
                contentDescription = "",
                modifier = Modifier.scale(0.75f)
            )
        }
    )
}
