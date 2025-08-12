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

package com.kin.athena.presentation.screens.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kin.athena.R
import com.kin.athena.core.utils.extensions.*
import com.kin.athena.core.utils.isDeviceRooted
import com.kin.athena.core.utils.isRootGranted
import com.kin.athena.domain.model.Application
import com.kin.athena.presentation.components.PermissionModal
import com.kin.athena.presentation.components.material.MaterialButton
import com.kin.athena.presentation.components.material.MaterialScaffold
import com.kin.athena.presentation.config
import com.kin.athena.presentation.screens.home.components.BottomMenu
import com.kin.athena.presentation.screens.home.components.GrantPermissions
import com.kin.athena.presentation.screens.home.components.RootPermission
import com.kin.athena.presentation.screens.settings.viewModel.SettingsViewModel
import com.kin.athena.presentation.screens.home.components.SearchBar
import com.kin.athena.presentation.screens.home.components.rootResut
import com.kin.athena.presentation.screens.home.viewModel.HomeViewModel
import com.kin.athena.presentation.screens.settings.components.IconType
import com.kin.athena.presentation.screens.settings.components.SettingDialog
import com.kin.athena.presentation.screens.settings.components.SettingType
import com.kin.athena.presentation.screens.settings.components.SettingsBox
import com.kin.athena.presentation.components.material.MaterialText
import com.kin.athena.presentation.components.CircleWrapper
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.dimensionResource
import com.kin.athena.core.utils.extensions.toBitmap
import androidx.compose.runtime.*
import androidx.compose.animation.AnimatedVisibility
import com.kin.athena.presentation.screens.settings.subSettings.dns.components.MagiskSystemlessHostsDialog
import com.kin.athena.presentation.screens.settings.subSettings.dns.root.HostsManager
import com.kin.athena.service.firewall.utils.FirewallStatus
import com.kin.athena.service.utils.manager.FirewallMode
import com.kin.athena.service.utils.manager.VpnManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("CoroutineCreationDuringComposition")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogsClicked: () -> Unit,
    homeViewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    onSettingsClicked: () -> Unit,
    onApplicationClicked: (String) -> Unit,
) {
    val rootState = rememberModalBottomSheetState()
    val sheetState = rememberModalBottomSheetState()
    val disableState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val isFirewallManager = homeViewModel.firewallManager.rulesLoaded.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(true) {
        homeViewModel.observePackages()

    }

    val firewallColor = getFirewallColor(isFirewallManager.value)

    fun onMenuClosed() {
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                homeViewModel.updateMenuStatus(false)
            }
        }
    }

    settingsViewModel.settings.value.logs.let {
        if (homeViewModel.menuStatus.value) {
            BottomMenu(
                sheetState = sheetState,
                onSettingsClicked = onSettingsClicked,
                onLogsClicked = onLogsClicked,
                onExit = { onMenuClosed() }
            )
        }
    }

    if (homeViewModel.magiskError.value) {
        MagiskSystemlessHostsDialog {
            homeViewModel.updateMagiskError(false)
        }
    }

    MaterialScaffold(
        topBar = {
            SearchBar(
                showStartInfo = !settingsViewModel.settings.value.dontShowHelp,
                query = homeViewModel.searchQuery.value,
                onQueryChange = { homeViewModel.updateSearchQueryStatus(it) },
                onClearClick = { homeViewModel.clearSearchQueryStatus() },
                button = {
                    if (settingsViewModel.settings.value.logs) {
                        MaterialButton(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Open Menu"
                        ) {
                            if (homeViewModel.menuStatus.value) {
                                onMenuClosed()
                            } else {
                                homeViewModel.updateMenuStatus(true)
                            }
                        }
                    } else {
                        MaterialButton(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings"
                        ) {
                            onSettingsClicked()
                        }
                    }
                },
                onFirewallClicked = {
                    settingsViewModel.update(settingsViewModel.settings.value.copy(dontShowHelp = true))
                    if (isFirewallManager.value == FirewallStatus.ONLINE) {
                        homeViewModel.setFirewallClicked(true)
                    } else {
                        handleFirewallClick(homeViewModel, context, settingsViewModel, isFirewallManager.value)
                    }
                },
                firewallColor = firewallColor
            )
        },
        content = {
            if (isFirewallManager.value.name() == FirewallStatus.OFFLINE.name()) {
                if (settingsViewModel.settings.value.useRootMode == true) {

                    homeViewModel.checkIfCleanedUp()
                    if (homeViewModel.rootUncleaned.value) {
                        fun onClick() {
                            scope.launch {
                                rootState.hide()
                            }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    homeViewModel.setRootUncleaned(false)
                                }
                            }
                        }
                        PermissionModal(
                            permissionName = stringResource(id = R.string.firewall_terminated),
                            permissionDescription = stringResource(id = R.string.firewall_terminated_description),
                            permissionRequest = stringResource(id = R.string.clear),
                            onDismiss = {
                                onClick()
                            },
                            onPermissionRequest = {
                                homeViewModel.cleanRoot()
                                onClick()
                            },
                            sheetState = rootState,
                            permissionAlternative = stringResource(id = R.string.cancel),
                            onPermissionAlternativeRequest = {
                                onClick()
                            }
                        )
                    }
                }
            }

            if (homeViewModel.firewallClicked.value) {
                fun onClick() {
                    scope.launch {
                        disableState.hide()
                    }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            homeViewModel.setFirewallClicked(false)
                        }
                    }
                }
                if (settingsViewModel.settings.value.showDialog) {
                    PermissionModal(
                        permissionName = stringResource(id = R.string.firewall_disable),
                        permissionDescription = stringResource(id = R.string.firewall_permission_description),
                        permissionRequest = stringResource(id = R.string.disable),
                        onDismiss = {
                            onClick()
                        },
                        onPermissionRequest =  {
                            handleFirewallClick(homeViewModel, context, settingsViewModel, isFirewallManager.value)
                            onClick()
                        },
                        sheetState = disableState,
                        permissionAlternative = stringResource(id = R.string.cancel),
                        onPermissionAlternativeRequest = {
                            onClick()
                        }
                    )
                } else {
                    handleFirewallClick(homeViewModel, context, settingsViewModel, isFirewallManager.value)
                    homeViewModel.setFirewallClicked(false)
                }
            }

            if (isFirewallManager.value.name() == FirewallStatus.LOADING().name()) {
                SettingDialog(text = "Updating Rules", onExit = { /*TODO*/ }) {
                    Column(
                        modifier = Modifier.padding(32.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { homeViewModel.firewallManager.rulesLoaded.value.getRulesLoaded() },
                            modifier = Modifier
                                .clip(RoundedCornerShape(32.dp))
                                .height(16.dp)
                                .fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceDim,
                            drawStopIndicator = {}
                        )
                    }
                }
            }

            HomeScreenContent(settingsViewModel, homeViewModel, context, onApplicationClicked)
            if (settingsViewModel.settings.value.isFirstTimeRunning) {
                GrantPermissions(context = context, viewModel = homeViewModel, settings = settingsViewModel)
                if (isDeviceRooted(context)) {
                    settingsViewModel.update(settingsViewModel.settings.value.copy(useRootMode = false))
                }
            }
        }
    )

    HandleVpnPermission(homeViewModel, context, settingsViewModel, isFirewallManager.value)
}

