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

package com.kin.athena.presentation.screens.home.viewModel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.drawable.Drawable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kin.athena.core.utils.Result
import com.kin.athena.core.utils.extensions.getApplicationName
import com.kin.athena.core.logging.Logger
import com.kin.athena.core.utils.Shell
import com.kin.athena.core.utils.extensions.getApplicationIcon
import com.kin.athena.data.service.PackageLoader
import com.kin.athena.domain.model.Application
import com.kin.athena.domain.usecase.application.ApplicationUseCases
import com.kin.athena.service.utils.receiver.AppChangeCallback
import com.kin.athena.service.utils.receiver.AppChangeReceiver
import com.kin.athena.presentation.screens.settings.viewModel.SettingsViewModel
import com.kin.athena.service.firewall.utils.FirewallStatus
import com.kin.athena.service.utils.manager.FirewallManager
import com.kin.athena.service.utils.manager.FirewallMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val applicationUseCases: ApplicationUseCases,
    val firewallManager: FirewallManager
) : ViewModel() {
    val iconMap: MutableState<Map<String, Drawable?>> = mutableStateOf(emptyMap())

    private val _firewallClicked = mutableStateOf(false)
    val firewallClicked: State<Boolean> get() = _firewallClicked

    private val _unfilteredPackages = mutableStateOf<List<Application>>(emptyList())

    private val _packages = mutableStateOf<List<Application>>(emptyList())
    val packages: State<List<Application>> = _packages

    private val _searchQuery = mutableStateOf("")
    val searchQuery: State<String> = _searchQuery

    private val _rootUncleaned = mutableStateOf(false)
    val rootUncleaned: State<Boolean> get() = _rootUncleaned

    private val _magiskError = mutableStateOf(false)
    val magiskError: State<Boolean> get() = _magiskError

    private val _menuStatus: MutableState<Boolean> = mutableStateOf(false)
    val menuStatus: State<Boolean> = _menuStatus

    private val _notificationPermissionRequested = mutableStateOf(false)
    val notificationPermissionRequested: State<Boolean> = _notificationPermissionRequested

    private val _rootPermissionRequested = mutableStateOf(false)
    val rootPermissionRequested: State<Boolean> = _rootPermissionRequested

    private val _vpnPermissionRequested = mutableStateOf(false)
    val vpnPermissionRequested: State<Boolean> = _vpnPermissionRequested

    private var appChangeReceiver: AppChangeReceiver? = null

    fun setRootUncleaned(value: Boolean) {
        _rootUncleaned.value = value
    }

    fun updateMagiskError(value: Boolean) {
        _magiskError.value = value
    }


    fun setFirewallClicked(clicked: Boolean) {
        _firewallClicked.value = clicked
    }

    fun updateMenuStatus(value: Boolean) {
        _menuStatus.value = value
    }

    fun updateSearchQueryStatus(value: String) {
        _searchQuery.value = value
        filterPackages(value)
    }

    fun clearSearchQueryStatus() {
        _searchQuery.value = ""
        _packages.value = _unfilteredPackages.value
    }

    fun updateFirewallStatus(value: FirewallStatus, serviceType: FirewallMode) {
        firewallManager.setFirewallMode(serviceType)
        value?.let {
            if (it.name() == FirewallStatus.ONLINE.name()) firewallManager.startFirewall() else if (it.name() == FirewallStatus.OFFLINE.name()) firewallManager.stopFirewall()
        }
    }


    fun updateNotificationPermissionStatus(isRequested: Boolean) {
        _notificationPermissionRequested.value = isRequested
    }

    fun updateRootPermissionStatus(isRequested: Boolean) {
        _rootPermissionRequested.value = isRequested
    }

    fun updateVpnPermissionStatus(isRequested: Boolean) {
        _vpnPermissionRequested.value = isRequested
    }

    private fun filterPackages(query: String) {
        val currentPackageName = context.packageName

        _packages.value = if (query.isBlank()) {
            _unfilteredPackages.value.filter { application ->
                application.packageID != currentPackageName
            }
        } else {
            _unfilteredPackages.value.filter { application ->
                val appName = application.getApplicationName(context.packageManager)
                appName?.let {
                    (appName.contains(query, ignoreCase = true) ||
                            application.packageID.contains(query, ignoreCase = true) ||
                            application.uid.toString().contains(query)) &&
                            application.packageID != currentPackageName
                } ?: false
            }
        }
    }

    fun updatePackage(packageEntity: Application) {
        viewModelScope.launch {
            Logger.error("Updating $packageEntity")
            applicationUseCases.updateApplication.execute(packageEntity)
            val existingPackages = _packages.value.toMutableList()
            val index = existingPackages.indexOfFirst { it.uid == packageEntity.uid }
            existingPackages[index] = packageEntity
            _packages.value = existingPackages
            observePackages()
            if (firewallManager.rulesLoaded.value == FirewallStatus.ONLINE) {
                firewallManager.updateFirewallRules(packageEntity)
            }
        }
    }

    fun addPackageByName(name: String, settingsViewModel: SettingsViewModel) {
        viewModelScope.launch {
            try {
                val info = context.packageManager.getApplicationInfo(name, 0)
                applicationUseCases.addApplication.execute(
                    Application(name, info.uid, info.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                )
                loadPackages(settingsViewModel)
            } catch (e: NameNotFoundException) {
               Logger.error("Package $name not found.")
            }
        }
    }

    suspend fun initialize(settingsViewModel: SettingsViewModel) {
        loadPackages(settingsViewModel)
        appChangeReceiver = AppChangeReceiver(createAppChangeCallback(settingsViewModel))
        appChangeReceiver?.register(context)
    }

    fun loadIcons(settingsViewModel: SettingsViewModel, color: Color) {
        iconMap.value = packages.value.mapNotNull { application ->
            try {
                val icon = application.getApplicationIcon(
                    context.packageManager,
                    tintColor = color.toArgb(),
                    useDynamicIcon = settingsViewModel.settings.value.useDynamicIcons,
                    context
                )
                application.packageID to icon
            } catch (e: Exception) {
                Logger.error("Failed to load icon for ${application.packageID}: ${e.message}", e)
                // Return a pair with null icon instead of crashing
                application.packageID to null
            }
        }.toMap()
    }

    private suspend fun loadPackages(settingsViewModel: SettingsViewModel) {
        PackageLoader(settingsViewModel, context.packageManager, applicationUseCases).loadPackages()
        observePackages()
    }

    suspend fun observePackages() {
        when (val result = applicationUseCases.getApplications.execute()) {
            is Result.Success -> {
                _unfilteredPackages.value = result.data
                filterPackages(_searchQuery.value)
                firewallManager.updateFirewallRules(null)
            }
            is Result.Failure -> Logger.error(result.error.stackTraceToString())
        }
    }

    private fun createAppChangeCallback(settingsViewModel: SettingsViewModel) = object : AppChangeCallback {
        override fun onAppInstalled(packageName: String?) {
            packageName?.let { addPackageByName(it, settingsViewModel) }
        }

        override fun onAppUninstalled(packageName: String?) {
            packageName?.let {
                viewModelScope.launch {
                    val application = applicationUseCases.getApplication.execute(it)
                    application.fold(
                        ifSuccess = { applicationModel ->
                            applicationUseCases.deleteApplication.execute(applicationModel)
                        }
                    )
                }
            }
        }
    }

    fun deleteApplication(application: Application) {
        viewModelScope.launch {
            applicationUseCases.deleteApplication.execute(application)
        }
    }


    fun checkIfCleanedUp() {
        val shell = Shell("su")

        shell.addOnStderrLineListener( object : Shell.OnLineListener {
            override fun onLine(line: String) {
                val lines = line.toIntOrNull()

                lines?.let {
                    if (lines !in 0..2) {
                        setRootUncleaned(true)
                    }
                }
            }
        })

        shell.addOnStdoutLineListener( object : Shell.OnLineListener {
            override fun onLine(line: String) {
                val lines = line.toIntOrNull()

                lines?.let {
                    if (lines !in 0..2) {
                        setRootUncleaned(true)
                    }
                }
            }
        })

        shell.run("iptables -L EasyApps -w | wc -l")
    }

    fun cleanRoot() {
        viewModelScope.launch {
            val shell = Shell("su")

            shell.run("iptables -F -w && ip6tables -F -w")
            shell.run("iptables -t nat -F OUTPUT")
        }
    }
}
