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

package com.kin.athena.service.firewall.rule

import com.kin.athena.core.logging.Logger
import com.kin.athena.domain.usecase.log.LogUseCases
import com.kin.athena.presentation.screens.settings.subSettings.dns.hosts.RuleDatabase
import com.kin.athena.service.firewall.model.FireWallModel
import com.kin.athena.service.firewall.model.FirewallResult
import com.kin.athena.service.vpn.network.transport.dns.DNSModel
import javax.inject.Inject

class DNSRule @Inject constructor(
    private val ruleDatabase: RuleDatabase
) : FirewallRule {
    private var isDnsBlockingEnabled = true
    
    init {
        updateBlocklist()
    }

    fun updateBlocklist() {
        ruleDatabase.initialize()
    }
    
    fun enableDnsBlocking() {
        isDnsBlockingEnabled = true
        Logger.info("DNS blocking enabled")
    }
    
    fun disableDnsBlocking() {
        isDnsBlockingEnabled = false
        Logger.info("DNS blocking disabled")
    }
    
    fun isDnsBlockingEnabled(): Boolean {
        return isDnsBlockingEnabled
    }

    override fun check(
        packet: FireWallModel,
        dnsModel: DNSModel?,
        logUseCases: LogUseCases,
        result: FirewallResult
    ): FirewallResult {
        // If DNS blocking is disabled, always allow
        if (!isDnsBlockingEnabled) {
            return result
        }
        
        dnsModel?.let {
            if (ruleDatabase.isBlocked(dnsModel.domainName)) {
                Logger.info("Blocked ${dnsModel.domainName}")
                return FirewallResult.DROP
            } else {
                return FirewallResult.ACCEPT

            }
        } ?: return FirewallResult.ACCEPT
    }
}