@Composable
private fun HomeScreenContent(
    settingsViewModel: SettingsViewModel,
    homeViewModel: HomeViewModel,
    context: Context,
    onApplicationClicked: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        val packages = homeViewModel.packages.value.filter { application ->
            val showSystemPackages = settingsViewModel.settings.value.showSystemPackages
            val showOfflinePackages = settingsViewModel.settings.value.showOfflinePackages

            when {
                !showSystemPackages && application.systemApp -> false
                !showOfflinePackages && !application.requiresNetworkPermissions(context.packageManager) -> false
                else -> true
            }
        }.sortedBy { application ->
            application.getApplicationName(context.packageManager)?.lowercase() ?: application.packageID.lowercase()
        }

        PackageList(
            packages = packages,
            viewModel = homeViewModel,
            context = context,
            onApplicationClicked = onApplicationClicked,
            settingsViewModel = settingsViewModel
        )
    }
}

@Composable
private fun PackageList(
    settingsViewModel: SettingsViewModel,
    packages: List<Application>,
    viewModel: HomeViewModel,
    context: Context,
    onApplicationClicked: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.clip(RoundedCornerShape(18.dp,18.dp,0.dp,0.dp))
    ) {
        itemsIndexed(packages) { index, packageEntity ->
            val title = packageEntity.getApplicationName(context.packageManager)
            val icon = viewModel.iconMap.value[packageEntity.packageID]

            val shape = when {
                0 == packages.lastIndex -> RoundedCornerShape(32.dp)
                index == 0 -> RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                index == packages.lastIndex -> RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                else -> RoundedCornerShape(0.dp)
            }

            Column(modifier = Modifier.clip(shape)) {
                if (title != null && icon != null) {
                    val description = if (packageEntity.usesGooglePlayServices()) {
                        "${packageEntity.packageID}\nIncoming messages are fetched from GMS"
                    } else {
                        packageEntity.packageID
                    }
                    
                    CustomSettingsBox(
                        icon = IconType.DrawableIcon(icon),
                        title = title,
                        description = description,
                        actionType = SettingType.CUSTOM,
                        circleWrapperColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        circleWrapperSize = if (settingsViewModel.settings.value.useDynamicIcons) 6.dp else 0.dp,
                        customButton = { AccessControlButtons(packageEntity = packageEntity, viewModel = viewModel)},
                        customAction = {
                            LaunchedEffect(true) {
                                onApplicationClicked(packageEntity.packageID)
                            }
                        },
                        usesGMS = packageEntity.usesGooglePlayServices()
                    )
                } else {
                    viewModel.deleteApplication(packageEntity)
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}


@Composable
private fun AccessControlButtons(
    packageEntity: Application,
    viewModel: HomeViewModel,
) {
    IconButton(onClick = { viewModel.updatePackage(packageEntity.copy(internetAccess = !packageEntity.internetAccess)) }) {
        Icon(
            imageVector = Icons.Rounded.Wifi,
            contentDescription = null,
            tint = getAccessControlTint(packageEntity.internetAccess),
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }

    IconButton(onClick = { viewModel.updatePackage(packageEntity.copy(cellularAccess = !packageEntity.cellularAccess)) }) {
        Icon(
            imageVector = Icons.Rounded.SignalCellularAlt,
            contentDescription = null,
            tint = getAccessControlTint(packageEntity.cellularAccess)
        )
    }
}


@Composable
private fun getFirewallColor(isFirewallActive: FirewallStatus?): Color {
    return when (isFirewallActive) {
        null -> MaterialTheme.colorScheme.error
        FirewallStatus.ONLINE -> MaterialTheme.colorScheme.primary
        FirewallStatus.OFFLINE -> MaterialTheme.colorScheme.error
        else ->  MaterialTheme.colorScheme.error
    }
}

private fun handleFirewallClick(homeViewModel: HomeViewModel, context: Context, settingsViewModel: SettingsViewModel, isFirewallManager: FirewallStatus) {
    when (settingsViewModel.settings.value.useRootMode) {
        true -> {
            CoroutineScope(Dispatchers.IO).launch {
                if (isRootGranted()) {
                    val hostManager = HostsManager(context, emptyList()).isHostsWritable()
                    if (config.hosts.items.isNotEmpty() && !hostManager.first)  {
                        homeViewModel.updateMagiskError(true)
                    } else {
                        homeViewModel.updateFirewallStatus(isFirewallManager.not(), FirewallMode.ROOT)
                    }
                } else {
                    settingsViewModel.update(settingsViewModel.settings.value.copy(useRootMode = false))
                }
            }
        }
        false -> {
            homeViewModel.updateVpnPermissionStatus(true)
        }
        null -> {
            if (VpnManager.permissionChecker(context)) {
                homeViewModel.updateFirewallStatus(isFirewallManager.not(), FirewallMode.VPN)
            } else {
                homeViewModel.updateVpnPermissionStatus(true)
            }
        }
    }
}

@Composable
private fun getAccessControlTint(isAccessEnabled: Boolean): Color {
    return if (isAccessEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
}


@Composable
private fun HandleVpnPermission(homeViewModel: HomeViewModel, context: Context, settingsViewModel: SettingsViewModel, isFirewallManager: FirewallStatus) {
    if (homeViewModel.vpnPermissionRequested.value && isFirewallManager != FirewallStatus.ONLINE) {
        when (settingsViewModel.settings.value.useRootMode) {
            false -> {
                fun useVPN() {
                    settingsViewModel.update(
                        settingsViewModel.settings.value.copy(
                            useRootMode = null
                        )
                    )
                    homeViewModel.updateVpnPermissionStatus(false)
                    handleFirewallClick(homeViewModel, context, settingsViewModel, isFirewallManager)
                }

                RootPermission(viewModel = homeViewModel) {
                    when (it) {
                        rootResut.VPN -> {
                            useVPN()
                        }
                        rootResut.DENIED -> {
                            useVPN()
                        }
                        rootResut.GRANTED -> {
                            settingsViewModel.update(
                                settingsViewModel.settings.value.copy(
                                    useRootMode = true
                                )
                            )
                            homeViewModel.updateVpnPermissionStatus(false)
                            if (config.hosts.items.isEmpty()) {
                                homeViewModel.updateFirewallStatus(FirewallStatus.ONLINE, FirewallMode.ROOT)
                            }
                        }
                        rootResut.CLOSED -> {
                            homeViewModel.updateVpnPermissionStatus(false)

                        }
                    }
                }
            }
            true -> {
                homeViewModel.updateFirewallStatus(FirewallStatus.ONLINE, FirewallMode.ROOT)
            }
            null -> {
                VpnManager.PermissionRequester(context, homeViewModel.vpnPermissionRequested.value) {
                    if (it) {
                        homeViewModel.updateFirewallStatus(value = FirewallStatus.ONLINE, serviceType = FirewallMode.VPN)
                    }
                    homeViewModel.updateVpnPermissionStatus(isRequested = false)
                }
            }
        }
    }
}

@Composable
private fun CustomSettingsBox(
    title: String,
    description: String,
    icon: IconType,
    actionType: SettingType,
    circleWrapperColor: Color,
    circleWrapperSize: androidx.compose.ui.unit.Dp,
    customButton: @Composable () -> Unit,
    customAction: @Composable (() -> Unit) -> Unit,
    usesGMS: Boolean
) {
    val context = LocalContext.current
    var showCustomAction by remember { mutableStateOf(false) }
    if (showCustomAction) customAction { showCustomAction = !showCustomAction }

    AnimatedVisibility(visible = true) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .padding(bottom = dimensionResource(id = R.dimen.card_padding_bottom))
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable {
                    showCustomAction = !showCustomAction
                }
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(
                        horizontal = dimensionResource(id = R.dimen.card_padding_horizontal),
                        vertical = 12.dp
                    )
                    .fillMaxWidth()
            ) {
                androidx.compose.foundation.layout.Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    when (icon) {
                        is IconType.DrawableIcon -> {
                            CircleWrapper(
                                size = circleWrapperSize,
                                color = circleWrapperColor
                            ) {
                                Image(
                                    bitmap = icon.drawable.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .height(38.dp - circleWrapperSize)
                                        .width(38.dp - circleWrapperSize)
                                )
                            }
                        }
                        is IconType.VectorIcon -> {
                            CircleWrapper(
                                size = 12.dp,
                                color = circleWrapperColor
                            ) {
                                Icon(
                                    imageVector = icon.imageVector,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    
                    if (usesGMS) {
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                            Text(
                                text = description.substringBefore('\n'),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                            Text(
                                text = "This app uses Google Play Services to receive incoming messages, meaning it cannot be blocked directly.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                    } else {
                        MaterialText(
                            title = title,
                            description = description
                        )
                    }
                }
                customButton()
            }
        }
    }
}
