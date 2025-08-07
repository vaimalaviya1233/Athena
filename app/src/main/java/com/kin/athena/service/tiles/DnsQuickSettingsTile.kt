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

package com.kin.athena.service.tiles

import android.graphics.drawable.Icon
import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.kin.athena.R
import com.kin.athena.core.logging.Logger
import com.kin.athena.service.utils.manager.VpnManager
import com.kin.athena.service.utils.manager.FirewallManager
import com.kin.athena.service.vpn.service.VpnConnectionServer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Quick Settings tile for toggling DNS blocking functionality
 * Allows users to quickly enable/disable DNS blocking from the notification shade
 * while keeping the VPN service running
 */
@AndroidEntryPoint
class DnsQuickSettingsTile : TileService() {

    @Inject lateinit var firewallManager: FirewallManager

    override fun onTileAdded() {
        super.onTileAdded()
        Logger.info("DNS Quick Settings tile added")
        updateTileState()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        
        try {
            if (!isVpnActive()) {
                // VPN is not running, cannot toggle DNS blocking
                Logger.warn("Quick Settings: VPN not active, cannot toggle DNS blocking")
                showToast("VPN must be running to toggle DNS blocking")
                openApp()
                return
            }
            
            val isDnsBlockingEnabled = firewallManager.isDnsBlockingEnabled()
            
            if (isDnsBlockingEnabled) {
                // DNS blocking is enabled, disable it
                Logger.info("Quick Settings: Disabling DNS blocking")
                firewallManager.setDnsBlocking(false)
                showToast("DNS Blocking Disabled")
            } else {
                // DNS blocking is disabled, enable it
                Logger.info("Quick Settings: Enabling DNS blocking")
                firewallManager.setDnsBlocking(true)
                showToast("DNS Blocking Enabled")
            }
            
            // Update tile state after action
            updateTileState()
            
        } catch (e: Exception) {
            Logger.error("Quick Settings tile error: ${e.message}", e)
            showToast("Error toggling DNS blocking")
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        
        try {
            val isVpnActive = isVpnActive()
            val isDnsBlockingEnabled = if (isVpnActive) firewallManager.isDnsBlockingEnabled() else false
            
            when {
                isVpnActive && isDnsBlockingEnabled -> {
                    // VPN is running and DNS blocking is enabled
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = "DNS Blocking"
                    tile.subtitle = "Active"
                    tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_shield_check)
                }
                isVpnActive && !isDnsBlockingEnabled -> {
                    // VPN is running but DNS blocking is disabled
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = "DNS Blocking"
                    tile.subtitle = "Tap to enable"
                    tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_shield)
                }
                else -> {
                    // VPN is not running
                    tile.state = Tile.STATE_UNAVAILABLE
                    tile.label = "DNS Blocking"
                    tile.subtitle = "VPN required"
                    tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_shield_off)
                }
            }
            
            tile.updateTile()
            
        } catch (e: Exception) {
            Logger.error("Error updating tile state: ${e.message}", e)
            
            // Fallback state
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "DNS Blocking"
            tile.subtitle = "Error"
            tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_shield_off)
            tile.updateTile()
        }
    }

    private fun isVpnActive(): Boolean {
        return try {
            // Check if our VPN service is running
            val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningServices = activityManager.getRunningServices(Int.MAX_VALUE)
            
            val serviceRunning = runningServices.any { serviceInfo ->
                serviceInfo.service.className == VpnConnectionServer::class.java.name ||
                serviceInfo.service.className.contains("VpnConnectionClient")
            }
            
            Logger.debug("VPN Status - service running: $serviceRunning")
            serviceRunning
        } catch (e: Exception) {
            Logger.error("Error checking VPN status: ${e.message}", e)
            false
        }
    }

    private fun openApp() {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                startActivityAndCollapse(launchIntent)
            }
        } catch (e: Exception) {
            Logger.error("Error opening app: ${e.message}", e)
        }
    }

    private fun showToast(message: String) {
        try {
            android.widget.Toast.makeText(applicationContext, message, android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Logger.error("Error showing toast: ${e.message}", e)
        }
    }
}