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

package com.kin.athena.presentation.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kin.athena.R
import com.kin.athena.presentation.components.material.MaterialButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    showStartInfo: Boolean = false,
    text: String = stringResource(R.string.search),
    query: String,
    onQueryChange: (String) -> Unit,
    onClearClick: () -> Unit,
    button: @Composable () -> Unit,
    onFirewallClicked: (() -> Unit)? = null,
    firewallColor: Color? = null,
    onBackClicked: (() -> Unit)? = null
) {
    SearchBar(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp, 0.dp, 24.dp, 18.dp),
        query = query,
        placeholder = { Text(text = if (showStartInfo) LocalContext.current.getString(R.string.search_bar_start) else text) },
        leadingIcon = {
            if (onFirewallClicked != null && firewallColor != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(onClick = { onFirewallClicked() }) {
                        Icon(Icons.Rounded.Security, tint = firewallColor, contentDescription = "Firewall")
                    }
                }
            } else {
                if (onBackClicked != null) {
                    IconButton(onClick = {onBackClicked() }) {
                        Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Back", modifier = Modifier.scale(0.8f))
                    }
                }
            }
        },
        trailingIcon = {
            Row(
                modifier = Modifier.padding(end = 6.dp)
            ) {
                if (query.isNotBlank()) {
                    MaterialButton(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close"
                    ) {
                        onClearClick()
                    }
                } else {
                    button()
                }
            }
        },
        onQueryChange = onQueryChange,
        onSearch = onQueryChange,
        onActiveChange = {},
        active = false,
        colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {}
}
