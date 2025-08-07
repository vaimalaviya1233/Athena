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

package com.kin.athena.presentation.screens.settings.subSettings.dns.viewModel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kin.athena.data.cache.DomainCacheService
import com.kin.athena.presentation.screens.settings.subSettings.dns.components.DownloadState
import com.kin.athena.presentation.screens.settings.subSettings.dns.hosts.RuleDatabase
import com.kin.athena.service.utils.manager.FirewallManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

@HiltViewModel
class BlockListViewModel @Inject constructor(
    val ruleDatabase: RuleDatabase,
    val firewallManager: FirewallManager,
    private val domainCacheService: DomainCacheService,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _showMagiskDialog = MutableStateFlow(false)
    val showMagiskDialog: StateFlow<Boolean> = _showMagiskDialog
    
    private val _downloadState = MutableStateFlow<DownloadState?>(null)
    val downloadState: StateFlow<DownloadState?> = _downloadState
    
    private val _showDownloadDialog = MutableStateFlow(false)
    val showDownloadDialog: StateFlow<Boolean> = _showDownloadDialog
    
    private val _currentDownloadingRule = MutableStateFlow<String?>(null)
    val currentDownloadingRule: StateFlow<String?> = _currentDownloadingRule

    val isLoading = domainCacheService.isLoading
    val isInitialized = domainCacheService.isInitialized

    init {
        // Initialize domains globally with caching
        domainCacheService.initializeGlobally()
    }

    fun showMagiskDialog() {
        _showMagiskDialog.value = true
    }

    fun hideMagiskDialog() {
        _showMagiskDialog.value = false
    }
    
    fun refreshDomains() {
        domainCacheService.forceRefresh()
    }
    
    fun invalidateDomainsCache() {
        domainCacheService.invalidateCache()
    }
    
    fun startDownload(ruleName: String) {
        _currentDownloadingRule.value = ruleName
        _downloadState.value = DownloadState.Downloading
        _showDownloadDialog.value = true
    }
    
    fun downloadSuccess() {
        _downloadState.value = DownloadState.Success
    }
    
    fun downloadNetworkError() {
        _downloadState.value = DownloadState.NetworkError
    }
    
    fun downloadError() {
        _downloadState.value = DownloadState.Error
    }
    
    fun dismissDownloadDialog() {
        _showDownloadDialog.value = false
        _downloadState.value = null
        _currentDownloadingRule.value = null
    }
    
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && 
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    fun isNetworkError(exception: Exception): Boolean {
        return when (exception) {
            is UnknownHostException,
            is ConnectException,
            is SocketTimeoutException -> true
            is java.net.SocketException -> true
            is javax.net.ssl.SSLException -> true
            is java.io.IOException -> {
                val message = exception.message?.lowercase() ?: ""
                message.contains("network") || 
                message.contains("connection") || 
                message.contains("timeout") ||
                message.contains("host") ||
                message.contains("unreachable") ||
                message.contains("failed to connect")
            }
            else -> {
                val message = exception.message?.lowercase() ?: ""
                message.contains("network") || 
                message.contains("connection") || 
                message.contains("timeout") ||
                message.contains("host") ||
                message.contains("unreachable")
            }
        }
    }
}