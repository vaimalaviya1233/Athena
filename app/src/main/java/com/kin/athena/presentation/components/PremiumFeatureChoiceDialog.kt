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

package com.kin.athena.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.kin.athena.BuildConfig
import com.kin.athena.R
import com.kin.athena.presentation.screens.settings.components.SettingsBox
import com.kin.athena.presentation.screens.settings.components.SettingType
import com.kin.athena.presentation.screens.settings.components.IconType
import com.kin.athena.presentation.screens.settings.components.settingsContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumFeatureChoiceDialog(
    featureName: String,
    featureDescription: String,
    singleFeaturePrice: String?,
    fullPremiumPrice: String?,
    onSingleFeaturePurchase: () -> Unit,
    onFullPremiumPurchase: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Feature Icon
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title
                    Text(
                        text = stringResource(R.string.premium_unlock_feature, featureName),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Description
                    Text(
                        text = if (BuildConfig.USE_PLAY_BILLING) {
                            stringResource(R.string.premium_feature_description, featureDescription)
                        } else {
                            stringResource(R.string.premium_kofi_description, featureName)
                        },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            settingsContainer {
                if (BuildConfig.USE_PLAY_BILLING) {
                    SettingsBox(
                        title = stringResource(R.string.premium_just_feature, featureName),
                        description = stringResource(R.string.premium_unlock_only_feature),
                        icon = IconType.VectorIcon(Icons.Rounded.Lock),
                        actionType = SettingType.LINK,
                        onLinkClicked = {
                            onSingleFeaturePurchase()
                            onDismiss()
                        },
                        customButton = {
                            Text(
                                text = singleFeaturePrice ?: stringResource(R.string.common_loading),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    SettingsBox(
                        title = stringResource(R.string.premium_lifetime),
                        description = stringResource(R.string.premium_all_features),
                        icon = IconType.VectorIcon(Icons.Rounded.Star),
                        actionType = SettingType.LINK,
                        onLinkClicked = {
                            onFullPremiumPurchase()
                            onDismiss()
                        },
                        circleWrapperColor = MaterialTheme.colorScheme.primaryContainer,
                        customButton = {
                            Text(
                                text = fullPremiumPrice ?: stringResource(R.string.common_loading),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                } else {
                    // F-Droid: Only show Ko-fi option
                    SettingsBox(
                        title = stringResource(R.string.premium_kofi_support),
                        description = stringResource(R.string.premium_all_features),
                        icon = IconType.VectorIcon(Icons.Rounded.Star),
                        actionType = SettingType.LINK,
                        onLinkClicked = {
                            onFullPremiumPurchase()
                            onDismiss()
                        },
                        circleWrapperColor = MaterialTheme.colorScheme.primaryContainer,
                        customButton = {
                            Text(
                                text = stringResource(R.string.premium_one_time),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }
            }
        }
    }
}

