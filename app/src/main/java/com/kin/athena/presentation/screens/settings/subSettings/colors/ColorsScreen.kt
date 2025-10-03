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

package com.kin.athena.presentation.screens.settings.subSettings.colors

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.HdrAuto
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kin.athena.R
import com.kin.athena.presentation.screens.home.viewModel.HomeViewModel
import com.kin.athena.presentation.screens.settings.components.IconType
import com.kin.athena.presentation.screens.settings.components.ListDialog
import com.kin.athena.presentation.screens.settings.components.SettingsScaffold
import com.kin.athena.presentation.screens.settings.components.SettingType
import com.kin.athena.presentation.screens.settings.components.SettingsBox
import com.kin.athena.presentation.screens.settings.components.settingsContainer
import com.kin.athena.presentation.screens.settings.viewModel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ColorsScreen(
    navController: NavController,
    settings: SettingsViewModel,
    homeViewModel: HomeViewModel,
) {
    SettingsScaffold(
        settings = settings,
        title = stringResource(id = R.string.colors_title),
        onBackNavClicked = { navController.navigateUp() }
    ) {
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.system_theme),
                description = stringResource(id = R.string.system_theme_description),
                icon = IconType.VectorIcon(Icons.Rounded.HdrAuto),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.automaticTheme,
                onSwitchEnabled = { settings.update(settings.settings.value.copy(automaticTheme = it)) }
            )
            SettingsBox(
                title = stringResource(id = R.string.dark_theme),
                description = stringResource(id = R.string.dark_theme_description),
                isEnabled = !settings.settings.value.automaticTheme,
                icon = IconType.VectorIcon(Icons.Rounded.Palette),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.darkTheme,
                onSwitchEnabled = {
                    settings.update(
                        settings.settings.value.copy(
                            automaticTheme = false,
                            darkTheme = it
                        )
                    )
                }
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.dynamic_colors),
                description = stringResource(id = R.string.dynamic_colors_description),
                icon = IconType.VectorIcon(Icons.Rounded.Colorize),
                isEnabled = !settings.settings.value.automaticTheme,
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.dynamicTheme,
                onSwitchEnabled = {
                    settings.update(
                        settings.settings.value.copy(
                            automaticTheme = false,
                            dynamicTheme = it
                        )
                    )
                }
            )
            val iconColors =  MaterialTheme.colorScheme.primary
            SettingsBox(
                title = stringResource(id = R.string.dynamic_icons),
                description = stringResource(id = R.string.dynamic_icons_description),
                icon = IconType.VectorIcon(Icons.Rounded.Apps),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.useDynamicIcons,
                onSwitchEnabled = {
                    settings.update(
                        settings.settings.value.copy(
                            useDynamicIcons = it
                        )
                    )
                    CoroutineScope(Dispatchers.IO).launch {
                        homeViewModel.loadIcons(settings, iconColors)
                    }
                }
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.amoled_colors),
                description = stringResource(id = R.string.amoled_colors_description),
                icon = IconType.VectorIcon(Icons.Rounded.DarkMode),
                actionType = SettingType.SWITCH,
                isEnabled = settings.settings.value.darkTheme,
                variable = settings.settings.value.amoledTheme,
                onSwitchEnabled = { settings.update(settings.settings.value.copy(amoledTheme = it)) }
            )
            SettingsBox(
                title = stringResource(id = R.string.show_disable_dialog),
                description = stringResource(id =  R.string.show_disable_dialog_description),
                icon = IconType.VectorIcon(Icons.Rounded.Security),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.showDialog,
                onSwitchEnabled = { settings.update(settings.settings.value.copy(showDialog = it)) }
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.language),
                description = stringResource(id = R.string.language_description),
                icon = IconType.VectorIcon(Icons.Rounded.Translate),
                actionType = SettingType.CUSTOM,
                customAction = { onExit ->
                    OnLanguageClicked(
                        settings
                    ) { onExit() }
                }
            )
        }
    }
}

@Composable
private fun OnLanguageClicked(settingsViewModel: SettingsViewModel, onExit: () -> Unit) {
    val context = LocalContext.current
    val languages = settingsViewModel.getSupportedLanguages(context).toList()
    ListDialog(
        text = stringResource(R.string.language),
        list = languages,
        onExit = onExit,
        extractDisplayData = { it },
        initialItem = Pair(context.getString(R.string.system_language), second = ""),
        setting = { displayData ->
            SettingsBox(
                size = 8.dp,
                title = displayData.first,
                actionType = SettingType.RADIOBUTTON,
                variable = if (displayData.second.isNotBlank()) {
                    AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag() == displayData.second
                } else {
                    AppCompatDelegate.getApplicationLocales().isEmpty
                },
                onSwitchEnabled = {
                    if (displayData.second.isNotBlank()) {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(displayData.second))
                    } else {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                    }
                }
            )
        }
    )
}