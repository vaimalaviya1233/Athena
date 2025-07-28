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

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.kin.athena.service.vpn.network.util.NetworkConstants
import com.kin.athena.core.logging.Logger
import com.kin.athena.domain.model.Application
import com.kin.athena.service.utils.receiver.AppChangeCallback
import com.kin.athena.service.utils.receiver.AppChangeReceiver
import com.kin.athena.domain.usecase.application.ApplicationUseCases
import com.kin.athena.domain.usecase.preferences.PreferencesUseCases
import com.kin.athena.service.firewall.handler.RuleHandler
import com.kin.athena.service.firewall.utils.FirewallStatus
import com.kin.athena.service.utils.manager.FirewallManager
import com.kin.athena.service.utils.manager.FirewallService
import com.kin.athena.service.utils.manager.VpnManager
import com.kin.athena.service.utils.notifications.showInstallNotification
import com.kin.athena.service.utils.notifications.showStartNotification
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

@AndroidEntryPoint
class VpnConnectionServer : Service(), CoroutineScope by MainScope(), AppChangeCallback, FirewallService {

    @Inject lateinit var firewallManager: FirewallManager
    @Inject lateinit var ruleManager: RuleHandler
    @Inject lateinit var applicationUseCases: ApplicationUseCases
    @Inject lateinit var preferencesUseCases: PreferencesUseCases
    @Inject @ApplicationContext lateinit var appContext: Context


    private var vpnInterface: ParcelFileDescriptor? = null
    private lateinit var tunnelManager: TunnelManager
    private var netGuardJob: Job? = null

    private var installedApplications: List<Application>? = null
    private val appChangeReceiver = AppChangeReceiver(this)
    private var isStoppedBySelf = false
    private var isShuttingDown = false

    inner class VpnConnection : Binder() {
        val service: VpnConnectionServer
            get() = this@VpnConnectionServer
    }

    fun loadApplications() {
        runBlocking {
            applicationUseCases.getApplications.execute().fold(
                ifSuccess = { apps -> installedApplications = apps },
                ifFailure = { Logger.error("Failed to fetch applications: ${it.message}") }
            )
        }
    }

    override fun startService(context: Context) {
        VpnManager.start(context)
    }

    override fun stopService(context: Context) {
        VpnManager.stop(context)
    }

    override fun updateLogs(enabled: Boolean) {
        ruleManager.updateLogs(enabled)
    }

    override fun updateScreen(value: Boolean) {
        ruleManager.updateScreenSetting()
    }


    override fun updateDomains() {
        ruleManager.updateBlocklist()
    }

    override fun updateHttpSettings() {
        ruleManager.updateHttpSettings()
    }


    override fun updateRules(application: Application?) {
        Logger.info("Updating firewall rules${if (application != null) " for ${application.packageID}" else ""}")
        
        // Clear all active sessions before applying new rules
        val clearSessionsServiceIntent = Intent(appContext, VpnConnectionServer::class.java).apply {
            action = NetworkConstants.ACTION_CLEAR_SESSIONS
        }
        appContext.startService(clearSessionsServiceIntent)
        
        runBlocking {
            loadApplications()
            showStartNotification(installedApplications!!, preferencesUseCases, appContext)
        }
        
        if (::ruleManager.isInitialized) {
            ruleManager.updateAppRules()
            Logger.info("Firewall rules updated successfully")
        } else {
            Logger.error("Attempted to update rules before ruleManager was initialized.")
        }
    }

    override fun onAppInstalled(packageName: String?) {

        packageName?.let { pkg ->
            CoroutineScope(Dispatchers.IO).launch {
                showInstallNotification(
                    pkg,
                    applicationUseCases,
                    preferencesUseCases,
                    useRootMode = false,
                    firewallManager = firewallManager
                )
            }
        }
    }

    override fun onAppUninstalled(packageName: String?) {

    }

    override fun onBind(intent: Intent): IBinder = VpnConnection()

    @SuppressLint("MissingPermission")
    private fun toggleWifiAccess(packageName: String?) {
        toggleAccess(packageName) { app -> app.copy(internetAccess = !app.internetAccess) }
    }

    @SuppressLint("MissingPermission")
    private fun toggleCellularAccess(packageName: String?) {
        toggleAccess(packageName) { app -> app.copy(cellularAccess = !app.cellularAccess) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            NetworkConstants.ACTION_TOGGLE_CELLURAL -> toggleWifiAccess(intent.getStringExtra("packageName"))
            NetworkConstants.ACTION_TOGGLE_WIFI -> toggleCellularAccess(intent.getStringExtra("packageName"))
            NetworkConstants.ACTION_STOP_VPN -> stopVpn()
            NetworkConstants.ACTION_START_VPN -> startVpn(intent)
            NetworkConstants.ACTION_CLEAR_SESSIONS -> clearSessions()
        }
        return START_STICKY
    }

