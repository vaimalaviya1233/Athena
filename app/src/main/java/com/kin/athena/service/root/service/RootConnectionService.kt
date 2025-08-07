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

package com.kin.athena.service.root.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import com.kin.athena.core.logging.Logger
import com.kin.athena.core.utils.Shell
import com.kin.athena.core.utils.constants.AppConstants
import com.kin.athena.core.utils.extensions.requiresNetworkPermissions
import com.kin.athena.core.utils.registerShell
import com.kin.athena.domain.model.Application
import com.kin.athena.domain.model.Ip
import com.kin.athena.domain.model.Settings
import com.kin.athena.domain.usecase.application.ApplicationUseCases
import com.kin.athena.domain.usecase.networkFilter.NetworkFilterUseCases
import com.kin.athena.domain.usecase.preferences.PreferencesUseCases
import com.kin.athena.presentation.screens.settings.subSettings.dns.hosts.RuleDatabase
import com.kin.athena.presentation.screens.settings.subSettings.dns.root.HostsManager
import com.kin.athena.service.firewall.handler.RuleHandler
import com.kin.athena.service.firewall.utils.FirewallStatus
import com.kin.athena.service.root.nflog.NflogManager
import com.kin.athena.service.utils.manager.FirewallManager
import com.kin.athena.service.utils.notifications.showInstallNotification
import com.kin.athena.service.vpn.network.util.NetworkConstants
import com.kin.athena.service.utils.manager.FirewallService
import com.kin.athena.service.utils.notifications.showStartNotification
import com.kin.athena.service.utils.receiver.AppChangeCallback
import com.kin.athena.service.utils.receiver.AppChangeReceiver
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class RootConnectionService : Service(), CoroutineScope by CoroutineScope(Dispatchers.IO), FirewallService, AppChangeCallback {

    @Inject lateinit var networkLogger: NflogManager
    @Inject lateinit var preferencesUseCases: PreferencesUseCases
    @Inject lateinit var firewallRulesHandler: RuleHandler
    @Inject lateinit var applicationUseCases: ApplicationUseCases
    @Inject lateinit var packageManager: ApplicationUseCases
    @Inject lateinit var firewallManager: FirewallManager
    @Inject lateinit var networkFilterManager: NetworkFilterUseCases
    @Inject @ApplicationContext lateinit var appContext: Context
    var ruleManager = RuleDatabase()
    var domains: List<String>? = null



    private val appChangeReceiver: AppChangeReceiver = AppChangeReceiver(this)
    private val shellExecutor: Shell by lazy { Shell.SU }

    private var installedApplications: List<Application>? = null
    private var ipAddresses: List<Ip>? = null

    private val serviceContext: Context = this

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                NetworkConstants.ACTION_TOGGLE_CELLURAL -> toggleAccess(intent.getStringExtra("packageName"), AccessType.CELLULAR)
                NetworkConstants.ACTION_TOGGLE_WIFI -> toggleAccess(intent.getStringExtra("packageName"), AccessType.WIFI)
                NetworkConstants.ACTION_STOP_ROOT -> onStopIntent()
                NetworkConstants.ACTION_START_ROOT -> onStartIntent()
            }
        }
        return START_STICKY
    }

    private fun toggleAccess(packageName: String?, accessType: AccessType) {
        packageName?.let { pkgName ->
            launch {
                packageManager.getApplication.execute(pkgName).fold(
                    ifSuccess = { application ->
                        val updatedApplication = application.toggleAccess(accessType)
                        packageManager.updateApplication.execute(updatedApplication).fold(
                            ifSuccess = {
                                firewallRulesHandler.updateAppRules(updatedApplication)
                                showInstallNotification(pkgName, updatedApplication)
                            }
                        )
                    },
                    ifFailure = {
                        onAppInstalled(packageName)
                    }
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @SuppressLint("MissingPermission")
    private fun showInstallNotification(packageName: String, application: Application) {
        launch {
            showInstallNotification(
                packageName = packageName,
                applicationUseCases = applicationUseCases,
                preferencesUseCases = preferencesUseCases,
                useRootMode = true,
                firewallManager = firewallManager,
                application = application
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder = RootBinder()

    override fun updateRules(application: Application?) {
        application?.let { app ->
            launch {
                updateFirewallRules(app)
            }
        }
    }

    private fun updateFirewallRules(application: Application) {
        val wifiRule = if (application.internetAccess) "ACCEPT" else "REJECT"
        val cellularRule = if (application.cellularAccess) "ACCEPT" else "REJECT"

        val shell = Shell.registerShell("su")

        shell.run("iptables -I EasyApps-wifi -m owner --uid-owner ${application.uid} -j $wifiRule -w")
        shell.run("iptables -I EasyApps-data -m owner --uid-owner ${application.uid} -j $cellularRule -w")

        runBlocking {
            packageManager.getApplications.execute().fold(
                ifSuccess = {
                    installedApplications = it
                    showStartNotification(installedApplications!!, preferencesUseCases, context = appContext)
                }
            )
        }
    }

    override fun startService(context: Context) {
        context.startService(Intent(context, RootConnectionService::class.java).apply {
            action = NetworkConstants.ACTION_START_ROOT
        })
    }

    override fun stopService(context: Context) {
        context.startService(Intent(context, RootConnectionService::class.java).apply {
            action = NetworkConstants.ACTION_STOP_ROOT
        })
    }

    override fun updateLogs(enabled: Boolean) {
        launch {
            packageManager.getApplications.execute().fold(
                ifSuccess = {
                    if (enabled) {
                        networkLogger.start(it, bypassSettings = true)
                    } else {
                        networkLogger.stop()
                    }
                }
            )
        }
    }

    override fun updateScreen(value: Boolean) {

    }

    override fun updateDomains() {

    }

    override fun updateHttpSettings() {

    }
    
    override fun setDnsBlocking(enabled: Boolean) {
        firewallRulesHandler.setDnsBlocking(enabled)
    }
    
    override fun isDnsBlockingEnabled(): Boolean {
        return firewallRulesHandler.isDnsBlockingEnabled()
    }

    private suspend fun loadRules() {
        val applicationsJob = async { packageManager.getApplications.execute().fold(ifSuccess = { installedApplications = it }) }
        val ipsJob = async { networkFilterManager.getIps.execute().fold(ifSuccess = { ipAddresses = it.first() }) }

        runBlocking {
            applicationsJob.await()
            ipsJob.await()
        }

        installedApplications?.let { apps ->
            ipAddresses?.let { ips ->
                showStartNotification(apps, preferencesUseCases)
                rootAppRules(apps, ips)
            }
        }
    }

    private fun onStartIntent() {
        Logger.info("RootConnectionService: onStartIntent() called")
        firewallManager.update(FirewallStatus.LOADING(0f))
        appChangeReceiver.register(this)
        
        launch { 
            loadRules()
            loadAndApplyDomains()
        }
    }
    
    private fun loadAndApplyDomains() {
        Logger.info("RootConnectionService: Loading domains from RuleDatabase")
        domains = ruleManager.initialize(true)
        Logger.info("RootConnectionService: Loaded ${domains?.size ?: 0} domains from RuleDatabase")
        domains?.take(5)?.forEach { domain ->
            Logger.info("RootConnectionService: Sample domain: $domain")
        }
        
        // Apply domains to system hosts file if domains are available
        if (!domains.isNullOrEmpty()) {
            try {
                Logger.info("RootConnectionService: Creating HostsManager with ${domains!!.size} domains")
                val hostsManager = HostsManager(appContext, domains!!)
                Logger.info("RootConnectionService: Calling hostsManager.apply()")
                hostsManager.apply()
                Logger.info("RootConnectionService: Successfully applied ${domains!!.size} domains to system hosts file")
            } catch (e: Exception) {
                Logger.error("RootConnectionService: Failed to apply domains to hosts file: ${e.message}", e)
            }
        } else {
            Logger.warn("RootConnectionService: No domains to apply to hosts file")
        }
    }

    private fun onStopIntent() {
        val hostsManager = domains?.let { HostsManager(appContext, it) }

        if (!domains.isNullOrEmpty()) {
            hostsManager?.revertToDefault()
        }
        appChangeReceiver.unregister(this)
        firewallManager.update(FirewallStatus.OFFLINE)
        launch {
            flushAppRules()
            networkLogger.stop()
        }
        stopForeground(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        onStopIntent()
    }

    inner class RootBinder : Binder() {
        val service: RootConnectionService
            get() = this@RootConnectionService
    }

    suspend fun rootAppRules(allPackages: List<Application>, ips: List<Ip>) = withContext(Dispatchers.IO) {
        setupEasyAppsChain(allPackages, ips)
        launch { networkLogger.start(allPackages) }
    }

    suspend fun flushAppRules() = withContext(Dispatchers.IO) {
        executeShellCommand("iptables -F -w && ip6tables -F -w")
        executeShellCommand("iptables -t nat -F OUTPUT")
    }

    suspend fun setupEasyAppsChain(applications: List<Application>, ips: List<Ip>) = withContext(Dispatchers.IO) {
        val hostsManager = domains?.let { HostsManager(appContext, it) }

        if (!domains.isNullOrEmpty()) {
            hostsManager?.apply()
        }

        val internetApps = applications.filter { it.requiresNetworkPermissions(serviceContext.packageManager) }
        val commands = mutableListOf<String>()
        val iptablesList = listOf("iptables", "ip6tables")

        iptablesList.forEach { iptables ->
            preferencesUseCases.loadSettings.execute().fold(
                ifSuccess = { settings ->
                    createChains(iptables, commands)
                    connectChains(iptables, commands)
                    createRejectChain(iptables, commands)
                    createAppRules(iptables, internetApps, commands)
                    closeChains(iptables, commands)
                    if (settings.blockPort80) {
                        block80(iptables, commands)
                    }
                    applyRules(iptables, commands)
                    createDNSRules(iptables, commands, settings)
                }
            )
        }
        applyIpRules("iptables", ips, commands)

        var commandIndex = 0
        executeShellCommand(commands.joinToString(" && ") { "echo ${++commandIndex} && $it" }, commands.count())
    }

    private fun block80(iptables: String, commands: MutableList<String>) {
        commands.add("$iptables -I EasyApps-ips -p tcp --dport 80 -j DROP -w")
    }

    private fun createChains(iptables: String, commands: MutableList<String>) {
        commands.addAll(listOf(
            "$iptables -I OUTPUT -j NFLOG --nflog-group ${AppConstants.NetworkConstants.NFLOG_GROUP_ID}",
            "$iptables -L EasyApps -w > /dev/null 2>&1 || $iptables -N EasyApps -w",
            "$iptables -L EasyApps-ips -w > /dev/null 2>&1 || $iptables -N EasyApps-ips -w",
            "$iptables -L EasyApps-wifi -w > /dev/null 2>&1 || $iptables -N EasyApps-wifi -w",
            "$iptables -L EasyApps-data -w > /dev/null 2>&1 || $iptables -N EasyApps-data -w",
            "$iptables -L EasyApps-reject -w > /dev/null 2>&1 || $iptables -N EasyApps-reject -w"
        ))
    }

    private fun connectChains(iptables: String, commands: MutableList<String>) {
        commands.addAll(listOf(
            "$iptables -A EasyApps -j EasyApps-ips",
            "$iptables -A EasyApps -o eth+ -j EasyApps-wifi",
            "$iptables -A EasyApps -o wlan+ -j EasyApps-wifi",
            "$iptables -A EasyApps -o tiwlan+ -j EasyApps-wifi",
            "$iptables -A EasyApps -o ra+ -j EasyApps-wifi",
            "$iptables -A EasyApps -o bnep+ -j EasyApps-wifi",
            "$iptables -A EasyApps -o rmnet+ -j EasyApps-data",
            "$iptables -A EasyApps -o pdp+ -j EasyApps-data",
            "$iptables -A EasyApps -o uwbr+ -j EasyApps-data",
            "$iptables -A EasyApps -o wimax+ -j EasyApps-data",
            "$iptables -A EasyApps -o vsnet+ -j EasyApps-data",
            "$iptables -A EasyApps -o rmnet_sdio+ -j EasyApps-data",
            "$iptables -A EasyApps -o ccmni+ -j EasyApps-data",
            "$iptables -A EasyApps -o qmi+ -j EasyApps-data",
            "$iptables -A EasyApps -o svnet0+ -j EasyApps-data",
            "$iptables -A EasyApps -o ccemni+ -j EasyApps-data",
            "$iptables -A EasyApps -o wwan+ -j EasyApps-data",
            "$iptables -A EasyApps -o cdma_rmnet+ -j EasyApps-data",
            "$iptables -A EasyApps -o clat4+ -j EasyApps-data",
            "$iptables -A EasyApps -o cc2mni+ -j EasyApps-data",
            "$iptables -A EasyApps -o bond1+ -j EasyApps-data",
            "$iptables -A EasyApps -o rmnet_smux+ -j EasyApps-data",
            "$iptables -A EasyApps -o ccinet+ -j EasyApps-data",
            "$iptables -A EasyApps -o v4-rmnet+ -j EasyApps-data",
            "$iptables -A EasyApps -o seth_w+ -j EasyApps-data",
            "$iptables -A EasyApps -o v4-rmnet_data+ -j EasyApps-data",
            "$iptables -A EasyApps -o rmnet_ipa+ -j EasyApps-data",
            "$iptables -A EasyApps -o rmnet_data+ -j EasyApps-data",
            "$iptables -A EasyApps -o r_rmnet_data+ -j EasyApps-data"
        ))
    }

    private fun createRejectChain(iptables: String, commands: MutableList<String>) {
        commands.addAll(
            listOf(
                "$iptables -A EasyApps-reject -p udp --dport 53 -m owner --uid-owner root -j ACCEPT -w",
                "$iptables -A EasyApps-reject -p tcp --dport 53 -j ACCEPT -w",
                "$iptables -A EasyApps-reject -p udp --dport 53 -j ACCEPT -w",
                "$iptables -A EasyApps-reject -j REJECT --reject-with ${if (iptables == "iptables") "icmp-port-unreachable" else "icmp6-adm-prohibited"} -w"
            )
        )
    }

    private fun createDNSRules(iptables: String, commands: MutableList<String>, settings: Settings) {
            if (iptables == "iptables") {
            commands.addAll(listOf(
                "$iptables -t nat -I OUTPUT -p udp --dport 53 -j DNAT --to ${settings.dnsServer1}:53",
            ))
        }
    }

    private fun createAppRules(iptables: String, internetApps: List<Application>, commands: MutableList<String>) {
        internetApps.forEach { application ->
            if (application.internetAccess) {
                commands.add("$iptables -A EasyApps-wifi -m owner --uid-owner ${application.uid} -j ACCEPT -w")
            }
            if (application.cellularAccess) {
                commands.add("$iptables -A EasyApps-data -m owner --uid-owner ${application.uid} -j ACCEPT -w")
            }
        }
    }

    private fun applyIpRules(iptables: String, ips: List<Ip>, commands: MutableList<String>) {
        ips.forEach { ip ->
            commands.add("$iptables -I EasyApps-ips -d ${ip.ip} -j DROP -w")
        }
    }

    private fun closeChains(iptables: String, commands: MutableList<String>) {
        commands.addAll(listOf(
            "$iptables -A EasyApps-wifi -j EasyApps-reject -w",
            "$iptables -A EasyApps-data -j EasyApps-reject -w",
        ))
    }

    private fun applyRules(iptables: String, commands: MutableList<String>) {
        commands.add("$iptables -A OUTPUT -j EasyApps -w")
    }

    private fun updateLoading(completedSteps: Int, totalSteps: Int) {
        if (::firewallManager.isInitialized) {
            val progress = completedSteps.toFloat() / totalSteps.toFloat()
            firewallManager.update(FirewallStatus.LOADING(progress))

            if (progress == 1f) {
                firewallManager.update(FirewallStatus.ONLINE)
            }
        }
    }

    fun isNumber(input: String): Boolean {
        return input.toDoubleOrNull() != null
    }

    private fun executeShellCommand(command: String, totalCmd: Int? = null) {
        shellExecutor.addOnStdoutLineListener(object : Shell.OnLineListener {
                override fun onLine(line: String) {
                    if (line.isNotBlank()) {
                        totalCmd?.let {
                            if (isNumber(line)) {
                                updateLoading(line.toInt(), it)
                            }
                        }
                    }
                }
            }
        )

        shellExecutor.run(command)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onAppInstalled(packageName: String?) {
        launch {
            packageName?.let {
                showInstallNotification(
                    it,
                    applicationUseCases,
                    preferencesUseCases,
                    true,
                    firewallManager,
                )
            }
        }
    }

    override fun onAppUninstalled(packageName: String?) {
        // Handle app uninstallation logic here
    }

    private fun Application.toggleAccess(accessType: AccessType): Application {
        return when (accessType) {
            AccessType.WIFI -> copy(internetAccess = !internetAccess)
            AccessType.CELLULAR -> copy(cellularAccess = !cellularAccess)
        }
    }

    enum class AccessType {
        WIFI, CELLULAR
    }
}