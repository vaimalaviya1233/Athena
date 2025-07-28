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

package com.kin.athena.presentation.screens.settings.subSettings.logs.viewModel

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kin.athena.core.utils.extensions.getAppNameFromPackage
import com.kin.athena.core.utils.extensions.toFormattedDateTime
import com.kin.athena.core.utils.extensions.uidToApplication
import com.kin.athena.domain.model.Log
import com.kin.athena.domain.usecase.log.LogUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val logsUseCases: LogUseCases,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _logs: MutableState<List<List<Log>>> = mutableStateOf(emptyList())
    val logs: State<List<List<Log>>> = _logs

    private val _filteredLogs: MutableState<List<List<Log>>> = mutableStateOf(emptyList())
    val filteredLogs: State<List<List<Log>>> = _filteredLogs

    private val _query = mutableStateOf("")
    val query: State<String> = _query

    init {
        getLogs()
    }

    fun deleteLogs() {
        viewModelScope.launch {
            logsUseCases.deleteLogs.execute()
            getLogs()
        }
    }

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        applyFilter()
    }

    fun clearQuery() {
        _query.value = ""
        _filteredLogs.value = _logs.value
    }

    private fun applyFilter() {
        val currentQuery = query.value.trim()
        if (currentQuery.isEmpty()) {
            _filteredLogs.value = _logs.value
        } else {
            _filteredLogs.value = _logs.value.map { group ->
                group.filter { log ->
                    val application = context.uidToApplication(log.packageID)?.packageID
                    val name = application?.let { context.getAppNameFromPackage(it) }
                    name?.contains(currentQuery, ignoreCase = true) ?: false ||
                            log.destinationIP.contains(currentQuery, ignoreCase = true) ||
                            log.packetStatus.name.contains(currentQuery, ignoreCase = true) ||
                            log.protocol.contains(currentQuery, ignoreCase = true) ||
                            log.sourceIP.contains(currentQuery, ignoreCase = true) ||
                            log.sourcePort.contains(currentQuery, ignoreCase = true) ||
                            log.destinationPort.contains(currentQuery, ignoreCase = true) ||
                            log.destinationAddress?.contains(currentQuery, ignoreCase = true) ?: false ||
                            log.time.toFormattedDateTime().contains(currentQuery, ignoreCase = true)
                }
            }.filter { it.isNotEmpty() }
        }
    }

    private fun groupConsecutiveLogs(logs: List<Log>): List<List<Log>> {
        val groupedLogs = mutableListOf<MutableList<Log>>()

        for (log in logs.filter { it.packageID != -1 }) {
            if (groupedLogs.isEmpty() || groupedLogs.last().last().packageID != log.packageID) {
                groupedLogs.add(mutableListOf(log))
            } else {
                groupedLogs.last().add(log)
            }
        }

        return groupedLogs.map { group ->
            group.distinctBy { log ->
                log.packageID to log.destinationIP to log.destinationPort to log.packetStatus
            }
        }
    }

    private fun getLogs() {
        viewModelScope.launch {
            logsUseCases.getLogs.execute().fold(
                ifSuccess = { logsUpdated ->
                    logsUpdated?.collect { logs ->
                        val groupedLogs = groupConsecutiveLogs(logs).reversed()
                        _logs.value = groupedLogs
                        applyFilter()
                    }
                }
            )
        }
    }
}