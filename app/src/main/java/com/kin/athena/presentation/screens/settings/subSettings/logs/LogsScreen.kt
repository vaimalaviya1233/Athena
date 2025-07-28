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

package com.kin.athena.presentation.screens.settings.subSettings.logs

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kin.athena.R
import com.kin.athena.core.utils.extensions.*
import com.kin.athena.domain.model.Log
import com.kin.athena.presentation.components.material.MaterialPlaceholder
import com.kin.athena.presentation.components.material.MaterialScaffold
import com.kin.athena.presentation.screens.home.components.SearchBar
import com.kin.athena.presentation.screens.settings.subSettings.logs.viewModel.LogsViewModel
import com.kin.athena.service.firewall.utils.Port
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.navigation.NavController
import com.kin.athena.presentation.navigation.routes.LogRoutes
import com.kin.athena.service.firewall.model.FirewallResult
import java.net.URL

@Composable
fun LogsScreen(
    onBackNavClicked: () -> Unit,
    navController: NavController,
    logsViewModel: LogsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    MaterialScaffold(
        topBar = {
            LogsSearchBar(
                query = logsViewModel.query.value,
                onQueryChange = logsViewModel::onQueryChanged,
                onClearClick = logsViewModel::clearQuery,
                onDeleteClick = logsViewModel::deleteLogs,
                onBackClicked = onBackNavClicked
            )
        }
    ) {
        LogsContent(
            logs = logsViewModel.filteredLogs.value,
            context = context,
            navController
        )
    }
}

@Composable
fun LogsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onBackClicked: () -> Unit
) {
    SearchBar(
        text = stringResource(R.string.search_packets),
        query = query,
        onQueryChange = onQueryChange,
        onClearClick = onClearClick,
        button = {
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
            }
        },
        onBackClicked = onBackClicked
    )
}

@Composable
fun LogsContent(logs: List<List<Log>>, context: Context, navController: NavController) {
    if (logs.isEmpty()) {
        MaterialPlaceholder(
            placeholderIcon = {
                Icon(
                    imageVector = Icons.Rounded.FileCopy,
                    contentDescription = null,
                    modifier = Modifier.scale(2f),
                    tint = MaterialTheme.colorScheme.outline
                )
            },
            placeholderText = stringResource(R.string.no_logs_found)
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            items(logs) { logGroup ->
                if (logGroup.firstOrNull()?.packageID != 0) {
                    LogGroupCard(
                        logGroup = logGroup,
                        context = context,
                        navController = navController
                    )
                }
            }
        }
    }
}

@Composable
fun LogGroupCard(logGroup: List<Log>, context: Context, includeHeader: Boolean = true, navController: NavController) {
    val application = context.uidToApplication(logGroup.first().packageID)
    val iconBitmap = application?.getApplicationIcon(
        context.packageManager,
        MaterialTheme.colorScheme.onSurface.toArgb(),
        context = context
    )?.toBitmap()?.asImageBitmap()
    application?.getApplicationName(context.packageManager)?.let {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .background(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (includeHeader) {
                    ApplicationHeader(
                        appName = it,
                        appIcon = iconBitmap,
                        timestamp = logGroup.first().time.toFormattedDateTime()
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
                LogDetails(logGroup, navController)
            }
        }
    }
}

@Composable
fun ApplicationHeader(appName: String, appIcon: androidx.compose.ui.graphics.ImageBitmap?, timestamp: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        appIcon?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = appName)
            Text(text = timestamp, fontSize = 12.sp)
        }
    }
}

@Composable
fun LogDetails(logs: List<Log>, navController: NavController) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
    ) {
        logs.forEach { log ->
            val color = when (log.packetStatus) {
                FirewallResult.ACCEPT -> Color.Unspecified
                FirewallResult.DROP -> MaterialTheme.colorScheme.error
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable {
                        navController.safeNavigate(LogRoutes.Packet.createRoute(log.id))
                    }
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 4.dp)
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        color = color,
                        text = (Port.getServiceName(log.destinationPort.toInt()) ?: log.destinationPort).uppercase(Locale.ROOT),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        color = color,
                        text = if (log.destinationAddress != null) extractBaseDomain(log.destinationAddress) else log.destinationIP,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .weight(2f)
                            .padding(horizontal = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Icon(
                        tint = if (color != MaterialTheme.colorScheme.error) LocalContentColor.current else color,
                        imageVector = Icons.Rounded.ArrowForwardIos,
                        contentDescription = "",
                        modifier = Modifier
                            .padding(vertical = 6.dp)
                            .scale(0.6f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(1.dp))
        }
    }
}

fun extractBaseDomain(fullDomain: String): String {
    return try {
        val url = URL("http://$fullDomain")
        val host = url.host
        val parts = host.split(".")
        if (parts.size >= 2) {
            "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
        } else {
            host
        }
    } catch (e: Exception) {
        fullDomain
    }
}