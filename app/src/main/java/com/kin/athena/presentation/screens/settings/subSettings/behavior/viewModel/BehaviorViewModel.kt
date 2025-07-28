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

package com.kin.athena.presentation.screens.settings.subSettings.behavior.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kin.athena.core.logging.Logger
import com.kin.athena.domain.usecase.application.ApplicationUseCases
import com.kin.athena.service.utils.manager.FirewallManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BehaviorViewModel @Inject constructor(
    val firewallManager: FirewallManager,
    private val applicationUseCases: ApplicationUseCases
) : ViewModel() {
    fun updateLogs(enabled: Boolean) {
        viewModelScope.launch {
            firewallManager.updateLogs(enabled)
        }
    }


    fun updateApp(wifi: Boolean, cellular: Boolean, updateSystemApps: Boolean) {
        viewModelScope.launch {
            applicationUseCases.getApplications.execute().fold(
                ifSuccess = { applications ->
                    applications.forEach { application ->
                        if (updateSystemApps) {
                            applicationUseCases.updateApplication.execute(application.copy(internetAccess = wifi, cellularAccess = cellular))
                        } else {
                            if (!application.systemApp) {
                                applicationUseCases.updateApplication.execute(application.copy(internetAccess = wifi, cellularAccess = cellular))
                            }
                        }
                    }
                }
            )
        }
    }
}