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

package com.kin.athena.presentation.theme

import android.app.Activity
import android.content.Context
import androidx.compose.material3.Typography
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kin.athena.presentation.screens.settings.viewModel.SettingsViewModel


private fun getColorScheme(context: Context, isDarkTheme: Boolean, isDynamicTheme: Boolean, isAmoledTheme: Boolean): ColorScheme {
    if (isDynamicTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDarkTheme) {
            if (isAmoledTheme) {
                return getDarkDynamicColorScheme(context).copy(background = Color.Black, surface = Color.Black)
            }

            return getDarkDynamicColorScheme(context)
        } else {
            return getLightDynamicColorScheme(context)
        }
    } else if (isDarkTheme) {
        if (isAmoledTheme) {
            return darkScheme.copy(surfaceContainerLow = Color.Black, surface = Color.Black)
        }

        return darkScheme
    } else {
        return lightScheme
    }
}

@Composable
fun EasyWallTheme(
    settingsModel: SettingsViewModel,
    content: @Composable () -> Unit
) {

    val context = LocalContext.current
    val activity = LocalView.current.context as Activity

    if (settingsModel.settings.value.automaticTheme) {
        settingsModel.update(settingsModel.settings.value.copy(dynamicTheme = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S))
        settingsModel.update(settingsModel.settings.value.copy(darkTheme = isSystemInDarkTheme()))
    }

    if (settingsModel.settings.value.screenProtection) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
        isAppearanceLightStatusBars = !settingsModel.settings.value.darkTheme
    }

    MaterialTheme(
        colorScheme = getColorScheme(context, settingsModel.settings.value.darkTheme, settingsModel.settings.value.dynamicTheme, settingsModel.settings.value.amoledTheme),
        typography = Typography(),
        content = content
    )
}