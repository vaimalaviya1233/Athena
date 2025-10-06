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

package com.kin.athena.presentation.screens.settings.subSettings.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ContactSupport
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.kin.athena.R
import com.kin.athena.core.utils.constants.AppConstants
import com.kin.athena.core.utils.constants.ProjectConstants
import com.kin.athena.core.utils.extensions.safeNavigate
import com.kin.athena.presentation.components.material.MaterialTextField
import com.kin.athena.presentation.navigation.routes.HomeRoutes
import com.kin.athena.presentation.screens.settings.components.IconType
import com.kin.athena.presentation.screens.settings.components.SettingDialog
import com.kin.athena.presentation.screens.settings.components.SettingType
import com.kin.athena.presentation.screens.settings.components.SettingsBox
import com.kin.athena.presentation.screens.settings.components.SettingsScaffold
import com.kin.athena.presentation.screens.settings.components.settingsContainer
import com.kin.athena.presentation.screens.settings.subSettings.about.components.LogoWithBlob
import com.kin.athena.presentation.screens.settings.subSettings.about.components.SettingsVerification
import com.kin.athena.presentation.screens.settings.viewModel.SettingsViewModel
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Gavel
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext

@Composable
fun AboutScreen(
    navController: NavController,
    settings: SettingsViewModel,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    
    // Function to handle rate app - tries Play Store first, falls back to web
    fun openRateApp() {
        try {
            // Try to open Play Store app first
            val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ProjectConstants.PLAY_STORE_URL))
            context.startActivity(playStoreIntent)
        } catch (e: ActivityNotFoundException) {
            // Fallback to web browser if Play Store app is not available
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ProjectConstants.PLAY_STORE_WEB_URL))
                context.startActivity(webIntent)
            } catch (e: ActivityNotFoundException) {
                // Last fallback - open project page
                uriHandler.openUri(ProjectConstants.PROJECT_DOWNLOADS)
            }
        }
    }
    
    SettingsScaffold(
        settings = settings,
        title = stringResource(id = R.string.about_title),
        onBackNavClicked = { navController.navigateUp() }
    ) {
        item {
            LogoWithBlob {
                navController.safeNavigate(HomeRoutes.Debug.route)
            }
        }
        item {
            SettingsVerification(
                isValid = settings.getAppSignature() == ProjectConstants.SHA_256_SIGNING,
                title = stringResource(id = R.string.about_verified_build),
                description = stringResource(id = R.string.about_maintained_by) + " " + ProjectConstants.DEVELOPER
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.about_translator),
                description = stringResource(id = R.string.about_your_name),
                actionType = SettingType.TEXT,
                icon = IconType.VectorIcon(Icons.Rounded.Translate),
            )
            SettingsBox(
                title = stringResource(id = R.string.details_version),
                description = settings.version,
                icon = IconType.VectorIcon(Icons.Rounded.Info),
                actionType = SettingType.TEXT,
            )
            SettingsBox(
                title = stringResource(id = R.string.about_build_type),
                description = settings.build,
                icon = IconType.VectorIcon(Icons.Rounded.Build),
                actionType = SettingType.TEXT,
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.about_latest_release),
                icon = IconType.VectorIcon(Icons.Rounded.Verified),
                actionType = SettingType.LINK,
                onLinkClicked = { uriHandler.openUri(ProjectConstants.PROJECT_DOWNLOADS) },
            )
            SettingsBox(
                title = stringResource(id = R.string.about_source_code),
                icon = IconType.VectorIcon(Icons.Rounded.Download),
                actionType = SettingType.LINK,
                onLinkClicked = { uriHandler.openUri(ProjectConstants.PROJECT_SOURCE_CODE) }
            )
            SettingsBox(
                title = stringResource(id = R.string.settings_license),
                description = stringResource(id = R.string.license_description),
                icon = IconType.VectorIcon(Icons.Rounded.Gavel),
                actionType = SettingType.LINK,
                onLinkClicked = { uriHandler.openUri(ProjectConstants.LICENSE_URL) }
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.about_email),
                icon = IconType.VectorIcon(Icons.Rounded.Email),
                clipboardText = ProjectConstants.SUPPORT_MAIL,
                actionType = SettingType.CLIPBOARD,
            )
            SettingsBox(
                title = stringResource(id = R.string.about_discord),
                icon = IconType.VectorIcon(Icons.AutoMirrored.Rounded.ContactSupport),
                actionType = SettingType.LINK,
                onLinkClicked = { uriHandler.openUri(ProjectConstants.SUPPORT_DISCORD) },
            )
            SettingsBox(
                title = stringResource(id = R.string.about_feature),
                icon = IconType.VectorIcon(Icons.Rounded.BugReport),
                onLinkClicked = { uriHandler.openUri(ProjectConstants.GITHUB_FEATURE_REQUEST) },
                actionType = SettingType.LINK,
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.about_rate_app),
                description = stringResource(id = R.string.about_rate_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Star),
                actionType = SettingType.LINK,
                onLinkClicked = { openRateApp() }
            )
            SettingsBox(
                title = stringResource(id = R.string.about_translate),
                description = stringResource(id = R.string.about_translate_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Translate),
                actionType = SettingType.LINK,
                onLinkClicked = { uriHandler.openUri(AppConstants.Links.TRANSLATE) }
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.premium_code),
                icon = IconType.VectorIcon(Icons.Rounded.Numbers),
                actionType = SettingType.CUSTOM,
                customAction = { onExit ->
                    PremiumCodeDialog(
                        settings =  settings,
                        onExit = { onExit() },
                    )
                }
            )
        }
    }
}

@Composable
fun PremiumCodeDialog(
    onExit: () -> Unit,
    settings: SettingsViewModel
) {
    var code by remember { mutableStateOf(TextFieldValue("")) }
    var notificationMessage by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }

    // Auto-clear notification after delay
    LaunchedEffect(notificationMessage) {
        if (notificationMessage.isNotEmpty()) {
            delay(3000)
            if (notificationMessage.startsWith("✅")) {
                onExit() // Exit dialog on success after showing message
            }
            notificationMessage = ""
        }
    }

    SettingDialog(
        text = stringResource(id = R.string.premium_code),
        onExit = { onExit() }
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                MaterialTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = code,
                    onValueChange = { code = it },
                    placeholder = "Enter premium code",
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Show notification message with Material You design
            if (notificationMessage.isNotEmpty()) {
                val isSuccess = notificationMessage.startsWith("✅")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSuccess) 
                            MaterialTheme.colorScheme.tertiaryContainer
                        else 
                            MaterialTheme.colorScheme.errorContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                            contentDescription = null,
                            tint = if (isSuccess)
                                MaterialTheme.colorScheme.onTertiaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            text = notificationMessage.removePrefix("✅ ").removePrefix("❌ "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSuccess)
                                MaterialTheme.colorScheme.onTertiaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    if (code.text.isNotBlank() && !isVerifying) {
                        isVerifying = true
                        settings.verifyLicense(code.text) { success, message ->
                            isVerifying = false
                            notificationMessage = message
                        }
                    }
                },
                enabled = !isVerifying && code.text.isNotBlank(),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(text = if (isVerifying) "Verifying..." else stringResource(id = R.string.common_done))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}