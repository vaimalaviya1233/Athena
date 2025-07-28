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

package com.kin.athena.presentation.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.navigation.NavController
import com.kin.athena.R
import com.kin.athena.core.utils.constants.AppConstants
import com.kin.athena.core.utils.constants.ProjectConstants
import com.kin.athena.core.utils.extensions.safeNavigate
import com.kin.athena.presentation.screens.settings.components.SettingsScaffold
import com.kin.athena.presentation.screens.settings.components.SectionBlock
import com.kin.athena.presentation.screens.settings.components.SettingSection
import com.kin.athena.presentation.navigation.routes.SettingRoutes
import com.kin.athena.presentation.screens.home.viewModel.HomeViewModel
import com.kin.athena.presentation.screens.settings.components.SettingBoxSmall
import com.kin.athena.presentation.screens.settings.components.SettingType
import com.kin.athena.presentation.screens.settings.components.SettingsBox
import com.kin.athena.presentation.screens.settings.components.settingsContainer
import com.kin.athena.presentation.screens.settings.viewModel.SettingsViewModel

@Composable
fun SettingsScreen(
    navController: NavController,
    settings: SettingsViewModel,
) {
    SettingsScaffold(
        settings = settings,
        title = stringResource(id = R.string.settings),
        onBackNavClicked = { navController.navigateUp() }
    ) {

        item {
            val context = LocalContext.current
            SettingBoxSmall(
                title = stringResource(R.string.premium),
                description = stringResource(R.string.support_development),
                onAction = {
                    if (! settings.settings.value.premiumUnlocked) {
                        settings.startBilling("all_features") {
                            settings.update(settings.settings.value.copy(premiumUnlocked = true))
                        }
                    } else {
                        val websiteUri = Uri.parse(ProjectConstants.DONATE)
                        val intent = Intent(Intent.ACTION_VIEW, websiteUri)
                        context.startActivity(intent)
                    }
                }
            )
        }
        item {
            SectionBlock(
                listOf(
                    SettingSection(
                        title = stringResource(id = R.string.colors_title),
                        features = listOf(
                            stringResource(id = R.string.colors_option_theme),
                            stringResource(id = R.string.colors_option_radius),
                            stringResource(id = R.string.colors_option_sort)
                        ),
                        icon = Icons.Rounded.Palette,
                        onClick = { navController.safeNavigate(SettingRoutes.Colors.route) }
                    ),
                    SettingSection(
                        title = stringResource(id = R.string.behavior_title),
                        features = listOf(
                            stringResource(id = R.string.behavior_option_system),
                            stringResource(id = R.string.behavior_option_rules),
                            stringResource(id = R.string.behavior_option_logging)
                        ),
                        icon = Icons.Rounded.RuleFolder,
                        onClick = { navController.safeNavigate(SettingRoutes.Behavior.route) }
                    )
                )
            )
        }
        item {
            SectionBlock(
                listOf(
                    SettingSection(
                        title = stringResource(id = R.string.network_title),
                        features = listOf(
                            stringResource(id = R.string.network_option_lockdown),
                            stringResource(id = R.string.network_option_calling)
                        ),
                        icon = Icons.Rounded.Wifi,
                        onClick = { navController.safeNavigate(SettingRoutes.Network.route) }
                    ),
                    SettingSection(
                        title = stringResource(id = R.string.dns_title),
                        features = listOf(
                            stringResource(id = R.string.dns_option_block),
                            stringResource(id = R.string.dns_option_hostname),
                            stringResource(id = R.string.dns_option_updates)
                        ),
                        icon = Icons.Rounded.Dns,
                        onClick = { navController.safeNavigate(SettingRoutes.Dns.route) }
                    )
                )
            )
        }
        item {
            SectionBlock(
                listOf(
                    SettingSection(
                        title = stringResource(id = R.string.privacy_title),
                        features = listOf(
                            stringResource(id = R.string.privacy_option_lock),
                            stringResource(id = R.string.privacy_option_hide_screen),
                            stringResource(id = R.string.privacy_option_privacy)
                        ),
                        icon = ImageVector.vectorResource(id = R.drawable.incognito),
                        onClick = { navController.safeNavigate(SettingRoutes.Privacy.route) }
                    ),
                    SettingSection(
                        title = stringResource(id = R.string.about_title),
                        features = listOf(
                            stringResource(id = R.string.about_option_version),
                            stringResource(id = R.string.about_option_developers),
                            stringResource(id = R.string.about_option_license)
                        ),
                        icon = Icons.Rounded.Info,
                        onClick = { navController.safeNavigate(SettingRoutes.About.route) }
                    )
                )
            )
        }
    }
}