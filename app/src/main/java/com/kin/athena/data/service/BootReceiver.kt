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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kin.athena.core.logging.Logger
import com.kin.athena.domain.usecase.preferences.PreferencesUseCases
import com.kin.athena.service.utils.manager.FirewallManager
import com.kin.athena.service.utils.manager.FirewallMode
import com.kin.athena.service.utils.manager.VpnManager
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var firewallManager: FirewallManager

    @Inject
    lateinit var preferencesUseCases: PreferencesUseCases

    @Inject
    @ApplicationContext
    lateinit var context: Context

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CoroutineScope(Dispatchers.IO).launch {
                preferencesUseCases.loadSettings.execute().fold(
                    ifSuccess = { settings ->
                        if (settings.startOnBoot) {
                            if (settings.useRootMode == true) {
                                firewallManager.setFirewallMode(FirewallMode.ROOT)
                                firewallManager.startFirewall()

                            } else {
                                if (VpnManager.permissionChecker(context)) {
                                    firewallManager.startFirewall()
                                } else {
                                    Logger.error("Vpn permission not granted")
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}
