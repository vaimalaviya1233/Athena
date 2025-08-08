/*
 * Copyright (C) 2024 Vexzure
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

package com.kin.athena.service.vpn.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import com.kin.athena.R
import com.kin.athena.service.vpn.network.util.NetworkConstants
import com.kin.athena.core.logging.Logger
import com.kin.athena.domain.model.Settings
import com.kin.athena.domain.usecase.preferences.PreferencesUseCases
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class VpnConnectionClient : VpnService() {

    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var preferencesUseCases: PreferencesUseCases


    private var settings: Settings? = null
    private var isStoppedBySelf = false
    private var vpnInterface: ParcelFileDescriptor? = null
    private var fd: Int = -1


    fun loadSettings() {
        runBlocking {
            preferencesUseCases.loadSettings.execute().fold(
                ifSuccess = { settings = it },
                ifFailure = { Logger.error("Failed to fetch applications: ${it.message}") }
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { handleAction(it) } ?: Logger.warn("No action specified in intent.")
        return START_STICKY
    }

    private fun handleAction(action: String) {
        when (action) {
            NetworkConstants.ACTION_START_VPN -> startVpnIfNotRunning()
            NetworkConstants.ACTION_STOP_VPN -> stopVpn()
            else -> Logger.warn("Unhandled action: $action")
        }
    }

    private fun startVpnIfNotRunning() {
        if (vpnInterface == null) {
            try {
                configureAndStartVpn()
            } catch (e: Exception) {
                Logger.error("Error starting VPN", e)
                onDestroy()
            }
        } else {
            Logger.info("VPN is already running.")
        }
    }

    private fun configureAndStartVpn() {
        vpnInterface = createVpnBuilder().establish()
        vpnInterface?.let {
            fd = it.detachFd()
            startVpnServiceWithFd(fd)
        } ?: run {
            Logger.error("Failed to establish VPN interface.")
            onDestroy()
        }
    }

    private fun createVpnBuilder() = Builder().apply {
        loadSettings()
        addDnsServer("198.18.0.1")
        addAddress(settings!!.ipv4, 32)
        addRoute(NetworkConstants.ALL_TRAFFIC, 0)
        setMtu(NetworkConstants.MAX_PACKET_LEN)
        setBlocking(true)
        setSession(getString(R.string.app_name))
        addDisallowedApplication(context.packageName)
        addDisallowedApplication("com.google.android.projection.gearhead").apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setMetered(false)
            }
        }
    }

    private fun startVpnServiceWithFd(fd: Int) {
        val intent = createServiceIntent(fd, NetworkConstants.ACTION_START_VPN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    private fun stopVpn(shouldStopSelf: Boolean = true) {
        stopVpnService(fd)
        vpnInterface.closeSafely()
        vpnInterface = null
        if (shouldStopSelf) {
            isStoppedBySelf = true
            stopSelf()
        } else {
            Logger.info("Firewall was stopped, possibly by another VPN.")
        }
    }

    private fun stopVpnService(fd: Int) {
        startService(createServiceIntent(fd, NetworkConstants.ACTION_STOP_VPN))
    }

    private fun createServiceIntent(fd: Int, action: String) = Intent(this, VpnConnectionServer::class.java).apply {
        this.action = action
        putExtra("vpnInterfaceFd", fd)
    }

    private fun ParcelFileDescriptor?.closeSafely() {
        try {
            this?.close()
        } catch (e: IOException) {
            Logger.error("Failed to close VPN interface: ${e.localizedMessage}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isStoppedBySelf) {
            stopVpn(false)
        }
    }
}
