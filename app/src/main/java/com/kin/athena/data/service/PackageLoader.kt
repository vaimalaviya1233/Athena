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

package com.kin.athena.data.service

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.kin.athena.core.utils.Error
import com.kin.athena.core.logging.Logger
import com.kin.athena.core.utils.Result
import com.kin.athena.core.utils.extensions.usesGooglePlayServices
import com.kin.athena.domain.model.Application
import com.kin.athena.domain.usecase.application.ApplicationUseCases
import com.kin.athena.presentation.screens.settings.viewModel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PackageLoader(
    val settingsViewModel: SettingsViewModel,
    private val packageManager: PackageManager,
    private val applicationUseCases: ApplicationUseCases
) {

    suspend fun loadPackages(): Result<Unit, Error> = withContext(Dispatchers.IO) {
        return@withContext try {
            val allApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            
            val showSystemPackages = settingsViewModel.settings.value.showSystemPackages
            val showOfflinePackages = settingsViewModel.settings.value.showOfflinePackages
            val wifiDefault = settingsViewModel.settings.value.wiFiDefault
            val cellularDefault = settingsViewModel.settings.value.cellularDefault
            
            val filteredApps = allApps.filter { appInfo ->
                val isSystemApp = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                
                if (isSystemApp && !showSystemPackages) {
                    return@filter false
                }
                
                if (!appInfo.enabled && !showOfflinePackages) {
                    return@filter false
                }
                
                true
            }
            
            Logger.info("PackageLoader: Found ${allApps.size} total apps, filtered to ${filteredApps.size} apps (removed ${allApps.size - filteredApps.size} apps)")
            
            // Process apps in small chunks to maintain UI responsiveness
            val chunkSize = 25
            filteredApps.chunked(chunkSize).forEach { chunk ->
                chunk.forEach { appInfo ->
                    val isSystemApp = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                    
                    // Create application entity
                    val packageEntity = Application(
                        packageID = appInfo.packageName,
                        uid = appInfo.uid,
                        systemApp = isSystemApp,
                        usesGooglePlayServices = false, // Defer GPS detection for performance
                        internetAccess = wifiDefault,
                        cellularAccess = cellularDefault
                    )

                    applicationUseCases.checkApplicationExists.execute(packageEntity.packageID).fold(
                        ifSuccess = { exists ->
                            if (!exists) {
                                val result = applicationUseCases.addApplication.execute(packageEntity)
                                if (result is Result.Failure) {
                                    Logger.error("Failed to add package ${packageEntity.packageID} (UID: ${packageEntity.uid}): ${result.error.message}")
                                }
                            }
                        }
                    )
                }
                
                kotlinx.coroutines.yield()
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Logger.error("Error while loading packages: ${e.message}")
            Result.Failure(Error.ServerError(e.message ?: "Error while loading packages"))
        }
    }
}