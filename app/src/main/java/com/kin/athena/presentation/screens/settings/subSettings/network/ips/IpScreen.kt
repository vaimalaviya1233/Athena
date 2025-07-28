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

package com.kin.athena.presentation.screens.settings.subSettings.network.ips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kin.athena.R
import com.kin.athena.presentation.components.material.MaterialActionButton
import com.kin.athena.presentation.components.material.MaterialPlaceholder
import com.kin.athena.presentation.components.material.MaterialScaffold
import com.kin.athena.presentation.screens.home.components.SearchBar
import com.kin.athena.presentation.screens.settings.components.IpDialog
import com.kin.athena.presentation.screens.settings.subSettings.network.ips.viewModel.IpViewModel

@Composable
fun IpScreen(
    navController: NavController,
    ipViewModel: IpViewModel = hiltViewModel()
) {
    if (ipViewModel.addDialogEnabled.value) {
        IpDialog(
            onExit = { ipViewModel.setAddDialog(false) },
            textFieldValue = ipViewModel.dialogTextField.value,
            onValueChange = ipViewModel::updateDialogText,
            onFinished = {
                ipViewModel.addIP(ipViewModel.dialogTextField.value)
                ipViewModel.updateDialogText(TextFieldValue())
            },
        )
    }

    MaterialScaffold(
        floatingActionButton = {
            MaterialActionButton (
                text = stringResource(R.string.enter_ip),
                icon = Icons.Rounded.Add,
                onClick = { ipViewModel.setAddDialog(true) }
            )
        },
        topBar = {
           SearchBar(
               query = ipViewModel.query.value,
               onQueryChange = ipViewModel::onQueryChanged,
               onClearClick = ipViewModel::clearQuery,
               onBackClicked = { navController.navigateUp() },
               button = {},
           )
        }
    ) {
        if (ipViewModel.ips.value.isEmpty()) {
            MaterialPlaceholder(
                placeholderIcon = {
                    Icon(
                        imageVector = Icons.Rounded.NetworkPing,
                        contentDescription = "Placeholder icon",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(64.dp)
                    )
                },
                placeholderText = stringResource(R.string.no_ips_found)
            )
        } else {
           LazyColumn(modifier = Modifier
               .padding(18.dp,18.dp,18.dp,18.dp)
               .clip(RoundedCornerShape(32.dp))
           ) {
               items(ipViewModel.ips.value) { ip ->
                   Row(
                       modifier = Modifier
                           .fillMaxWidth()
                           .background(color = MaterialTheme.colorScheme.surfaceContainer)
                           .clickable {
                               ipViewModel.deleteIP(ip.ip)
                           }
                           .padding(18.dp, 18.dp, 18.dp, 16.dp),
                       horizontalArrangement = Arrangement.SpaceBetween
                   ) {
                       Text(ip.ip)
                       Icon(
                           imageVector = Icons.Rounded.Delete,
                           contentDescription = ""
                       )
                   }
                   Spacer(modifier = Modifier.height(2.dp))
               }
           }
       }
    }
}