    /**
     * Clears all active sessions. This is useful when firewall rules are updated
     * to ensure previously allowed sessions are terminated and new rules are applied.
     */
    fun clearSessions() {
        try {
            if (isShuttingDown) {
                Logger.warn("Cannot clear sessions: VPN is shutting down")
                return
            }
            if (::tunnelManager.isInitialized) {
                Logger.info("Clearing all active sessions due to rule update")
                tunnelManager.clearSessions()
                Logger.info("All sessions cleared successfully")
            } else {
                Logger.warn("TunnelManager not initialized, cannot clear sessions")
            }
        } catch (e: Exception) {
            Logger.error("Error clearing sessions: ${e.message}", e)
        }
    }


    private fun toggleAccess(packageName: String?, update: (Application) -> Application) {
        packageName?.let { pkg ->
            CoroutineScope(Dispatchers.IO).launch {
                applicationUseCases.getApplication.execute(pkg).fold(
                    ifSuccess = { app ->
                        val updatedApp = update(app)
                        applicationUseCases.updateApplication.execute(updatedApp).fold(
                            ifSuccess = {
                                ruleManager.updateAppRules(updatedApp)
                                showInstallNotification(
                                    pkg,
                                    applicationUseCases,
                                    preferencesUseCases,
                                    useRootMode = true,
                                    firewallManager = firewallManager
                                )
                            },
                            ifFailure = { Logger.error("Failed to update application: ${it.message}") }
                        )
                    },
                    ifFailure = { Logger.error("Failed to fetch application: ${it.message}") }
                )
            }
        }
    }

    private fun startVpn(intent: Intent) {
        val vpnInterfaceFd = intent.getIntExtra("vpnInterfaceFd", -1)
        if (vpnInterfaceFd != -1) {
            vpnInterface = ParcelFileDescriptor.adoptFd(vpnInterfaceFd)
            vpnInterface?.let {
                initializeVpnComponents(it)
                startVpnCoroutines()
                appChangeReceiver.register(this)
            } ?: run {
                Logger.error("Invalid VPN interface file descriptor received. Service is stopping.")
                stopSelf()
            }
        }
    }

    private fun initializeVpnComponents(vpnInterface: ParcelFileDescriptor) {
        // Get DNS server from settings
        val dnsServer = runBlocking {
            preferencesUseCases.loadSettings.execute().fold(
                ifSuccess = { it.dnsServer1 },
                ifFailure = { "9.9.9.9" }
            )
        }
        
        // Initialize TunnelManager with the injected RuleHandler and user's DNS server
        tunnelManager = TunnelManager(ruleManager, dnsServer)
        
        if (!tunnelManager.initialize()) {
            Logger.error("Failed to initialize Tunnel Manager")
            stopSelf()
            return
        }

        Logger.info("Tunnel Manager initialized successfully")
    }


    private fun startVpnCoroutines() {
        runBlocking {
            loadApplications()
            showStartNotification(installedApplications!!, preferencesUseCases)
        }
        launch { if (::firewallManager.isInitialized) { firewallManager.update(FirewallStatus.ONLINE) } }
        launch(Dispatchers.IO) { ruleManager.updateAppRules() }
        
        // Start Tunnel Manager VPN processing
        val logLevel = 2 // ANDROID_LOG_WARN
        tunnelManager.start(logLevel)
        
        netGuardJob = launch(Dispatchers.IO) {
            try {
                vpnInterface?.let { vpnFd ->
                    Logger.info("Starting Tunnel Manager packet processing")
                    tunnelManager.run(vpnFd.fd, forwardDns = true, rcode = 3)
                }
            } catch (e: Exception) {
                Logger.error("Tunnel Manager processing error: ${e.message}", e)
            }
        }
    }


    private fun closeResources() {
        isShuttingDown = true
        netGuardJob?.cancel()
        if (::tunnelManager.isInitialized) {
            tunnelManager.stop()
            tunnelManager.release()
        }
        vpnInterface?.close()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isStoppedBySelf) {
            stopVpn()
        }
    }


    private fun stopVpn() {
        appChangeReceiver.unregister(this)
        if (::firewallManager.isInitialized) {
            if (firewallManager.rulesLoaded.value.name() == FirewallStatus.ONLINE.name()) {
                firewallManager.update(FirewallStatus.OFFLINE)
                coroutineContext.cancel()
                try {
                    closeResources()
                } catch (e: IOException) {
                    Logger.error("Error closing resources: ${e.localizedMessage}", e)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    stopForeground(true)
                }
                Logger.info("Vpn server is stopped")
                isStoppedBySelf = true
                stopSelf()
            }
        } else {
            Logger.warn("Vpn server is not running")
        }
    }
}


