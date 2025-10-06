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
import com.kin.athena.BuildConfig
import com.kin.athena.R

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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
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

            Spacer(modifier = Modifier.height(24.dp))

            if (BuildConfig.USE_PLAY_BILLING) {
                // Play Store: Show both options
                // Single Feature Option
                PremiumOptionCard(
                    icon = Icons.Rounded.Lock,
                    title = stringResource(R.string.premium_just_feature, featureName),
                    description = stringResource(R.string.premium_unlock_only_feature),
                    price = singleFeaturePrice ?: stringResource(R.string.common_loading),
                    onClick = onSingleFeaturePurchase
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Full Premium Option with Badge
                PremiumOptionCard(
                    icon = Icons.Rounded.Star,
                    title = stringResource(R.string.premium_lifetime),
                    description = stringResource(R.string.premium_all_features),
                    price = fullPremiumPrice ?: stringResource(R.string.common_loading),
                    onClick = onFullPremiumPurchase,
                    isBestValue = true
                )
            } else {
                // F-Droid: Only show Ko-fi option
                PremiumOptionCard(
                    icon = Icons.Rounded.Star,
                    title = stringResource(R.string.premium_kofi_support),
                    description = stringResource(R.string.premium_all_features),
                    price = stringResource(R.string.premium_one_time),
                    onClick = onFullPremiumPurchase,
                    isBestValue = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PremiumOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    price: String,
    onClick: () -> Unit,
    isBestValue: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isBestValue) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isBestValue) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                
                Text(
                    text = price,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (isBestValue) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}