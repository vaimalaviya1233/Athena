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

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kin.athena.R
import com.kin.athena.core.utils.extensions.requestDisableBatteryOptimization
import com.kin.athena.core.utils.isDeviceRooted
import com.kin.athena.service.utils.manager.VpnManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComprehensivePermissionModal(
    context: Context,
    sheetState: SheetState,
    onPermissionsComplete: (useVpn: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var currentStep by remember { mutableStateOf(PermissionStep.VPN_METHOD) }
    var selectedMethod by remember { mutableStateOf<Boolean?>(null) } // true = VPN, false = Root
    
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Proceed to battery optimization regardless of notification permission result
        context.requestDisableBatteryOptimization()
        // Complete the flow
        selectedMethod?.let { useVpn ->
            onPermissionsComplete(useVpn)
        }
    }
    
    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // VPN permission handled, now request notification if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Skip notification request on older versions
            context.requestDisableBatteryOptimization()
            onPermissionsComplete(true) // VPN method
        }
    }

    if (sheetState.isVisible) {
        ModalBottomSheet(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(width = 32.dp, height = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(2.dp)
                        )
                )
            },
            content = {
                when (currentStep) {
                    PermissionStep.VPN_METHOD -> {
                        VpnMethodSelection(
                            context = context,
                            onVpnSelected = { 
                                selectedMethod = true
                                currentStep = PermissionStep.VPN_PERMISSION
                            },
                            onRootSelected = {
                                selectedMethod = false
                                // Root method - skip VPN permission, go straight to notifications
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    context.requestDisableBatteryOptimization()
                                    onPermissionsComplete(false) // Root method
                                }
                            }
                        )
                    }
                    PermissionStep.VPN_PERMISSION -> {
                        VpnPermissionRequest(
                            context = context,
                            vpnLauncher = vpnLauncher
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun VpnMethodSelection(
    context: Context,
    onVpnSelected: () -> Unit,
    onRootSelected: () -> Unit
) {
    val isRooted = isDeviceRooted(context)
    
    Column(
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.vpn_permission_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.vpn_permission_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onVpnSelected,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.method_use_vpn),
                fontWeight = FontWeight.Bold
            )
        }
        
        if (isRooted) {
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = onRootSelected,
                colors = ButtonDefaults.buttonColors().copy(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.permissions_use_root_method),
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun VpnPermissionRequest(
    context: Context,
    vpnLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>
) {
    LaunchedEffect(Unit) {
        val intent = android.net.VpnService.prepare(context)
        if (intent != null) {
            vpnLauncher.launch(intent)
        } else {
            // VPN already permitted, proceed to next step
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Will be handled by the launcher callback
            }
        }
    }
    
    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.permissions_setup),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.permissions_grant_required),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private enum class PermissionStep {
    VPN_METHOD,
    VPN_PERMISSION
}