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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kin.athena.core.logging.Logger
import com.kin.athena.domain.model.CustomDomain
import com.kin.athena.domain.repository.CustomDomainRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DomainManagementViewModel @Inject constructor(
    private val customDomainRepository: CustomDomainRepository
) : ViewModel() {

    private val _allowlistDomains = MutableStateFlow<List<CustomDomain>>(emptyList())
    val allowlistDomains: StateFlow<List<CustomDomain>> = _allowlistDomains.asStateFlow()

    private val _blocklistDomains = MutableStateFlow<List<CustomDomain>>(emptyList())
    val blocklistDomains: StateFlow<List<CustomDomain>> = _blocklistDomains.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _allowlistCount = MutableStateFlow(0)
    val allowlistCount: StateFlow<Int> = _allowlistCount.asStateFlow()

    private val _blocklistCount = MutableStateFlow(0)
    val blocklistCount: StateFlow<Int> = _blocklistCount.asStateFlow()

    init {
        loadDomains()
        loadCounts()
    }

    private fun loadDomains() {
        _isLoading.value = true
        
        // Load allowlist domains
        viewModelScope.launch {
            customDomainRepository.getAllowlistDomains()
                .catch { e ->
                    Logger.error("Failed to load allowlist domains: ${e.message}", e)
                    _errorMessage.value = "Failed to load allowlist domains"
                }
                .collect { domains ->
                    _allowlistDomains.value = domains
                    _isLoading.value = false
                }
        }

        // Load blocklist domains
        viewModelScope.launch {
            customDomainRepository.getBlocklistDomains()
                .catch { e ->
                    Logger.error("Failed to load blocklist domains: ${e.message}", e)
                    _errorMessage.value = "Failed to load blocklist domains"
                }
                .collect { domains ->
                    _blocklistDomains.value = domains
                }
        }
    }

    private fun loadCounts() {
        viewModelScope.launch {
            try {
                _allowlistCount.value = customDomainRepository.getAllowlistCount()
                _blocklistCount.value = customDomainRepository.getBlocklistCount()
            } catch (e: Exception) {
                Logger.error("Failed to load domain counts: ${e.message}", e)
            }
        }
    }

    fun addDomain(domain: String, description: String, isRegex: Boolean, isAllowlist: Boolean) {
        viewModelScope.launch {
            try {
                // Validate domain first
                val validationResult = validateDomain(domain, isRegex)
                if (validationResult is ValidationResult.Error) {
                    _errorMessage.value = validationResult.message
                    return@launch
                }

                // Check for duplicates
                val isDuplicate = customDomainRepository.isDomainExists(domain, isAllowlist)
                if (isDuplicate) {
                    _errorMessage.value = "Domain already exists in ${if (isAllowlist) "allowlist" else "blocklist"}"
                    return@launch
                }

                val customDomain = CustomDomain(
                    domain = domain,
                    description = description,
                    isRegex = isRegex,
                    isAllowlist = isAllowlist
                )

                customDomainRepository.insertDomain(customDomain)
                loadCounts() // Update counts after adding
                Logger.info("Added domain: $domain to ${if (isAllowlist) "allowlist" else "blocklist"}")
                
            } catch (e: Exception) {
                Logger.error("Failed to add domain: ${e.message}", e)
                _errorMessage.value = "Failed to add domain: ${e.message}"
            }
        }
    }

    fun removeDomain(domain: CustomDomain) {
        viewModelScope.launch {
            try {
                customDomainRepository.deleteDomain(domain)
                loadCounts() // Update counts after removing
                Logger.info("Removed domain: ${domain.domain} from ${if (domain.isAllowlist) "allowlist" else "blocklist"}")
                
            } catch (e: Exception) {
                Logger.error("Failed to remove domain: ${e.message}", e)
                _errorMessage.value = "Failed to remove domain: ${e.message}"
            }
        }
    }

    fun toggleDomainEnabled(domain: CustomDomain) {
        viewModelScope.launch {
            try {
                customDomainRepository.updateDomainEnabled(domain.id, !domain.isEnabled)
                Logger.info("Toggled domain enabled state: ${domain.domain}")
                
            } catch (e: Exception) {
                Logger.error("Failed to toggle domain enabled state: ${e.message}", e)
                _errorMessage.value = "Failed to update domain: ${e.message}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun validateDomain(domain: String, isRegex: Boolean): ValidationResult {
        return when {
            domain.isBlank() -> ValidationResult.Error("Please enter a domain")
            isRegex -> {
                try {
                    Regex(domain)
                    ValidationResult.Success
                } catch (e: Exception) {
                    ValidationResult.Error("Invalid regex pattern: ${e.message}")
                }
            }
            else -> {
                when {
                    domain.contains(" ") -> ValidationResult.Error("Domain cannot contain spaces")
                    !domain.contains(".") -> ValidationResult.Error("Domain must contain at least one dot")
                    domain.startsWith(".") -> ValidationResult.Error("Domain cannot start with a dot")
                    domain.endsWith(".") && domain.length > 1 -> ValidationResult.Success // Allow trailing dot for DNS
                    domain.length < 3 -> ValidationResult.Error("Domain too short")
                    domain.length > 253 -> ValidationResult.Error("Domain too long")
                    else -> ValidationResult.Success
                }
            }
        }
    }

    fun deleteAllDomainsByType(isAllowlist: Boolean) {
        viewModelScope.launch {
            try {
                customDomainRepository.deleteAllDomainsByType(isAllowlist)
                loadCounts()
                Logger.info("Deleted all ${if (isAllowlist) "allowlist" else "blocklist"} domains")
                
            } catch (e: Exception) {
                Logger.error("Failed to delete all domains: ${e.message}", e)
                _errorMessage.value = "Failed to delete domains: ${e.message}"
            }
        }
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}